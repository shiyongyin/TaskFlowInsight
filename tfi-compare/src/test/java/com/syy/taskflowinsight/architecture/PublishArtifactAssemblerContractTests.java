package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线发布制品 assembler 的 CLI、输入和内容闭集合同。 */
class PublishArtifactAssemblerContractTests {

    private static final List<String> POLICY_KEYS = List.of(
            "policyId", "reviewAssignmentId", "repository", "protectedRef", "candidateRevision",
            "finalVersion", "releaseTarget", "externalPublicationAuthority",
            "externalPublicationAuthoritySha256", "publishArtifactManifest",
            "publishArtifactManifestSha256", "runtimePerformancePolicy",
            "runtimePerformancePolicySha256", "compatibilityMatrix", "compatibilityMatrixSha256",
            "releaseExecutionPolicy", "releaseExecutionPolicySha256", "buildToolchainManifest",
            "buildToolchainManifestSha256", "productionAuthoritiesManifest",
            "productionAuthoritiesManifestSha256", "trustedBuilder", "provenanceWorkflow",
            "licensePolicy", "licensePolicySha256", "vulnerabilityScanner",
            "vulnerabilityFailCvssThreshold", "vulnerabilityDbMaxAgeHours", "secretScanner",
            "sbomGenerator", "sbomFormat", "requiredSignatures");

    @TempDir
    Path temporaryDirectory;

    @Test
    void cliRejectsUnknownModeWrongArityAndMissingInputs() throws Exception {
        ProcessResult unknown = runAssembler("inspect", "unused", "unused");
        ProcessResult wrongArity = runAssembler("assemble", "unused");
        ProcessResult missingInputs = runAssembler(
                "assemble",
                temporaryDirectory.resolve("missing-evidence").toString(),
                temporaryDirectory.resolve("missing-policy.tsv").toString());

        assertThat(unknown.exitCode()).isNotZero();
        assertThat(wrongArity.exitCode()).isNotZero();
        assertThat(missingInputs.exitCode()).isNotZero();
        assertThat(unknown.output()).contains("unknown mode: inspect");
        assertThat(wrongArity.output()).contains("Usage: PublishArtifactAssembler assemble");
        assertThat(missingInputs.output()).contains("evidence directory is not a readable directory");
    }

