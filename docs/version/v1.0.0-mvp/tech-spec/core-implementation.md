# 核心实现技术规格 v1.0.0-MVP

## 概述

本文档定义 TaskFlow Insight MVP 版本的核心技术实现规格，为高级开发工程师提供详细的实现指导。

## 核心类设计

### 1. TFI 主入口类

```java
package com.syy.taskflowinsight.api;

/**
 * TaskFlow Insight 主入口类
 * 提供静态方法访问核心功能
 * 
 * 线程安全：是
 * 性能要求：每次调用 < 1微秒 (不含业务逻辑)
 */
public final class TFI {
    
    // 禁止实例化
    private TFI() {}
    
    // ==================== 任务管理 ====================
    
    /**
     * 开始一个新任务
     * @param taskName 任务名称，非空
     * @return 任务上下文，用于后续操作
     * @throws IllegalArgumentException 如果 taskName 为空
     */
    public static TaskContext start(String taskName);
    
    /**
     * 结束当前任务
     * 必须与 start() 配对使用
     * @throws IllegalStateException 如果没有活动任务
     */
    public static void stop();
    
    // ==================== 消息记录 ====================
    
    /**
     * 记录信息消息
     * @param message 消息内容
     */
    public static void message(String message);
    
    /**
     * 记录异常信息
     * @param throwable 异常对象
     */
    public static void recordException(Throwable throwable);
    
    // ==================== 状态查询 ====================
    
    /**
     * 检查是否有活动任务
     * @return true 如果有活动任务
     */
    public static boolean isActive();
    
    /**
     * 获取当前任务上下文
     * @return 当前任务上下文，如果没有则返回 null
     */
    public static TaskContext getCurrentTask();
    
    /**
     * 获取当前会话
     * @return 当前会话，如果没有则返回 null
     */
    public static Session getCurrentSession();
    
    // ==================== 输出功能 ====================
    
    /**
     * 打印当前会话的任务树到控制台
     */
    public static void printTree();
    
    /**
     * 导出当前会话为 JSON 字符串
     * @return JSON 字符串
     */
    public static String exportJson();
    
    // ==================== 系统控制 ====================
    
    /**
     * 启用 TFI (默认已启用)
     */
    public static void enable();
    
    /**
     * 禁用 TFI
     * 禁用后所有操作变为空操作，不影响性能
     */
    public static void disable();
    
    /**
     * 检查 TFI 是否启用
     * @return true 如果启用
     */
    public static boolean isEnabled();
    
    /**
     * 清理当前线程的 ThreadLocal 数据
     * 注意：仅在线程结束或线程池环境下调用
     */
    public static void clearThreadLocal();
}
```

### 2. 核心数据模型

#### Session (会话)

```java
package com.syy.taskflowinsight.model;

/**
 * 任务会话，表示一个完整的任务执行上下文
 * 从根任务开始到结束的完整执行树
 */
public final class Session {
    private final String sessionId;        // UUID
    private final long threadId;           // 线程ID
    private final long createdAt;          // 创建时间(ms)
    private volatile long endedAt;         // 结束时间(ms), 0表示进行中
    private TaskNode root;                 // 根任务节点
    private volatile SessionStatus status; // 会话状态
    
    // 构造函数
    public Session(long threadId) {
        this.sessionId = UUID.randomUUID().toString();
        this.threadId = threadId;
        this.createdAt = System.currentTimeMillis();
        this.endedAt = 0L;
        this.status = SessionStatus.RUNNING;
    }
    
    // Getter方法
    public String getSessionId() { return sessionId; }
    public long getThreadId() { return threadId; }
    public long getCreatedAt() { return createdAt; }
    public long getEndedAt() { return endedAt; }
    public TaskNode getRoot() { return root; }
    public SessionStatus getStatus() { return status; }
    
    // 业务方法
    public void setRoot(TaskNode root) { this.root = root; }
    public void end() {
        this.endedAt = System.currentTimeMillis();
        this.status = SessionStatus.COMPLETED;
    }
    
    public boolean isActive() { return endedAt == 0L; }
    public long getDurationMs() {
        return isActive() ? 
            (System.currentTimeMillis() - createdAt) : 
            (endedAt - createdAt);
    }
}

enum SessionStatus {
    RUNNING, COMPLETED, ERROR
}
```

