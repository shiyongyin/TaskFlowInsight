package com.syy.taskflowinsight.api;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import com.syy.taskflowinsight.annotation.ObjectType;
import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;

/**
 * 追踪配置选项
 * 提供深度追踪、字段过滤、比较策略等配置能力
 * 
 * @author TaskFlow Insight Team
 * @version 3.1.0
 * @since 2025-01-13
 */
public class TrackingOptions {
    
    public enum TrackingDepth {
        SHALLOW,    // 浅层追踪（仅标量字段）
        DEEP,       // 深度追踪（递归所有字段）
        CUSTOM      // 自定义深度
    }
    
    public enum CollectionStrategy {
        IGNORE,     // 忽略集合变更
        SUMMARY,    // 集合摘要（大小、类型等）
        ELEMENT     // 逐元素比较
    }
    
    public enum CompareStrategy {
        REFLECTION, // 反射比较
        EQUALS,     // equals方法比较
        CUSTOM      // 自定义比较器
    }
    
    private final TrackingDepth depth;
    private final int maxDepth;
    private final CollectionStrategy collectionStrategy;
    private final CompareStrategy compareStrategy;
    private final Set<String> includeFields;
    private final Set<String> excludeFields;
    private final boolean enableCycleDetection;
    private final boolean enablePerformanceMonitoring;
    private final long timeBudgetMs;
    private final int collectionSummaryThreshold;
    private final boolean typeAwareEnabled;
    private final ObjectType forcedObjectType;
    private final ValueObjectCompareStrategy forcedStrategy;
    
    private TrackingOptions(Builder builder) {
        this.depth = builder.depth;
        this.maxDepth = builder.maxDepth;
        this.collectionStrategy = builder.collectionStrategy;
        this.compareStrategy = builder.compareStrategy;
        this.includeFields = Collections.unmodifiableSet(new HashSet<>(builder.includeFields));
        this.excludeFields = Collections.unmodifiableSet(new HashSet<>(builder.excludeFields));
        this.enableCycleDetection = builder.enableCycleDetection;
        this.enablePerformanceMonitoring = builder.enablePerformanceMonitoring;
        this.timeBudgetMs = builder.timeBudgetMs;
        this.collectionSummaryThreshold = builder.collectionSummaryThreshold;
        this.typeAwareEnabled = builder.typeAwareEnabled;
        this.forcedObjectType = builder.forcedObjectType;
        this.forcedStrategy = builder.forcedStrategy;
    }
    
    /**
     * 创建默认的浅层追踪选项
     */
    public static TrackingOptions shallow() {
        return new Builder()
            .depth(TrackingDepth.SHALLOW)
            .maxDepth(1)
            .collectionStrategy(CollectionStrategy.IGNORE)
            .compareStrategy(CompareStrategy.REFLECTION)
            .build();
    }
    
    /**
     * 创建默认的深度追踪选项
     */
    public static TrackingOptions deep() {
        return new Builder()
            .depth(TrackingDepth.DEEP)
            .maxDepth(10)  // 使用ConfigDefaults.MAX_DEPTH统一默认值
            .collectionStrategy(CollectionStrategy.ELEMENT)
            .compareStrategy(CompareStrategy.REFLECTION)
            .enableCycleDetection(true)
            .enablePerformanceMonitoring(true)
            .timeBudgetMs(1000)  // 使用ConfigDefaults.TIME_BUDGET_MS统一默认值
            .collectionSummaryThreshold(100)  // 使用ConfigDefaults.COLLECTION_SUMMARY_THRESHOLD
            .build();
    }
    
