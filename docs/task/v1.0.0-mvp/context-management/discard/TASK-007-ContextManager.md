# TASK-007: ContextManager 上下文管理器实现【已废弃】

> ⚠️ **注意**: 此文档已被废弃。请参考改进版本：[TASK-007-ContextManager-Improved.md](../TASK-007-ContextManager-Improved.md)
> 
> **废弃原因**：
> - 被动清理机制不可靠
> - 缺乏主动泄漏检测和修复
> - 线程池场景下有泄漏风险
> 
> **保留原因**：作为架构演进和教训参考

---

# TASK-007: ContextManager 上下文管理器实现（原始版本）

## 任务背景

ContextManager是TaskFlow Insight系统的核心管理组件，负责管理所有线程的ThreadContext实例，提供全局的上下文访问和会话索引功能。它使用ThreadLocal机制确保线程隔离，同时维护全局的Session索引以支持跨线程的查询和监控。ContextManager需要处理高并发场景，提供高效的上下文管理和资源回收机制。

## 目标

1. 实现ContextManager单例类，提供全局统一的上下文管理
2. 使用ThreadLocal机制实现线程隔离的上下文访问
3. 维护全局Session索引，支持跨线程Session查询
4. 实现自动的资源清理和内存回收机制
5. 提供系统级别的统计信息和监控接口
6. 实现上下文的启用/禁用控制，支持性能调优
7. 确保高并发场景下的线程安全和性能表现

## 实现方案

### 7.1 ContextManager核心实现

