# TASK-009: 上下文管理单元测试

## 任务概述

为TaskFlowInsight的核心上下文管理组件（ThreadContext、ContextManager、ThreadLocal内存管理）实现全面的单元测试，确保多线程环境下的正确性和稳定性。

## 需求分析

1. 对ThreadContext核心功能进行单元测试，确保线程独立性
2. 对ContextManager进行单元测试，验证上下文生命周期管理
3. 对ThreadLocal内存管理进行单元测试，确保内存泄漏防护
4. 实现并发场景测试，验证多线程安全性
5. 实现性能基准测试，验证性能指标
6. 提供完整的测试覆盖率

## 技术实现

### 1. ThreadContext单元测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class ThreadContextTest {
    
    private ThreadContext threadContext;
    
    @BeforeEach
    void setUp() {
        threadContext = new ThreadContext(Thread.currentThread().threadId());
    }
    
    @Test
    @Order(1)
    @DisplayName("测试ThreadContext基本功能")
    void testBasicFunctionality() {
        // 基础验证
        assertNotNull(threadContext);
        assertEquals(Thread.currentThread().threadId(), threadContext.getThreadId());
        assertNull(threadContext.getCurrentSession());
        assertTrue(threadContext.getTaskStack().isEmpty());
        assertEquals(0, threadContext.getCurrentDepth());
    }
    
    @Test
    @Order(2)
    @DisplayName("测试会话管理")
    void testSessionManagement() {
        // 创建会话
        Session session = new Session(Thread.currentThread().threadId());
        threadContext.setCurrentSession(session);
        
        // 验证会话设置
        assertNotNull(threadContext.getCurrentSession());
        assertEquals(session, threadContext.getCurrentSession());
        assertEquals(Thread.currentThread().threadId(), threadContext.getCurrentSession().getThreadId());
    }
    
    @Test
    @Order(3)
    @DisplayName("测试任务栈操作")
    void testTaskStackOperations() {
        // 创建会话
        Session session = new Session(Thread.currentThread().threadId());
        threadContext.setCurrentSession(session);
        
        // 创建任务节点
        TaskNode task1 = new TaskNode("task1", 1, System.nanoTime());
        TaskNode task2 = new TaskNode("task2", 2, System.nanoTime());
        
        // 测试压栈操作
        threadContext.pushTask(task1);
        assertEquals(1, threadContext.getCurrentDepth());
        assertEquals(task1, threadContext.getCurrentTask());
        
        threadContext.pushTask(task2);
        assertEquals(2, threadContext.getCurrentDepth());
        assertEquals(task2, threadContext.getCurrentTask());
        
        // 测试出栈操作
        TaskNode poppedTask = threadContext.popTask();
        assertEquals(task2, poppedTask);
        assertEquals(1, threadContext.getCurrentDepth());
        assertEquals(task1, threadContext.getCurrentTask());
        
        poppedTask = threadContext.popTask();
        assertEquals(task1, poppedTask);
        assertEquals(0, threadContext.getCurrentDepth());
        assertNull(threadContext.getCurrentTask());
    }
    
    @Test
    @Order(4)
    @DisplayName("测试异常场景处理")
    void testExceptionHandling() {
        // 测试空栈出栈
        assertNull(threadContext.popTask());
        assertEquals(0, threadContext.getCurrentDepth());
        
        // 测试null任务处理
        assertDoesNotThrow(() -> threadContext.pushTask(null));
        
        // 测试会话清理
        Session session = new Session(Thread.currentThread().threadId());
        threadContext.setCurrentSession(session);
        
        TaskNode task = new TaskNode("test", 1, System.nanoTime());
        threadContext.pushTask(task);
        
        threadContext.cleanup();
        assertNull(threadContext.getCurrentSession());
        assertTrue(threadContext.getTaskStack().isEmpty());
        assertEquals(0, threadContext.getCurrentDepth());
    }
    
    @Test
    @Order(5)
    @DisplayName("测试性能指标")
    void testPerformanceMetrics() {
        Session session = new Session(Thread.currentThread().threadId());
        threadContext.setCurrentSession(session);
        
        // 性能测试：压栈和出栈操作
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 10000; i++) {
            TaskNode task = new TaskNode("task" + i, i + 1, System.nanoTime());
            threadContext.pushTask(task);
        }
        
        for (int i = 0; i < 10000; i++) {
            threadContext.popTask();
        }
        
        long duration = System.nanoTime() - startTime;
        long averageOperationTime = duration / 20000; // 20000次操作
        
        // 验证单次操作时间应小于1微秒（1000纳秒）
        assertTrue(averageOperationTime < 1000, 
            "Average operation time should be less than 1μs, but was: " + averageOperationTime + "ns");
    }
}
```

### 2. ContextManager单元测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class ContextManagerTest {
    
    private ContextManager contextManager;
    
    @BeforeEach
    void setUp() {
        contextManager = ContextManager.getInstance();
        contextManager.cleanup(); // 清理之前的状态
    }
    
    @AfterEach
    void tearDown() {
        contextManager.cleanup();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试单例模式")
    void testSingletonPattern() {
        ContextManager instance1 = ContextManager.getInstance();
        ContextManager instance2 = ContextManager.getInstance();
        
        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
    }
    
    @Test
    @Order(2)
    @DisplayName("测试ThreadContext创建和获取")
    void testThreadContextCreationAndRetrieval() {
        // 首次获取应创建新的ThreadContext
        ThreadContext context1 = contextManager.getOrCreateThreadContext();
        assertNotNull(context1);
        assertEquals(Thread.currentThread().threadId(), context1.getThreadId());
        
        // 再次获取应返回相同的实例
        ThreadContext context2 = contextManager.getOrCreateThreadContext();
        assertSame(context1, context2);
    }
    
    @Test
    @Order(3)
    @DisplayName("测试新会话启动")
    void testNewSessionStart() {
        ThreadContext context = contextManager.getOrCreateThreadContext();
        
        // 启动新会话
        Session session = contextManager.startNewSession();
        
        assertNotNull(session);
        assertSame(session, context.getCurrentSession());
        assertEquals(Thread.currentThread().threadId(), session.getThreadId());
    }
    
    @Test
    @Order(4)
    @DisplayName("测试任务开始和结束")
    void testTaskStartAndEnd() {
        contextManager.startNewSession();
        
        // 开始任务
        TaskNode task = contextManager.startTask("testTask");
        assertNotNull(task);
        assertEquals("testTask", task.getTaskName());
        assertEquals(1, task.getDepth());
        
        ThreadContext context = contextManager.getOrCreateThreadContext();
        assertEquals(1, context.getCurrentDepth());
        assertEquals(task, context.getCurrentTask());
        
        // 结束任务
        TaskNode completedTask = contextManager.endCurrentTask();
        assertNotNull(completedTask);
        assertSame(task, completedTask);
        assertTrue(completedTask.isCompleted());
        assertEquals(0, context.getCurrentDepth());
    }
    
    @Test
    @Order(5)
    @DisplayName("测试嵌套任务")
    void testNestedTasks() {
        contextManager.startNewSession();
        
        // 创建嵌套任务
        TaskNode parentTask = contextManager.startTask("parentTask");
        TaskNode childTask = contextManager.startTask("childTask");
        TaskNode grandChildTask = contextManager.startTask("grandChildTask");
        
        ThreadContext context = contextManager.getOrCreateThreadContext();
        assertEquals(3, context.getCurrentDepth());
        assertEquals(grandChildTask, context.getCurrentTask());
        
        // 按顺序结束任务
        TaskNode completedGrandChild = contextManager.endCurrentTask();
        assertEquals(grandChildTask, completedGrandChild);
        assertEquals(2, context.getCurrentDepth());
        assertEquals(childTask, context.getCurrentTask());
        
        TaskNode completedChild = contextManager.endCurrentTask();
        assertEquals(childTask, completedChild);
        assertEquals(1, context.getCurrentDepth());
        assertEquals(parentTask, context.getCurrentTask());
        
        TaskNode completedParent = contextManager.endCurrentTask();
        assertEquals(parentTask, completedParent);
        assertEquals(0, context.getCurrentDepth());
        assertNull(context.getCurrentTask());
    }
    
    @Test
    @Order(6)
    @DisplayName("测试消息添加")
    void testMessageAddition() {
        contextManager.startNewSession();
        TaskNode task = contextManager.startTask("messageTask");
        
        // 添加消息
        contextManager.addMessage("Test message 1", MessageType.INFO);
        contextManager.addMessage("Test message 2", MessageType.DEBUG);
        
        // 验证消息
        assertEquals(2, task.getMessages().size());
        assertEquals("Test message 1", task.getMessages().get(0).getContent());
        assertEquals("Test message 2", task.getMessages().get(1).getContent());
    }
    
    @Test
    @Order(7)
    @DisplayName("测试系统统计信息")
    void testSystemStatistics() {
        contextManager.startNewSession();
        
        // 创建一些任务来生成统计信息
        contextManager.startTask("task1");
        contextManager.endCurrentTask();
        
        contextManager.startTask("task2");
        contextManager.addMessage("message", MessageType.INFO);
        contextManager.endCurrentTask();
        
        SystemStatistics stats = contextManager.getSystemStatistics();
        assertNotNull(stats);
        assertTrue(stats.getTotalTasks() >= 2);
        assertTrue(stats.getCompletedTasks() >= 2);
        assertTrue(stats.getTotalMessages() >= 1);
    }
    
    @Test
    @Order(8)
    @DisplayName("测试清理功能")
    void testCleanupFunctionality() {
        contextManager.startNewSession();
        TaskNode task = contextManager.startTask("cleanupTest");
        contextManager.addMessage("test message", MessageType.INFO);
        
        ThreadContext context = contextManager.getOrCreateThreadContext();
        assertNotNull(context.getCurrentSession());
        assertNotNull(context.getCurrentTask());
        
        // 执行清理
        contextManager.cleanup();
        
        assertNull(context.getCurrentSession());
        assertNull(context.getCurrentTask());
        assertEquals(0, context.getCurrentDepth());
        assertTrue(context.getTaskStack().isEmpty());
    }
}
```