#### TaskNode (任务节点)

```java
package com.syy.taskflowinsight.model;

/**
 * 任务节点，表示任务树中的单个任务
 * 线程安全：写操作仅在单线程内，读操作使用 volatile 保证可见性
 */
public final class TaskNode {
    // 基础标识
    private final String nodeId;           // 节点ID (UUID)
    private final String name;             // 任务名称
    private final int depth;               // 嵌套深度 (0-based)
    
    // 层次关系
    private TaskNode parent;               // 父节点
    private final List<TaskNode> children; // 子节点列表
    
    // 时间信息 (高精度)
    private final long startNano;          // 开始时间(纳秒)
    private final long startMillis;        // 开始时间(毫秒，用于展示)
    private volatile long endNano;         // 结束时间(纳秒), 0表示进行中
    private volatile long endMillis;       // 结束时间(毫秒)
    
    // 状态信息
    private volatile TaskStatus status;    // 任务状态
    
    // 消息集合
    private final List<Message> messages;  // 消息列表 (线程安全)
    
    // 构造函数
    public TaskNode(String name, int depth) {
        this.nodeId = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.depth = depth;
        this.startNano = System.nanoTime();
        this.startMillis = System.currentTimeMillis();
        this.endNano = 0L;
        this.endMillis = 0L;
        this.status = TaskStatus.RUNNING;
        this.children = new ArrayList<>();
        this.messages = Collections.synchronizedList(new ArrayList<>());
    }
    
    // 业务方法
    public void stop() {
        this.endNano = System.nanoTime();
        this.endMillis = System.currentTimeMillis();
        this.status = TaskStatus.COMPLETED;
    }
    
    public void addChild(TaskNode child) {
        child.parent = this;
        this.children.add(child);
    }
    
    public void addMessage(Message message) {
        this.messages.add(message);
    }
    
    // 时间计算
    public long getSelfDurationNs() {
        return endNano > 0 ? (endNano - startNano) : 
                           (System.nanoTime() - startNano);
    }
    
    public long getSelfDurationMs() {
        return getSelfDurationNs() / 1_000_000;
    }
    
    public long getAccDurationMs() {
        long selfMs = getSelfDurationMs();
        long childrenMs = children.stream()
                                 .mapToLong(TaskNode::getAccDurationMs)
                                 .sum();
        return selfMs + childrenMs;
    }
    
    // Getter方法
    public String getNodeId() { return nodeId; }
    public String getName() { return name; }
    public int getDepth() { return depth; }
    public TaskNode getParent() { return parent; }
    public List<TaskNode> getChildren() { return Collections.unmodifiableList(children); }
    public long getStartMillis() { return startMillis; }
    public long getEndMillis() { return endMillis; }
    public TaskStatus getStatus() { return status; }
    public List<Message> getMessages() { return Collections.unmodifiableList(messages); }
    
    public boolean isActive() { return endNano == 0L; }
    public boolean isRoot() { return parent == null; }
    public boolean isLeaf() { return children.isEmpty(); }
}

enum TaskStatus {
    RUNNING, COMPLETED, FAILED
}
```

#### Message (消息)

```java
package com.syy.taskflowinsight.model;

/**
 * 任务消息，记录任务执行过程中的信息
 */
public final class Message {
    private final MessageType type;        // 消息类型
    private final String content;          // 消息内容
    private final long timestamp;          // 时间戳(毫秒)
    
    public Message(MessageType type, String content) {
        this.type = Objects.requireNonNull(type);
        this.content = Objects.requireNonNull(content);
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getter方法
    public MessageType getType() { return type; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}

enum MessageType {
    INFO,        // 信息消息
    EXCEPTION    // 异常消息
}
```

