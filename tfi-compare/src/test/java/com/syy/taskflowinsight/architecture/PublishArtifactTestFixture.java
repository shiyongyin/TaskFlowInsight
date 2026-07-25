package com.syy.taskflowinsight.architecture;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 为 assembler 合同生成不依赖 reactor target 的真实发布四件套。 */
final class PublishArtifactTestFixture {

    private PublishArtifactTestFixture() {
    }

    static Fixture write(Path evidence, List<String> manifestLines, Path repositoryRoot) throws Exception {
        Files.createDirectories(evidence.resolve("metadata"));
        Files.createDirectories(evidence.resolve("build/primaries"));
        byte[] license = Files.readAllBytes(repositoryRoot.resolve("LICENSE"));
        String version = coordinate(manifestLines.get(1)).version();
        Map<String, ModuleFixture> modules = new LinkedHashMap<>();
        for (String artifact : List.of(
                "tfi-flow-core", "tfi-flow-spring-starter", "tfi-compare",
                "tfi-compare-spring-starter", "tfi-ops-spring", "TaskFlowInsight")) {
            modules.put(artifact, moduleFixture(evidence.resolve("fixture-work").resolve(artifact),
                    artifact, version, license));
        }

        List<String> inputs = new ArrayList<>();
        inputs.add("kind\tsubjectCoordinate\trole\tmodulePath\tsourceRevisionPath\tretainedPath\tsha256");
        Map<Integer, byte[]> primaryBytes = new HashMap<>();
        Map<String, Path> retained = new HashMap<>();
        for (int index = 1; index <= 25; index++) {
            String[] row = manifestLines.get(index).split("\t", -1);
            Coordinate coordinate = Coordinate.parse(row[2]);
            byte[] bytes = primaryBytes(coordinate, row[4], modules, version);
            String retainedPath = "build/primaries/" + index + suffix(row[4]);
            Path output = evidence.resolve(retainedPath);
            Files.write(output, bytes);
            inputs.add("PRIMARY\t" + row[2] + "\t" + row[4] + "\t"
                    + modulePath(coordinate.artifact()) + "\t-\t" + retainedPath + "\t" + sha256(bytes));
            primaryBytes.put(index, bytes);
            retained.put(coordinate.artifact() + ":" + row[4], output);
        }

        String licensePath = "source-revision/LICENSE";
        Files.createDirectories(evidence.resolve("source-revision"));
        Files.write(evidence.resolve(licensePath), license);
        inputs.add("LICENSE\tcom.syy:taskflowinsight-parent:pom:" + version
                + "\tLICENSE\t.\tLICENSE\t" + licensePath + "\t" + sha256(license));
        for (ModuleFixture module : modules.values()) {
            String retainedPath = "source-revision/" + module.modulePath() + "/" + module.sourceEntry();
            Path output = evidence.resolve(retainedPath);
            Files.createDirectories(output.getParent());
            Files.write(output, module.sourceBytes());
            String revisionPath = module.modulePath() + "/src/main/java/" + module.sourceEntry();
            inputs.add("SOURCE\tcom.syy:" + module.artifact() + ":jar:" + version
                    + "\tSOURCE\t" + module.modulePath() + "\t" + revisionPath + "\t"
                    + retainedPath + "\t" + sha256(module.sourceBytes()));
            retained.put(module.artifact() + ":SOURCE", output);
        }
        Path buildManifest = evidence.resolve("metadata/publish-build-inputs.tsv");
        Files.write(buildManifest, inputs, StandardCharsets.UTF_8);
        return new Fixture(Map.copyOf(primaryBytes), Map.copyOf(retained), buildManifest, evidence);
    }

