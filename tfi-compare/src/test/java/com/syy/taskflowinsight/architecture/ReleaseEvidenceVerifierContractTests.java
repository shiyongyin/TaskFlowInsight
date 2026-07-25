package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 发布证据 verifier 的 CLI、安全解析和只读完整性合同。 */
class ReleaseEvidenceVerifierContractTests {
    private static final String BASELINE_MANIFEST_SHA256 =
            "3c2badbdb56559c6a1503a92e05e7f643c199c9eea2eb6ea5c702814cc635fa6";

    /** 每个 verifier 反例使用独立 evidence root，避免 manifest 相互污染。 */
    @TempDir
    Path temporaryDirectory;

    @Test
    void unknownModeUsesCliExitCode() throws Exception {
        ProcessResult result = run("unknown-mode");

        assertThat(result.exitCode()).isEqualTo(64);
        assertThat(result.output()).contains("unknown mode: unknown-mode");
        assertThat(result.output()).contains("Usage: ReleaseEvidenceVerifier");
    }

    @Test
    void preparedMarkerRejectsNonAuthorityBaselineHash() throws Exception {
        Fixture fixture = preparedFixture("wrong-baseline-authority", "c".repeat(64), false);

        ProcessResult result = run(
                "verify-integrity",
                fixture.evidence().toString(),
                fixture.expectedReports().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("baseline authority SHA is invalid");
    }

    @Test
    void integrityRejectsMissingCandidateArtifactClosure() throws Exception {
        Fixture fixture = preparedFixture(
                "missing-candidate-artifacts", BASELINE_MANIFEST_SHA256, false);

        ProcessResult result = run(
                "verify-integrity",
                fixture.evidence().toString(),
                fixture.expectedReports().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("candidate artifact manifest");
    }

    @Test
    void preparedMarkerRequiresTerminalLf() throws Exception {
        Fixture fixture = preparedFixture("marker-without-terminal-lf", BASELINE_MANIFEST_SHA256, true);
        Path marker = fixture.evidence().resolve("PREPARED");
        String content = Files.readString(marker, StandardCharsets.UTF_8);
        Files.writeString(marker, content.substring(0, content.length() - 1), StandardCharsets.UTF_8);
        rewriteManifest(fixture.evidence());

        ProcessResult result = run(
                "verify-integrity",
                fixture.evidence().toString(),
                fixture.expectedReports().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("evidence marker must end with one LF");
    }

    @Test
    void integrityRejectsExpectedReportsDifferentFromRetainedAuthority() throws Exception {
        Fixture fixture = preparedFixture(
                "expected-reports-authority-drift", BASELINE_MANIFEST_SHA256, true);
        Files.writeString(
                fixture.expectedReports(),
                "phase\tmodule\treportPath\tminimumTests\tallowSkipped\n",
                StandardCharsets.UTF_8);

        ProcessResult result = run(
                "verify-integrity",
                fixture.evidence().toString(),
                fixture.expectedReports().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("expected reports authority differs from retained bytes");
    }

    @Test
    void verifyAllLoadsPolicyFromRetainedCanonicalPath() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("verify-all-policy-source"));
        Path reports = temporaryDirectory.resolve("verify-all-reports.tsv");
        Files.writeString(reports,
                "phase\tmodule\treportPath\tminimumTests\tallowSkipped\n",
                StandardCharsets.UTF_8);

        ProcessResult result = run("verify-all", evidence.toString(), reports.toString());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("retained production policy");
    }

    @Test
    void commandLedgerRejectsPlaceholderDriftAcrossRows() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("command-placeholder-drift"));
        writeCommandEvidence(evidence);

        ProcessResult accepted = runProbe("commands", evidence.toString());
        assertThat(accepted.exitCode()).as(accepted.output()).isZero();

        Path ledger = evidence.resolve("metadata/commands.tsv");
        List<String> rows = new ArrayList<>(Files.readAllLines(ledger, StandardCharsets.UTF_8));
        for (int index = 1; index < rows.size(); index++) {
            if (rows.get(index).contains("4.0.0")) {
                rows.set(index, rows.get(index).replace("4.0.0", "4.0.1"));
                break;
            }
        }
        Files.write(ledger, rows, StandardCharsets.UTF_8);

        ProcessResult rejected = runProbe("commands", evidence.toString());
        assertThat(rejected.exitCode()).isNotZero();
        assertThat(rejected.output()).contains("command placeholder binding changed between rows");
    }

