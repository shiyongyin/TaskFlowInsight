package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.time.Instant;

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

    /**
     * 创建无状态的线程上下文门面。
     *
     * <p>保留公共构造器仅用于兼容既有调用；所有能力均由静态方法提供。</p>
     */
    public ThreadContext() {
    }
    
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
     * 获取当前活跃会话
     * 
     * @return 当前活跃会话，如果不存在或已发布终态则返回null
     */
    public static Session currentSession() {
        ManagedThreadContext context = current();
        Session session = context != null ? context.getCurrentSession() : null;
        return session != null && session.isActive() ? session : null;
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
            context.forceCleanup("explicit clear");
        }

        // 注意：变更追踪清理由 tfi-compare 模块负责（如存在）
    }
    
    /**
     * 以破坏式兼容语义传播快照。
     *
     * <p>null 保持“不改变当前绑定并返回 null”；non-null 与
     * {@link ContextSnapshot#restore()} 共用唯一 manager 路径。线程池 wrapper 使用
     * {@link ContextScope}，因此不会借此入口覆盖 worker prior。</p>
     *
     * @param snapshot 上下文快照；null 表示不传播
     * @return 恢复的 linked-child Context；snapshot 为 null 时返回 null
     */
    public static ManagedThreadContext propagate(ContextSnapshot snapshot) {
        return SafeContextManager.getInstance().restoreDestructively(snapshot);
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
        ManagedThreadContext context = create(taskName);
        try {
            return task.execute(context);
        } catch (Exception | Error failure) {
            failPreservingPrimary(context, failure);
            throw failure;
        } finally {
            context.close();
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
        ManagedThreadContext context = create(taskName);
        try {
            task.run(context);
        } catch (Exception | Error failure) {
            failPreservingPrimary(context, failure);
            throw failure;
        } finally {
            context.close();
        }
    }

    private static void failPreservingPrimary(ManagedThreadContext context, Throwable primary) {
        try {
            context.fail(primary);
        } catch (RuntimeException | Error cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
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
        return SafeContextManager.getInstance().metrics().propagations();
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
        ContextMetrics metrics = SafeContextManager.getInstance().metrics();
        return new ContextStatistics(
            metrics.activeContexts(),
            metrics.createdContexts(),
            metrics.propagations(),
            metrics.detectedLeaks() > 0,
            metrics.capturedAt()
        );
    }
    
    /**
     * 在指定上下文内计算结果的任务。
     *
     * @param <T> 任务结果类型
     */
    @FunctionalInterface
    public interface ContextTask<T> {
        /**
         * 在已激活的上下文内执行业务逻辑。
         *
         * @param context 当前调用独占的上下文
         * @return 业务计算结果，可由任务自行定义 null 语义
         * @throws Exception 业务逻辑无法完成时抛出
         */
        T execute(ManagedThreadContext context) throws Exception;
    }
    
    /**
     * 上下文运行接口
     */
    @FunctionalInterface
    public interface ContextRunnable {
        /**
         * 在已激活的上下文内执行业务逻辑。
         *
         * @param context 当前调用独占的上下文
         * @throws Exception 业务逻辑无法完成时抛出
         */
        void run(ManagedThreadContext context) throws Exception;
    }
    
    /**
     * 上下文统计信息
     */
    public static class ContextStatistics {
        /** 捕获时仍处于活动状态的上下文数量。 */
        public final int activeContexts;
        /** 进程启动后累计创建的上下文数量。 */
        public final long totalCreated;
        /** 进程启动后累计执行的上下文传播次数。 */
        public final long totalPropagations;
        /** 捕获前是否至少观测到一次潜在上下文泄漏。 */
        public final boolean potentialLeak;
        /** 统计快照的 wall-clock 捕获时刻。 */
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
            this(activeContexts, totalCreated, totalPropagations, potentialLeak, Instant.now());
        }

        /*
         * Adapter 传入原始观测边界，避免兼容统计对象给同一组数值重新标注时间。
         * 保持 package-private 是为了不把内部快照桥扩张成新的公共构造契约。
         */
        ContextStatistics(int activeContexts, long totalCreated, long totalPropagations,
                          boolean potentialLeak, Instant timestamp) {
            this.activeContexts = activeContexts;
            this.totalCreated = totalCreated;
            this.totalPropagations = totalPropagations;
            this.potentialLeak = potentialLeak;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("ContextStatistics{active=%d, created=%d, propagations=%d, leak=%s}",
                activeContexts, totalCreated, totalPropagations, potentialLeak);
        }
    }
}
