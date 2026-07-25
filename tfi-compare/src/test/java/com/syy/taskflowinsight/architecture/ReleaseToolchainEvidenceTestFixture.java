package com.syy.taskflowinsight.architecture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** 生成只供合同测试使用的 actual toolchain 与 raw loaded-byte measurement。 */
final class ReleaseToolchainEvidenceTestFixture {
    /** Actual build toolchain 的固定 schema。 */
    private static final String TOOLCHAIN_HEADER =
            "ordinal\trole\tcoordinate\tevidencePath\tsha256";
    /** Raw measurement index 的固定 schema。 */
    private static final String MEASUREMENT_HEADER =
            "role\tcoordinate\tmeasurementKind\trawEvidencePath\trawEvidenceSha256";
    /** Raw loaded fact 不能由 actual manifest 行直接替代。 */
    private static final String RAW_FACT_HEADER =
            "observationId\tmeasurementKind\tsourceLocator\trole\tcoordinate\t"
                    + "loadedEvidencePath\tloadedSha256\trawSourcePath\trawSourceSha256";

    private ReleaseToolchainEvidenceTestFixture() {
    }

    static void addCommandSpecs(Path policy) throws Exception {
        Path directory = policy.getParent();
        Path executionPolicy = directory.resolve(policyValue(policy, "releaseExecutionPolicy"));
        List<String> executions = new ArrayList<>(
                Files.readAllLines(executionPolicy, StandardCharsets.UTF_8));
        Path commands = Files.createDirectory(directory.resolve("commands"));
        String commandBytes = "ordinal\targ\n1\t/usr/bin/true\n";
        String commandSha = sha256(commandBytes.getBytes(StandardCharsets.UTF_8));
        for (int index = 1; index < executions.size(); index++) {
            String[] row = executions.get(index).split("\t", -1);
            Files.writeString(commands.resolve(row[2] + ".argv.tsv"),
                    commandBytes, StandardCharsets.UTF_8);
            row[3] = commandSha;
            executions.set(index, String.join("\t", row));
        }
        Files.write(executionPolicy, executions, StandardCharsets.UTF_8);
        replacePolicyValue(policy, "releaseExecutionPolicySha256",
                sha256(Files.readAllBytes(executionPolicy)));

        Path performance = directory.resolve(policyValue(policy, "runtimePerformancePolicy"));
        List<String> workloads = new ArrayList<>(
                Files.readAllLines(performance, StandardCharsets.UTF_8));
        for (int index = 1; index < workloads.size(); index++) {
            String[] row = workloads.get(index).split("\t", -1);
            row[11] = commandSha;
            workloads.set(index, String.join("\t", row));
        }
        Files.write(performance, workloads, StandardCharsets.UTF_8);
        replacePolicyValue(policy, "runtimePerformancePolicySha256",
                sha256(Files.readAllBytes(performance)));
    }

    static void addToolClosures(Path evidence, Path policy) throws Exception {
        for (String[] tool : List.of(
                new String[]{"vulnerabilityScanner", "fixture-vulnerability", "security/tool-closures/"
                        + "vulnerability-scanner.tsv", "vulnerability-scanner"},
                new String[]{"secretScanner", "fixture-secret", "security/tool-closures/"
                        + "secret-scanner.tsv", "secret-scanner"},
                new String[]{"sbomGenerator", "fixture-sbom", "supply-chain/tool-closures/"
                        + "sbom-generator.tsv", "sbom-generator"})) {
            String bytesRelative = "supply-chain/tool-bytes/" + tool[3] + "/tool.bin";
            Path bytes = evidence.resolve(bytesRelative);
            Files.createDirectories(bytes.getParent());
            Files.writeString(bytes, "TEST_ONLY " + tool[1] + "\n", StandardCharsets.UTF_8);
            Path manifest = evidence.resolve(tool[2]);
            Files.createDirectories(manifest.getParent());
            Files.write(manifest, List.of(
                    "ordinal\tkind\tcoordinate\tevidencePath\tsha256",
                    "1\tBINARY\tbin:" + tool[1] + ":1.0.0:linux:amd64\t"
                            + bytesRelative + "\t" + sha256(Files.readAllBytes(bytes))),
                    StandardCharsets.UTF_8);
            replacePolicyValue(policy, tool[0], tool[1] + "@1.0.0#bundle-sha256:"
                    + sha256(Files.readAllBytes(manifest)));
        }
    }