    private static ModuleFixture moduleFixture(
            Path work, String artifact, String version, byte[] license) throws Exception {
        String modulePath = modulePath(artifact);
        String packageName = "fixture." + artifact.toLowerCase().replace('-', '_');
        String sourceEntry = packageName.replace('.', '/') + "/PublishedType.java";
        byte[] source = ("package " + packageName + ";\n"
                + "/** Published fixture type. */\n"
                + "public final class PublishedType {\n"
                + "    private PublishedType() {}\n"
                + "    /** Published nested type. */\n"
                + "    public static final class Nested { private Nested() {} }\n"
                + "    /** Protected nested type. */\n"
                + "    protected static class ProtectedNested {\n"
                + "        /** Creates a protected nested fixture. */\n"
                + "        protected ProtectedNested() {}\n"
                + "    }\n"
                + "}\n"
                + "/** Package-local fixture type. */\n"
                + "final class HiddenType {}\n").getBytes(StandardCharsets.UTF_8);
        Path sourceFile = work.resolve("src").resolve(sourceEntry);
        Path classes = work.resolve("classes");
        Path docs = work.resolve("docs");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(docs);
        Files.write(sourceFile, source);
        int compile = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "-proc:none", "-d", classes.toString(), sourceFile.toString());
        int javadoc = ToolProvider.getSystemDocumentationTool().run(
                null, null, null, "-quiet", "-notimestamp", "-d", docs.toString(), sourceFile.toString());
        if (compile != 0 || javadoc != 0) {
            throw new IllegalStateException("fixture compiler or Javadoc generation failed");
        }