### 3. 核心管理器

#### ContextManager (上下文管理器)

```java
package com.syy.taskflowinsight.core;

/**
 * 上下文管理器
 * 管理 ThreadLocal 上下文和任务栈
 * 
 * 线程安全：是 (基于 ThreadLocal)
 */
@Component
public class ContextManager {
    
    // ThreadLocal 上下文
    private final ThreadLocal<ThreadContext> contextHolder = new ThreadLocal<>();
    
    // 全局会话索引 (跨线程访问)
    private final ConcurrentHashMap<String, Session> sessionIndex = new ConcurrentHashMap<>();
    
    // 配置参数
    private static final int MAX_DEPTH = 100;
    private static final int MAX_MESSAGES_PER_TASK = 1000;
    
    /**
     * 获取当前线程上下文
     */
    public ThreadContext getCurrentContext() {
        ThreadContext context = contextHolder.get();
        if (context == null) {
            context = new ThreadContext();
            contextHolder.set(context);
        }
        return context;
    }
    
    /**
     * 开始新任务
     */
    public TaskContext startTask(String taskName) {
        ThreadContext context = getCurrentContext();
        
        // 检查深度限制
        if (context.getDepth() >= MAX_DEPTH) {
            throw new IllegalStateException("Maximum task depth exceeded: " + MAX_DEPTH);
        }
        
        // 创建任务节点
        TaskNode node = new TaskNode(taskName, context.getDepth());
        
        // 建立父子关系
        TaskNode parent = context.getCurrentTask();
        if (parent != null) {
            parent.addChild(node);
        } else {
            // 根任务，创建新会话
            Session session = new Session(Thread.currentThread().getId());
            session.setRoot(node);
            context.setCurrentSession(session);
            sessionIndex.put(session.getSessionId(), session);
        }
        
        // 压入任务栈
        context.pushTask(node);
        
        return new TaskContext(node);
    }
    
    /**
     * 结束当前任务
     */
    public void stopTask() {
        ThreadContext context = getCurrentContext();
        
        if (context.isEmpty()) {
            throw new IllegalStateException("No active task to stop");
        }
        
        // 弹出任务栈
        TaskNode node = context.popTask();
        node.stop();
        
        // 如果是根任务，结束会话
        if (context.isEmpty()) {
            Session session = context.getCurrentSession();
            if (session != null) {
                session.end();
                context.setCurrentSession(null);
            }
        }
    }
    
    /**
     * 获取会话
     */
    public Session getSession(String sessionId) {
        return sessionIndex.get(sessionId);
    }
    
    /**
     * 清理 ThreadLocal
     */
    public void clearThreadLocal() {
        contextHolder.remove();
    }
    
    /**
     * 获取当前任务
     */
    public TaskNode getCurrentTask() {
        ThreadContext context = contextHolder.get();
        return context != null ? context.getCurrentTask() : null;
    }
    
    /**
     * 获取当前会话
     */
    public Session getCurrentSession() {
        ThreadContext context = contextHolder.get();
        return context != null ? context.getCurrentSession() : null;
    }
    
    /**
     * 检查是否有活动任务
     */
    public boolean isActive() {
        ThreadContext context = contextHolder.get();
        return context != null && !context.isEmpty();
    }
}
```

#### ThreadContext (线程上下文)