    static void addToolExecutions(Path evidence, Path policy) throws Exception {
        Path executionPolicy = policy.getParent().resolve(policyValue(policy, "releaseExecutionPolicy"));
        List<String> executions = Files.readAllLines(executionPolicy, StandardCharsets.UTF_8);
        List<String> ledger = new ArrayList<>();
        ledger.add("executionId\ttoolRole\tcommandId\tbundleSha256\tloadedKind\tloadedCoordinate\t"
                + "loadedEvidencePath\tloadedSha256\tmeasurementKind\tmeasurementPath\t"
                + "measurementSha256");
        int observation = 1;
        for (int index = 1; index < executions.size(); index++) {
            String[] execution = executions.get(index).split("\t", -1);
            String[] mapping = executionTool(execution[1]);
            if (mapping == null) {
                continue;
            }
            Path manifest = evidence.resolve(mapping[1]);
            List<String> components = Files.readAllLines(manifest, StandardCharsets.UTF_8);
            String bundleSha = sha256(Files.readAllBytes(manifest));
            for (int componentIndex = 1; componentIndex < components.size(); componentIndex++) {
                String[] component = components.get(componentIndex).split("\t", -1);
                String measurementKind = switch (component[1]) {
                    case "MAVEN" -> "JVM_CODESOURCE";
                    case "OCI" -> "OCI_RUNTIME_INSPECTION";
                    default -> "PROCESS_EXECUTABLE_MAP";
                };
                String rawRelative = "supply-chain/tool-measurements/executions/"
                        + execution[0] + "-" + componentIndex + ".tsv";
                Path raw = evidence.resolve(rawRelative);
                Files.createDirectories(raw.getParent());
                String locator = toolSourceLocator(
                        measurementKind, component[2], observation);
                String rawSourceRelative = rawRelative.replace(".tsv", ".source");
                Path rawSource = evidence.resolve(rawSourceRelative);
                writeRawSource(rawSource, measurementKind, locator);
                Files.write(raw, List.of(
                        "observationId\texecutionId\ttoolRole\tmeasurementKind\tsourceLocator\t"
                                + "loadedKind\tloadedCoordinate\tloadedEvidencePath\tloadedSha256\t"
                                + "rawSourcePath\trawSourceSha256",
                        String.join("\t", String.format("TOOL-%03d", observation++), execution[0],
                                mapping[0], measurementKind, locator, component[1], component[2],
                                component[3], component[4], rawSourceRelative,
                                sha256(Files.readAllBytes(rawSource)))),
                        StandardCharsets.UTF_8);
                ledger.add(String.join("\t", execution[0], mapping[0], execution[2], bundleSha,
                        component[1], component[2], component[3], component[4], measurementKind,
                        rawRelative, sha256(Files.readAllBytes(raw))));
            }
        }
        Path metadata = evidence.resolve("metadata");
        Files.createDirectories(metadata);
        Files.write(metadata.resolve("tool-executions.tsv"), ledger, StandardCharsets.UTF_8);
    }

    static void addReleaseExecutionInputs(Path evidence, Path policy) throws Exception {
        Path policyBase = policy.getParent();
        Path performancePolicy = policyBase.resolve(policyValue(policy, "runtimePerformancePolicy"));
        Path compatibilityPolicy = policyBase.resolve(policyValue(policy, "compatibilityMatrix"));
        Path scopes = evidence.resolve("metadata/execution-scopes");
        Files.createDirectories(scopes);
        Files.copy(performancePolicy, scopes.resolve("performance-policy.tsv"));
        Files.copy(compatibilityPolicy, scopes.resolve("compatibility-matrix.tsv"));
        writePerformanceResults(evidence, performancePolicy);
        writeCompatibilityResults(evidence, compatibilityPolicy);
    }

    static void addReleaseExecutions(Path evidence, Path policy) throws Exception {
        Path policyBase = policy.getParent();
        Path executionPolicy = policyBase.resolve(policyValue(policy, "releaseExecutionPolicy"));
        List<String> expected = Files.readAllLines(executionPolicy, StandardCharsets.UTF_8);
        List<String> actual = new ArrayList<>();
        actual.add("executionId\trole\tcommandId\tcommandSpecSha256\tconfigPath\tconfigSha256\t"
                + "rulesPath\trulesSha256\tscopeRule\tscopePath\tscopeSha256\trawReportPath\t"
                + "rawReportSha256\tactualExit\tstartedAtUtc\tendedAtUtc\tstatus");
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        for (int index = 1; index < expected.size(); index++) {
            String[] row = expected.get(index).split("\t", -1);
            String scopePath = scopePath(row[1]);
            String reportPath = reportPath(row[1], policy);
            actual.add(expected.get(index) + "\t" + scopePath + "\t"
                    + sha256(Files.readAllBytes(evidence.resolve(scopePath))) + "\t"
                    + reportPath + "\t" + sha256(Files.readAllBytes(evidence.resolve(reportPath)))
                    + "\t0\t" + now + "\t" + now + "\tPASS");
        }
        Files.write(evidence.resolve("metadata/release-executions.tsv"), actual, StandardCharsets.UTF_8);
    }

