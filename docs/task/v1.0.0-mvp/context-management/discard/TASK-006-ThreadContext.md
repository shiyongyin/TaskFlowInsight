# TASK-006: ThreadContext 线程上下文实现【已废弃】

> ⚠️ **注意**: 此文档已被废弃。请参考改进版本：[TASK-006-ThreadContext-Improved.md](../TASK-006-ThreadContext-Improved.md)
> 
> **废弃原因**：
> - 存在严重的ThreadLocal内存泄漏风险
> - 缺乏强制资源管理机制
> - 不支持异步/虚拟线程场景
> 
> **保留原因**：作为历史记录和设计演进参考

---

# TASK-006: ThreadContext 线程上下文实现（原始版本）

## 任务背景

ThreadContext是TaskFlow Insight中管理单个线程执行上下文的核心组件。每个线程都有独立的ThreadContext实例，用于维护该线程的Session信息、任务栈状态和当前执行上下文。ThreadContext需要提供高效的任务栈管理（LIFO操作）、线程隔离保证和异常安全性，确保在多线程环境下各线程的执行上下文完全独立且不互相干扰。

## 目标

1. 实现ThreadContext类，提供线程级别的上下文管理
2. 实现高效的任务栈管理，支持任务的嵌套调用和回退
3. 提供Session的生命周期管理和状态维护
4. 确保异常安全性，避免异常导致的上下文状态不一致
5. 实现上下文快照和恢复功能，支持调试和监控
6. 提供高性能的栈操作，满足高频调用场景

## 实现方案

### 6.1 ThreadContext核心实现

