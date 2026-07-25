package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 已提交 production-policy parser fixture 与动态合同 writer 的一致性检查。 */
class ProductionPolicyStaticFixtureContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void staticPolicyPassesAndUnknownKeyFixtureFailsClosed() throws Exception {
        Path repository = repositoryRoot();
        Path fixture = repository.resolve("scripts/release-evidence/fixtures/production-policy");
        if (Boolean.getBoolean("tfi.generateProductionPolicyFixture")) {
            generate(fixture);
        }

        ProcessResult valid = verify(fixture.resolve("policy.tsv"));
        ProcessResult invalid = verify(fixture.resolve("negative/unknown-key-policy.tsv"));

        assertThat(valid.exitCode()).as(valid.output()).isZero();
        assertThat(invalid.exitCode()).isNotZero();
        assertThat(invalid.output()).contains("policy key at line 1 must be policyId");
    }

    private void generate(Path target) throws Exception {
        if (Files.exists(target)) {
            throw new IllegalStateException("static production-policy fixture already exists");
        }
        Path generated = temporaryDirectory.resolve("generated");
        ReleasePolicyParserContractTests.writePolicyFixture(generated, Map.of());
        try (var paths = Files.walk(generated)) {
            for (Path source : paths.sorted().toList()) {
                Path destination = target.resolve(generated.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        Path negative = target.resolve("negative/unknown-key-policy.tsv");
        Files.createDirectories(negative.getParent());
        List<String> rows = new ArrayList<>(Files.readAllLines(
                target.resolve("policy.tsv"), StandardCharsets.UTF_8));
        rows.set(0, rows.getFirst().replace("policyId\t", "unknownPolicyId\t"));
        Files.write(negative, rows, StandardCharsets.UTF_8);
    }

    private static ProcessResult verify(Path policy) throws Exception {
        Path root = repositoryRoot();
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString(),
                "verify-policy",
                policy.toString())
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** @param exitCode verifier exit; @param output redacted verifier output. */
    private record ProcessResult(int exitCode, String output) {
    }
}
