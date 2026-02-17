package com.syy.taskflowinsight.context;

import java.util.Objects;

/**
 * 上下文快照
 * 用于跨线程传递上下文信息的不可变快照
 * 
 * 设计原则：
 * - 不可变性：所有字段都是final
 * - 轻量级：仅包含ID和元数据，不包含对象引用
 * - 线程安全：可以安全地在多线程间传递
 * - 避免泄漏：不持有ThreadLocal或可变对象引用
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
     * @param timestamp 快照时间戳（纳秒）
     */
    public ContextSnapshot(String contextId, String sessionId, String taskPath, long timestamp) {
        this.contextId = Objects.requireNonNull(contextId, "contextId cannot be null");
        this.sessionId = sessionId;
        this.taskPath = taskPath;
        this.timestamp = timestamp;
    }
    
    /**
     * 恢复上下文（在目标线程中调用）
     * 创建一个新的上下文实例，关联到快照信息
     * 
     * @return 新的上下文实例
     */
    public ManagedThreadContext restore() {
        return ManagedThreadContext.restoreFromSnapshot(this);
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
    
    // 属性访问方法
    
    public String getContextId() {
        return contextId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getTaskPath() {
        return taskPath;
    }
    
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