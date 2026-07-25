package com.syy.taskflowinsight.it;

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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 当前 fixture 的 retained JAR CodeSource 证据写入器。 */
final class ArtifactCodeSourceEvidence {
    private ArtifactCodeSourceEvidence() {
    }

    static void write(List<Class<?>> types) throws Exception {
        Path repository = Path.of(requiredProperty("tfi.it.expected.repository"))
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path manifest = Path.of(requiredProperty("tfi.it.expected.sha.manifest"))
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        String version = requiredProperty("tfi.it.expected.version");
        Map<String, String> expected = manifestShas(manifest);
        List<String> rows = new ArrayList<>(List.of(
                "className\trepositoryPath\tactualSha256\texpectedSha256\tstatus"));
        for (Class<?> type : types.stream().sorted(Comparator.comparing(Class::getName)).toList()) {
            URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Path.of(location).toRealPath(LinkOption.NOFOLLOW_LINKS);
            assertThat(jar).startsWith(repository);
            String relative = repository.relativize(jar).toString().replace('\\', '/');
            assertThat(relative).contains("/" + version + "/").endsWith("-" + version + ".jar");
            String expectedSha = expected.get(relative);
            assertThat(expectedSha).as(relative).isNotNull();
            String actualSha = sha256(Files.readAllBytes(jar));
            assertThat(actualSha).isEqualTo(expectedSha);
            rows.add(type.getName() + "\t" + relative + "\t" + actualSha
                    + "\t" + expectedSha + "\tPASS");
        }
        writeDurably(Path.of(requiredProperty("tfi.it.codesource.output")), rows);
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
}