```java
package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程上下文管理器
 * 管理单个线程的执行上下文，包括Session和任务栈
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ThreadContext {
    
    // 静态计数器，用于生成唯一的上下文ID
    private static final AtomicLong CONTEXT_COUNTER = new AtomicLong(0);
    
    private final String contextId;           // 上下文唯一标识
    private final long threadId;              // 所属线程ID
    private final long createdAt;             // 创建时间戳(毫秒)
    
    private Session session;                  // 当前会话
    private final Deque<TaskNode> taskStack;  // 任务栈 (LIFO)
    private TaskNode currentTask;             // 当前活跃任务
    
    private volatile boolean enabled = true;  // 上下文是否启用
    private volatile long lastActiveAt;       // 最后活跃时间
    
    // 统计信息
    private final ContextStatistics statistics;
    
    /**
     * 构造函数 - 包可见性，由ContextManager创建
     * 
     * @param threadId 线程ID
     */
    ThreadContext(long threadId) {
        this.contextId = generateContextId();
        this.threadId = threadId;
        this.createdAt = System.currentTimeMillis();
        this.taskStack = new ArrayDeque<>();
        this.statistics = new ContextStatistics();
        this.lastActiveAt = this.createdAt;
    }
    
    /**
     * 生成上下文唯一ID
     * 
     * @return 上下文ID
     */
    private static String generateContextId() {
        return String.format("CTX-%d-%016X", 
            System.currentTimeMillis(), 
            CONTEXT_COUNTER.incrementAndGet());
    }
    
    /**
     * 初始化会话
     * 如果已有活跃会话，则抛出异常
     * 
     * @return 新创建的Session
     * @throws IllegalStateException 如果已有活跃会话
     */
    public Session initializeSession() {
        if (session != null && session.isActive()) {
            throw new IllegalStateException("Active session already exists: " + session.getSessionId());
        }
        
        this.session = Session.create();
        this.session.start();
        this.currentTask = session.getRoot();
        this.taskStack.clear();
        this.taskStack.push(currentTask);
        
        updateLastActiveTime();
        statistics.incrementSessionCount();
        
        return session;
    }
    
    /**
     * 开始新任务
     * 在当前任务下创建子任务，并入栈
     * 
     * @param taskName 任务名称
     * @return 新创建的TaskNode
     * @throws IllegalStateException 如果没有活跃会话
     */
    public TaskNode startTask(String taskName) {
        validateActiveSession();
        
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be null or empty");
        }
        
        // 创建新的子任务
        int depth = currentTask != null ? currentTask.getDepth() + 1 : 0;
        TaskNode newTask = TaskNode.create(taskName.trim(), currentTask, depth);
        
        // 启动任务并入栈
        newTask.start();
        taskStack.push(newTask);
        currentTask = newTask;
        
        updateLastActiveTime();
        statistics.incrementTaskCount();
        statistics.updateMaxDepth(depth);
        
        return newTask;
    }
    
    /**
     * 结束当前任务
     * 将当前任务标记为完成，并从栈中弹出
     * 
     * @throws IllegalStateException 如果没有活跃任务或栈为空
     */
    public void endTask() {
        validateActiveSession();
        
        if (taskStack.isEmpty()) {
            throw new IllegalStateException("No active task to end");
        }
        
        TaskNode taskToEnd = taskStack.pop();
        
        // 如果任务还在运行，则标记为完成
        if (taskToEnd.getStatus() == TaskStatus.RUNNING) {
            taskToEnd.complete();
        }
        
        // 更新当前任务为栈顶任务
        currentTask = taskStack.peek();
        
        updateLastActiveTime();
        statistics.incrementCompletedTaskCount();
    }
    
    /**
     * 任务执行失败
     * 将当前任务标记为失败，并从栈中弹出
     * 
     * @param reason 失败原因
     */
    public void failTask(String reason) {
        validateActiveSession();
        
        if (taskStack.isEmpty()) {
            throw new IllegalStateException("No active task to fail");
        }
        
        TaskNode taskToFail = taskStack.pop();
        taskToFail.fail(reason != null ? reason : "Task failed");
        
        // 更新当前任务
        currentTask = taskStack.peek();
        
        updateLastActiveTime();
        statistics.incrementFailedTaskCount();
    }
    
    /**
     * 添加消息到当前任务
     * 
     * @param message 消息对象
     */
    public void addMessage(Message message) {
        validateActiveSession();
        
        if (currentTask != null && message != null) {
            currentTask.addMessage(message);
            updateLastActiveTime();
            statistics.incrementMessageCount();
        }
    }
    
    /**
     * 完成会话
     * 结束所有未完成的任务，并将会话标记为完成
     */
    public void completeSession() {
        if (session == null || !session.isActive()) {
            return; // 没有活跃会话，直接返回
        }
        
        // 结束所有未完成的任务
        while (!taskStack.isEmpty()) {
            endTask();
        }
        
        // 完成会话
        session.complete();
        currentTask = null;
        
        updateLastActiveTime();
    }
    
    /**
     * 中止会话
     * 强制结束所有任务，将会话标记为中止状态
     * 
     * @param reason 中止原因
     */
    public void abortSession(String reason) {
        if (session == null) {
            return;
        }
        
        // 强制结束所有任务
        while (!taskStack.isEmpty()) {
            TaskNode task = taskStack.pop();
            if (task.isActive()) {
                task.fail("Session aborted: " + (reason != null ? reason : "Unknown"));
            }
        }
        
        // 中止会话
        session.abort(reason);
        currentTask = null;
        
        updateLastActiveTime();
    }
    
    /**
     * 清理上下文
     * 清理所有状态，准备重用或销毁
     */
    public void cleanup() {
        if (session != null && session.isActive()) {
            abortSession("Context cleanup");
        }
        
        taskStack.clear();
        currentTask = null;
        session = null;
        enabled = false;
        
        statistics.reset();
    }
    
    /**
     * 创建上下文快照
     * 用于调试和监控
     * 
     * @return 上下文快照
     */
    public ContextSnapshot createSnapshot() {
        return new ContextSnapshot(
            contextId,
            threadId,
            session != null ? session.getSessionId() : null,
            session != null ? session.getStatus() : null,
            currentTask != null ? currentTask.getNodeId() : null,
            currentTask != null ? currentTask.getName() : null,
            taskStack.size(),
            getTaskPath(),
            enabled,
            lastActiveAt,
            statistics.copy()
        );
    }
    
    // === Getter方法 ===
    
    public String getContextId() { return contextId; }
    public long getThreadId() { return threadId; }
    public long getCreatedAt() { return createdAt; }
    public Session getSession() { return session; }
    public TaskNode getCurrentTask() { return currentTask; }
    public int getTaskStackDepth() { return taskStack.size(); }
    public boolean isEnabled() { return enabled; }
    public long getLastActiveAt() { return lastActiveAt; }
    public ContextStatistics getStatistics() { return statistics.copy(); }
    
    /**
     * 获取当前任务路径
     * 
     * @return 任务路径字符串，如 "ROOT/TaskA/TaskB"
     */
    public String getTaskPath() {
        if (currentTask == null) {
            return "";
        }
        return currentTask.getPathName();
    }
    
    /**
     * 获取任务栈的副本（只读）
     * 
     * @return 任务栈副本
     */
    public List<TaskNode> getTaskStackCopy() {
        return new ArrayList<>(taskStack);
    }
    
    /**
     * 判断是否有活跃会话
     * 
     * @return true if has active session
     */
    public boolean hasActiveSession() {
        return session != null && session.isActive();
    }
    
    /**
     * 判断是否有活跃任务
     * 
     * @return true if has active task
     */
    public boolean hasActiveTask() {
        return currentTask != null && currentTask.isActive();
    }
    
    /**
     * 启用/禁用上下文
     * 
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        updateLastActiveTime();
    }
    
    // === 私有辅助方法 ===
    
    /**
     * 验证是否有活跃会话
     * 
     * @throws IllegalStateException 如果没有活跃会话
     */
    private void validateActiveSession() {
        if (!enabled) {
            throw new IllegalStateException("ThreadContext is disabled");
        }
        
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("No active session");
        }
    }
    
    /**
     * 更新最后活跃时间
     */
    private void updateLastActiveTime() {
        this.lastActiveAt = System.currentTimeMillis();
    }
    
    /**
     * 获取任务栈路径列表
     * 
     * @return 路径列表
     */
    private List<String> getTaskPath() {
        return taskStack.stream()
                .map(TaskNode::getName)
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public String toString() {
        return String.format("ThreadContext{id='%s', thread=%d, session='%s', depth=%d, enabled=%s}", 
            contextId, 
            threadId, 
            session != null ? session.getSessionId().substring(0, 8) : "none",
            taskStack.size(),
            enabled);
    }
}
```