    private static void writePerformanceResults(Path evidence, Path policy) throws Exception {
        List<String> authority = Files.readAllLines(policy, StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        rows.add("workloadId\tevidenceCommandId\tcandidateReportPath\tbaselineReportPath\t"
                + "candidateP99Nanos\tbaselineP99Nanos\tregressionPercent\t"
                + "candidateAllocationBytesPerOp\tsemanticLogicalFactsSha256\t"
                + "semanticFactsFileSha256\tenvironmentSha256\tsemanticStatus\t"
                + "codeSourceStatus\tstatus");
        for (int index = 1; index < authority.size(); index++) {
            String[] row = authority.get(index).split("\t", -1);
            rows.add(row[0] + "\t" + row[10] + "\tfixture/candidate.json\tfixture/baseline.json"
                    + "\t100\t100\t0\t64\t" + "a".repeat(64) + "\t" + "b".repeat(64)
                    + "\t" + "c".repeat(64) + "\tPASS\tPASS\tPASS");
        }
        Path result = evidence.resolve("runtime/performance/results.tsv");
        Files.createDirectories(result.getParent());
        Files.write(result, rows, StandardCharsets.UTF_8);
    }

    private static void writeCompatibilityResults(Path evidence, Path policy) throws Exception {
        List<String> authority = Files.readAllLines(policy, StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        rows.add("edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion\t"
                + "expected\tenforcement\tevidenceCommandId\tactualExit\tfailureClassifier\t"
                + "resolvedArtifactsPath\tdependencyTreePath\tcodeSourcePath\trawEvidencePath\tstatus");
        for (int index = 1; index < authority.size(); index++) {
            rows.add(authority.get(index)
                    + "\t0\tNONE\tfixture/resolved.tsv\tfixture/tree.tsv\t"
                    + "fixture/codesource.tsv\tfixture/raw.tsv\tPASS");
        }
        Path result = evidence.resolve("runtime/compatibility/results.tsv");
        Files.createDirectories(result.getParent());
        Files.write(result, rows, StandardCharsets.UTF_8);
    }

    private static String scopePath(String role) {
        return switch (role) {
            case "PERFORMANCE" -> "metadata/execution-scopes/performance-policy.tsv";
            case "COMPATIBILITY" -> "metadata/execution-scopes/compatibility-matrix.tsv";
            case "VULNERABILITY_SCAN", "SBOM_GENERATE" -> "metadata/runtime-artifacts.tsv";
            case "SECRET_SCAN_FIRST" -> "security/secret-scan/scope.tsv";
            case "SECRET_SCAN_SELF" -> "security/secret-scan/report-self-scan-scope.tsv";
            default -> "security/sensitive-log/scope.tsv";
        };
    }

    private static String reportPath(String role, Path policy) throws Exception {
        return switch (role) {
            case "PERFORMANCE" -> "runtime/performance/results.tsv";
            case "COMPATIBILITY" -> "runtime/compatibility/results.tsv";
            case "VULNERABILITY_SCAN" -> "security/vulnerability/report.json";
            case "SECRET_SCAN_FIRST" -> "security/secret-scan/report.json";
            case "SECRET_SCAN_SELF" -> "security/secret-scan/report-self-scan.tsv";
            case "SBOM_GENERATE" -> "CycloneDX-1.6".equals(policyValue(policy, "sbomFormat"))
                    ? "supply-chain/sbom/bom.cdx.json" : "supply-chain/sbom/bom.spdx.json";
            default -> "security/sensitive-log/raw-result.tsv";
        };
    }

    private static String[] executionTool(String role) {
        return switch (role) {
            case "VULNERABILITY_SCAN" -> new String[]{
                    "VULNERABILITY_SCANNER", "security/tool-closures/vulnerability-scanner.tsv"};
            case "SECRET_SCAN_FIRST", "SECRET_SCAN_SELF", "SENSITIVE_LOG_SCAN" -> new String[]{
                    "SECRET_SCANNER", "security/tool-closures/secret-scanner.tsv"};
            case "SBOM_GENERATE" -> new String[]{
                    "SBOM_GENERATOR", "supply-chain/tool-closures/sbom-generator.tsv"};
            default -> null;
        };
    }

    private static String toolSourceLocator(String kind, String coordinate, int ordinal) {
        return switch (kind) {
            case "OCI_RUNTIME_INSPECTION" -> "oci-runtime://" + coordinate.substring("oci:".length());
            case "JVM_CODESOURCE" -> "file:/opt/tfi/scanner" + ordinal;
            default -> "/proc/" + (300 + ordinal) + "/maps:/opt/tfi/scanner" + ordinal;
        };
    }

    static void addBuildToolchain(Path evidence, Path policy) throws Exception {
        Path authority = policy.getParent().resolve(policyValue(policy, "buildToolchainManifest"));
        List<String> expected = Files.readAllLines(authority, StandardCharsets.UTF_8);
        Path actualDirectory = evidence.resolve("supply-chain/tool-bytes/build-toolchain");
        Path rawDirectory = evidence.resolve("supply-chain/tool-measurements/build-toolchain");
        Files.createDirectories(actualDirectory);
        Files.createDirectories(rawDirectory);
        List<String> actual = new ArrayList<>();
        List<String> measurements = new ArrayList<>();
        actual.add(TOOLCHAIN_HEADER);
        measurements.add(MEASUREMENT_HEADER);
        for (int index = 1; index < expected.size(); index++) {
            String[] row = expected.get(index).split("\t", -1);
            Path source = policy.getParent().resolve(row[3]);
            String fileName = String.format("%03d-%s", index, source.getFileName());
            String actualRelative = "supply-chain/tool-bytes/build-toolchain/" + fileName;
            Path actualBytes = evidence.resolve(actualRelative);
            Files.copy(source, actualBytes);
            String actualSha = sha256(Files.readAllBytes(actualBytes));
            actual.add(String.join("\t", row[0], row[1], row[2], actualRelative, actualSha));

            String kind = measurementKind(row[1]);
            String rawRelative = "supply-chain/tool-measurements/build-toolchain/"
                    + String.format("%03d.tsv", index);
            Path raw = evidence.resolve(rawRelative);
            String locator = sourceLocator(kind, row[2], index);
            String rawSourceRelative = rawRelative.replace(".tsv", ".source");
            Path rawSource = evidence.resolve(rawSourceRelative);
            writeRawSource(rawSource, kind, locator);
            Files.write(raw, List.of(
                    RAW_FACT_HEADER,
                    String.join("\t", "BUILD-" + String.format("%03d", index), kind, locator,
                            row[1], row[2], actualRelative, actualSha, rawSourceRelative,
                            sha256(Files.readAllBytes(rawSource)))), StandardCharsets.UTF_8);
            measurements.add(String.join("\t", row[1], row[2], kind, rawRelative,
                    sha256(Files.readAllBytes(raw))));
        }
        Path closure = evidence.resolve("supply-chain/tool-closures");
        Files.createDirectories(closure);
        Files.write(closure.resolve("build-toolchain.tsv"), actual, StandardCharsets.UTF_8);
        Files.write(closure.resolve("build-toolchain-measurements.tsv"),
                measurements, StandardCharsets.UTF_8);
    }

    private static String measurementKind(String role) {
        return switch (role) {
            case "RUNNER_IMAGE" -> "OCI_RUNTIME_INSPECTION";
            case "MAVEN_WRAPPER" -> "JVM_CODESOURCE";
            case "MAVEN_PLUGIN", "BUILD_EXTENSION", "BUILD_DEPENDENCY" -> "MAVEN_CLASSREALM";
            default -> "PROCESS_EXECUTABLE_MAP";
        };
    }

    private static String sourceLocator(String kind, String coordinate, int ordinal) {
        return switch (kind) {
            case "OCI_RUNTIME_INSPECTION" -> "oci-runtime://" + coordinate.substring("oci:".length());
            case "PROCESS_EXECUTABLE_MAP" -> "/proc/" + (100 + ordinal) + "/maps:/opt/tfi/tool" + ordinal;
            default -> "file:/opt/tfi/tool" + ordinal;
        };
    }

    private static void writeRawSource(Path path, String kind, String locator) throws Exception {
        String content = switch (kind) {
            case "OCI_RUNTIME_INSPECTION" -> "{\"RepoDigests\":[\""
                    + locator.substring("oci-runtime://".length()) + "\"]}\n";
            case "PROCESS_EXECUTABLE_MAP" -> "00400000-00452000 r-xp 00000000 08:01 12345 "
                    + locator.substring(locator.indexOf(':') + 1) + "\n";
            case "JVM_CODESOURCE", "MAVEN_CLASSREALM" -> locator + "\n";
            default -> throw new IllegalArgumentException("Unsupported raw source kind " + kind);
        };
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    static String policyValue(Path policy, String key) throws Exception {
        String prefix = key + "\t";
        return Files.readAllLines(policy, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
    }

    static void replacePolicyValue(Path policy, String key, String value) throws Exception {
        List<String> lines = new ArrayList<>(Files.readAllLines(policy, StandardCharsets.UTF_8));
        String prefix = key + "\t";
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                lines.set(index, prefix + value);
                Files.write(policy, lines, StandardCharsets.UTF_8);
                return;
            }
        }
        throw new IllegalArgumentException("Missing policy key " + key);
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
