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

    /** 普通List固定使用ordered-index语义。 */
    public static final String STRATEGY_SIMPLE = "SIMPLE";
    /** 同质Entity列表保留给唯一identity/content配对策略。 */
    public static final String STRATEGY_ENTITY = "ENTITY";
}
