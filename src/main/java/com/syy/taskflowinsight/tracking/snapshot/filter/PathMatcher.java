package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 路径匹配工具类
 * 提供Glob和正则表达式路径匹配能力，带Pattern缓存
 *
 * 支持的Glob模式：
 * - {@code *} : 匹配单层路径（不包含 {@code .}）
 * - {@code **} : 匹配跨层路径（任意层级）
 * - {@code [*]} : 匹配数组/列表任意索引（如 {@code items[*].id}）
 * - {@code ?} : 匹配单个字符
 *
 * 示例：
 * <pre>
 * PathMatcher.matchGlob("order.items", "order.*")          → true
 * PathMatcher.matchGlob("order.items.name", "order.**")   → true
 * PathMatcher.matchGlob("items[0].id", "items[*].id")     → true
 * PathMatcher.matchRegex("debug_001", "^debug_\\d{3}$")   → true
 * </pre>
 *
 * 性能特性：
 * - Pattern缓存：最多1024个，超出时清空（简化策略）
 * - 缓存命中率：预期 > 95%（相同模式重复匹配场景）
 * - 非法正则：缓存null，返回false，不中断流程
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-10-09
 */
public final class PathMatcher {

    private static final Logger logger = LoggerFactory.getLogger(PathMatcher.class);

    /**
     * Pattern缓存最大容量
     * 超出时清空全部缓存（简化策略，避免复杂的LRU实现）
     */
    private static final int MAX_CACHE_SIZE = 1024;

    /**
     * 正则Pattern缓存
     * key: 正则表达式或Glob模式（已转换为正则）
     * value: 编译后的Pattern对象
     */
    private static final ConcurrentMap<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    /**
     * 无效正则表达式集合
     * 记录编译失败的正则表达式，避免重复尝试编译
     */
    private static final Set<String> INVALID_REGEX_SET = ConcurrentHashMap.newKeySet();

    /**
     * 私有构造器，防止实例化
     */
    private PathMatcher() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Glob模式匹配
     *
     * @param path 待匹配的路径（如 "order.items[0].id"）
     * @param glob Glob模式（如 "order.**", "items[*].id"）
     * @return true表示匹配，false表示不匹配
     */
    public static boolean matchGlob(String path, String glob) {
        if (path == null || glob == null) {
            return false;
        }

        // 转换Glob为正则表达式
        String regex = convertGlobToRegex(glob);
        return matchRegex(path, regex);
    }

    /**
     * 正则表达式匹配
     *
     * @param path 待匹配的路径
     * @param regex 正则表达式
     * @return true表示匹配，false表示不匹配（包括非法正则的情况）
     */
    public static boolean matchRegex(String path, String regex) {
        if (path == null || regex == null) {
            return false;
        }

        Pattern pattern = getOrCompilePattern(regex);

        // 非法正则返回false（不中断流程）
        if (pattern == null) {
            logger.debug("Invalid regex pattern, returning false: {}", regex);
            return false;
        }

        return pattern.matcher(path).matches();
    }

    /**
     * 获取或编译Pattern（带缓存）
     *
     * @param regex 正则表达式
     * @return 编译后的Pattern，非法正则返回null
     */
    private static Pattern getOrCompilePattern(String regex) {
        // 检查缓存大小，超出时清空（简化策略）
        if (REGEX_CACHE.size() + INVALID_REGEX_SET.size() > MAX_CACHE_SIZE) {
            logger.warn("Pattern cache size exceeded {}, clearing cache", MAX_CACHE_SIZE);
            REGEX_CACHE.clear();
            INVALID_REGEX_SET.clear();
        }

        // 先检查是否是已知的无效正则
        if (INVALID_REGEX_SET.contains(regex)) {
            return null;
        }

        // 尝试从缓存获取
        Pattern cached = REGEX_CACHE.get(regex);
        if (cached != null) {
            return cached;
        }

        // 编译Pattern
        try {
            Pattern pattern = Pattern.compile(regex);
            REGEX_CACHE.put(regex, pattern);
            return pattern;
        } catch (PatternSyntaxException e) {
            // 非法正则：记录到无效集合，记录DEBUG日志
            logger.debug("Invalid regex pattern syntax: {}", regex, e);
            INVALID_REGEX_SET.add(regex);
            return null;
        }
    }

