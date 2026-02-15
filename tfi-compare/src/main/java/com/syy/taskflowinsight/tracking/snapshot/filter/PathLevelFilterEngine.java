package com.syy.taskflowinsight.tracking.snapshot.filter;

import java.util.Set;

/**
 * 路径级过滤引擎
 * 提供基于路径模式（Glob/Regex）的过滤能力
 *
 * 核心功能：
 * - Include白名单：优先级最高，匹配路径不被过滤
 * - Exclude黑名单：Glob模式路径过滤
 * - Regex黑名单：正则表达式路径过滤
 *
 * 优先级规则（由UnifiedFilterEngine统一处理）：
 * - Include白名单优先于Exclude黑名单
 * - Glob和Regex并列，结果为并集
 *
 * 设计原则：
 * - 静态工具类，无状态
 * - 单一职责：仅判定路径是否匹配
 * - 委托PathMatcher进行实际匹配
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-10-09
 */
public final class PathLevelFilterEngine {

    /**
     * 私有构造器，防止实例化
     */
    private PathLevelFilterEngine() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 检查路径是否匹配Include白名单
     *
     * 用途：确定路径是否应被显式包含（不被过滤）
     *
     * @param path 待检查的路径（如 "order.items[0].id"）
     * @param includePatterns Include白名单模式集合（Glob格式）
     * @return true表示匹配白名单，false表示不匹配或白名单为空
     */
    public static boolean matchesIncludePatterns(String path, Set<String> includePatterns) {
        if (path == null) {
            return false;
        }

        // 空白名单表示不启用Include过滤
        if (includePatterns == null || includePatterns.isEmpty()) {
            return false;
        }

        // 只要匹配任一Include模式即返回true
        for (String pattern : includePatterns) {
            if (pattern != null && PathMatcher.matchGlob(path, pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查路径是否应被过滤（黑名单匹配）
     *
     * 用途：确定路径是否应被排除（Glob或Regex黑名单）
     *
     * @param path 待检查的路径（如 "order.internal.cache"）
     * @param excludeGlobPatterns Exclude黑名单Glob模式集合
     * @param excludeRegexPatterns Exclude黑名单正则表达式集合
     * @return true表示应被过滤，false表示不应被过滤
     */
    public static boolean shouldIgnoreByPath(String path,
                                            Set<String> excludeGlobPatterns,
                                            Set<String> excludeRegexPatterns) {
        if (path == null) {
            return false;
        }

        // Glob黑名单匹配
        if (excludeGlobPatterns != null && !excludeGlobPatterns.isEmpty()) {
            for (String pattern : excludeGlobPatterns) {
                if (pattern != null && PathMatcher.matchGlob(path, pattern)) {
                    return true;
                }
            }
        }

        // Regex黑名单匹配
        if (excludeRegexPatterns != null && !excludeRegexPatterns.isEmpty()) {
            for (String regex : excludeRegexPatterns) {
                if (regex != null && PathMatcher.matchRegex(path, regex)) {
                    return true;
                }
            }
        }

        return false;
    }
}
