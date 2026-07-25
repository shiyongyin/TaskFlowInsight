package com.syy.taskflowinsight.architecture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅为合同测试生成无明文 secret 的两遍扫描和 77 canary machine evidence。 */
final class ReleaseSecurityEvidenceTestFixture {
    private static final Set<String> POST_SCOPE_PATHS = Set.of(
            "security/secret-scan/scope.tsv",
            "security/secret-scan/report.json",
            "security/secret-scan/normalized-findings.tsv",
            "security/secret-scan/report-self-scan-scope.tsv",
            "security/secret-scan/report-self-scan.tsv",
            "security/secret-scan/commands.tsv",
            "security/secret-scan/summary.tsv",
            "security/secret-scan/process-attestation.sigstore.json",
            "supply-chain/provenance/artifact-provenance.sigstore.json",
            "metadata/tool-executions.tsv",
            "metadata/release-executions.tsv",
            "metadata/actual-command-ledgers.tsv",
            "metadata/commands.tsv",
            "metadata/report-summary.tsv",
            "architecture/xrt-11/complexity.tsv",
            "metadata/evidence-subject-manifest.tsv",
            "supply-chain/signatures/artifact-signature-results.tsv",
            "supply-chain/provenance/evidence-attestation.sigstore.json",
            "PREPARED", "CI_ONLY", "evidence-manifest.sha256");

    private ReleaseSecurityEvidenceTestFixture() {
    }

    static Paths add(Path evidence, Path policy) throws Exception {
        String scanner = ReleaseToolchainEvidenceTestFixture.policyValue(policy, "secretScanner");
        Path candidate = evidence.resolve("candidate/source.txt");
        Files.createDirectories(candidate.getParent());
        Files.writeString(candidate, "public fixture\n", StandardCharsets.UTF_8);
        SensitivePaths sensitive = writeSensitiveLogEvidence(evidence, policy, scanner);
        Paths secret = writeSecretEvidence(evidence, policy, scanner);
        return new Paths(
                secret.scope(), secret.selfScope(), secret.commands(),
                sensitive.receipts(), sensitive.scope());
    }

