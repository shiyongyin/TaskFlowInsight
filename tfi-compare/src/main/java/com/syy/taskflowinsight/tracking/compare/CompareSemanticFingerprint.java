package com.syy.taskflowinsight.tracking.compare;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 对生效比较语义生成稳定摘要，供结果诊断解释本次相等域。
 *
 * <p>编码使用字段名、类型标签和长度前缀，集合按语义键排序；因此不会受到分隔符、注册顺序、
 * locale或对象hashCode影响。摘要只用于追踪，不能作为相等证据或独立cache key。</p>
 */
final class CompareSemanticFingerprint {

    /** 对外摘要格式与preimage版本。 */
    private static final String VERSION_PREFIX = "sha256-v1:";
    /** 所有内建执行分支的版本身份；行为变更必须提升对应版本。 */
    private static final List<String> BUILT_IN_ALGORITHMS = List.of(
            "tfi:array:v1",
            "tfi:collection:v1",
            "tfi:identity:v1",
            "tfi:map:v1",
            "tfi:nullness:v1",
            "tfi:object:v1",
            "tfi:set:v1",
            "tfi:type-mismatch:v1");

    /** 当前runtime冻结的Policy事实。 */
    private final ComparePolicy policy;
    /** 当前runtime冻结的custom extension身份，已按语义键排序。 */
    private final List<ExtensionFact> extensions;

    CompareSemanticFingerprint(ComparePolicy policy, List<ExtensionFact> extensions) {
        this.policy = Objects.requireNonNull(policy, "policy");
        List<ExtensionFact> sorted = new ArrayList<>(extensions);
        sorted.sort(Comparator.naturalOrder());
        this.extensions = List.copyOf(sorted);
    }

    String forOptions(CompareOptions options) {
        Objects.requireNonNull(options, "options");
        DigestWriter writer = new DigestWriter();
        writer.text("tfi.compare.semantic-fingerprint.v1");
        writePolicy(writer);
        writeOptions(writer, options);
        writer.textList(BUILT_IN_ALGORITHMS);
        writer.integer(extensions.size());
        extensions.forEach(extension -> extension.writeTo(writer));
        return VERSION_PREFIX + HexFormat.of().formatHex(writer.finish());
    }

    private void writePolicy(DigestWriter writer) {
        writer.namedBoolean("policy.enabled", policy.enabled());
        writer.namedBoolean("policy.computeSimilarity", policy.computeSimilarity());
        writer.namedBoolean("policy.includeCollectionContents", policy.includeCollectionContents());
        writer.namedInt("policy.maxDepth", policy.maxDepth());
        writer.namedInt("policy.maxComparedNodes", policy.maxComparedNodes());
        writer.namedInt("policy.maxElements", policy.maxElements());
        writer.namedDuration("policy.deadline", policy.deadline());
        writer.namedInt("policy.maxChangeDetails", policy.maxChangeDetails());
        writer.namedInt("policy.maxIssues", policy.maxIssues());
        writer.namedInt("policy.maxResultValueChars", policy.maxResultValueChars());
        writer.namedInt("policy.maxPathEncodedChars", policy.maxPathEncodedChars());
        writer.namedInt("policy.maxResultTotalChars", policy.maxResultTotalChars());
        writer.namedInt("policy.maxEntityKeyComponents", policy.maxEntityKeyComponents());
        writer.namedInt("policy.maxEntityKeyEncodedBytes", policy.maxEntityKeyEncodedBytes());
        writer.namedDecimal("policy.numericAbsoluteTolerance", policy.numericAbsoluteTolerance());
        writer.namedDouble("policy.numericRelativeTolerance", policy.numericRelativeTolerance());
        writer.namedDuration("policy.temporalTolerance", policy.temporalTolerance());
        writer.text("policy.includePathRules");
        writer.textList(policy.includePathRuleFacts());
        writer.text("policy.excludePathRules");
        writer.textList(policy.excludePathRuleFacts());
    }

