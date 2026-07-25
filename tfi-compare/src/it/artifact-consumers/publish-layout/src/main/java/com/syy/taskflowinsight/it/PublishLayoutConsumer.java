package com.syy.taskflowinsight.it;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从隔离 Maven repository 加载六个发布模块，并把真实 CodeSource 字节与 assembler manifest 对齐。
 */
public final class PublishLayoutConsumer {

    /** 代表类型到其唯一 owner artifactId 的固定映射。 */
    private static final Map<String, String> OWNER_TYPES = ownerTypes();

    private PublishLayoutConsumer() {
    }

    /**
     * 执行隔离制品身份验证。
     *
     * @param args artifact manifest、隔离本地仓库与 final version
     * @throws Exception 任何闭集、路径或摘要不一致都直接阻断 Maven verify
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || List.of(args).contains("UNSET")) {
            throw new IllegalArgumentException(
                    "Expected publishable-artifacts.tsv, isolated repository, and final version");
        }
        Path manifest = Path.of(args[0]).toAbsolutePath().normalize();
        Path isolatedRepository = Path.of(args[1]).toAbsolutePath().normalize();
        String version = args[2];
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(isolatedRepository, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Consumer evidence paths are not sealed regular locations");
        }

        Map<String, String> expectedShas = binaryShas(manifest, version);
        Map<String, Path> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, String> owner : OWNER_TYPES.entrySet()) {
            String coordinate = "com.syy:" + owner.getValue() + ":jar:" + version;
            String expectedSha = expectedShas.get(coordinate);
            if (expectedSha == null) {
                throw new IllegalStateException("Missing binary manifest row for " + coordinate);
            }
            Class<?> type = Class.forName(owner.getKey(), false, PublishLayoutConsumer.class.getClassLoader());
            URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Path.of(location).toAbsolutePath().normalize();
            if (!jar.startsWith(isolatedRepository)
                    || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)
                    || !expectedSha.equals(sha256(Files.readAllBytes(jar)))) {
                throw new IllegalStateException("CodeSource does not match publish layout for " + coordinate);
            }
            loaded.put(coordinate, jar);
        }
        if (!loaded.keySet().equals(expectedShas.keySet())) {
            throw new IllegalStateException("Loaded binary closure differs from publish manifest: " + loaded.keySet());
        }
        System.out.println("PUBLISH_LAYOUT_CONSUMER_OK");
    }

    private static Map<String, String> binaryShas(Path manifest, String version) throws Exception {
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        String header = "ordinal\tsubjectOrdinal\tsubjectCoordinate\trepositoryPath\trole\tsidecarKind\tsha256";
        if (lines.size() != 76 || !header.equals(lines.getFirst())) {
            throw new IllegalStateException("Publishable artifact manifest shape changed");
        }
        Map<String, String> binaries = new HashMap<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] columns = lines.get(index).split("\t", -1);
            if (columns.length != 7) {
                throw new IllegalStateException("Publishable artifact row shape changed");
            }
            if ("BINARY".equals(columns[4])) {
                if (!columns[2].endsWith(":" + version)
                        || !columns[6].matches("[0-9a-f]{64}")
                        || binaries.putIfAbsent(columns[2], columns[6]) != null) {
                    throw new IllegalStateException("Invalid binary manifest row: " + columns[2]);
                }
            }
        }
        if (binaries.size() != OWNER_TYPES.size()) {
            throw new IllegalStateException("Binary manifest closure must contain six modules");
        }
        return Map.copyOf(binaries);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Map<String, String> ownerTypes() {
        Map<String, String> owners = new LinkedHashMap<>();
        owners.put("com.syy.taskflowinsight.api.TfiFlow", "tfi-flow-core");
        owners.put("com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate", "tfi-flow-spring-starter");
        owners.put("com.syy.taskflowinsight.tracking.compare.CompareService", "tfi-compare");
        owners.put("com.syy.taskflowinsight.compare.spring.TfiCompareProperties", "tfi-compare-spring-starter");
        owners.put("com.syy.taskflowinsight.store.StoreConfig", "tfi-ops-spring");
        owners.put("com.syy.taskflowinsight.api.TFI", "TaskFlowInsight");
        return Map.copyOf(owners);
    }
}
