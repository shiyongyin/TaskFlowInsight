package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * 将结果中的有界值事实转换为临时诊断输出。
 *
 * <p>结果对象的 {@code toString()} 必须保持不泄漏，因此旧报告器不能再依赖默认字符串化。
 * 本类只解释已经捕获的 canonical facts，不接触业务对象；是否掩码仍由具体输出层在调用前决定。</p>
 *
 * @since 4.0.0
 */
public final class ValueSnapshotFormatter {

    /** JSON control character使用大写十六进制，保持跨运行时 wire 稳定。 */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private ValueSnapshotFormatter() {
    }

    /**
     * 生成面向人的有界文本；降级表示会显式保留类型和降级原因，避免被误认为原始值。
     *
     * @param snapshot 已经过结果边界裁剪的值事实，不能为空
     * @return 不触发业务对象回调的诊断文本
     */
    public static String diagnosticText(ValueSnapshot snapshot) {
        if (snapshot.representation() == ValueSnapshot.Representation.OMITTED) {
            return "<" + snapshot.typeCode() + ":omitted:"
                    + snapshot.omissionReason().orElseThrow().name().toLowerCase() + ">";
        }
        if (snapshot.representation() == ValueSnapshot.Representation.SUMMARY) {
            return "<" + snapshot.typeCode() + ":summary:"
                    + String.join(",", snapshot.canonicalTextFacts()) + ">";
        }
        return exactText(snapshot);
    }

    /**
     * 为仍存在的旧 patch/report 入口生成 JSON literal；machine schema 将由后续 projection owner 统一实现。
     *
     * @param snapshot 已经过结果边界裁剪的值事实，不能为空
     * @return 可直接嵌入旧 JSON 输出的单个 literal
     */
    public static String legacyJsonLiteral(ValueSnapshot snapshot) {
        if (snapshot.representation() != ValueSnapshot.Representation.EXACT) {
            return quote(diagnosticText(snapshot));
        }
        List<String> facts = snapshot.canonicalTextFacts();
        return switch (snapshot.typeCode()) {
            case "null" -> "null";
            case "boolean", "byte", "short", "int", "long", "big-integer" -> facts.getFirst();
            case "big-decimal" -> decimalText(facts);
            default -> quote(exactText(snapshot));
        };
    }

    private static String exactText(ValueSnapshot snapshot) {
        List<String> facts = snapshot.canonicalTextFacts();
        return switch (snapshot.typeCode()) {
            case "null" -> "null";
            case "big-decimal" -> decimalText(facts);
            case "enum", "type-metadata" -> facts.getLast();
            case "array", "list", "set", "map", "collection" ->
                    "<" + snapshot.typeCode() + ":size=" + facts.getFirst() + ">";
            default -> facts.isEmpty() ? "<" + snapshot.typeCode() + ">" : facts.getFirst();
        };
    }

    private static String decimalText(List<String> facts) {
        return new BigDecimal(new BigInteger(facts.getFirst()), Integer.parseInt(facts.get(1))).toString();
    }

    private static String quote(String value) {
        StringBuilder output = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (current < 0x20 || isUnpairedSurrogate(value, index)) {
                        appendUnicodeEscape(output, current);
                    } else {
                        output.append(current);
                        if (Character.isHighSurrogate(current)) {
                            output.append(value.charAt(++index));
                        }
                    }
                }
            }
        }
        return output.append('"').toString();
    }

    private static boolean isUnpairedSurrogate(String value, int index) {
        char current = value.charAt(index);
        if (Character.isHighSurrogate(current)) {
            return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
        }
        return Character.isLowSurrogate(current);
    }

    private static void appendUnicodeEscape(StringBuilder output, char value) {
        output.append("\\u")
                .append(HEX[(value >>> 12) & 0xF])
                .append(HEX[(value >>> 8) & 0xF])
                .append(HEX[(value >>> 4) & 0xF])
                .append(HEX[value & 0xF]);
    }
}
