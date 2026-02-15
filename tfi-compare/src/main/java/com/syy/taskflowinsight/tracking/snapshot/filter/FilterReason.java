package com.syy.taskflowinsight.tracking.snapshot.filter;

/**
 * 标准化的过滤决策原因常量。
 * 用于测试断言与调试日志，避免魔法字符串分散。
 */
public final class FilterReason {
    private FilterReason() {}

    // Priority 1
    public static final String INCLUDE_PATTERNS = "includePatterns";

    // Priority 3 (path blacklist)
    public static final String EXCLUDE_PATTERNS = "excludePatterns";
    public static final String REGEX_EXCLUDES   = "regexExcludes";

    // Priority 4/5 (class/package)
    public static final String EXCLUDE_PACKAGES = "class/package";

    // Priority 6 (default exclusions)
    public static final String DEFAULT_EXCLUSIONS = "defaultExclusions";

    // Priority 7 (default retain)
    public static final String DEFAULT_RETAIN = "default";
}

