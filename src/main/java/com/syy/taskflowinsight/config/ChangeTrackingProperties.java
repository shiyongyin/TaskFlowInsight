package com.syy.taskflowinsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 变更追踪配置属性
 * 通过Spring Configuration Properties机制注入配置
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
@Data
@ConfigurationProperties(prefix = "tfi.change-tracking")
public class ChangeTrackingProperties {
    
    /**
     * 是否启用变更追踪功能
     * 默认禁用，需要显式开启
     */
    private boolean enabled = false;
    
    /**
     * 值表示的最大长度
     * 超过此长度的字符串将被截断并添加"... (truncated)"后缀
     */
    private int valueReprMaxLength = 8192;
    
    /**
     * 清理间隔（分钟）
     * 定时清理ThreadLocal数据的间隔，0表示不启用定时清理
     * M0阶段默认不启用定时清理
     */
    private int cleanupIntervalMinutes = 5;
    
    /**
     * 反射缓存的最大类数量
     * 超过此数量后，新的类将不会被缓存
     */
    private int maxCachedClasses = 1024;
    
    /**
     * 是否在DEBUG级别记录详细的追踪信息
     */
    private boolean debugLogging = false;
    
    /**
     * 检查配置是否有效
     * 
     * @return true如果配置有效
     */
    public boolean isValid() {
        return valueReprMaxLength > 0 && 
               cleanupIntervalMinutes >= 0 &&
               maxCachedClasses > 0;
    }
    
    /**
     * 是否启用定时清理
     * 
     * @return true如果应该启用定时清理
     */
    public boolean isCleanupEnabled() {
        return cleanupIntervalMinutes > 0;
    }
    
    /**
     * 是否启用变更追踪功能
     * 
     * @return true如果启用变更追踪
     */
    public boolean isEnabled() {
        return enabled;
    }
}