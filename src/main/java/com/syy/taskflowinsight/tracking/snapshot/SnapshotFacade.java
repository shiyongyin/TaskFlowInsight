package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;
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
     * 使用TrackingOptions捕获对象快照
     * 
     * @param objectName 对象名称
     * @param target 目标对象
     * @param options 追踪配置选项
     * @return 快照结果
     */
    public Map<String, Object> capture(String objectName, Object target, TrackingOptions options) {
        if (target == null) {
            return Collections.emptyMap();
        }
        
        if (options == null) {
            // 使用默认配置
            return capture(objectName, target);
        }
        
        // 根据TrackingOptions决定使用哪种快照方式
        if (options.getDepth() == TrackingOptions.TrackingDepth.DEEP) {
            logger.debug("Using deep snapshot for object: {} with options: {}", objectName, options.getDepth());
            return captureDeepWithOptions(objectName, target, options);
        } else {
            logger.debug("Using shallow snapshot for object: {} with options: {}", objectName, options.getDepth());
            return captureShallowWithOptions(objectName, target, options);
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
            
            // 确保使用默认的集合策略
            deepSnapshot.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
            
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
     * 使用深度快照和TrackingOptions捕获
     */
    private Map<String, Object> captureDeepWithOptions(String objectName, Object target, TrackingOptions options) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 创建专用的深度快照实例（使用TrackingOptions的配置）
            SnapshotConfig customConfig = createCustomConfig(options);
            ObjectSnapshotDeep customDeepSnapshot = new ObjectSnapshotDeep(customConfig);
            
            // 设置集合处理策略
            customDeepSnapshot.setCollectionStrategy(options.getCollectionStrategy());
            
            Map<String, Object> result = customDeepSnapshot.captureDeep(
                target,
                options.getMaxDepth(),
                options.getIncludeFields(),
                options.getExcludeFields()
            );
            
            long duration = System.currentTimeMillis() - startTime;
            if (duration > options.getTimeBudgetMs()) {
                logger.warn("Deep snapshot exceeded time budget: {}ms > {}ms for object: {}", 
                    duration, options.getTimeBudgetMs(), objectName);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Deep snapshot with options failed for object: {}, falling back to shallow", objectName, e);
            // 失败时降级到浅快照
            return captureShallowWithOptions(objectName, target, options);
        }
    }
    
    /**
     * 使用浅快照和TrackingOptions捕获
     */
    private Map<String, Object> captureShallowWithOptions(String objectName, Object target, TrackingOptions options) {
        try {
            String[] fields = null;
            if (!options.getIncludeFields().isEmpty()) {
                fields = options.getIncludeFields().toArray(new String[0]);
            }
            return ObjectSnapshot.capture(objectName, target, fields);
        } catch (Exception e) {
            logger.error("Shallow snapshot with options failed for object: {}", objectName, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * 根据TrackingOptions创建自定义SnapshotConfig
     */
    private SnapshotConfig createCustomConfig(TrackingOptions options) {
        SnapshotConfig customConfig = new SnapshotConfig();
        customConfig.setEnableDeep(true);
        customConfig.setMaxDepth(options.getMaxDepth());
        customConfig.setTimeBudgetMs(options.getTimeBudgetMs());
        customConfig.setCollectionSummaryThreshold(options.getCollectionSummaryThreshold());
        customConfig.setMetricsEnabled(options.isEnablePerformanceMonitoring());
        
        // 设置排除模式
        List<String> excludePatterns = new ArrayList<>(options.getExcludeFields());
        customConfig.setExcludePatterns(excludePatterns);
        
        return customConfig;
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