```java
package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.enums.SystemStatus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 上下文管理器
 * 全局单例，管理所有线程的ThreadContext和Session索引
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ContextManager {
    
    // === 单例实例 ===
    private static final ContextManager INSTANCE = new ContextManager();
    
    // === 系统状态管理 ===
    private volatile SystemStatus systemStatus = SystemStatus.UNINITIALIZED;
    private volatile boolean globalEnabled = false;
    
    // === ThreadLocal存储 ===
    private final ThreadLocal<ThreadContext> contextHolder = new ThreadLocal<ThreadContext>() {
        @Override
        protected ThreadContext initialValue() {
            return createThreadContext();
        }
    };
    
    // === 全局索引和统计 ===
    private final ConcurrentHashMap<String, Session> sessionIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ThreadContext> threadContextIndex = new ConcurrentHashMap<>();
    private final ReadWriteLock statisticsLock = new ReentrantReadWriteLock();
    
    // === 统计计数器 ===
    private final AtomicLong totalSessions = new AtomicLong(0);
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalThreads = new AtomicLong(0);
    
    // === 清理和维护 ===
    private final ScheduledExecutorService cleanupExecutor;
    private final long cleanupIntervalMs = TimeUnit.MINUTES.toMillis(5); // 5分钟清理间隔
    private final long inactiveThresholdMs = TimeUnit.HOURS.toMillis(1);  // 1小时不活跃阈值
    
    // === 系统配置 ===
    private volatile int maxSessionsPerThread = 10;
    private volatile int maxTaskDepth = 100;
    private volatile boolean autoCleanupEnabled = true;
    
    /**
     * 私有构造函数
     */
    private ContextManager() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TFI-ContextManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定期清理任务
        scheduleCleanupTask();
        
        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "TFI-ContextManager-Shutdown"));
    }
    
    /**
     * 获取单例实例
     * 
     * @return ContextManager实例
     */
    public static ContextManager getInstance() {
        return INSTANCE;
    }
    
    // === 系统控制方法 ===
    
    /**
     * 初始化系统
     * 
     * @return 是否初始化成功
     */
    public synchronized boolean initialize() {
        if (systemStatus == SystemStatus.ENABLED) {
            return true; // 已经初始化
        }
        
        try {
            // 清理可能的残留状态
            cleanup();
            
            // 设置系统状态
            systemStatus = SystemStatus.ENABLED;
            globalEnabled = true;
            
            return true;
        } catch (Exception e) {
            systemStatus = SystemStatus.ERROR;
            globalEnabled = false;
            return false;
        }
    }
    
    /**
     * 启用系统
     */
    public synchronized void enable() {
        if (systemStatus == SystemStatus.UNINITIALIZED) {
            initialize();
        } else {
            systemStatus = SystemStatus.ENABLED;
            globalEnabled = true;
        }
    }
    
    /**
     * 禁用系统
     */
    public synchronized void disable() {
        globalEnabled = false;
        systemStatus = SystemStatus.DISABLED;
    }
    
    /**
     * 判断系统是否启用
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return globalEnabled && systemStatus.canAcceptTasks();
    }
    
    /**
     * 获取系统状态
     * 
     * @return 系统状态
     */
    public SystemStatus getSystemStatus() {
        return systemStatus;
    }
    
    // === 上下文管理方法 ===
    
    /**
     * 获取当前线程的上下文
     * 
     * @return ThreadContext实例
     */
    public ThreadContext getCurrentContext() {
        if (!isEnabled()) {
            return NullThreadContext.INSTANCE; // 返回空上下文
        }
        
        return contextHolder.get();
    }
    
    /**
     * 获取指定线程的上下文
     * 
     * @param threadId 线程ID
     * @return ThreadContext实例，如果不存在返回null
     */
    public ThreadContext getContextByThread(long threadId) {
        return threadContextIndex.get(threadId);
    }
    
    /**
     * 创建新的会话
     * 
     * @return Session实例
     * @throws IllegalStateException 如果系统未启用或上下文已有活跃会话
     */
    public Session startSession() {
        if (!isEnabled()) {
            throw new IllegalStateException("ContextManager is not enabled");
        }
        
        ThreadContext context = getCurrentContext();
        Session session = context.initializeSession();
        
        // 添加到全局索引
        sessionIndex.put(session.getSessionId(), session);
        totalSessions.incrementAndGet();
        
        return session;
    }
    
    /**
     * 完成当前会话
     */
    public void completeSession() {
        if (!isEnabled()) {
            return;
        }
        
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.completeSession();
        }
    }
    
    /**
     * 中止当前会话
     * 
     * @param reason 中止原因
     */
    public void abortSession(String reason) {
        if (!isEnabled()) {
            return;
        }
        
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.abortSession(reason);
        }
    }
    
    /**
     * 开始新任务
     * 
     * @param taskName 任务名称
     * @return TaskNode实例
     */
    public TaskNode startTask(String taskName) {
        if (!isEnabled()) {
            return NullTaskNode.create(taskName);
        }
        
        ThreadContext context = getCurrentContext();
        TaskNode task = context.startTask(taskName);
        
        totalTasks.incrementAndGet();
        return task;
    }
    
    /**
     * 结束当前任务
     */
    public void endTask() {
        if (!isEnabled()) {
            return;
        }
        
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.endTask();
        }
    }
    
    /**
     * 任务执行失败
     * 
     * @param reason 失败原因
     */
    public void failTask(String reason) {
        if (!isEnabled()) {
            return;
        }
        
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.failTask(reason);
        }
    }
    
    /**
     * 添加消息到当前任务
     * 
     * @param message 消息对象
     */
    public void addMessage(Message message) {
        if (!isEnabled() || message == null) {
            return;
        }
        
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.addMessage(message);
            totalMessages.incrementAndGet();
        }
    }
    
    // === 查询和监控方法 ===
    
    /**
     * 根据Session ID获取Session
     * 
     * @param sessionId 会话ID
     * @return Session实例，不存在返回null
     */
    public Session getSessionById(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionIndex.get(sessionId);
    }
    
    /**
     * 获取所有活跃会话
     * 
     * @return 活跃会话列表
     */
    public List<Session> getActiveSessions() {
        return sessionIndex.values().stream()
                .filter(Session::isActive)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取所有线程的上下文快照
     * 
     * @return 上下文快照列表
     */
    public List<ContextSnapshot> getAllContextSnapshots() {
        return threadContextIndex.values().stream()
                .map(ThreadContext::createSnapshot)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取系统统计信息
     * 
     * @return 系统统计信息
     */
    public SystemStatistics getSystemStatistics() {
        statisticsLock.readLock().lock();
        try {
            return new SystemStatistics(
                systemStatus,
                globalEnabled,
                totalSessions.get(),
                totalTasks.get(),
                totalMessages.get(),
                totalThreads.get(),
                sessionIndex.size(),
                threadContextIndex.size(),
                getActiveSessions().size(),
                System.currentTimeMillis()
            );
        } finally {
            statisticsLock.readLock().unlock();
        }
    }
    
    // === 清理和维护方法 ===
    
    /**
     * 清理不活跃的上下文和会话
     */
    public void cleanup() {
        if (!autoCleanupEnabled) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // 清理不活跃的ThreadContext
        threadContextIndex.entrySet().removeIf(entry -> {
            ThreadContext context = entry.getValue();
            if (currentTime - context.getLastActiveAt() > inactiveThresholdMs) {
                context.cleanup();
                return true;
            }
            return false;
        });
        
        // 清理已完成的Session
        sessionIndex.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            return !session.isActive();
        });
        
        // 清理ThreadLocal
        cleanupCurrentThreadContext();
    }
    
    /**
     * 清理当前线程的上下文
     */
    public void cleanupCurrentThreadContext() {
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.cleanup();
            contextHolder.remove();
            threadContextIndex.remove(Thread.currentThread().getId());
        }
    }
    
    /**
     * 系统关闭
     */
    public synchronized void shutdown() {
        systemStatus = SystemStatus.MAINTENANCE;
        globalEnabled = false;
        
        // 停止清理任务
        cleanupExecutor.shutdown();
        
        // 清理所有上下文
        threadContextIndex.values().forEach(ThreadContext::cleanup);
        threadContextIndex.clear();
        sessionIndex.clear();
        
        // 清理ThreadLocal
        contextHolder.remove();
        
        systemStatus = SystemStatus.DISABLED;
    }
    
    // === 配置方法 ===
    
    public void setMaxSessionsPerThread(int maxSessions) {
        this.maxSessionsPerThread = Math.max(1, maxSessions);
    }
    
    public void setMaxTaskDepth(int maxDepth) {
        this.maxTaskDepth = Math.max(10, maxDepth);
    }
    
    public void setAutoCleanupEnabled(boolean enabled) {
        this.autoCleanupEnabled = enabled;
    }
    
    public int getMaxSessionsPerThread() { return maxSessionsPerThread; }
    public int getMaxTaskDepth() { return maxTaskDepth; }
    public boolean isAutoCleanupEnabled() { return autoCleanupEnabled; }
    
    // === 私有辅助方法 ===
    
    /**
     * 创建ThreadContext实例
     * 
     * @return ThreadContext实例
     */
    private ThreadContext createThreadContext() {
        long threadId = Thread.currentThread().getId();
        ThreadContext context = new ThreadContext(threadId);
        
        threadContextIndex.put(threadId, context);
        totalThreads.incrementAndGet();
        
        return context;
    }
    
    /**
     * 启动定期清理任务
     */
    private void scheduleCleanupTask() {
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanup,
            cleanupIntervalMs,
            cleanupIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }
}
```

