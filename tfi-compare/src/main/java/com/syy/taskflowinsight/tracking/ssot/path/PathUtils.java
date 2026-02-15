package com.syy.taskflowinsight.tracking.ssot.path;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PathUtils {

    // 支持转义字符的正则：匹配 非] 或 转义序列 \X
    private static final Pattern ENTITY_OR_MAP = Pattern.compile("^(entity|map)\\[((?:[^\\\\\\]]|\\\\.)*)\\](?:\\.(.*))?$");

    private PathUtils() {}

    /**
     * 转义路径中的特殊字符（与 EntityKeyUtils 一致）
     * 注意：build* 方法已接收预转义的 key，通常无需手动调用此方法
     */
    static String escape(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("=", "\\=")
                .replace("#", "\\#")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(":", "\\:");
    }

    /**
     * 反转义路径中的特殊字符（用于显示或测试）
     * 注意：parse 返回的 key 保持转义状态，调用方按需 unescape
     */
    public static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\:", ":")
                .replace("\\]", "]")
                .replace("\\[", "[")
                .replace("\\#", "#")
                .replace("\\=", "=")
                .replace("\\|", "|")
                .replace("\\\\", "\\");
    }

    public static String buildEntityPath(String stableKey, String... segments) {
        return build("entity", stableKey, -1, segments);
    }

    public static String buildEntityPathWithDup(String stableKey, int dupIdx, String... segments) {
        return build("entity", stableKey + "#" + dupIdx, -1, segments);
    }

    public static String buildMapValuePath(String displayKey, String... segments) {
        return build("map", displayKey, -1, segments);
    }

    public static String buildMapKeyAttrPath(String stableKey, String... segments) {
        return build("map", "KEY:" + stableKey, -1, segments);
    }

    public static String buildListIndexPath(int index, String... segments) {
        String base = "[" + index + "]";
        return segments == null || segments.length == 0 ? base : base + "." + String.join(".", segments);
    }

    public static KeyFieldPair parse(String path) {
        Matcher m = ENTITY_OR_MAP.matcher(Objects.requireNonNull(path));
        if (m.matches()) {
            String prefix = m.group(1);
            String key = m.group(2);
            String field = m.group(3) == null ? "" : m.group(3);
            return new KeyFieldPair(prefix + "[" + key + "]", field);
        }
        return new KeyFieldPair("-", path); // 非 entity/map 路径
    }

    private static String build(String prefix, String inside, int idx, String... segments) {
        String base = prefix + "[" + inside + "]";
        if (segments == null || segments.length == 0) return base;
        String tail = String.join(".", segments);
        return tail.isEmpty() ? base : base + "." + tail;
    }

    public record KeyFieldPair(String key, String field) {}
}
