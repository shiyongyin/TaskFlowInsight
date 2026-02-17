package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.SessionStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话管理类
 * 管理任务执行会话的生命周期，每个会话包含一个根任务节点
 * 
 * <p>设计原则：
 * <ul>
 *   <li>静态工厂 - 通过静态方法创建实例</li>
 *   <li>线程检测 - 自动检测线程ID并管理线程本地会话</li>
 *   <li>生命周期 - 管理从创建到终止的完整生命周期</li>
 *   <li>线程安全 - 使用原子操作和并发集合</li>
 *   <li>状态管理 - 支持RUNNING → COMPLETED/ERROR状态转换</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class Session {
    
    private static final ConcurrentHashMap<String, Session> THREAD_SESSIONS = new ConcurrentHashMap<>();
    
    private final String sessionId;
    private final String threadId;
    private final String threadName;
    private final long createdMillis;
    private final long createdNanos;
    private final TaskNode rootTask;
    
    private final AtomicReference<SessionStatus> status;
    private volatile Long completedMillis;
    private volatile Long completedNanos;
    
    /**
     * 私有构造函数，强制使用工厂方法创建实例
     * 
     * @param rootTaskName 根任务名称
     */
    private Session(String rootTaskName) {
        this.sessionId = UUID.randomUUID().toString();
        
        Thread currentThread = Thread.currentThread();
        this.threadId = String.valueOf(currentThread.threadId());
        this.threadName = currentThread.getName();
        
        Instant now = Instant.now();
        this.createdMillis = now.toEpochMilli();
        this.createdNanos = System.nanoTime();
        
        this.rootTask = new TaskNode(rootTaskName);
        this.status = new AtomicReference<>(SessionStatus.RUNNING);
    }
    
    /**
     * 创建新的会话实例
     * 
     * @param rootTaskName 根任务名称
     * @return 新创建的会话实例
     * @throws IllegalArgumentException 如果rootTaskName为null或空字符串
     */
    public static Session create(String rootTaskName) {
        if (rootTaskName == null || rootTaskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Root task name cannot be null or empty");
        }
        return new Session(rootTaskName.trim());
    }
    
    /**
     * 获取当前线程的活跃会话
     * 
     * @return 当前线程的活跃会话，如果不存在则返回null
     */
    public static Session getCurrent() {
        String threadId = String.valueOf(Thread.currentThread().threadId());
        Session session = THREAD_SESSIONS.get(threadId);
        
        // 检查会话是否仍然有效（状态为RUNNING且是同一线程）
        if (session != null && session.isActive() && threadId.equals(session.threadId)) {
            return session;
        }
        
        // 清理无效会话
        if (session != null) {
            THREAD_SESSIONS.remove(threadId, session);
        }
        
        return null;
    }
    
    /**
     * 将当前会话设置为线程本地活跃会话
     * 
     * @return 当前会话实例
     * @throws IllegalStateException 如果会话不在RUNNING状态
     */
    public synchronized Session activate() {
        if (!isActive()) {
            throw new IllegalStateException(
                "Cannot activate session that is not running. Current status: " + status.get());
        }
        
        // 清理可能存在的旧会话
        Session oldSession = THREAD_SESSIONS.get(threadId);
        if (oldSession != null && oldSession != this) {
            THREAD_SESSIONS.remove(threadId, oldSession);
        }
        
        THREAD_SESSIONS.put(threadId, this);
        return this;
    }
    
    /**
     * 取消激活当前会话
     * 
     * @return 当前会话实例
     */
    public synchronized Session deactivate() {
        THREAD_SESSIONS.remove(threadId, this);
        return this;
    }
    
    /**
     * 正常完成会话
     * 
     * @throws IllegalStateException 如果会话不在RUNNING状态
     */
    public synchronized void complete() {
        if (!status.compareAndSet(SessionStatus.RUNNING, SessionStatus.COMPLETED)) {
            throw new IllegalStateException(
                "Cannot complete session that is not running. Current status: " + status.get());
        }
        
        // 完成根任务（如果还在运行）
        if (rootTask.getStatus().isActive()) {
            rootTask.complete();
        }
        
        markCompleted();
        deactivate();
    }
    
    /**
     * 异常终止会话
     * 
     * @throws IllegalStateException 如果会话不在RUNNING状态
     */
    public synchronized void error() {
        if (!status.compareAndSet(SessionStatus.RUNNING, SessionStatus.ERROR)) {
            throw new IllegalStateException(
                "Cannot error session that is not running. Current status: " + status.get());
        }
        
        // 失败根任务（如果还在运行）
        if (rootTask.getStatus().isActive()) {
            rootTask.fail();
        }
        
        markCompleted();
        deactivate();
    }
    
    /**
     * 异常终止会话并添加错误消息
     * 
     * @param errorContent 错误消息内容
     * @throws IllegalStateException 如果会话不在RUNNING状态
     * @throws IllegalArgumentException 如果errorContent为null或空字符串
     */
    public synchronized void error(String errorContent) {
        // 先添加错误消息到根任务
        rootTask.addError(errorContent);
        error();
    }
    
    /**
     * 根据异常终止会话
     * 
     * @param throwable 异常对象
     * @throws IllegalStateException 如果会话不在RUNNING状态
     * @throws IllegalArgumentException 如果throwable为null
     */
    public synchronized void error(Throwable throwable) {
        // 先添加错误消息到根任务
        rootTask.addError(throwable);
        error();
    }
    
    /**
     * 标记完成时间
     */
    private void markCompleted() {
        Instant now = Instant.now();
        this.completedMillis = now.toEpochMilli();
        this.completedNanos = System.nanoTime();
    }
    
    /**
     * 获取会话唯一标识
     * 
     * @return 会话ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 获取线程ID
     * 
     * @return 线程ID
     */
    public String getThreadId() {
        return threadId;
    }
    
    /**
     * 获取线程名称
     * 
     * @return 线程名称
     */
    public String getThreadName() {
        return threadName;
    }
    
    /**
     * 获取创建时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳
     */
    public long getCreatedMillis() {
        return createdMillis;
    }
    
    /**
     * 获取创建时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳
     */
    public long getCreatedNanos() {
        return createdNanos;
    }
    
    /**
     * 获取根任务节点
     * 
     * @return 根任务节点
     */
    public TaskNode getRootTask() {
        return rootTask;
    }
    
    /**
     * 获取当前会话状态
     * 
     * @return 会话状态
     */
    public SessionStatus getStatus() {
        return status.get();
    }
    
    /**
     * 获取完成时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳，如果会话未完成则返回null
     */
    public Long getCompletedMillis() {
        return completedMillis;
    }
    
    /**
     * 获取完成时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳，如果会话未完成则返回null
     */
    public Long getCompletedNanos() {
        return completedNanos;
    }
    
    /**
     * 获取会话持续时长（毫秒）
     * 
     * @return 持续时长，如果会话未完成则返回null
     */
    public Long getDurationMillis() {
        if (completedMillis == null) {
            return null;
        }
        return completedMillis - createdMillis;
    }
    
    /**
     * 获取会话持续时长（纳秒）
     * 
     * @return 持续时长，如果会话未完成则返回null
     */
    public Long getDurationNanos() {
        if (completedNanos == null) {
            return null;
        }
        return completedNanos - createdNanos;
    }
    
    /**
     * 判断会话是否处于活跃状态
     * 
     * @return true 如果会话处于RUNNING状态
     */
    public boolean isActive() {
        return status.get().isActive();
    }
    
    /**
     * 判断会话是否已经终止
     * 
     * @return true 如果会话已经终止
     */
    public boolean isTerminated() {
        return status.get().isTerminated();
    }
    
    /**
     * 判断会话是否正常完成
     * 
     * @return true 如果会话正常完成
     */
    public boolean isCompleted() {
        return status.get().isCompleted();
    }
    
    /**
     * 判断会话是否异常结束
     * 
     * @return true 如果会话异常结束
     */
    public boolean isError() {
        return status.get().isError();
    }
    
    /**
     * 获取当前活跃会话数量
     * 
     * @return 活跃会话数量
     */
    public static int getActiveSessionCount() {
        return THREAD_SESSIONS.size();
    }
    
    /**
     * 清理所有非活跃会话
     * 
     * @return 清理的会话数量
     */
    public static int cleanupInactiveSessions() {
        int cleaned = 0;
        for (var iterator = THREAD_SESSIONS.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            Session session = entry.getValue();
            
            if (!session.isActive()) {
                iterator.remove();
                cleaned++;
            }
        }
        return cleaned;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        Session session = (Session) obj;
        return Objects.equals(sessionId, session.sessionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
    
    @Override
    public String toString() {
        return String.format("Session{id='%s', thread='%s', status=%s}", 
                sessionId, threadName, status.get());
    }
}