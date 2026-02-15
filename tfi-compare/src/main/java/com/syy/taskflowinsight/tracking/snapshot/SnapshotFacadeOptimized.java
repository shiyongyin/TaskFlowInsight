package com.syy.taskflowinsight.tracking.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 优化后的快照门面类
 * 使用Strategy模式管理不同的快照策略
 * 
 * 改进点：
 * - 策略模式解耦
 * - 支持异步快照
 * - 配置验证
 * - 更好的扩展性
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@Component
public class SnapshotFacadeOptimized {
    
    private static final Logger logger = LoggerFactory.getLogger(SnapshotFacadeOptimized.class);
    
    private final SnapshotConfig config;
    private final Map<String, SnapshotStrategy> strategies = new HashMap<>();
    private SnapshotStrategy currentStrategy;
    
    @Autowired
    public SnapshotFacadeOptimized(SnapshotConfig config,
                                   ShallowSnapshotStrategy shallowStrategy,
                                   DeepSnapshotStrategy deepStrategy) {
        this.config = config;
        registerStrategy(shallowStrategy);
        registerStrategy(deepStrategy);
        updateCurrentStrategy();
    }
    
    /**
     * 配置验证和初始化
     */
    @PostConstruct
    public void init() {
        validateConfiguration();
        logger.info("SnapshotFacade initialized with strategy: {}", currentStrategy.getName());
    }
    
    /**
     * 验证配置
     */
    private void validateConfiguration() {
        if (currentStrategy != null) {
            currentStrategy.validateConfig(config);
        }
    }
    
    /**
     * 注册策略
     */
    public void registerStrategy(SnapshotStrategy strategy) {
        strategies.put(strategy.getName(), strategy);
        logger.debug("Registered snapshot strategy: {}", strategy.getName());
    }
    
    /**
     * 捕获对象快照（同步）
     */
    public Map<String, Object> capture(String objectName, Object target, String... fields) {
        if (target == null) {
            return Collections.emptyMap();
        }
        
        try {
            return currentStrategy.capture(objectName, target, config, fields);
        } catch (Exception e) {
            logger.error("Snapshot capture failed for object: {}, falling back to shallow", objectName, e);
            return fallbackCapture(objectName, target, fields);
        }
    }
    
    /**
     * 捕获对象快照（异步）
     */
    public CompletableFuture<Map<String, Object>> captureAsync(String objectName, Object target, String... fields) {
        if (!currentStrategy.supportsAsync()) {
            // 如果策略不支持异步，使用默认的异步包装
            return CompletableFuture.supplyAsync(() -> capture(objectName, target, fields));
        }
        
        if (currentStrategy instanceof DeepSnapshotStrategy) {
            return ((DeepSnapshotStrategy) currentStrategy).captureAsync(objectName, target, config, fields);
        }
        
        return CompletableFuture.supplyAsync(() -> capture(objectName, target, fields));
    }
    
    /**
     * 降级捕获
     */
    private Map<String, Object> fallbackCapture(String objectName, Object target, String... fields) {
        SnapshotStrategy fallback = strategies.get("shallow");
        if (fallback != null) {
            try {
                return fallback.capture(objectName, target, config, fields);
            } catch (Exception e) {
                logger.error("Fallback capture also failed for object: {}", objectName, e);
            }
        }
        return Collections.emptyMap();
    }
    
    /**
     * 更新当前策略
     */
    private void updateCurrentStrategy() {
        String strategyName = config.isEnableDeep() ? "deep" : "shallow";
        currentStrategy = strategies.get(strategyName);
        
        if (currentStrategy == null) {
            currentStrategy = strategies.get("shallow");
            logger.warn("Strategy {} not found, using shallow", strategyName);
        }
    }
    
    /**
     * 动态切换策略
     */
    public void setStrategy(String strategyName) {
        SnapshotStrategy strategy = strategies.get(strategyName);
        if (strategy != null) {
            currentStrategy = strategy;
            currentStrategy.validateConfig(config);
            logger.info("Switched to snapshot strategy: {}", strategyName);
        } else {
            logger.warn("Strategy {} not found", strategyName);
        }
    }
    
    /**
     * 动态切换深度快照开关
     */
    public void setEnableDeep(boolean enable) {
        config.setEnableDeep(enable);
        updateCurrentStrategy();
        logger.info("Deep snapshot {} at runtime", enable ? "enabled" : "disabled");
    }
    
    /**
     * 获取当前策略名称
     */
    public String getCurrentStrategyName() {
        return currentStrategy != null ? currentStrategy.getName() : "none";
    }
    
    /**
     * 获取所有可用策略
     */
    public Set<String> getAvailableStrategies() {
        return strategies.keySet();
    }
    
    /**
     * 获取配置
     */
    public SnapshotConfig getConfig() {
        return config;
    }
    
    /**
     * 获取性能指标
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("current_strategy", getCurrentStrategyName());
        metrics.put("available_strategies", getAvailableStrategies());
        metrics.put("deep_snapshot_metrics", ObjectSnapshotDeepOptimized.getMetrics());
        return metrics;
    }
}