        byte[] pom = pom(artifact, version, false);
        byte[] manifest = "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> binaryEntries = regularFiles(classes);
        binaryEntries.put("META-INF/MANIFEST.MF", manifest);
        binaryEntries.put("META-INF/LICENSE", license);
        binaryEntries.put("META-INF/maven/com.syy/" + artifact + "/pom.xml", pom);
        Map<String, byte[]> sourceEntries = new LinkedHashMap<>();
        sourceEntries.put("META-INF/MANIFEST.MF", manifest);
        sourceEntries.put(sourceEntry, source);
        return new ModuleFixture(
                artifact, modulePath, sourceEntry, source, pom,
                archive(binaryEntries), archive(sourceEntries), archive(regularFiles(docs)));
    }

    private static byte[] primaryBytes(
            Coordinate coordinate, String role, Map<String, ModuleFixture> modules, String version) {
        if ("taskflowinsight-parent".equals(coordinate.artifact())) {
            return pom(coordinate.artifact(), version, true);
        }
        ModuleFixture module = modules.get(coordinate.artifact());
        return switch (role) {
            case "POM" -> module.pom();
            case "BINARY" -> module.binary();
            case "SOURCES" -> module.sources();
            case "JAVADOC" -> module.javadoc();
            default -> throw new IllegalArgumentException("Unknown fixture role " + role);
        };
    }

    private static byte[] pom(String artifact, String version, boolean parent) {
        String packaging = parent ? "  <packaging>pom</packaging>\n" : "";
        String name = parent ? "TaskFlowInsight Parent" : "Fixture " + artifact;
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.syy</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                %s  <name>%s</name>
                  <description>Production publishability fixture</description>
                  <url>https://github.com/shiyongyin/TaskFlowInsight</url>
                  <licenses>
                    <license>
                      <name>Apache License, Version 2.0</name>
                      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                      <distribution>repo</distribution>
                    </license>
                  </licenses>
                  <developers>
                    <developer>
                      <id>shiyongyin</id>
                      <name>shiyongyin</name>
                      <url>https://github.com/shiyongyin</url>
                    </developer>
                  </developers>
                  <scm>
                    <connection>scm:git:https://github.com/shiyongyin/TaskFlowInsight.git</connection>
                    <developerConnection>scm:git:ssh://git@github.com/shiyongyin/TaskFlowInsight.git</developerConnection>
                    <tag>HEAD</tag>
                    <url>https://github.com/shiyongyin/TaskFlowInsight</url>
                  </scm>
                </project>
                """).formatted(artifact, version, packaging, name).getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> regularFiles(Path root) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                files.put(root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"),
                        Files.readAllBytes(path));
            }
        }
        return files;
    }

    static byte[] archive(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0L);
                jar.putNextEntry(jarEntry);
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String suffix(String role) {
        return switch (role) {
            case "POM" -> ".pom";
            case "BINARY" -> ".jar";
            case "SOURCES" -> "-sources.jar";
            case "JAVADOC" -> "-javadoc.jar";
            default -> ".bin";
        };
    }

    private static String modulePath(String artifact) {
        return switch (artifact) {
            case "taskflowinsight-parent" -> ".";
            case "TaskFlowInsight" -> "tfi-all";
            default -> artifact;
        };
    }

    private static Coordinate coordinate(String manifestLine) {
        return Coordinate.parse(manifestLine.split("\t", -1)[2]);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    record Fixture(
            Map<Integer, byte[]> primaryBytes,
            Map<String, Path> retained,
            Path buildManifest,
            Path evidence) {

        void replaceRetained(String key, byte[] bytes) throws Exception {
            Path path = retained.get(key);
            Files.write(path, bytes);
            String relative = evidence.relativize(path).toString().replace(
                    path.getFileSystem().getSeparator(), "/");
            List<String> lines = new ArrayList<>(Files.readAllLines(buildManifest, StandardCharsets.UTF_8));
            for (int index = 1; index < lines.size(); index++) {
                String[] columns = lines.get(index).split("\t", -1);
                if (relative.equals(columns[5])) {
                    columns[6] = sha256(bytes);
                    lines.set(index, String.join("\t", columns));
                }
            }
            Files.write(buildManifest, lines, StandardCharsets.UTF_8);
        }

        void replaceArchiveEntry(String key, String entryName, byte[] bytes) throws Exception {
            Map<String, byte[]> entries = archiveEntries(Files.readAllBytes(retained.get(key)));
            if (entries.put(entryName, bytes) == null) {
                throw new IllegalArgumentException("Archive entry does not exist: " + entryName);
            }
            replaceRetained(key, archive(entries));
        }

        void addArchiveEntry(String key, String entryName, byte[] bytes) throws Exception {
            Map<String, byte[]> entries = archiveEntries(Files.readAllBytes(retained.get(key)));
            if (entries.putIfAbsent(entryName, bytes) != null) {
                throw new IllegalArgumentException("Archive entry already exists: " + entryName);
            }
            replaceRetained(key, archive(entries));
        }

        void removeArchiveEntry(String key, String entryName) throws Exception {
            Map<String, byte[]> entries = archiveEntries(Files.readAllBytes(retained.get(key)));
            if (entries.remove(entryName) == null) {
                throw new IllegalArgumentException("Archive entry does not exist: " + entryName);
            }
            replaceRetained(key, archive(entries));
        }

        Fixture copyTo(Path target) throws Exception {
            List<String> lines = Files.readAllLines(buildManifest, StandardCharsets.UTF_8);
            for (int index = 1; index < lines.size(); index++) {
                String retainedPath = lines.get(index).split("\t", -1)[5];
                Path destination = target.resolve(retainedPath);
                Files.createDirectories(destination.getParent());
                Files.copy(evidence.resolve(retainedPath), destination);
            }
            Path copiedManifest = target.resolve("metadata/publish-build-inputs.tsv");
            Files.createDirectories(copiedManifest.getParent());
            Files.write(copiedManifest, lines, StandardCharsets.UTF_8);
            Map<String, Path> copiedRetained = new HashMap<>();
            retained.forEach((key, path) -> copiedRetained.put(
                    key, target.resolve(evidence.relativize(path).toString())));
            return new Fixture(primaryBytes, Map.copyOf(copiedRetained), copiedManifest, target);
        }

        private static Map<String, byte[]> archiveEntries(byte[] archive) throws Exception {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        entries.put(entry.getName(), zip.readAllBytes());
                    }
                }
            }
            return entries;
        }
    }

    private record ModuleFixture(
            String artifact,
            String modulePath,
            String sourceEntry,
            byte[] sourceBytes,
            byte[] pom,
            byte[] binary,
            byte[] sources,
            byte[] javadoc) {
    }

    private record Coordinate(String artifact, String version) {
        private static Coordinate parse(String raw) {
            String[] parts = raw.split(":", -1);
            return new Coordinate(parts[1], parts[parts.length - 1]);
        }
    }
}
