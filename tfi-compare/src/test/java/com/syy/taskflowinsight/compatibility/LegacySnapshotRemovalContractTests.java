package com.syy.taskflowinsight.compatibility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy snapshot 第二执行图的制品级删除合同。
 */
class LegacySnapshotRemovalContractTests {

    @Test
    void legacyTypesAndSourcesAreAbsent() throws IOException {
        Path root = CompareApiInventory.repositoryRoot();
        List<String> retiredTypes = List.of(
                "com.syy.taskflowinsight.tracking.snapshot.DirectSnapshotProvider",
                "com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot",
                "com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep",
                "com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig",
                "com.syy.taskflowinsight.tracking.snapshot.SnapshotFacade",
                "com.syy.taskflowinsight.tracking.snapshot.SnapshotProvider",
                "com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders",
                "com.syy.taskflowinsight.tracking.snapshot.filter.ClassLevelFilterEngine",
                "com.syy.taskflowinsight.tracking.snapshot.filter.DefaultExclusionEngine",
                "com.syy.taskflowinsight.tracking.snapshot.filter.FilterDecision",
                "com.syy.taskflowinsight.tracking.snapshot.filter.FilterReason",
                "com.syy.taskflowinsight.tracking.ssot.path.PathNavigator");
        Path sourceRoot = root.resolve("tfi-compare/src/main/java");
        Path classesRoot = root.resolve("tfi-compare/target/classes");
        Path jarPath = findBuiltJar(root.resolve("tfi-compare/target"), "tfi-compare");

        assertThat(retiredTypes)
                .allSatisfy(type -> {
                    Path relativeType = Path.of(type.replace('.', '/'));
                    assertThat(sourceRoot.resolve(relativeType + ".java")).doesNotExist();
                    assertThat(classesRoot.resolve(relativeType + ".class")).doesNotExist();
                });
        assertThat(productionReferences(root, retiredTypes)).isEmpty();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertThat(retiredTypes)
                    .allSatisfy(type -> assertThat(jar.getJarEntry(classEntry(type))).isNull());
            assertRetainedPathTypes(sourceRoot, classesRoot, jar);
        }

        String comparePolicy = Files.readString(sourceRoot.resolve(
                "com/syy/taskflowinsight/tracking/compare/ComparePolicy.java"));
        String requestSnapshot = Files.readString(sourceRoot.resolve(
                "com/syy/taskflowinsight/tracking/compare/internal/RequestLocalSnapshot.java"));
        assertThat(comparePolicy).contains("PathPattern", "PathPatternCompiler");
        assertThat(requestSnapshot).contains("PathPattern");
    }

    private static List<String> productionReferences(
            Path root, List<String> retiredTypes) throws IOException {
        List<String> references = new ArrayList<>();
        for (Path source : productionFiles(root)) {
            String content = Files.readString(source);
            for (String type : retiredTypes) {
                String simpleName = type.substring(type.lastIndexOf('.') + 1);
                Pattern exactName = Pattern.compile(
                        "(?<![A-Za-z0-9_$])" + Pattern.quote(simpleName)
                                + "(?![A-Za-z0-9_$])");
                if (exactName.matcher(content).find()) {
                    references.add(root.relativize(source).toString().replace('\\', '/')
                            + " -> " + simpleName);
                }
            }
        }
        return List.copyOf(references);
    }

    private static List<Path> productionFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path main = module.resolve("src/main");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> candidates = Files.walk(main)) {
                    files.addAll(candidates.filter(Files::isRegularFile).sorted().toList());
                }
            }
        }
        return List.copyOf(files);
    }

    private static Path findBuiltJar(Path target, String expectedArtifactId) throws IOException {
        Properties coordinates = new Properties();
        try (var input = Files.newInputStream(target.resolve("maven-archiver/pom.properties"))) {
            coordinates.load(input);
        }
        assertThat(coordinates.getProperty("artifactId")).isEqualTo(expectedArtifactId);
        Path currentJar = target.resolve(
                coordinates.getProperty("artifactId") + "-" + coordinates.getProperty("version") + ".jar");
        assertThat(currentJar).as("本轮 Maven 构建的 %s JAR", expectedArtifactId).isRegularFile();
        return currentJar;
    }

    private static void assertRetainedPathTypes(
            Path sourceRoot, Path classesRoot, JarFile jar) {
        for (String type : List.of(
                "com.syy.taskflowinsight.tracking.snapshot.filter.PathPattern",
                "com.syy.taskflowinsight.tracking.snapshot.filter.PathPatternCompiler")) {
            Path relativeType = Path.of(type.replace('.', '/'));
            assertThat(sourceRoot.resolve(relativeType + ".java")).isRegularFile();
            assertThat(classesRoot.resolve(relativeType + ".class")).isRegularFile();
            assertThat(jar.getJarEntry(classEntry(type))).isNotNull();
        }
    }

    private static String classEntry(String type) {
        return type.replace('.', '/') + ".class";
    }
}
