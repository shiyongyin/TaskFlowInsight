package com.syy.taskflowinsight.tracking.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 深度快照策略实现
 * 支持对象图深度遍历和异步执行
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
public class DeepSnapshotStrategy implements SnapshotStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(DeepSnapshotStrategy.class);
    
    private final ObjectSnapshotDeep deepSnapshot;
    
    @Autowired
    public DeepSnapshotStrategy(SnapshotConfig config) {
        this.deepSnapshot = new ObjectSnapshotDeep(config);
    }
    
    @Override
    public Map<String, Object> capture(String objectName, Object target, SnapshotConfig config, String... fields) {
        if (target == null) {
            return Collections.emptyMap();
        }
        
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
            logger.error("Deep snapshot failed for object: {}", objectName, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * 异步捕获快照
     */
    public CompletableFuture<Map<String, Object>> captureAsync(String objectName, Object target, 
                                                                SnapshotConfig config, String... fields) {
        return CompletableFuture.supplyAsync(() -> capture(objectName, target, config, fields))
            .orTimeout(config.getTimeBudgetMs() * 2, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                logger.error("Async deep snapshot failed for object: {}", objectName, ex);
                return Collections.emptyMap();
            });
    }
    
    @Override
    public String getName() {
        return "deep";
    }
    
    @Override
    public boolean supportsAsync() {
        return true;
    }
    
    @Override
    public void validateConfig(SnapshotConfig config) {
        if (config.getMaxDepth() < 0 || config.getMaxDepth() > 10) {
            throw new IllegalArgumentException("maxDepth must be between 0 and 10, got: " + config.getMaxDepth());
        }
        
        if (config.getMaxStackDepth() < 100 || config.getMaxStackDepth() > 10000) {
            throw new IllegalArgumentException("maxStackDepth must be between 100 and 10000, got: " + config.getMaxStackDepth());
        }
        
        if (config.getTimeBudgetMs() < 0 || config.getTimeBudgetMs() > 5000) {
            throw new IllegalArgumentException("timeBudgetMs must be between 0 and 5000, got: " + config.getTimeBudgetMs());
        }
        
        if (config.getCollectionSummaryThreshold() < 10) {
            throw new IllegalArgumentException("collectionSummaryThreshold must be >= 10, got: " + config.getCollectionSummaryThreshold());
        }
    }
}