    private static Paths writeSecretEvidence(Path evidence, Path policy, String scanner) throws Exception {
        Path candidateManifest = evidence.resolve("metadata/candidate-tree.tsv");
        write(candidateManifest, List.of(
                "relativePath\tsha256",
                "candidate/source.txt\t"
                        + sha256(Files.readAllBytes(evidence.resolve("candidate/source.txt")))));
        Path root = evidence.resolve("security/secret-scan");
        Files.createDirectories(root);
        Set<String> postScopePaths = postScopePaths(evidence);
        List<String> scopeRows = new ArrayList<>();
        scopeRows.add("scopeRoot\trelativePath\tsha256");
        addScope(scopeRows, "CANDIDATE_TREE", evidence, "candidate/source.txt");
        List<String> evidencePaths;
        try (var paths = Files.walk(evidence)) {
            evidencePaths = paths.filter(Files::isRegularFile)
                    .map(evidence::relativize)
                    .map(path -> path.toString().replace(java.io.File.separatorChar, '/'))
                    .filter(path -> !postScopePaths.contains(path))
                    .sorted()
                    .toList();
        }
        for (String relative : evidencePaths) {
            addScope(scopeRows, "EVIDENCE", evidence, relative);
        }
        Path scope = root.resolve("scope.tsv");
        write(scope, scopeRows);
        String scopeSha = sha256(Files.readAllBytes(scope));
        Path report = root.resolve("report.json");
        Files.writeString(report, "{\"analysisErrors\":[],\"findings\":[],\"scannerIdentity\":\"" + scanner
                + "\",\"scopeSha256\":\"" + scopeSha + "\",\"status\":\"PASS\"}",
                StandardCharsets.UTF_8);
        Path normalized = root.resolve("normalized-findings.tsv");
        Files.writeString(normalized,
                "ruleId\tscopeRoot\trelativePath\tfingerprint\tstatus\n", StandardCharsets.UTF_8);
        List<String> selfRows = new ArrayList<>();
        selfRows.add("scopeRoot\trelativePath\tsha256");
        addScope(selfRows, "EVIDENCE", evidence, "security/secret-scan/normalized-findings.tsv");
        addScope(selfRows, "EVIDENCE", evidence, "security/secret-scan/report.json");
        addScope(selfRows, "EVIDENCE", evidence, "security/secret-scan/scope.tsv");
        Path selfScope = root.resolve("report-self-scan-scope.tsv");
        write(selfScope, selfRows);
        String selfScopeSha = sha256(Files.readAllBytes(selfScope));
        Path selfReport = root.resolve("report-self-scan.tsv");
        write(selfReport, List.of(
                "scannerIdentity\tscopeSha256\tactualExit\tfindings\tanalysisErrors\tstatus",
                scanner + "\t" + selfScopeSha + "\t0\t0\t0\tPASS"));
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Path commands = root.resolve("commands.tsv");
        write(commands, List.of(
                "ordinal\tcommandId\texecutionId\tcommandSpecSha256\tscopeSha256\tactualExit\t"
                        + "findings\tanalysisErrors\treportSha256\tstartedAtUtc\tendedAtUtc\tstatus",
                command(1, "CMD-EXEC-SECRET-FIRST", "EXEC-SECRET-FIRST", scopeSha,
                        sha256(Files.readAllBytes(report)), now,
                        commandSha(policy, "EXEC-SECRET-FIRST")),
                command(2, "CMD-EXEC-SECRET-SELF", "EXEC-SECRET-SELF", selfScopeSha,
                        sha256(Files.readAllBytes(selfReport)), now,
                        commandSha(policy, "EXEC-SECRET-SELF"))));
        write(root.resolve("summary.tsv"), List.of(
                "firstPassFindings\tsecondPassFindings\tanalysisErrors\tsecretFindings\tstatus",
                "0\t0\t0\t0\tPASS"));
        return new Paths(scope, selfScope, commands, null, null);
    }

    private static Set<String> postScopePaths(Path evidence) throws Exception {
        Set<String> result = new java.util.HashSet<>(POST_SCOPE_PATHS);
        Path manifest = evidence.resolve("metadata/publishable-artifacts.tsv");
        if (!Files.isRegularFile(manifest)) {
            return Set.copyOf(result);
        }
        List<String> rows = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        for (int index = 1; index < rows.size(); index++) {
            String[] row = rows.get(index).split("\t", -1);
            if (row.length == 7 && "SIGNATURE".equals(row[4])) {
                result.add("artifacts/publishable-repository/" + row[3]);
            }
        }
        return Set.copyOf(result);
    }

