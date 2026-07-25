package com.syy.taskflowinsight.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** retained POM 驱动的 compatibility matrix 生成合同。 */
class CompatibilityMatrixFixturePreparerContractTests {

    private static final List<String> ARTIFACTS = List.of(
            "taskflowinsight-parent", "tfi-flow-core", "tfi-flow-spring-starter",
            "tfi-compare", "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight");

    @TempDir
    Path temporaryDirectory;

    @Test
    void retainedPomsProduceExactMatrixAndMissingBaselineStarterEvidence() throws Exception {
        Path baseline = temporaryDirectory.resolve("baseline");
        Path candidate = temporaryDirectory.resolve("candidate");
        writeRepository(baseline, "3.0.0", false);
        writeRepository(candidate, "4.0.0", true);
        Path output = temporaryDirectory.resolve("output");

        ProcessResult result = run("prepare", baseline.toString(), candidate.toString(), "4.0.0", output.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("COMPATIBILITY_MATRIX_PREPARED\t41");
        List<String> matrix = Files.readAllLines(output.resolve("compatibility-matrix.tsv"));
        assertThat(matrix).hasSize(42);
        assertThat(matrix.getFirst()).isEqualTo(
                "edgeKind\tconsumerGa\tconsumerVersion\tdependencyGa\tdependencyVersion"
                        + "\texpected\tenforcement\tevidenceCommandId");
        Map<String, Long> distribution = new HashMap<>();
        matrix.stream().skip(1).map(line -> line.split("\t", -1))
                .forEach(row -> distribution.merge(row[5] + "/" + row[6], 1L, Long::sum));
        assertThat(distribution).containsExactlyInAnyOrderEntriesOf(Map.of(
                "SUPPORTED/ARTIFACT_TEST", 20L,
                "REJECTED/DEPENDENCY_CONVERGENCE", 15L,
                "REJECTED/STARTUP_FAIL_FAST", 2L,
                "SUPPORTED/MAVEN_MODEL_VALIDATION", 4L));
        assertThat(Files.readString(output.resolve("compatibility-pom-inventory.tsv")))
                .contains("BASELINE\tcom.syy:tfi-compare-spring-starter\t3.0.0\t-\t-\t-\t-\tABSENT");
    }

    @Test
    void mutableVersionAndUnsafePomAreRejectedWithoutPartialMatrix() throws Exception {
        Path baseline = temporaryDirectory.resolve("baseline-negative");
        Path candidate = temporaryDirectory.resolve("candidate-negative");
        writeRepository(baseline, "3.0.0", false);
        writeRepository(candidate, "4.0.0", true);

        Path mutableOutput = temporaryDirectory.resolve("mutable-output");
        ProcessResult mutable = run(
                "prepare", baseline.toString(), candidate.toString(), "4.0.0-SNAPSHOT", mutableOutput.toString());
        assertThat(mutable.exitCode()).isNotZero();
        assertThat(mutable.output()).contains("final version must be fixed");

        Path opsPom = pomPath(candidate, "tfi-ops-spring", "4.0.0");
        Files.writeString(opsPom, "<?xml version=\"1.0\"?><!DOCTYPE project SYSTEM \"file:///etc/passwd\">"
                + "<project><artifactId>tfi-ops-spring</artifactId></project>");
        Path unsafeOutput = temporaryDirectory.resolve("unsafe-output");
        ProcessResult unsafe = run(
                "prepare", baseline.toString(), candidate.toString(), "4.0.0", unsafeOutput.toString());
        assertThat(unsafe.exitCode()).isNotZero();
        assertThat(unsafe.output()).contains("not secure well-formed XML");
        assertThat(unsafeOutput.resolve("compatibility-matrix.tsv")).doesNotExist();
    }

    private static void writeRepository(Path repository, String version, boolean candidate) throws Exception {
        for (String artifact : ARTIFACTS) {
            if (!candidate && "tfi-compare-spring-starter".equals(artifact)) {
                continue;
            }
            Path pom = pomPath(repository, artifact, version);
            Files.createDirectories(pom.getParent());
            Files.writeString(pom, pom(artifact, version, candidate), StandardCharsets.UTF_8);
            if (!"taskflowinsight-parent".equals(artifact)) {
                Files.writeString(pom.resolveSibling(artifact + "-" + version + ".jar"),
                        "retained-" + artifact + "-" + version, StandardCharsets.UTF_8);
            }
        }
    }

    private static String pom(String artifact, String version, boolean candidate) {
        String parent = !candidate && SetLike.PARENTED.contains(artifact)
                ? "<parent><groupId>com.syy</groupId><artifactId>taskflowinsight-parent</artifactId>"
                        + "<version>" + version + "</version></parent>" : "";
        List<Dependency> dependencies = dependencies(artifact, candidate);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>"
                + parent + "<groupId>com.syy</groupId><artifactId>" + artifact + "</artifactId>"
                + "<version>" + version + "</version>");
        if (!dependencies.isEmpty()) {
            xml.append("<dependencies>");
            dependencies.forEach(dependency -> xml.append("<dependency><groupId>com.syy</groupId><artifactId>")
                    .append(dependency.artifact()).append("</artifactId><version>").append(version)
                    .append("</version>").append(dependency.optional() ? "<optional>true</optional>" : "")
                    .append("</dependency>"));
            xml.append("</dependencies>");
        }
        return xml.append("</project>").toString();
    }

    private static List<Dependency> dependencies(String artifact, boolean candidate) {
        return switch (artifact) {
            case "tfi-flow-spring-starter", "tfi-compare" -> List.of(new Dependency("tfi-flow-core", false));
            case "tfi-compare-spring-starter" -> List.of(
                    new Dependency("tfi-compare", false), new Dependency("tfi-flow-spring-starter", true));
            case "tfi-ops-spring" -> List.of(
                    new Dependency("tfi-flow-core", false), new Dependency("tfi-compare", true));
            case "TaskFlowInsight" -> allDependencies(candidate);
            default -> List.of();
        };
    }

    private static List<Dependency> allDependencies(boolean candidate) {
        List<Dependency> result = new ArrayList<>(List.of(
                new Dependency("tfi-flow-core", false),
                new Dependency("tfi-flow-spring-starter", false),
                new Dependency("tfi-compare", false),
                new Dependency("tfi-ops-spring", false)));
        if (candidate) {
            result.add(new Dependency("tfi-compare-spring-starter", false));
        }
        return List.copyOf(result);
    }

    private static Path pomPath(Path repository, String artifact, String version) {
        return repository.resolve("com/syy").resolve(artifact).resolve(version)
                .resolve(artifact + "-" + version + ".pom");
    }

    private static ProcessResult run(String... args) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                root.resolve("scripts/release-evidence/CompatibilityMatrixFixturePreparer.java").toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private record Dependency(String artifact, boolean optional) {
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private static final class SetLike {
        private static final List<String> PARENTED = List.of(
                "tfi-flow-spring-starter", "tfi-compare", "tfi-ops-spring", "TaskFlowInsight");
    }
}
