package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Collector CLI、argv authority、权限和临时明文清理合同。 */
class ReleaseEvidenceCollectorContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void collectAndSecretFinalizeUseSealedArgvAndRemoveEphemeralBytes() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture = fixture("collector-pass");
        Path temporaryRoot = Files.createDirectory(temporaryDirectory.resolve("ephemeral"));
        ownerOnly(temporaryRoot);

        ProcessResult collect = run(temporaryRoot, "collect", fixture.evidence(), fixture.policy());
        ProcessResult finalize = run(
                temporaryRoot, "secret-finalize", fixture.evidence(), fixture.policy());

        assertThat(collect.exitCode()).as(collect.output()).isZero();
        assertThat(finalize.exitCode()).as(finalize.output()).isZero();
        try (var entries = Files.list(temporaryRoot)) {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    void collectorRejectsUnknownModeBroadPermissionsAndArgvDigestDrift() throws Exception {
        SupplyChainEvidenceContractTests.Fixture unknown = fixture("collector-unknown");
        Path temporaryRoot = Files.createDirectory(temporaryDirectory.resolve("unknown-tmp"));
        ownerOnly(temporaryRoot);
        ProcessResult unknownResult = run(
                temporaryRoot, "unknown", unknown.evidence(), unknown.policy());

        SupplyChainEvidenceContractTests.Fixture broad = fixture("collector-permissions");
        Files.setPosixFilePermissions(broad.evidence(), PosixFilePermissions.fromString("rwxr-xr-x"));
        ProcessResult broadResult = run(
                temporaryRoot, "collect", broad.evidence(), broad.policy());

        SupplyChainEvidenceContractTests.Fixture drift = fixture("collector-drift");
        String commandId = "CMD-EXEC-VULN";
        Path spec = drift.policy().getParent().resolve("commands/" + commandId + ".argv.tsv");
        Files.writeString(spec, "ordinal\targ\n1\t/usr/bin/false\n", StandardCharsets.UTF_8);
        ProcessResult driftResult = run(
                temporaryRoot, "collect", drift.evidence(), drift.policy());

        assertThat(unknownResult.exitCode()).isEqualTo(2);
        assertThat(unknownResult.output()).contains("Usage:");
        assertThat(broadResult.exitCode()).isEqualTo(2);
        assertThat(broadResult.output()).contains("permissions must deny group and other access");
        assertThat(driftResult.exitCode()).isEqualTo(2);
        assertThat(driftResult.output()).contains("argv authority SHA differs");
    }

    @Test
    void attestFinalFailsClosedWithoutSealedSignerAuthority() throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture = fixture("attest-final-unsealed");
        Path temporaryRoot = Files.createDirectory(temporaryDirectory.resolve("attest-final-tmp"));
        ownerOnly(temporaryRoot);

        ProcessResult result = run(
                temporaryRoot, "attest-final", fixture.evidence(), fixture.policy());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("final attestation command authority is not yet sealed");
        try (var entries = Files.list(temporaryRoot)) {
            assertThat(entries).isEmpty();
        }
    }

    private SupplyChainEvidenceContractTests.Fixture fixture(String name) throws Exception {
        SupplyChainEvidenceContractTests.Fixture fixture =
                SupplyChainEvidenceContractTests.writeFixtureAt(temporaryDirectory.resolve(name));
        ownerOnly(fixture.evidence());
        return fixture;
    }

    private static void ownerOnly(Path directory) throws IOException {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    }

    private static ProcessResult run(
            Path temporaryRoot, String mode, Path evidence, Path policy)
            throws IOException, InterruptedException {
        Path root = repositoryRoot();
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(root.resolve("scripts/collect_tfi_compare_supply_chain_evidence.sh").toString());
        command.add(mode);
        command.add(evidence.toString());
        command.add(policy.toString());
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true);
        builder.environment().put("TMPDIR", temporaryRoot.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** @param exitCode collector exit; @param output redacted combined output. */
    private record ProcessResult(int exitCode, String output) {
    }
}