### 3. 异步上下文传递测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class AsyncContextPropagationTest {
    
    private SafeContextManager contextManager;
    
    @BeforeEach
    void setUp() {
        contextManager = SafeContextManager.getInstance();
        contextManager.enable();
    }
    
    @AfterEach
    void tearDown() {
        contextManager.cleanup();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试CompletableFuture上下文传递")
    void testCompletableFutureContextPropagation() throws Exception {
        // 主线程创建上下文
        ManagedThreadContext mainContext = contextManager.getOrCreateContext();
        mainContext.startSession();
        TaskNode mainTask = mainContext.startTask("mainTask");
        
        // 创建上下文快照
        ManagedThreadContext.ContextSnapshot snapshot = mainContext.createSnapshot();
        
        // 异步任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            // 在新线程中恢复上下文
            try (ManagedThreadContext asyncContext = snapshot.restore()) {
                asyncContext.startTask("asyncTask");
                
                // 验证可以访问上下文
                assertNotNull(asyncContext.getCurrentTask());
                assertEquals("asyncTask", asyncContext.getCurrentTask().getName());
                
                asyncContext.endTask();
                return "async-completed";
            }
        });
        
        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("async-completed", result);
        
        mainContext.endTask();
        mainContext.endSession();
    }
    
    @Test
    @Order(2)
    @DisplayName("测试多级异步链上下文传递")
    void testChainedAsyncContextPropagation() throws Exception {
        ManagedThreadContext mainContext = contextManager.getOrCreateContext();
        mainContext.startSession();
        
        CompletableFuture<String> chain = CompletableFuture
            .supplyAsync(() -> {
                try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                    ctx.startTask("async-level-1");
                    return "level-1";
                }
            })
            .thenApplyAsync(prev -> {
                try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                    ctx.startTask("async-level-2");
                    return prev + "-level-2";
                }
            })
            .thenApplyAsync(prev -> {
                try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                    ctx.startTask("async-level-3");
                    return prev + "-level-3";
                }
            });
        
        String result = chain.get(5, TimeUnit.SECONDS);
        assertEquals("level-1-level-2-level-3", result);
    }
    
    @Test
    @Order(3)
    @DisplayName("测试并行异步任务上下文隔离")
    void testParallelAsyncContextIsolation() throws Exception {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                    ctx.startSession();
                    ctx.startTask("parallel-task-" + taskId);
                    
                    // 验证上下文隔离
                    assertEquals("parallel-task-" + taskId, ctx.getCurrentTask().getName());
                    
                    // 模拟处理
                    Thread.sleep(10);
                    
                    ctx.endTask();
                    return "task-" + taskId + "-completed";
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        allOf.get(10, TimeUnit.SECONDS);
        
        // 验证所有任务完成
        for (int i = 0; i < futures.size(); i++) {
            assertEquals("task-" + i + "-completed", futures.get(i).get());
        }
    }
}
```

### 4. 虚拟线程测试（Java 21+）
```java
@TestMethodOrder(OrderAnnotation.class)
@EnabledForJreRange(min = JRE.JAVA_21) // 仅在Java 21+运行
public class VirtualThreadContextTest {
    
