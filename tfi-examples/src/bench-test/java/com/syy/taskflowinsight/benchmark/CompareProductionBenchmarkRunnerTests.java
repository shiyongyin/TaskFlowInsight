package com.syy.taskflowinsight.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.ScalarResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.Options;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 生产 Compare workload 计划、runner 选项与证据拒绝合同。 */
class CompareProductionBenchmarkRunnerTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void staleSuccessMarkersAreInvalidatedBeforeCleanupCanFail() throws Exception {
        Path evidenceDirectory = temporaryDirectory.resolve("stale-evidence");
        Files.createDirectories(evidenceDirectory);
        Path marker = evidenceDirectory.resolve("_SUCCESS");
        Path pendingMarker = evidenceDirectory.resolve("_SUCCESS.tmp");
        Files.writeString(marker, "stale success\n");
        Files.writeString(pendingMarker, "stale pending success\n");

        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.prepareOutputDirectory(
                evidenceDirectory, evidenceDirectory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("escapes its owner");
        assertThat(marker).doesNotExist();
        assertThat(pendingMarker).doesNotExist();
    }

    @Test
    void workloadPlanIsTheExactSevenByThreeCartesianClosure() {
        var plans = CompareProductionBenchmarkRunner.workloadPlans();

        assertThat(plans).hasSize(21);
        assertThat(plans)
                .extracting(CompareProductionBenchmarkRunner.WorkloadPlan::scenario)
                .containsOnly(CompareProductionBenchmarkRunner.Scenario.values());
        assertThat(plans)
                .extracting(CompareProductionBenchmarkRunner.WorkloadPlan::threads)
                .containsOnlyElementsOf(Set.of(1, 8, 32));
        assertThat(plans)
                .extracting(CompareProductionBenchmarkRunner.WorkloadPlan::workloadId)
                .doesNotHaveDuplicates()
                .allMatch(id -> id.matches("[a-z_]+-t(?:1|8|32)"));
        assertThat(plans.stream()
                .map(plan -> plan.scenario() + ":" + plan.threads())
                .distinct())
                .hasSize(21);
    }

    @Test
    void everyWorkloadBuildsAnIndependentSampleTimeJsonRunWithGcProfiler() {
        for (var plan : CompareProductionBenchmarkRunner.workloadPlans()) {
            Path report = temporaryDirectory.resolve(plan.workloadId() + ".json");

            Options options = CompareProductionBenchmarkRunner.optionsFor(plan, report, 1);

            assertThat(options.getIncludes())
                    .containsExactly("^com\\.syy\\.taskflowinsight\\.benchmark"
                            + "\\.CompareProductionBenchmarks\\.compare$");
            assertThat(options.getParameter("scenario").get())
                    .containsExactly(plan.scenario().name());
            assertThat(options.getThreads().get()).isEqualTo(plan.threads());
            assertThat(options.getBenchModes()).containsExactly(Mode.SampleTime);
            assertThat(options.getTimeUnit().get()).isEqualTo(TimeUnit.NANOSECONDS);
            assertThat(options.getResultFormat().get()).isEqualTo(ResultFormatType.JSON);
            assertThat(options.getResult().get()).isEqualTo(report.toString());
            assertThat(options.getProfilers())
                    .extracting(profiler -> profiler.getKlass())
                    .containsExactly(GCProfiler.class.getName());
            assertThat(options.getJvmArgsAppend().get())
                    .anyMatch(argument -> argument.startsWith(
                            "-Dtfi.compare.bench.fork.evidence="))
                    .contains("-Dtfi.compare.bench.workload.id=" + plan.workloadId());
            assertThat(options.shouldFailOnError().get()).isTrue();
        }
    }

