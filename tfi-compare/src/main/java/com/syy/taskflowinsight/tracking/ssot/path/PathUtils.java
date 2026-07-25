package com.syy.taskflowinsight.tracking.ssot.path;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧版字符串路径的集中编码与解析入口。
 *
 * <p>canonical比较逻辑使用typed路径；本类型只服务展示与兼容API，集中转义规则是为了避免各消费者
 * 自行拼接后把动态key误判为结构分隔符。</p>
 *
 * @since 3.0.0
 */
public final class PathUtils {

    // 支持转义字符的正则：匹配 非] 或 转义序列 \X
    private static final Pattern ENTITY_OR_MAP = Pattern.compile("^(entity|map)\\[((?:[^\\\\\\]]|\\\\.)*)\\](?:\\.(.*))?$");

    private PathUtils() {}

    /**
     * 转义兼容路径中的单个动态事实，调用方不得自行复制分隔符规则。
     *
     * @param s 待编码文本；null保持null
     * @return 可安全放入方括号路径段的转义文本
     */
    public static String escape(String s) {
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
     *
     * @param s 待解码文本；null保持null
     * @return 解码后的兼容显示文本
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

    /**
     * 构造Entity兼容路径；调用方必须先对动态key完成集中转义。
     *
     * @param stableKey 已转义的稳定key
     * @param segments 可选属性路径段
     * @return Entity字符串路径
     */
    public static String buildEntityPath(String stableKey, String... segments) {
        return build("entity", stableKey, -1, segments);
    }

    /**
     * 构造带重复序号的历史Entity路径，仅供旧协议读取。
     *
     * @param stableKey 已转义的稳定key
     * @param dupIdx 同key实例的历史序号
     * @param segments 可选属性路径段
     * @return 带重复序号的Entity字符串路径
     */
    public static String buildEntityPathWithDup(String stableKey, int dupIdx, String... segments) {
        return build("entity", stableKey + "#" + dupIdx, -1, segments);
    }

    /**
     * 构造Map value兼容路径，不承担Map key的canonical identity语义。
     *
     * @param displayKey 已转义的显示key
     * @param segments 可选属性路径段
     * @return Map value字符串路径
     */
    public static String buildMapValuePath(String displayKey, String... segments) {
        return build("map", displayKey, -1, segments);
    }

    /**
     * 构造Map key属性的历史投影路径。
     *
     * @param stableKey 已转义的稳定key
     * @param segments 可选属性路径段
     * @return Map key属性字符串路径
     */
    public static String buildMapKeyAttrPath(String stableKey, String... segments) {
        return build("map", "KEY:" + stableKey, -1, segments);
    }

    /**
     * 构造List索引兼容路径；索引只是位置，不应被提升为Entity identity。
     *
     * @param index 列表物理索引
     * @param segments 可选属性路径段
     * @return List索引字符串路径
     */
    public static String buildListIndexPath(int index, String... segments) {
        String base = "[" + index + "]";
        return segments == null || segments.length == 0 ? base : base + "." + String.join(".", segments);
    }

    /**
     * 拆分Entity/Map兼容路径的key与字段部分，key保持转义状态以避免信息丢失。
     *
     * @param path 非null的兼容字符串路径
     * @return 拆分结果；非Entity/Map路径以{@code -}作为key
     */
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

    /**
     * 兼容路径拆分结果；两个组件均为展示投影，不能用于canonical配对。
     *
     * @param key 保持转义状态的容器key投影
     * @param field 容器后的字段路径；没有字段时为空字符串
     * @since 3.0.0
     */
    public record KeyFieldPair(String key, String field) {}
}