### 6.2 ContextStatistics统计信息类

```java
package com.syy.taskflowinsight.context;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上下文统计信息
 * 记录ThreadContext的运行统计数据
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ContextStatistics {
    
    private final AtomicLong sessionCount = new AtomicLong(0);      // 会话总数
    private final AtomicLong taskCount = new AtomicLong(0);         // 任务总数
    private final AtomicLong completedTaskCount = new AtomicLong(0); // 完成任务数
    private final AtomicLong failedTaskCount = new AtomicLong(0);   // 失败任务数
    private final AtomicLong messageCount = new AtomicLong(0);      // 消息总数
    private final AtomicInteger maxDepth = new AtomicInteger(0);    // 最大嵌套深度
    
    private final long createdAt;                                   // 创建时间
    private volatile long lastResetAt;                              // 最后重置时间
    
    public ContextStatistics() {
        this.createdAt = System.currentTimeMillis();
        this.lastResetAt = this.createdAt;
    }
    
    // === 计数器增加方法 ===
    
    void incrementSessionCount() {
        sessionCount.incrementAndGet();
    }
    
    void incrementTaskCount() {
        taskCount.incrementAndGet();
    }
    
    void incrementCompletedTaskCount() {
        completedTaskCount.incrementAndGet();
    }
    
    void incrementFailedTaskCount() {
        failedTaskCount.incrementAndGet();
    }
    
    void incrementMessageCount() {
        messageCount.incrementAndGet();
    }
    
    void updateMaxDepth(int depth) {
        maxDepth.updateAndGet(current -> Math.max(current, depth));
    }
    
    // === 统计信息获取 ===
    
    public long getSessionCount() { return sessionCount.get(); }
    public long getTaskCount() { return taskCount.get(); }
    public long getCompletedTaskCount() { return completedTaskCount.get(); }
    public long getFailedTaskCount() { return failedTaskCount.get(); }
    public long getMessageCount() { return messageCount.get(); }
    public int getMaxDepth() { return maxDepth.get(); }
    public long getCreatedAt() { return createdAt; }
    public long getLastResetAt() { return lastResetAt; }
    
    /**
     * 获取任务成功率
     * 
     * @return 成功率 (0.0 - 1.0)
     */
    public double getTaskSuccessRate() {
        long total = completedTaskCount.get() + failedTaskCount.get();
        if (total == 0) {
            return 1.0;
        }
        return (double) completedTaskCount.get() / total;
    }
    
    /**
     * 获取平均每个任务的消息数
     * 
     * @return 平均消息数
     */
    public double getAverageMessagesPerTask() {
        long tasks = taskCount.get();
        if (tasks == 0) {
            return 0.0;
        }
        return (double) messageCount.get() / tasks;
    }
    
    /**
     * 获取运行时长（毫秒）
     * 
     * @return 运行时长
     */
    public long getUptimeMillis() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * 重置所有统计信息
     */
    void reset() {
        sessionCount.set(0);
        taskCount.set(0);
        completedTaskCount.set(0);
        failedTaskCount.set(0);
        messageCount.set(0);
        maxDepth.set(0);
        lastResetAt = System.currentTimeMillis();
    }
    
    /**
     * 创建统计信息副本
     * 
     * @return 统计信息副本
     */
    public ContextStatistics copy() {
        ContextStatistics copy = new ContextStatistics();
        copy.sessionCount.set(this.sessionCount.get());
        copy.taskCount.set(this.taskCount.get());
        copy.completedTaskCount.set(this.completedTaskCount.get());
        copy.failedTaskCount.set(this.failedTaskCount.get());
        copy.messageCount.set(this.messageCount.get());
        copy.maxDepth.set(this.maxDepth.get());
        copy.lastResetAt = this.lastResetAt;
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("ContextStatistics{sessions=%d, tasks=%d, completed=%d, failed=%d, messages=%d, maxDepth=%d, successRate=%.2f}",
            getSessionCount(),
            getTaskCount(),
            getCompletedTaskCount(),
            getFailedTaskCount(),
            getMessageCount(),
            getMaxDepth(),
            getTaskSuccessRate() * 100);
    }
}
```

