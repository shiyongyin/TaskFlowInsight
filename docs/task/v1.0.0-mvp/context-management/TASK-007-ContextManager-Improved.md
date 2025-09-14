# TASK-007: ContextManager上下文管理器实现（改进版）

## 任务背景

ContextManager是TaskFlow Insight系统的核心管理组件。基于架构评审，本版本重新设计了上下文管理机制，采用**防御性编程**和**强制清理策略**，解决了ThreadLocal内存泄漏风险，并为异步场景预留了扩展能力。

**关键改进**：
- 强制资源管理，防止ThreadLocal泄漏
- 支持异步上下文传递的基础设施
- 主动泄漏检测和自动修复机制
- 线程池安全保证

## 目标

1. 实现全局统一的上下文管理，确保零泄漏
2. 提供线程安全的会话索引和查询功能
3. 实现主动的泄漏检测和修复机制
4. 支持异步任务的上下文传递
5. 提供完善的监控和诊断接口

## 实现方案

### 1. SafeContextManager核心实现

**文件位置**: `src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java`

```java
package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 安全的上下文管理器 - 零泄漏版本
 * 采用防御性编程，确保ThreadLocal不会泄漏
 * 
 * @author TaskFlow Insight Team  
 * @version 1.0.0-improved
 */
public final class SafeContextManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeContextManager.class);
    private static final SafeContextManager INSTANCE = new SafeContextManager();
    
    // 系统状态
    private volatile boolean enabled = true;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // 上下文存储 - 使用InheritableThreadLocal支持父子线程
    private final InheritableThreadLocal<ManagedThreadContext> contextHolder = 
        new InheritableThreadLocal<ManagedThreadContext>() {
            @Override
            protected ManagedThreadContext childValue(ManagedThreadContext parent) {
                // 子线程继承父线程上下文的快照
                if (parent != null && !parent.isClosed()) {
                    LOGGER.debug("Child thread {} inheriting context from parent thread {}", 
                        Thread.currentThread().getId(), parent.getThreadId());
                    // 返回快照，避免并发修改
                    return parent.createSnapshot().restore();
                }
                return null;
            }
        };
    
    // 全局索引
    private final ConcurrentHashMap<String, Session> sessionIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WeakReference<ManagedThreadContext>> threadIndex = new ConcurrentHashMap<>();
    
    // 泄漏检测
    private final Set<Long> leakedThreads = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledExecutorService monitorExecutor;
    
    // 统计信息
    private final AtomicLong totalContextsCreated = new AtomicLong(0);
    private final AtomicLong totalContextsCleaned = new AtomicLong(0);
    private final AtomicLong totalLeaksDetected = new AtomicLong(0);
    private final AtomicLong totalLeaksFixed = new AtomicLong(0);
    
    // 配置
    private volatile long leakDetectionIntervalMs = TimeUnit.MINUTES.toMillis(1);
    private volatile long maxContextAgeMs = TimeUnit.HOURS.toMillis(1);
    
    private SafeContextManager() {
        // 清理执行器
        this.cleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TFI-Context-Cleaner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        
        // 监控执行器
        this.monitorExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TFI-Context-Monitor");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
        
        // 初始化
        initialize();
    }
    
    public static SafeContextManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 初始化管理器
     */
    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            // 启动泄漏检测
            cleanupExecutor.scheduleWithFixedDelay(
                this::detectAndFixLeaks, 
                leakDetectionIntervalMs, 
                leakDetectionIntervalMs, 
                TimeUnit.MILLISECONDS
            );
            
            // 启动监控
            monitorExecutor.scheduleWithFixedDelay(
                this::reportMetrics, 
                TimeUnit.MINUTES.toMillis(5), 
                TimeUnit.MINUTES.toMillis(5), 
                TimeUnit.MILLISECONDS
            );
            
            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "TFI-Context-Shutdown"));
            
            LOGGER.info("SafeContextManager initialized");
        }
    }
    
    /**
     * 获取或创建当前线程的上下文
     * 使用托管模式确保清理
     */
    public ManagedThreadContext getCurrentContext() {
        if (!enabled) {
            // 系统禁用时返回null，调用方需要判空
            return null;
        }
        
        ManagedThreadContext context = contextHolder.get();
        if (context == null || context.isClosed()) {
            context = createNewContext();
        }
        
        return context;
    }
    
    /**
     * 安全获取上下文，系统禁用时返回空操作上下文
     */
    public ManagedThreadContext getCurrentContextSafe() {
        ManagedThreadContext ctx = getCurrentContext();
        return ctx != null ? ctx : NoOpThreadContext.INSTANCE;
    }
    
    /**
     * 创建新的托管上下文
     */
    private ManagedThreadContext createNewContext() {
        long threadId = Thread.currentThread().getId();
        
        // 检查是否有泄漏的旧上下文
        WeakReference<ManagedThreadContext> oldRef = threadIndex.get(threadId);
        if (oldRef != null) {
            ManagedThreadContext old = oldRef.get();
            if (old != null && !old.isClosed()) {
                LOGGER.warn("Detected unclosed context in thread {}, force cleaning", threadId);
                old.close();
                totalLeaksDetected.incrementAndGet();
                totalLeaksFixed.incrementAndGet();
            }
        }
        
        // 创建新上下文
        ManagedThreadContext context = ManagedThreadContext.create();
        contextHolder.set(context);
        threadIndex.put(threadId, new WeakReference<>(context));
        totalContextsCreated.incrementAndGet();
        
        LOGGER.debug("Created new context for thread {}", threadId);
        return context;
    }
    
    /**
     * 使用托管的上下文执行任务
     * 确保自动清理
     */
    public <T> T executeInContext(String taskName, Supplier<T> task) {
        try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
            ctx.startTask(taskName);
            try {
                return task.get();
            } finally {
                ctx.endTask();
            }
        }
    }
    
    /**
     * 异步执行任务，传递上下文
     */
    public CompletableFuture<Void> executeAsync(String taskName, Runnable task) {
        // 捕获当前上下文快照
        ManagedThreadContext current = getCurrentContext();
        ManagedThreadContext.ContextSnapshot snapshot = current.createSnapshot();
        
        return CompletableFuture.runAsync(() -> {
            // 在异步线程中恢复上下文
            try (ManagedThreadContext ctx = snapshot.restore()) {
                ctx.startTask(taskName);
                try {
                    task.run();
                } finally {
                    ctx.endTask();
                }
            }
        });
    }
    
    /**
     * 清理当前线程的上下文
     */
    public void clearCurrentThread() {
        long threadId = Thread.currentThread().getId();
        
        ManagedThreadContext context = contextHolder.get();
        if (context != null) {
            context.close();
            contextHolder.remove();
            totalContextsCleaned.incrementAndGet();
        }
        
        threadIndex.remove(threadId);
        leakedThreads.remove(threadId);
        
        LOGGER.debug("Cleared context for thread {}", threadId);
    }
    
    /**
     * 检测并修复内存泄漏
     */
    private void detectAndFixLeaks() {
        try {
            int leaksFound = 0;
            int leaksFixed = 0;
            
            // 检查所有已知线程
            for (Map.Entry<Long, WeakReference<ManagedThreadContext>> entry : threadIndex.entrySet()) {
                Long threadId = entry.getKey();
                WeakReference<ManagedThreadContext> ref = entry.getValue();
                
                if (ref != null) {
                    ManagedThreadContext context = ref.get();
                    if (context != null && !context.isClosed()) {
                        // 检查线程是否还活着
                        Thread thread = findThread(threadId);
                        if (thread == null || !thread.isAlive()) {
                            // 线程已死但上下文未清理 - 泄漏！
                            LOGGER.warn("Found leaked context for dead thread {}", threadId);
                            context.close();
                            threadIndex.remove(threadId);
                            leakedThreads.add(threadId);
                            leaksFound++;
                            leaksFixed++;
                        } else if (isContextTooOld(context)) {
                            // 上下文太旧，可能是泄漏
                            LOGGER.warn("Found stale context in thread {} (age: {} ms)", 
                                threadId, System.currentTimeMillis() - context.getCreatedAt());
                            leakedThreads.add(threadId);
                            leaksFound++;
                        }
                    }
                }
            }
            
            if (leaksFound > 0) {
                totalLeaksDetected.addAndGet(leaksFound);
                totalLeaksFixed.addAndGet(leaksFixed);
                LOGGER.info("Leak detection: found {} leaks, fixed {}", leaksFound, leaksFixed);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error during leak detection", e);
        }
    }
    
    /**
     * 检查上下文是否太旧
     */
    private boolean isContextTooOld(ManagedThreadContext context) {
        long age = System.currentTimeMillis() - context.getCreatedAt();
        return age > maxContextAgeMs;
    }
    
    /**
     * 查找线程
     */
    private Thread findThread(long threadId) {
        Thread[] threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        
        for (Thread thread : threads) {
            if (thread != null && thread.getId() == threadId) {
                return thread;
            }
        }
        return null;
    }
    
    /**
     * 报告指标
     */
    private void reportMetrics() {
        LOGGER.info("ContextManager Metrics: " +
            "contexts_created={}, contexts_cleaned={}, " +
            "leaks_detected={}, leaks_fixed={}, " +
            "active_sessions={}, active_threads={}",
            totalContextsCreated.get(),
            totalContextsCleaned.get(),
            totalLeaksDetected.get(),
            totalLeaksFixed.get(),
            sessionIndex.size(),
            threadIndex.size()
        );
        
        // 检查异常情况
        if (leakedThreads.size() > 10) {
            LOGGER.error("High number of leaked threads detected: {}", leakedThreads.size());
        }
    }
    
    /**
     * 虚拟线程支持（Java 21+）
     * 注意：InheritableThreadLocal在虚拟线程中可能有限制
     * 推荐使用显式传递或StructuredTaskScope
     */
    public <T> T executeInVirtualThread(String taskName, Callable<T> task) throws Exception {
        // 捕获当前上下文快照
        ManagedThreadContext current = getCurrentContext();
        if (current == null) {
            // 直接执行，无上下文
            return Thread.startVirtualThread(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).get();
        }
        
        ManagedThreadContext.ContextSnapshot snapshot = current.createSnapshot();
        
        // 在虚拟线程中执行，显式传递上下文
        return Thread.startVirtualThread(() -> {
            try (ManagedThreadContext ctx = snapshot.restore()) {
                ctx.startTask(taskName);
                try {
                    return task.call();
                } finally {
                    ctx.endTask();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get();
    }
    
    /**
     * 使用StructuredTaskScope执行结构化并发任务（Java 21+）
     * 这是虚拟线程的推荐使用方式
     */
    public <T> List<T> executeStructuredTasks(String scopeName, List<Callable<T>> tasks) 
            throws InterruptedException, ExecutionException {
        ManagedThreadContext current = getCurrentContext();
        ManagedThreadContext.ContextSnapshot snapshot = 
            current != null ? current.createSnapshot() : null;
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Future<T>> futures = new ArrayList<>();
            
            for (Callable<T> task : tasks) {
                Future<T> future = scope.fork(() -> {
                    // 在每个子任务中恢复上下文
                    if (snapshot != null) {
                        try (ManagedThreadContext ctx = snapshot.restore()) {
                            return task.call();
                        }
                    } else {
                        return task.call();
                    }
                });
                futures.add(future);
            }
            
            scope.join();           // 等待所有任务完成
            scope.throwIfFailed();  // 如果有任务失败则抛出异常
            
            // 收集结果
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.resultNow());
            }
            return results;
        }
    }
    
    /**
     * 关闭管理器
     */
    private void shutdown() {
        LOGGER.info("Shutting down SafeContextManager");
        
        enabled = false;
        
        // 清理所有上下文
        for (WeakReference<ManagedThreadContext> ref : threadIndex.values()) {
            ManagedThreadContext context = ref.get();
            if (context != null && !context.isClosed()) {
                context.close();
            }
        }
        
        // 关闭执行器
        cleanupExecutor.shutdown();
        monitorExecutor.shutdown();
        
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 最终报告
        reportMetrics();
        
        LOGGER.info("SafeContextManager shutdown complete");
    }
    
    // === 公共API ===
    
    /**
     * 注册会话
     */
    public void registerSession(Session session) {
        if (session != null) {
            sessionIndex.put(session.getSessionId(), session);
        }
    }
    
    /**
     * 注销会话
     */
    public void unregisterSession(String sessionId) {
        sessionIndex.remove(sessionId);
    }
    
    /**
     * 获取会话
     */
    public Session getSession(String sessionId) {
        return sessionIndex.get(sessionId);
    }
    
    /**
     * 获取所有活动会话
     */
    public Collection<Session> getActiveSessions() {
        return new ArrayList<>(sessionIndex.values());
    }
    
    // === 监控和诊断 ===
    
    public long getTotalContextsCreated() { return totalContextsCreated.get(); }
    public long getTotalContextsCleaned() { return totalContextsCleaned.get(); }
    public long getTotalLeaksDetected() { return totalLeaksDetected.get(); }
    public long getTotalLeaksFixed() { return totalLeaksFixed.get(); }
    public int getActiveSessionCount() { return sessionIndex.size(); }
    public int getActiveThreadCount() { return threadIndex.size(); }
    public Set<Long> getLeakedThreads() { return new HashSet<>(leakedThreads); }
}

/**
 * 空操作上下文，用于系统禁用时
 */
class NoOpThreadContext extends ManagedThreadContext {
    static final NoOpThreadContext INSTANCE = new NoOpThreadContext();
    
    private NoOpThreadContext() {
        super();
    }
    
    @Override
    public Session startSession() {
        // 空操作
        return null;
    }
    
    @Override
    public TaskNode startTask(String taskName) {
        // 空操作
        return null;
    }
    
    @Override
    public void close() {
        // 空操作，无需清理
    }
}
```

