package com.syy.taskflowinsight.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Map;
import java.util.Set;

/**
 * 统一配置类（不可变）
 * 替代原有的6个配置类，采用record设计保证不可变性
 * 
 * 配置映射：
 * - tfi.enabled → 全局开关
 * - tfi.change-tracking.* → ChangeTrackingPropertiesV2.*
 * - tfi.context.* → ContextManagerProperties.*, ThreadLocalManagerProperties.*
 * - tfi.metrics.* → 指标配置
 * - tfi.security.* → 安全配置
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-09-18
 */
@ConfigurationProperties(prefix = "tfi")
@Validated
public record TfiConfig(
    @NotNull Boolean enabled,
    @Valid @NotNull ChangeTracking changeTracking,
    @Valid @NotNull Context context,
    @Valid @NotNull Metrics metrics,
    @Valid @NotNull Security security
) {
    // 默认值构造
    public TfiConfig {
        enabled = enabled != null ? enabled : false;
        changeTracking = changeTracking != null ? changeTracking : new ChangeTracking(null, null, null, null, null, null, null, null);
        context = context != null ? context : new Context(null, null, null, null, null);
        metrics = metrics != null ? metrics : new Metrics(null, null, null);
        security = security != null ? security : new Security(null, null);
    }
    
    /**
     * 变更追踪配置
     * 映射自 ChangeTrackingPropertiesV2
     */
    public record ChangeTracking(
        Boolean enabled,
        @Min(10) @Max(10000) Integer valueReprMaxLength,
        @Min(1) @Max(60) Integer cleanupIntervalMinutes,
        @Valid Snapshot snapshot,
        @Valid Diff diff,
        @Valid Export export,
        @Min(1) @Max(10000) Integer maxCachedClasses,
        @Valid Summary summary
    ) {
        public ChangeTracking {
            enabled = enabled != null ? enabled : false;
            valueReprMaxLength = valueReprMaxLength != null ? valueReprMaxLength : 8192;
            cleanupIntervalMinutes = cleanupIntervalMinutes != null ? cleanupIntervalMinutes : 5;
            maxCachedClasses = maxCachedClasses != null ? maxCachedClasses : 1024;
            snapshot = snapshot != null ? snapshot : new Snapshot(null, null, null, null, null, null);
            diff = diff != null ? diff : new Diff(null, null, null, null, null);
            export = export != null ? export : new Export(null, null, null, null, null);
            summary = summary != null ? summary : new Summary(null, null, null, null);
        }
        
        /**
         * 快照配置
         * 注意：最终生效默认值以 ConfigDefaults/Resolver 解析结果为准
         */
        public record Snapshot(
            @Min(1) @Max(100) Integer maxDepth,
            @Min(10) @Max(10000) Integer maxElements,
            Set<String> excludes,
            @Min(1) @Max(10000) Integer maxStackDepth,
            Boolean enableDeep,
            Long timeBudgetMs  // 补充缺失的时间预算配置
        ) {
            public Snapshot {
                maxDepth = maxDepth != null ? maxDepth : 10;  // 与 ConfigDefaults.MAX_DEPTH 对齐
                maxElements = maxElements != null ? maxElements : 100;
                excludes = excludes != null ? excludes : Set.of("*.password", "*.secret", "*.token", "*.key");
                maxStackDepth = maxStackDepth != null ? maxStackDepth : 1000;
                enableDeep = enableDeep != null ? enableDeep : false;
                timeBudgetMs = timeBudgetMs != null ? timeBudgetMs : 1000L;  // 与 ConfigDefaults.TIME_BUDGET_MS 对齐
            }
        }
        
        /**
         * 差异检测配置
         */
        public record Diff(
            String outputMode,
            Boolean includeNullChanges,
            @Min(1) @Max(10000) Integer maxChangesPerObject,
            Boolean normalizeValues,
            String pathFormat
        ) {
            public Diff {
                outputMode = outputMode != null ? outputMode : "compat";
                includeNullChanges = includeNullChanges != null ? includeNullChanges : false;
                maxChangesPerObject = maxChangesPerObject != null ? maxChangesPerObject : 1000;
                normalizeValues = normalizeValues != null ? normalizeValues : true;
                // 路径格式：legacy=单引号兼容模式，standard=双引号新标准
                pathFormat = pathFormat != null ? pathFormat : "legacy";
            }
            
            /**
             * 是否使用新的路径格式标准
             */
            public boolean useStandardPathFormat() {
                return "standard".equals(pathFormat);
            }
        }
        
        /**
         * 导出配置
         */
        public record Export(
            String format,
            Boolean prettyPrint,
            Boolean showTimestamp,
            Boolean includeSensitiveInfo,
            Boolean includeMetadata
        ) {
            public Export {
                format = format != null ? format : "json";
                prettyPrint = prettyPrint != null ? prettyPrint : true;
                showTimestamp = showTimestamp != null ? showTimestamp : false;
                includeSensitiveInfo = includeSensitiveInfo != null ? includeSensitiveInfo : false;
                includeMetadata = includeMetadata != null ? includeMetadata : false;
            }
        }
        
        /**
         * 摘要配置
         */
        public record Summary(
            Boolean enabled,
            @Min(1) @Max(10000) Integer maxSize,
            @Min(0) @Max(100) Integer maxExamples,
            Set<String> sensitiveWords
        ) {
            public Summary {
                enabled = enabled != null ? enabled : true;
                maxSize = maxSize != null ? maxSize : 100;
                maxExamples = maxExamples != null ? maxExamples : 10;
                sensitiveWords = sensitiveWords != null ? sensitiveWords : Set.of("password", "token", "secret", "key", "credential");
            }
        }
    }
    
    /**
     * 上下文配置
     * 映射自 ContextManagerProperties + ThreadLocalManagerProperties
     */
    public record Context(
        @Min(1000) Long maxAgeMillis,
        Boolean leakDetectionEnabled,
        @Min(1000) Long leakDetectionIntervalMillis,
        Boolean cleanupEnabled,
        @Min(1000) Long cleanupIntervalMillis
    ) {
        public Context {
            maxAgeMillis = maxAgeMillis != null ? maxAgeMillis : 3600000L;
            leakDetectionEnabled = leakDetectionEnabled != null ? leakDetectionEnabled : false;
            leakDetectionIntervalMillis = leakDetectionIntervalMillis != null ? leakDetectionIntervalMillis : 60000L;
            cleanupEnabled = cleanupEnabled != null ? cleanupEnabled : false;
            cleanupIntervalMillis = cleanupIntervalMillis != null ? cleanupIntervalMillis : 60000L;
        }
    }
    
    /**
     * 指标配置
     */
    public record Metrics(
        Boolean enabled,
        Map<String, String> tags,
        String exportInterval
    ) {
        public Metrics {
            enabled = enabled != null ? enabled : true;
            tags = tags != null ? tags : Map.of();
            exportInterval = exportInterval != null ? exportInterval : "PT1M";
        }
    }
    
    /**
     * 安全配置
     */
    public record Security(
        Boolean enableDataMasking,
        Set<String> sensitiveFields
    ) {
        public Security {
            enableDataMasking = enableDataMasking != null ? enableDataMasking : true;
            sensitiveFields = sensitiveFields != null ? sensitiveFields : Set.of("password", "secret", "token", "key", "credential");
        }
    }
    
    /**
     * 验证配置有效性
     */
    public boolean isValid() {
        return enabled != null &&
               changeTracking != null &&
               context != null &&
               metrics != null &&
               security != null &&
               changeTracking.valueReprMaxLength() > 0 &&
               changeTracking.valueReprMaxLength() <= 1_000_000 &&
               changeTracking.cleanupIntervalMinutes() >= 0 &&
               changeTracking.snapshot().maxDepth() > 0 &&
               changeTracking.snapshot().maxDepth() <= 100 &&
               changeTracking.snapshot().maxElements() >= 0 &&
               changeTracking.diff().maxChangesPerObject() > 0 &&
               context.maxAgeMillis() >= 1000 &&
               context.leakDetectionIntervalMillis() >= 1000 &&
               context.cleanupIntervalMillis() >= 1000;
    }
}