    @Test
    void forkCaptureIncludesSyntheticNestAndItsOwnSemanticReceipt() throws Exception {
        var fact = CompareProductionBenchmarks.validateAllScenarios().stream()
                .filter(candidate -> candidate.scenario()
                        == CompareProductionBenchmarkRunner.Scenario.NESTED_POJO)
                .findFirst()
                .orElseThrow();
        Path forkDirectory = temporaryDirectory.resolve("fork-capture");
        String previousDirectory = System.getProperty("tfi.compare.bench.fork.evidence");
        String previousWorkload = System.getProperty("tfi.compare.bench.workload.id");
        try {
            System.setProperty(
                    "tfi.compare.bench.fork.evidence", forkDirectory.toString());
            System.setProperty("tfi.compare.bench.workload.id", "nested_pojo-t1");

            BenchmarkForkEvidence.captureIfRequested(
                    CompareRuntime.builder().build().engine(), fact);
        } finally {
            restoreProperty("tfi.compare.bench.fork.evidence", previousDirectory);
            restoreProperty("tfi.compare.bench.workload.id", previousWorkload);
        }

        long processId = ProcessHandle.current().pid();
        assertThat(BenchmarkForkEvidence.benchmarkNestClasses())
                .extracting(Class::getName)
                .contains(CompareProductionBenchmarks.class.getName() + "$1");
        assertThat(Files.readAllLines(
                forkDirectory.resolve("code-sources-" + processId + ".tsv")))
                .anyMatch(line -> line.contains(
                        "\t" + CompareProductionBenchmarks.class.getName() + "$1\t"));
        var receipt = new ObjectMapper().readTree(
                forkDirectory.resolve("semantic-fact-" + processId + ".json").toFile());
        assertThat(receipt.path("schema").textValue())
                .isEqualTo("TFI_COMPARE_FORK_SEMANTIC_V1");
        assertThat(receipt.path("processId").longValue()).isEqualTo(processId);
        assertThat(receipt.path("scenario").textValue()).isEqualTo("NESTED_POJO");
        assertThat(receipt.path("changeTokens")).hasSize(1);
    }

    @Test
    void retainedForkCopyMustMatchTheCurrentOriginalCodeSource() throws Exception {
        Class<?> synthetic = BenchmarkForkEvidence.benchmarkNestClasses().stream()
                .filter(type -> type.getName().equals(
                        CompareProductionBenchmarks.class.getName() + "$1"))
                .findFirst()
                .orElseThrow();
        String location = synthetic.getProtectionDomain().getCodeSource()
                .getLocation().toExternalForm();
        Path sourceRoot = Path.of(synthetic.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        Path classFile = sourceRoot.resolve(synthetic.getName().replace('.', '/') + ".class");

        CompareProductionBenchmarkRunner.requireOriginalLocationDigest(
                synthetic.getName(), location, "CLASS_BYTES", sha256(classFile), "test-t1");
        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireOriginalLocationDigest(
                synthetic.getName(), location, "CLASS_BYTES", "0".repeat(64), "test-t1"))
                .hasMessageContaining("does not match retained bytes");
    }