### 2. 线程池集成

```java
package com.syy.taskflowinsight.context;

import java.util.concurrent.*;

/**
 * TFI感知的线程池
 * 自动管理上下文传递和清理
 */
public class TFIAwareThreadPool extends ThreadPoolExecutor {
    
    public TFIAwareThreadPool(int corePoolSize, int maximumPoolSize,
                             long keepAliveTime, TimeUnit unit,
                             BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
              new TFIThreadFactory(), new TFIRejectedExecutionHandler());
    }
    
    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(new ContextAwareRunnable(task));
    }
    
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(new ContextAwareCallable<>(task));
    }
    
    /**
     * 上下文感知的Runnable
     */
    private static class ContextAwareRunnable implements Runnable {
        private final Runnable delegate;
        private final ManagedThreadContext.ContextSnapshot snapshot;
        
        ContextAwareRunnable(Runnable delegate) {
            this.delegate = delegate;
            // 捕获提交时的上下文
            ManagedThreadContext current = SafeContextManager.getInstance().getCurrentContext();
            this.snapshot = current.createSnapshot();
        }
        
        @Override
        public void run() {
            // 在工作线程中恢复上下文
            try (ManagedThreadContext ctx = snapshot.restore()) {
                delegate.run();
            }
        }
    }
    
    /**
     * 上下文感知的Callable
     */
    private static class ContextAwareCallable<V> implements Callable<V> {
        private final Callable<V> delegate;
        private final ManagedThreadContext.ContextSnapshot snapshot;
        
        ContextAwareCallable(Callable<V> delegate) {
            this.delegate = delegate;
            ManagedThreadContext current = SafeContextManager.getInstance().getCurrentContext();
            this.snapshot = current.createSnapshot();
        }
        
        @Override
        public V call() throws Exception {
            try (ManagedThreadContext ctx = snapshot.restore()) {
                return delegate.call();
            }
        }
    }
    
    /**
     * TFI线程工厂
     */
    private static class TFIThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "TFI-Worker-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
    
    /**
     * TFI拒绝策略
     */
    private static class TFIRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            LOGGER.error("Task rejected by TFI thread pool: {}", r);
            throw new RejectedExecutionException("Task rejected: " + r);
        }
    }
}
```

