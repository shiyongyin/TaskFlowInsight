package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程上下文统一管理器
 * 提供线程级别的追踪上下文管理，与ChangeTracker集成
 * 
 * 核心功能：
 * - 统一的线程上下文API
 * - 自动清理机制
 * - 内存泄漏检测
 * - 性能统计
 * 
 * @author TaskFlow Insight Team  
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ThreadContext {
    
    private static final AtomicLong TOTAL_PROPAGATIONS = new AtomicLong(0);
    
    /**
     * 创建并激活新的线程上下文
     * 
     * @param taskName 任务名称
     * @return 创建的上下文
     */
    public static ManagedThreadContext create(String taskName) {
        return ManagedThreadContext.create(taskName);
    }
    
    /**
     * 获取当前线程的上下文
     * 
     * @return 当前上下文，如果不存在返回null
     */
    public static ManagedThreadContext current() {
        return ManagedThreadContext.current();
    }
    
    /**
     * 获取当前会话
     * 
     * @return 当前会话，如果不存在返回null
     */
    public static Session currentSession() {
        ManagedThreadContext context = current();
        return context != null ? context.getCurrentSession() : null;
    }
    
    /**
     * 获取当前任务
     * 
     * @return 当前任务，如果不存在返回null
     */
    public static TaskNode currentTask() {
        ManagedThreadContext context = current();
        return context != null ? context.getCurrentTask() : null;
    }
    
    /**
     * 清理当前线程的上下文
     */
    public static void clear() {
        ManagedThreadContext context = ManagedThreadContext.current();
        if (context != null && !context.isClosed()) {
            context.close();
        }

        // 注意：变更追踪清理由 tfi-compare 模块负责（如存在）
    }
    
    /**
     * 传播上下文到新线程
     * 
     * @param snapshot 上下文快照
     * @return 恢复的上下文
     */
    public static ManagedThreadContext propagate(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        
        // 清理现有上下文
        clear();
        
        // 恢复上下文
        ManagedThreadContext context = ManagedThreadContext.restoreFromSnapshot(snapshot);
        
        // 更新统计
        TOTAL_PROPAGATIONS.incrementAndGet();
        
        return context;
    }
    
    /**
     * 执行带上下文的任务
     * 
     * @param taskName 任务名称
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务执行结果
     * @throws Exception 任务执行异常
     */
    public static <T> T execute(String taskName, ContextTask<T> task) throws Exception {
        try (ManagedThreadContext context = create(taskName)) {
            return task.execute(context);
        }
    }
    
    /**
     * 执行带上下文的任务（无返回值）
     * 
     * @param taskName 任务名称
     * @param task 要执行的任务
     * @throws Exception 任务执行异常
     */
    public static void run(String taskName, ContextRunnable task) throws Exception {
        try (ManagedThreadContext context = create(taskName)) {
            task.run(context);
        }
    }
    
    /**
     * 获取活跃上下文数量
     * 
     * @return 活跃上下文数量
     */
    public static int getActiveContextCount() {
        return SafeContextManager.getInstance().getActiveContextCount();
    }
    
    /**
     * 获取总创建上下文数量
     * 
     * @return 总创建数量
     */
    public static long getTotalContextsCreated() {
        return SafeContextManager.getInstance().getContextCreatedCount();
    }
    
    /**
     * 获取总传播次数
     * 
     * @return 总传播次数
     */
    public static long getTotalPropagations() {
        return TOTAL_PROPAGATIONS.get();
    }
    
    /**
     * 检测潜在的内存泄漏
     * 
     * @return 是否存在潜在泄漏
     */
    public static boolean detectPotentialLeaks() {
        SafeContextManager manager = SafeContextManager.getInstance();
        long before = manager.getLeakDetectedCount();
        manager.detectAndCleanLeaks();
        return manager.getLeakDetectedCount() > before;
    }
    
    /**
     * 获取性能统计信息
     * 
     * @return 性能统计
     */
    public static ContextStatistics getStatistics() {
        return new ContextStatistics(
            getActiveContextCount(),
            getTotalContextsCreated(),
            TOTAL_PROPAGATIONS.get(),
            detectPotentialLeaks()
        );
    }
    
    /**
     * 上下文任务接口
     */
    @FunctionalInterface
    public interface ContextTask<T> {
        T execute(ManagedThreadContext context) throws Exception;
    }
    
    /**
     * 上下文运行接口
     */
    @FunctionalInterface
    public interface ContextRunnable {
        void run(ManagedThreadContext context) throws Exception;
    }
    
    /**
     * 上下文统计信息
     */
    public static class ContextStatistics {
        public final int activeContexts;
        public final long totalCreated;
        public final long totalPropagations;
        public final boolean potentialLeak;
        public final Instant timestamp;

        /**
         * 构造上下文统计快照。
         *
         * @param activeContexts 活跃上下文数量
         * @param totalCreated 累计创建数
         * @param totalPropagations 累计传播数
         * @param potentialLeak 是否存在潜在泄漏
         */
        public ContextStatistics(int activeContexts, long totalCreated, 
                                long totalPropagations, boolean potentialLeak) {
            this.activeContexts = activeContexts;
            this.totalCreated = totalCreated;
            this.totalPropagations = totalPropagations;
            this.potentialLeak = potentialLeak;
            this.timestamp = Instant.now();
        }
        
        @Override
        public String toString() {
            return String.format("ContextStatistics{active=%d, created=%d, propagations=%d, leak=%s}",
                activeContexts, totalCreated, totalPropagations, potentialLeak);
        }
    }
}
