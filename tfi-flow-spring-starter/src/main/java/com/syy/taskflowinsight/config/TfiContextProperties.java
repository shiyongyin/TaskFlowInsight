package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.context.ContextManagerConfig;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Flow 上下文配置属性（flow-starter 专用）.
 *
 * <p>用于在 Spring 环境下配置 Flow Core 的上下文生命周期与泄漏检测策略。
 * 该类不包含 compare/change-tracking 相关配置。
 *
 * <p>对应配置前缀：{@code tfi.context}
 *
 * <h2>配置项列表</h2>
 * <ul>
 *   <li>{@code tfi.context.max-age-millis} — 上下文最大存活时间（默认 3600000ms = 1 小时）</li>
 *   <li>{@code tfi.context.leak-detection-enabled} — 是否启用泄漏检测（默认 false）</li>
 *   <li>{@code tfi.context.leak-detection-interval-millis} — 泄漏检测间隔（默认 60000ms）</li>
 * </ul>
 *
 * <h2>生产环境建议</h2>
 * <p>在线程池密集的生产环境中，建议开启 manager 唯一的泄漏检测调度：
 * <pre>{@code
 * tfi:
 *   context:
 *     leak-detection-enabled: true
 *     leak-detection-interval-millis: 30000
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 * @see ContextMonitoringAutoConfiguration
 */
@Validated
@ConfigurationProperties(prefix = "tfi.context")
public class TfiContextProperties {

    /** Core record 是 manager 与 Starter 的唯一默认值来源，避免两模块独立演进后产生漂移. */
    private static final ContextManagerConfig DEFAULTS = ContextManagerConfig.defaults();

    /**
     * 使用 Flow Core 的权威默认值创建可由 Spring 绑定的属性对象。
     */
    public TfiContextProperties() {
    }

    /**
     * 上下文最大存活时间（毫秒）.
     * <p>超过此时间的上下文将被视为过期，可被泄漏检测报告或自动清理回收。
     */
    @Min(value = 1, message = "maxAgeMillis must be positive")
    private long maxAgeMillis = DEFAULTS.timeoutMillis();

    /**
     * 是否启用泄漏检测.
     * <p>启用后，将定期扫描未释放的上下文并输出告警日志。
     */
    private boolean leakDetectionEnabled = DEFAULTS.leakDetectionEnabled();

    /**
     * 泄漏检测间隔（毫秒）.
     * <p>仅在 {@code leakDetectionEnabled=true} 时生效。
     */
    @Min(value = 1000, message = "leakDetectionIntervalMillis must be >= 1000ms")
    private long leakDetectionIntervalMillis = DEFAULTS.leakDetectionIntervalMillis();

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

}
