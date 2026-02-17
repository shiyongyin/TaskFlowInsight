package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程上下文管理器
 * 提供线程级别的任务栈和会话管理，支持嵌套任务和上下文快照
 * 
 * 设计原则：
 * - 强制资源管理：必须通过try-with-resources使用
 * - 线程封闭：每个线程独立的上下文实例
 * - 任务栈管理：支持任务嵌套和自动清理
 * - 快照传播：支持跨线程的上下文传递
 * - 零泄漏设计：自动清理未关闭的资源
 */
public class ManagedThreadContext implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(ManagedThreadContext.class);
    
    private final String contextId;
    private final long threadId;
    @Getter
    private final String threadName;
    private final long createdNanos;
    private final Deque<TaskNode> taskStack;
    // 手动声明了 getCurrentSession()，不再由 Lombok 生成，以避免重复方法警告
    private Session currentSession;
    private volatile boolean closed = false;

    /**
     * 显式提供布尔型getter以符合调用方期望
     * 一些代码使用了 isClosed() 语义，这里确保可用
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * 获取线程ID
     * @return 线程ID
     */
    public long getThreadId() {
        return threadId;
    }
    
    /**
     * 获取上下文ID
     * @return 上下文ID
     */
    public String getContextId() {
        return contextId;
    }
    
    /**
     * 获取创建时间纳秒数
     * @return 创建时间纳秒数
     */
    public long getCreatedNanos() {
        return createdNanos;
    }
    
    // 元数据存储
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    /**
     * 私有构造函数，通过静态工厂方法创建
     */
    private ManagedThreadContext() {
        this.contextId = UUID.randomUUID().toString();
        Thread current = Thread.currentThread();
        this.threadId = current.threadId();
        this.threadName = current.getName();
        this.createdNanos = System.nanoTime();
        this.taskStack = new ArrayDeque<>();
    }
    
    /**
     * 创建并激活新的线程上下文
     * 
     * @param rootTaskName 根任务名称
     * @return 新创建的上下文实例
     */
    public static ManagedThreadContext create(String rootTaskName) {
        ManagedThreadContext context = new ManagedThreadContext();
        
        // 注册到SafeContextManager
        SafeContextManager manager = SafeContextManager.getInstance();
        ManagedThreadContext existing = manager.getCurrentContext();
        if (existing != null && !existing.closed) {
            logger.debug("Creating new context while existing context is active. "
                + "Existing context will be closed: {}", existing.contextId);
            existing.close();
        }
        
        // 创建会话
        context.startSession(rootTaskName);
        
        // 注册新上下文
        manager.registerContext(context);
        
        return context;
    }
    
    /**
     * 获取当前线程的活动上下文
     * 
     * @return 当前线程的上下文，如果不存在返回null
     */
    public static ManagedThreadContext current() {
        return SafeContextManager.getInstance().getCurrentContext();
    }
    
    /**
     * 开始新会话
     * MVP阶段每线程仅支持单会话
     * 
     * @param rootTaskName 根任务名称
     * @return 创建的会话
     */
    public Session startSession(String rootTaskName) {
        checkNotClosed();
        
        if (currentSession != null && currentSession.isActive()) {
            throw new IllegalStateException("Session already active: " + currentSession.getSessionId());
        }
        
        // 使用现有Session API
        Session session = Session.create(rootTaskName);
        session.activate();
        this.currentSession = session;
        
        // 根任务自动入栈
        this.taskStack.push(session.getRootTask());
        
        return session;
    }
    
    /**
     * 结束当前会话
     */
    public void endSession() {
        checkNotClosed();
        
        if (currentSession == null) {
            return;
        }
        
        // 清理未完成的任务
        while (!taskStack.isEmpty()) {
            TaskNode task = taskStack.peek();
            if (task.getStatus().isActive()) {
                endTask();
            } else {
                taskStack.pop();
            }
        }
        
        // 完成会话
        if (currentSession.isActive()) {
            currentSession.complete();
        }
        
        currentSession = null;
    }
    
    /**
     * 开始新任务
     * 
     * @param taskName 任务名称
     * @return 创建的任务节点
     */
    public TaskNode startTask(String taskName) {
        checkNotClosed();
        
        if (currentSession == null) {
            throw new IllegalStateException("No active session");
        }
        
        TaskNode parent = taskStack.peek();
        if (parent == null) {
            throw new IllegalStateException("No parent task available");
        }
        
        // 创建子任务
        TaskNode task = new TaskNode(parent, taskName);
        taskStack.push(task);
        
        return task;
    }
    
    /**
     * 结束当前任务
     * 
     * @return 结束的任务节点
     */
    public TaskNode endTask() {
        checkNotClosed();
        
        if (taskStack.isEmpty()) {
            throw new IllegalStateException("No active task to end");
        }
        
        TaskNode task = taskStack.pop();
        
        // 自动完成活动任务
        if (task.getStatus().isActive()) {
            task.complete();
        }
        
        return task;
    }
    
    /**
     * 创建上下文快照用于跨线程传播
     * 仅包含ID和不可变元数据，不包含对象引用
     * 
     * @return 上下文快照
     */
    public ContextSnapshot createSnapshot() {
        checkNotClosed();
        
        String sessionId = currentSession != null ? currentSession.getSessionId() : null;
        String taskPath = !taskStack.isEmpty() ? taskStack.peek().getTaskPath() : null;
        
        return new ContextSnapshot(contextId, sessionId, taskPath, System.nanoTime());
    }
    
    /**
     * 从快照恢复上下文（在新线程中）
     * 
     * @param snapshot 上下文快照
     * @return 恢复的上下文
     */
    public static ManagedThreadContext restoreFromSnapshot(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        
        // 创建新的上下文实例（不同线程）
        ManagedThreadContext context = new ManagedThreadContext();
        
        // 记录快照信息但不共享对象（处理null值）
        context.attributes.put("parent.contextId", snapshot.getContextId());
        if (snapshot.getSessionId() != null) {
            context.attributes.put("parent.sessionId", snapshot.getSessionId());
        }
        if (snapshot.getTaskPath() != null) {
            context.attributes.put("parent.taskPath", snapshot.getTaskPath());
        }
        context.attributes.put("parent.timestamp", snapshot.getTimestamp());
        
        // 如果有会话信息，创建一个新的会话（避免跨线程共享）
        if (snapshot.hasSession()) {
            String rootTaskName = extractRootTaskName(snapshot.getTaskPath());
            context.startSession(rootTaskName);
        }
        
        // 注册到管理器
        SafeContextManager.getInstance().registerContext(context);
        
        return context;
    }
    
    /**
     * 从任务路径中提取根任务名称
     */
    private static String extractRootTaskName(String taskPath) {
        if (taskPath == null || taskPath.trim().isEmpty()) {
            return "restored-task";
        }
        
        String[] parts = taskPath.split("/");
        return parts.length > 0 ? parts[0] : "restored-task";
    }
    
    /**
     * 获取当前任务栈深度
     * 
     * @return 任务栈深度
     */
    public int getTaskDepth() {
        return taskStack.size();
    }
    
    /**
     * 获取当前任务
     * 
     * @return 当前任务节点，如果栈为空返回null
     */
    public TaskNode getCurrentTask() {
        return taskStack.peek();
    }

    /**
     * 获取当前会话
     * 
     * @return 当前会话，如果没有返回null
     */
    public Session getCurrentSession() {
        return currentSession;
    }

    /**
     * 设置上下文属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        checkNotClosed();
        attributes.put(key, value);
    }
    
    /**
     * 获取上下文属性
     * 
     * @param key 属性键
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    /**
     * 检查上下文是否已关闭
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Context already closed: " + contextId);
        }
    }
    
    /**
     * 关闭上下文并清理资源
     * 在try-with-resources块结束时自动调用
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        try {
            // 清理未完成的任务
            while (!taskStack.isEmpty()) {
                TaskNode task = taskStack.pop();
            if (task.getStatus().isActive()) {
                logger.debug("Closing context with active task: {}", task.getTaskName());
                task.fail("Context closed with active task");
            }
        }
        
        // 结束会话
        if (currentSession != null && currentSession.isActive()) {
            logger.debug("Closing context with active session: {}", currentSession.getSessionId());
            currentSession.error("Context closed abnormally");
        }
            
            // 清理属性
            attributes.clear();
            
        } finally {
            closed = true;
            
            // 注意：变更追踪清理（如已启用）由 tfi-compare 模块通过会话生命周期钩子处理，
            // 而非直接在 flow-core 中执行。
            
            // 从管理器注销
            SafeContextManager.getInstance().unregisterContext(this);
        }
    }

    public long getElapsedNanos() {
        return System.nanoTime() - createdNanos;
    }
    
    @Override
    public String toString() {
        return String.format("ManagedThreadContext{id='%s', thread='%s', taskDepth=%d, closed=%s}",
                contextId, threadName, taskStack.size(), closed);
    }
}
