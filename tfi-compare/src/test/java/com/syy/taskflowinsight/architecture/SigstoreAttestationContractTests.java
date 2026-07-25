package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 三层 Sigstore v0.3 DSSE、证书、Rekor proof 和 predicate 绑定合同。 */
class SigstoreAttestationContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validThreeLayerChainPassesAndTamperedDsseSignatureFails() throws Exception {
        SupplyChainEvidenceContractTests.Fixture valid =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve("valid"));
        SigstoreAttestationTestFixture.add(valid.evidence(), valid.policy());
        SupplyChainEvidenceContractTests.Fixture tampered =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve("tampered"));
        SigstoreAttestationTestFixture.Paths paths =
                SigstoreAttestationTestFixture.add(tampered.evidence(), tampered.policy());
        String bundle = Files.readString(paths.artifact(), StandardCharsets.UTF_8);
        int signatureStart = bundle.indexOf("\"sig\":\"") + "\"sig\":\"".length();
        char replacement = bundle.charAt(signatureStart) == 'A' ? 'B' : 'A';
        Files.writeString(paths.artifact(), bundle.substring(0, signatureStart) + replacement
                + bundle.substring(signatureStart + 1), StandardCharsets.UTF_8);

        ProcessResult validResult = verify(valid);
        ProcessResult tamperedResult = verify(tampered);

        assertThat(validResult.exitCode()).as(validResult.output()).isZero();
        assertThat(tamperedResult.exitCode()).isNotZero();
        assertThat(tamperedResult.output()).contains("Sigstore DSSE signature is invalid");
    }

    @Test
    void fulcioOidcIssuerExtensionMatchesTrustedBuilder() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("oidc-issuer"));
        SigstoreAttestationTestFixture.addWithOidcIssuer(fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    @Test
    void multiLeafRekorInclusionProofPassesOfflineVerification() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("multi-leaf-proof"));
        SigstoreAttestationTestFixture.addWithMultiLeafProof(
                fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    @Test
    void tamperedSignedEntryTimestampFailsClosed() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("tampered-set"));
        SigstoreAttestationTestFixture.Paths paths =
                SigstoreAttestationTestFixture.add(fixture.evidence(), fixture.policy());
        tamperBase64Field(paths.artifact(), "\"signedEntryTimestamp\":\"");

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore signed entry timestamp is invalid");
    }

    @Test
    void tamperedMerkleRootFailsClosed() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("tampered-merkle-root"));
        SigstoreAttestationTestFixture.Paths paths =
                SigstoreAttestationTestFixture.add(fixture.evidence(), fixture.policy());
        tamperBase64Field(paths.artifact(), "\"rootHash\":\"");

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore Merkle inclusion proof is invalid");
    }

    @Test
    void tamperedCheckpointSignatureFailsClosed() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("tampered-checkpoint"));
        SigstoreAttestationTestFixture.Paths paths =
                SigstoreAttestationTestFixture.add(fixture.evidence(), fixture.policy());
        tamperBase64Field(paths.artifact(), "\u2014 tfi.test.log ", 8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore checkpoint signature is invalid");
    }

    @Test
    void tamperedCheckpointKeyHintFailsClosed() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("tampered-checkpoint-key-hint"));
        SigstoreAttestationTestFixture.Paths paths =
                SigstoreAttestationTestFixture.add(fixture.evidence(), fixture.policy());
        tamperBase64Field(paths.artifact(), "\u2014 tfi.test.log ");

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore checkpoint key hint is invalid");
    }

    @Test
    void transparencyIntegrationCannotPrecedeSignedPredicateFacts() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("future-predicate-times"));
        SigstoreAttestationTestFixture.addWithFuturePredicateTimes(
                fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(
                "Sigstore attestation integratedTime precedes signed predicate facts");
    }

    private static void tamperBase64Field(Path path, String marker) throws Exception {
        tamperBase64Field(path, marker, 0);
    }

    private static void tamperBase64Field(Path path, String marker, int offset) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        int markerIndex = content.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalStateException("Sigstore fixture field is missing: " + marker);
        }
        int start = markerIndex + marker.length() + offset;
        char replacement = content.charAt(start) == 'A' ? 'B' : 'A';
        Files.writeString(path, content.substring(0, start) + replacement
                + content.substring(start + 1), StandardCharsets.UTF_8);
    }

    private static ProcessResult verify(SupplyChainEvidenceContractTests.Fixture fixture)
            throws Exception {
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

    private record ProcessResult(int exitCode, String output) {
    }
}
