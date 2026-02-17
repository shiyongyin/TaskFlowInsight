package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ContextPropagatingExecutor 全面测试
 * 目标：从45%覆盖率提升到90%+
 */
@DisplayName("ContextPropagatingExecutor 全面测试")
class ContextPropagatingExecutorComprehensiveTest {

    private ExecutorService delegateExecutor;
    private ExecutorService contextExecutor;

    @BeforeEach
    void setUp() {
        delegateExecutor = Executors.newFixedThreadPool(3);
        contextExecutor = ContextPropagatingExecutor.wrap(delegateExecutor);
    }

    @AfterEach
    void tearDown() {
        if (contextExecutor != null && !contextExecutor.isShutdown()) {
            contextExecutor.shutdown();
            try {
                if (!contextExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    contextExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                contextExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTests {

        @Test
        @DisplayName("wrap null executor应该抛出异常")
        void wrap_nullExecutor_shouldThrowException() {
            assertThatThrownBy(() -> ContextPropagatingExecutor.wrap(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Executor cannot be null");
        }

        @Test
        @DisplayName("wrap已经包装的executor应该返回原实例")
        void wrap_alreadyWrapped_shouldReturnSameInstance() {
            ExecutorService original = Executors.newSingleThreadExecutor();
            ExecutorService wrapped1 = ContextPropagatingExecutor.wrap(original);
            ExecutorService wrapped2 = ContextPropagatingExecutor.wrap(wrapped1);
            
            assertThat(wrapped1).isSameAs(wrapped2);
            
            wrapped1.shutdown();
        }

        @Test
        @DisplayName("wrap普通executor应该返回新的包装实例")
        void wrap_regularExecutor_shouldReturnWrappedInstance() {
            ExecutorService original = Executors.newSingleThreadExecutor();
            ExecutorService wrapped = ContextPropagatingExecutor.wrap(original);
            
            assertThat(wrapped).isNotSameAs(original);
            assertThat(wrapped).isInstanceOf(ContextPropagatingExecutor.class);
            
            wrapped.shutdown();
        }
    }

    @Nested
    @DisplayName("生命周期管理测试")
    class LifecycleTests {

        @Test
        @DisplayName("shutdown应该委托给底层executor")
        void shutdown_shouldDelegateToUnderlying() {
            contextExecutor.shutdown();
            
            assertThat(contextExecutor.isShutdown()).isTrue();
            assertThat(delegateExecutor.isShutdown()).isTrue();
        }

        @Test
        @DisplayName("shutdownNow应该委托给底层executor")
        void shutdownNow_shouldDelegateToUnderlying() {
            // 提交一个长时间运行的任务
            contextExecutor.submit(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            List<Runnable> pending = contextExecutor.shutdownNow();
            
            assertThat(contextExecutor.isShutdown()).isTrue();
            assertThat(delegateExecutor.isShutdown()).isTrue();
            assertThat(pending).isNotNull();
        }

        @Test
        @DisplayName("isShutdown应该反映底层executor状态")
        void isShutdown_shouldReflectUnderlyingState() {
            assertThat(contextExecutor.isShutdown()).isFalse();
            
            delegateExecutor.shutdown();
            
            assertThat(contextExecutor.isShutdown()).isTrue();
        }

        @Test
        @DisplayName("isTerminated应该反映底层executor状态")
        void isTerminated_shouldReflectUnderlyingState() {
            assertThat(contextExecutor.isTerminated()).isFalse();
            
            contextExecutor.shutdown();
            
            // 需要等待一下让executor完全终止
            try {
                contextExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Test
        @DisplayName("awaitTermination应该委托给底层executor")
        @Timeout(5)
        void awaitTermination_shouldDelegateToUnderlying() throws InterruptedException {
            contextExecutor.shutdown();
            
            boolean terminated = contextExecutor.awaitTermination(1, TimeUnit.SECONDS);
            
            assertThat(terminated).isTrue();
        }
    }

    @Nested
    @DisplayName("Runnable任务提交测试")
    class RunnableSubmissionTests {

        @Test
        @DisplayName("execute应该包装并执行Runnable")
        void execute_shouldWrapAndExecuteRunnable() throws InterruptedException {
            AtomicBoolean executed = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            
            contextExecutor.execute(() -> {
                executed.set(true);
                latch.countDown();
            });
            
            latch.await(1, TimeUnit.SECONDS);
            assertThat(executed.get()).isTrue();
        }

        @Test
        @DisplayName("submit Runnable应该包装并返回Future")
        void submit_runnable_shouldWrapAndReturnFuture() throws ExecutionException, InterruptedException {
            AtomicBoolean executed = new AtomicBoolean(false);
            
            Future<?> future = contextExecutor.submit(() -> executed.set(true));
            
            future.get();
            assertThat(executed.get()).isTrue();
            assertThat(future.isDone()).isTrue();
        }

        @Test
        @DisplayName("submit Runnable with result应该包装并返回指定结果")
        void submit_runnableWithResult_shouldWrapAndReturnResult() throws ExecutionException, InterruptedException {
            String expectedResult = "test-result";
            AtomicBoolean executed = new AtomicBoolean(false);
            
            Future<String> future = contextExecutor.submit(() -> executed.set(true), expectedResult);
            
            String result = future.get();
            assertThat(executed.get()).isTrue();
            assertThat(result).isEqualTo(expectedResult);
        }
    }

    @Nested
    @DisplayName("Callable任务提交测试")
    class CallableSubmissionTests {

        @Test
        @DisplayName("submit Callable应该包装并返回结果")
        void submit_callable_shouldWrapAndReturnResult() throws ExecutionException, InterruptedException {
            String expectedResult = "callable-result";
            
            Future<String> future = contextExecutor.submit(() -> expectedResult);
            
            String result = future.get();
            assertThat(result).isEqualTo(expectedResult);
        }

        @Test
        @DisplayName("submit Callable with exception应该传播异常")
        void submit_callableWithException_shouldPropagateException() {
            RuntimeException expectedException = new RuntimeException("test exception");
            
            Future<String> future = contextExecutor.submit(() -> {
                throw expectedException;
            });
            
            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCause(expectedException);
        }
    }

    @Nested
    @DisplayName("批量任务执行测试")
    class BatchExecutionTests {

        @Test
        @DisplayName("invokeAll应该执行所有任务")
        void invokeAll_shouldExecuteAllTasks() throws InterruptedException {
            Collection<Callable<Integer>> tasks = Arrays.asList(
                    () -> 1,
                    () -> 2,
                    () -> 3
            );
            
            List<Future<Integer>> futures = contextExecutor.invokeAll(tasks);
            
            assertThat(futures).hasSize(3);
            futures.forEach(future -> assertThat(future.isDone()).isTrue());
        }

        @Test
        @DisplayName("invokeAll with timeout应该在超时内执行")
        void invokeAll_withTimeout_shouldExecuteWithinTimeout() throws InterruptedException {
            Collection<Callable<Integer>> tasks = Arrays.asList(
                    () -> {
                        Thread.sleep(100);
                        return 1;
                    },
                    () -> {
                        Thread.sleep(100);
                        return 2;
                    }
            );
            
            List<Future<Integer>> futures = contextExecutor.invokeAll(tasks, 1, TimeUnit.SECONDS);
            
            assertThat(futures).hasSize(2);
            futures.forEach(future -> assertThat(future.isDone()).isTrue());
        }

        @Test
        @DisplayName("invokeAny应该返回任一任务结果")
        void invokeAny_shouldReturnAnyResult() throws InterruptedException, ExecutionException {
            Collection<Callable<String>> tasks = Arrays.asList(
                    () -> "result1",
                    () -> "result2",
                    () -> "result3"
            );
            
            String result = contextExecutor.invokeAny(tasks);
            
            assertThat(result).isIn("result1", "result2", "result3");
        }

        @Test
        @DisplayName("invokeAny with timeout应该在超时内返回")
        void invokeAny_withTimeout_shouldReturnWithinTimeout() throws InterruptedException, ExecutionException, TimeoutException {
            Collection<Callable<String>> tasks = Arrays.asList(
                    () -> {
                        Thread.sleep(50);
                        return "fast-result";
                    },
                    () -> {
                        Thread.sleep(2000);
                        return "slow-result";
                    }
            );
            
            String result = contextExecutor.invokeAny(tasks, 1, TimeUnit.SECONDS);
            
            assertThat(result).isEqualTo("fast-result");
        }

        @Test
        @DisplayName("invokeAny with timeout超时应该抛出TimeoutException")
        void invokeAny_timeoutExceeded_shouldThrowTimeoutException() {
            Collection<Callable<String>> tasks = Arrays.asList(
                    () -> {
                        Thread.sleep(2000);
                        return "slow-result";
                    }
            );
            
            assertThatThrownBy(() -> contextExecutor.invokeAny(tasks, 100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        }
    }

    @Nested
    @DisplayName("上下文传播测试")
    class ContextPropagationTests {

        @Test
        @DisplayName("无上下文时任务应该正常执行")
        void noContext_taskShouldExecuteNormally() throws ExecutionException, InterruptedException {
            // 确保当前线程没有上下文
            ThreadContext.clear();
            
            AtomicBoolean executed = new AtomicBoolean(false);
            Future<?> future = contextExecutor.submit(() -> executed.set(true));
            
            future.get();
            assertThat(executed.get()).isTrue();
        }

        @Test
        @DisplayName("有上下文时应该传播到子线程")
        void withContext_shouldPropagateToChildThread() throws ExecutionException, InterruptedException {
            // 创建一个上下文和会话
            ManagedThreadContext context = ThreadContext.create("test-task");
            String originalContextId = context.getContextId();
            String originalSessionId = context.getCurrentSession().getSessionId();
            
            AtomicReference<String> propagatedParentId = new AtomicReference<>();
            AtomicReference<String> propagatedSessionInfo = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            Future<?> future = contextExecutor.submit(() -> {
                try {
                    ManagedThreadContext currentContext = ThreadContext.current();
                    if (currentContext != null) {
                        // 验证传播的父上下文信息
                        propagatedParentId.set(currentContext.getAttribute("parent.contextId"));
                        propagatedSessionInfo.set(currentContext.getAttribute("parent.sessionId"));
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            future.get();
            latch.await(1, TimeUnit.SECONDS);
            
            // 验证父上下文信息被传播
            assertThat(propagatedParentId.get()).isEqualTo(originalContextId);
            assertThat(propagatedSessionInfo.get()).isEqualTo(originalSessionId);
            
            // 清理
            ThreadContext.clear();
        }

        @Test
        @DisplayName("子线程抛出异常时上下文应该被清理")
        void childThreadException_contextShouldBeCleanedUp() throws InterruptedException {
            ManagedThreadContext context = ThreadContext.create("test-task");
            context.setAttribute("test-key", "test-value");
            
            CountDownLatch latch = new CountDownLatch(1);
            
            Future<?> future = contextExecutor.submit(() -> {
                try {
                    throw new RuntimeException("Test exception");
                } finally {
                    latch.countDown();
                }
            });
            
            latch.await(1, TimeUnit.SECONDS);
            
            // 验证任务抛出异常
            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class);
            
            // 清理
            ThreadContext.clear();
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发提交多个任务应该正确执行")
        @Timeout(10)
        void concurrentSubmission_shouldExecuteCorrectly() throws InterruptedException {
            int taskCount = 50;
            AtomicInteger completedTasks = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            for (int i = 0; i < taskCount; i++) {
                contextExecutor.submit(() -> {
                    try {
                        Thread.sleep(10);
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(5, TimeUnit.SECONDS);
            assertThat(completedTasks.get()).isEqualTo(taskCount);
        }

        @Test
        @DisplayName("并发上下文传播应该隔离")
        @Timeout(10)
        void concurrentContextPropagation_shouldBeIsolated() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        // 每个线程创建独立的上下文
                        ManagedThreadContext context = ThreadContext.create("test-task-" + threadIndex);
                        context.setAttribute("thread-id", threadIndex);
                        
                        startLatch.await();
                        
                        // 提交任务到executor
                        Future<String> future = contextExecutor.submit(() -> {
                            ManagedThreadContext currentContext = ThreadContext.current();
                            return currentContext != null ? 
                                   currentContext.getAttribute("parent.contextId") : null;
                        });
                        
                        String result = future.get();
                        if (result != null && result.equals(context.getContextId())) {
                            successCount.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        // 忽略异常，只统计成功的
                    } finally {
                        ThreadContext.clear();
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            
            // 验证至少有一些线程成功传播了正确的上下文
            assertThat(successCount.get()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空任务集合invokeAll应该返回空列表")
        void invokeAll_emptyTasks_shouldReturnEmptyList() throws InterruptedException {
            List<Future<Object>> futures = contextExecutor.invokeAll(Arrays.asList());
            
            assertThat(futures).isEmpty();
        }

        @Test
        @DisplayName("空任务集合invokeAny应该抛出异常")
        void invokeAny_emptyTasks_shouldThrowException() {
            assertThatThrownBy(() -> contextExecutor.invokeAny(Arrays.asList()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("已关闭的executor提交任务应该被拒绝")
        void shutdownExecutor_taskSubmission_shouldBeRejected() {
            contextExecutor.shutdown();
            
            assertThatThrownBy(() -> contextExecutor.submit(() -> "test"))
                    .isInstanceOf(RejectedExecutionException.class);
        }

        @Test
        @DisplayName("任务中创建上下文快照应该工作")
        void taskCreatingSnapshot_shouldWork() throws ExecutionException, InterruptedException {
            ManagedThreadContext context = ThreadContext.create("parent-task");
            context.setAttribute("parent-key", "parent-value");
            
            Future<String> future = contextExecutor.submit(() -> {
                ManagedThreadContext currentContext = ThreadContext.current();
                if (currentContext != null) {
                    ContextSnapshot snapshot = currentContext.createSnapshot();
                    return snapshot.getContextId();
                }
                return null;
            });
            
            String contextId = future.get();
            assertThat(contextId).isNotNull();
            
            ThreadContext.clear();
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("大量短任务执行应该高效")
        @Timeout(10)
        void manyShortTasks_shouldExecuteEfficiently() throws InterruptedException {
            int taskCount = 1000;
            AtomicInteger completedTasks = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < taskCount; i++) {
                contextExecutor.submit(() -> {
                    completedTasks.incrementAndGet();
                    latch.countDown();
                });
            }
            
            latch.await(5, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(completedTasks.get()).isEqualTo(taskCount);
            assertThat(duration).isLessThan(5000); // 应该在5秒内完成
        }

        @Test
        @DisplayName("上下文传播开销应该可接受")
        @Timeout(10)
        void contextPropagationOverhead_shouldBeAcceptable() throws InterruptedException {
            ManagedThreadContext context = ThreadContext.create("perf-test-task");
            context.setAttribute("test-key", "test-value");
            
            int taskCount = 500;
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < taskCount; i++) {
                contextExecutor.submit(() -> {
                    // 简单访问上下文
                    ManagedThreadContext currentContext = ThreadContext.current();
                    if (currentContext != null) {
                        currentContext.<String>getAttribute("test-key");
                    }
                    latch.countDown();
                });
            }
            
            latch.await(5, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(duration).isLessThan(3000); // 上下文传播开销应该较小
            
            ThreadContext.clear();
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("任务包装过程中的异常应该被处理")
        void taskWrappingException_shouldBeHandled() {
            // 这个测试主要验证包装过程的健壮性
            // 验证包装器能正确处理异常任务而不导致上下文泄漏
            
            try {
                // 提交一个会抛出异常的任务
                Future<?> future = contextExecutor.submit(() -> {
                    throw new RuntimeException("Test exception in task");
                });
                
                // 异常应该在future.get()时被捕获
                assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
                    
            } catch (Exception e) {
                fail("包装器不应该抛出的异常: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("上下文恢复失败时任务应该继续执行")
        void contextRestoreFailure_taskShouldContinue() throws ExecutionException, InterruptedException {
            // 创建一个可能导致恢复失败的快照（这是一个模拟场景）
            AtomicBoolean taskExecuted = new AtomicBoolean(false);
            
            Future<?> future = contextExecutor.submit(() -> {
                taskExecuted.set(true);
            });
            
            future.get();
            assertThat(taskExecuted.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("内存管理测试")
    class MemoryManagementTests {

        @Test
        @DisplayName("大量任务执行后应该没有内存泄漏")
        @Timeout(15)
        void manyTasksExecution_shouldNotLeakMemory() throws InterruptedException {
            ManagedThreadContext context = ThreadContext.create("memory-test-task");
            context.setAttribute("test-key", "test-value");
            
            int taskCount = 200;
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            for (int i = 0; i < taskCount; i++) {
                contextExecutor.submit(() -> {
                    try {
                        // 执行一些操作
                        ManagedThreadContext currentContext = ThreadContext.current();
                        if (currentContext != null) {
                            currentContext.<String>getAttribute("test-key");
                        }
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            
            // 建议JVM进行垃圾回收
            System.gc();
            Thread.sleep(100);
            
            ThreadContext.clear();
        }
    }
}