### 3. InheritableThreadLocal边界说明

```java
/**
 * InheritableThreadLocal的局限性和最佳实践
 */
public class ThreadLocalBoundaries {
    
    /**
     * InheritableThreadLocal适用场景：
     * 1. 父子线程直接创建（new Thread()）
     * 2. 简单的一对一继承关系
     */
    public void goodCase() {
        // 适用：直接创建子线程
        Thread child = new Thread(() -> {
            // 可以继承父线程的上下文
        });
        child.start();
    }
    
    /**
     * InheritableThreadLocal不适用场景：
     * 1. 线程池（线程重用）
     * 2. ForkJoinPool（工作窃取）
     * 3. 虚拟线程（载体线程切换）
     */
    public void badCase() {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        pool.submit(() -> {
            // 无法继承！线程池中的线程早已创建
        });
    }
    
    /**
     * 推荐方案：显式传递 + 装饰器模式
     */
    public void recommendedApproach() {
        // 1. 使用ContextAwareRunnable包装
        ExecutorService pool = new TFIAwareThreadPool(...);
        pool.submit(new ContextAwareRunnable(task));
        
        // 2. 使用快照显式传递
        ContextSnapshot snapshot = getCurrentContext().createSnapshot();
        pool.submit(() -> {
            try (ManagedThreadContext ctx = snapshot.restore()) {
                // 执行任务
            }
        });
        
        // 3. 使用第三方库（如micrometer-context-propagation）
        // 提供更完善的上下文传播机制
    }
}
```

## 测试标准

### 内存安全测试
- [ ] ThreadLocal 100%清理验证
- [ ] 泄漏检测机制有效性测试
- [ ] 线程池场景无泄漏测试
- [ ] 长时间运行稳定性测试

### 功能测试
- [ ] 上下文创建和管理
- [ ] 会话索引和查询
- [ ] 异步任务上下文传递
- [ ] 父子线程上下文继承

### 性能测试
- [ ] 上下文操作 < 1微秒
- [ ] 泄漏检测开销 < 1%
- [ ] 内存占用 < 5MB (1000线程)

## 验收标准

### MVP必需
- [ ] **零内存泄漏保证**
- [ ] **自动泄漏检测和修复**
- [ ] **线程池安全集成**
- [ ] **基础异步支持**

### 监控要求
- [ ] 实时泄漏报告
- [ ] 性能指标监控
- [ ] 异常情况告警

---

**重要**: 此版本彻底解决了ThreadLocal内存泄漏问题，通过主动检测、自动修复和强制管理，确保生产环境的稳定性和可靠性。