```java
package com.syy.taskflowinsight.core;

/**
 * 线程上下文
 * 每个线程的私有数据，包含任务栈和当前会话
 * 
 * 线程安全：否 (仅在单线程内使用)
 */
class ThreadContext {
    
    // 任务栈 (LIFO)
    private final Deque<TaskNode> taskStack = new ArrayDeque<>();
    
    // 当前会话
    private Session currentSession;
    
    /**
     * 压入任务到栈顶
     */
    public void pushTask(TaskNode task) {
        taskStack.push(task);
    }
    
    /**
     * 弹出栈顶任务
     */
    public TaskNode popTask() {
        if (taskStack.isEmpty()) {
            throw new IllegalStateException("Task stack is empty");
        }
        return taskStack.pop();
    }
    
    /**
     * 获取当前任务 (栈顶)
     */
    public TaskNode getCurrentTask() {
        return taskStack.isEmpty() ? null : taskStack.peek();
    }
    
    /**
     * 获取当前深度
     */
    public int getDepth() {
        return taskStack.size();
    }
    
    /**
     * 检查栈是否为空
     */
    public boolean isEmpty() {
        return taskStack.isEmpty();
    }
    
    /**
     * 获取/设置当前会话
     */
    public Session getCurrentSession() {
        return currentSession;
    }
    
    public void setCurrentSession(Session session) {
        this.currentSession = session;
    }
    
    /**
     * 清理上下文
     */
    public void clear() {
        taskStack.clear();
        currentSession = null;
    }
}
```

### 4. 任务上下文接口

```java
package com.syy.taskflowinsight.api;

/**
 * 任务上下文接口
 * 提供任务级别的操作方法
 */
public final class TaskContext {
    private final TaskNode taskNode;
    
    TaskContext(TaskNode taskNode) {
        this.taskNode = Objects.requireNonNull(taskNode);
    }
    
    /**
     * 添加信息消息
     */
    public TaskContext message(String content) {
        taskNode.addMessage(new Message(MessageType.INFO, content));
        return this;
    }
    
    /**
     * 记录异常
     */
    public TaskContext recordException(Throwable throwable) {
        String content = throwable.getClass().getSimpleName() + ": " + 
                        throwable.getMessage();
        taskNode.addMessage(new Message(MessageType.EXCEPTION, content));
        return this;
    }
    
    /**
     * 获取任务名称
     */
    public String getTaskName() {
        return taskNode.getName();
    }
    
    /**
     * 获取任务ID
     */
    public String getTaskId() {
        return taskNode.getNodeId();
    }
    
    /**
     * 获取当前持续时间 (毫秒)
     */
    public long getDurationMs() {
        return taskNode.getSelfDurationMs();
    }
    
    /**
     * 检查任务是否还在运行
     */
    public boolean isActive() {
        return taskNode.isActive();
    }
}
```

## 实现要点

### 1. 性能优化策略

```java
/**
 * 性能优化关键点
 */
public class PerformanceOptimizations {
    
    // 1. 使用对象池减少 GC 压力
    private static final ThreadLocal<Deque<TaskNode>> NODE_POOL = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(16));
    
    // 2. 延迟字符串拼接
    public void addMessage(Supplier<String> messageSupplier) {
        if (TFI.isEnabled()) {
            addMessage(messageSupplier.get());
        }
    }
    
    // 3. 预分配集合大小
    public TaskNode(String name, int depth) {
        this.children = new ArrayList<>(8);  // 预期的子任务数
        this.messages = new ArrayList<>(4);  // 预期的消息数
    }
    
    // 4. 使用原始类型避免装箱
    private long startNano;  // 而不是 Long
    private int depth;       // 而不是 Integer
}
```

### 2. 内存管理策略

```java
/**
 * 内存管理关键点
 */
public class MemoryManagement {
    
    // 1. 弱引用避免内存泄漏
    private final Map<String, WeakReference<Session>> sessionCache = 
        new ConcurrentHashMap<>();
    
    // 2. 限制消息数量
    private static final int MAX_MESSAGES = 1000;
    
    public void addMessage(Message message) {
        if (messages.size() >= MAX_MESSAGES) {
            messages.remove(0); // 移除最旧的消息
        }
        messages.add(message);
    }
    
    // 3. 主动清理
    public void clearThreadLocal() {
        ThreadContext context = contextHolder.get();
        if (context != null) {
            context.clear();
            contextHolder.remove();
        }
    }
}
```

### 3. 线程安全策略

