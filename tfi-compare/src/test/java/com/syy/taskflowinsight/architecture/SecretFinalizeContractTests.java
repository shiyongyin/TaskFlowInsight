package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 两遍 secret finalize 与 77-row sensitive-log canary 的闭集合同。 */
class SecretFinalizeContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void completeTwoPassSecretAndSensitiveLogEvidenceIsAccepted() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve("valid-secret"));

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("SUPPLY_CHAIN_OK");
    }

    @Test
    void secretFirstPassRequiresExplicitAnalysisErrorsField() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("missing-first-analysis-errors"));
        Path report = fixture.evidence().resolve("security/secret-scan/report.json");
        String raw = Files.readString(report, StandardCharsets.UTF_8);
        Files.writeString(report, raw.replace("\"analysisErrors\":[],", ""),
                StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(
                "secret first-pass report has unknown or missing JSON keys");
    }

    @Test
    void secretFirstPassRejectsScannerAnalysisErrorEvenWhenStatusSaysPass() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("first-analysis-error"));
        Path report = fixture.evidence().resolve("security/secret-scan/report.json");
        String raw = Files.readString(report, StandardCharsets.UTF_8);
        Files.writeString(report,
                raw.replace("\"analysisErrors\":[]", "\"analysisErrors\":[\"SCANNER_TIMEOUT\"]"),
                StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("secret first-pass evidence contains analysis errors");
    }

    @Test
    void secretFirstPassRejectsRegularFileAddedOutsideFrozenEvidenceScope() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("unscoped-evidence-file"));
        Files.writeString(fixture.evidence().resolve("unscoped-ordinary-evidence.txt"),
                "must be scanned\n", StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("secret first-pass EVIDENCE scope closure differs");
    }

    @Test
    void secretFirstPassCandidateScopeMustExactlyMatchRetainedCandidateManifest() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("candidate-scope-omission"));
        Path scope = fixture.evidence().resolve("security/secret-scan/scope.tsv");
        List<String> rows = Files.readAllLines(scope, StandardCharsets.UTF_8);
        rows.remove(1);
        Files.write(scope, rows, StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(
                "secret first-pass CANDIDATE_TREE scope closure differs");
    }

    @Test
    void secretFinalizeRejectsReceiptSelfScopeAndSensitiveResultDrift() throws Exception {
        SupplyChainEvidenceContractTests.Fixture missingReceipt =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve("missing-receipt"));
        Path receipts = missingReceipt.evidence().resolve(
                "security/sensitive-log/injection-receipts.tsv");
        List<String> receiptRows = Files.readAllLines(receipts, StandardCharsets.UTF_8);
        receiptRows.removeLast();
        Files.write(receipts, receiptRows, StandardCharsets.UTF_8);

        SupplyChainEvidenceContractTests.Fixture selfScopeDrift =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve("self-scope-drift"));
        Path selfScope = selfScopeDrift.evidence().resolve(
                "security/secret-scan/report-self-scan-scope.tsv");
        List<String> selfRows = Files.readAllLines(selfScope, StandardCharsets.UTF_8);
        String[] self = selfRows.get(1).split("\t", -1);
        self[2] = "0".repeat(64);
        selfRows.set(1, String.join("\t", self));
        Files.write(selfScope, selfRows, StandardCharsets.UTF_8);

        SupplyChainEvidenceContractTests.Fixture hiddenFinding =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve("hidden-finding"));
        Path rawResult = hiddenFinding.evidence().resolve("security/sensitive-log/raw-result.tsv");
        List<String> rawRows = Files.readAllLines(rawResult, StandardCharsets.UTF_8);
        String[] raw = rawRows.get(1).split("\t", -1);
        raw[6] = "1";
        rawRows.set(1, String.join("\t", raw));
        Files.write(rawResult, rawRows, StandardCharsets.UTF_8);

        ProcessResult receiptResult = verify(missingReceipt);
        ProcessResult scopeResult = verify(selfScopeDrift);
        ProcessResult findingResult = verify(hiddenFinding);

        assertThat(receiptResult.exitCode()).isNotZero();
        assertThat(receiptResult.output()).contains(
                "secret first-pass scoped bytes SHA does not match retained bytes");
        assertThat(scopeResult.exitCode()).isNotZero();
        assertThat(scopeResult.output()).contains("secret self-scan scope SHA differs from retained bytes");
        assertThat(findingResult.exitCode()).isNotZero();
        assertThat(findingResult.output()).contains(
                "secret first-pass scoped bytes SHA does not match retained bytes");
    }

    private static ProcessResult verify(SupplyChainEvidenceContractTests.Fixture fixture)
            throws IOException, InterruptedException {
        Path root = repositoryRoot();
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString());
        command.add("verify-supply-chain");
        command.add(fixture.evidence().toString());
        command.add(fixture.policy().toString());
        Process process = new ProcessBuilder(command)
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** @param exitCode verifier process exit code; @param output redacted combined output. */
    private record ProcessResult(int exitCode, String output) {
    }
}
