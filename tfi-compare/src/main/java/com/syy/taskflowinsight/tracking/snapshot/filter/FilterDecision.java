package com.syy.taskflowinsight.tracking.snapshot.filter;

/**
 * 过滤决策：包含是否排除与原因（用于调试与测试断言）。
 */
public final class FilterDecision {
    private final boolean shouldExclude;
    private final String reason;

    private FilterDecision(boolean shouldExclude, String reason) {
        this.shouldExclude = shouldExclude;
        this.reason = reason;
    }

    public static FilterDecision include(String reason) {
        return new FilterDecision(false, reason);
    }

    public static FilterDecision exclude(String reason) {
        return new FilterDecision(true, reason);
    }

    public boolean shouldExclude() {
        return shouldExclude;
    }

    public boolean shouldInclude() {
        return !shouldExclude;
    }

    public String getReason() {
        return reason;
    }
}

