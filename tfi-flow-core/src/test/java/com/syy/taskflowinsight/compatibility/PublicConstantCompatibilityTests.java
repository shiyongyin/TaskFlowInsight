package com.syy.taskflowinsight.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 公共编译期常量的双向兼容门禁。
 *
 * <p>Java 调用方可能把 {@code public static final} 值内联进自身字节码，
 * 因此字段名和描述符未变化也不代表兼容。
 * 测试同时扫描当前编译产物和发布基线 manifest，避免新增项漏审、删除项残留，
 * 或数值类型被统一字符串化后掩盖差异。
 */
class PublicConstantCompatibilityTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFIG_DEFAULTS = "com.syy.taskflowinsight.internal.ConfigDefaults";
    private static final String CONFIG_KEYS = CONFIG_DEFAULTS + "$Keys";

    @Test
    void should_match_current_public_constants_bidirectionally_with_manifest() throws Exception {
        Map<String, ManifestEntry> manifest = readManifest();
        Map<String, Field> current = scanCurrentConstants();
        BreakingPolicy breakingPolicy = readBreakingPolicy();
        assertThat(breakingPolicy.classes())
                .containsExactlyInAnyOrder(CONFIG_DEFAULTS, CONFIG_KEYS);
        Set<String> requiredManifestKeys = new TreeSet<>(manifest.keySet());
        requiredManifestKeys.removeIf(breakingPolicy::allows);

        assertThat(current.keySet())
                .as("当前 class 与 manifest 必须双向一致；新增、删除或改名均需显式审查")
                .containsExactlyInAnyOrderElementsOf(requiredManifestKeys);
        for (String key : requiredManifestKeys) {
            assertConstantMatches(current.get(key), manifest.get(key));
        }

        assertThat(manifest.keySet()).anyMatch(key -> key.startsWith(CONFIG_DEFAULTS + "#"));
        assertThat(manifest.keySet()).anyMatch(key -> key.startsWith(CONFIG_KEYS + "#"));
    }

    @Test
    void should_keep_manifest_keys_sorted_for_reviewable_diffs() throws Exception {
        List<String> lines = manifestLines();
        List<String> keys = lines.stream().map(PublicConstantCompatibilityTests::keyOf).toList();

        assertThat(keys).containsExactlyElementsOf(keys.stream().sorted().toList());
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void should_reject_a_changed_value_in_controlled_negative_fixture() throws Exception {
        Field field = NegativeConstantFixture.class.getField("VALUE");
        ManifestEntry changedBaseline = new ManifestEntry(
                PublicConstantManifestGenerator.fieldKey(field), "int", 2);

        assertThatThrownBy(() -> assertConstantMatches(field, changedBaseline))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("VALUE")
                .hasMessageContaining("expected: 2")
                .hasMessageContaining("but was: 1");
    }

    @Test
    void should_only_consume_removed_field_or_class_with_constant_evidence() throws Exception {
        JsonNode manifest = MAPPER.readTree("""
                {
                  "entries": [
                    {
                      "symbol": "example.LegacyConstants#REMOVED_FIELD",
                      "kind": "FIELD",
                      "change": "REMOVED",
                      "evidence": ["PUBLIC_CONSTANT_MANIFEST"]
                    },
                    {
                      "symbol": "example.LegacyConstants",
                      "kind": "CLASS",
                      "change": "REMOVED",
                      "evidence": ["PUBLIC_CONSTANT_MANIFEST"]
                    },
                    {
                      "symbol": "example.ChangedConstants#CHANGED_FIELD",
                      "kind": "FIELD",
                      "change": "VALUE_CHANGED",
                      "evidence": ["PUBLIC_CONSTANT_MANIFEST"]
                    },
                    {
                      "symbol": "example.MethodOwner#method()",
                      "kind": "METHOD",
                      "change": "REMOVED",
                      "evidence": ["PUBLIC_CONSTANT_MANIFEST"]
                    },
                    {
                      "symbol": "example.UnprovenConstants#UNPROVEN_FIELD",
                      "kind": "FIELD",
                      "change": "REMOVED",
                      "evidence": ["JAPICMP"]
                    }
                  ]
                }
                """);
        BreakingPolicy policy = readBreakingPolicy(new ByteArrayInputStream(
                MAPPER.writeValueAsBytes(manifest)));

        assertThat(policy.allows("example.LegacyConstants#REMOVED_FIELD")).isTrue();
        assertThat(policy.allows("example.LegacyConstants#ANY_FIELD")).isTrue();
        assertThat(policy.allows("example.ChangedConstants#CHANGED_FIELD")).isFalse();
        assertThat(policy.allows("example.MethodOwner#method()")).isFalse();
        assertThat(policy.allows("example.UnprovenConstants#UNPROVEN_FIELD")).isFalse();
    }

    private static Map<String, Field> scanCurrentConstants() throws Exception {
        Path classesRoot = resolveClassesRoot();
        Map<String, Field> constants = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(classesRoot)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                String className = classesRoot.relativize(classFile).toString()
                        .replace(classFile.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                Class<?> type = Class.forName(className, false,
                        PublicConstantCompatibilityTests.class.getClassLoader());
                for (Field field : type.getFields()) {
                    if (PublicConstantManifestGenerator.isCompileTimeConstant(field)
                            && field.getDeclaringClass().getName().startsWith("com.syy.taskflowinsight.")) {
                        constants.putIfAbsent(PublicConstantManifestGenerator.fieldKey(field), field);
                    }
                }
            }
        }
        return constants;
    }

    private static void assertConstantMatches(Field field, ManifestEntry expected) {
        assertThat(field).as("manifest field %s", expected.key()).isNotNull();
        int modifiers = field.getModifiers();
        assertThat(Modifier.isPublic(modifiers)).as(expected.key() + " public").isTrue();
        assertThat(Modifier.isStatic(modifiers)).as(expected.key() + " static").isTrue();
        assertThat(Modifier.isFinal(modifiers)).as(expected.key() + " final").isTrue();
        assertThat(PublicConstantManifestGenerator.typeName(field))
                .as(expected.key() + " descriptor")
                .isEqualTo(expected.type());
        assertThat(PublicConstantManifestGenerator.readValue(field))
                .as(expected.key() + " value")
                .isEqualTo(expected.value());
    }

    private static Map<String, ManifestEntry> readManifest() throws Exception {
        Map<String, ManifestEntry> entries = new LinkedHashMap<>();
        for (String line : manifestLines()) {
            int equals = line.indexOf('=');
            int colon = line.indexOf(':', equals + 1);
            if (equals <= 0 || colon <= equals + 1) {
                throw new IllegalStateException("Malformed constant manifest line: " + line);
            }
            String key = line.substring(0, equals);
            String type = line.substring(equals + 1, colon);
            Object value = parseValue(type, line.substring(colon + 1));
            if (entries.put(key, new ManifestEntry(key, type, value)) != null) {
                throw new IllegalStateException("Duplicate constant manifest key: " + key);
            }
        }
        return entries;
    }

    private static List<String> manifestLines() throws IOException {
        try (InputStream input = PublicConstantCompatibilityTests.class.getResourceAsStream(
                "/compatibility/public-constants.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing /compatibility/public-constants.properties");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.stripLeading().startsWith("#"))
                    .toList();
        }
    }

    private static Object parseValue(String type, String value) {
        return switch (type) {
            case "boolean" -> Boolean.valueOf(value);
            case "byte" -> Byte.valueOf(value);
            case "char" -> parseChar(value);
            case "short" -> Short.valueOf(value);
            case "int" -> Integer.valueOf(value);
            case "long" -> Long.valueOf(value);
            case "float" -> Float.valueOf(value);
            case "double" -> Double.valueOf(value);
            case "java.lang.String" -> value;
            default -> throw new IllegalStateException("Unsupported constant type: " + type);
        };
    }

    private static char parseChar(String value) {
        if (value.length() != 1) {
            throw new IllegalStateException("Invalid char constant: " + value);
        }
        return value.charAt(0);
    }

    private static BreakingPolicy readBreakingPolicy() throws IOException {
        try (InputStream input = PublicConstantCompatibilityTests.class.getResourceAsStream(
                "/compatibility/breaking-changes-v4.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing /compatibility/breaking-changes-v4.json");
            }
            return readBreakingPolicy(input);
        }
    }

    private static BreakingPolicy readBreakingPolicy(InputStream input) throws IOException {
        JsonNode entries = MAPPER.readTree(input).path("entries");
        Set<String> fields = new TreeSet<>();
        Set<String> classes = new TreeSet<>();
        for (JsonNode entry : entries) {
            String kind = entry.path("kind").asText();
            if (!"REMOVED".equals(entry.path("change").asText())
                    || !("FIELD".equals(kind) || "CLASS".equals(kind))
                    || !hasEvidence(entry, "PUBLIC_CONSTANT_MANIFEST")) {
                continue;
            }
            String symbol = entry.path("symbol").asText();
            if (symbol.contains("*") || symbol.contains("..") || symbol.endsWith(".")) {
                throw new IllegalStateException("Breaking authorization must be an exact symbol: " + symbol);
            }
            if ("FIELD".equals(kind)) {
                fields.add(symbol);
            } else {
                classes.add(symbol);
            }
        }
        return new BreakingPolicy(fields, classes);
    }

    private static boolean hasEvidence(JsonNode entry, String required) {
        // 常量值兼容不是 ABI 比较问题，只有专用 manifest 证据才能授权跳过历史常量。
        for (JsonNode evidence : entry.path("evidence")) {
            if (required.equals(evidence.asText())) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveClassesRoot() {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path direct = moduleRoot.resolve("target/classes/com/syy/taskflowinsight");
        return Files.isDirectory(direct)
                ? direct.getParent().getParent().getParent()
                : moduleRoot.resolve("tfi-flow-core/target/classes");
    }

    private static String keyOf(String line) {
        return line.substring(0, line.indexOf('='));
    }

    private record ManifestEntry(String key, String type, Object value) {
    }

    private record BreakingPolicy(Set<String> fields, Set<String> classes) {
        private boolean allows(String key) {
            return fields.contains(key) || classes.stream().anyMatch(owner -> key.startsWith(owner + "#"));
        }
    }

    /** 负向用例独立于主 class 输出，避免测试 fixture 被扫描进生产常量 manifest。 */
    public static final class NegativeConstantFixture {
        public static final int VALUE = 1;

        private NegativeConstantFixture() {
        }
    }
}
