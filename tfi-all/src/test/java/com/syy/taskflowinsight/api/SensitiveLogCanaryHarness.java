package com.syy.taskflowinsight.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Policy-driven、test-only 的 77-row trusted pre-redaction injection harness。 */
final class SensitiveLogCanaryHarness {
    /** Coverage 允许的七类 raw source。 */
    private static final Set<String> CANARY_KINDS = Set.of(
            "BEFORE_VALUE", "AFTER_VALUE", "CREDENTIAL", "TOKEN", "PII", "ENTITY_KEY", "STORE_KEY");
    /** Coverage 允许的十一类 retained sink。 */
    private static final Set<String> SINK_KINDS = Set.of(
            "APPLICATION_LOG", "MAVEN_LOG", "EXCEPTION", "METER", "ACTUATOR", "SUREFIRE",
            "FAILSAFE", "DEPENDENCY_TREE", "JSON", "TSV", "ARTIFACT");
    /** Ephemeral canary 使用系统安全随机源生成 256-bit entropy。 */
    private final SecureRandom random = new SecureRandom();
    /** 七类 raw source 到生产 redaction 边界的 test-only adapter。 */
    private final SensitiveCanarySourceRedactor redactor = new SensitiveCanarySourceRedactor();
    /** 十一类 sink 的实际 retained-byte driver。 */
    private final SensitiveCanarySinkDrivers sinks;
    /** 明文只允许存在于当前 0700 测试目录对应的进程内。 */
    private final Path root;

    SensitiveLogCanaryHarness(Path root) throws Exception {
        this.root = root;
        try {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 测试平台仍由 @TempDir 提供隔离；生产 runner 由 collector 强制 0700。
        }
        this.sinks = new SensitiveCanarySinkDrivers(root);
    }

    Result execute(Path coveragePath) throws Exception {
        List<Coverage> coverage = readCoverage(coveragePath);
        PreRedactionHook hook = new PreRedactionHook();
        List<Receipt> receipts = new ArrayList<>();
        List<String> ephemeralCanaries = new ArrayList<>();
        Set<String> combinations = new LinkedHashSet<>();
        for (Coverage row : coverage) {
            String canary = newCanary(row);
            ephemeralCanaries.add(canary);
            String canarySha = hook.observe(row.injectionDriverId(), canary);
            String redacted = redactor.redact(row.canaryKind(), canary);
            SensitiveCanarySinkDrivers.Evidence evidence =
                    sinks.retain(row.sinkKind(), row.injectionDriverId(), redacted);
            combinations.add(row.canaryKind() + "\t" + row.sinkKind());
            receipts.add(new Receipt(
                    row.canaryId(), row.canaryKind(), row.sinkKind(), row.injectionDriverId(),
                    canarySha, evidence.relativePath(), evidence.sha256(), "INJECTED"));
        }
        writeReceipts(receipts);
        return new Result(
                List.copyOf(receipts),
                Set.copyOf(hook.observations().keySet()),
                Set.copyOf(combinations),
                retainedBytesExclude(ephemeralCanaries));
    }

    private String newCanary(Coverage row) {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String randomHex = HexFormat.of().formatHex(entropy);
        return switch (row.canaryKind()) {
            case "PII" -> "tfi" + randomHex + "@example.test";
            case "ENTITY_KEY" -> "TFI_" + randomHex + " 123-45-6789";
            default -> "TFI_CANARY_" + row.canaryId() + "_" + randomHex;
        };
    }

    private static List<Coverage> readCoverage(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != 78
                || !"canaryId\tcanaryKind\tsinkKind\tinjectionDriverId".equals(lines.getFirst())) {
            throw new IllegalArgumentException("sensitive-log coverage is not the exact 77-row schema");
        }
        List<Coverage> result = new ArrayList<>();
        Set<String> canaryIds = new LinkedHashSet<>();
        Set<String> combinations = new LinkedHashSet<>();
        Set<String> driverIds = new LinkedHashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] row = lines.get(index).split("\t", -1);
            if (row.length != 4
                    || !row[0].matches("[A-Z0-9][A-Z0-9_-]{0,63}")
                    || !CANARY_KINDS.contains(row[1])
                    || !SINK_KINDS.contains(row[2])
                    || !row[3].equals("DRIVER_" + row[1] + "_" + row[2])
                    || !canaryIds.add(row[0])
                    || !combinations.add(row[1] + "\t" + row[2])
                    || !driverIds.add(row[3])) {
                throw new IllegalArgumentException("sensitive-log coverage row has no trusted driver");
            }
            result.add(new Coverage(row[0], row[1], row[2], row[3]));
        }
        return List.copyOf(result);
    }

    private void writeReceipts(List<Receipt> receipts) throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("canaryId\tcanaryKind\tsinkKind\tinjectionDriverId\tcanarySha256\t"
                + "evidencePath\tevidenceSha256\tinjectionStatus");
        receipts.stream().map(Receipt::tsv).forEach(rows::add);
        Files.write(root.resolve("injection-receipts.tsv"), rows, StandardCharsets.UTF_8);
    }

    private boolean retainedBytesExclude(List<String> canaries) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                if (canaries.stream().anyMatch(bytes::contains)) {
                    return false;
                }
            }
        }
        return true;
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Hook 只保留 driver 到 raw canary SHA 的映射，禁止持有或输出明文。 */
    private static final class PreRedactionHook {
        /** 每个 coverage driver 恰好一次的 hash-only observation。 */
        private final Map<String, String> observations = new LinkedHashMap<>();

        private String observe(String driverId, String rawCanary) throws Exception {
            String digest = sha256(rawCanary.getBytes(StandardCharsets.UTF_8));
            if (observations.putIfAbsent(driverId, digest) != null) {
                throw new IllegalStateException("pre-redaction driver executed more than once");
            }
            return digest;
        }

        private Map<String, String> observations() {
            return Map.copyOf(observations);
        }
    }

    /** @param canaryId policy ID; @param canaryKind raw source; @param sinkKind sink; @param injectionDriverId driver。 */
    private record Coverage(String canaryId, String canaryKind, String sinkKind, String injectionDriverId) {
    }

    /** Hash-only machine receipt；不含 ephemeral canary。 */
    record Receipt(String canaryId, String canaryKind, String sinkKind, String injectionDriverId,
                   String canarySha256, String evidencePath, String evidenceSha256, String status) {
        private String tsv() {
            return String.join("\t", canaryId, canaryKind, sinkKind, injectionDriverId,
                    canarySha256, evidencePath, evidenceSha256, status);
        }
    }

    /** @param receipts 77 receipts; @param preRedactionObservations hook drivers; @param coveredCombinations 7x11; @param allRetainedBytesExcludeCanaries no raw bytes。 */
    record Result(List<Receipt> receipts, Set<String> preRedactionObservations,
                  Set<String> coveredCombinations, boolean allRetainedBytesExcludeCanaries) {
    }
}