    private SafeContextManager contextManager;
    
    @BeforeEach
    void setUp() {
        contextManager = SafeContextManager.getInstance();
        contextManager.enable();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试虚拟线程上下文管理")
    void testVirtualThreadContext() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            
            List<Future<String>> futures = new ArrayList<>();
            
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                Future<String> future = scope.fork(() -> {
                    try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                        ctx.startSession();
                        ctx.startTask("vthread-task-" + taskId);
                        
                        // 验证虚拟线程中的上下文
                        assertNotNull(ctx.getCurrentTask());
                        assertEquals("vthread-task-" + taskId, ctx.getCurrentTask().getName());
                        
                        // 模拟IO操作
                        Thread.sleep(50);
                        
                        ctx.endTask();
                        return "vthread-" + taskId + "-done";
                    }
                });
                futures.add(future);
            }
            
            scope.join();
            scope.throwIfFailed();
            
            // 验证所有虚拟线程任务完成
            for (int i = 0; i < futures.size(); i++) {
                assertEquals("vthread-" + i + "-done", futures.get(i).get());
            }
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("测试虚拟线程大规模并发")
    void testMassiveVirtualThreadConcurrency() throws Exception {
        final int THREAD_COUNT = 10000; // 10000个虚拟线程
        AtomicInteger successCount = new AtomicInteger(0);
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < THREAD_COUNT; i++) {
                scope.fork(() -> {
                    try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                        ctx.startTask("massive-vthread-task");
                        Thread.sleep(1); // 模拟短暂处理
                        ctx.endTask();
                        successCount.incrementAndGet();
                        return null;
                    }
                });
            }
            
            scope.join();
            scope.throwIfFailed();
        }
        
        assertEquals(THREAD_COUNT, successCount.get());
    }
    
    @Test
    @Order(3)
    @DisplayName("测试虚拟线程与平台线程混合使用")
    void testMixedVirtualAndPlatformThreads() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> platformResult = new AtomicReference<>();
        AtomicReference<String> virtualResult = new AtomicReference<>();
        
        // 平台线程
        Thread platformThread = Thread.ofPlatform().start(() -> {
            try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                ctx.startTask("platform-thread-task");
                platformResult.set("platform-completed");
                ctx.endTask();
            } finally {
                latch.countDown();
            }
        });
        
        // 虚拟线程
        Thread virtualThread = Thread.ofVirtual().start(() -> {
            try (ManagedThreadContext ctx = contextManager.getOrCreateContext()) {
                ctx.startTask("virtual-thread-task");
                virtualResult.set("virtual-completed");
                ctx.endTask();
            } finally {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        assertEquals("platform-completed", platformResult.get());
        assertEquals("virtual-completed", virtualResult.get());
    }
}
```

### 5. ThreadLocal内存管理测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class ThreadLocalMemoryManagementTest {
    
    private ContextManager contextManager;
    
    @BeforeEach
    void setUp() {
        contextManager = ContextManager.getInstance();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试ThreadLocal线程隔离")
    void testThreadLocalIsolation() throws InterruptedException {
        AtomicReference<ThreadContext> context1 = new AtomicReference<>();
        AtomicReference<ThreadContext> context2 = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        
        // 线程1
        Thread thread1 = new Thread(() -> {
            try {
                contextManager.startNewSession();
                contextManager.startTask("thread1Task");
                context1.set(contextManager.getOrCreateThreadContext());
            } finally {
                latch.countDown();
            }
        });
        
        // 线程2
        Thread thread2 = new Thread(() -> {
            try {
                contextManager.startNewSession();
                contextManager.startTask("thread2Task");
                context2.set(contextManager.getOrCreateThreadContext());
            } finally {
                latch.countDown();
            }
        });
        
        thread1.start();
        thread2.start();
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 验证线程隔离
        assertNotNull(context1.get());
        assertNotNull(context2.get());
        assertNotSame(context1.get(), context2.get());
        
        assertNotEquals(context1.get().getThreadId(), context2.get().getThreadId());
        assertEquals("thread1Task", context1.get().getCurrentTask().getTaskName());
        assertEquals("thread2Task", context2.get().getCurrentTask().getTaskName());
    }
    
    @Test
    @Order(2)
    @DisplayName("测试ThreadLocal内存清理")
    void testThreadLocalMemoryCleanup() throws InterruptedException {
        AtomicBoolean cleanupCompleted = new AtomicBoolean(false);
        
        Thread testThread = new Thread(() -> {
            // 在子线程中创建上下文
            contextManager.startNewSession();
            contextManager.startTask("memoryTestTask");
            
            ThreadContext context = contextManager.getOrCreateThreadContext();
            assertNotNull(context);
            
            // 显式清理
            contextManager.cleanup();
            cleanupCompleted.set(true);
        });
        
        testThread.start();
        testThread.join(5000);
        
        assertTrue(cleanupCompleted.get());
        assertFalse(testThread.isAlive());
    }
    
    @Test
    @Order(3)
    @DisplayName("测试大量ThreadLocal创建和销毁")
    void testMassiveThreadLocalCreationAndDestruction() throws InterruptedException {
        final int THREAD_COUNT = 100;
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    // 模拟正常业务流程
                    contextManager.startNewSession();
                    contextManager.startTask("task" + threadIndex);
                    contextManager.addMessage("message" + threadIndex, MessageType.INFO);
                    contextManager.endCurrentTask();
                    
                    // 验证上下文正确性
                    ThreadContext context = contextManager.getOrCreateThreadContext();
                    assertNotNull(context);
                    assertEquals(0, context.getCurrentDepth());
                    
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    contextManager.cleanup();
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(THREAD_COUNT, successCount.get());
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(4)
    @DisplayName("测试内存泄漏防护")
    void testMemoryLeakProtection() {
        // 获取初始内存状态
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // 建议进行垃圾回收
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 创建大量上下文（模拟潜在内存泄漏场景）
        for (int i = 0; i < 1000; i++) {
            contextManager.startNewSession();
            for (int j = 0; j < 10; j++) {
                contextManager.startTask("task_" + i + "_" + j);
                contextManager.addMessage("message_" + i + "_" + j, MessageType.INFO);
            }
            // 模拟异常退出，不调用endCurrentTask
            contextManager.cleanup(); // 强制清理
        }
        
        // 强制垃圾回收
        for (int i = 0; i < 5; i++) {
            runtime.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // 验证内存增长在合理范围内（小于5MB）
        assertTrue(memoryIncrease < 5 * 1024 * 1024, 
            "Memory increase should be less than 5MB, but was: " + memoryIncrease + " bytes");
    }
}
```

### 6. 嵌套上下文与强制清理测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class NestedContextAndCleanupTest {
    
    private SafeContextManager contextManager;
    private ZeroLeakThreadLocalManager leakManager;
    
    @BeforeEach
    void setUp() {
        contextManager = SafeContextManager.getInstance();
        contextManager.enable();
        leakManager = ZeroLeakThreadLocalManager.getInstance();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试嵌套上下文检测和警告")
    void testNestedContextDetection() {
        // 创建第一个上下文
        try (ManagedThreadContext ctx1 = ManagedThreadContext.create()) {
            ctx1.startSession();
            ctx1.startTask("outer-task");
            
            // 尝试创建嵌套上下文（应该触发警告）
            try (ManagedThreadContext ctx2 = ManagedThreadContext.create()) {
                ctx2.startSession();
                ctx2.startTask("nested-task");
                
                // 验证嵌套级别
                assertTrue(ctx2.getNestingLevel() > 0);
                
                // 再次嵌套
                try (ManagedThreadContext ctx3 = ManagedThreadContext.create()) {
                    ctx3.startTask("deeply-nested-task");
                    assertTrue(ctx3.getNestingLevel() > 1);
                }
                
                // ctx3自动清理后，ctx2应该仍然有效
                assertNotNull(ctx2.getCurrentTask());
            }
            
            // ctx2清理后，ctx1应该仍然有效
            assertNotNull(ctx1.getCurrentTask());
        }
        
        // 所有上下文清理后，应该没有活动上下文
        assertFalse(ManagedThreadContext.hasActiveContext());
    }
    
    @Test
    @Order(2)
    @DisplayName("测试强制清理机制")
    void testForceCleanup() {
        ManagedThreadContext ctx = ManagedThreadContext.create();
        ctx.startSession();
        ctx.startTask("test-task");
        
        // 验证上下文活动
        assertTrue(ManagedThreadContext.hasActiveContext());
        assertNotNull(ctx.getCurrentTask());
        
        // 强制清理
        ctx.close();
        
        // 验证上下文已关闭
        assertTrue(ctx.isClosed());
        assertThrows(IllegalStateException.class, () -> ctx.startTask("after-close"));
        
        // 清理后不应有活动上下文
        assertFalse(ManagedThreadContext.hasActiveContext());
    }
    
    @Test
    @Order(3)
    @DisplayName("测试异常情况下的自动清理")
    void testAutoCleanupOnException() {
        assertThrows(RuntimeException.class, () -> {
            try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
                ctx.startSession();
                ctx.startTask("exception-task");
                
                // 模拟异常
                throw new RuntimeException("Simulated exception");
            }
        });
        
        // 即使有异常，上下文也应该被清理
        assertFalse(ManagedThreadContext.hasActiveContext());
    }
    
    @Test
    @Order(4)
    @DisplayName("测试线程池中的强制清理")
    void testThreadPoolForceCleanup() throws Exception {
        ExecutorService executor = new SafeThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
        );
        
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger cleanedCount = new AtomicInteger(0);
        
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    // SafeThreadPoolExecutor会自动包装任务
                    ManagedThreadContext ctx = ManagedThreadContext.current();
                    ctx.startTask("pool-task");
                    
                    // 模拟工作
                    Thread.sleep(10);
                    
                    ctx.endTask();
                    cleanedCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(10, cleanedCount.get());
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(5)
    @DisplayName("测试内存泄漏检测和修复")
    void testMemoryLeakDetectionAndRepair() throws Exception {
        // 启用诊断模式
        leakManager.setDiagnosticMode(true);
        
        // 模拟泄漏场景：创建上下文但不清理
        Thread leakyThread = new Thread(() -> {
            ManagedThreadContext ctx = ManagedThreadContext.create();
            ctx.startSession();
            ctx.startTask("leaky-task");
            // 故意不调用close()
        });
        
        leakyThread.start();
        leakyThread.join();
        
        // 等待泄漏检测
        Thread.sleep(1000);
        
        // 验证泄漏被检测到
        assertTrue(leakManager.getTotalLeaksDetected() > 0);
        
        // 等待自动修复
        Thread.sleep(2000);
        
        // 验证泄漏被修复
        assertTrue(leakManager.getTotalLeaksFixed() > 0);
    }
    
    @Test
    @Order(6)
    @DisplayName("测试上下文限制和紧急清理")
    void testContextLimitAndEmergencyCleanup() {
        // 创建超过限制的上下文
        List<ManagedThreadContext> contexts = new ArrayList<>();
        
        try {
            for (int i = 0; i < 10; i++) {
                ManagedThreadContext ctx = ManagedThreadContext.create();
                ctx.startTask("limit-test-" + i);
                contexts.add(ctx);
            }
            
            // 应该触发紧急清理
            assertTrue(leakManager.getTotalEmergencyCleanups() > 0);
            
        } finally {
            // 清理所有上下文
            for (ManagedThreadContext ctx : contexts) {
                if (!ctx.isClosed()) {
                    ctx.close();
                }
            }
        }
    }
}
```

### 7. 并发安全测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class ConcurrencyTest {
    
    private ContextManager contextManager;
    
    @BeforeEach
    void setUp() {
        contextManager = ContextManager.getInstance();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试高并发任务创建")
    void testHighConcurrencyTaskCreation() throws InterruptedException {
        final int THREAD_COUNT = 50;
        final int TASKS_PER_THREAD = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch finishLatch = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger totalTasks = new AtomicInteger(0);
        final AtomicInteger errors = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始信号
                    
                    contextManager.startNewSession();
                    
                    for (int j = 0; j < TASKS_PER_THREAD; j++) {
                        String taskName = "thread_" + threadId + "_task_" + j;
                        contextManager.startTask(taskName);
                        contextManager.addMessage("Processed " + j, MessageType.INFO);
                        contextManager.endCurrentTask();
                        totalTasks.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    contextManager.cleanup();
                    finishLatch.countDown();
                }
            });
        }
        
        // 统一开始
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(finishLatch.await(60, TimeUnit.SECONDS));
        
        // 验证结果
        assertEquals(THREAD_COUNT * TASKS_PER_THREAD, totalTasks.get());
        assertEquals(0, errors.get());
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(2)
    @DisplayName("测试并发会话管理")
    void testConcurrentSessionManagement() throws InterruptedException {
        final int THREAD_COUNT = 20;
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final Set<Long> sessionIds = ConcurrentHashMap.newKeySet();
        final AtomicInteger errors = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    Session session = contextManager.startNewSession();
                    assertNotNull(session);
                    
                    // 记录会话ID，验证唯一性
                    assertTrue(sessionIds.add(session.getSessionId()));
                    
                    // 执行一些任务
                    contextManager.startTask("sessionTest");
                    contextManager.addMessage("Session test message", MessageType.INFO);
                    contextManager.endCurrentTask();
                    
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    contextManager.cleanup();
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        
        // 验证结果
        assertEquals(THREAD_COUNT, sessionIds.size());
        assertEquals(0, errors.get());
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(3)
    @DisplayName("测试长时间运行的并发稳定性")
    void testLongRunningConcurrencyStability() throws InterruptedException {
        final AtomicBoolean shouldStop = new AtomicBoolean(false);
        final AtomicInteger processedTasks = new AtomicInteger(0);
        final AtomicInteger errors = new AtomicInteger(0);
        final int WORKER_COUNT = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        
        // 启动工作线程
        for (int i = 0; i < WORKER_COUNT; i++) {
            final int workerId = i;
            executor.submit(() -> {
                int localTaskCount = 0;
                while (!shouldStop.get()) {
                    try {
                        contextManager.startNewSession();
                        
                        // 模拟复杂的嵌套任务
                        contextManager.startTask("worker_" + workerId + "_outer");
                        
                        for (int j = 0; j < 5; j++) {
                            contextManager.startTask("inner_task_" + j);
                            contextManager.addMessage("Processing " + j, MessageType.DEBUG);
                            Thread.sleep(1); // 模拟处理时间
                            contextManager.endCurrentTask();
                        }
                        
                        contextManager.endCurrentTask();
                        localTaskCount++;
                        processedTasks.incrementAndGet();
                        
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        contextManager.cleanup();
                    }
                }
            });
        }
        
        // 运行5秒
        Thread.sleep(5000);
        shouldStop.set(true);
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        
        // 验证结果
        assertTrue(processedTasks.get() > 0);
        assertEquals(0, errors.get());
        
        System.out.println("Processed " + processedTasks.get() + " tasks in 5 seconds with " + WORKER_COUNT + " workers");
    }
}
```

