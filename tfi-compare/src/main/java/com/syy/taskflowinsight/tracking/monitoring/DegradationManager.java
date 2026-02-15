package com.syy.taskflowinsight.tracking.monitoring;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 降级管理器 - 核心控制器
 * 
 * 负责：
 * - 系统指标采集
 * - 降级级别决策
 * - 降级事件记录
 * - 周期性评估和调整
 * 
 * 线程安全设计，默认关闭自动降级，仅记录指标
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Component
@ConditionalOnProperty(prefix = "tfi.change-tracking.degradation", name = "enabled", havingValue = "true")
public class DegradationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DegradationManager.class);
    
    /** 当前降级级别 */
    private volatile DegradationLevel currentLevel = DegradationLevel.FULL_TRACKING;
    
    /** 最后评估的指标 */
    private final AtomicReference<SystemMetrics> lastMetrics = new AtomicReference<>();
    
    /** 最后级别变更时间 */
    private volatile long lastLevelChangeTime = 0;
    
    // 依赖注入的组件
    private final DegradationPerformanceMonitor performanceMonitor;
    private final ResourceMonitor resourceMonitor;
    private final TfiMetrics tfiMetrics;
    private final DegradationConfig config;
    private final DegradationDecisionEngine decisionEngine;
    private final ApplicationEventPublisher eventPublisher;
    
    public DegradationManager(
            DegradationPerformanceMonitor performanceMonitor,
            ResourceMonitor resourceMonitor,
            TfiMetrics tfiMetrics,
            DegradationConfig config,
            DegradationDecisionEngine decisionEngine,
            ApplicationEventPublisher eventPublisher) {
        this.performanceMonitor = performanceMonitor;
        this.resourceMonitor = resourceMonitor;
        this.tfiMetrics = tfiMetrics;
        this.config = config;
        this.decisionEngine = decisionEngine;
        this.eventPublisher = eventPublisher;
        
        logger.info("DegradationManager initialized with config: {}", config);
        
        // 注册当前降级级别Gauge指标
        registerDegradationLevelGauge();
    }
    
    /**
     * 周期性评估和调整降级级别
     * 使用配置的评估间隔（默认5秒）
     */
    @Scheduled(fixedDelayString = "${tfi.change-tracking.degradation.evaluation-interval:5000}")
    public void evaluateAndAdjust() {
        try {
            // 收集当前系统指标
            SystemMetrics currentMetrics = gatherSystemMetrics();
            lastMetrics.set(currentMetrics);
            
            // 基于指标计算推荐的降级级别
            DegradationLevel recommendedLevel = decisionEngine.calculateOptimalLevel(currentMetrics, performanceMonitor);
            
            // 检查是否需要变更级别（考虑滞后机制）
            if (shouldChangeDegradationLevel(recommendedLevel)) {
                DegradationLevel previousLevel = this.currentLevel;
                this.currentLevel = recommendedLevel;
                this.lastLevelChangeTime = System.currentTimeMillis();
                
                // 设置全局降级上下文，供主链路使用
                DegradationContext.setCurrentLevel(recommendedLevel);
                DegradationContext.setPerformanceMonitor(performanceMonitor);
                
                // 记录降级事件
                String reason = getDegradationReason(currentMetrics, recommendedLevel);
                tfiMetrics.recordDegradationEvent(
                    previousLevel.name(),
                    recommendedLevel.name(),
                    reason
                );
                
                logger.info("Degradation level changed: {} -> {} ({})", 
                    previousLevel, recommendedLevel, reason);
                
                // 发布Spring事件（如果启用）
                publishDegradationEvent(previousLevel, recommendedLevel, reason, currentMetrics);
            }
            
            // 记录当前指标到监控系统
            recordCurrentMetrics(currentMetrics);
            
        } catch (Exception e) {
            logger.error("Error during degradation evaluation", e);
            tfiMetrics.recordError("degradation_evaluation_failed");
        }
    }
    
    /**
     * 收集当前系统指标
     */
    private SystemMetrics gatherSystemMetrics() {
        return SystemMetrics.builder()
            .averageOperationTime(performanceMonitor.getAverageTime())
            .slowOperationCount(performanceMonitor.getSlowOperationCount())
            .memoryUsagePercent(resourceMonitor.getMemoryUsagePercent())
            .availableMemoryMB(resourceMonitor.getAvailableMemoryMB())
            .cpuUsagePercent(resourceMonitor.getCpuUsagePercent())
            .threadCount(resourceMonitor.getActiveThreadCount())
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    
    /**
     * 检查是否应该变更降级级别（滞后机制）
     */
    private boolean shouldChangeDegradationLevel(DegradationLevel recommendedLevel) {
        if (recommendedLevel == currentLevel) {
            return false;
        }
        
        // 滞后机制：避免频繁变更
        long timeSinceLastChange = System.currentTimeMillis() - lastLevelChangeTime;
        if (timeSinceLastChange < config.minLevelChangeDuration().toMillis()) {
            logger.debug("Degradation level change suppressed due to hysteresis: {} -> {} ({}ms ago)",
                currentLevel, recommendedLevel, timeSinceLastChange);
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取降级原因描述
     */
    private String getDegradationReason(SystemMetrics metrics, DegradationLevel level) {
        StringBuilder reason = new StringBuilder();
        
        if (metrics.memoryUsagePercent() >= config.memoryThresholds().summaryOnly()) {
            reason.append("memory_usage=").append(String.format("%.1f%%", metrics.memoryUsagePercent()));
        }
        
        if (metrics.averageOperationTime().toMillis() > config.performanceThresholds().averageOperationTimeMs()) {
            if (reason.length() > 0) reason.append(",");
            reason.append("avg_time=").append(metrics.averageOperationTime().toMillis()).append("ms");
        }
        
        if (performanceMonitor.getSlowOperationRate() > config.performanceThresholds().slowOperationRate()) {
            if (reason.length() > 0) reason.append(",");
            reason.append("slow_rate=").append(String.format("%.2f%%", performanceMonitor.getSlowOperationRate() * 100));
        }
        
        return reason.length() > 0 ? reason.toString() : "auto_evaluation";
    }
    
    /**
     * 记录当前指标到监控系统
     */
    private void recordCurrentMetrics(SystemMetrics metrics) {
        // 记录到现有的TfiMetrics系统
        if (config.isSlowOperation(metrics.averageOperationTime())) {
            tfiMetrics.recordSlowOperation("change_tracking", metrics.averageOperationTime().toMillis());
        }
        
        // 可以在这里添加更多指标记录
    }
    
    // ==================== 公共API ====================
    
    /**
     * 获取当前降级级别
     */
    public DegradationLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * 强制设置降级级别
     */
    public void forceLevel(DegradationLevel level, String reason) {
        DegradationLevel previousLevel = this.currentLevel;
        this.currentLevel = level;
        this.lastLevelChangeTime = System.currentTimeMillis();
        
        tfiMetrics.recordDegradationEvent(previousLevel.name(), level.name(), "forced:" + reason);
        logger.warn("Forced degradation level change: {} -> {} ({})", previousLevel, level, reason);
    }
    
    /**
     * 获取最近的系统指标
     */
    public SystemMetrics getLastMetrics() {
        return lastMetrics.get();
    }
    
    /**
     * 检查系统是否健康
     */
    public boolean isSystemHealthy() {
        SystemMetrics metrics = lastMetrics.get();
        if (metrics == null) {
            return true; // 没有指标时假设健康
        }
        
        return currentLevel != DegradationLevel.DISABLED &&
               !config.isCriticalMemoryUsage(metrics.memoryUsagePercent()) &&
               !config.isSlowOperation(metrics.averageOperationTime());
    }
    
    /**
     * 获取状态摘要
     */
    public String getStatusSummary() {
        SystemMetrics metrics = lastMetrics.get();
        return String.format("DegradationManager{level=%s, healthy=%s, metrics=%s}",
            currentLevel, isSystemHealthy(), metrics != null ? metrics.toString() : "none");
    }
    
    /**
     * 发布降级事件
     */
    private void publishDegradationEvent(DegradationLevel from, DegradationLevel to, 
                                        String reason, SystemMetrics metrics) {
        try {
            if (eventPublisher != null) {
                DegradationLevelChangedEvent event = new DegradationLevelChangedEvent(
                    this, from, to, reason, metrics
                );
                eventPublisher.publishEvent(event);
                logger.debug("Published degradation event: {} -> {}", from, to);
            }
        } catch (Exception e) {
            logger.warn("Failed to publish degradation event: {}", e.getMessage());
        }
    }
    
    /**
     * 注册当前降级级别Gauge指标
     */
    private void registerDegradationLevelGauge() {
        try {
            // 通过TfiMetrics注册Gauge
            if (tfiMetrics != null) {
                // 降级级别数值（0-4，越高越严格）
                tfiMetrics.registerGauge("tfi.degradation.current_level", 
                    () -> (double) currentLevel.ordinal());
                
                // 系统健康状态（1=健康，0=不健康）
                tfiMetrics.registerGauge("tfi.degradation.healthy", 
                    () -> isSystemHealthy() ? 1.0 : 0.0);
                
                logger.debug("Registered degradation level gauge metrics");
            }
        } catch (Exception e) {
            logger.warn("Failed to register degradation gauge metrics: {}", e.getMessage());
        }
    }
}