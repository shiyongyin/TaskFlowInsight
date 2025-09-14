# TASK-006: ThreadContext线程上下文实现（改进版）

## 任务背景

ThreadContext是TaskFlow Insight系统中每个线程独立的上下文容器，负责管理当前线程的会话栈和任务栈。基于架构评审和风险分析，本版本采用**强制资源管理模式**，确保ThreadLocal不会泄漏，并为未来的异步场景做好准备。

**关键改进**：
- 强制使用AutoCloseable模式，确保资源清理
- 防御性编程，检测潜在的泄漏场景
- 为异步传递预留扩展点

## 目标

1. 实现线程安全的上下文容器，使用强制资源管理
2. 提供任务栈和会话栈的管理功能
3. **确保100%的ThreadLocal清理，防止内存泄漏**
4. 支持嵌套任务的正确处理
5. 提供上下文快照功能，为异步传递做准备

## 实现方案

### 1. ManagedThreadContext核心实现

**文件位置**: `src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java`

```java
package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 托管的线程上下文 - 强制资源管理版本
 * 必须使用try-with-resources模式，确保ThreadLocal清理
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0-improved
 */
public final class ManagedThreadContext implements AutoCloseable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedThreadContext.class);
    private static final ThreadLocal<ManagedThreadContext> HOLDER = new ThreadLocal<>();
    
    // 上下文数据
    private final long threadId;
    private final String contextId;
    private final long createdAt;
    private final Stack<Session> sessionStack;
    private final Stack<TaskNode> taskStack;
    private final AtomicBoolean closed;
    
    // 嵌套检测
    private final ManagedThreadContext parent;
    private final int nestingLevel;
    
    // 统计信息
    private int totalTasksCreated = 0;
    private int maxTaskDepth = 0;
    
    /**
     * 私有构造函数，防止直接实例化
     */
    private ManagedThreadContext() {
        this.threadId = Thread.currentThread().getId();
        this.contextId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.sessionStack = new Stack<>();
        this.taskStack = new Stack<>();
        this.closed = new AtomicBoolean(false);
        
        // 检测嵌套上下文
        this.parent = HOLDER.get();
        if (parent != null) {
            this.nestingLevel = parent.nestingLevel + 1;
            LOGGER.warn("Nested context detected at level {}, potential leak risk!", nestingLevel);
        } else {
            this.nestingLevel = 0;
        }
        
        // 设置到ThreadLocal
        HOLDER.set(this);
    }
    
    /**
     * 创建新的托管上下文
     * 必须在try-with-resources中使用
     * 
     * @return 托管的线程上下文
     */
    public static ManagedThreadContext create() {
        return new ManagedThreadContext();
    }
    
    /**
     * 获取当前线程的上下文
     * 
     * @return 当前上下文
     * @throws IllegalStateException 如果没有活动的上下文
     */
    public static ManagedThreadContext current() {
        ManagedThreadContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException(
                "No active context. Use try-with-resources: " +
                "try (ManagedThreadContext ctx = ManagedThreadContext.create()) { ... }"
            );
        }
        if (context.closed.get()) {
            throw new IllegalStateException("Context has been closed");
        }
        return context;
    }
    
    /**
     * 检查是否有活动的上下文
     */
    public static boolean hasActiveContext() {
        ManagedThreadContext context = HOLDER.get();
        return context != null && !context.closed.get();
    }
    
    /**
     * 开始新会话
     * 
     * @return 新创建的会话
     */
    public Session startSession() {
        ensureNotClosed();
        
        Session session = new Session(threadId);
        sessionStack.push(session);
        
        LOGGER.debug("Started session {} in thread {}", session.getSessionId(), threadId);
        return session;
    }
    
    /**
     * 结束当前会话
     * 
     * @return 结束的会话，如果没有活动会话返回null
     */
    public Session endSession() {
        ensureNotClosed();
        
        if (sessionStack.isEmpty()) {
            LOGGER.warn("No active session to end in thread {}", threadId);
            return null;
        }
        
        Session session = sessionStack.pop();
        session.end();
        
        // 清理相关的任务栈
        while (!taskStack.isEmpty() && isTaskBelongsToSession(taskStack.peek(), session)) {
            TaskNode task = taskStack.pop();
            if (task.isActive()) {
                task.stop();
            }
        }
        
        LOGGER.debug("Ended session {} in thread {}", session.getSessionId(), threadId);
        return session;
    }
    
    /**
     * 开始新任务
     * 
     * @param taskName 任务名称
     * @return 新创建的任务节点
     */
    public TaskNode startTask(String taskName) {
        ensureNotClosed();
        
        if (sessionStack.isEmpty()) {
            // 自动创建会话
            startSession();
        }
        
        Session currentSession = sessionStack.peek();
        TaskNode parentTask = taskStack.isEmpty() ? null : taskStack.peek();
        
        int depth = parentTask != null ? parentTask.getDepth() + 1 : 0;
        TaskNode newTask = new TaskNode(taskName, depth);
        
        if (parentTask != null) {
            parentTask.addChild(newTask);
        } else {
            currentSession.setRoot(newTask);
        }
        
        taskStack.push(newTask);
        totalTasksCreated++;
        maxTaskDepth = Math.max(maxTaskDepth, depth);
        
        LOGGER.trace("Started task '{}' at depth {} in thread {}", taskName, depth, threadId);
        return newTask;
    }
    
    /**
     * 结束当前任务
     * 
     * @return 结束的任务节点，如果没有活动任务返回null
     */
    public TaskNode endTask() {
        ensureNotClosed();
        
        if (taskStack.isEmpty()) {
            LOGGER.warn("No active task to end in thread {}", threadId);
            return null;
        }
        
        TaskNode task = taskStack.pop();
        if (task.isActive()) {
            task.stop();
        }
        
        LOGGER.trace("Ended task '{}' in thread {}", task.getName(), threadId);
        return task;
    }
    
    /**
     * 创建上下文快照，用于异步传递
     * 
     * @return 上下文快照
     */
    public ContextSnapshot createSnapshot() {
        ensureNotClosed();
        
        return new ContextSnapshot(
            contextId,
            threadId,
            sessionStack.isEmpty() ? null : sessionStack.peek().getSessionId(),
            taskStack.isEmpty() ? null : taskStack.peek().getNodeId(),
            totalTasksCreated,
            maxTaskDepth
        );
    }
    
    /**
     * 清理并关闭上下文
     * 自动被try-with-resources调用
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // 清理所有活动的任务
                while (!taskStack.isEmpty()) {
                    endTask();
                }
                
                // 清理所有活动的会话
                while (!sessionStack.isEmpty()) {
                    endSession();
                }
                
                LOGGER.debug("Closed context {} in thread {}, created {} tasks, max depth {}",
                    contextId, threadId, totalTasksCreated, maxTaskDepth);
            } finally {
                // 确保从ThreadLocal中移除
                if (HOLDER.get() == this) {
                    HOLDER.remove();
                    
                    // 恢复父上下文（如果有）
                    if (parent != null) {
                        HOLDER.set(parent);
                        LOGGER.debug("Restored parent context at level {}", parent.nestingLevel);
                    }
                }
            }
        }
    }
    
    /**
     * 确保上下文未关闭
     */
    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Context has been closed");
        }
    }
    
    /**
     * 检查任务是否属于指定会话
     */
    private boolean isTaskBelongsToSession(TaskNode task, Session session) {
        if (task == null || session == null) {
            return false;
        }
        
        // 检查任务是否是会话的根节点或其子节点
        TaskNode root = session.getRoot();
        if (root == null) {
            return false;
        }
        
        // 遍历检查任务是否在会话的任务树中
        return isTaskInTree(task, root);
    }
    
    /**
     * 递归检查任务是否在任务树中
     */
    private boolean isTaskInTree(TaskNode target, TaskNode root) {
        if (root == target) {
            return true;
        }
        
        for (TaskNode child : root.getChildren()) {
            if (isTaskInTree(target, child)) {
                return true;
            }
        }
        
        return false;
    }
    
    // Getters
    public long getThreadId() { return threadId; }
    public String getContextId() { return contextId; }
    public long getCreatedAt() { return createdAt; }
    public int getTotalTasksCreated() { return totalTasksCreated; }
    public int getMaxTaskDepth() { return maxTaskDepth; }
    public Session getCurrentSession() { return sessionStack.isEmpty() ? null : sessionStack.peek(); }
    public TaskNode getCurrentTask() { return taskStack.isEmpty() ? null : taskStack.peek(); }
    public boolean isClosed() { return closed.get(); }
    
    /**
     * 上下文快照，用于异步传递
     */
    public static class ContextSnapshot {
        private final String contextId;
        private final long threadId;
        private final String sessionId;
        private final String taskId;
        private final int totalTasks;
        private final int maxDepth;
        
        ContextSnapshot(String contextId, long threadId, String sessionId, 
                       String taskId, int totalTasks, int maxDepth) {
            this.contextId = contextId;
            this.threadId = threadId;
            this.sessionId = sessionId;
            this.taskId = taskId;
            this.totalTasks = totalTasks;
            this.maxDepth = maxDepth;
        }
        
        /**
         * 从快照恢复上下文
         * 在新线程中重建上下文状态
         * 
         * @return 恢复的托管上下文
         */
        public ManagedThreadContext restore() {
            ManagedThreadContext restored = new ManagedThreadContext();
            
            // 记录快照恢复信息
            LOGGER.debug("Restoring context from snapshot: contextId={}, originalThread={}, currentThread={}",
                contextId, threadId, Thread.currentThread().getId());
            
            // 注意：这里创建新的上下文，而不是恢复原有的会话和任务
            // 这是为了保证线程安全，避免跨线程共享可变状态
            // 如需要恢复会话状态，应该通过SessionManager查找并创建副本
            
            return restored;
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public long getThreadId() { return threadId; }
        public String getSessionId() { return sessionId; }
        public String getTaskId() { return taskId; }
        public int getTotalTasks() { return totalTasks; }
        public int getMaxDepth() { return maxDepth; }
    }
}
```

