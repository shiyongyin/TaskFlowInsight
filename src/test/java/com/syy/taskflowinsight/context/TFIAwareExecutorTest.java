package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TFIAwareExecutor单元测试
 * 验证线程池上下文传播功能
 */
class TFIAwareExecutorTest {
    
    private TFIAwareExecutor executor;
    
    @BeforeEach
    void setUp() {
        executor = TFIAwareExecutor.newFixedThreadPool(2);
        
        // 清理现有上下文
        ManagedThreadContext current = ManagedThreadContext.current();
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
    
    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 清理测试上下文
        ManagedThreadContext current = ManagedThreadContext.current();
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
    
    @Test
    @DisplayName("基础Runnable执行和上下文传播")
    void testRunnableExecution() throws Exception {
        AtomicReference<String> mainContextId = new AtomicReference<>();
        AtomicReference<String> workerContextId = new AtomicReference<>();
        AtomicReference<String> workerParentContextId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        // 在主线程创建上下文
        try (ManagedThreadContext mainContext = ManagedThreadContext.create("mainTask")) {
            mainContextId.set(mainContext.getContextId());
            
            // 提交任务到线程池
            executor.execute(() -> {
                try {
                    // 工作线程应该有自己的上下文
                    ManagedThreadContext workerContext = ManagedThreadContext.current();
                    assertNotNull(workerContext, "Worker thread should have context");
                    
                    workerContextId.set(workerContext.getContextId());
                    
                    // 应该包含父上下文信息
                    String parentId = workerContext.getAttribute("parent.contextId");
                    workerParentContextId.set(parentId);
                    
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待任务完成
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 验证上下文传播
        assertNotNull(workerContextId.get());
        assertNotEquals(mainContextId.get(), workerContextId.get()); // 不同线程有不同上下文
        assertEquals(mainContextId.get(), workerParentContextId.get()); // 包含父上下文ID
    }
    
    @Test
    @DisplayName("Callable执行和结果返回")
    void testCallableExecution() throws Exception {
        String mainContextId;
        String result;
        
        // 在主线程创建上下文
        try (ManagedThreadContext mainContext = ManagedThreadContext.create("callableTest")) {
            mainContextId = mainContext.getContextId();
            
            // 提交Callable任务
            Future<String> future = executor.submit(() -> {
                ManagedThreadContext workerContext = ManagedThreadContext.current();
                assertNotNull(workerContext);
                
                // 验证可以执行任务操作
                TaskNode task = workerContext.startTask("workerTask");
                assertNotNull(task);
                workerContext.endTask();
                
                return "success-" + workerContext.getContextId();
            });
            
            result = future.get(5, TimeUnit.SECONDS);
        }
        
        assertNotNull(result);
        assertTrue(result.startsWith("success-"));
    }
    
    @Test
    @DisplayName("无上下文时的正常执行")
    void testExecutionWithoutContext() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> hasContext = new AtomicReference<>();
        
        // 确保主线程没有上下文
        assertNull(ManagedThreadContext.current());
        
        executor.execute(() -> {
            try {
                // 工作线程也应该没有上下文
                ManagedThreadContext context = ManagedThreadContext.current();
                hasContext.set(context != null);
            } finally {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(hasContext.get(), "Should not have context when none exists");
    }
    
    @Test
    @DisplayName("批量任务提交")
    void testInvokeAll() throws Exception {
        java.util.List<Callable<String>> tasks = java.util.List.of(
            () -> "task1-" + Thread.currentThread().getName(),
            () -> "task2-" + Thread.currentThread().getName(),
            () -> "task3-" + Thread.currentThread().getName()
        );
        
        try (ManagedThreadContext context = ManagedThreadContext.create("batchTest")) {
            context.setAttribute("batchId", "batch123");
            
            java.util.List<Future<String>> results = executor.invokeAll(tasks);
            
            assertEquals(3, results.size());
            
            for (Future<String> result : results) {
                String value = result.get();
                assertNotNull(value);
                assertTrue(value.startsWith("task"));
            }
        }
    }
    
    @Test
    @DisplayName("任务异常处理")
    void testExceptionHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        try (ManagedThreadContext context = ManagedThreadContext.create("exceptionTest")) {
            
            Future<?> future = executor.submit(() -> {
                latch.countDown();
                throw new RuntimeException("Test exception");
            });
            
            // 等待任务开始
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            
            // 异常应该被Future捕获
            assertThrows(ExecutionException.class, () -> {
                future.get(5, TimeUnit.SECONDS);
            });
        }
    }
    
    @Test
    @DisplayName("线程池状态管理")
    void testExecutorLifecycle() {
        assertFalse(executor.isShutdown());
        assertFalse(executor.isTerminated());
        
        executor.shutdown();
        assertTrue(executor.isShutdown());
        
        // 等待终止
        try {
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(executor.isTerminated());
        } catch (InterruptedException e) {
            fail("Shutdown interrupted");
        }
    }
    
    @Test
    @DisplayName("静态工厂方法")
    void testStaticFactoryMethods() {
        // 测试newThreadPool
        TFIAwareExecutor customPool = TFIAwareExecutor.newThreadPool(
            1, 2, 60L, TimeUnit.SECONDS);
        assertNotNull(customPool);
        assertFalse(customPool.isShutdown());
        customPool.shutdown();
        
        // 测试newFixedThreadPool
        TFIAwareExecutor fixedPool = TFIAwareExecutor.newFixedThreadPool(3);
        assertNotNull(fixedPool);
        assertFalse(fixedPool.isShutdown());
        fixedPool.shutdown();
    }
}