    @Test
    void rawReportCannotBeAnUnrelatedNonEmptyJsonArray() throws Exception {
        var plan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        Path report = temporaryDirectory.resolve("unrelated.json");
        Files.writeString(report, "[{}]");

        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireMeasurement(
                plan, report, List.of(runResult(plan, "ns/op", true))))
                .hasMessageContaining("raw JMH");
    }

    @Test
    void validRawResultExposesUnroundedP99AndAllocation() throws Exception {
        var plan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        Path report = temporaryDirectory.resolve("valid.json");
        writeRawResult(report, plan, "ns/op", true, 19.75d, 48.5d);

        var measurement = CompareProductionBenchmarkRunner.requireMeasurement(
                plan, report, List.of(runResult(plan, "ns/op", true)));

        assertThat(measurement.workloadId()).isEqualTo(plan.workloadId());
        assertThat(measurement.p99Nanos()).isEqualTo(19.75d);
        assertThat(measurement.allocationBytesPerOperation()).isEqualTo(48.5d);
        assertThat(measurement.sampleCount()).isEqualTo(1L);
    }

    @Test
    void rawResultMustExistAndContainExactlyOneSelectedBenchmark() throws Exception {
        var plan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        Path missing = temporaryDirectory.resolve("missing.json");

        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireMeasurement(
                plan, missing, List.of(runResult(plan, "ns/op", true))))
                .hasMessageContaining("non-empty raw JSON");

        Path report = temporaryDirectory.resolve("empty-result.json");
        Files.writeString(report, "[{}]");
        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireMeasurement(
                plan, report, List.of()))
                .hasMessageContaining("exactly one JMH result");
    }

    @Test
    void rawResultRejectsWrongUnitOrMissingAllocationMetric() throws Exception {
        var plan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        Path report = temporaryDirectory.resolve("invalid-metric.json");
        writeRawResult(report, plan, "us/op", true, 19.75d, 48.5d);

        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireMeasurement(
                plan, report, List.of(runResult(plan, "ns/op", true))))
                .hasMessageContaining("raw JMH");
        writeRawResult(report, plan, "ns/op", false, 19.75d, 48.5d);
        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireMeasurement(
                plan, report, List.of(runResult(plan, "ns/op", true))))
                .hasMessageContaining("raw JMH");
    }

    @Test
    void rawReportMustMatchTheReturnedJmhResult() throws Exception {
        var plan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        Path report = temporaryDirectory.resolve("mismatch.json");
        writeRawResult(report, plan, "ns/op", true, 20.75d, 48.5d);

        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.requireMeasurement(
                plan, report, List.of(runResult(plan, "ns/op", true))))
                .hasMessageContaining("does not match in-memory result");
    }

    @Test
    void evidenceWriterRequiresTheCompleteWorkloadClosure() throws Exception {
        Path evidenceDirectory = temporaryDirectory.resolve("evidence");
        Files.createDirectories(evidenceDirectory.resolve("raw"));
        List<CompareProductionBenchmarkRunner.WorkloadMeasurement> measurements =
                new ArrayList<>();
        List<CompareProductionBenchmarkRunner.ForkEvidence> forkEvidence = new ArrayList<>();
        Map<CompareProductionBenchmarkRunner.Scenario,
                CompareProductionBenchmarks.SemanticFact> facts =
                CompareProductionBenchmarks.validateAllScenarios().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                CompareProductionBenchmarks.SemanticFact::scenario,
                                fact -> fact));
        for (var plan : CompareProductionBenchmarkRunner.workloadPlans()) {
            Path rawReport = evidenceDirectory.resolve("raw")
                    .resolve(plan.workloadId() + ".json");
            writeRawResult(rawReport, plan, "ns/op", true, 19.75d, 48.5d);
            var actualForkEvidence = forkEvidence(
                    plan, evidenceDirectory, facts.get(plan.scenario()));
            CompareProductionBenchmarkRunner.enrichRawReport(
                    plan, rawReport, actualForkEvidence.semanticFact(), actualForkEvidence);
            forkEvidence.add(actualForkEvidence);
            measurements.add(new CompareProductionBenchmarkRunner.WorkloadMeasurement(
                    plan.workloadId(), plan.scenario(), plan.threads(), rawReport,
                    19.75d, 48.5d, 1L));
        }

        CompareProductionBenchmarkRunner.writeEvidence(
                evidenceDirectory,
                measurements,
                List.copyOf(facts.values()),
                CompareProductionBenchmarkRunner.captureCodeSources(),
                forkEvidence);

        assertThat(Files.readAllLines(evidenceDirectory.resolve("measurements.tsv")))
                .hasSize(22)
                .first()
                .isEqualTo("workloadId\tscenario\tthreads\trawReport\tp99Nanos"
                        + "\tgcAllocRateNormBytesPerOp\tsampleCount");
        assertThat(Files.readAllLines(evidenceDirectory.resolve("semantic-facts.tsv")))
                .hasSize(22)
                .first()
                .isEqualTo("workloadId\tscenario\tthreads\toutcome\tcompletion"
                        + "\tchangeCount\tchangeTokens\tlimitationCodes"
                        + "\tdistinctInputs\tobservedDecorator");
        assertThat(Files.readAllLines(evidenceDirectory.resolve("fork-code-sources.tsv")))
                .hasSize(22);
        var firstPlan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        var enriched = new ObjectMapper().readTree(
                evidenceDirectory.resolve("raw")
                        .resolve(firstPlan.workloadId() + ".json")
                        .toFile())
                .get(0);
        assertThat(enriched.path("tfiEvidenceSchema").textValue())
                .isEqualTo("TFI_COMPARE_JMH_V1");
        assertThat(enriched.path("tfiSemantic").path("changeTokens")).hasSize(1);
        assertThat(enriched.path("tfiSemantic").path("forkReceipts")).singleElement()
                .satisfies(receipt -> {
                    Path retained = Path.of(URI.create(receipt.path("receipt").textValue()));
                    assertThat(retained).isRegularFile();
                    assertThat(receipt.path("sha256").textValue())
                            .isEqualTo(sha256(retained));
                });
        assertThat(enriched.path("tfiForkCodeSources")).singleElement()
                .satisfies(source -> {
                    Path retained = Path.of(URI.create(
                            source.path("retainedPreimage").textValue()));
                    assertThat(retained).isRegularFile();
                    assertThat(source.path("sha256").textValue())
                            .isEqualTo(sha256(retained));
                });
        assertThat(Files.readAllLines(evidenceDirectory.resolve("code-sources.tsv")))
                .hasSizeGreaterThanOrEqualTo(4)
                .allSatisfy(line -> {
                    if (line.startsWith("className\t")) {
                        assertThat(line).isEqualTo("className\toriginalLocation"
                                + "\tretainedPreimage\tdigestKind\tsha256");
                    } else {
                        String[] fields = line.split("\t");
                        Path preimage = Path.of(URI.create(fields[2]));
                        assertThat(preimage).isRegularFile()
                                .startsWith(evidenceDirectory.resolve("code-sources"));
                        assertThat(fields[4]).isEqualTo(sha256(preimage));
                    }
                });

        var substitutedRoot = new ObjectMapper().readTree(
                measurements.getFirst().rawReport().toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) substitutedRoot.get(0)
                .path("tfiSemantic")).putArray("changeTokens");
        new ObjectMapper().writeValue(
                measurements.getFirst().rawReport().toFile(), substitutedRoot);
        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.writeEvidence(
                evidenceDirectory,
                measurements,
                List.copyOf(facts.values()),
                CompareProductionBenchmarkRunner.captureCodeSources(),
                forkEvidence))
                .hasMessageContaining("raw report extension");
        CompareProductionBenchmarkRunner.enrichRawReport(
                firstPlan,
                measurements.getFirst().rawReport(),
                facts.get(firstPlan.scenario()),
                forkEvidence.getFirst());

        assertThatThrownBy(() -> CompareProductionBenchmarkRunner.writeEvidence(
                temporaryDirectory.resolve("incomplete"),
                measurements.subList(0, 20),
                List.copyOf(facts.values()),
                CompareProductionBenchmarkRunner.captureCodeSources(),
                forkEvidence.subList(0, 20)))
                .hasMessageContaining("21-workload closure");
    }

    @Test
    void successMarkerBindsTheEntireEvidenceTreeAndRejectsTampering() throws Exception {
        Path evidenceDirectory = temporaryDirectory.resolve("marker-evidence");
        writeCompleteEvidenceBundle(evidenceDirectory);

        CompareProductionBenchmarkRunner.writeSuccessMarker(evidenceDirectory);
        CompareProductionBenchmarkRunner.verifySuccessMarker(evidenceDirectory);

        var firstPlan = CompareProductionBenchmarkRunner.workloadPlans().getFirst();
        long processId = 100_000L + firstPlan.threads();
        Path workloadDirectory = evidenceDirectory.resolve("fork-evidence")
                .resolve(firstPlan.workloadId());
        List<Path> tamperTargets = List.of(
                evidenceDirectory.resolve("raw").resolve(firstPlan.workloadId() + ".json"),
                workloadDirectory.resolve("semantic-fact-" + processId + ".json"),
                workloadDirectory.resolve("code-sources-" + processId + ".tsv"),
                workloadDirectory.resolve("preimages").resolve("fixture.class"));
        List<String> markerRows = Files.readAllLines(evidenceDirectory.resolve("_SUCCESS"));
        assertThat(markerRows)
                .contains("entryType\trelativePath\tsha256", "directory\traw\t-")
                .noneMatch(row -> row.contains("_SUCCESS"));
        for (Path target : tamperTargets) {
            String relative = evidenceDirectory.relativize(target).toString().replace('\\', '/');
            assertThat(markerRows).contains("file\t" + relative + "\t" + sha256(target));

            byte[] original = Files.readAllBytes(target);
            Files.writeString(target, "tampered", StandardOpenOption.APPEND);
            assertThatThrownBy(() ->
                    CompareProductionBenchmarkRunner.verifySuccessMarker(evidenceDirectory))
                    .hasMessageContaining("success marker does not match evidence tree");
            Files.write(target, original);
            CompareProductionBenchmarkRunner.verifySuccessMarker(evidenceDirectory);
        }
    }

    @Test
    void successMarkerRejectsTamperingWithItsOwnManifestRows() throws Exception {
        Path evidenceDirectory = temporaryDirectory.resolve("tampered-marker-evidence");
        writeCompleteEvidenceBundle(evidenceDirectory);
        CompareProductionBenchmarkRunner.writeSuccessMarker(evidenceDirectory);
        Path marker = evidenceDirectory.resolve("_SUCCESS");
        List<String> rows = new ArrayList<>(Files.readAllLines(marker));
        int firstFileRow = java.util.stream.IntStream.range(0, rows.size())
                .filter(index -> rows.get(index).startsWith("file\t"))
                .findFirst()
                .orElseThrow();
        String originalRow = rows.get(firstFileRow);
        char replacement = originalRow.endsWith("0") ? '1' : '0';
        rows.set(firstFileRow,
                originalRow.substring(0, originalRow.length() - 1) + replacement);
        Files.writeString(marker, String.join("\n", rows) + "\n");

        assertThatThrownBy(() ->
                CompareProductionBenchmarkRunner.verifySuccessMarker(evidenceDirectory))
                .hasMessageContaining("success marker does not match evidence tree");
    }

    private static void writeCompleteEvidenceBundle(Path evidenceDirectory) throws Exception {
        Files.createDirectories(evidenceDirectory.resolve("raw"));
        List<CompareProductionBenchmarkRunner.WorkloadMeasurement> measurements =
                new ArrayList<>();
        List<CompareProductionBenchmarkRunner.ForkEvidence> forkEvidence = new ArrayList<>();
        Map<CompareProductionBenchmarkRunner.Scenario,
                CompareProductionBenchmarks.SemanticFact> facts =
                CompareProductionBenchmarks.validateAllScenarios().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                CompareProductionBenchmarks.SemanticFact::scenario,
                                fact -> fact));
        for (var plan : CompareProductionBenchmarkRunner.workloadPlans()) {
            Path rawReport = evidenceDirectory.resolve("raw")
                    .resolve(plan.workloadId() + ".json");
            writeRawResult(rawReport, plan, "ns/op", true, 19.75d, 48.5d);
            var actualForkEvidence = forkEvidence(
                    plan, evidenceDirectory, facts.get(plan.scenario()));
            CompareProductionBenchmarkRunner.enrichRawReport(
                    plan, rawReport, actualForkEvidence.semanticFact(), actualForkEvidence);
            forkEvidence.add(actualForkEvidence);
            measurements.add(new CompareProductionBenchmarkRunner.WorkloadMeasurement(
                    plan.workloadId(), plan.scenario(), plan.threads(), rawReport,
                    19.75d, 48.5d, 1L));
        }
        CompareProductionBenchmarkRunner.writeEvidence(
                evidenceDirectory,
                measurements,
                List.copyOf(facts.values()),
                CompareProductionBenchmarkRunner.captureCodeSources(),
                forkEvidence);
    }

    private static CompareProductionBenchmarkRunner.ForkEvidence forkEvidence(
            CompareProductionBenchmarkRunner.WorkloadPlan plan,
            Path evidenceDirectory,
            CompareProductionBenchmarks.SemanticFact semanticFact) throws Exception {
        long processId = 100_000L + plan.threads();
        Path preimage = evidenceDirectory.resolve("fork-evidence")
                .resolve(plan.workloadId())
                .resolve("preimages")
                .resolve("fixture.class");
        Files.createDirectories(preimage.getParent());
        Files.write(preimage, plan.workloadId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var source = new CompareProductionBenchmarkRunner.ForkCodeSource(
                plan.workloadId(),
                processId,
                CompareProductionBenchmarks.class.getName(),
                temporaryCodeSource(),
                preimage.toUri().toString(),
                "CLASS_BYTES",
                sha256(preimage));
        Path semanticReceipt = preimage.getParent().getParent()
                .resolve("semantic-fact-" + processId + ".json");
        Files.writeString(semanticReceipt, "{\"schema\":\"test-fixture\"}\n");
        var receipt = new CompareProductionBenchmarkRunner.ForkSemanticReceipt(
                processId,
                semanticReceipt.toUri().toString(),
                sha256(semanticReceipt),
                semanticFact);
        Path manifest = preimage.getParent().getParent()
                .resolve("code-sources-" + processId + ".tsv");
        Files.writeString(manifest,
                "workloadId\tprocessId\tclassName\toriginalLocation"
                        + "\tretainedPreimage\tdigestKind\tsha256\n"
                        + String.join("\t", source.workloadId(), Long.toString(processId),
                        source.className(), source.originalLocation(), source.retainedPreimage(),
                        source.digestKind(), source.sha256()) + "\n");
        return new CompareProductionBenchmarkRunner.ForkEvidence(
                plan.workloadId(), semanticFact, List.of(receipt), List.of(source));
    }

    private static String temporaryCodeSource() {
        return "file:/fixture/classes/";
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RunResult runResult(
            CompareProductionBenchmarkRunner.WorkloadPlan plan,
            String primaryUnit,
            boolean includeAllocation) {
        RunResult runResult = mock(RunResult.class);
        BenchmarkParams parameters = mock(BenchmarkParams.class);
        Result primary = new ScalarResult(
                "compare", 19.75d, primaryUnit, AggregationPolicy.AVG);
        Result allocation = new ScalarResult(
                "gc.alloc.rate.norm", 48.5d, "B/op", AggregationPolicy.AVG);

        when(runResult.getParams()).thenReturn(parameters);
        when(parameters.getBenchmark()).thenReturn(
                "com.syy.taskflowinsight.benchmark.CompareProductionBenchmarks.compare");
        when(parameters.getParam("scenario")).thenReturn(plan.scenario().name());
        when(parameters.getThreads()).thenReturn(plan.threads());
        when(runResult.getPrimaryResult()).thenReturn(primary);
        when(runResult.getSecondaryResults()).thenReturn(includeAllocation
                ? Map.of("gc.alloc.rate.norm", allocation)
                : Map.of());
        return runResult;
    }

    private static void writeRawResult(
            Path report,
            CompareProductionBenchmarkRunner.WorkloadPlan plan,
            String primaryUnit,
            boolean includeAllocation,
            double p99,
            double allocation) throws IOException {
        Map<String, Object> secondaryMetrics = includeAllocation
                ? Map.of("gc.alloc.rate.norm", Map.of(
                        "score", allocation,
                        "scoreUnit", "B/op"))
                : Map.of();
        Map<String, Object> rawResult = Map.of(
                "benchmark", "com.syy.taskflowinsight.benchmark."
                        + "CompareProductionBenchmarks.compare",
                "threads", plan.threads(),
                "params", Map.of("scenario", plan.scenario().name()),
                "primaryMetric", Map.of(
                        "scoreUnit", primaryUnit,
                        "scorePercentiles", Map.of("99.0", p99),
                        "rawDataHistogram", List.of(List.of(List.of(List.of(p99, 1L))))),
                "secondaryMetrics", secondaryMetrics);
        new ObjectMapper().writeValue(report.toFile(), List.of(rawResult));
    }

    @Test
    void everyProductionFixtureHasANonIdentitySemanticOracle() {
        var facts = CompareProductionBenchmarks.validateAllScenarios();

        assertThat(facts).hasSize(7);
        assertThat(facts)
                .extracting(CompareProductionBenchmarks.SemanticFact::scenario)
                .containsOnly(CompareProductionBenchmarkRunner.Scenario.values());
        assertThat(facts).allSatisfy(fact -> assertThat(fact.distinctInputs()).isTrue());
        assertThat(facts).allSatisfy(fact ->
                assertThat(fact.changeTokens()).hasSize(fact.changeCount()));
        assertThat(facts)
                .filteredOn(fact -> fact.scenario()
                        != CompareProductionBenchmarkRunner.Scenario.SET_AMBIGUOUS)
                .allSatisfy(fact -> {
                    assertThat(fact.outcome()).isEqualTo(CompareOutcome.DIFFERENT);
                    assertThat(fact.completion()).isEqualTo(CompareCompletion.COMPLETE);
                    assertThat(fact.limitationCodes()).isEmpty();
                });
        assertThat(facts).extracting(
                        CompareProductionBenchmarks.SemanticFact::scenario,
                        CompareProductionBenchmarks.SemanticFact::changeCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.NESTED_POJO, 1),
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.LIST, 1),
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.MAP, 1),
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.SET_SCALAR, 2),
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.SET_ENTITY, 1),
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.SET_AMBIGUOUS, 0),
                        org.assertj.core.groups.Tuple.tuple(
                                CompareProductionBenchmarkRunner.Scenario.OBSERVED_COMPARE, 1));
        assertThat(facts)
                .filteredOn(fact -> fact.scenario()
                        == CompareProductionBenchmarkRunner.Scenario.SET_SCALAR)
                .singleElement()
                .extracting(CompareProductionBenchmarks.SemanticFact::changeTokens)
                .asList()
                .anySatisfy(token -> assertThat(token.toString()).startsWith("ADD|"))
                .anySatisfy(token -> assertThat(token.toString()).startsWith("REMOVE|"));
        assertThat(facts)
                .filteredOn(fact -> fact.scenario()
                        == CompareProductionBenchmarkRunner.Scenario.SET_AMBIGUOUS)
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.outcome()).isEqualTo(CompareOutcome.INDETERMINATE);
                    assertThat(fact.completion()).isEqualTo(CompareCompletion.PARTIAL);
                    assertThat(fact.changeCount()).isZero();
                    assertThat(fact.limitationCodes())
                            .containsExactly(CompareLimitationCode.KEY_AMBIGUOUS);
                });
        assertThat(facts)
                .filteredOn(CompareProductionBenchmarks.SemanticFact::observedDecorator)
                .extracting(CompareProductionBenchmarks.SemanticFact::scenario)
                .containsExactly(CompareProductionBenchmarkRunner.Scenario.OBSERVED_COMPARE);
    }
}
