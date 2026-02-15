package com.syy.taskflowinsight.core;

import com.syy.taskflowinsight.config.TfiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TFI核心服务
 * 承载运行态开关状态，提供实例化的核心功能
 * 
 * 设计原则：
 * - 实例化服务：无静态状态，通过Spring管理生命周期
 * - 运行态管理：承载enabled和changeTrackingEnabled开关
 * - 线程安全：使用volatile保证多线程可见性
 * - 配置初始化：从TfiConfig读取初始值
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-09-18
 */
@Component
public class TfiCore {
    private static final Logger logger = LoggerFactory.getLogger(TfiCore.class);
    
    // 运行态状态（从TFI静态字段迁移过来）
    private volatile boolean globalEnabled;
    private volatile boolean changeTrackingEnabled;
    
    /**
     * 构造函数，从配置中读取初始值
     * 
     * @param config TFI配置
     */
    public TfiCore(TfiConfig config) {
        if (config != null) {
            this.globalEnabled = config.enabled();
            this.changeTrackingEnabled = config.changeTracking().enabled();
            logger.info("TfiCore initialized: globalEnabled={}, changeTrackingEnabled={}", 
                globalEnabled, changeTrackingEnabled);
        } else {
            // 默认值处理
            this.globalEnabled = true;
            this.changeTrackingEnabled = false;
            logger.warn("TfiCore initialized with default values due to null config");
        }
    }
    
    // ==================== 系统控制方法 ====================
    
    /**
     * 启用 TaskFlow Insight 系统
     */
    public void enable() {
        globalEnabled = true;
        logger.debug("TaskFlow Insight enabled via TfiCore");
    }
    
    /**
     * 禁用 TaskFlow Insight 系统
     */
    public void disable() {
        globalEnabled = false;
        logger.debug("TaskFlow Insight disabled via TfiCore");
    }
    
    /**
     * 检查系统是否启用
     * 
     * @return true 如果系统已启用
     */
    public boolean isEnabled() {
        return globalEnabled;
    }
    
    // ==================== 变更追踪控制方法 ====================
    
    /**
     * 设置变更追踪功能开关
     * 
     * @param enabled 是否启用变更追踪
     */
    public void setChangeTrackingEnabled(boolean enabled) {
        this.changeTrackingEnabled = enabled;
        logger.debug("Change tracking set to: {} via TfiCore", enabled);
    }
    
    /**
     * 检查变更追踪功能是否启用
     * 
     * @return true 如果变更追踪功能已启用且系统已启用
     */
    public boolean isChangeTrackingEnabled() {
        return changeTrackingEnabled && globalEnabled;
    }
    
    /**
     * 获取变更追踪原始状态（不考虑全局开关）
     * 
     * @return 变更追踪开关状态
     */
    public boolean getChangeTrackingRawState() {
        return changeTrackingEnabled;
    }
    
    /**
     * 获取全局启用状态（用于调试）
     * 
     * @return 全局启用状态
     */
    public boolean getGlobalEnabledRawState() {
        return globalEnabled;
    }
}