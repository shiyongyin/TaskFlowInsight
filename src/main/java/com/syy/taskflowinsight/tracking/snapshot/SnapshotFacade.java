package com.syy.taskflowinsight.tracking.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 快照门面类
 * 统一管理浅快照和深度快照的路由
 * 
 * 核心设计：
 * - 兼容性优先：默认使用浅快照
 * - 配置控制：通过配置决定是否启用深度快照
 * - 统一接口：对外提供一致的API
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
public class SnapshotFacade {
    
    private static final Logger logger = LoggerFactory.getLogger(SnapshotFacade.class);
    
    private final SnapshotConfig config;
    private final ObjectSnapshotDeep deepSnapshot;
    
    @Autowired
    public SnapshotFacade(SnapshotConfig config) {
        this.config = config;
        this.deepSnapshot = new ObjectSnapshotDeep(config);
    }
    
    /**
     * 捕获对象快照
     * 根据配置决定使用浅快照还是深度快照
     * 
     * @param objectName 对象名称
     * @param target 目标对象
     * @param fields 要捕获的字段列表
     * @return 快照结果
     */
    public Map<String, Object> capture(String objectName, Object target, String... fields) {
        if (target == null) {
            return Collections.emptyMap();
        }
        
        // 根据配置决定使用哪种快照方式
        if (shouldUseDeep()) {
            logger.debug("Using deep snapshot for object: {}", objectName);
            return captureDeep(objectName, target, fields);
        } else {
            logger.debug("Using shallow snapshot for object: {}", objectName);
            return captureShallow(objectName, target, fields);
        }
    }
    
    /**
     * 使用深度快照捕获
     */
    private Map<String, Object> captureDeep(String objectName, Object target, String... fields) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 转换字段列表为包含模式
            Set<String> includeFields = fields.length > 0 
                ? new HashSet<>(Arrays.asList(fields))
                : Collections.emptySet();
            
            Map<String, Object> result = deepSnapshot.captureDeep(
                target,
                config.getMaxDepth(),
                includeFields,
                config.getExcludePatternSet()
            );
            
            long duration = System.currentTimeMillis() - startTime;
            if (duration > config.getTimeBudgetMs()) {
                logger.warn("Deep snapshot exceeded time budget: {}ms > {}ms for object: {}", 
                    duration, config.getTimeBudgetMs(), objectName);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Deep snapshot failed for object: {}, falling back to shallow", objectName, e);
            // 失败时降级到浅快照
            return captureShallow(objectName, target, fields);
        }
    }
    
    /**
     * 使用浅快照捕获（兼容原有逻辑）
     */
    private Map<String, Object> captureShallow(String objectName, Object target, String... fields) {
        try {
            return ObjectSnapshot.capture(objectName, target, fields);
        } catch (Exception e) {
            logger.error("Shallow snapshot failed for object: {}", objectName, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * 判断是否应该使用深度快照
     */
    private boolean shouldUseDeep() {
        return config.isEnableDeep();
    }
    
    /**
     * 获取当前配置（用于测试和调试）
     */
    public SnapshotConfig getConfig() {
        return config;
    }
    
    /**
     * 动态切换深度快照开关（用于运行时调整）
     */
    public void setEnableDeep(boolean enable) {
        config.setEnableDeep(enable);
        logger.info("Deep snapshot {} at runtime", enable ? "enabled" : "disabled");
    }
}