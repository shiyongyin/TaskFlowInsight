package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Final evidence manifest、marker、expected XML 与只读语义合同。 */
class ReleaseEvidenceIntegrityContractTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void completeCiManifestAndExpectedReportAreAcceptedWithoutWrites() throws Exception {
        Fixture fixture = fixture("integrity-pass", validXml());
        String manifestBefore = sha256(Files.readAllBytes(fixture.manifest()));

        ProcessResult result = verify(fixture);

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("INTEGRITY_OK");
        assertThat(sha256(Files.readAllBytes(fixture.manifest()))).isEqualTo(manifestBefore);
    }

    @Test
    void integrityRejectsUnlistedFileHashDriftAndDoctype() throws Exception {
        Fixture extra = fixture("integrity-extra", validXml());
        Files.writeString(extra.evidence().resolve("unlisted.txt"), "extra\n", StandardCharsets.UTF_8);

        Fixture drift = fixture("integrity-drift", validXml());
        Files.writeString(drift.report(), "changed\n", StandardCharsets.UTF_8);

        Fixture doctype = fixture("integrity-doctype",
                "<?xml version=\"1.0\"?><!DOCTYPE testsuite [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        ProcessResult extraResult = verify(extra);
        ProcessResult driftResult = verify(drift);
        ProcessResult doctypeResult = verify(doctype);

        assertThat(extraResult.exitCode()).isNotZero();
        assertThat(extraResult.output()).contains("evidence manifest file closure differs");
        assertThat(driftResult.exitCode()).isNotZero();
        assertThat(driftResult.output()).contains("evidence manifest SHA differs from retained bytes");
        assertThat(doctypeResult.exitCode()).isNotZero();
        assertThat(doctypeResult.output()).contains("expected test report XML is unsafe or malformed");
    }

    private Fixture fixture(String name, String xml) throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve(name));
        Path reports = Files.createDirectories(root.resolve("reports"));
        Path report = reports.resolve("TEST-fixture.xml");
        Files.writeString(report, xml, StandardCharsets.UTF_8);
        List<Path> artifacts = writeCandidateArtifacts(root, "4.0.0");
        Path candidateManifest = root.resolve("metadata/candidate-artifacts.sha256");
        Files.createDirectories(candidateManifest.getParent());
        List<String> candidateRows = new ArrayList<>();
        for (Path artifact : artifacts) {
            String repositoryPath = root.resolve("artifacts/repository").relativize(artifact)
                    .toString().replace('\\', '/');
            candidateRows.add(sha256(Files.readAllBytes(artifact)) + "  " + repositoryPath);
        }
        candidateRows.sort(java.util.Comparator.comparing(row -> row.substring(66)));
        Files.write(candidateManifest, candidateRows, StandardCharsets.UTF_8);
        Path marker = root.resolve("CI_ONLY");
        Files.write(marker, List.of(
                "candidateRevision\t" + "a".repeat(40),
                "candidateSetSha256\t" + sha256(Files.readAllBytes(candidateManifest)),
                "mode\tCI_ONLY"), StandardCharsets.UTF_8);
        Path expected = temporaryDirectory.resolve(name + "-expected-reports.tsv");
        Files.write(expected, List.of(
                "phase\tmodule\treportPath\tminimumTests\tallowSkipped",
                "FOCUSED\ttfi-compare\treports/TEST-fixture.xml\t1\tfalse"),
                StandardCharsets.UTF_8);
        Path retainedReports = root.resolve("metadata/expected-reports.tsv");
        Files.write(retainedReports, Files.readAllBytes(expected));
        Path manifest = root.resolve("evidence-manifest.sha256");
        List<Path> retained = new ArrayList<>(artifacts);
        retained.add(marker);
        retained.add(candidateManifest);
        retained.add(retainedReports);
        retained.add(report);
        retained.sort(java.util.Comparator.comparing(path -> root.relativize(path)
                .toString().replace('\\', '/')));
        List<String> rows = new ArrayList<>();
        for (Path file : retained) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            rows.add(sha256(Files.readAllBytes(file)) + "  " + relative);
        }
        Files.write(manifest, rows, StandardCharsets.UTF_8);
        return new Fixture(root, report, expected, manifest);
    }

    private static List<Path> writeCandidateArtifacts(Path root, String version) throws Exception {
        List<String> repositoryPaths = new ArrayList<>();
        repositoryPaths.add("com/syy/taskflowinsight-parent/" + version
                + "/taskflowinsight-parent-" + version + ".pom");
        for (String artifact : List.of(
                "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight")) {
            String base = "com/syy/" + artifact + "/" + version + "/" + artifact + "-" + version;
            repositoryPaths.add(base + ".jar");
            repositoryPaths.add(base + ".pom");
        }
        List<Path> result = new ArrayList<>();
        for (String repositoryPath : repositoryPaths) {
            Path file = root.resolve("artifacts/repository").resolve(repositoryPath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "fixture " + repositoryPath + "\n", StandardCharsets.UTF_8);
            result.add(file);
        }
        return List.copyOf(result);
    }

    private static String validXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<testsuite name=\"fixture\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>";
    }

    private static ProcessResult verify(Fixture fixture) throws IOException, InterruptedException {
        Path root = repositoryRoot();
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                root.resolve("scripts/release-evidence/ReleaseEvidenceVerifier.java").toString(),
                "verify-integrity", fixture.evidence().toString(), fixture.expectedReports().toString())
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    /** @param evidence finalized evidence root; @param report retained XML; @param expectedReports authority. */
    private record Fixture(Path evidence, Path report, Path expectedReports, Path manifest) {
    }

    /** @param exitCode verifier exit; @param output stable combined output. */
    private record ProcessResult(int exitCode, String output) {
    }
}