### 7.2 SystemStatistics系统统计信息类

```java
package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.enums.SystemStatus;

/**
 * 系统统计信息
 * 提供TFI系统的全局统计数据
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class SystemStatistics {
    
    private final SystemStatus systemStatus;
    private final boolean globalEnabled;
    private final long totalSessions;
    private final long totalTasks;
    private final long totalMessages;
    private final long totalThreads;
    private final int totalSessionsInIndex;
    private final int totalContextsInIndex;
    private final int activeSessions;
    private final long snapshotTime;
    
    /**
     * 构造函数
     */
    public SystemStatistics(SystemStatus systemStatus, boolean globalEnabled,
                           long totalSessions, long totalTasks, long totalMessages,
                           long totalThreads, int totalSessionsInIndex,
                           int totalContextsInIndex, int activeSessions,
                           long snapshotTime) {
        this.systemStatus = systemStatus;
        this.globalEnabled = globalEnabled;
        this.totalSessions = totalSessions;
        this.totalTasks = totalTasks;
        this.totalMessages = totalMessages;
        this.totalThreads = totalThreads;
        this.totalSessionsInIndex = totalSessionsInIndex;
        this.totalContextsInIndex = totalContextsInIndex;
        this.activeSessions = activeSessions;
        this.snapshotTime = snapshotTime;
    }
    
    // === Getter方法 ===
    
    public SystemStatus getSystemStatus() { return systemStatus; }
    public boolean isGlobalEnabled() { return globalEnabled; }
    public long getTotalSessions() { return totalSessions; }
    public long getTotalTasks() { return totalTasks; }
    public long getTotalMessages() { return totalMessages; }
    public long getTotalThreads() { return totalThreads; }
    public int getTotalSessionsInIndex() { return totalSessionsInIndex; }
    public int getTotalContextsInIndex() { return totalContextsInIndex; }
    public int getActiveSessions() { return activeSessions; }
    public long getSnapshotTime() { return snapshotTime; }
    
    /**
     * 获取平均每个会话的任务数
     * 
     * @return 平均任务数
     */
    public double getAverageTasksPerSession() {
        if (totalSessions == 0) {
            return 0.0;
        }
        return (double) totalTasks / totalSessions;
    }
    
    /**
     * 获取平均每个任务的消息数
     * 
     * @return 平均消息数
     */
    public double getAverageMessagesPerTask() {
        if (totalTasks == 0) {
            return 0.0;
        }
        return (double) totalMessages / totalTasks;
    }
    
    /**
     * 获取会话完成率
     * 
     * @return 完成率 (0.0 - 1.0)
     */
    public double getSessionCompletionRate() {
        if (totalSessions == 0) {
            return 1.0;
        }
        long completedSessions = totalSessions - activeSessions;
        return (double) completedSessions / totalSessions;
    }
    
    /**
     * 计算系统健康度分数 (0-100)
     * 
     * @return 健康度分数
     */
    public int getHealthScore() {
        if (!globalEnabled || !systemStatus.isWorking()) {
            return 0;
        }
        
        int score = 100;
        
        // 根据活跃会话数调整分数
        if (activeSessions > totalContextsInIndex * 2) {
            score -= 20; // 活跃会话过多
        }
        
        // 根据完成率调整分数
        double completionRate = getSessionCompletionRate();
        if (completionRate < 0.8) {
            score -= (int) ((1.0 - completionRate) * 30);
        }
        
        return Math.max(0, score);
    }
    
    @Override
    public String toString() {
        return String.format("SystemStatistics{status=%s, enabled=%s, sessions=%d, tasks=%d, messages=%d, threads=%d, active=%d, health=%d}",
            systemStatus,
            globalEnabled,
            totalSessions,
            totalTasks,
            totalMessages,
            totalThreads,
            activeSessions,
            getHealthScore());
    }
    
    /**
     * 转换为详细的统计字符串
     * 
     * @return 详细统计信息
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TFI System Statistics ===\n");
        sb.append("System Status: ").append(systemStatus).append("\n");
        sb.append("Global Enabled: ").append(globalEnabled).append("\n");
        sb.append("Total Sessions: ").append(totalSessions).append("\n");
        sb.append("Total Tasks: ").append(totalTasks).append("\n");
        sb.append("Total Messages: ").append(totalMessages).append("\n");
        sb.append("Total Threads: ").append(totalThreads).append("\n");
        sb.append("Sessions in Index: ").append(totalSessionsInIndex).append("\n");
        sb.append("Contexts in Index: ").append(totalContextsInIndex).append("\n");
        sb.append("Active Sessions: ").append(activeSessions).append("\n");
        sb.append("Average Tasks/Session: ").append(String.format("%.2f", getAverageTasksPerSession())).append("\n");
        sb.append("Average Messages/Task: ").append(String.format("%.2f", getAverageMessagesPerTask())).append("\n");
        sb.append("Session Completion Rate: ").append(String.format("%.2f%%", getSessionCompletionRate() * 100)).append("\n");
        sb.append("System Health Score: ").append(getHealthScore()).append("\n");
        sb.append("Snapshot Time: ").append(new java.util.Date(snapshotTime)).append("\n");
        return sb.toString();
    }
}
```