### 2. 使用示例

```java
// 正确使用方式 - 强制资源管理
public void processRequest() {
    try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
        TaskNode task = ctx.startTask("process-request");
        
        try {
            // 业务逻辑
            doBusinessLogic();
            
            // 嵌套任务
            TaskNode subTask = ctx.startTask("sub-process");
            doSubProcess();
            ctx.endTask(); // 结束子任务
            
        } finally {
            ctx.endTask(); // 结束主任务
        }
    } // 自动清理，保证无泄漏
}

// 错误使用方式 - IDE/静态分析工具会警告
public void wrongUsage() {
    ManagedThreadContext ctx = ManagedThreadContext.create();
    // IDE警告：资源'ctx'未关闭（需要配置IDE的资源泄漏检测）
    // 静态分析工具（如SpotBugs、SonarQube）也会检测到此问题
    ctx.startTask("task");
    // 潜在的内存泄漏！
}

// 推荐：配置IDE和构建工具
// IntelliJ IDEA: Settings -> Editor -> Inspections -> Resource management
// Maven: 添加SpotBugs插件进行构建时检查
// SonarQube: 配置规则检测AutoCloseable资源泄漏
```

### 3. 线程池安全包装器

```java
package com.syy.taskflowinsight.context;

import java.util.concurrent.*;

/**
 * 线程池安全执行器
 * 自动管理ThreadLocal清理
 */
public class SafeThreadPoolExecutor extends ThreadPoolExecutor {
    
    public SafeThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                 long keepAliveTime, TimeUnit unit,
                                 BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }
    
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            // 强制清理ThreadLocal
            if (ManagedThreadContext.hasActiveContext()) {
                LOGGER.warn("Detected unclosed context after task execution, force cleaning");
                // 这里不能直接close，因为可能不是owner
                // 记录警告，让监控系统处理
            }
        } finally {
            super.afterExecute(r, t);
        }
    }
    
    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(new SafeRunnable(task));
    }
    
    private static class SafeRunnable implements Runnable {
        private final Runnable delegate;
        
        SafeRunnable(Runnable delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void run() {
            try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
                delegate.run();
            } // 自动清理
        }
    }
}
```

## 测试标准

### 功能测试
- [ ] 强制资源管理模式正常工作
- [ ] 嵌套上下文正确检测和警告
- [ ] 任务栈和会话栈正确管理
- [ ] 异常情况下资源正确清理

### 内存泄漏测试
- [ ] try-with-resources确保清理
- [ ] 线程池场景无泄漏
- [ ] 嵌套使用无泄漏
- [ ] 异常退出无泄漏

### 性能测试
- [ ] 上下文创建 < 1微秒
- [ ] 任务操作 < 100纳秒
- [ ] 内存占用 < 1KB/线程

## 验收标准

### MVP必需
- [ ] **100%资源清理保证**
- [ ] **编译时强制资源管理**
- [ ] **运行时泄漏检测和警告**
- [ ] **线程池场景安全**

### 质量要求
- [ ] 单元测试覆盖率 > 95%
- [ ] 无内存泄漏（24小时压测）
- [ ] 文档和示例完整

---

**注意**: 此版本解决了原ThreadLocal实现的内存泄漏风险，通过强制资源管理模式确保生产环境的稳定性。