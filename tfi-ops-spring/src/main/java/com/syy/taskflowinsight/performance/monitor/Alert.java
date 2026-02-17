package com.syy.taskflowinsight.performance.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * 性能告警 DTO。
 *
 * <p>表示一条由 {@link com.syy.taskflowinsight.performance.monitor.PerformanceMonitor}
 * 触发的性能告警，包含告警键、级别、消息和时间戳。</p>
 *
 * <p>告警键格式：{@code <operation>.<dimension>}，
 * 如 {@code snapshot.latency}、{@code system.memory}。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Data
@Builder
public class Alert {

    /** 告警唯一键，用于去重和清除。格式：{@code <operation>.<dimension>} */
    private String key;

    /** 告警级别 */
    private AlertLevel level;

    /** 人可读的告警描述 */
    private String message;

    /** 告警触发时间（epoch millis） */
    private long timestamp;

    /**
     * 判断是否为严重告警。
     *
     * @return {@code true} 当且仅当 {@code level == CRITICAL}
     */
    public boolean isCritical() {
        return level == AlertLevel.CRITICAL;
    }

    /**
     * 格式化为日志友好的字符串。
     *
     * @return 格式：{@code [LEVEL] key: message}
     */
    public String format() {
        return String.format("[%s] %s: %s", level, key, message);
    }
}