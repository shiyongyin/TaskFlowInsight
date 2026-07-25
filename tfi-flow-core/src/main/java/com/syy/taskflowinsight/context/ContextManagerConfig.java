package com.syy.taskflowinsight.context;

/**
 * Safe Context manager 的不可变配置快照。
 *
 * <p>timeout、检测开关和检测间隔必须作为一个整体发布，否则并发读取可能组合出从未应用过的配置。
 * record 同时承担完整性校验，使 scheduler 准备失败前后都能保留一个有效三元组。
 *
 * @param timeoutMillis Context 最大存活时间，必须大于 0
 * @param leakDetectionEnabled 是否启用周期性泄漏检测
 * @param leakDetectionIntervalMillis 泄漏检测间隔，必须大于 0
 * @since 4.0.0
 * @see SafeContextManager#apply(ContextManagerConfig)
 */
public record ContextManagerConfig(
        long timeoutMillis,
        boolean leakDetectionEnabled,
        long leakDetectionIntervalMillis) {

    /**
     * 校验完整配置，防止无效 duration 进入 runtime 准备阶段。
     */
    public ContextManagerConfig {
        if (timeoutMillis <= 0 || leakDetectionIntervalMillis <= 0) {
            throw new IllegalArgumentException("Context durations must be positive");
        }
    }

    /**
     * 返回 Core 与 Starter 共同使用的唯一默认配置。
     *
     * @return 默认关闭泄漏检测的有效配置
     */
    public static ContextManagerConfig defaults() {
        return new ContextManagerConfig(3_600_000L, false, 60_000L);
    }
}
