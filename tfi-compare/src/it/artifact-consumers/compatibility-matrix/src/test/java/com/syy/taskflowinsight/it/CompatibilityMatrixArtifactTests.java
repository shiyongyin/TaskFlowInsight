package com.syy.taskflowinsight.it;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.InvocationTargetException;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 对兼容矩阵单行执行真实制品加载或预期启动拒绝。 */
class CompatibilityMatrixArtifactTests {

    /** 每次 Maven invocation 只验证一行，避免一条成功覆盖其他组合。 */
    @Test
    void matrixRowMatchesRetainedArtifactsOrFailsFast() throws Exception {
        String rowKey = required("tfi.matrix.row-key");
        String expected = required("tfi.matrix.expected");
        String enforcement = required("tfi.matrix.enforcement");
        if ("SUPPORTED".equals(expected) && "ARTIFACT_TEST".equals(enforcement)) {
            verifyCodeSources();
        } else if ("REJECTED".equals(expected) && "STARTUP_FAIL_FAST".equals(enforcement)) {
            verifyStartupFailure(required("tfi.matrix.consumer.class"));
        } else {
            fail("dependency convergence rows must fail before Surefire: " + rowKey);
        }
        System.out.println("COMPATIBILITY_ROW_OK\t" + rowKey + "\t" + enforcement);
    }

    private static void verifyCodeSources() throws Exception {
        Path repository = Path.of(required("tfi.matrix.repository"))
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        Map<String, String> consumerManifest = manifest(required("tfi.matrix.consumer.manifest"));
        Map<String, String> dependencyManifest = manifest(required("tfi.matrix.dependency.manifest"));
        List<TypeExpectation> expectations = List.of(
                new TypeExpectation(required("tfi.matrix.consumer.class"), consumerManifest),
                new TypeExpectation(required("tfi.matrix.dependency.class"), dependencyManifest));
        List<String> rows = new ArrayList<>(List.of(
                "className\trepositoryPath\tactualSha256\texpectedSha256\tstatus"));
        for (TypeExpectation expectation : expectations.stream()
                .sorted(Comparator.comparing(TypeExpectation::className)).toList()) {
            Class<?> type = Class.forName(expectation.className(), false,
                    Thread.currentThread().getContextClassLoader());
            URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Path.of(location).toRealPath(LinkOption.NOFOLLOW_LINKS);
            assertTrue(jar.startsWith(repository), type.getName() + " escaped isolated repository");
            String relative = posix(repository.relativize(jar));
            String expectedSha = expectation.shas().get(relative);
            assertNotNull(expectedSha, "manifest has no exact row for " + relative);
            String actualSha = sha256(Files.readAllBytes(jar));
            assertEquals(expectedSha, actualSha, "retained bytes changed for " + type.getName());
            rows.add(type.getName() + "\t" + relative + "\t" + actualSha
                    + "\t" + expectedSha + "\tPASS");
        }
        assertEquals(3, rows.size());
        writeDurably(Path.of(required("tfi.matrix.codesource.output")), rows);
    }

    private static void verifyStartupFailure(String consumerClass) throws Exception {
        String configurationName;
        String expectedMessage;
        ApplicationContextRunner runner = new ApplicationContextRunner();
        if (consumerClass.contains("compare.spring.TfiCompareProperties")) {
            configurationName = "com.syy.taskflowinsight.compare.spring."
                    + "TfiCompareTrackingPrerequisiteAutoConfiguration";
            expectedMessage = "tfi.compare.tracking.enabled=true requires tfi-flow-spring-starter";
            runner = runner.withPropertyValues("tfi.compare.tracking.enabled=true");
        } else if (consumerClass.contains("ops.compare.CompareObservationAutoConfiguration")) {
            configurationName = "com.syy.taskflowinsight.ops.compare."
                    + "LegacyCompareVersionGuardAutoConfiguration";
            expectedMessage = "tfi-ops-spring 4.x is incompatible with tfi-compare 3.x";
        } else {
            throw new AssertionError("no startup classifier for " + consumerClass);
        }
        Class<?> configuration = Class.forName(configurationName);
        runner.withConfiguration(AutoConfigurations.of(configuration)).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure, "mixed optional TFI versions must fail during startup");
            assertTrue(causeMessages(failure).contains(expectedMessage), causeMessages(failure));
        });
    }

    private static String causeMessages(Throwable failure) {
        List<String> messages = new ArrayList<>();
        Throwable current = failure;
        while (current != null && messages.size() < 32) {
            if (current.getMessage() != null) {
                messages.add(current.getMessage());
            }
            current = current instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : current.getCause();
        }
        return String.join(" | ", messages);
    }

    private static Map<String, String> manifest(String value) throws Exception {
        List<String> lines = Files.readAllLines(
                Path.of(value).toRealPath(LinkOption.NOFOLLOW_LINKS), StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "artifact manifest is empty");
        Map<String, String> result = new LinkedHashMap<>();
        if (lines.getFirst().contains("\trepositoryPath\t") && lines.getFirst().endsWith("\tsha256")) {
            String[] header = lines.getFirst().split("\t", -1);
            int pathIndex = Arrays.asList(header).indexOf("repositoryPath");
            int shaIndex = Arrays.asList(header).indexOf("sha256");
            for (String line : lines.subList(1, lines.size())) {
                String[] columns = line.split("\t", -1);
                putUnique(result, normalize(columns[pathIndex]), columns[shaIndex]);
            }
        } else {
            for (String line : lines) {
                String[] columns = line.split("  | \\*", 2);
                assertEquals(2, columns.length, "invalid checksum manifest row");
                putUnique(result, normalize(columns[1]), columns[0]);
            }
        }
        return Map.copyOf(result);
    }

    private static void putUnique(Map<String, String> rows, String path, String sha) {
        assertTrue(sha.matches("[0-9a-f]{64}"), "invalid SHA-256 for " + path);
        assertEquals(null, rows.putIfAbsent(path, sha), "duplicate manifest path " + path);
    }

    private static String normalize(String path) {
        String normalized = path.strip().replace('\\', '/');
        for (String prefix : List.of("artifacts/repository/", "repository/")) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return normalized;
    }

    private static void writeDurably(Path output, List<String> rows) throws Exception {
        output = output.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        byte[] bytes = (String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(output, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
    }

    private static String required(String name) {
        String value = System.getProperty(name, "UNSET");
        assertFalse(value.isBlank() || "UNSET".equals(value), name + " must be injected");
        return value;
    }

    private static String posix(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record TypeExpectation(String className, Map<String, String> shas) {
    }
}
