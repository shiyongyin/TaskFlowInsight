package com.syy.taskflowinsight.it;

import com.syy.taskflowinsight.ops.compare.CompareObservationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 只消费 Ops retained artifact、完全缺少 Compare 时的真实 Boot 启动合同。 */
class OpsOnlyArtifactTests {

    @Test
    void candidateOpsStartsWithoutCompareFromRetainedArtifacts() throws Exception {
        new ApplicationContextRunner().withUserConfiguration(TestApplication.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("observedCompareOperations")).isFalse();
            assertThat(context.containsBean("compareHealthIndicator")).isFalse();
            assertThatThrownBy(() -> Class.forName(
                    "com.syy.taskflowinsight.tracking.compare.CompareEngine",
                    false, context.getClassLoader())).isInstanceOf(ClassNotFoundException.class);
        });
        writeCodeSource(CompareObservationAutoConfiguration.class);
    }

    private static void writeCodeSource(Class<?> type) throws Exception {
        Path repository = Path.of(requiredProperty("tfi.it.expected.repository"))
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path manifest = Path.of(requiredProperty("tfi.it.expected.sha.manifest"))
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        String version = requiredProperty("tfi.it.expected.version");
        URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path jar = Path.of(location).toRealPath(LinkOption.NOFOLLOW_LINKS);
        assertThat(jar).startsWith(repository);
        String relative = repository.relativize(jar).toString().replace('\\', '/');
        assertThat(relative).contains("/" + version + "/").endsWith("-" + version + ".jar");
        String expectedSha = manifestShas(manifest).get(relative);
        assertThat(expectedSha).as(relative).isNotNull();
        String actualSha = sha256(Files.readAllBytes(jar));
        assertThat(actualSha).isEqualTo(expectedSha);
        writeDurably(Path.of(requiredProperty("tfi.it.codesource.output")), List.of(
                "className\trepositoryPath\tactualSha256\texpectedSha256\tstatus",
                type.getName() + "\t" + relative + "\t" + actualSha
                        + "\t" + expectedSha + "\tPASS"));
    }

    private static Map<String, String> manifestShas(Path manifest) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            String[] columns = line.split("  ", -1);
            assertThat(columns).hasSize(2);
            assertThat(columns[0]).matches("[0-9a-f]{64}");
            assertThat(result.putIfAbsent(columns[1], columns[0])).isNull();
        }
        return Map.copyOf(result);
    }

    private static void writeDurably(Path output, List<String> rows) throws Exception {
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        byte[] bytes = (String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
                normalized, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "UNSET");
        assertThat(value).as(name).isNotBlank().isNotEqualTo("UNSET");
        return value;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
