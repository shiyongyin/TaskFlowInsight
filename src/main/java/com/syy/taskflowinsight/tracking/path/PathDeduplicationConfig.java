package com.syy.taskflowinsight.tracking.path;

/**
 * 路径去重配置
 * 统一管理去重功能的所有配置参数
 * 
 * 配置项覆盖：
 * - 功能开关：启用/禁用增强去重
 * - 缓存设置：大小、策略、统计
 * - 裁决策略：权重、tie-break规则
 * - 性能调优：并发、内存限制
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class PathDeduplicationConfig {
    
    // 功能开关
    private boolean enabled = true;
    private boolean cacheEnabled = true;
    private boolean statisticsEnabled = true;
    
    // 缓存配置
    private int maxCacheSize = 10000;
    private String cacheEvictionPolicy = "LRU";  // LRU, FIFO, SIZE_BASED
    private boolean weakReferences = true;
    
    // 裁决策略配置
    private int depthWeight = 1000;           // 深度权重（重要但不压倒性）
    private int accessTypeWeight = 10000;     // 访问类型权重（最高优先级）
    private int lexicalWeight = 1;            // 字典序权重（仅作为次级tie-break）
    private int stableIdWeight = 1;           // 稳定ID权重（最终tie-break）
    
    // 性能调优
    private int maxCollectionDepth = 10;     // 对象图遍历最大深度
    private int maxObjectsPerLevel = 1000;   // 每层最大对象数
    private boolean parallelProcessing = false; // 是否启用并行处理
    
    // 统计与监控
    private boolean detailedStatistics = false;
    private int statisticsReportInterval = 1000; // 统计报告间隔（操作次数）
    // 性能优化开关
    private boolean cachePrewarmEnabled = false; // 预热缓存默认关闭，优先性能
    
    // 候选治理：限制每个目标对象参与裁决的最大候选数，防止组合爆炸
    private int maxCandidates = 5; // 默认为5，符合CARD-CT-004要求
    // 快速路径阈值：当变更条目超过该值时跳过对象图收集（默认 800）
    private int fastPathChangeLimit = 800;

    // 路径过滤规则
    private java.util.List<String> includePatterns = java.util.Collections.emptyList();
    private java.util.List<String> excludePatterns = java.util.Collections.emptyList();
    
    /**
     * 默认配置构造器
     */
    public PathDeduplicationConfig() {
    }
    
    /**
     * 复制构造器
     */
    public PathDeduplicationConfig(PathDeduplicationConfig other) {
        this.enabled = other.enabled;
        this.cacheEnabled = other.cacheEnabled;
        this.statisticsEnabled = other.statisticsEnabled;
        this.maxCacheSize = other.maxCacheSize;
        this.cacheEvictionPolicy = other.cacheEvictionPolicy;
        this.weakReferences = other.weakReferences;
        this.depthWeight = other.depthWeight;
        this.accessTypeWeight = other.accessTypeWeight;
        this.lexicalWeight = other.lexicalWeight;
        this.stableIdWeight = other.stableIdWeight;
        this.maxCollectionDepth = other.maxCollectionDepth;
        this.maxObjectsPerLevel = other.maxObjectsPerLevel;
        this.parallelProcessing = other.parallelProcessing;
        this.detailedStatistics = other.detailedStatistics;
        this.statisticsReportInterval = other.statisticsReportInterval;
        this.maxCandidates = other.maxCandidates;
    }

    /**
     * 从系统属性/环境变量应用覆盖（轻量可选）。
     * 支持的键：
     * - tfi.path.dedup.enabled (boolean)
     * - tfi.path.cache.enabled (boolean)
     * - tfi.path.cache.maxSize (int)
     * - tfi.path.cache.policy (String: LRU/FIFO/SIZE_BASED)
     * - tfi.path.dedup.maxDepth (int)
     * - tfi.path.dedup.parallel (boolean)
     * - tfi.path.dedup.statistics (boolean)
     * - tfi.path.cache.prewarm (boolean)
     * - tfi.path.dedup.maxCandidates (int)
     */
    public void applySystemOverrides() {
        try {
            // boolean helpers
            java.util.function.Function<String, Boolean> b = k -> {
                String v = System.getProperty(k);
                if (v == null) v = System.getenv(k.toUpperCase().replace('.', '_'));
                return v != null ? Boolean.parseBoolean(v) : null;
            };
            // int helpers
            java.util.function.Function<String, Integer> i = k -> {
                String v = System.getProperty(k);
                if (v == null) v = System.getenv(k.toUpperCase().replace('.', '_'));
                if (v == null) return null;
                try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
            };
            // string helper
            java.util.function.Function<String, String> s = k -> {
                String v = System.getProperty(k);
                if (v == null) v = System.getenv(k.toUpperCase().replace('.', '_'));
                return v;
            };

            Boolean dedupEnabled = b.apply("tfi.path.dedup.enabled");
            if (dedupEnabled != null) setEnabled(dedupEnabled);

            Boolean cacheOn = b.apply("tfi.path.cache.enabled");
            if (cacheOn != null) setCacheEnabled(cacheOn);

            Integer cacheSize = i.apply("tfi.path.cache.maxSize");
            if (cacheSize != null && cacheSize >= 0) setMaxCacheSize(cacheSize);

            String policy = s.apply("tfi.path.cache.policy");
            if (policy != null && !policy.isBlank()) setCacheEvictionPolicy(policy.trim());

            Integer maxDepth = i.apply("tfi.path.dedup.maxDepth");
            if (maxDepth != null && maxDepth >= 1) setMaxDepth(maxDepth);

            Boolean parallel = b.apply("tfi.path.dedup.parallel");
            if (parallel != null) setParallelProcessing(parallel);

            Boolean stats = b.apply("tfi.path.dedup.statistics");
            if (stats != null) setDetailedStatistics(stats);

            Boolean prewarm = b.apply("tfi.path.cache.prewarm");
            if (prewarm != null) setCachePrewarmEnabled(prewarm);

            Integer mc = i.apply("tfi.path.dedup.maxCandidates");
            if (mc != null && mc >= 1) setMaxCandidates(mc);

        } catch (Throwable ignore) {
            // 覆盖失败不影响主流程
        }
    }
    
    /**
     * 创建高性能配置（适用于大型对象）
     */
    public static PathDeduplicationConfig forHighPerformance() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        config.maxCacheSize = 50000;
        config.parallelProcessing = true;
        config.detailedStatistics = false;
        config.maxCollectionDepth = 5; // 限制深度提升性能
        return config;
    }
    
    /**
     * 创建高精度配置（适用于复杂对象图）
     */
    public static PathDeduplicationConfig forHighAccuracy() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        config.maxCollectionDepth = 20;
        config.maxObjectsPerLevel = 5000;
        config.detailedStatistics = true;
        config.parallelProcessing = false; // 确保一致性
        return config;
    }
    
    /**
     * 创建内存优化配置（适用于内存敏感环境）
     */
    public static PathDeduplicationConfig forMemoryOptimized() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        config.maxCacheSize = 1000;
        config.weakReferences = true;
        config.cacheEvictionPolicy = "SIZE_BASED";
        config.maxObjectsPerLevel = 100;
        return config;
    }
    
    /**
     * 验证配置有效性
     */
    public void validate() {
        if (maxCacheSize < 0) {
            throw new IllegalArgumentException("maxCacheSize must be >= 0");
        }
        if (maxCollectionDepth < 1) {
            throw new IllegalArgumentException("maxCollectionDepth must be >= 1");
        }
        if (maxObjectsPerLevel < 1) {
            throw new IllegalArgumentException("maxObjectsPerLevel must be >= 1");
        }
        if (statisticsReportInterval < 1) {
            throw new IllegalArgumentException("statisticsReportInterval must be >= 1");
        }
        if (!("LRU".equals(cacheEvictionPolicy) || "FIFO".equals(cacheEvictionPolicy) || "SIZE_BASED".equals(cacheEvictionPolicy))) {
            throw new IllegalArgumentException("cacheEvictionPolicy must be one of: LRU, FIFO, SIZE_BASED");
        }
    }
    
    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    
    public boolean isStatisticsEnabled() { return statisticsEnabled; }
    public void setStatisticsEnabled(boolean statisticsEnabled) { this.statisticsEnabled = statisticsEnabled; }
    
    public int getMaxCacheSize() { return maxCacheSize; }
    public void setMaxCacheSize(int maxCacheSize) { this.maxCacheSize = maxCacheSize; }
    
    public String getCacheEvictionPolicy() { return cacheEvictionPolicy; }
    public void setCacheEvictionPolicy(String cacheEvictionPolicy) { this.cacheEvictionPolicy = cacheEvictionPolicy; }
    
    public boolean isWeakReferences() { return weakReferences; }
    public void setWeakReferences(boolean weakReferences) { this.weakReferences = weakReferences; }
    
    public int getDepthWeight() { return depthWeight; }
    public void setDepthWeight(int depthWeight) { this.depthWeight = depthWeight; }
    
    public int getAccessTypeWeight() { return accessTypeWeight; }
    public void setAccessTypeWeight(int accessTypeWeight) { this.accessTypeWeight = accessTypeWeight; }
    
    public int getLexicalWeight() { return lexicalWeight; }
    public void setLexicalWeight(int lexicalWeight) { this.lexicalWeight = lexicalWeight; }
    
    public int getStableIdWeight() { return stableIdWeight; }
    public void setStableIdWeight(int stableIdWeight) { this.stableIdWeight = stableIdWeight; }
    
    public int getMaxCollectionDepth() { return maxCollectionDepth; }
    public void setMaxCollectionDepth(int maxCollectionDepth) { this.maxCollectionDepth = maxCollectionDepth; }

    // Alias for maxCollectionDepth (convenience method)
    public int getMaxDepth() { return maxCollectionDepth; }
    public void setMaxDepth(int maxDepth) { this.maxCollectionDepth = maxDepth; }
    
    public int getMaxObjectsPerLevel() { return maxObjectsPerLevel; }
    public void setMaxObjectsPerLevel(int maxObjectsPerLevel) { this.maxObjectsPerLevel = maxObjectsPerLevel; }
    
    public boolean isParallelProcessing() { return parallelProcessing; }
    public void setParallelProcessing(boolean parallelProcessing) { this.parallelProcessing = parallelProcessing; }
    
    public boolean isDetailedStatistics() { return detailedStatistics; }
    public void setDetailedStatistics(boolean detailedStatistics) { this.detailedStatistics = detailedStatistics; }
    
    public int getStatisticsReportInterval() { return statisticsReportInterval; }
    public void setStatisticsReportInterval(int statisticsReportInterval) { this.statisticsReportInterval = statisticsReportInterval; }
    public boolean isCachePrewarmEnabled() { return cachePrewarmEnabled; }
    public void setCachePrewarmEnabled(boolean cachePrewarmEnabled) { this.cachePrewarmEnabled = cachePrewarmEnabled; }
    public int getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(int maxCandidates) { this.maxCandidates = Math.max(1, maxCandidates); }
    public int getFastPathChangeLimit() { return fastPathChangeLimit; }
    public void setFastPathChangeLimit(int fastPathChangeLimit) { this.fastPathChangeLimit = Math.max(1, fastPathChangeLimit); }

    // 过滤规则
    public java.util.List<String> getIncludePatterns() { return includePatterns; }
    public void setIncludePatterns(java.util.List<String> includePatterns) { this.includePatterns = includePatterns != null ? includePatterns : java.util.Collections.emptyList(); }
    public java.util.List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(java.util.List<String> excludePatterns) { this.excludePatterns = excludePatterns != null ? excludePatterns : java.util.Collections.emptyList(); }

    /**
     * 判断路径是否匹配过滤规则
     */
    public boolean isPathAllowed(String path) {
        if (path == null) return false;
        // 先检查排除
        for (String pat : excludePatterns) {
            if (pat != null && path.matches(globToRegex(pat))) return false;
        }
        // 再检查包含
        if (includePatterns == null || includePatterns.isEmpty()) return true;
        for (String pat : includePatterns) {
            if (pat != null && path.matches(globToRegex(pat))) return true;
        }
        return false;
    }

    private static String globToRegex(String glob) {
        // 简单的glob -> regex: * -> .*, ? -> .
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append('.'); break;
                case '.': sb.append("\\."); break;
                case '[': sb.append("\\["); break;
                case ']': sb.append("\\]"); break;
                case '(': case ')': case '{': case '}': case '+': case '^': case '$': case '|': case '\\':
                    sb.append('\\').append(c); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("PathDeduplicationConfig{enabled=%s, cache=%s/%d, depth=%d, parallel=%s, maxCandidates=%d}",
            enabled, cacheEnabled, maxCacheSize, maxCollectionDepth, parallelProcessing, maxCandidates);
    }
}