    private static SensitivePaths writeSensitiveLogEvidence(
            Path evidence, Path policy, String scanner)
            throws Exception {
        Path root = evidence.resolve("security/sensitive-log");
        Path sinks = root.resolve("sinks");
        Files.createDirectories(sinks);
        Map<String, Path> sinkFiles = new LinkedHashMap<>();
        for (String sink : sinks()) {
            Path file = sinks.resolve(sink + ".txt");
            Files.writeString(file, "REDACTED " + sink + "\n", StandardCharsets.UTF_8);
            sinkFiles.put(sink, file);
        }
        Path coverage = policy.getParent().resolve("config/canary-coverage.tsv");
        List<String> coverageRows = Files.readAllLines(coverage, StandardCharsets.UTF_8);
        List<String> receipts = new ArrayList<>();
        receipts.add("canaryId\tcanaryKind\tsinkKind\tinjectionDriverId\tcanarySha256\t"
                + "evidencePath\tevidenceSha256\tinjectionStatus");
        List<String> canarySet = new ArrayList<>();
        for (int index = 1; index < coverageRows.size(); index++) {
            String[] row = coverageRows.get(index).split("\t", -1);
            String canarySha = sha256((row[0] + "-EPHEMERAL").getBytes(StandardCharsets.UTF_8));
            String relative = "security/sensitive-log/sinks/" + row[2] + ".txt";
            receipts.add(String.join("\t", row[0], row[1], row[2], row[3], canarySha,
                    relative, sha256(Files.readAllBytes(sinkFiles.get(row[2]))), "INJECTED"));
            canarySet.add(row[0] + "\t" + canarySha);
        }
        Path receiptPath = root.resolve("injection-receipts.tsv");
        write(receiptPath, receipts);
        List<String> scopeRows = new ArrayList<>();
        scopeRows.add("sinkKind\tevidencePath\tsha256");
        for (Map.Entry<String, Path> sink : sinkFiles.entrySet()) {
            scopeRows.add(sink.getKey() + "\tsecurity/sensitive-log/sinks/" + sink.getKey()
                    + ".txt\t" + sha256(Files.readAllBytes(sink.getValue())));
        }
        Path scope = root.resolve("scope.tsv");
        write(scope, scopeRows);
        write(root.resolve("raw-result.tsv"), List.of(
                "scannerIdentity\texecutionId\tcoverageSha256\tscopeSha256\tcanarySetSha256\t"
                        + "actualExit\tfindings\tanalysisErrors\tstatus",
                scanner + "\tEXEC-SENSITIVE\t" + sha256(Files.readAllBytes(coverage)) + "\t"
                        + sha256(Files.readAllBytes(scope)) + "\t" + sha256(linesBytes(canarySet))
                        + "\t0\t0\t0\tPASS"));
        Files.writeString(root.resolve("findings.tsv"),
                "canaryId\tsinkKind\tevidencePath\tfingerprint\tstatus\n", StandardCharsets.UTF_8);
        write(root.resolve("summary.tsv"), List.of(
                "sensitiveLogFindings\tanalysisErrors\tstatus", "0\t0\tPASS"));
        return new SensitivePaths(receiptPath, scope);
    }

    private static void addScope(List<String> rows, String root, Path evidence, String relative)
            throws Exception {
        rows.add(root + "\t" + relative + "\t"
                + sha256(Files.readAllBytes(evidence.resolve(relative))));
    }

    private static String command(
            int ordinal, String commandId, String executionId, String scopeSha, String reportSha,
            String time, String commandSha) {
        return ordinal + "\t" + commandId + "\t" + executionId + "\t" + commandSha
                + "\t" + scopeSha + "\t0\t0\t0\t" + reportSha
                + "\t" + time + "\t" + time + "\tPASS";
    }

    private static String commandSha(Path policy, String executionId) throws Exception {
        Path executionPolicy = policy.getParent().resolve(
                ReleaseToolchainEvidenceTestFixture.policyValue(policy, "releaseExecutionPolicy"));
        return Files.readAllLines(executionPolicy, StandardCharsets.UTF_8).stream()
                .skip(1)
                .map(line -> line.split("\t", -1))
                .filter(row -> executionId.equals(row[0]))
                .map(row -> row[3])
                .findFirst()
                .orElseThrow();
    }

    private static List<String> sinks() {
        return List.of(
                "APPLICATION_LOG", "MAVEN_LOG", "EXCEPTION", "METER", "ACTUATOR", "SUREFIRE",
                "FAILSAFE", "DEPENDENCY_TREE", "JSON", "TSV", "ARTIFACT");
    }

    private static void write(Path path, List<String> lines) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static byte[] linesBytes(List<String> lines) {
        return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * @param scope first-pass scope
     * @param selfScope report-only second-pass scope
     * @param commands exact two-command ledger
     * @param receipts 77-row injection receipts
     * @param sensitiveScope 11-sink retained scope
     */
    record Paths(Path scope, Path selfScope, Path commands, Path receipts, Path sensitiveScope) {
    }

    /** @param receipts 77-row canary receipt; @param scope exact 11-sink scope. */
    private record SensitivePaths(Path receipts, Path scope) {
    }
}