### 5. 性能基准测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class PerformanceBenchmarkTest {
    
    private ContextManager contextManager;
    
    @BeforeEach
    void setUp() {
        contextManager = ContextManager.getInstance();
        // JVM预热
        warmUp();
    }
    
    private void warmUp() {
        for (int i = 0; i < 1000; i++) {
            contextManager.startNewSession();
            contextManager.startTask("warmup");
            contextManager.endCurrentTask();
            contextManager.cleanup();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("测试单线程性能基准")
    void testSingleThreadedPerformanceBenchmark() {
        final int ITERATIONS = 100000;
        
        long startTime = System.nanoTime();
        
        contextManager.startNewSession();
        
        for (int i = 0; i < ITERATIONS; i++) {
            contextManager.startTask("perf_test_" + i);
            contextManager.addMessage("Performance test message " + i, MessageType.INFO);
            contextManager.endCurrentTask();
        }
        
        contextManager.cleanup();
        
        long duration = System.nanoTime() - startTime;
        long averageOperationTime = duration / (ITERATIONS * 3); // 3 operations per iteration
        
        System.out.println("Single-threaded performance:");
        System.out.println("Total time: " + duration / 1_000_000 + " ms");
        System.out.println("Average operation time: " + averageOperationTime + " ns");
        System.out.println("Operations per second: " + (1_000_000_000L / averageOperationTime));
        
        // 验证性能要求：每个操作应小于1微秒
        assertTrue(averageOperationTime < 1000, 
            "Average operation time should be less than 1μs, but was: " + averageOperationTime + "ns");
    }
    
    @Test
    @Order(2)
    @DisplayName("测试多线程性能基准")
    void testMultiThreadedPerformanceBenchmark() throws InterruptedException {
        final int THREAD_COUNT = 10;
        final int ITERATIONS_PER_THREAD = 10000;
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        long startTime = System.nanoTime();
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    contextManager.startNewSession();
                    
                    for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                        contextManager.startTask("mt_perf_test_" + threadId + "_" + i);
                        contextManager.addMessage("MT performance test", MessageType.INFO);
                        contextManager.endCurrentTask();
                    }
                    
                } finally {
                    contextManager.cleanup();
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long duration = System.nanoTime() - startTime;
        
        long totalOperations = THREAD_COUNT * ITERATIONS_PER_THREAD * 3L;
        long averageOperationTime = duration / totalOperations;
        
        System.out.println("Multi-threaded performance (" + THREAD_COUNT + " threads):");
        System.out.println("Total time: " + duration / 1_000_000 + " ms");
        System.out.println("Average operation time: " + averageOperationTime + " ns");
        System.out.println("Operations per second: " + (1_000_000_000L / averageOperationTime));
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        
        // 多线程环境下性能可能稍低，但仍应保持在合理范围内
        assertTrue(averageOperationTime < 2000, 
            "Average operation time should be less than 2μs in multi-threaded scenario, but was: " + averageOperationTime + "ns");
    }
    
    @Test
    @Order(3)
    @DisplayName("测试内存使用基准")
    void testMemoryUsageBenchmark() {
        Runtime runtime = Runtime.getRuntime();
        
        // 强制垃圾回收以获得准确的基线
        runtime.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        contextManager.startNewSession();
        
        // 创建大量任务和消息
        final int TASK_COUNT = 10000;
        for (int i = 0; i < TASK_COUNT; i++) {
            contextManager.startTask("memory_test_" + i);
            contextManager.addMessage("Memory test message " + i, MessageType.INFO);
            contextManager.addMessage("Additional message " + i, MessageType.DEBUG);
            contextManager.endCurrentTask();
        }
        
        long peakMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = peakMemory - baselineMemory;
        
        contextManager.cleanup();
        
        // 再次垃圾回收并测量内存
        runtime.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long remainingIncrease = finalMemory - baselineMemory;
        
        System.out.println("Memory usage benchmark (" + TASK_COUNT + " tasks):");
        System.out.println("Baseline memory: " + baselineMemory / 1024 + " KB");
        System.out.println("Peak memory increase: " + memoryIncrease / 1024 + " KB");
        System.out.println("Memory per task: " + (memoryIncrease / TASK_COUNT) + " bytes");
        System.out.println("Remaining increase after cleanup: " + remainingIncrease / 1024 + " KB");
        
        // 验证内存使用合理（每个任务平均不超过500字节）
        assertTrue(memoryIncrease / TASK_COUNT < 500, 
            "Memory usage per task should be less than 500 bytes, but was: " + (memoryIncrease / TASK_COUNT));
        
        // 验证内存清理效果（剩余增长应小于总增长的10%）
        assertTrue(remainingIncrease < memoryIncrease * 0.1, 
            "Memory cleanup should be effective");
    }
}
```

## 关键特性

### 测试覆盖特性
1. **功能覆盖特性**：所有核心功能的完整测试
2. **异常覆盖特性**：异常场景的全面测试
3. **边界覆盖特性**：边界条件和极端情况测试
4. **集成覆盖特性**：组件间协作的集成测试

### 并发安全特性
1. **线程隔离特性**：ThreadLocal线程隔离验证
2. **并发安全特性**：多线程环境安全性验证
3. **竞争条件特性**：竞争条件检测和防护
4. **死锁防护特性**：死锁场景检测和预防

### 性能验证特性
1. **响应时间特性**：操作响应时间验证
2. **吞吐量特性**：系统吞吐量基准测试
3. **内存效率特性**：内存使用效率验证
4. **资源泄漏特性**：内存和资源泄漏检测

## 验收标准

### 功能验收
- [ ] ThreadContext核心功能测试通过率100%
- [ ] ContextManager生命周期测试通过率100%
- [ ] ThreadLocal内存管理测试通过率100%
- [ ] 所有异常场景测试通过

### 性能验收
- [ ] 单线程平均操作时间小于1微秒
- [ ] 多线程平均操作时间小于2微秒
- [ ] 每个任务内存使用小于500字节
- [ ] 内存清理效率大于90%

### 并发验收
- [ ] 100个并发线程稳定运行
- [ ] 线程间数据完全隔离
- [ ] 无竞争条件和死锁问题
- [ ] 长时间运行稳定性验证

## 依赖关系

### 前置依赖
- TASK-004: ThreadContext线程上下文实现
- TASK-005: ContextManager上下文管理器实现
- TASK-008: ThreadLocal内存管理实现

### 后置依赖
- TASK-013: API接口单元测试

## 开发计划

### 分阶段开发计划
- **Day 1**: ThreadContext和ContextManager基础单元测试
- **Day 2**: ThreadLocal内存管理和并发安全测试
- **Day 3**: 性能基准测试和测试报告生成

## 风险评估

### 技术风险
1. **测试复杂度风险**：并发测试场景复杂，可能遗漏边界情况
   - 缓解措施：分阶段测试，从简单到复杂逐步验证
2. **性能测试准确性**：JVM预热和垃圾回收可能影响测试准确性
   - 缓解措施：充分的预热和多次测试取平均值

### 业务风险
1. **测试环境差异**：测试环境与生产环境的差异可能影响结果
   - 缓解措施：在类似生产环境的条件下进行测试
2. **测试数据代表性**：测试数据可能不能代表实际使用场景
   - 缓解措施：基于实际使用模式设计测试数据

## 实现文件

1. **ThreadContext单元测试** (`ThreadContextTest.java`)
2. **ContextManager单元测试** (`ContextManagerTest.java`)
3. **ThreadLocal内存管理测试** (`ThreadLocalMemoryManagementTest.java`)
4. **并发安全测试** (`ConcurrencyTest.java`)
5. **性能基准测试** (`PerformanceBenchmarkTest.java`)
6. **测试配置文件** (`test-application.yml`)
7. **测试报告模板** (`test-report-template.md`)