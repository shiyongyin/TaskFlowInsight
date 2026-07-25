package com.syy.taskflowinsight.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syy.taskflowinsight.ops.compare.ObservedCompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 逐 workload 执行 Compare 生产 JMH，并保留可独立裁决的原始证据。
 */
public final class CompareProductionBenchmarkRunner {

    /** JMH 返回的 benchmark 名必须逐字匹配，防止 include 意外命中其他方法。 */
    private static final String BENCHMARK_NAME =
            "com.syy.taskflowinsight.benchmark.CompareProductionBenchmarks.compare";
    /** 只允许选择本卡定义的单个生产 Compare benchmark 方法。 */
    private static final String BENCHMARK_PATTERN =
            "^com\\.syy\\.taskflowinsight\\.benchmark"
                    + "\\.CompareProductionBenchmarks\\.compare$";
    /** 生产并发轴；每个场景都必须独立执行这三个真实 JMH thread count。 */
    private static final List<Integer> REQUIRED_THREADS = List.of(1, 8, 32);

    private CompareProductionBenchmarkRunner() {
    }

    /**
     * 逐一执行 21 个生产 workload，并在全部成功后写出闭集证据。
     *
     * <p>输出目录可用 {@code tfi.compare.bench.output} 指定，fork 数可用
     * {@code jmh.forks} 指定；默认分别为模块 target 目录和 1。</p>
     *
     * @param args 未使用
     * @throws IOException raw report 或 TSV 证据无法写入时抛出
     * @throws RunnerException 任一 JMH workload 或证据校验失败时抛出
     */
    public static void main(String[] args) throws IOException, RunnerException {
        Path outputDirectory = Path.of(System.getProperty(
                "tfi.compare.bench.output", "target/perf/compare-production"))
                .toAbsolutePath()
                .normalize();
        int forks = Integer.parseInt(System.getProperty("jmh.forks", "1"));
        if (forks <= 0) {
            throw new IllegalArgumentException("jmh.forks must be positive");
        }
        prepareOutputDirectory(outputDirectory, outputDirectory.getParent());

        List<CompareProductionBenchmarks.SemanticFact> semanticFacts =
                CompareProductionBenchmarks.validateAllScenarios();
        Map<Scenario, CompareProductionBenchmarks.SemanticFact> semanticByScenario =
                semanticFacts.stream().collect(Collectors.toMap(
                        CompareProductionBenchmarks.SemanticFact::scenario,
                        fact -> fact));
        List<CodeSourceEvidence> codeSources = captureCodeSources();
        Path rawDirectory = outputDirectory.resolve("raw");
        Path forkEvidenceDirectory = outputDirectory.resolve("fork-evidence");
        Files.createDirectories(rawDirectory);
        List<WorkloadMeasurement> measurements = new ArrayList<>();
        List<ForkEvidence> forkEvidence = new ArrayList<>();
        for (WorkloadPlan plan : workloadPlans()) {
            Path report = rawDirectory.resolve(plan.workloadId() + ".json");
            Path workloadForkDirectory = forkEvidenceDirectory.resolve(plan.workloadId());
            recreateDirectory(workloadForkDirectory, forkEvidenceDirectory);
            Collection<RunResult> results = new Runner(
                    optionsFor(plan, report, forks, workloadForkDirectory)).run();
            measurements.add(requireMeasurement(plan, report, results));
            ForkEvidence actualForkEvidence = readForkEvidence(
                    plan,
                    workloadForkDirectory,
                    forks,
                    ProcessHandle.current().pid(),
                    semanticByScenario.get(plan.scenario()));
            forkEvidence.add(actualForkEvidence);
            enrichRawReport(
                    plan,
                    report,
                    actualForkEvidence.semanticFact(),
                    actualForkEvidence);
        }
        writeEvidence(outputDirectory, measurements, semanticFacts, codeSources, forkEvidence);
        writeSuccessMarker(outputDirectory);
        System.out.println("Compare production benchmark evidence written to "
                + outputDirectory.toUri());
    }

    /** 生产性能政策允许的封闭场景集合。 */
    enum Scenario {
        /** 多层普通对象的非 identity 字段变化。 */
        NESTED_POJO,
        /** 有序列表中部成员变化。 */
        LIST,
        /** typed key Map 的 value 变化。 */
        MAP,
        /** scalar Set 的成员替换。 */
        SET_SCALAR,
        /** unique key Entity Set 的内容变化。 */
        SET_ENTITY,
        /** duplicate key Entity Set 的显式 ambiguity。 */
        SET_AMBIGUOUS,
        /** 当前 Spring Context 的 metrics decorator 路径。 */
        OBSERVED_COMPARE
    }

    /**
     * 生成 scenario x threads 的稳定笛卡尔闭集。
     *
     * @return 以 scenario 声明顺序、thread 升序排列的 21 个 workload
     */
    static List<WorkloadPlan> workloadPlans() {
        List<WorkloadPlan> plans = new ArrayList<>(Scenario.values().length * REQUIRED_THREADS.size());
        for (Scenario scenario : Scenario.values()) {
            for (int threads : REQUIRED_THREADS) {
                plans.add(new WorkloadPlan(
                        scenario.name().toLowerCase(Locale.ROOT) + "-t" + threads,
                        scenario,
                        threads));
            }
        }
        return List.copyOf(plans);
    }

    /**
     * 为一个 workload 构造独立 JMH 运行，避免多个 scenario 共用报告或线程配置。
     *
     * @param plan 本次唯一的 scenario/thread 选择
     * @param report 本次 JMH 原始 JSON 路径
     * @param forks fork 数，必须为正数
     * @return 已固定 SampleTime、纳秒单位与 GC allocation profiler 的选项
     */
    static Options optionsFor(WorkloadPlan plan, Path report, int forks) {
        Path forkEvidence = report.toAbsolutePath().normalize()
                .resolveSibling(plan.workloadId() + "-fork-evidence");
        return optionsFor(plan, report, forks, forkEvidence);
    }

    private static Options optionsFor(
            WorkloadPlan plan,
            Path report,
            int forks,
            Path forkEvidence) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(forkEvidence, "forkEvidence");
        if (forks <= 0) {
            throw new IllegalArgumentException("forks must be positive");
        }
        return new OptionsBuilder()
                .include(BENCHMARK_PATTERN)
                .param("scenario", plan.scenario().name())
                .threads(plan.threads())
                .mode(Mode.SampleTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .forks(forks)
                .warmupIterations(3)
                .measurementIterations(5)
                .addProfiler(GCProfiler.class)
                .jvmArgsAppend(
                        "-Dtfi.compare.bench.fork.evidence="
                                + forkEvidence.toAbsolutePath().normalize(),
                        "-Dtfi.compare.bench.workload.id=" + plan.workloadId())
                .shouldFailOnError(true)
                .result(report.toString())
                .resultFormat(ResultFormatType.JSON)
                .build();
    }

