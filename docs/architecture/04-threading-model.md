# 多线程设计

## 线程模型概述

TFI 采用线程隔离的设计模式，确保多线程环境下的数据安全和性能。核心原则是"写操作线程隔离，读操作跨线程安全"。

## 线程隔离策略

### ThreadLocal 上下文

每个线程维护独立的执行上下文，互不干扰：

```java
public class ThreadContext {
    // 任务执行栈（线程私有，无需同步）
    private final Deque<TaskNode> taskStack = new ArrayDeque<>();
    
    // 当前会话（线程私有）
    private Session currentSession;
    
    // 最近会话历史（线程私有，环形缓存）
    private final Deque<Session> recentSessions = new ArrayDeque<>();
    
    // 配置参数
    private final int maxSessionsPerThread = 10;
    private final int maxTaskDepth = 100;
}
```

### 全局上下文管理器

```java
@Component
public class ContextManager {
    // 线程上下文存储
    private final ThreadLocal<ThreadContext> contextHolder = new ThreadLocal<>();
    
    // 全局会话索引（支持跨线程访问）
    private final ConcurrentMap<Long, Deque<Session>> allSessions = new ConcurrentHashMap<>();
    
    // 会话ID索引（快速查找）
    private final ConcurrentMap<String, Session> sessionIndex = new ConcurrentHashMap<>();
    
    public ThreadContext getCurrentContext() {
        ThreadContext context = contextHolder.get();
        if (context == null) {
            context = new ThreadContext();
            contextHolder.set(context);
        }
        return context;
    }
    
    public void clearContext() {
        ThreadContext context = contextHolder.get();
        if (context != null) {
            // 清理资源
            context.clear();
            contextHolder.remove();
        }
    }
}
```

## 并发读写策略

### COW (Copy-On-Write) 策略

会话结束时创建不可变快照，支持无锁并发读取：

```java
public class Session {
    private volatile boolean frozen = false;
    private TaskNode root;
    private TaskNode frozenRoot; // 不可变快照
    
    // 会话结束时冻结为不可变状态
    public synchronized void freeze() {
        if (!frozen) {
            this.frozenRoot = createImmutableCopy(root);
            this.frozen = true;
            this.endedAt = System.currentTimeMillis();
        }
    }
    
    // 跨线程读取时返回不可变快照
    public TaskNode getRoot() {
        return frozen ? frozenRoot : createRuntimeSnapshot(root);
    }
    
    // 创建运行时快照（有限深度/节点数）
    private TaskNode createRuntimeSnapshot(TaskNode node) {
        // 轻量级快照，避免长时间持锁
        return node.createSnapshot(maxDepth, maxNodes);
    }
}
```

### 不可变快照创建

```java
public class ImmutableTaskNode {
    // 所有字段都是 final，确保不可变性
    private final String nodeId;
    private final String name;
    private final long startMillis;
    private final long endMillis;
    private final long selfDurationNs;
    private final long accDurationNs;
    private final TaskStatus status;
    private final List<Message> messages;           // 不可变列表
    private final List<ImmutableTaskNode> children; // 不可变子节点
    
    // 递归创建不可变副本
    public static ImmutableTaskNode from(TaskNode node) {
        return new ImmutableTaskNode(
            node.getNodeId(),
            node.getName(),
            node.getStartMillis(),
            node.getEndMillis(),
            node.getSelfDurationNs(),
            node.getAccDurationNs(),
            node.getStatus(),
            List.copyOf(node.getMessages()),
            node.getChildren().stream()
                .map(ImmutableTaskNode::from)
                .collect(toUnmodifiableList())
        );
    }
}
```

## 线程安全保证

### 写操作安全

```java
public class TaskManager {
    // 写操作完全在单线程内，无需同步
    public TaskNode startTask(String name) {
        ThreadContext context = getCurrentContext();
        TaskNode node = new TaskNode(name);
        
        // 栈操作线程安全（ThreadLocal）
        context.getTaskStack().push(node);
        
        // 建立父子关系
        if (!context.getTaskStack().isEmpty()) {
            TaskNode parent = context.getTaskStack().peek();
            parent.addChild(node);
        }
        
        return node;
    }
    
    public void stopTask() {
        ThreadContext context = getCurrentContext();
        if (context.getTaskStack().isEmpty()) {
            log.warn("No active task to stop");
            return;
        }
        
        TaskNode node = context.getTaskStack().pop();
        node.stop();
        
        // 根任务完成，结束会话
        if (context.getTaskStack().isEmpty()) {
            endSession(context.getCurrentSession());
        }
    }
}
```

### 读操作安全

```java
public class SessionQueryService {
    // 跨线程读取使用不可变快照，无需同步
    public Session getSession(String sessionId) {
        Session session = sessionIndex.get(sessionId);
        return session != null && session.isFrozen() ? session : null;
    }
    
    // 获取线程的最近会话
    public List<Session> getRecentSessions(long threadId, int limit) {
        Deque<Session> sessions = allSessions.get(threadId);
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        
        return sessions.stream()
                      .filter(Session::isFrozen)  // 只返回已完成的会话
                      .limit(limit)
                      .collect(toList());
    }
}
```

## 线程池兼容性

### ThreadLocal 清理

确保在线程池环境下正确清理 ThreadLocal：