    private static void writeOptions(DigestWriter writer, CompareOptions options) {
        writer.namedBoolean("options.computeSimilarity", options.computeSimilarity());
        writer.namedBoolean("options.includeCollectionContents", options.includeCollectionContents());
        writer.namedInt("options.maxDepth", options.maxDepth());
        writer.namedInt("options.maxComparedNodes", options.maxComparedNodes());
        writer.namedInt("options.maxElements", options.maxElements());
        writer.namedDuration("options.deadline", options.deadline());
        writer.namedInt("options.maxChangeDetails", options.maxChangeDetails());
        writer.namedInt("options.maxIssues", options.maxIssues());
        writer.namedInt("options.maxResultValueChars", options.maxResultValueChars());
        writer.namedInt("options.maxPathEncodedChars", options.maxPathEncodedChars());
        writer.namedInt("options.maxResultTotalChars", options.maxResultTotalChars());
        writer.namedInt("options.maxEntityKeyComponents", options.maxEntityKeyComponents());
        writer.namedInt("options.maxEntityKeyEncodedBytes", options.maxEntityKeyEncodedBytes());
        writer.namedDecimal("options.numericAbsoluteTolerance", options.numericAbsoluteTolerance());
        writer.namedDouble("options.numericRelativeTolerance", options.numericRelativeTolerance());
        writer.namedDuration("options.temporalTolerance", options.temporalTolerance());
    }

    /** 冻结的扩展选择事实；只保存类型名、selector与低基数AlgorithmId。 */
    static final class ExtensionFact implements Comparable<ExtensionFact> {
        /** 扩展类别，固定为strategy或comparator。 */
        private final String kind;
        /** exact target或declaring class的binary name。 */
        private final String targetType;
        /** comparator字段名；strategy固定为空字符串。 */
        private final String fieldName;
        /** 版本化算法身份。 */
        private final String algorithmId;

        private ExtensionFact(String kind, String targetType, String fieldName, AlgorithmId algorithmId) {
            this.kind = kind;
            this.targetType = targetType;
            this.fieldName = fieldName;
            this.algorithmId = algorithmId.value();
        }

        static ExtensionFact strategy(Class<?> targetType, AlgorithmId algorithmId) {
            return new ExtensionFact("strategy", targetType.getName(), "", algorithmId);
        }

        static ExtensionFact comparator(PropertySelector selector, AlgorithmId algorithmId) {
            return new ExtensionFact(
                    "comparator", selector.declaringClass().getName(), selector.fieldName(), algorithmId);
        }

        private void writeTo(DigestWriter writer) {
            writer.text(kind);
            writer.text(targetType);
            writer.text(fieldName);
            writer.text(algorithmId);
        }

        @Override
        public int compareTo(ExtensionFact other) {
            return Comparator.comparing((ExtensionFact fact) -> fact.kind)
                    .thenComparing(fact -> fact.targetType)
                    .thenComparing(fact -> fact.fieldName)
                    .thenComparing(fact -> fact.algorithmId)
                    .compare(this, other);
        }
    }

    /** 只接受typed fact的SHA-256写入器，禁止调用方拼接preimage字符串。 */
    private static final class DigestWriter {
        /** JDK保证存在的SHA-256实例。 */
        private final MessageDigest digest;

        private DigestWriter() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("JDK must provide SHA-256", impossible);
            }
        }

        private void namedBoolean(String name, boolean value) { text(name); bool(value); }
        private void namedInt(String name, int value) { text(name); integer(value); }
        private void namedDouble(String name, double value) {
            text(name);
            longValue(Double.doubleToLongBits(value == 0.0d ? 0.0d : value));
        }
        private void namedDuration(String name, Duration value) {
            text(name); longValue(value.getSeconds()); integer(value.getNano());
        }
        private void namedDecimal(String name, BigDecimal value) {
            text(name); text(value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString());
        }
        private void textList(List<String> values) {
            List<String> sorted = new ArrayList<>(values); sorted.sort(String::compareTo);
            integer(sorted.size()); sorted.forEach(this::text);
        }
        private void bool(boolean value) { digest.update((byte) 1); digest.update((byte) (value ? 1 : 0)); }
        private void integer(int value) { digest.update((byte) 2); rawInt(value); }
        private void longValue(long value) {
            digest.update((byte) 3); rawInt((int) (value >>> 32)); rawInt((int) value);
        }
        private void text(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 4); rawInt(bytes.length); digest.update(bytes);
        }
        private void rawInt(int value) {
            digest.update((byte) (value >>> 24)); digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8)); digest.update((byte) value);
        }
        private byte[] finish() { return digest.digest(); }
    }
}