    @Test
    void codeSourceRejectsActualJarDigestDrift() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("codesource-digest-drift"));
        String className = "com.syy.taskflowinsight.tracking.compare.CompareEngine";
        String repositoryPath = "com/syy/tfi-compare/4.0.0/tfi-compare-4.0.0.jar";
        String expectedSha = "a".repeat(64);
        Path codeSource = evidence.resolve("codesource.tsv");
        Files.write(codeSource, List.of(
                "className\trepositoryPath\tactualSha256\texpectedSha256\tstatus",
                className + "\t" + repositoryPath + "\t" + expectedSha + "\t"
                        + expectedSha + "\tPASS"), StandardCharsets.UTF_8);

        ProcessResult accepted = runProbe(
                "codesource", evidence.toString(), "codesource.tsv", className,
                repositoryPath, expectedSha, "4.0.0");
        assertThat(accepted.exitCode()).as(accepted.output()).isZero();

        List<String> rows = new ArrayList<>(Files.readAllLines(codeSource, StandardCharsets.UTF_8));
        rows.set(1, rows.get(1).replaceFirst(expectedSha, "b".repeat(64)));
        Files.write(codeSource, rows, StandardCharsets.UTF_8);

        ProcessResult rejected = runProbe(
                "codesource", evidence.toString(), "codesource.tsv", className,
                repositoryPath, expectedSha, "4.0.0");
        assertThat(rejected.exitCode()).isNotZero();
        assertThat(rejected.output()).contains("artifact CodeSource row is invalid");
    }

    @Test
    void performanceMarkerRejectsRawWorkloadDigestDrift() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("performance-digest-drift"));
        Path rawWorkload = writePerformanceEvidence(evidence);

        ProcessResult accepted = runProbe("performance", evidence.toString());
        assertThat(accepted.exitCode()).as(accepted.output()).isZero();

        Files.writeString(rawWorkload, "{\"drift\":true}\n", StandardCharsets.UTF_8);

        ProcessResult rejected = runProbe("performance", evidence.toString());
        assertThat(rejected.exitCode()).isNotZero();
        assertThat(rejected.output()).contains("production performance tree SHA differs");
    }

    private static void writeCommandEvidence(Path evidence) throws Exception {
        Path root = repositoryRoot();
        Path authority = root.resolve("scripts/release-evidence/expected-commands.tsv");
        Path retained = evidence.resolve("metadata/expected-commands.tsv");
        Files.createDirectories(retained.getParent());
        Files.copy(authority, retained);

        List<String> templates = Files.readAllLines(authority, StandardCharsets.UTF_8);
        List<String> actual = new ArrayList<>();
        actual.add(templates.getFirst() + "\tstartedAtUtc\tendedAtUtc\tactualExit\tcopyStatus");
        String runRepository = evidence.resolve("run-repository").toString();
        String policy = evidence.resolve("policy/production-policy.tsv").toString();
        String timestamp = "2026-07-18T00:00:00Z";
        int disposableOrdinal = 0;
        for (int index = 1; index < templates.size(); index++) {
            String[] row = templates.get(index).split("\t", -1);
            String argv = row[4]
                    .replace("<RUN_REPO>", runRepository)
                    .replace("<EVIDENCE>", evidence.toString())
                    .replace("<CANDIDATE_VERSION>", "4.0.0")
                    .replace("<CANDIDATE_REVISION>", "a".repeat(40))
                    .replace("<AUDIT_MODE>", "auto")
                    .replace("<PRODUCTION_POLICY>", policy)
                    .replace("<FINAL_VERSION>", "4.0.0");
            if (argv.contains("<DISPOSABLE_REPO>")) {
                disposableOrdinal++;
                argv = argv.replace(
                        "<DISPOSABLE_REPO>",
                        evidence.resolve("disposable-repository-" + disposableOrdinal).toString());
            }
            String cwd = "<REPO_ROOT>".equals(row[3])
                    ? root.toString()
                    : root.resolve(row[3]).normalize().toString();
            if (!"-".equals(row[6])) {
                for (String relative : row[6].split(",", -1)) {
                    Path copied = evidence.resolve(relative);
                    Files.createDirectories(copied.getParent());
                    Files.writeString(copied, "retained command output\n", StandardCharsets.UTF_8);
                }
            }
            String actualExit = "C-MIXED".equals(row[1]) ? "1" : "0";
            actual.add(String.join("\t",
                    row[0], row[1], row[2], cwd, argv, row[5], row[6],
                    timestamp, timestamp, actualExit, "PASS"));
        }
        Files.write(evidence.resolve("metadata/commands.tsv"), actual, StandardCharsets.UTF_8);
    }

    private static Path writePerformanceEvidence(Path evidence) throws Exception {
        Path root = Files.createDirectories(evidence.resolve("performance/compare-production"));
        Path raw = Files.createDirectories(root.resolve("raw"));
        List<String> semantic = new ArrayList<>();
        semantic.add("workloadId\tscenario\tthreads\toutcome\tcompletion\tchangeCount\t"
                + "changeTokens\tlimitationCodes\tdistinctInputs\tobservedDecorator");
        Path firstRaw = null;
        for (String scenario : List.of(
                "NESTED_POJO", "LIST", "MAP", "SET_SCALAR", "SET_ENTITY",
                "SET_AMBIGUOUS", "OBSERVED_COMPARE")) {
            for (String threads : List.of("1", "8", "32")) {
                String workload = scenario.toLowerCase(java.util.Locale.ROOT) + "-t" + threads;
                Path rawReport = raw.resolve(workload + ".json");
                Files.writeString(rawReport, "{\"workload\":\"" + workload + "\"}\n",
                        StandardCharsets.UTF_8);
                if (firstRaw == null) {
                    firstRaw = rawReport;
                }
                boolean ambiguous = "SET_AMBIGUOUS".equals(scenario);
                boolean observed = "OBSERVED_COMPARE".equals(scenario);
                semantic.add(String.join("\t",
                        workload, scenario, threads,
                        ambiguous ? "INDETERMINATE" : "DIFFERENT",
                        ambiguous ? "PARTIAL" : "COMPLETE",
                        ambiguous ? "0" : "1",
                        ambiguous ? "-" : "VALUE_CHANGED",
                        ambiguous ? "KEY_AMBIGUOUS" : "-",
                        "true", Boolean.toString(observed)));
            }
        }
        Files.write(root.resolve("semantic-facts.tsv"), semantic, StandardCharsets.UTF_8);

        List<String> marker = new ArrayList<>(List.of(
                "schema\tTFI_COMPARE_JMH_V1",
                "workloadCount\t21",
                "entryType\trelativePath\tsha256"));
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(candidate -> !candidate.equals(root)).sorted().toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                marker.add(Files.isDirectory(path)
                        ? "directory\t" + relative + "\t-"
                        : "file\t" + relative + "\t" + sha256(Files.readAllBytes(path)));
            }
        }
        Files.write(root.resolve("_SUCCESS"), marker, StandardCharsets.UTF_8);

        String repositoryPath = "com/syy/tfi-compare/4.0.0/tfi-compare-4.0.0.jar";
        String artifactSha = "c".repeat(64);
        Path candidateManifest = evidence.resolve("metadata/candidate-artifacts.sha256");
        Files.createDirectories(candidateManifest.getParent());
        Files.write(candidateManifest, List.of(artifactSha + "  " + repositoryPath),
                StandardCharsets.UTF_8);
        Files.write(evidence.resolve("performance/artifacts.tsv"), List.of(
                "repositoryPath\tsha256", repositoryPath + "\t" + artifactSha),
                StandardCharsets.UTF_8);
        Files.writeString(evidence.resolve("performance/tfi-routing-enabled.json"), "[]\n",
                StandardCharsets.UTF_8);
        Files.writeString(evidence.resolve("performance/tfi-routing-legacy.json"), "[]\n",
                StandardCharsets.UTF_8);
        return java.util.Objects.requireNonNull(firstRaw);
    }

    private Fixture preparedFixture(String name, String baselineSha, boolean withCandidates)
            throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve(name));
        Path report = Files.createDirectories(evidence.resolve("reports")).resolve("TEST-fixture.xml");
        Files.writeString(report,
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>\n",
                StandardCharsets.UTF_8);
        List<Path> retained = new ArrayList<>();
        retained.add(report);
        String candidateSetSha = "b".repeat(64);
        if (withCandidates) {
            List<Path> artifacts = writeCandidateArtifacts(evidence, "4.0.0");
            retained.addAll(artifacts);
            Path candidateManifest = evidence.resolve("metadata/candidate-artifacts.sha256");
            Files.createDirectories(candidateManifest.getParent());
            List<String> rows = new ArrayList<>();
            for (Path artifact : artifacts) {
                String path = evidence.resolve("artifacts/repository").relativize(artifact)
                        .toString().replace('\\', '/');
                rows.add(sha256(Files.readAllBytes(artifact)) + "  " + path);
            }
            rows.sort(java.util.Comparator.comparing(row -> row.substring(66)));
            Files.write(candidateManifest, rows, StandardCharsets.UTF_8);
            candidateSetSha = sha256(Files.readAllBytes(candidateManifest));
            retained.add(candidateManifest);
        }
        Path marker = evidence.resolve("PREPARED");
        Files.write(marker, List.of(
                "candidateRevision\t" + "a".repeat(40),
                "candidateSetSha256\t" + candidateSetSha,
                "baselineManifestSha256\t" + baselineSha,
                "reviewAssignmentId\trelease-owner:assignment-1",
                "productionPolicySha256\t" + "d".repeat(64),
                "finalVersion\t4.0.0",
                "releaseTarget\tINTERNAL_REPOSITORY:https://repo.example.test/releases",
                "publishableArtifactSetSha256\t" + "e".repeat(64),
                "sbomSha256\t" + "f".repeat(64),
                "evidencePreparer\trelease:preparer:session-1",
                "independentReviewer\trelease:reviewer:session-2",
                "evidenceStatus\tPREPARED"), StandardCharsets.UTF_8);
        retained.add(marker);
        Path expectedReports = temporaryDirectory.resolve(name + "-reports.tsv");
        Files.write(expectedReports, List.of(
                "phase\tmodule\treportPath\tminimumTests\tallowSkipped",
                "FOCUSED\ttfi-compare\treports/TEST-fixture.xml\t1\tfalse"),
                StandardCharsets.UTF_8);
        Path retainedReports = evidence.resolve("metadata/expected-reports.tsv");
        Files.createDirectories(retainedReports.getParent());
        Files.write(retainedReports, Files.readAllBytes(expectedReports));
        retained.add(retainedReports);
        writeManifest(evidence, retained);
        return new Fixture(evidence, expectedReports);
    }

    private static List<Path> writeCandidateArtifacts(Path evidence, String version)
            throws Exception {
        List<String> paths = new ArrayList<>();
        paths.add("com/syy/taskflowinsight-parent/" + version
                + "/taskflowinsight-parent-" + version + ".pom");
        for (String artifact : List.of(
                "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight")) {
            String base = "com/syy/" + artifact + "/" + version + "/" + artifact + "-" + version;
            paths.add(base + ".jar");
            paths.add(base + ".pom");
        }
        List<Path> result = new ArrayList<>();
        for (String path : paths) {
            Path file = evidence.resolve("artifacts/repository").resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "fixture " + path + "\n", StandardCharsets.UTF_8);
            result.add(file);
        }
        return List.copyOf(result);
    }

    private static void rewriteManifest(Path evidence) throws Exception {
        List<Path> retained;
        try (var paths = Files.walk(evidence)) {
            retained = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(evidence.resolve("evidence-manifest.sha256")))
                    .toList();
        }
        writeManifest(evidence, retained);
    }

    private static void writeManifest(Path evidence, List<Path> files) throws Exception {
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(java.util.Comparator.comparing(file -> relativePath(evidence, file)));
        List<String> rows = new ArrayList<>();
        for (Path file : sorted) {
            String relative = relativePath(evidence, file);
            rows.add(sha256(Files.readAllBytes(file)) + "  " + relative);
        }
        Files.write(evidence.resolve("evidence-manifest.sha256"), rows, StandardCharsets.UTF_8);
    }

    private static String relativePath(Path evidence, Path file) {
        return evidence.relativize(file).toString().replace('\\', '/');
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static ProcessResult run(String... arguments)
            throws IOException, InterruptedException {
        Path root = repositoryRoot();
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString());
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private ProcessResult runProbe(String... arguments) throws Exception {
        Path root = repositoryRoot();
        Path verifierClasses = temporaryDirectory.resolve("verifier-classes");
        if (!Files.isDirectory(verifierClasses)) {
            Files.createDirectories(verifierClasses);
            Process compiler = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                    "-d", verifierClasses.toString(),
                    root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString())
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(
                    compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (compiler.waitFor() != 0) {
                throw new AssertionError("verifier probe compilation failed: " + output);
            }
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(verifierClasses + File.pathSeparator + System.getProperty("java.class.path"));
        command.add(ValidatorProbe.class.getName());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    /** 测试进程内只调用生产 verifier 的真实阶段函数，不复制其 parser 或判定逻辑。 */
    public static final class ValidatorProbe {
        private ValidatorProbe() {
        }

        /**
         * @param arguments validator name and its fixture arguments
         */
        public static void main(String[] arguments) {
            try {
                Path evidence = Path.of(arguments[1]);
                switch (arguments[0]) {
                    case "commands" -> validateCommands(evidence);
                    case "codesource" -> invoke(
                            "validateCodeSources",
                            new Class<?>[]{Path.class, String.class, Set.class, Map.class, String.class},
                            evidence, arguments[2], Set.of(arguments[3]),
                            Map.of(arguments[4], arguments[5]), arguments[6]);
                    case "performance" -> invoke(
                            "validatePerformanceEvidence", new Class<?>[]{Path.class}, evidence);
                    default -> throw new IllegalArgumentException(
                            "unknown validator probe: " + arguments[0]);
                }
                System.out.println("VALIDATOR_OK");
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                System.err.println(cause == null ? failure.getMessage() : cause.getMessage());
                System.exit(2);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                failure.printStackTrace(System.err);
                System.exit(3);
            }
        }

        @SuppressWarnings("unchecked")
        private static void validateCommands(Path evidence) throws ReflectiveOperationException {
            Map<String, String> files = (Map<String, String>) invoke(
                    "snapshot", new Class<?>[]{Path.class}, evidence);
            invoke(
                    "validateCommandEvidence",
                    new Class<?>[]{Path.class, Map.class, Map.class},
                    evidence, files, Map.of());
        }

        private static Object invoke(String name, Class<?>[] parameterTypes, Object... arguments)
                throws ReflectiveOperationException {
            Class<?> verifier = Class.forName("ReleaseEvidenceVerifier$IntegrityEvidence");
            Method method = verifier.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        }
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** @param exitCode verifier exit; @param output stable combined output. */
    private record ProcessResult(int exitCode, String output) {
    }

    /** @param evidence finalized evidence root; @param expectedReports external report authority. */
    private record Fixture(Path evidence, Path expectedReports) {
    }
}
