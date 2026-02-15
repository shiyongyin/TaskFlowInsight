package com.syy.taskflowinsight.tracking.compare;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CompareOptions 的性能预算配置绑定（可选）。
 * YAML 前缀：tfi.diff.perf.*
 *
 * 示例：
 * tfi:
 *   diff:
 *     perf:
 *       timeout-ms: 5000
 *       max-elements: 10000
 *       strict-mode: false
 *       degradation-strategy: FALLBACK_TO_SIMPLE
 *
 * 本配置仅用于为 CompareOptions 提供默认值，不会覆盖显式传入的 CompareOptions。
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
@Configuration
@ConfigurationProperties(prefix = "tfi.diff.perf")
public class ComparePerfProperties {

    /** 性能预算超时阈值（毫秒） */
    private int timeoutMs = 5000;
    /** 性能预算元素数量阈值 */
    private int maxElements = 10000;
    /** 严格模式（超预算抛异常） */
    private boolean strictMode = false;
    /** 降级策略（占位，按需扩展） */
    private String degradationStrategy = "FALLBACK_TO_SIMPLE";

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getMaxElements() { return maxElements; }
    public void setMaxElements(int maxElements) { this.maxElements = maxElements; }

    public boolean isStrictMode() { return strictMode; }
    public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }

    public String getDegradationStrategy() { return degradationStrategy; }
    public void setDegradationStrategy(String degradationStrategy) { this.degradationStrategy = degradationStrategy; }
}

