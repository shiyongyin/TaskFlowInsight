package com.syy.taskflowinsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 变更追踪配置属性（增强版）
 * 支持分层嵌套配置结构，提供更细粒度的控制
 * 
 * 根据VIP-007要求，采用MVP最小配置集
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@ConfigurationProperties(prefix = "tfi.change-tracking")
public class ChangeTrackingPropertiesV2 {
    
    /**
     * 主开关 - 是否启用变更追踪功能
     * MVP最小配置集核心项
     */
    private boolean enabled = false;
    
    /**
     * 值表示的最大长度
     * 超过此长度的字符串将被截断
     */
    private int valueReprMaxLength = 8192;
    
    /**
     * 清理间隔（分钟）
     * 0表示不启用定时清理
     */
    private int cleanupIntervalMinutes = 5;
    
    /**
     * 反射缓存的最大类数量
     */
    private int maxCachedClasses = 1024;
    
    /**
     * 快照配置
     */
    private SnapshotProperties snapshot = new SnapshotProperties();
    
    /**
     * 集合摘要配置
     */
    private SummaryProperties summary = new SummaryProperties();
    
    /**
     * 差异检测配置
     */
    private DiffProperties diff = new DiffProperties();
    
    /**
     * 导出配置
     */
    private ExportProperties export = new ExportProperties();
    
    /**
     * 快照配置属性
     */
    @Data
    public static class SnapshotProperties {
        /**
         * 是否启用深度快照（Phase 2+）
         */
        private boolean enableDeep = false;
        
        /**
         * 最大深度
         */
        private int maxDepth = 3;
        
        /**
         * 栈深度限制（防止循环引用）
         */
        private int maxStackDepth = 1000;
        
        /**
         * 集合最大元素数
         */
        private int maxElements = 100;
        
        /**
         * 包含的字段模式
         */
        private List<String> includes = new ArrayList<>();
        
        /**
         * 排除的字段模式（默认排除敏感字段）
         */
        private List<String> excludes = Arrays.asList("*.password", "*.secret", "*.token", "*.key");
    }
    
    /**
     * 集合摘要配置属性
     */
    @Data
    public static class SummaryProperties {
        /**
         * 是否启用集合摘要
         */
        private boolean enabled = true;
        
        /**
         * 触发摘要的集合大小阈值
         */
        private int maxSize = 100;
        
        /**
         * 摘要中的最大示例数
         */
        private int maxExamples = 10;
        
        /**
         * 敏感词列表（用于脱敏）
         */
        private List<String> sensitiveWords = Arrays.asList("password", "token", "secret", "key", "credential");
    }
    
    /**
     * 差异检测配置属性
     */
    @Data
    public static class DiffProperties {
        /**
         * 输出模式：compat（兼容）/enhanced（增强）
         */
        private String outputMode = "compat";
        
        /**
         * 是否包含null->null的变更
         */
        private boolean includeNullChanges = false;
        
        /**
         * 每个对象的最大变更数
         */
        private int maxChangesPerObject = 1000;
        
        /**
         * 是否启用值归一化
         */
        private boolean normalizeValues = true;
        
        // Explicit getters for IDE compatibility
        public String getOutputMode() { return outputMode; }
        public boolean isIncludeNullChanges() { return includeNullChanges; }
        public int getMaxChangesPerObject() { return maxChangesPerObject; }
        public boolean isNormalizeValues() { return normalizeValues; }
    }
    
    /**
     * 导出配置属性
     */
    @Data
    public static class ExportProperties {
        /**
         * 默认导出格式：json/console/map
         */
        private String format = "json";
        
        /**
         * 是否格式化输出（美化）
         */
        private boolean prettyPrint = true;
        
        /**
         * 是否包含元数据
         */
        private boolean includeMetadata = false;
        
        /**
         * 是否包含时间戳
         */
        private boolean showTimestamp = false;
        
        /**
         * 是否包含敏感信息（默认脱敏）
         */
        private boolean includeSensitiveInfo = false;
        
        // Explicit getters for IDE compatibility
        public String getFormat() { return format; }
        public boolean isPrettyPrint() { return prettyPrint; }
        public boolean isIncludeMetadata() { return includeMetadata; }
        public boolean isShowTimestamp() { return showTimestamp; }
        public boolean isIncludeSensitiveInfo() { return includeSensitiveInfo; }
    }
    
    // Explicit getters for IDE compatibility (in case Lombok annotation processing fails)
    public boolean isEnabled() { return enabled; }
    public int getValueReprMaxLength() { return valueReprMaxLength; }
    public int getCleanupIntervalMinutes() { return cleanupIntervalMinutes; }
    public int getMaxCachedClasses() { return maxCachedClasses; }
    public SnapshotProperties getSnapshot() { return snapshot; }
    public SummaryProperties getSummary() { return summary; }
    public DiffProperties getDiff() { return diff; }
    public ExportProperties getExport() { return export; }
    
    /**
     * 验证配置有效性
     */
    public boolean isValid() {
        return valueReprMaxLength > 0 && 
               valueReprMaxLength <= 1_000_000 && // 合理上限
               cleanupIntervalMinutes >= 0 &&
               maxCachedClasses > 0 &&
               snapshot.maxDepth > 0 &&
               snapshot.maxDepth <= 100 && // 防止过深
               snapshot.maxStackDepth > 0 &&
               snapshot.maxElements >= 0 &&
               summary.maxSize > 0 &&
               summary.maxExamples >= 0 &&
               diff.maxChangesPerObject > 0;
    }
    
    /**
     * 是否启用定时清理
     */
    public boolean isCleanupEnabled() {
        return cleanupIntervalMinutes > 0;
    }
    
    /**
     * 获取清理间隔（毫秒）
     */
    public long getCleanupIntervalMillis() {
        return cleanupIntervalMinutes * 60L * 1000L;
    }
}