## 测试标准

### 7.1 功能测试要求

1. **单例模式测试**
   - 验证单例实例的唯一性
   - 验证线程安全的单例访问
   - 验证初始化状态正确性

2. **系统状态管理测试**
   - 验证初始化、启用、禁用流程
   - 验证状态转换的正确性
   - 验证异常情况下的状态处理

3. **线程隔离测试**
   - 验证ThreadLocal机制的正确性
   - 验证不同线程上下文的独立性
   - 验证跨线程访问的安全性

4. **资源管理测试**
   - 验证自动清理机制
   - 验证内存泄漏防护
   - 验证关闭时的资源清理

### 7.2 性能测试要求

1. **上下文访问性能**
   - 获取当前上下文耗时 < 0.1微秒
   - 会话创建和管理耗时 < 1微秒
   - 任务管理操作耗时 < 0.5微秒

2. **并发性能测试**
   - 100个线程并发操作性能稳定
   - 无锁等待和死锁情况
   - 内存占用随线程数线性增长

3. **大规模测试**
   - 支持1000个活跃上下文
   - 10000个Session索引查询性能
   - 长时间运行稳定性测试

### 7.3 并发安全测试

1. **多线程访问测试**
   - 验证ConcurrentHashMap使用正确性
   - 验证原子操作的线程安全性
   - 验证读写锁使用的正确性