    private static void recreateDirectory(final Path directory, final Path requiredParent)
            throws IOException {
        final Path normalizedParent = requiredParent.toAbsolutePath().normalize();
        final Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedParent)
                || normalizedDirectory.equals(normalizedParent)) {
            throw new IOException("fork evidence directory escapes its owner");
        }
        Files.createDirectories(normalizedParent);
        if (Files.exists(normalizedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(normalizedDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectory(normalizedDirectory);
    }

    /**
     * 在任何旧证据清理前先撤销成功声明，避免中断或删除失败后残留可误判的 marker。
     */
    static void prepareOutputDirectory(final Path directory, final Path requiredParent)
            throws IOException {
        final Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(normalizedDirectory.resolve("_SUCCESS"));
            Files.deleteIfExists(normalizedDirectory.resolve("_SUCCESS.tmp"));
        }
        recreateDirectory(normalizedDirectory, requiredParent);
    }

    private static ForkEvidence readForkEvidence(
            final WorkloadPlan plan,
            final Path evidenceDirectory,
            final int expectedForks,
            final long parentProcessId,
            final CompareProductionBenchmarks.SemanticFact expectedSemantic)
            throws IOException, RunnerException {
        final List<Path> manifests;
        try (var paths = Files.list(evidenceDirectory)) {
            manifests = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString()
                            .matches("code-sources-[1-9][0-9]*\\.tsv"))
                    .sorted()
                    .toList();
        }
        if (manifests.size() != expectedForks) {
            throw new RunnerException("fork evidence count does not match JMH forks: "
                    + plan.workloadId());
        }
        final List<ForkCodeSource> sources = new ArrayList<>();
        final List<ForkSemanticReceipt> semanticReceipts = new ArrayList<>();
        final Set<Long> processIds = new java.util.HashSet<>();
        CompareProductionBenchmarks.SemanticFact forkSemantic = null;
        for (Path manifest : manifests) {
            final List<ForkCodeSource> manifestSources = readForkManifest(
                    plan, evidenceDirectory, manifest);
            final long processId = manifestSources.getFirst().processId();
            if (processId == parentProcessId || !processIds.add(processId)
                    || manifestSources.stream().anyMatch(source -> source.processId() != processId)
                    || !manifest.getFileName().toString()
                            .equals("code-sources-" + processId + ".tsv")) {
                throw new RunnerException("fork PID attribution is inconsistent: "
                        + plan.workloadId());
            }
            requireForkClassClosure(plan, manifestSources);
            final ForkSemanticReceipt receipt = readForkSemanticReceipt(
                    plan, evidenceDirectory, processId);
            final CompareProductionBenchmarks.SemanticFact actualSemantic = receipt.semanticFact();
            if (!actualSemantic.equals(expectedSemantic)
                    || (forkSemantic != null && !forkSemantic.equals(actualSemantic))) {
                throw new RunnerException("fork semantic receipt does not match oracle: "
                        + plan.workloadId());
            }
            forkSemantic = actualSemantic;
            semanticReceipts.add(receipt);
            sources.addAll(manifestSources);
        }
        if (forkSemantic == null) {
            throw new RunnerException("fork semantic receipt is missing: " + plan.workloadId());
        }
        requireForkDirectoryClosure(evidenceDirectory, processIds, sources);
        return new ForkEvidence(
                plan.workloadId(),
                forkSemantic,
                List.copyOf(semanticReceipts),
                List.copyOf(sources));
    }

    private static List<ForkCodeSource> readForkManifest(
            final WorkloadPlan plan,
            final Path evidenceDirectory,
            final Path manifest) throws IOException, RunnerException {
        final List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        final String header = "workloadId\tprocessId\tclassName\toriginalLocation"
                + "\tretainedPreimage\tdigestKind\tsha256";
        if (lines.size() < 2 || !header.equals(lines.getFirst())) {
            throw new RunnerException("fork CodeSource manifest is incomplete: "
                    + plan.workloadId());
        }
        final Path preimageRoot = evidenceDirectory.resolve("preimages")
                .toAbsolutePath().normalize();
        final List<ForkCodeSource> sources = new ArrayList<>();
        final Set<String> classNames = new java.util.HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            final String[] fields = line.split("\t", -1);
            if (fields.length != 7 || !plan.workloadId().equals(fields[0])
                    || !fields[1].matches("[1-9][0-9]*")
                    || !fields[2].matches("[A-Za-z_$][A-Za-z0-9_.$]*")
                    || fields[3].isEmpty()
                    || !Set.of("ARTIFACT", "CLASS_BYTES").contains(fields[5])
                    || !fields[6].matches("[0-9a-f]{64}")
                    || !classNames.add(fields[2])) {
                throw new RunnerException("fork CodeSource row is invalid: "
                        + plan.workloadId());
            }
            final Path retained;
            try {
                retained = Path.of(URI.create(fields[4])).toAbsolutePath().normalize();
            } catch (IllegalArgumentException exception) {
                throw new RunnerException("fork retained preimage URI is invalid: "
                        + plan.workloadId(), exception);
            }
            if (!retained.startsWith(preimageRoot)
                    || !Files.isRegularFile(retained, LinkOption.NOFOLLOW_LINKS)
                    || !fields[6].equals(sha256(Files.readAllBytes(retained)))) {
                throw new RunnerException("fork retained preimage does not match digest: "
                        + plan.workloadId());
            }
            requireOriginalLocationDigest(
                    fields[2], fields[3], fields[5], fields[6], plan.workloadId());
            sources.add(new ForkCodeSource(
                    fields[0], Long.parseLong(fields[1]), fields[2], fields[3],
                    fields[4], fields[5], fields[6]));
        }
        return List.copyOf(sources);
    }

    /**
     * 把 retained 副本重新连接到 fork 报告的原始 CodeSource，拒绝 manifest 与副本成对替换。
     */
    static void requireOriginalLocationDigest(
            final String className,
            final String originalLocation,
            final String digestKind,
            final String expectedSha256,
            final String workloadId) throws IOException, RunnerException {
        final Path sourcePath;
        try {
            final URI uri = URI.create(originalLocation);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("CodeSource is not a file URI");
            }
            sourcePath = Path.of(uri).toAbsolutePath().normalize();
        } catch (IllegalArgumentException exception) {
            throw new RunnerException(
                    "fork original CodeSource URI is invalid: " + workloadId, exception);
        }

        final Path measured;
        if ("ARTIFACT".equals(digestKind)) {
            measured = sourcePath;
        } else if ("CLASS_BYTES".equals(digestKind)
                && Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            measured = sourcePath.resolve(className.replace('.', '/') + ".class").normalize();
            if (!measured.startsWith(sourcePath)) {
                throw new RunnerException("fork class path escapes CodeSource: " + workloadId);
            }
        } else {
            throw new RunnerException("fork original CodeSource type is invalid: " + workloadId);
        }
        if (!Files.isRegularFile(measured, LinkOption.NOFOLLOW_LINKS)
                || !expectedSha256.equals(sha256(Files.readAllBytes(measured)))) {
            throw new RunnerException(
                    "fork original CodeSource does not match retained bytes: " + workloadId);
        }
    }

    private static ForkSemanticReceipt readForkSemanticReceipt(
            final WorkloadPlan plan,
            final Path evidenceDirectory,
            final long processId) throws IOException, RunnerException {
        final Path receipt = evidenceDirectory.resolve(
                "semantic-fact-" + processId + ".json");
        if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("fork semantic receipt is missing: " + plan.workloadId());
        }
        final byte[] receiptBytes = Files.readAllBytes(receipt);
        final JsonNode root = new ObjectMapper().readTree(receiptBytes);
        final Set<String> expectedFields = Set.of(
                "schema", "workloadId", "processId", "scenario", "outcome", "completion",
                "changeCount", "changeTokens", "limitationCodes", "distinctInputs",
                "observedDecorator");
        final Set<String> actualFields = new java.util.HashSet<>();
        if (root != null && root.isObject()) {
            root.fieldNames().forEachRemaining(actualFields::add);
        }
        if (root == null || !root.isObject() || !actualFields.equals(expectedFields)
                || !"TFI_COMPARE_FORK_SEMANTIC_V1".equals(root.path("schema").textValue())
                || !plan.workloadId().equals(root.path("workloadId").textValue())
                || !root.path("processId").isIntegralNumber()
                || root.path("processId").longValue() != processId
                || !root.path("changeCount").isIntegralNumber()
                || !isTextArray(root.path("changeTokens"))
                || !isTextArray(root.path("limitationCodes"))
                || !root.path("distinctInputs").isBoolean()
                || !root.path("observedDecorator").isBoolean()) {
            throw new RunnerException(
                    "fork semantic receipt is invalid: " + plan.workloadId());
        }
        try {
            final List<String> changes = textValues(root.path("changeTokens"));
            final List<CompareLimitationCode> limitations = textValues(
                    root.path("limitationCodes")).stream()
                    .map(CompareLimitationCode::valueOf)
                    .toList();
            final CompareProductionBenchmarks.SemanticFact fact =
                    new CompareProductionBenchmarks.SemanticFact(
                            Scenario.valueOf(root.path("scenario").textValue()),
                            CompareOutcome.valueOf(root.path("outcome").textValue()),
                            CompareCompletion.valueOf(root.path("completion").textValue()),
                            root.path("changeCount").intValue(),
                            changes,
                            limitations,
                            root.path("distinctInputs").booleanValue(),
                            root.path("observedDecorator").booleanValue());
            if (fact.scenario() != plan.scenario()
                    || fact.changeCount() != fact.changeTokens().size()) {
                throw new IllegalArgumentException("semantic selection mismatch");
            }
            return new ForkSemanticReceipt(
                    processId,
                    receipt.toAbsolutePath().normalize().toUri().toString(),
                    sha256(receiptBytes),
                    fact);
        } catch (IllegalArgumentException exception) {
            throw new RunnerException(
                    "fork semantic receipt contains an unknown value: " + plan.workloadId(),
                    exception);
        }
    }

    private static boolean isTextArray(final JsonNode value) {
        if (!value.isArray()) {
            return false;
        }
        for (JsonNode element : value) {
            if (!element.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private static void requireForkDirectoryClosure(
            final Path evidenceDirectory,
            final Set<Long> processIds,
            final List<ForkCodeSource> sources) throws IOException, RunnerException {
        final Set<String> expectedRootFiles = new java.util.HashSet<>();
        expectedRootFiles.add("preimages");
        processIds.forEach(processId -> {
            expectedRootFiles.add("code-sources-" + processId + ".tsv");
            expectedRootFiles.add("semantic-fact-" + processId + ".json");
        });
        final Set<String> actualRootFiles = new java.util.HashSet<>();
        try (var paths = Files.list(evidenceDirectory)) {
            for (Path path : paths.toList()) {
                final String name = path.getFileName().toString();
                final boolean validType = "preimages".equals(name)
                        ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                if (!validType || !actualRootFiles.add(name)) {
                    throw new RunnerException(
                            "fork evidence directory contains an invalid child: "
                                    + evidenceDirectory);
                }
            }
        }
        if (!actualRootFiles.equals(expectedRootFiles)) {
            throw new RunnerException(
                    "fork evidence directory is not a closed set: " + evidenceDirectory);
        }
        final Set<String> expectedPreimages = new java.util.HashSet<>();
        for (ForkCodeSource source : sources) {
            expectedPreimages.add(Path.of(URI.create(source.retainedPreimage()))
                    .getFileName().toString());
        }
        requireExactChildren(
                evidenceDirectory.resolve("preimages"), expectedPreimages, false);
    }

    private static void requireForkClassClosure(
            final WorkloadPlan plan,
            final List<ForkCodeSource> sources) throws IOException, RunnerException {
        final Set<String> classNames = sources.stream()
                .map(ForkCodeSource::className)
                .collect(Collectors.toSet());
        final Set<String> expectedClasses = BenchmarkForkEvidence.benchmarkNestClasses().stream()
                .map(Class::getName)
                .collect(Collectors.toSet());
        try {
            BenchmarkForkEvidence.generatedHarnessClasses().stream()
                    .map(Class::getName)
                    .forEach(expectedClasses::add);
        } catch (URISyntaxException | ClassNotFoundException exception) {
            throw new RunnerException("cannot resolve generated JMH class closure", exception);
        }
        expectedClasses.add(BenchmarkForkEvidence.class.getName());
        expectedClasses.add(Scenario.class.getName());
        expectedClasses.add(CompareRuntime.class.getName());
        expectedClasses.add("com.syy.taskflowinsight.tracking.compare.CompareEngine");
        if (plan.scenario() == Scenario.OBSERVED_COMPARE) {
            expectedClasses.add(ObservedCompareOperations.class.getName());
        }
        if (!classNames.equals(expectedClasses)) {
            throw new RunnerException("fork CodeSource class closure is incomplete: "
                    + plan.workloadId());
        }
    }

    /**
     * 把 fork 内语义与 CodeSource 原始事实绑定到对应 JMH raw report。
     *
     * @param plan raw report 的唯一 workload 选择
     * @param report JMH 已完整关闭的 JSON 文件
     * @param semanticFact setup oracle 的精确语义事实
     * @param forkEvidence 实际测量 fork 写出的 PID 与 preimage 事实
     * @throws IOException raw report 无法安全读写时抛出
     * @throws RunnerException raw 与扩展事实选择不一致时抛出
     */
    static void enrichRawReport(
            final WorkloadPlan plan,
            final Path report,
            final CompareProductionBenchmarks.SemanticFact semanticFact,
            final ForkEvidence forkEvidence) throws IOException, RunnerException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(semanticFact, "semanticFact");
        Objects.requireNonNull(forkEvidence, "forkEvidence");
        if (semanticFact.scenario() != plan.scenario()
                || !forkEvidence.workloadId().equals(plan.workloadId())
                || forkEvidence.sources().isEmpty()) {
            throw new RunnerException("raw extension selection does not match workload: "
                    + plan.workloadId());
        }
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(report.toFile());
        if (root == null || !root.isArray() || root.size() != 1 || !root.get(0).isObject()) {
            throw invalidRawReport(plan);
        }
        final ObjectNode result = (ObjectNode) root.get(0);
        result.put("tfiEvidenceSchema", "TFI_COMPARE_JMH_V1");
        final ObjectNode semantic = result.putObject("tfiSemantic");
        semantic.put("scenario", semanticFact.scenario().name());
        semantic.put("outcome", semanticFact.outcome().name());
        semantic.put("completion", semanticFact.completion().name());
        semantic.put("changeCount", semanticFact.changeCount());
        final ArrayNode changeTokens = semantic.putArray("changeTokens");
        semanticFact.changeTokens().forEach(changeTokens::add);
        final ArrayNode limitations = semantic.putArray("limitationCodes");
        semanticFact.limitationCodes().forEach(code -> limitations.add(code.name()));
        semantic.put("distinctInputs", semanticFact.distinctInputs());
        semantic.put("observedDecorator", semanticFact.observedDecorator());
        final ArrayNode semanticReceipts = semantic.putArray("forkReceipts");
        forkEvidence.semanticReceipts().stream()
                .sorted(Comparator.comparingLong(ForkSemanticReceipt::processId))
                .forEach(receipt -> {
                    final ObjectNode row = semanticReceipts.addObject();
                    row.put("processId", receipt.processId());
                    row.put("receipt", receipt.receipt());
                    row.put("sha256", receipt.sha256());
                });

        final ArrayNode codeSources = result.putArray("tfiForkCodeSources");
        for (ForkCodeSource source : forkEvidence.sources()) {
            final ObjectNode row = codeSources.addObject();
            row.put("workloadId", source.workloadId());
            row.put("processId", source.processId());
            row.put("className", source.className());
            row.put("originalLocation", source.originalLocation());
            row.put("retainedPreimage", source.retainedPreimage());
            row.put("digestKind", source.digestKind());
            row.put("sha256", source.sha256());
        }
        Files.writeString(
                report,
                mapper.writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
        requireRawExtensions(plan, report, semanticFact, forkEvidence);
    }

    private static void requireRawExtensions(
            final WorkloadPlan plan,
            final Path report,
            final CompareProductionBenchmarks.SemanticFact expectedSemantic,
            final ForkEvidence expectedForkEvidence)
            throws IOException, RunnerException {
        final JsonNode result = new ObjectMapper().readTree(report.toFile()).path(0);
        final JsonNode semantic = result.path("tfiSemantic");
        final JsonNode semanticReceipts = semantic.path("forkReceipts");
        final JsonNode codeSources = result.path("tfiForkCodeSources");
        if (!"TFI_COMPARE_JMH_V1".equals(result.path("tfiEvidenceSchema").textValue())
                || !plan.scenario().name().equals(semantic.path("scenario").textValue())
                || !expectedSemantic.outcome().name()
                        .equals(semantic.path("outcome").textValue())
                || !expectedSemantic.completion().name()
                        .equals(semantic.path("completion").textValue())
                || expectedSemantic.changeCount() != semantic.path("changeCount").intValue()
                || !semantic.path("changeTokens").isArray()
                || !semantic.path("limitationCodes").isArray()
                || !textValues(semantic.path("changeTokens"))
                        .equals(expectedSemantic.changeTokens())
                || !textValues(semantic.path("limitationCodes")).equals(
                        expectedSemantic.limitationCodes().stream().map(Enum::name).toList())
                || expectedSemantic.distinctInputs()
                        != semantic.path("distinctInputs").booleanValue()
                || expectedSemantic.observedDecorator()
                        != semantic.path("observedDecorator").booleanValue()
                || !semanticReceipts.isArray()
                || semanticReceipts.size() != expectedForkEvidence.semanticReceipts().size()
                || !codeSources.isArray()
                || codeSources.size() != expectedForkEvidence.sources().size()) {
            throw new RunnerException("raw report extension is incomplete: "
                    + plan.workloadId());
        }
        final List<ForkSemanticReceipt> orderedReceipts = expectedForkEvidence.semanticReceipts()
                .stream()
                .sorted(Comparator.comparingLong(ForkSemanticReceipt::processId))
                .toList();
        for (int index = 0; index < semanticReceipts.size(); index++) {
            final JsonNode actual = semanticReceipts.get(index);
            final ForkSemanticReceipt expected = orderedReceipts.get(index);
            if (expected.processId() != actual.path("processId").longValue()
                    || !expected.receipt().equals(actual.path("receipt").textValue())
                    || !expected.sha256().equals(actual.path("sha256").textValue())) {
                throw new RunnerException("raw fork semantic receipt is invalid: "
                        + plan.workloadId());
            }
        }
        for (int index = 0; index < codeSources.size(); index++) {
            final JsonNode source = codeSources.get(index);
            final ForkCodeSource expected = expectedForkEvidence.sources().get(index);
            if (!expected.workloadId().equals(source.path("workloadId").textValue())
                    || expected.processId() != source.path("processId").longValue()
                    || !expected.className().equals(source.path("className").textValue())
                    || !expected.originalLocation()
                            .equals(source.path("originalLocation").textValue())
                    || !expected.retainedPreimage()
                            .equals(source.path("retainedPreimage").textValue())
                    || !expected.digestKind().equals(source.path("digestKind").textValue())
                    || !expected.sha256().equals(source.path("sha256").textValue())) {
                throw new RunnerException("raw fork CodeSource is invalid: "
                        + plan.workloadId());
            }
        }
    }

    private static List<String> textValues(final JsonNode array) {
        final List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.textValue()));
        return List.copyOf(values);
    }

    /**
     * 从真实 JMH 返回值提取未舍入的 p99 与每操作分配量，并核对 raw JSON 已落盘。
     *
     * @param plan 本次预期的 scenario/thread 选择
     * @param report JMH 本次写出的 JSON 文件
     * @param results JMH 内存结果
     * @return 经选择事实、单位和数值完整性校验后的测量值
     * @throws IOException 无法检查报告文件时抛出
     * @throws RunnerException 报告或 JMH 结果不能形成完整证据时抛出
     */
    static WorkloadMeasurement requireMeasurement(
            WorkloadPlan plan,
            Path report,
            Collection<RunResult> results) throws IOException, RunnerException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(results, "results");
        if (!Files.isRegularFile(report) || Files.size(report) == 0L) {
            throw new RunnerException("workload requires a non-empty raw JSON report: "
                    + plan.workloadId());
        }
        if (results.size() != 1) {
            throw new RunnerException("workload requires exactly one JMH result: "
                    + plan.workloadId());
        }

        WorkloadMeasurement rawMeasurement = readRawMeasurement(plan, report);
        RunResult runResult = results.iterator().next();
        String actualScenario = runResult.getParams().getParam("scenario");
        if (!BENCHMARK_NAME.equals(runResult.getParams().getBenchmark())
                || plan.threads() != runResult.getParams().getThreads()
                || !plan.scenario().name().equals(actualScenario)) {
            throw new RunnerException("JMH result selection does not match workload: "
                    + plan.workloadId());
        }

        Result<?> primary = runResult.getPrimaryResult();
        if (!"ns/op".equals(primary.getScoreUnit())) {
            throw new RunnerException("JMH primary result must use ns/op: " + plan.workloadId());
        }
        double p99Nanos = primary.getStatistics().getPercentile(99.0d);
        long sampleCount = primary.getSampleCount();
        if (!Double.isFinite(p99Nanos) || p99Nanos < 0.0d || sampleCount <= 0L) {
            throw new RunnerException("JMH p99 and sample count must be finite and positive: "
                    + plan.workloadId());
        }

        Result<?> allocation = runResult.getSecondaryResults().get("gc.alloc.rate.norm");
        if (allocation == null || !"B/op".equals(allocation.getScoreUnit())) {
            throw new RunnerException("JMH result requires gc.alloc.rate.norm in B/op: "
                    + plan.workloadId());
        }
        double allocationBytesPerOperation = allocation.getScore();
        if (!Double.isFinite(allocationBytesPerOperation)
                || allocationBytesPerOperation < 0.0d) {
            throw new RunnerException("JMH allocation must be finite and non-negative: "
                    + plan.workloadId());
        }
        if (Double.compare(rawMeasurement.p99Nanos(), p99Nanos) != 0
                || Double.compare(
                        rawMeasurement.allocationBytesPerOperation(),
                        allocationBytesPerOperation) != 0
                || rawMeasurement.sampleCount() != sampleCount) {
            throw new RunnerException("raw JMH report does not match in-memory result: "
                    + plan.workloadId());
        }
        return rawMeasurement;
    }

    private static WorkloadMeasurement readRawMeasurement(WorkloadPlan plan, Path report)
            throws RunnerException {
        final JsonNode root;
        try {
            root = new ObjectMapper().readTree(report.toFile());
        } catch (IOException exception) {
            throw invalidRawReport(plan);
        }
        if (root == null || !root.isArray() || root.size() != 1 || !root.get(0).isObject()) {
            throw invalidRawReport(plan);
        }
        JsonNode result = root.get(0);
        JsonNode scenario = result.path("params").path("scenario");
        JsonNode threads = result.path("threads");
        if (!result.path("benchmark").isTextual()
                || !BENCHMARK_NAME.equals(result.path("benchmark").textValue())
                || !scenario.isTextual()
                || !plan.scenario().name().equals(scenario.textValue())
                || !threads.isIntegralNumber()
                || threads.intValue() != plan.threads()) {
            throw invalidRawReport(plan);
        }

        JsonNode primary = result.path("primaryMetric");
        JsonNode p99 = primary.path("scorePercentiles").path("99.0");
        JsonNode allocation = result.path("secondaryMetrics").path("gc.alloc.rate.norm");
        JsonNode allocationScore = allocation.path("score");
        if (!"ns/op".equals(primary.path("scoreUnit").textValue())
                || !p99.isNumber()
                || !"B/op".equals(allocation.path("scoreUnit").textValue())
                || !allocationScore.isNumber()) {
            throw invalidRawReport(plan);
        }
        double p99Nanos = p99.doubleValue();
        double allocationBytesPerOperation = allocationScore.doubleValue();
        long sampleCount = rawSampleCount(primary.path("rawDataHistogram"), plan);
        if (!Double.isFinite(p99Nanos)
                || p99Nanos < 0.0d
                || !Double.isFinite(allocationBytesPerOperation)
                || allocationBytesPerOperation < 0.0d
                || sampleCount <= 0L) {
            throw invalidRawReport(plan);
        }
        return new WorkloadMeasurement(
                plan.workloadId(), plan.scenario(), plan.threads(), report,
                p99Nanos, allocationBytesPerOperation, sampleCount);
    }

    private static long rawSampleCount(JsonNode histogram, WorkloadPlan plan)
            throws RunnerException {
        if (!histogram.isArray() || histogram.isEmpty()) {
            throw invalidRawReport(plan);
        }
        long total = 0L;
        try {
            for (JsonNode fork : histogram) {
                if (!fork.isArray() || fork.isEmpty()) {
                    throw invalidRawReport(plan);
                }
                for (JsonNode iteration : fork) {
                    if (!iteration.isArray()) {
                        throw invalidRawReport(plan);
                    }
                    for (JsonNode bucket : iteration) {
                        if (!bucket.isArray()
                                || bucket.size() != 2
                                || !bucket.get(1).isIntegralNumber()
                                || bucket.get(1).longValue() < 0L) {
                            throw invalidRawReport(plan);
                        }
                        total = Math.addExact(total, bucket.get(1).longValue());
                    }
                }
            }
        } catch (ArithmeticException exception) {
            throw invalidRawReport(plan);
        }
        return total;
    }

    private static RunnerException invalidRawReport(WorkloadPlan plan) {
        return new RunnerException("raw JMH report is incomplete or inconsistent: "
                + plan.workloadId());
    }

    /**
     * 只有完整 21-workload 闭集才写汇总证据，避免部分运行被误认为完整基准。
     *
     * @param outputDirectory 四份确定性 TSV 的目录
     * @param measurements 已通过 raw JMH 校验的测量
     * @param semanticFacts 七个 fixture 的真实 Compare 语义事实
     * @param codeSources 实际加载类的 CodeSource 摘要
     * @param forkEvidence 21 个 workload 的实际 fork PID/CodeSource 证据
     * @throws IOException 写证据失败时抛出
     * @throws RunnerException 任一闭集缺失、重复或选择事实漂移时抛出
     */
    static void writeEvidence(
            Path outputDirectory,
            List<WorkloadMeasurement> measurements,
            List<CompareProductionBenchmarks.SemanticFact> semanticFacts,
            List<CodeSourceEvidence> codeSources,
            List<ForkEvidence> forkEvidence) throws IOException, RunnerException {
        Map<String, WorkloadMeasurement> measurementById = new HashMap<>();
        for (WorkloadMeasurement measurement : measurements) {
            if (measurementById.put(measurement.workloadId(), measurement) != null) {
                throw new RunnerException("duplicate workload measurement: "
                        + measurement.workloadId());
            }
        }
        List<WorkloadPlan> plans = workloadPlans();
        if (measurementById.size() != plans.size()) {
            throw new RunnerException("evidence requires the exact 21-workload closure");
        }
        for (WorkloadPlan plan : plans) {
            WorkloadMeasurement measurement = measurementById.get(plan.workloadId());
            if (measurement == null
                    || measurement.scenario() != plan.scenario()
                    || measurement.threads() != plan.threads()) {
                throw new RunnerException("evidence requires the exact 21-workload closure");
            }
        }

        Map<Scenario, CompareProductionBenchmarks.SemanticFact> factByScenario = new HashMap<>();
        for (CompareProductionBenchmarks.SemanticFact fact : semanticFacts) {
            if (factByScenario.put(fact.scenario(), fact) != null) {
                throw new RunnerException("duplicate semantic fact: " + fact.scenario());
            }
        }
        if (!factByScenario.keySet().equals(Set.of(Scenario.values()))) {
            throw new RunnerException("semantic evidence requires all seven scenarios");
        }
        if (semanticFacts.stream().anyMatch(fact ->
                fact.changeCount() != fact.changeTokens().size()
                        || fact.changeTokens().stream().anyMatch(
                                token -> token.isEmpty() || !isTsvSafe(token)))) {
            throw new RunnerException("semantic evidence requires exact TSV-safe change paths");
        }

        Map<String, ForkEvidence> forkEvidenceById = new HashMap<>();
        for (ForkEvidence evidence : forkEvidence) {
            final Set<Long> sourceProcessIds = evidence.sources().stream()
                    .map(ForkCodeSource::processId)
                    .collect(Collectors.toSet());
            final Set<Long> receiptProcessIds = evidence.semanticReceipts().stream()
                    .map(ForkSemanticReceipt::processId)
                    .collect(Collectors.toSet());
            if (forkEvidenceById.put(evidence.workloadId(), evidence) != null
                    || evidence.sources().isEmpty()
                    || evidence.semanticReceipts().isEmpty()
                    || !sourceProcessIds.equals(receiptProcessIds)
                    || evidence.semanticReceipts().stream().anyMatch(receipt ->
                            !receipt.semanticFact().equals(evidence.semanticFact()))
                    || evidence.sources().stream().anyMatch(source ->
                            !source.workloadId().equals(evidence.workloadId()))) {
                throw new RunnerException("fork evidence contains duplicate or invalid workload");
            }
        }
        if (!forkEvidenceById.keySet().equals(measurementById.keySet())) {
            throw new RunnerException("fork evidence requires the exact 21-workload closure");
        }

        Set<String> codeSourceClasses = codeSources.stream()
                .map(CodeSourceEvidence::className)
                .collect(Collectors.toSet());
        if (codeSources.isEmpty() || codeSourceClasses.size() != codeSources.size()
                || codeSources.stream().anyMatch(source ->
                        !source.className().matches("[A-Za-z_$][A-Za-z0-9_.$]*")
                                || !Set.of("ARTIFACT", "CLASS_BYTES").contains(source.digestKind())
                                || source.preimage().length == 0
                                || !source.sha256().matches("[0-9a-f]{64}")
                                || !source.sha256().equals(sha256(source.preimage())))) {
            throw new RunnerException("CodeSource evidence must be non-empty and uniquely hashed");
        }

        List<String> measurementRows = new ArrayList<>();
        measurementRows.add("workloadId\tscenario\tthreads\trawReport\tp99Nanos"
                + "\tgcAllocRateNormBytesPerOp\tsampleCount");
        List<String> semanticRows = new ArrayList<>();
        semanticRows.add("workloadId\tscenario\tthreads\toutcome\tcompletion\tchangeCount"
                + "\tchangeTokens\tlimitationCodes\tdistinctInputs\tobservedDecorator");
        List<String> forkRows = new ArrayList<>();
        forkRows.add("workloadId\tprocessId\tclassName\toriginalLocation"
                + "\tretainedPreimage\tdigestKind\tsha256");
        for (WorkloadPlan plan : plans) {
            WorkloadMeasurement measurement = measurementById.get(plan.workloadId());
            Path expectedReport = outputDirectory.resolve("raw")
                    .resolve(plan.workloadId() + ".json")
                    .toAbsolutePath().normalize();
            if (!measurement.rawReport().toAbsolutePath().normalize().equals(expectedReport)) {
                throw new RunnerException("raw report path is outside the evidence closure: "
                        + plan.workloadId());
            }
            WorkloadMeasurement rawMeasurement = readRawMeasurement(
                    plan, measurement.rawReport());
            if (Double.compare(rawMeasurement.p99Nanos(), measurement.p99Nanos()) != 0
                    || Double.compare(
                            rawMeasurement.allocationBytesPerOperation(),
                            measurement.allocationBytesPerOperation()) != 0
                    || rawMeasurement.sampleCount() != measurement.sampleCount()) {
                throw new RunnerException("raw report does not match summary evidence: "
                        + plan.workloadId());
            }
            measurementRows.add(String.join("\t",
                    measurement.workloadId(), measurement.scenario().name(),
                    Integer.toString(measurement.threads()),
                    measurement.rawReport().toAbsolutePath().normalize().toUri().toString(),
                    Double.toString(measurement.p99Nanos()),
                    Double.toString(measurement.allocationBytesPerOperation()),
                    Long.toString(measurement.sampleCount())));

            ForkEvidence workloadForkEvidence = forkEvidenceById.get(plan.workloadId());
            CompareProductionBenchmarks.SemanticFact fact = workloadForkEvidence.semanticFact();
            if (!fact.equals(factByScenario.get(plan.scenario()))) {
                throw new RunnerException("fork semantic evidence does not match parent oracle: "
                        + plan.workloadId());
            }
            String limitationCodes = fact.limitationCodes().isEmpty()
                    ? "-"
                    : String.join(",", fact.limitationCodes().stream()
                            .map(Enum::name)
                            .toList());
            semanticRows.add(String.join("\t",
                    plan.workloadId(), plan.scenario().name(), Integer.toString(plan.threads()),
                    fact.outcome().name(), fact.completion().name(),
                    Integer.toString(fact.changeCount()),
                    fact.changeTokens().isEmpty() ? "-" : String.join(";", fact.changeTokens()),
                    limitationCodes,
                    Boolean.toString(fact.distinctInputs()),
                    Boolean.toString(fact.observedDecorator())));
            requireRawExtensions(plan, measurement.rawReport(), fact, workloadForkEvidence);
            workloadForkEvidence.sources().stream()
                    .sorted(Comparator.comparingLong(ForkCodeSource::processId)
                            .thenComparing(ForkCodeSource::className))
                    .map(ForkCodeSource::toTsv)
                    .forEach(forkRows::add);
        }
        requireExactChildren(
                outputDirectory.resolve("raw"),
                plans.stream().map(plan -> plan.workloadId() + ".json")
                        .collect(Collectors.toSet()),
                false);
        requireExactChildren(
                outputDirectory.resolve("fork-evidence"),
                plans.stream().map(WorkloadPlan::workloadId).collect(Collectors.toSet()),
                true);

        Path codeSourceDirectory = outputDirectory.resolve("code-sources");
        recreateDirectory(codeSourceDirectory, outputDirectory);
        List<String> codeSourceRows = new ArrayList<>();
        codeSourceRows.add("className\toriginalLocation\tretainedPreimage\tdigestKind\tsha256");
        List<CodeSourceEvidence> orderedCodeSources = codeSources.stream()
                .sorted(Comparator.comparing(CodeSourceEvidence::className))
                .toList();
        for (CodeSourceEvidence source : orderedCodeSources) {
            String suffix = "ARTIFACT".equals(source.digestKind()) ? ".jar" : ".class";
            Path retained = codeSourceDirectory.resolve(
                    source.className().replace('.', '-') + suffix);
            Files.write(retained, source.preimage());
            codeSourceRows.add(String.join("\t", source.className(), source.location(),
                    retained.toAbsolutePath().normalize().toUri().toString(),
                    source.digestKind(), source.sha256()));
        }
        Files.createDirectories(outputDirectory);
        writeTsv(outputDirectory.resolve("measurements.tsv"), measurementRows);
        writeTsv(outputDirectory.resolve("semantic-facts.tsv"), semanticRows);
        writeTsv(outputDirectory.resolve("code-sources.tsv"), codeSourceRows);
        writeTsv(outputDirectory.resolve("fork-code-sources.tsv"), forkRows);
    }

    private static boolean isTsvSafe(final String value) {
        return value.indexOf('\t') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    private static void requireExactChildren(
            final Path directory,
            final Set<String> expectedNames,
            final boolean directories) throws IOException, RunnerException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("evidence closure directory is missing: " + directory);
        }
        final Set<String> actualNames = new java.util.HashSet<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                final boolean expectedType = directories
                        ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                if (!expectedType || !actualNames.add(path.getFileName().toString())) {
                    throw new RunnerException("evidence closure contains an invalid child: "
                            + directory);
                }
            }
        }
        if (!actualNames.equals(expectedNames)) {
            throw new RunnerException("evidence closure contains missing or extra children: "
                    + directory);
        }
    }

    /** @return 父 runner、完整 benchmark nest、Compare runtime 与 observed decorator 的加载证据 */
    static List<CodeSourceEvidence> captureCodeSources() throws IOException, RunnerException {
        Set<Class<?>> evidenceTypes = new java.util.LinkedHashSet<>();
        evidenceTypes.addAll(List.of(CompareProductionBenchmarkRunner.class.getNestMembers()));
        evidenceTypes.addAll(BenchmarkForkEvidence.benchmarkNestClasses());
        evidenceTypes.add(BenchmarkForkEvidence.class);
        evidenceTypes.add(CompareRuntime.class);
        evidenceTypes.add(ObservedCompareOperations.class);
        List<CodeSourceEvidence> evidence = new ArrayList<>(evidenceTypes.size());
        for (Class<?> type : evidenceTypes) {
            evidence.add(captureCodeSource(type));
        }
        return List.copyOf(evidence);
    }

    private static CodeSourceEvidence captureCodeSource(Class<?> type)
            throws IOException, RunnerException {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new RunnerException("missing CodeSource for " + type.getName());
        }
        String location = codeSource.getLocation().toExternalForm();
        final Path sourcePath;
        try {
            sourcePath = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new RunnerException("CodeSource is not a readable file URI for "
                    + type.getName(), exception);
        }
        if (Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            byte[] preimage = Files.readAllBytes(sourcePath);
            return new CodeSourceEvidence(
                    type.getName(), location, "ARTIFACT", sha256(preimage), preimage);
        }
        final Path classFile = sourcePath.resolve(
                type.getName().replace('.', '/') + ".class").normalize();
        if (!Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)
                || !classFile.startsWith(sourcePath)
                || !Files.isRegularFile(classFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("missing class bytes for " + type.getName());
        }
        byte[] preimage = Files.readAllBytes(classFile);
        return new CodeSourceEvidence(
                type.getName(), location, "CLASS_BYTES", sha256(preimage), preimage);
    }

    private static String sha256(byte[] preimage) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
        return HexFormat.of().formatHex(digest.digest(preimage));
    }

    static void writeSuccessMarker(final Path outputDirectory)
            throws IOException, RunnerException {
        final Set<String> expectedChildren = Set.of(
                "raw",
                "fork-evidence",
                "code-sources",
                "measurements.tsv",
                "semantic-facts.tsv",
                "code-sources.tsv",
                "fork-code-sources.tsv");
        final Set<String> actualChildren = new java.util.HashSet<>();
        try (var paths = Files.list(outputDirectory)) {
            for (Path path : paths.toList()) {
                final String name = path.getFileName().toString();
                final boolean validType = Set.of("raw", "fork-evidence", "code-sources")
                        .contains(name)
                        ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                if (!validType || !actualChildren.add(name)) {
                    throw new RunnerException(
                            "evidence root contains an invalid child before success: "
                                    + outputDirectory);
                }
            }
        }
        if (!actualChildren.equals(expectedChildren)) {
            throw new RunnerException(
                    "evidence root is incomplete before success: " + outputDirectory);
        }

        final List<String> rows = new ArrayList<>();
        rows.add("schema\tTFI_COMPARE_JMH_V1");
        rows.add("workloadCount\t" + workloadPlans().size());
        rows.add("entryType\trelativePath\tsha256");
        rows.addAll(evidenceTreeRows(outputDirectory));
        final Path pending = outputDirectory.resolve("_SUCCESS.tmp");
        final Path marker = outputDirectory.resolve("_SUCCESS");
        boolean published = false;
        try {
            Files.writeString(
                    pending,
                    String.join("\n", rows) + "\n",
                    StandardCharsets.UTF_8);
            Files.move(pending, marker, StandardCopyOption.ATOMIC_MOVE);
            published = true;
            verifySuccessMarker(outputDirectory);
        } catch (IOException | RunnerException exception) {
            try {
                Files.deleteIfExists(pending);
                if (published) {
                    Files.deleteIfExists(marker);
                }
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    /**
     * 独立复算成功 marker 与当前证据树，拒绝缺失、额外、替换或类型漂移的任一条目。
     */
    static void verifySuccessMarker(final Path outputDirectory)
            throws IOException, RunnerException {
        final Path marker = outputDirectory.resolve("_SUCCESS");
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("success marker is missing or is not a regular file");
        }
        final List<String> rows = Files.readAllLines(marker, StandardCharsets.UTF_8);
        if (rows.size() < 4
                || !"schema\tTFI_COMPARE_JMH_V1".equals(rows.get(0))
                || !("workloadCount\t" + workloadPlans().size()).equals(rows.get(1))
                || !"entryType\trelativePath\tsha256".equals(rows.get(2))) {
            throw new RunnerException("success marker header is invalid");
        }
        final List<String> declaredTree = rows.subList(3, rows.size());
        if (!declaredTree.equals(evidenceTreeRows(outputDirectory))) {
            throw new RunnerException("success marker does not match evidence tree");
        }
    }

    private static List<String> evidenceTreeRows(final Path outputDirectory)
            throws IOException, RunnerException {
        final Path root = outputDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new RunnerException("evidence root is missing or is not a directory");
        }
        final Path marker = root.resolve("_SUCCESS");
        final Path pending = root.resolve("_SUCCESS.tmp");
        final List<String> rows = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                final Path normalized = path.toAbsolutePath().normalize();
                if (normalized.equals(root) || normalized.equals(marker)) {
                    continue;
                }
                if (normalized.equals(pending)) {
                    throw new RunnerException("pending success marker remains in evidence tree");
                }
                final String relative = root.relativize(normalized).toString().replace('\\', '/');
                if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                    rows.add("directory\t" + relative + "\t-");
                } else if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                    rows.add("file\t" + relative + "\t"
                            + sha256(Files.readAllBytes(normalized)));
                } else {
                    throw new RunnerException("evidence tree contains a symbolic or special entry: "
                            + relative);
                }
            }
        }
        return rows.stream().sorted().toList();
    }

    private static void writeTsv(Path path, List<String> rows) throws IOException {
        Files.writeString(path, String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
    }

    /** 单次 JMH 运行的不可变选择事实。 */
    record WorkloadPlan(
            /** 跨报告稳定且唯一的 workload 标识。 */ String workloadId,
            /** 本次运行唯一的结构化场景。 */ Scenario scenario,
            /** JMH 实际 worker thread 数。 */ int threads) {
    }

    /** 单次 workload raw report 中可供后续政策裁决的原始测量摘要。 */
    record WorkloadMeasurement(
            /** 对应的稳定 workload 标识。 */ String workloadId,
            /** raw report 中实际选择的固定场景。 */ Scenario scenario,
            /** raw report 中实际使用的 JMH worker 数。 */ int threads,
            /** JMH 保存的原始 JSON 文件。 */ Path rawReport,
            /** SampleTime 原始统计中的 99 百分位，单位 ns/op。 */ double p99Nanos,
            /** GC profiler 报告的每操作分配字节数，单位 B/op。 */ double allocationBytesPerOperation,
            /** 用于计算 SampleTime 分布的样本数。 */ long sampleCount) {
    }

    /** 一个 workload 的全部实际 JMH fork CodeSource 行。 */
    record ForkEvidence(
            /** 与 scenario/thread 计划唯一对应的 workload ID。 */ String workloadId,
            /** 全部 fork receipt 一致且已与父进程 oracle 核对的语义。 */
            CompareProductionBenchmarks.SemanticFact semanticFact,
            /** 每个 fork 自己写出的语义 receipt 路径与摘要。 */
            List<ForkSemanticReceipt> semanticReceipts,
            /** 按 fork manifest 与 class 名稳定排序的实际加载证据。 */
            List<ForkCodeSource> sources) {

        ForkEvidence {
            semanticFact = Objects.requireNonNull(semanticFact, "semanticFact");
            semanticReceipts = List.copyOf(semanticReceipts);
            sources = List.copyOf(sources);
        }
    }

    /** fork 内 setup oracle 写出的原始语义 receipt。 */
    record ForkSemanticReceipt(
            /** 写 receipt 的实际 JMH fork PID。 */ long processId,
            /** 保留在 workload 闭集中的 JSON receipt URI。 */ String receipt,
            /** receipt 原始 JSON bytes 的 SHA-256。 */ String sha256,
            /** 从 receipt 解析出的精确 Compare 语义。 */
            CompareProductionBenchmarks.SemanticFact semanticFact) {
    }

    /** fork 内单个实际加载 class 对应的 retained preimage。 */
    record ForkCodeSource(
            /** 当前 fork 所属 workload ID。 */ String workloadId,
            /** 与父 runner 不同的实际 fork PID。 */ long processId,
            /** fork 中已加载或显式解析的 class 全名。 */ String className,
            /** fork ProtectionDomain 报告的原始 CodeSource。 */ String originalLocation,
            /** fork 自身写出的 exact preimage URI。 */ String retainedPreimage,
            /** 完整 JAR 或单个 class bytes。 */ String digestKind,
            /** retained preimage 的 SHA-256。 */ String sha256) {

        private String toTsv() {
            return String.join("\t",
                    workloadId,
                    Long.toString(processId),
                    className,
                    originalLocation,
                    retainedPreimage,
                    digestKind,
                    sha256);
        }
    }

    /** 一个实际加载类的 CodeSource 路径与可复算摘要。 */
    record CodeSourceEvidence(
            /** 被核对的类全名。 */ String className,
            /** ProtectionDomain 报告的原始 CodeSource URI。 */ String location,
            /** 摘要对象类型：完整制品或目录中的 class bytes。 */ String digestKind,
            /** 摘要对象的 SHA-256 小写十六进制。 */ String sha256,
            /** 写入证据闭集的实际摘要对象 bytes。 */ byte[] preimage) {

        CodeSourceEvidence {
            preimage = Objects.requireNonNull(preimage, "preimage").clone();
        }

        @Override
        public byte[] preimage() {
            return preimage.clone();
        }
    }
}