### 6.3 ContextSnapshot上下文快照类

```java
package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.enums.SessionStatus;
import java.util.List;
import java.util.Objects;

/**
 * 上下文快照
 * 用于调试、监控和状态检查的只读快照
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ContextSnapshot {
    
    private final String contextId;
    private final long threadId;
    private final String sessionId;
    private final SessionStatus sessionStatus;
    private final String currentTaskId;
    private final String currentTaskName;
    private final int taskStackDepth;
    private final List<String> taskPath;
    private final boolean enabled;
    private final long lastActiveAt;
    private final ContextStatistics statistics;
    private final long snapshotTime;
    
    /**
     * 构造函数 - 包可见性
     */
    ContextSnapshot(String contextId, long threadId, String sessionId, 
                   SessionStatus sessionStatus, String currentTaskId, String currentTaskName,
                   int taskStackDepth, List<String> taskPath, boolean enabled, 
                   long lastActiveAt, ContextStatistics statistics) {
        this.contextId = contextId;
        this.threadId = threadId;
        this.sessionId = sessionId;
        this.sessionStatus = sessionStatus;
        this.currentTaskId = currentTaskId;
        this.currentTaskName = currentTaskName;
        this.taskStackDepth = taskStackDepth;
        this.taskPath = List.copyOf(taskPath != null ? taskPath : List.of());
        this.enabled = enabled;
        this.lastActiveAt = lastActiveAt;
        this.statistics = statistics;
        this.snapshotTime = System.currentTimeMillis();
    }
    
    // === Getter方法 ===
    
    public String getContextId() { return contextId; }
    public long getThreadId() { return threadId; }
    public String getSessionId() { return sessionId; }
    public SessionStatus getSessionStatus() { return sessionStatus; }
    public String getCurrentTaskId() { return currentTaskId; }
    public String getCurrentTaskName() { return currentTaskName; }
    public int getTaskStackDepth() { return taskStackDepth; }
    public List<String> getTaskPath() { return taskPath; }
    public boolean isEnabled() { return enabled; }
    public long getLastActiveAt() { return lastActiveAt; }
    public ContextStatistics getStatistics() { return statistics; }
    public long getSnapshotTime() { return snapshotTime; }
    
    /**
     * 判断是否有活跃会话
     * 
     * @return true if has active session
     */
    public boolean hasActiveSession() {
        return sessionStatus != null && sessionStatus.isActive();
    }
    
    /**
     * 判断是否有活跃任务
     * 
     * @return true if has active task
     */
    public boolean hasActiveTask() {
        return currentTaskId != null && taskStackDepth > 0;
    }
    
    /**
     * 获取任务路径字符串
     * 
     * @return 路径字符串，如 "ROOT/TaskA/TaskB"
     */
    public String getTaskPathString() {
        return String.join("/", taskPath);
    }
    
    /**
     * 获取自最后活跃以来的时间
     * 
     * @return 毫秒数
     */
    public long getInactiveTimeMillis() {
        return snapshotTime - lastActiveAt;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ContextSnapshot that = (ContextSnapshot) obj;
        return Objects.equals(contextId, that.contextId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(contextId);
    }
    
    @Override
    public String toString() {
        return String.format("ContextSnapshot{context='%s', thread=%d, session='%s', task='%s', depth=%d, enabled=%s}",
            contextId,
            threadId,
            sessionId != null ? sessionId.substring(0, 8) : "none",
            currentTaskName != null ? currentTaskName : "none",
            taskStackDepth,
            enabled);
    }
    
    /**
     * 转换为详细的调试字符串
     * 
     * @return 详细信息字符串
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Context Snapshot ===\n");
        sb.append("Context ID: ").append(contextId).append("\n");
        sb.append("Thread ID: ").append(threadId).append("\n");
        sb.append("Session ID: ").append(sessionId != null ? sessionId : "none").append("\n");
        sb.append("Session Status: ").append(sessionStatus != null ? sessionStatus : "none").append("\n");
        sb.append("Current Task: ").append(currentTaskName != null ? currentTaskName : "none").append("\n");
        sb.append("Stack Depth: ").append(taskStackDepth).append("\n");
        sb.append("Task Path: ").append(getTaskPathString()).append("\n");
        sb.append("Enabled: ").append(enabled).append("\n");
        sb.append("Last Active: ").append(new java.util.Date(lastActiveAt)).append("\n");
        sb.append("Inactive Time: ").append(getInactiveTimeMillis()).append(" ms\n");
        sb.append("Statistics: ").append(statistics).append("\n");
        sb.append("Snapshot Time: ").append(new java.util.Date(snapshotTime)).append("\n");
        return sb.toString();
    }
}
```

