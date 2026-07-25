package com.syy.taskflowinsight.it;

import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证同一稳定门面在升级和回退后保持精确语义，并绑定各阶段真实制品字节。 */
class StableFacadeSmokeTests {

    /** 三阶段共享的唯一稳定公开入口。 */
    @Test
    void stableFacadeUsesExpectedArtifact() throws Exception {
        String phase = requiredProperty("tfi.it.phase");
        String expectedVersion = requiredProperty("tfi.it.expected.version");
        Path repository = realDirectory(requiredProperty("tfi.it.expected.repository"));
        Path manifest = realFile(requiredProperty("tfi.it.expected.sha.manifest"));
        Path codeSourceOutput = outputPath(requiredProperty("tfi.it.codesource.output"));
        assertEquals(phase, codeSourceOutput.getParent().getFileName().toString());

        TFI.loadProviders(Thread.currentThread().getContextClassLoader());
        Object result = TFI.compare(
                new Order("PENDING", new Address("Shanghai"), List.of("paid"),
                        Map.of("items", 2), new LinkedHashSet<>(List.of("buyer", "member"))),
                new Order("SHIPPED", new Address("Shanghai"), List.of("paid"),
                        Map.of("items", 2), new LinkedHashSet<>(List.of("buyer", "member"))));
        assertNotNull(result);

        List<String> semantic = canonicalSemantic(result);
        assertEquals(List.of(
                "kind\tordinal\tvalue",
                "OUTCOME\t1\tDIFFERENT",
                "COMPLETION\t1\tCOMPLETE",
                "CHANGE_PATH\t1\tstatus"), semantic);
        writeDurably(codeSourceOutput.resolveSibling("semantic-result.tsv"), semantic);
        writeCodeSources(codeSourceOutput, repository, manifest, expectedVersion,
                List.of(TFI.class, result.getClass()));
    }

    private static List<String> canonicalSemantic(Object result) throws Exception {
        List<?> changes = (List<?>) invoke(result, "getChanges");
        List<String> paths = new ArrayList<>();
        for (Object change : changes) {
            paths.add(String.valueOf(invoke(change, "getFieldPath")));
        }
        paths = paths.stream().distinct().sorted().toList();
        assertFalse(paths.isEmpty(), "stable input must expose at least one exact change path");

        Object outcome = invokeIfPresent(result, "getOutcome");
        String canonicalOutcome = outcome == null
                ? (Boolean.TRUE.equals(invoke(result, "isIdentical")) ? "EQUAL" : "DIFFERENT")
                : outcome.toString();
        Object completion = invokeIfPresent(result, "getCompletion");
        String canonicalCompletion = completion == null
                ? legacyCompletion(result)
                : completion.toString();
        List<String> rows = new ArrayList<>(List.of(
                "kind\tordinal\tvalue",
                "OUTCOME\t1\t" + canonicalOutcome,
                "COMPLETION\t1\t" + canonicalCompletion));
        for (int index = 0; index < paths.size(); index++) {
            rows.add("CHANGE_PATH\t" + (index + 1) + "\t" + paths.get(index));
        }
        return List.copyOf(rows);
    }

    private static String legacyCompletion(Object result) throws Exception {
        Object reasons = invokeIfPresent(result, "getDegradationReasons");
        return reasons instanceof Collection<?> collection && !collection.isEmpty() ? "PARTIAL" : "COMPLETE";
    }

    private static void writeCodeSources(
            Path output, Path repository, Path manifest, String version, List<Class<?>> types) throws Exception {
        Map<String, String> expectedShas = manifestShas(manifest);
        List<String> rows = new ArrayList<>(List.of(
                "className\trepositoryPath\tactualSha256\texpectedSha256\tstatus"));
        for (Class<?> type : types.stream().sorted(Comparator.comparing(Class::getName)).toList()) {
            URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Path.of(location).toRealPath(LinkOption.NOFOLLOW_LINKS);
            assertTrue(jar.startsWith(repository), type.getName() + " escaped the isolated repository");
            String relative = posix(repository.relativize(jar));
            assertTrue(relative.contains("/" + version + "/") && relative.endsWith("-" + version + ".jar"));
            String expected = expectedShas.get(relative);
            assertNotNull(expected, "manifest has no exact row for " + relative);
            String actual = sha256(Files.readAllBytes(jar));
            assertEquals(expected, actual, "CodeSource bytes changed for " + type.getName());
            rows.add(type.getName() + "\t" + relative + "\t" + actual + "\t" + expected + "\tPASS");
        }
        assertEquals(3, rows.size(), "TFI facade and CompareResult must have distinct evidence rows");
        writeDurably(output, rows);
    }

    private static Map<String, String> manifestShas(Path manifest) throws Exception {
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "SHA manifest is empty");
        Map<String, String> result = new LinkedHashMap<>();
        if (lines.getFirst().contains("\trepositoryPath\t") && lines.getFirst().endsWith("\tsha256")) {
            String[] header = lines.getFirst().split("\t", -1);
            int pathIndex = Arrays.asList(header).indexOf("repositoryPath");
            int shaIndex = Arrays.asList(header).indexOf("sha256");
            for (String line : lines.subList(1, lines.size())) {
                String[] columns = line.split("\t", -1);
                putUnique(result, normalizeManifestPath(columns[pathIndex]), columns[shaIndex]);
            }
        } else {
            for (String line : lines) {
                String[] columns = line.split("  | \\*", 2);
                assertEquals(2, columns.length, "invalid checksum manifest row");
                putUnique(result, normalizeManifestPath(columns[1]), columns[0]);
            }
        }
        return Map.copyOf(result);
    }

    private static void putUnique(Map<String, String> rows, String path, String sha) {
        assertTrue(sha.matches("[0-9a-f]{64}"), "invalid SHA-256 for " + path);
        assertEquals(null, rows.putIfAbsent(path, sha), "duplicate manifest path " + path);
    }

    private static String normalizeManifestPath(String path) {
        String normalized = path.strip().replace('\\', '/');
        for (String prefix : List.of("artifacts/repository/", "repository/")) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return normalized;
    }

    private static Object invoke(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Object invokeIfPresent(Object target, String name) throws Exception {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (NoSuchMethodException absentOnBaseline) {
            return null;
        }
    }

    private static void writeDurably(Path output, List<String> rows) throws Exception {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        byte[] bytes = (String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(output, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "UNSET");
        assertFalse(value.isBlank() || "UNSET".equals(value), name + " must be injected");
        return value;
    }

    private static Path realDirectory(String value) throws Exception {
        Path path = Path.of(value).toRealPath(LinkOption.NOFOLLOW_LINKS);
        assertTrue(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
        return path;
    }

    private static Path realFile(String value) throws Exception {
        Path path = Path.of(value).toRealPath(LinkOption.NOFOLLOW_LINKS);
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        return path;
    }

    private static Path outputPath(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static String posix(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** nested POJO/List/Map/Set 都进入同一稳定输入，唯一业务差异保留在 status。 */
    private record Order(String status, Address address, List<String> tags,
                         Map<String, Integer> totals, LinkedHashSet<String> roles) {
    }

    /** 嵌套对象用于证明回滚不是只比较两个标量。 */
    private record Address(String city) {
    }
}