    /**
     * 将Glob模式转换为正则表达式
     *
     * 转换规则：
     * - {@code **} 在末尾 → {@code (\..*)?} （跨层匹配，可选）
     * - {@code **} 在中间 → {@code (\..*\.)?} （跨层匹配，可选，两端带点）
     * - {@code *} → {@code [^.]+} （单层匹配，至少一个字符，不包含点）
     * - {@code [*]} → {@code \[\d+\]} （数组索引匹配）
     * - {@code ?} → {@code .} （单字符匹配）
     * - {@code .} → {@code \.} （点转义）
     * - 其他正则特殊字符：转义处理
     *
     * @param glob Glob模式
     * @return 正则表达式
     */
    private static String convertGlobToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");

        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);

            if (c == '*') {
                // 检查是否是 ** (跨层)
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    // ** 的处理取决于位置
                    i += 2;

                    // 检查**之前是否有点
                    boolean hasDotBefore = (i >= 3 && glob.charAt(i - 3) == '.');

                    // 检查**之后是否有点
                    boolean hasDotAfter = (i < glob.length() && glob.charAt(i) == '.');

                    if (hasDotBefore && hasDotAfter) {
                        // 中间位置的 .**.
                        // 需要匹配零层或多层，如 order.**.name 匹配 order.name 和 order.a.b.name
                        // 转换为 (\..+)? （可选的一个或多个层级）
                        // 回退前面的\.,添加可选模式
                        if (regex.length() >= 2 && regex.substring(regex.length() - 2).equals("\\.")) {
                            regex.delete(regex.length() - 2, regex.length());
                            regex.append("(\\..*)?");
                        } else {
                            regex.append(".*");
                        }
                    } else if (hasDotBefore) {
                        // 末尾位置的 .**  → (\..*)?  (可选的后缀匹配)
                        // 回退前面的\.,添加可选模式
                        // 当前regex已有\.,删除它，添加(\..*)?
                        if (regex.length() >= 2 && regex.substring(regex.length() - 2).equals("\\.")) {
                            regex.delete(regex.length() - 2, regex.length());
                            regex.append("(\\..*)?");
                        } else {
                            regex.append(".*");
                        }
                    } else {
                        // 开头或其他位置的 **  → .*
                        regex.append(".*");
                    }
                } else {
                    // 单层匹配（零个或多个字符，不包含点）
                    regex.append("[^.]*");
                    i++;
                }
            } else if (c == '[' && i + 2 < glob.length()
                       && glob.charAt(i + 1) == '*'
                       && glob.charAt(i + 2) == ']') {
                // [*] 匹配数组索引
                regex.append("\\[\\d+\\]");
                i += 3;
            } else if (c == '?') {
                // 单字符匹配
                regex.append(".");
                i++;
            } else if (c == '.') {
                // 点转义
                regex.append("\\.");
                i++;
            } else if (isRegexSpecialChar(c)) {
                // 其他正则特殊字符转义
                regex.append("\\").append(c);
                i++;
            } else {
                // 普通字符
                regex.append(c);
                i++;
            }
        }

        regex.append("$");
        return regex.toString();
    }

    /**
     * 判断是否为正则特殊字符（需要转义）
     * 排除已单独处理的 * ? . [ ]
     */
    private static boolean isRegexSpecialChar(char c) {
        return "(){}\\^$|+".indexOf(c) >= 0;
    }

    /**
     * 获取缓存统计信息（用于测试和监控）
     *
     * @return 缓存大小（有效Pattern + 无效正则）
     */
    public static int getCacheSize() {
        return REGEX_CACHE.size() + INVALID_REGEX_SET.size();
    }

    /**
     * 清空缓存（用于测试和基准测试）
     */
    public static void clearCache() {
        REGEX_CACHE.clear();
        INVALID_REGEX_SET.clear();
    }
}
