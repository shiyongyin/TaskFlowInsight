package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Publishable primary 与 policy-required artifact signature 的闭集合同。 */
class ArtifactSignatureContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiredSigstoreSignatureCannotBeOmitted() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("missing-artifact-signature"));
        SigstoreAttestationTestFixture.addWithMissingArtifactSignature(
                fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("artifact signature results are required");
    }

    @Test
    void malformedArtifactSignatureResultsCannotSatisfyPolicy() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("malformed-artifact-signature-results"));
        SigstoreAttestationTestFixture.addWithMissingArtifactSignature(
                fixture.evidence(), fixture.policy());
        Path results = fixture.evidence().resolve(
                "supply-chain/signatures/artifact-signature-results.tsv");
        Files.createDirectories(results.getParent());
        Files.writeString(results, "PASS\n", StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("artifact signature results has an invalid header");
    }

    @Test
    void selfReportedPassWithoutPublishableSidecarFailsClosed() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("missing-publishable-signature-sidecar"));
        SigstoreAttestationTestFixture.addWithMissingArtifactSignature(
                fixture.evidence(), fixture.policy());
        String[] primary = Files.readAllLines(
                fixture.evidence().resolve("metadata/publishable-artifacts.tsv"),
                StandardCharsets.UTF_8).get(1).split("\t", -1);
        String sidecar = "artifacts/publishable-repository/" + primary[3] + ".sigstore.json";
        Path results = fixture.evidence().resolve(
                "supply-chain/signatures/artifact-signature-results.tsv");
        Files.createDirectories(results.getParent());
        Files.write(results, List.of(
                "subjectOrdinal\tscheme\tsubjectSha256\tsidecarPath\tsidecarSha256\t"
                        + "signerKeyId\tdigestAlgorithm\tsignatureAlgorithm\tintegratedTime\tstatus",
                primary[0] + "\tSIGSTORE\t" + primary[6] + "\t" + sidecar + "\t"
                        + "a".repeat(64) + "\tCN=TFI Test Root|https://builder.example.test/workflow\t"
                        + "SHA2_256\tECDSA_P256_SHA256\t0\tPASS"), StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(
                "artifact signature result does not match a publishable sidecar");
    }

    @Test
    void validSigstoreMessageSignatureClosesPrimaryAndSidecar() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("valid-sigstore-artifact-signature"));
        SigstoreAttestationTestFixture.addWithArtifactSignature(
                fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    @Test
    void unreferencedSigstoreSidecarCannotBypassFirstSecretScope() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("unreferenced-sigstore-sidecar"));
        SigstoreAttestationTestFixture.addWithArtifactSignature(
                fixture.evidence(), fixture.policy());
        Files.writeString(fixture.evidence().resolve(
                        "artifacts/publishable-repository/unreferenced.sigstore.json"),
                "UNSCANNED TEST_ONLY BYTES\n", StandardCharsets.UTF_8);

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(
                "secret first-pass EVIDENCE scope closure differs");
    }

    @Test
    void cryptographicallyInvalidMessageSignatureFailsEvenWhenOuterHashesMatch() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("invalid-message-signature"));
        SigstoreAttestationTestFixture.addWithTamperedArtifactSignature(
                fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore artifact message signature is invalid");
    }

    @Test
    void policyRejectsArtifactTrustSchemeOutsideRequiredSet() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("artifact-trust-scheme-mismatch"));
        SigstoreAttestationTestFixture.addWithMissingArtifactSignature(
                fixture.evidence(), fixture.policy());
        Path trust = fixture.policy().getParent().resolve("trust/artifact-signatures.tsv");
        List<String> trustRows = new ArrayList<>(Files.readAllLines(trust, StandardCharsets.UTF_8));
        trustRows.set(1, trustRows.get(1).replace("SIGSTORE\t", "PGP\t"));
        Files.write(trust, trustRows, StandardCharsets.UTF_8);
        List<String> authorities = new ArrayList<>(Files.readAllLines(
                fixture.policy().getParent().resolve("authorities.tsv"), StandardCharsets.UTF_8));
        String[] artifactAuthority = authorities.get(2).split("\t", -1);
        authorities.set(2, artifactAuthority[0] + "\t" + artifactAuthority[1] + "\t"
                + sha256(Files.readAllBytes(trust)));
        ReleasePolicyParserContractTests.replaceReference(
                fixture.policy(), "productionAuthoritiesManifest", authorities);

        ProcessResult result = run("verify-policy", fixture.policy().toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(
                "artifact signature trust must exactly match required schemes");
    }

    @Test
    void sigstoreIntegratedTimeMustFallInsideArtifactBuildWindow() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(
                        temporaryDirectory.resolve("artifact-signature-outside-build-window"));
        SigstoreAttestationTestFixture.addWithOutOfWindowArtifactSignature(
                fixture.evidence(), fixture.policy());

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("Sigstore artifact signature is outside build window");
    }

    private static ProcessResult verify(SupplyChainEvidenceContractTests.Fixture fixture)
            throws Exception {
        return run("verify-supply-chain", fixture.evidence().toString(), fixture.policy().toString());
    }

    private static ProcessResult run(String... arguments) throws Exception {
        Path root = repositoryRoot();
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** @param exitCode verifier exit; @param output redacted verifier output. */
    private record ProcessResult(int exitCode, String output) {
    }
}
