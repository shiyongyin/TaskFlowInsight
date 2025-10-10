package com.syy.taskflowinsight.config.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置迁移映射器
 * 
 * 负责处理旧配置键到新配置键的映射，并提供一次性迁移警告
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Component
public class ConfigMigrationMapper {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigMigrationMapper.class);
    
    /** 旧键 -> 新键 映射表 */
    private static final Map<String, String> MIGRATION_MAPPINGS = new HashMap<>();
    
    /** 已警告的键（避免重复警告） */
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();
    
    /** 迁移统计 */
    private final Map<String, MigrationStats> migrationStats = new ConcurrentHashMap<>();
    
    static {
        // 初始化迁移映射表
        initializeMappings();
    }
    
    private static void initializeMappings() {
        // 深度和性能相关
        MIGRATION_MAPPINGS.put(
            "tfi.change-tracking.max-depth",
            "tfi.change-tracking.snapshot.max-depth"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.change-tracking.time-budget",
            "tfi.change-tracking.snapshot.time-budget-ms"
        );
        
        // 支持环境变量配置键名（任务卡要求）  
        MIGRATION_MAPPINGS.put(
            "tfi.config.env-vars.enabled",
            "tfi.config.enable-env"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.tracking.slow-operation-threshold",
            "tfi.change-tracking.degradation.slow-operation-threshold-ms"
        );
        
        // 监控键名统一（任务卡要求）
        MIGRATION_MAPPINGS.put(
            "tfi.change-tracking.degradation.slow-operation-threshold-ms",
            "tfi.change-tracking.monitoring.slow-operation-ms"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.tracking.slow-threshold",
            "tfi.change-tracking.degradation.slow-operation-threshold-ms"
        );
        
        // 上下文管理相关
        MIGRATION_MAPPINGS.put(
            "tfi.context.cleanup-interval",
            "tfi.context.cleanup.interval-millis"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.context.max-age",
            "tfi.context.max-context-age-millis"
        );
        MIGRATION_MAPPINGS.put(
            "taskflow.context.max-age-millis",
            "tfi.context.max-context-age-millis"
        );
        
        // 集合和摘要相关
        MIGRATION_MAPPINGS.put(
            "tfi.change-tracking.collection-threshold",
            "tfi.change-tracking.snapshot.collection-summary-threshold"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.change-tracking.summary-threshold",
            "tfi.change-tracking.summary.max-size"
        );
        
        // 降级相关
        MIGRATION_MAPPINGS.put(
            "tfi.degradation.memory-threshold",
            "tfi.change-tracking.degradation.critical-memory-threshold"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.degradation.cpu-threshold",
            "tfi.change-tracking.degradation.performance-thresholds.cpu-usage-percent"
        );
        
        // 功能开关相关
        MIGRATION_MAPPINGS.put(
            "tfi.tracking.enabled",
            "tfi.change-tracking.enabled"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.annotation.enable",
            "tfi.annotation.enabled"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.metrics.enable",
            "tfi.change-tracking.snapshot.metrics-enabled"
        );
        
        // 导出配置相关
        MIGRATION_MAPPINGS.put(
            "tfi.export.format-type",
            "tfi.change-tracking.export.format"
        );
        MIGRATION_MAPPINGS.put(
            "tfi.export.pretty",
            "tfi.change-tracking.export.pretty-print"
        );
    }
    
    /**
     * 检查并转换旧配置键
     * 
     * @param key 配置键
     * @return 新配置键（如果需要迁移），否则返回原键
     */
    public String checkAndMigrate(String key) {
        if (MIGRATION_MAPPINGS.containsKey(key)) {
            String newKey = MIGRATION_MAPPINGS.get(key);
            
            // 只警告一次
            if (warnedKeys.add(key)) {
                logMigrationWarning(key, newKey);
            }
            
            // 记录统计
            updateStats(key, newKey);
            
            return newKey;
        }
        return key;
    }
    
    /**
     * 检查是否是已废弃的键
     */
    public boolean isDeprecatedKey(String key) {
        return MIGRATION_MAPPINGS.containsKey(key);
    }
    
    /**
     * 获取新键（不产生警告）
     */
    public String getNewKey(String oldKey) {
        return MIGRATION_MAPPINGS.getOrDefault(oldKey, oldKey);
    }
    
    /**
     * 记录迁移警告
     */
    private void logMigrationWarning(String oldKey, String newKey) {
        logger.warn("DEPRECATED: Configuration key '{}' is deprecated since v3.0.0. " +
                   "Please migrate to '{}'. Support for deprecated keys will be removed in v4.0.0.",
                   oldKey, newKey);
    }
    
    /**
     * 更新迁移统计
     */
    private void updateStats(String oldKey, String newKey) {
        migrationStats.compute(oldKey, (k, stats) -> {
            if (stats == null) {
                return new MigrationStats(oldKey, newKey, 1);
            }
            stats.incrementUsage();
            return stats;
        });
    }
    
    /**
     * 获取迁移报告
     */
    public String getMigrationReport() {
        if (migrationStats.isEmpty()) {
            return "No deprecated configuration keys detected.";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("=== Configuration Migration Report ===\n");
        report.append("Deprecated keys detected: ").append(migrationStats.size()).append("\n\n");
        
        migrationStats.values().forEach(stats -> {
            report.append(String.format("  %s -> %s (used %d times)\n",
                stats.oldKey, stats.newKey, stats.usageCount));
        });
        
        report.append("\nAction Required: Update your configuration files to use the new keys.\n");
        report.append("Support for deprecated keys will be removed in v4.0.0.\n");
        
        return report.toString();
    }
    
    /**
     * 获取所有迁移映射（用于文档）
     */
    public static Map<String, String> getAllMappings() {
        return new HashMap<>(MIGRATION_MAPPINGS);
    }

    /**
     * 获取映射到指定新键的旧键（如果存在）
     * @param newKey 新配置键
     * @return 对应的旧配置键；如果不存在，返回null
     */
    public String getOldKeyForNew(String newKey) {
        for (Map.Entry<String, String> e : MIGRATION_MAPPINGS.entrySet()) {
            if (Objects.equals(e.getValue(), newKey)) {
                return e.getKey();
            }
        }
        return null;
    }
    
    /**
     * 清除警告缓存（用于测试）
     */
    public void clearWarnings() {
        warnedKeys.clear();
        migrationStats.clear();
    }
    
    /**
     * 迁移统计信息
     */
    private static class MigrationStats {
        private final String oldKey;
        private final String newKey;
        private int usageCount;
        
        MigrationStats(String oldKey, String newKey, int usageCount) {
            this.oldKey = oldKey;
            this.newKey = newKey;
            this.usageCount = usageCount;
        }
        
        void incrementUsage() {
            this.usageCount++;
        }
    }
}
