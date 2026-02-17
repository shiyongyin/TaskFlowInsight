package com.syy.taskflowinsight.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Flow 上下文配置属性（flow-starter 专用）.
 *
 * <p>用于在 Spring 环境下配置 Flow Core 的上下文管理与 ThreadLocal 清理策略。
 * 该类不包含 compare/change-tracking 相关配置。
 *
 * <p>对应配置前缀：{@code tfi.context}
 *
 * <h3>配置项列表</h3>
 * <ul>
 *   <li>{@code tfi.context.max-age-millis} — 上下文最大存活时间（默认 3600000ms = 1 小时）</li>
 *   <li>{@code tfi.context.leak-detection-enabled} — 是否启用泄漏检测（默认 false）</li>
 *   <li>{@code tfi.context.leak-detection-interval-millis} — 泄漏检测间隔（默认 60000ms）</li>
 *   <li>{@code tfi.context.cleanup-enabled} — 是否启用自动清理（默认 false）</li>
 *   <li>{@code tfi.context.cleanup-interval-millis} — 清理间隔（默认 60000ms）</li>
 * </ul>
 *
 * <h3>生产环境建议</h3>
 * <p>在线程池密集的生产环境中，建议开启泄漏检测和自动清理：
 * <pre>{@code
 * tfi:
 *   context:
 *     leak-detection-enabled: true
 *     cleanup-enabled: true
 *     cleanup-interval-millis: 30000
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 * @see ContextMonitoringAutoConfiguration
 */
@Validated
@ConfigurationProperties(prefix = "tfi.context")
public class TfiContextProperties {

    /** 默认上下文最大存活时间（1 小时）. */
    private static final long DEFAULT_MAX_AGE = 3_600_000L;

    /** 默认检测/清理间隔（1 分钟）. */
    private static final long DEFAULT_INTERVAL = 60_000L;

    /**
     * 上下文最大存活时间（毫秒）.
     * <p>超过此时间的上下文将被视为过期，可被泄漏检测报告或自动清理回收。
     */
    @Min(value = 1, message = "maxAgeMillis must be positive")
    private long maxAgeMillis = DEFAULT_MAX_AGE;

    /**
     * 是否启用泄漏检测.
     * <p>启用后，将定期扫描未释放的上下文并输出告警日志。
     */
    private boolean leakDetectionEnabled = false;

    /**
     * 泄漏检测间隔（毫秒）.
     * <p>仅在 {@code leakDetectionEnabled=true} 时生效。
     */
    @Min(value = 1000, message = "leakDetectionIntervalMillis must be >= 1000ms")
    private long leakDetectionIntervalMillis = DEFAULT_INTERVAL;

    /**
     * 是否启用 ThreadLocal 定期清理.
     * <p>在线程池场景下建议开启，防止 ThreadLocal 泄漏。
     */
    private boolean cleanupEnabled = false;

    /**
     * ThreadLocal 清理间隔（毫秒）.
     * <p>仅在 {@code cleanupEnabled=true} 时生效。
     */
    @Min(value = 1000, message = "cleanupIntervalMillis must be >= 1000ms")
    private long cleanupIntervalMillis = DEFAULT_INTERVAL;

    /**
     * 获取上下文最大存活时间.
     *
     * @return 最大存活时间（毫秒），默认 3600000
     */
    public long getMaxAgeMillis() {
        return maxAgeMillis;
    }

    /**
     * 设置上下文最大存活时间.
     *
     * @param maxAgeMillis 最大存活时间（毫秒），应为正数
     */
    public void setMaxAgeMillis(long maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis;
    }

    /**
     * 获取泄漏检测开关状态.
     *
     * @return {@code true} 表示泄漏检测已启用
     */
    public boolean isLeakDetectionEnabled() {
        return leakDetectionEnabled;
    }

    /**
     * 设置泄漏检测开关.
     *
     * @param leakDetectionEnabled {@code true} 启用泄漏检测
     */
    public void setLeakDetectionEnabled(boolean leakDetectionEnabled) {
        this.leakDetectionEnabled = leakDetectionEnabled;
    }

    /**
     * 获取泄漏检测间隔.
     *
     * @return 检测间隔（毫秒），默认 60000
     */
    public long getLeakDetectionIntervalMillis() {
        return leakDetectionIntervalMillis;
    }

    /**
     * 设置泄漏检测间隔.
     *
     * @param leakDetectionIntervalMillis 检测间隔（毫秒），应为正数
     */
    public void setLeakDetectionIntervalMillis(long leakDetectionIntervalMillis) {
        this.leakDetectionIntervalMillis = leakDetectionIntervalMillis;
    }

    /**
     * 获取自动清理开关状态.
     *
     * @return {@code true} 表示自动清理已启用
     */
    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    /**
     * 设置自动清理开关.
     *
     * @param cleanupEnabled {@code true} 启用自动清理
     */
    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    /**
     * 获取清理间隔.
     *
     * @return 清理间隔（毫秒），默认 60000
     */
    public long getCleanupIntervalMillis() {
        return cleanupIntervalMillis;
    }

    /**
     * 设置清理间隔.
     *
     * @param cleanupIntervalMillis 清理间隔（毫秒），应为正数
     */
    public void setCleanupIntervalMillis(long cleanupIntervalMillis) {
        this.cleanupIntervalMillis = cleanupIntervalMillis;
    }
}
