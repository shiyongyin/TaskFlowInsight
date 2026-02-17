package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * TFI Flow 上下文监控自动配置.
 *
 * <p>在 Spring Boot 应用启动时，自动将 {@link TfiContextProperties} 中的配置值
 * 应用到 {@link SafeContextManager} 和 {@link ZeroLeakThreadLocalManager}，
 * 完成上下文生命周期管理、泄漏检测和自动清理的配置。
 *
 * <h3>生效条件</h3>
 * <ul>
 *   <li>classpath 中存在 {@link SafeContextManager}（即 {@code tfi-flow-core} 已引入）</li>
 * </ul>
 *
 * <h3>配置前缀</h3>
 * <ul>
 *   <li>{@code tfi.context.*} — 上下文管理参数</li>
 *   <li>{@code tfi.security.*} — 安全策略参数</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 * @see TfiContextProperties
 * @see TfiSecurityProperties
 */
@AutoConfiguration
@ConditionalOnClass(SafeContextManager.class)
@EnableConfigurationProperties({TfiContextProperties.class, TfiSecurityProperties.class})
public class ContextMonitoringAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ContextMonitoringAutoConfiguration.class);

    /** 默认上下文最大存活时间（1 小时）. */
    private static final long DEFAULT_MAX_AGE_MILLIS = 3_600_000L;

    /** 默认检测/清理间隔（1 分钟）. */
    private static final long DEFAULT_INTERVAL_MILLIS = 60_000L;

    private final TfiContextProperties properties;

    /**
     * 构造自动配置实例.
     *
     * @param properties 上下文配置属性
     */
    public ContextMonitoringAutoConfiguration(TfiContextProperties properties) {
        this.properties = properties;
    }

    /**
     * 在 Bean 初始化后，将配置属性应用到 Flow Core 的上下文管理器.
     *
     * <p>配置失败时仅记录警告日志，不阻断应用启动。
     */
    @PostConstruct
    public void applyMonitoringProperties() {
        try {
            applyToSafeContextManager();
            applyToZeroLeakManager();

            logger.info("Applied tfi.context properties: maxAgeMillis={} leakDetectionEnabled={}"
                            + " leakDetectionIntervalMillis={} cleanupEnabled={} cleanupIntervalMillis={}",
                    properties.getMaxAgeMillis(),
                    properties.isLeakDetectionEnabled(),
                    properties.getLeakDetectionIntervalMillis(),
                    properties.isCleanupEnabled(),
                    properties.getCleanupIntervalMillis());
        } catch (Exception e) {
            logger.warn("Failed to apply tfi.context properties — using defaults. Cause: {}", e.getMessage());
        }
    }

    /**
     * 将配置应用到 {@link SafeContextManager}.
     */
    private void applyToSafeContextManager() {
        SafeContextManager mgr = SafeContextManager.getInstance();
        mgr.configure(
                sanitizeMillis(properties.getMaxAgeMillis(), DEFAULT_MAX_AGE_MILLIS),
                properties.isLeakDetectionEnabled(),
                sanitizeMillis(properties.getLeakDetectionIntervalMillis(), DEFAULT_INTERVAL_MILLIS)
        );
    }

    /**
     * 将配置应用到 {@link ZeroLeakThreadLocalManager}.
     */
    private void applyToZeroLeakManager() {
        ZeroLeakThreadLocalManager zlm = ZeroLeakThreadLocalManager.getInstance();
        zlm.setContextTimeoutMillis(sanitizeMillis(properties.getMaxAgeMillis(), DEFAULT_MAX_AGE_MILLIS));
        zlm.setCleanupIntervalMillis(sanitizeMillis(properties.getCleanupIntervalMillis(), DEFAULT_INTERVAL_MILLIS));
        zlm.setCleanupEnabled(properties.isCleanupEnabled());
    }

    /**
     * 校验毫秒值，非法值（&le; 0）回退到默认值.
     *
     * @param configured 用户配置值
     * @param fallback   默认回退值
     * @return 校验后的有效值
     */
    static long sanitizeMillis(long configured, long fallback) {
        return configured > 0 ? configured : fallback;
    }
}