    @Test
    void productionPolicyRejectsMutableVersionOrderTraversalAndDigestMismatch() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("evidence"));
        Path manifest = temporaryDirectory.resolve("publish-manifest.tsv");
        Files.writeString(manifest,
                "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind\n",
                StandardCharsets.UTF_8);

        Path mutable = writePolicy("mutable.tsv", Map.of("finalVersion", "4.0.0-SNAPSHOT"), manifest);
        ProcessResult mutableResult = runAssembler("assemble", evidence.toString(), mutable.toString());
        assertThat(mutableResult.exitCode()).isNotZero();
        assertThat(mutableResult.output()).contains("finalVersion must be a fixed non-SNAPSHOT version");

        Path wrongOrder = writePolicy("wrong-order.tsv", Map.of(), manifest);
        List<String> reordered = Files.readAllLines(wrongOrder, StandardCharsets.UTF_8);
        String first = reordered.get(0);
        reordered.set(0, reordered.get(1));
        reordered.set(1, first);
        Files.write(wrongOrder, reordered, StandardCharsets.UTF_8);
        ProcessResult orderResult = runAssembler("assemble", evidence.toString(), wrongOrder.toString());
        assertThat(orderResult.exitCode()).isNotZero();
        assertThat(orderResult.output()).contains("policy key at line 1 must be policyId");

        Path traversal = writePolicy(
                "traversal.tsv", Map.of("publishArtifactManifest", "../publish-manifest.tsv"), manifest);
        ProcessResult traversalResult = runAssembler("assemble", evidence.toString(), traversal.toString());
        assertThat(traversalResult.exitCode()).isNotZero();
        assertThat(traversalResult.output()).contains("publishArtifactManifest must be a relative POSIX path");

        Path digestMismatch = writePolicy(
                "digest-mismatch.tsv", Map.of("publishArtifactManifestSha256", "0".repeat(64)), manifest);
        ProcessResult digestResult = runAssembler("assemble", evidence.toString(), digestMismatch.toString());
        assertThat(digestResult.exitCode()).isNotZero();
        assertThat(digestResult.output()).contains("publishArtifactManifestSha256 does not match retained bytes");
    }

    @Test
    void publishManifestRejectsNonMavenPathAndUnexpectedKernelPrimary() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("manifest-evidence"));
        List<String> valid = publishManifestLines("4.0.0");

        List<String> traversalLines = new ArrayList<>(valid);
        String[] traversalRow = traversalLines.get(1).split("\t", -1);
        traversalRow[3] = "../" + traversalRow[3];
        traversalLines.set(1, String.join("\t", traversalRow));
        Path traversalManifest = temporaryDirectory.resolve("traversal-manifest.tsv");
        Files.write(traversalManifest, traversalLines, StandardCharsets.UTF_8);
        Path traversalPolicy = writePolicy("traversal-manifest-policy.tsv", Map.of(), traversalManifest);
        ProcessResult traversal = runAssembler(
                "assemble", evidence.toString(), traversalPolicy.toString());
        assertThat(traversal.exitCode()).isNotZero();
        assertThat(traversal.output()).contains("repositoryPath must be a relative Maven2 path");

        List<String> kernelLines = new ArrayList<>(valid);
        int primaryOrdinal = kernelLines.size();
        String coordinate = "com.syy:tfi-kernel:pom:4.0.0";
        String primaryPath = "com/syy/tfi-kernel/4.0.0/tfi-kernel-4.0.0.pom";
        kernelLines.add(primaryOrdinal + "\t-\t" + coordinate + "\t" + primaryPath + "\tPOM\t-");
        kernelLines.add((primaryOrdinal + 1) + "\t" + primaryOrdinal + "\t" + coordinate + "\t"
                + primaryPath + ".sha256\tCHECKSUM\tSHA256");
        kernelLines.add((primaryOrdinal + 2) + "\t" + primaryOrdinal + "\t" + coordinate + "\t"
                + primaryPath + ".sha512\tCHECKSUM\tSHA512");
        Path kernelManifest = temporaryDirectory.resolve("kernel-manifest.tsv");
        Files.write(kernelManifest, kernelLines, StandardCharsets.UTF_8);
        Path kernelPolicy = writePolicy("kernel-policy.tsv", Map.of(), kernelManifest);
        ProcessResult kernel = runAssembler("assemble", evidence.toString(), kernelPolicy.toString());
        assertThat(kernel.exitCode()).isNotZero();
        assertThat(kernel.output()).contains("publish primary closure contains unexpected entry");
    }

    @Test
    void assemblerCreatesExactMavenLayoutChecksumsAndStructuredManifests() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("assembly-evidence"));
        Files.createDirectories(evidence.resolve("metadata"));
        Files.createDirectories(evidence.resolve("build/primaries"));
        List<String> manifestLines = publishManifestLines("4.0.0");
        Path manifest = temporaryDirectory.resolve("valid-publish-manifest.tsv");
        Files.write(manifest, manifestLines, StandardCharsets.UTF_8);
        Path policy = writePolicy("valid-policy.tsv", Map.of(), manifest);
        PublishArtifactTestFixture.Fixture fixture = writeBuildInputs(evidence, manifestLines);

        ProcessResult result = runAssembler("assemble", evidence.toString(), policy.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        Path repository = evidence.resolve("artifacts/publishable-repository");
        try (Stream<Path> files = Files.walk(repository)) {
            assertThat(files.filter(Files::isRegularFile)).hasSize(75);
        }
        String firstPrimaryPath = manifestLines.get(1).split("\t", -1)[3];
        Path firstPrimary = repository.resolve(firstPrimaryPath);
        assertThat(Files.readAllBytes(firstPrimary)).isEqualTo(fixture.primaryBytes().get(1));
        assertThat(Files.readString(Path.of(firstPrimary + ".sha256"), StandardCharsets.UTF_8))
                .isEqualTo(sha256(fixture.primaryBytes().get(1)) + "\n");
        assertThat(Files.readString(Path.of(firstPrimary + ".sha512"), StandardCharsets.UTF_8))
                .isEqualTo(sha512(fixture.primaryBytes().get(1)) + "\n");

        List<String> artifactRows = Files.readAllLines(
                evidence.resolve("metadata/publishable-artifacts.tsv"), StandardCharsets.UTF_8);
        assertThat(artifactRows).hasSize(76);
        assertThat(artifactRows.getFirst()).isEqualTo(
                "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind\tsha256");
        List<String> contentRows = Files.readAllLines(
                evidence.resolve("metadata/publishable-content.tsv"), StandardCharsets.UTF_8);
        assertThat(contentRows).hasSize(13);
        assertThat(contentRows.getFirst())
                .startsWith("binaryCoordinate\tlogicalTypeName\tvisibility\tsourceRevisionPath");
        assertThat(contentRows.subList(1, contentRows.size())).allSatisfy(row -> {
            String[] columns = row.split("\t", -1);
            assertThat(columns).hasSize(11);
            assertThat(columns[0]).startsWith("com.syy:").endsWith(":jar:4.0.0");
            assertThat(columns[4]).matches("[0-9a-f]{64}");
            assertThat(columns[6]).matches("[0-9a-f]{64}");
            assertThat(columns[7]).matches("[0-9a-f]{64}");
            assertThat(columns[10]).isEqualTo("PASS");
            if ("PUBLIC_API".equals(columns[2])) {
                assertThat(columns[8]).endsWith("PublishedType.html");
                assertThat(columns[9]).matches("[0-9a-f]{64}");
            } else {
                assertThat(columns[2]).isEqualTo("NON_PUBLIC");
                assertThat(columns[8]).isEqualTo("-");
                assertThat(columns[9]).isEqualTo("-");
            }
        });
        assertThat(contentRows.subList(1, contentRows.size()))
                .filteredOn(row -> row.contains("\tPUBLIC_API\t"))
                .hasSize(6);
        assertThat(contentRows.subList(1, contentRows.size()))
                .filteredOn(row -> row.contains("\tNON_PUBLIC\t"))
                .hasSize(6);
    }

    @Test
    void contentVerifierRejectsPomLicenseSourceAndJavadocDriftWithoutPartialOutput() throws Exception {
        Path baseEvidence = Files.createDirectory(temporaryDirectory.resolve("content-base"));
        List<String> manifestLines = publishManifestLines("4.0.0");
        Path manifest = temporaryDirectory.resolve("content-publish-manifest.tsv");
        Files.write(manifest, manifestLines, StandardCharsets.UTF_8);
        Path policy = writePolicy("content-policy.tsv", Map.of(), manifest);
        PublishArtifactTestFixture.Fixture base = writeBuildInputs(baseEvidence, manifestLines);

        PublishArtifactTestFixture.Fixture pom = base.copyTo(
                temporaryDirectory.resolve("invalid-pom"));
        Path pomPath = pom.retained().get("tfi-compare:POM");
        pom.replaceRetained("tfi-compare:POM", Files.readString(pomPath)
                .replace("Production publishability fixture", "")
                .getBytes(StandardCharsets.UTF_8));
        assertAssemblyFails(pom, policy, "retained POM description must not be empty");

        PublishArtifactTestFixture.Fixture license = base.copyTo(
                temporaryDirectory.resolve("invalid-license"));
        license.replaceArchiveEntry(
                "tfi-compare:BINARY", "META-INF/LICENSE", "not Apache-2.0".getBytes(StandardCharsets.UTF_8));
        assertAssemblyFails(license, policy, "binary archive LICENSE does not match retained root LICENSE");

        PublishArtifactTestFixture.Fixture sourceDrift = base.copyTo(
                temporaryDirectory.resolve("source-drift"));
        byte[] changedSource = (Files.readString(sourceDrift.retained().get("tfi-compare:SOURCE"))
                + "// retained revision drift\n").getBytes(StandardCharsets.UTF_8);
        sourceDrift.replaceRetained("tfi-compare:SOURCE", changedSource);
        assertAssemblyFails(sourceDrift, policy,
                "sources archive entry differs from retained production source");

        String sourceEntry = "fixture/tfi_compare/PublishedType.java";
        byte[] dummySource = "package fixture.tfi_compare;\n// no top-level type\n"
                .getBytes(StandardCharsets.UTF_8);
        PublishArtifactTestFixture.Fixture dummy = base.copyTo(
                temporaryDirectory.resolve("dummy-source"));
        dummy.replaceRetained("tfi-compare:SOURCE", dummySource);
        dummy.replaceArchiveEntry("tfi-compare:SOURCES", sourceEntry, dummySource);
        assertAssemblyFails(dummy, policy, "production source does not declare a top-level type");

        PublishArtifactTestFixture.Fixture extra = base.copyTo(
                temporaryDirectory.resolve("extra-source"));
        extra.addArchiveEntry("tfi-compare:SOURCES", "fixture/tfi_compare/Extra.java", dummySource);
        assertAssemblyFails(extra, policy,
                "sources archive and retained production source closure differ");

        PublishArtifactTestFixture.Fixture missing = base.copyTo(
                temporaryDirectory.resolve("missing-source"));
        missing.removeArchiveEntry("tfi-compare:SOURCES", sourceEntry);
        assertAssemblyFails(missing, policy,
                "sources archive and retained production source closure differ");

        PublishArtifactTestFixture.Fixture missingJavadoc = base.copyTo(
                temporaryDirectory.resolve("missing-javadoc"));
        missingJavadoc.removeArchiveEntry(
                "tfi-compare:JAVADOC", "fixture/tfi_compare/PublishedType.html");
        assertAssemblyFails(missingJavadoc, policy,
                "Javadoc archive is missing entry fixture/tfi_compare/PublishedType.html");

        PublishArtifactTestFixture.Fixture dummyJavadoc = base.copyTo(
                temporaryDirectory.resolve("dummy-javadoc"));
        dummyJavadoc.addArchiveEntry(
                "tfi-compare:JAVADOC", "fixture/tfi_compare/Fake.html", "<html></html>".getBytes(StandardCharsets.UTF_8));
        assertAssemblyFails(dummyJavadoc, policy,
                "Javadoc type page is not generated type documentation");
    }

    @Test
    void assemblerRejectsSymbolicPublishOutputParent() throws Exception {
        Path evidence = Files.createDirectory(temporaryDirectory.resolve("symbolic-output-evidence"));
        List<String> manifestLines = publishManifestLines("4.0.0");
        Path manifest = temporaryDirectory.resolve("symbolic-output-manifest.tsv");
        Files.write(manifest, manifestLines, StandardCharsets.UTF_8);
        Path policy = writePolicy("symbolic-output-policy.tsv", Map.of(), manifest);
        writeBuildInputs(evidence, manifestLines);
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside-publish-output"));
        Files.createSymbolicLink(evidence.resolve("artifacts"), outside);

        ProcessResult result = runAssembler("assemble", evidence.toString(), policy.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("publish output parent must be an owned directory: artifacts");
        try (Stream<Path> outsideFiles = Files.list(outside)) {
            assertThat(outsideFiles).isEmpty();
        }
        assertThat(evidence.resolve("metadata/publishable-artifacts.tsv")).doesNotExist();
        assertThat(evidence.resolve("metadata/publishable-content.tsv")).doesNotExist();
    }

    private static void assertAssemblyFails(
            PublishArtifactTestFixture.Fixture fixture, Path policy, String expectedMessage) throws Exception {
        ProcessResult result = runAssembler("assemble", fixture.evidence().toString(), policy.toString());
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(expectedMessage);
        assertThat(fixture.evidence().resolve("artifacts/publishable-repository")).doesNotExist();
        assertThat(fixture.evidence().resolve("metadata/publishable-artifacts.tsv")).doesNotExist();
        assertThat(fixture.evidence().resolve("metadata/publishable-content.tsv")).doesNotExist();
    }

    private static PublishArtifactTestFixture.Fixture writeBuildInputs(
            Path evidence, List<String> manifestLines)
            throws Exception {
        return PublishArtifactTestFixture.write(evidence, manifestLines, repositoryRoot());
    }

    private static List<String> publishManifestLines(String version) {
        List<Primary> primaries = new ArrayList<>();
        primaries.add(primary("taskflowinsight-parent", "POM", "pom", null, version));
        for (String artifact : List.of(
                "tfi-flow-core",
                "tfi-flow-spring-starter",
                "tfi-compare",
                "tfi-compare-spring-starter",
                "tfi-ops-spring",
                "TaskFlowInsight")) {
            primaries.add(primary(artifact, "POM", "pom", null, version));
            primaries.add(primary(artifact, "BINARY", "jar", null, version));
            primaries.add(primary(artifact, "SOURCES", "jar", "sources", version));
            primaries.add(primary(artifact, "JAVADOC", "jar", "javadoc", version));
        }

        List<String> lines = new ArrayList<>();
        lines.add("ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind");
        for (int index = 0; index < primaries.size(); index++) {
            Primary primary = primaries.get(index);
            lines.add((index + 1) + "\t-\t" + primary.coordinate() + "\t"
                    + primary.repositoryPath() + "\t" + primary.role() + "\t-");
        }
        int ordinal = primaries.size() + 1;
        for (int index = 0; index < primaries.size(); index++) {
            Primary primary = primaries.get(index);
            int subjectOrdinal = index + 1;
            lines.add(ordinal++ + "\t" + subjectOrdinal + "\t" + primary.coordinate() + "\t"
                    + primary.repositoryPath() + ".sha256\tCHECKSUM\tSHA256");
            lines.add(ordinal++ + "\t" + subjectOrdinal + "\t" + primary.coordinate() + "\t"
                    + primary.repositoryPath() + ".sha512\tCHECKSUM\tSHA512");
        }
        return List.copyOf(lines);
    }

    private static Primary primary(
            String artifact, String role, String extension, String classifier, String version) {
        String classifierPart = classifier == null ? "" : ":" + classifier;
        String coordinate = "com.syy:" + artifact + ":" + extension + classifierPart + ":" + version;
        String fileClassifier = classifier == null ? "" : "-" + classifier;
        String path = "com/syy/" + artifact + "/" + version + "/"
                + artifact + "-" + version + fileClassifier + "." + extension;
        return new Primary(coordinate, role, path);
    }

    private Path writePolicy(String fileName, Map<String, String> overrides, Path manifest) throws Exception {
        Map<String, String> values = new HashMap<>();
        for (String key : POLICY_KEYS) {
            values.put(key, "value-" + key);
        }
        values.put("policyId", "authority:test-policy");
        values.put("candidateRevision", "a".repeat(40));
        values.put("finalVersion", "4.0.0");
        values.put("publishArtifactManifest", manifest.getFileName().toString());
        values.put("publishArtifactManifestSha256", sha256(Files.readAllBytes(manifest)));
        values.putAll(overrides);

        List<String> lines = POLICY_KEYS.stream()
                .map(key -> key + "\t" + values.get(key))
                .toList();
        Path policy = temporaryDirectory.resolve(fileName);
        Files.write(policy, lines, StandardCharsets.UTF_8);
        return policy;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String sha512(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes));
    }

    private static ProcessResult runAssembler(String... arguments) throws IOException, InterruptedException {
        Path root = repositoryRoot();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add(root.resolve("scripts/release-evidence/PublishArtifactAssembler.java").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private static Path repositoryRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private record Primary(String coordinate, String role, String repositoryPath) {
    }
}
