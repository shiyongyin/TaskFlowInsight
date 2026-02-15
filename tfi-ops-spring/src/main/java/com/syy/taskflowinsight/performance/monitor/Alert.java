package com.syy.taskflowinsight.performance.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * 性能告警
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@Data
@Builder
public class Alert {
    private String key;
    private AlertLevel level;
    private String message;
    private long timestamp;
    
    /**
     * 是否为严重告警
     */
    public boolean isCritical() {
        return level == AlertLevel.CRITICAL;
    }
    
    /**
     * 格式化输出
     */
    public String format() {
        return String.format("[%s] %s: %s", level, key, message);
    }
}