```java
public class TaskExecutor {
    public <T> T executeWithTracking(String taskName, Supplier<T> supplier) {
        TFI.start(taskName);
        try {
            return supplier.get();
        } catch (Exception e) {
            TFI.recordException(e);
            throw e;
        } finally {
            TFI.stop();
            
            // 检查是否为根任务完成
            if (isRootTaskCompleted()) {
                // 清理ThreadLocal，避免内存泄漏
                ContextManager.clearContext();
            }
        }
    }
}
```

### 异步任务传播

支持异步任务的上下文传播：

```java
public class AsyncTaskSupport {
    @Async
    public CompletableFuture<Void> processAsync(String taskName, Runnable task) {
        // 捕获当前线程的会话信息
        Session parentSession = TFI.getCurrentSession();
        String parentTaskPath = TFI.getCurrentTaskPath();
        
        return CompletableFuture.runAsync(() -> {
            // 在异步线程中创建子会话
            TFI.startChildSession(parentSession.getSessionId(), parentTaskPath);
            TFI.start(taskName);
            
            try {
                task.run();
            } finally {
                TFI.stop();
                TFI.endSession();
            }
        });
    }
}
```

## 内存一致性

### 可见性保证

使用 volatile 确保跨线程可见性：

```java
public class TaskNode {
    // 使用 volatile 确保跨线程可见性
    private volatile long endNano;
    private volatile TaskStatus status;
    private volatile String errorMessage;
    
    // 原子操作保证一致性
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicLong accDurationNs = new AtomicLong(0);
}
```

### 发布安全

确保对象发布的安全性：

```java
public class SessionManager {
    // 会话完成后安全发布到全局索引
    public void endSession(Session session) {
        // 1. 先冻结会话（创建不可变快照）
        session.freeze();
        
        // 2. 再发布到全局索引（写入 volatile 字段）
        sessionIndex.put(session.getSessionId(), session);
        
        // 3. 加入线程历史
        allSessions.computeIfAbsent(session.getThreadId(), 
                                  k -> new ConcurrentLinkedDeque<>())
                   .addFirst(session);
        
        // 4. 维护大小限制
        trimSessionHistory(session.getThreadId());
    }
}
```

## 死锁预防

### 锁排序

避免死锁的锁获取顺序：

```java
public class LockOrdering {
    // 使用一致的锁排序避免死锁
    public void transferContext(long fromThreadId, long toThreadId) {
        // 按线程ID排序获取锁，避免死锁
        long firstId = Math.min(fromThreadId, toThreadId);
        long secondId = Math.max(fromThreadId, toThreadId);
        
        synchronized(getLock(firstId)) {
            synchronized(getLock(secondId)) {
                // 执行转移操作
                doTransfer(fromThreadId, toThreadId);
            }
        }
    }
}
```

### 超时机制

使用超时避免无限等待：

```java
public class TimeoutSupport {
    private final ReentrantLock lock = new ReentrantLock();
    
    public boolean tryOperation(Duration timeout) throws InterruptedException {
        if (lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            try {
                // 执行需要同步的操作
                return performOperation();
            } finally {
                lock.unlock();
            }
        } else {
            log.warn("Failed to acquire lock within timeout: {}", timeout);
            return false;
        }
    }
}
```

## 性能监控

### 线程竞争检测

```java
public class ContentionMonitor {
    private final ConcurrentMap<String, AtomicLong> contentionCounters = new ConcurrentHashMap<>();
    
    public void recordContention(String operation) {
        contentionCounters.computeIfAbsent(operation, k -> new AtomicLong(0))
                         .incrementAndGet();
    }
    
    @Scheduled(fixedRate = 60000) // 每分钟输出一次
    public void reportContentionStats() {
        contentionCounters.forEach((operation, count) -> {
            if (count.get() > 0) {
                log.info("Contention detected - Operation: {}, Count: {}", 
                        operation, count.getAndSet(0));
            }
        });
    }
}
```

### 内存使用监控

```java
public class MemoryMonitor {
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    
    public void checkMemoryUsage() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        
        double usagePercent = (double) used / max * 100;
        if (usagePercent > 80) {
            log.warn("High memory usage detected: {}%", usagePercent);
            // 触发清理或采样降级
            triggerCleanup();
        }
    }
}
```

## 最佳实践

### 1. 避免共享可变状态
```java
// 好的做法：使用不可变对象
public class ImmutableMessage {
    private final String content;
    private final long timestamp;
    
    // 构造函数和getter方法...
}

// 避免：共享可变对象
public class MutableMessage {
    private String content;  // 可能被多线程修改
    private long timestamp;  // 线程安全风险
}
```

### 2. 合理使用ThreadLocal
```java
// 好的做法：及时清理
public void processRequest() {
    try {
        TFI.start("processRequest");
        // 业务处理
    } finally {
        TFI.stop();
        if (isRootTask()) {
            TFI.clearThreadLocal(); // 清理ThreadLocal
        }
    }
}
```

### 3. 异步处理非关键操作
```java
// 好的做法：异步导出，不阻塞主流程
@Async
public void exportSession(Session session) {
    // 耗时的导出操作
    String json = JsonExporter.export(session);
    FileUtils.writeToFile(json, getOutputFile(session));
}
```

### 4. 使用合适的并发容器
```java
// 读多写少场景：CopyOnWriteArrayList
private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();

// 高并发读写：ConcurrentHashMap
private final ConcurrentMap<String, Session> sessionIndex = new ConcurrentHashMap<>();

// 单线程访问：ArrayList
private final List<TaskNode> children = new ArrayList<>(); // ThreadLocal中使用
```