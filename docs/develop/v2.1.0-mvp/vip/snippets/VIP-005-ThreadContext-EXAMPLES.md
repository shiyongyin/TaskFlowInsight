# VIP-005 ThreadContext 示例汇总（由正文迁移）

## ManagedThreadContext 与传播示例（原文代码块）
```java
// ManagedThreadContext.java（增强版）
public class ManagedThreadContext {
    private static final ThreadLocal<ManagedThreadContext> contextHolder = 
        ThreadLocal.withInitial(ManagedThreadContext::new);
    
    private Session currentSession;
    private TaskNode currentTask;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Instant createTime = Instant.now();
    
    // ========== 核心API ==========
    
    /**
     * 获取当前上下文
     */
    public static ManagedThreadContext current() {
        return contextHolder.get();
    }
    
    /**
     * 创建新上下文
     */
    public static ManagedThreadContext create() {
        ManagedThreadContext context = new ManagedThreadContext();
        contextHolder.set(context);
        return context;
    }
    
    /**
     * 清理当前上下文
     */
    public static void clear() {
        ManagedThreadContext context = contextHolder.get();
        if (context != null) {
            context.cleanup();
        }
        contextHolder.remove();
    }
    
    /**
     * 设置Session
     */
    public void setSession(Session session) {
        this.currentSession = session;
        if (session != null) {
            attributes.put("sessionId", session.getSessionId());
        }
    }
    
    /**
     * 开始新任务
     */
    public TaskNode startTask(String taskName) {
        TaskNode task = TaskNode.builder()
            .taskId(generateTaskId())
            .taskName(taskName)
            .parentId(currentTask != null ? currentTask.getTaskId() : null)
            .startTime(Instant.now())
            .build();
        
        if (currentTask != null) {
            currentTask.addChild(task);
        }
        currentTask = task;
        
        return task;
    }
    
    /**
     * 结束当前任务
     */
    public void endTask() {
        if (currentTask != null) {
            currentTask.setEndTime(Instant.now());
            currentTask.setStatus(TaskStatus.COMPLETED);
            
            // 回到父任务
            if (currentTask.getParentId() != null) {
                currentTask = findParentTask(currentTask.getParentId());
            } else {
                currentTask = null;
            }
        }
    }
    
    // ========== 上下文传播 ==========
    
    /**
     * 创建上下文快照（用于传播）
     */
    public ContextSnapshot snapshot() {
        return new ContextSnapshot(this);
    }
    
    /**
     * 从快照恢复上下文
     */
    public static ManagedThreadContext restore(ContextSnapshot snapshot) {
        ManagedThreadContext context = create();
        context.currentSession = snapshot.session;
        context.currentTask = snapshot.task;
        context.attributes.putAll(snapshot.attributes);
        return context;
    }
    
    /**
     * 上下文快照
     */
    public static class ContextSnapshot {
        private final Session session;
        private final TaskNode task;
        private final Map<String, Object> attributes;
        
        private ContextSnapshot(ManagedThreadContext context) {
            this.session = context.currentSession;
            this.task = context.currentTask != null ? 
                context.currentTask.deepCopy() : null;
            this.attributes = new HashMap<>(context.attributes);
        }
    }
    
    // ========== 属性管理 ==========
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

// ... 省略若干，与原文一致

// Spring配置
@Configuration
public class ContextConfiguration {
    
    @Bean
    @Primary
    public TaskExecutor contextAwareTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.initialize();
        
        // 包装为上下文感知的执行器
        return new TaskExecutorAdapter(
            ContextPropagatingExecutor.wrap(executor.getThreadPoolExecutor())
        );
    }
    
    @Bean
    public AsyncConfigurer asyncConfigurer(TaskExecutor contextAwareTaskExecutor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return contextAwareTaskExecutor;
            }
        };
    }
}
```

## 配置示例（YAML）
```yaml
tfi:
  context:
    enabled: true                       # 启用上下文管理
    auto-cleanup: true                  # 自动清理
    cleanup-on-exception: true          # 异常时清理
    max-context-age-minutes: 30         # 上下文最大生存时间
    
    propagation:
      enabled: true                     # 启用传播
      async-enabled: true               # 异步传播
      include-attributes: true          # 传播属性
      
    monitoring:
      enabled: true                     # 监控上下文
      warn-on-leak: true               # 泄漏警告
      log-level: DEBUG                 # 日志级别
```

## 测试示例
```java
// 基础功能测试
@Test
public void testContextLifecycle() {
    ManagedThreadContext context = ManagedThreadContext.create();
    assertThat(context).isNotNull();
    
    Session session = new Session();
    context.setSession(session);
    assertThat(context.getCurrentSession()).isEqualTo(session);
    
    ManagedThreadContext.clear();
    assertThat(ManagedThreadContext.current().getCurrentSession()).isNull();
}

// 任务嵌套测试
@Test
public void testNestedTasks() {
    ManagedThreadContext context = ManagedThreadContext.current();
    
    TaskNode task1 = context.startTask("task1");
    TaskNode task2 = context.startTask("task2");
    
    assertThat(task2.getParentId()).isEqualTo(task1.getTaskId());
    
    context.endTask(); // 结束task2
    assertThat(context.getCurrentTask()).isEqualTo(task1);
}

// 上下文传播测试
@Test
public void testContextPropagation() throws Exception {
    ManagedThreadContext.current().setAttribute("key", "value");
    
    ExecutorService executor = ContextPropagatingExecutor.wrap(
        Executors.newSingleThreadExecutor()
    );
    
    Future<String> future = executor.submit(() -> {
        return (String) ManagedThreadContext.current().getAttribute("key");
    });
    
    assertThat(future.get()).isEqualTo("value");
}
```

## 使用模式示例
```java
// 自动管理模式
@ContextManaged
public void businessMethod() {
    ManagedThreadContext context = ManagedThreadContext.current();
    context.startTask("businessTask");
    
    // 业务逻辑
    
    context.endTask();
} // 自动清理

// 手动管理模式
public void manualMethod() {
    ManagedThreadContext context = ManagedThreadContext.create();
    try {
        // 业务逻辑
    } finally {
        ManagedThreadContext.clear();
    }
}

// 异步传播模式
@Async
public CompletableFuture<Result> asyncMethod() {
    // 上下文自动传播到异步线程
    ManagedThreadContext context = ManagedThreadContext.current();
    String sessionId = context.getCurrentSession().getSessionId();
    
    return CompletableFuture.completedFuture(new Result(sessionId));
}
```

