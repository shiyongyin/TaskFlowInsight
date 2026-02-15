package com.syy.taskflowinsight.tracking.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer 诊断指标门面
 * <p>
 * 提供最小化的指标记录接口，用于 CompareEngine 和其他核心组件。
 * 如果没有 MeterRegistry，所有操作都是 No-Op。
 * </p>
 * <p>
 * 注意：此类是 TfiMetrics 的轻量级替代，仅用于不需要完整指标功能的场景。
 * 优先使用 TfiMetrics，当 TfiMetrics 不可用时才使用此类。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
public class MicrometerDiagnosticSink {

    private static final Logger logger = LoggerFactory.getLogger(MicrometerDiagnosticSink.class);

    private final MeterRegistry registry; // 可为 null → No-Op

    /**
     * 构造函数
     *
     * @param registry MeterRegistry 实例，可以为 null（No-Op 模式）
     */
    public MicrometerDiagnosticSink(MeterRegistry registry) {
        this.registry = registry;
        if (registry == null) {
            logger.debug("MicrometerDiagnosticSink initialized in No-Op mode (no registry)");
        } else {
            logger.debug("MicrometerDiagnosticSink initialized with registry: {}",
                registry.getClass().getSimpleName());
        }
    }

    /**
     * 记录计数指标
     *
     * @param name 指标名称
     * @param tags 标签键值对（偶数个元素：key1, value1, key2, value2, ...）
     */
    public void recordCount(String name, String... tags) {
        if (registry == null) {
            return; // No-Op
        }

        try {
            Counter.builder(name)
                .tags(tags)
                .register(registry)
                .increment();
        } catch (Exception e) {
            logger.warn("Failed to record count metric '{}': {}", name, e.getMessage());
        }
    }

    /**
     * 记录时延指标
     *
     * @param name 指标名称
     * @param nanos 纳秒级时延
     * @param tags 标签键值对（偶数个元素：key1, value1, key2, value2, ...）
     */
    public void recordDuration(String name, long nanos, String... tags) {
        if (registry == null) {
            return; // No-Op
        }

        try {
            Timer.builder(name)
                .tags(tags)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            logger.warn("Failed to record duration metric '{}': {}", name, e.getMessage());
        }
    }

    /**
     * 记录降级事件
     *
     * @param reason 降级原因
     */
    public void recordDegradation(String reason) {
        recordCount("tfi.perf.degradation", "reason", reason != null ? reason : "unknown");
    }

    /**
     * 记录错误
     *
     * @param errorType 错误类型
     */
    public void recordError(String errorType) {
        recordCount("tfi.compare.error", "type", errorType != null ? errorType : "unknown");
    }

    /**
     * 检查是否为 No-Op 模式
     *
     * @return 如果没有注册表（No-Op 模式）返回 true
     */
    public boolean isNoOp() {
        return registry == null;
    }

    /**
     * 获取底层的 MeterRegistry（用于高级场景）
     *
     * @return MeterRegistry 实例，可能为 null
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