```java
/**
 * 线程安全关键点
 */
public class ThreadSafety {
    
    // 1. 写操作完全在 ThreadLocal 内
    // 无需同步，天然线程安全
    
    // 2. 读操作使用 volatile 保证可见性
    private volatile long endNano;
    private volatile TaskStatus status;
    
    // 3. 集合使用线程安全包装
    private final List<Message> messages = 
        Collections.synchronizedList(new ArrayList<>());
    
    // 4. 全局索引使用并发容器
    private final ConcurrentHashMap<String, Session> sessionIndex = 
        new ConcurrentHashMap<>();
}
```

### 4. 异常安全策略

```java
/**
 * 异常安全关键点
 */
public class ExceptionSafety {
    
    public static void stop() {
        try {
            contextManager.stopTask();
        } catch (Exception e) {
            // 记录内部错误，但不影响业务
            logger.warn("TFI internal error during stop(): {}", e.getMessage());
            // 不重新抛出异常
        }
    }
    
    public static void message(String content) {
        if (!isEnabled()) {
            return; // 快速返回，零开销
        }
        
        try {
            TaskNode current = getCurrentTask();
            if (current != null) {
                current.addMessage(new Message(MessageType.INFO, content));
            }
        } catch (Exception e) {
            // 静默失败，不影响业务
            logger.debug("Failed to add message: {}", e.getMessage());
        }
    }
}
```

## 验收测试用例

### 1. 功能测试

```java
@Test
public void testBasicTaskTracking() {
    // 开始任务
    TaskContext task = TFI.start("testTask");
    assertNotNull(task);
    assertTrue(TFI.isActive());
    
    // 添加消息
    task.message("test message");
    
    // 结束任务
    TFI.stop();
    assertFalse(TFI.isActive());
    
    // 验证会话
    Session session = TFI.getCurrentSession();
    assertNull(session); // 已结束
}
```

### 2. 性能测试

```java
@Test
public void testPerformanceOverhead() {
    int iterations = 10000;
    
    // 基准测试：无追踪
    long baselineStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        businessMethod();
    }
    long baselineTime = System.nanoTime() - baselineStart;
    
    // 对比测试：有追踪
    long trackedStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        TFI.start("businessMethod");
        businessMethod();
        TFI.stop();
    }
    long trackedTime = System.nanoTime() - trackedStart;
    
    // 验证开销 < 5%
    double overhead = (double)(trackedTime - baselineTime) / baselineTime;
    assertTrue("Overhead too high: " + overhead, overhead < 0.05);
}
```

### 3. 并发测试

```java
@Test
public void testConcurrentSafety() throws InterruptedException {
    int threadCount = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger errorCount = new AtomicInteger(0);
    
    for (int i = 0; i < threadCount; i++) {
        final int threadId = i;
        executor.submit(() -> {
            try {
                TFI.start("thread-" + threadId);
                Thread.sleep(10);
                TFI.message("thread message");
                TFI.stop();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await(10, TimeUnit.SECONDS);
    assertEquals("Concurrent errors detected", 0, errorCount.get());
}
```

## 实现检查清单

### 核心功能
- [ ] TFI 主入口类实现
- [ ] Session/TaskNode/Message 数据模型
- [ ] ContextManager 上下文管理
- [ ] ThreadContext 线程上下文
- [ ] TaskContext 任务上下文接口

### 性能优化
- [ ] 对象池化机制
- [ ] 延迟计算策略
- [ ] 内存使用优化
- [ ] CPU 开销控制

### 质量保证
- [ ] 单元测试 (>80% 覆盖率)
- [ ] 性能基准测试
- [ ] 并发安全测试
- [ ] 内存泄漏测试

### 输出功能
- [ ] 控制台树形输出
- [ ] JSON 序列化支持
- [ ] 基础统计信息

这个技术规格为高级开发工程师提供了详细的实现指导，确保 MVP 版本的核心功能能够高质量交付。