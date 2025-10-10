package com.syy.taskflowinsight.tracking.monitoring;

/**
 * 降级上下文（ThreadLocal）
 * 
 * 用于在ChangeTracker中传递当前降级级别，实现最小侵入的集成
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class DegradationContext {
    
    /** 线程本地的降级级别 */
    private static final ThreadLocal<DegradationLevel> CURRENT_LEVEL = 
        ThreadLocal.withInitial(() -> DegradationLevel.FULL_TRACKING);
    
    /** 线程本地的性能监控器引用 */
    private static final ThreadLocal<DegradationPerformanceMonitor> PERFORMANCE_MONITOR = 
        new ThreadLocal<>();
    
    private DegradationContext() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 获取当前线程的降级级别
     */
    public static DegradationLevel getCurrentLevel() {
        return CURRENT_LEVEL.get();
    }
    
    /**
     * 设置当前线程的降级级别
     */
    public static void setCurrentLevel(DegradationLevel level) {
        if (level != null) {
            CURRENT_LEVEL.set(level);
        }
    }
    
    /**
     * 重置为默认级别
     */
    public static void reset() {
        CURRENT_LEVEL.set(DegradationLevel.FULL_TRACKING);
        PERFORMANCE_MONITOR.remove();
    }
    
    /**
     * 清理线程本地变量
     */
    public static void clear() {
        CURRENT_LEVEL.remove();
        PERFORMANCE_MONITOR.remove();
    }
    
    /**
     * 设置性能监控器（用于记录操作时长）
     */
    public static void setPerformanceMonitor(DegradationPerformanceMonitor monitor) {
        PERFORMANCE_MONITOR.set(monitor);
    }
    
    /**
     * 获取性能监控器
     */
    public static DegradationPerformanceMonitor getPerformanceMonitor() {
        return PERFORMANCE_MONITOR.get();
    }
    
    /**
     * 记录操作性能（如果性能监控器存在）
     */
    public static void recordOperationIfEnabled(String operationType, long startTimeNanos) {
        DegradationPerformanceMonitor monitor = PERFORMANCE_MONITOR.get();
        if (monitor != null) {
            long durationNanos = System.nanoTime() - startTimeNanos;
            monitor.recordOperation(java.time.Duration.ofNanos(durationNanos));
        }
    }
    
    /**
     * 检查当前级别是否允许深度分析
     */
    public static boolean allowsDeepAnalysis() {
        return getCurrentLevel().allowsDeepAnalysis();
    }
    
    /**
     * 检查当前级别是否允许移动检测
     */
    public static boolean allowsMoveDetection() {
        return getCurrentLevel().allowsMoveDetection();
    }
    
    /**
     * 检查当前级别是否允许路径优化
     */
    public static boolean allowsPathOptimization() {
        return getCurrentLevel().allowsPathOptimization();
    }
    
    /**
     * 检查当前级别是否只允许摘要信息
     */
    public static boolean onlySummaryInfo() {
        return getCurrentLevel().onlySummaryInfo();
    }
    
    /**
     * 检查当前级别是否完全禁用
     */
    public static boolean isDisabled() {
        return getCurrentLevel().isDisabled();
    }
    
    /**
     * 获取当前级别的最大深度
     */
    public static int getMaxDepth() {
        return getCurrentLevel().getMaxDepth();
    }
    
    /**
     * 获取当前级别的最大元素数
     */
    public static int getMaxElements() {
        return getCurrentLevel().getMaxElements();
    }
    
    /**
     * 检查集合大小是否超过当前级别限制
     */
    public static boolean exceedsElementLimit(int size) {
        int maxElements = getMaxElements();
        return maxElements > 0 && size > maxElements;
    }
    
    /**
     * 获取调整后的集合大小（不超过限制）
     */
    public static int getAdjustedSize(int originalSize) {
        int maxElements = getMaxElements();
        if (maxElements <= 0) {
            return originalSize;
        }
        return Math.min(originalSize, maxElements);
    }
}