package com.syy.taskflowinsight.tracking.snapshot.filter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * 统一过滤引擎（Master）：按优先级执行过滤决策。
 *
 * 优先级（高→低）：
 * 1) Include 白名单（glob）
 * 2) 字段注解（由调用处处理；此处不参与）
 * 3) 路径黑名单（glob/regex）
 * 4) 类/包级（注解/包名单）
 * 5) 默认忽略（static/transient/synthetic/logger/serialVersionUID/$jacocoData）
 * 6) 默认保留
 */
public final class UnifiedFilterEngine {
    private UnifiedFilterEngine() {}

    public static FilterDecision shouldIgnore(
        Class<?> ownerClass,
        Field field,
        String fieldPath,
        Set<String> includePatterns,
        Set<String> excludeGlobPatterns,
        Set<String> excludeRegexPatterns,
        List<String> excludePackages,
        boolean defaultExclusionsEnabled
    ) {
        // Priority 1: Include whitelist
        if (PathLevelFilterEngine.matchesIncludePatterns(fieldPath, includePatterns)) {
            return FilterDecision.include(FilterReason.INCLUDE_PATTERNS);
        }

        // Priority 3: Path blacklist (glob/regex)
        if (excludeGlobPatterns != null) {
            for (String pattern : excludeGlobPatterns) {
                if (pattern != null && PathMatcher.matchGlob(fieldPath, pattern)) {
                    return FilterDecision.exclude(FilterReason.EXCLUDE_PATTERNS);
                }
            }
        }
        if (excludeRegexPatterns != null) {
            for (String regex : excludeRegexPatterns) {
                if (regex != null && PathMatcher.matchRegex(fieldPath, regex)) {
                    return FilterDecision.exclude(FilterReason.REGEX_EXCLUDES);
                }
            }
        }

        // Priority 4/5: Class/Package-level
        if (ClassLevelFilterEngine.shouldIgnoreByClass(ownerClass, field, excludePackages)) {
            return FilterDecision.exclude(FilterReason.EXCLUDE_PACKAGES);
        }

        // Priority 6: Default exclusions
        if (DefaultExclusionEngine.isDefaultExcluded(field, defaultExclusionsEnabled)) {
            return FilterDecision.exclude(FilterReason.DEFAULT_EXCLUSIONS);
        }

        // Priority 7: Default retain
        return FilterDecision.include(FilterReason.DEFAULT_RETAIN);
    }
}

