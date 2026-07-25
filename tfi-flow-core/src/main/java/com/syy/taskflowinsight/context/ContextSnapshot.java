package com.syy.taskflowinsight.context;

import java.util.Objects;

/**
 * 跨线程传递 Context 链接信息的不可变快照。
 *
 * <p>快照只携带捕获源 identity 与父链元数据，不持有 Session、TaskNode 或 ThreadLocal 引用，
 * 因此 consumer 只能创建独立 linked child，不能跨线程共享可变任务树。</p>
 *
 * <p>public restore 保留破坏式兼容语义；框架内部临时传播由
 * {@link ContextScope} 恢复 worker prior。</p>
 *
 * @since 3.0.0
 * @see ManagedThreadContext#createSnapshot()
 */
public final class ContextSnapshot {
    
    private final String contextId;
    private final String sessionId;
    private final String taskPath;
    private final long timestamp;
    
    /**
     * 创建上下文快照
     * 
     * @param contextId 上下文ID
     * @param sessionId 会话ID（可为null）
     * @param taskPath 任务路径（可为null）
     * @param timestamp {@link System#nanoTime()} 时间戳，只用于同一 JVM 内计算年龄
     */
    public ContextSnapshot(String contextId, String sessionId, String taskPath, long timestamp) {
        this.contextId = Objects.requireNonNull(contextId, "contextId cannot be null");
        this.sessionId = sessionId;
        this.taskPath = taskPath;
        this.timestamp = timestamp;
    }
    
    /**
     * 在目标线程破坏式恢复快照。
     *
     * <p>该 public API 保留“替换当前 Context”的既有语义；需要临时传播并恢复 worker
     * 原绑定的框架代码必须使用 package-private {@link ContextScope}，
     * 避免把调用者 Session 当作 replacement 关闭。</p>
     *
     * @return 新建并绑定的 linked-child Context
     */
    public ManagedThreadContext restore() {
        return SafeContextManager.getInstance().restoreDestructively(this);
    }

    /**
     * 判断当前 Context 是否就是本快照的捕获源。
     *
     * <p>只比较全局唯一 context identity；Session/task 元数据可能在捕获后继续变化，
     * 不能用来决定 CallerRuns 是否可安全复用当前可变 Context。</p>
     */
    boolean matches(ManagedThreadContext context) {
        return context != null && !context.isClosed() && contextId.equals(context.getContextId());
    }
    
    /**
     * 判断快照是否包含会话信息
     * 
     * @return true如果有会话ID
     */
    public boolean hasSession() {
        return sessionId != null;
    }
    
    /**
     * 判断快照是否包含任务信息
     * 
     * @return true如果有任务路径
     */
    public boolean hasTask() {
        return taskPath != null;
    }
    
    /**
     * 获取快照年龄（纳秒）
     * 
     * @return 从创建到现在的纳秒数
     */
    public long getAgeNanos() {
        return System.nanoTime() - timestamp;
    }
    
    /**
     * 获取快照年龄（毫秒）
     * 
     * @return 从创建到现在的毫秒数
     */
    public long getAgeMillis() {
        return getAgeNanos() / 1_000_000;
    }
    
    /**
     * 返回捕获源 Context identity；linked child 将其记录为 parent.contextId。
     *
     * @return 非 null 的捕获源 Context ID
     */
    public String getContextId() {
        return contextId;
    }

    /**
     * 返回捕获时的 Session identity，不携带 Session 对象所有权。
     *
     * @return 父 Session ID；捕获源没有 Session 时为 null
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 返回捕获时的任务路径，仅作为 child 可观测父链。
     *
     * @return 父任务路径；捕获时没有任务时为 null
     */
    public String getTaskPath() {
        return taskPath;
    }

    /**
     * 返回单调时钟采样值；该值不是 epoch 时间，不得序列化为业务时间。
     *
     * @return 创建快照时的 {@link System#nanoTime()} 值
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        ContextSnapshot that = (ContextSnapshot) o;
        return timestamp == that.timestamp &&
               contextId.equals(that.contextId) &&
               Objects.equals(sessionId, that.sessionId) &&
               Objects.equals(taskPath, that.taskPath);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(contextId, sessionId, taskPath, timestamp);
    }
    
    @Override
    public String toString() {
        return String.format("ContextSnapshot{contextId='%s', sessionId='%s', taskPath='%s', age=%dms}",
                contextId, sessionId, taskPath, getAgeMillis());
    }
}