2. **资源竞争测试**
   - 验证清理操作的线程安全性
   - 验证统计信息更新的原子性

## 验收标准

### 7.1 功能验收

- [ ] ContextManager单例正确实现和工作
- [ ] ThreadLocal机制提供完整的线程隔离
- [ ] 全局Session索引功能正常
- [ ] 系统状态管理流程完整
- [ ] 资源清理和回收机制正常工作

### 7.2 质量验收

- [ ] 代码结构清晰，职责分明
- [ ] 异常处理完善，错误恢复机制健全
- [ ] 内存管理正确，无泄漏风险
- [ ] 并发安全机制完善

### 7.3 性能验收

- [ ] 核心操作性能满足要求
- [ ] 并发访问性能表现良好
- [ ] 内存占用合理，随负载线性增长
- [ ] 大规模场景性能稳定

### 7.4 测试验收

- [ ] 单元测试覆盖率 ≥ 95%
- [ ] 功能测试全面，覆盖所有重要场景
- [ ] 并发测试充分，验证线程安全性
- [ ] 性能测试通过，满足性能要求

## 依赖关系

- **前置依赖**: TASK-006 (ThreadContext), TASK-004 (SystemStatus枚举)
- **后置依赖**: TASK-010 (TFI主API使用ContextManager)
- **相关任务**: TASK-011, TASK-012 (输出和导出功能需要通过ContextManager获取数据)

## 预计工期

- **开发时间**: 2.5天
- **测试时间**: 1.5天
- **总计**: 4天

## 风险识别

1. **内存泄漏风险**: ThreadLocal和全局索引可能导致内存泄漏
   - **缓解措施**: 实现完善的清理机制和定期维护任务

2. **性能瓶颈风险**: 全局索引在高并发下可能成为瓶颈
   - **缓解措施**: 使用高效的并发数据结构和分片策略

3. **线程安全复杂性**: 复杂的多线程交互可能导致竞争条件
   - **缓解措施**: 仔细设计同步机制，充分的并发测试