package com.syy.taskflowinsight.tracking.compare;

/**
 * 比较策略相关常量配置
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public final class CompareConstants {
    
    private CompareConstants() {
        // prevent instantiation
    }
    
    // ========== List比较策略相关 ==========
    
    /**
     * List大小阈值：超过此值触发降级
     * 500: LEVENSHTEIN算法的推荐最大值
     */
    public static final int LIST_SIZE_DEGRADATION_THRESHOLD = 500;
    
    /**
     * List强制降级阈值：>=1000强制使用SIMPLE策略
     */
    public static final int LIST_SIZE_FORCE_SIMPLE_THRESHOLD = 1000;
    
    /**
     * LEVENSHTEIN算法的最大推荐大小
     */
    public static final int LEVENSHTEIN_MAX_RECOMMENDED_SIZE = 500;
    
    /**
     * AS_SET策略的最大推荐大小
     */
    public static final int AS_SET_MAX_RECOMMENDED_SIZE = 10000;

    /**
     * LCS算法的最大推荐大小（M3新增）
     */
    public static final int LCS_MAX_RECOMMENDED_SIZE = 300;
    
    // ========== Map重命名检测相关 ==========
    
    /**
     * Map键重命名相似度阈值
     * 注：原CARD要求≥0.9，但实践中0.7更适合常见重命名场景
     * 例如：userName -> user_name (0.777), userEmail -> user_email (0.8)
     */
    public static final double MAP_KEY_RENAME_SIMILARITY_THRESHOLD = 0.7;
    
    /**
     * Map候选配对数降级阈值
     * K = deletedKeys.size() * addedKeys.size() > 1000时禁用重命名检测
     */
    public static final int MAP_CANDIDATE_PAIRS_DEGRADATION_THRESHOLD = 1000;
    
    // ========== 性能相关 ==========
    
    /**
     * 默认比较超时时间（毫秒）
     */
    public static final long DEFAULT_COMPARISON_TIMEOUT_MS = 5000;
    
    /**
     * 性能测试目标：100元素应在此时间内完成（毫秒）
     */
    public static final long PERFORMANCE_TARGET_100_ELEMENTS_MS = 10;
    
    /**
     * 性能测试目标：500元素应在此时间内完成（毫秒）
     */
    public static final long PERFORMANCE_TARGET_500_ELEMENTS_MS = 100;
    
    /**
     * 性能测试目标：1000元素应在此时间内完成（毫秒）
     */
    public static final long PERFORMANCE_TARGET_1000_ELEMENTS_MS = 200;
    
    // ========== 策略名称 ==========

    public static final String STRATEGY_SIMPLE = "SIMPLE";
    public static final String STRATEGY_LEVENSHTEIN = "LEVENSHTEIN";
    public static final String STRATEGY_AS_SET = "AS_SET";
    public static final String STRATEGY_ENTITY = "ENTITY";
    public static final String STRATEGY_LCS = "LCS"; // M3新增：LCS列表策略
    
    // ========== 降级原因 ==========
    
    public static final String DEGRADATION_REASON_SIZE_EXCEEDED = "size_exceeded";
    public static final String DEGRADATION_REASON_FORCE_SIMPLE = "force_simple";
    public static final String DEGRADATION_REASON_BUSINESS_HINT = "business_hint";
    public static final String DEGRADATION_REASON_MAP_CANDIDATES = "map_candidate_pairs_exceeded";
    
    /**
     * 列表比较：K对数（n1*n2）超过阈值触发的降级原因
     */
    public static final String DEGRADATION_REASON_K_PAIRS_EXCEEDED = "k_pairs_exceeded";
}