    /**
     * 创建自定义追踪选项构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public TrackingDepth getDepth() { return depth; }
    public int getMaxDepth() { return maxDepth; }
    public CollectionStrategy getCollectionStrategy() { return collectionStrategy; }
    public CompareStrategy getCompareStrategy() { return compareStrategy; }
    public Set<String> getIncludeFields() { return includeFields; }
    public Set<String> getExcludeFields() { return excludeFields; }
    public boolean isEnableCycleDetection() { return enableCycleDetection; }
    public boolean isEnablePerformanceMonitoring() { return enablePerformanceMonitoring; }
    public long getTimeBudgetMs() { return timeBudgetMs; }
    public int getCollectionSummaryThreshold() { return collectionSummaryThreshold; }
    public boolean isTypeAwareEnabled() { return typeAwareEnabled; }
    public ObjectType getForcedObjectType() { return forcedObjectType; }
    public ValueObjectCompareStrategy getForcedStrategy() { return forcedStrategy; }
    
    /**
     * 构建器模式
     */
    public static class Builder {
        private TrackingDepth depth = TrackingDepth.SHALLOW;
        private int maxDepth = 3;
        private CollectionStrategy collectionStrategy = CollectionStrategy.SUMMARY;
        private CompareStrategy compareStrategy = CompareStrategy.REFLECTION;
        private Set<String> includeFields = new HashSet<>();
        private Set<String> excludeFields = new HashSet<>();
        private boolean enableCycleDetection = false;
        private boolean enablePerformanceMonitoring = false;
        private long timeBudgetMs = 1000;
        private int collectionSummaryThreshold = 50;
        private boolean typeAwareEnabled = false;
        private ObjectType forcedObjectType = null;
        private ValueObjectCompareStrategy forcedStrategy = null;
        
        public Builder depth(TrackingDepth depth) {
            this.depth = depth;
            return this;
        }
        
        public Builder maxDepth(int maxDepth) {
            this.maxDepth = Math.max(1, maxDepth);
            return this;
        }
        
        public Builder collectionStrategy(CollectionStrategy strategy) {
            this.collectionStrategy = strategy;
            return this;
        }
        
        public Builder compareStrategy(CompareStrategy strategy) {
            this.compareStrategy = strategy;
            return this;
        }
        
        public Builder includeFields(String... fields) {
            Collections.addAll(this.includeFields, fields);
            return this;
        }
        
        public Builder excludeFields(String... fields) {
            Collections.addAll(this.excludeFields, fields);
            return this;
        }
        
        public Builder enableCycleDetection(boolean enable) {
            this.enableCycleDetection = enable;
            return this;
        }
        
        public Builder enablePerformanceMonitoring(boolean enable) {
            this.enablePerformanceMonitoring = enable;
            return this;
        }
        
        public Builder timeBudgetMs(long timeBudgetMs) {
            this.timeBudgetMs = Math.max(100, timeBudgetMs);
            return this;
        }
        
        public Builder collectionSummaryThreshold(int threshold) {
            this.collectionSummaryThreshold = Math.max(1, threshold);
            return this;
        }
        
        public Builder enableTypeAware() {
            this.typeAwareEnabled = true;
            return this;
        }
        
        public Builder enableTypeAware(boolean enable) {
            this.typeAwareEnabled = enable;
            return this;
        }
        
        public Builder forceObjectType(ObjectType objectType) {
            this.forcedObjectType = objectType;
            return this;
        }
        
        public Builder forceStrategy(ValueObjectCompareStrategy strategy) {
            this.forcedStrategy = strategy;
            return this;
        }
        
        public TrackingOptions build() {
            return new TrackingOptions(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("TrackingOptions{depth=%s, maxDepth=%d, collectionStrategy=%s, " +
                           "compareStrategy=%s, includeFields=%s, excludeFields=%s, " +
                           "enableCycleDetection=%s, enablePerformanceMonitoring=%s, " +
                           "timeBudgetMs=%d, collectionSummaryThreshold=%d, " +
                           "typeAwareEnabled=%s, forcedObjectType=%s, forcedStrategy=%s}",
                           depth, maxDepth, collectionStrategy, compareStrategy,
                           includeFields, excludeFields, enableCycleDetection,
                           enablePerformanceMonitoring, timeBudgetMs, collectionSummaryThreshold,
                           typeAwareEnabled, forcedObjectType, forcedStrategy);
    }
}