## 测试标准

### 6.1 功能测试要求

1. **上下文初始化测试**
   - 验证ThreadContext正确创建和初始化
   - 验证唯一ID生成和线程关联
   - 验证初始状态设置

2. **会话管理测试**
   - 验证会话创建和启动流程
   - 验证会话完成和中止流程
   - 验证会话状态转换正确性

3. **任务栈管理测试**
   - 验证任务入栈和出栈操作
   - 验证嵌套任务的正确处理
   - 验证异常情况下的栈状态恢复

4. **异常安全测试**
   - 验证异常发生时的上下文状态一致性
   - 验证资源清理的完整性
   - 验证错误恢复机制

### 6.2 性能测试要求

1. **栈操作性能**
   - 任务入栈操作耗时 < 0.5微秒
   - 任务出栈操作耗时 < 0.5微秒
   - 深度嵌套(100层)性能测试

2. **内存效率测试**
   - 单个ThreadContext内存占用 < 1KB
   - 1000个嵌套任务内存占用 < 100KB
   - 内存泄漏检测

3. **并发性能测试**
   - 多线程独立操作无性能干扰
   - 快照创建操作耗时 < 1微秒

### 6.3 并发安全测试

1. **线程隔离测试**
   - 验证不同线程的上下文完全隔离
   - 验证并发操作不会相互影响

2. **原子性测试**
   - 验证复合操作的原子性
   - 验证状态一致性保证

## 验收标准

### 6.1 功能验收

- [ ] ThreadContext正确实现所有核心功能
- [ ] 会话和任务管理流程完整正确
- [ ] 异常处理和错误恢复机制完善
- [ ] 统计信息准确记录和计算
- [ ] 快照功能提供完整的状态信息

### 6.2 质量验收

- [ ] 代码设计清晰，职责明确
- [ ] 异常安全性得到保证
- [ ] 资源管理正确，无内存泄漏
- [ ] 线程安全机制完善

### 6.3 性能验收

- [ ] 栈操作性能满足要求 (< 0.5微秒)
- [ ] 内存占用满足要求 (< 1KB基础占用)
- [ ] 并发性能表现良好
- [ ] 大量嵌套场景性能稳定

### 6.4 测试验收

- [ ] 单元测试覆盖率 ≥ 95%
- [ ] 所有功能场景测试通过
- [ ] 边界和异常测试完整
- [ ] 性能和并发测试通过

## 依赖关系

- **前置依赖**: TASK-001, TASK-002, TASK-003, TASK-004 (核心数据模型和枚举)
- **后置依赖**: TASK-007 (ContextManager使用ThreadContext)
- **相关任务**: TASK-010 (TFI主API通过ContextManager使用ThreadContext)

## 预计工期

- **开发时间**: 2天
- **测试时间**: 1天
- **总计**: 3天

## 风险识别

1. **内存管理风险**: 任务栈可能导致内存占用过高
   - **缓解措施**: 实现栈深度限制和自动清理机制

2. **异常处理复杂性**: 复杂的嵌套异常处理逻辑
   - **缓解措施**: 简化异常处理流程，使用try-with-resources模式

3. **性能优化权衡**: 功能完整性与性能之间的平衡
   - **缓解措施**: 基于性能测试结果进行针对性优化