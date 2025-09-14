package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    
    private static final ThreadLocal<ManagedThreadContext> CONTEXT_HOLDER = new ThreadLocal<>();
    private static final AtomicInteger ACTIVE_CONTEXTS = new AtomicInteger(0);
    private static final AtomicLong TOTAL_CONTEXTS_CREATED = new AtomicLong(0);
    private static final AtomicLong TOTAL_PROPAGATIONS = new AtomicLong(0);
    
    // 性能统计 - 使用ConcurrentHashMap确保线程安全
    private static final Map<Long, Long> CONTEXT_DURATIONS = new ConcurrentHashMap<>();
    
    // 并发控制锁 - 用于保护关键操作
    private static final Object CREATION_LOCK = new Object();
    private static final Object CLEANUP_LOCK = new Object();
    
    /**
     * 创建并激活新的线程上下文
     * 
     * @param taskName 任务名称
     * @return 创建的上下文
     */
    public static ManagedThreadContext create(String taskName) {
        synchronized (CREATION_LOCK) {
            // 清理现有上下文
            ManagedThreadContext existing = CONTEXT_HOLDER.get();
            if (existing != null) {
                if (!existing.isClosed()) {
                    existing.close();
                    // 只有在真正关闭时才减少计数
                    ACTIVE_CONTEXTS.decrementAndGet();
                    
                    // 记录持续时间
                    Long startTime = CONTEXT_DURATIONS.remove(existing.getThreadId());
                    if (startTime != null) {
                        long duration = System.nanoTime() - startTime;
                        // 可以记录到指标系统
                    }
                }
                CONTEXT_HOLDER.remove();
            }
            
            // 创建新上下文
            ManagedThreadContext context = ManagedThreadContext.create(taskName);
            CONTEXT_HOLDER.set(context);
            
            // 原子更新统计
            ACTIVE_CONTEXTS.incrementAndGet();
            TOTAL_CONTEXTS_CREATED.incrementAndGet();
            CONTEXT_DURATIONS.put(context.getThreadId(), System.nanoTime());
            
            return context;
        }
    }
    
    /**
     * 获取当前线程的上下文
     * 
     * @return 当前上下文，如果不存在返回null
     */
    public static ManagedThreadContext current() {
        ManagedThreadContext context = CONTEXT_HOLDER.get();
        // 如果ThreadLocal中有已关闭的上下文，清理它
        if (context != null && context.isClosed()) {
            CONTEXT_HOLDER.remove();
            return null;
        }
        return context;
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
        synchronized (CLEANUP_LOCK) {
            ManagedThreadContext context = CONTEXT_HOLDER.get();
            if (context != null) {
                try {
                    if (!context.isClosed()) {
                        context.close();
                        
                        // 原子更新统计
                        int currentActive = ACTIVE_CONTEXTS.decrementAndGet();
                        if (currentActive < 0) {
                            // 防御性编程：修正负数
                            ACTIVE_CONTEXTS.compareAndSet(currentActive, 0);
                        }
                        
                        Long startTime = CONTEXT_DURATIONS.remove(context.getThreadId());
                        if (startTime != null) {
                            long duration = System.nanoTime() - startTime;
                            // 可以记录到日志或指标系统
                        }
                    }
                } finally {
                    CONTEXT_HOLDER.remove();
                }
            } else {
                // 即使没有上下文，也要清理ThreadLocal
                CONTEXT_HOLDER.remove();
            }
            
            // 同时清理ChangeTracker
            com.syy.taskflowinsight.api.TFI.clearAllTracking();
        }
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
        // 设置到CONTEXT_HOLDER，这样ThreadContext.current()才能获取到
        CONTEXT_HOLDER.set(context);
        
        // 更新统计
        ACTIVE_CONTEXTS.incrementAndGet();
        TOTAL_PROPAGATIONS.incrementAndGet();
        CONTEXT_DURATIONS.put(context.getThreadId(), System.nanoTime());
        
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
        return ACTIVE_CONTEXTS.get();
    }
    
    /**
     * 获取总创建上下文数量
     * 
     * @return 总创建数量
     */
    public static long getTotalContextsCreated() {
        return TOTAL_CONTEXTS_CREATED.get();
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
        int activeCount = ACTIVE_CONTEXTS.get();
        int threadCount = Thread.activeCount();
        
        // 如果活跃上下文数量明显大于线程数，可能存在泄漏
        return activeCount > threadCount * 2;
    }
    
    /**
     * 获取性能统计信息
     * 
     * @return 性能统计
     */
    public static ContextStatistics getStatistics() {
        return new ContextStatistics(
            ACTIVE_CONTEXTS.get(),
            TOTAL_CONTEXTS_CREATED.get(),
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