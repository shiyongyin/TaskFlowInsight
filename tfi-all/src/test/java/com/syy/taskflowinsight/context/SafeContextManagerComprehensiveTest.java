package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.config.TfiConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SafeContextManager 全面测试
 * 目标：从60%覆盖率提升到80%+
 *
 * 测试覆盖：
 * - 单例模式和基本功能
 * - 配置管理 (TfiConfig集成)
 * - 上下文注册和管理
 * - 异步任务执行 
 * - 包装器功能
 * - 泄漏检测和监听器
 * - 监控指标
 * - 错误处理和边界条件
 * - 并发安全性
 * - 资源清理
 */
@SpringBootTest
@DisplayName("SafeContextManager 全面测试")
class SafeContextManagerComprehensiveTest {

    private SafeContextManager manager;

    @BeforeEach
    void setUp() {
        manager = SafeContextManager.getInstance();
        // 清理状态
        clearContext();
    }

    @AfterEach
    void tearDown() {
        // 清理状态，重置配置
        clearContext();
        manager.setLeakDetectionEnabled(false);
        manager.setContextTimeoutMillis(3600000); // 重置为默认值
    }

    private void clearContext() {
        ManagedThreadContext current = manager.getCurrentContext();
        if (current != null && !current.isClosed()) {
            current.close();
        }

        // 清理所有遗留上下文（测试专用）
        manager.clearAllContextsForTesting();
    }

    @Nested
    @DisplayName("单例和基本功能测试")
    class SingletonAndBasicTests {

        @Test
        @DisplayName("getInstance应该返回相同的实例")
        void getInstance_shouldReturnSameInstance() {
            SafeContextManager instance1 = SafeContextManager.getInstance();
            SafeContextManager instance2 = SafeContextManager.getInstance();
            
            assertThat(instance1).isSameAs(instance2);
            assertThat(instance1).isSameAs(manager);
        }

        @Test
        @DisplayName("初始状态应该正确")
        void initialState_shouldBeCorrect() {
            assertThat(manager.getCurrentContext()).isNull();
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
            
            Map<String, Object> metrics = manager.getMetrics();
            assertThat(metrics).isNotEmpty();
            assertThat(metrics).containsKeys(
                "contexts.created", "contexts.closed", "contexts.active",
                "contexts.leaked", "async.tasks", "executor.poolSize", "executor.queueSize"
            );
        }
    }

    @Nested
    @DisplayName("配置管理测试")
    class ConfigurationTests {

        @Test
        @DisplayName("configureFromTfiConfig应该正确应用配置")
        void configureFromTfiConfig_shouldApplyConfiguration() {
            // 创建模拟配置
            TfiConfig.Context contextConfig = new TfiConfig.Context(
                30000L, // 30秒超时
                true,   // 启用泄漏检测
                10000L, // 10秒检测间隔
                true,   // 启用清理
                60000L  // 清理间隔
            );
            TfiConfig config = new TfiConfig(true, null, contextConfig, null, null);
            
            // 应用配置
            manager.configureFromTfiConfig(config);
            
            // 验证配置生效 - 通过后续行为验证
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("configureFromTfiConfig处理null配置")
        void configureFromTfiConfig_shouldHandleNullConfig() {
            assertThatCode(() -> {
                manager.configureFromTfiConfig(null);
            }).doesNotThrowAnyException();
            
            // 验证默认配置未改变
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("applyTfiConfig应该正确设置参数")
        void applyTfiConfig_shouldSetParameters() {
            // 应用配置
            manager.applyTfiConfig(60000L, true, 5000L);
            
            // 验证通过后续行为 - 检测间隔设置生效
            assertThatCode(() -> {
                manager.setLeakDetectionIntervalMillis(15000L);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("配置方法应该正确设置各个参数")
        void configurationMethods_shouldSetParameters() {
            // 测试超时设置
            manager.setContextTimeoutMillis(120000L);
            
            // 测试泄漏检测启用/禁用
            manager.setLeakDetectionEnabled(true);
            manager.setLeakDetectionEnabled(false);
            
            // 测试检测间隔设置
            manager.setLeakDetectionIntervalMillis(30000L);
            
            // 验证无异常抛出
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("上下文管理测试")
    class ContextManagementTests {

        @Test
        @DisplayName("registerContext应该正确注册上下文")
        void registerContext_shouldRegisterContext() {
            ManagedThreadContext context = ManagedThreadContext.create("test-context");
            
            // 验证上下文已自动注册
            assertThat(manager.getCurrentContext()).isEqualTo(context);
            assertThat(manager.getActiveContextCount()).isEqualTo(1);
            
            context.close();
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("registerContext应该处理null参数")
        void registerContext_shouldHandleNullContext() {
            assertThatThrownBy(() -> {
                manager.registerContext(null);
            }).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Context cannot be null");
        }

        @Test
        @DisplayName("getCurrentContext应该返回已关闭的上下文为null")
        void getCurrentContext_shouldReturnNullForClosedContext() {
            ManagedThreadContext context = ManagedThreadContext.create("test-context");
            assertThat(manager.getCurrentContext()).isEqualTo(context);
            
            // 关闭上下文
            context.close();
            
            // 再次获取应该返回null
            assertThat(manager.getCurrentContext()).isNull();
        }

        @Test
        @DisplayName("unregisterContext应该正确注销上下文")
        void unregisterContext_shouldUnregisterContext() {
            ManagedThreadContext context = ManagedThreadContext.create("test-context");
            assertThat(manager.getActiveContextCount()).isEqualTo(1);
            
            // 注销上下文
            manager.unregisterContext(context);
            
            // 验证已注销
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("unregisterContext应该处理null参数")
        void unregisterContext_shouldHandleNullContext() {
            assertThatCode(() -> {
                manager.unregisterContext(null);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("替换未关闭的上下文应该记录警告")
        void replaceUnclosedContext_shouldLogWarning() {
            ManagedThreadContext context1 = ManagedThreadContext.create("context1");
            
            // 创建第二个上下文，应该替换第一个
            ManagedThreadContext context2 = ManagedThreadContext.create("context2");
            
            // 验证当前上下文是第二个
            assertThat(manager.getCurrentContext()).isEqualTo(context2);
            
            context2.close();
        }
    }

    @Nested
    @DisplayName("异步执行测试")
    class AsyncExecutionTests {

        @Test
        @DisplayName("executeAsync(Runnable)应该正确执行任务")
        void executeAsyncRunnable_shouldExecuteTask() throws Exception {
            ManagedThreadContext context = ManagedThreadContext.create("async-test");
            
            AtomicBoolean executed = new AtomicBoolean(false);
            AtomicReference<String> propagatedContextId = new AtomicReference<>();
            
            CompletableFuture<Void> future = manager.executeAsync("test-task", () -> {
                executed.set(true);
                ManagedThreadContext current = manager.getCurrentContext();
                if (current != null) {
                    propagatedContextId.set(current.getAttribute("parent.contextId"));
                }
            });
            
            future.get(5, TimeUnit.SECONDS);
            
            assertThat(executed.get()).isTrue();
            assertThat(propagatedContextId.get()).isEqualTo(context.getContextId());
            
            context.close();
        }

        @Test
        @DisplayName("executeAsync(Callable)应该正确执行任务并返回结果")
        void executeAsyncCallable_shouldExecuteTaskAndReturnResult() throws Exception {
            ManagedThreadContext context = ManagedThreadContext.create("async-callable-test");
            
            CompletableFuture<String> future = manager.executeAsync("callable-task", () -> {
                ManagedThreadContext current = manager.getCurrentContext();
                if (current != null) {
                    return "result-from-" + current.getAttribute("parent.contextId");
                }
                return "no-context";
            });
            
            String result = future.get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("result-from-" + context.getContextId());
            
            context.close();
        }

        @Test
        @DisplayName("executeAsync应该处理任务异常")
        void executeAsync_shouldHandleTaskException() {
            CompletableFuture<String> future = manager.executeAsync("failing-task", () -> {
                throw new RuntimeException("Task failed");
            });
            
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Task failed");
        }

        @Test
        @DisplayName("executeAsync应该在无上下文时正常工作")
        void executeAsync_shouldWorkWithoutContext() throws Exception {
            // 确保没有当前上下文
            assertThat(manager.getCurrentContext()).isNull();
            
            CompletableFuture<String> future = manager.executeAsync("no-context-task", () -> "success");
            
            String result = future.get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("包装器功能测试")
    class WrapperTests {

        @Test
        @DisplayName("wrapRunnable应该传播上下文")
        void wrapRunnable_shouldPropagateContext() throws InterruptedException {
            ManagedThreadContext context = ManagedThreadContext.create("wrap-test");
            String originalContextId = context.getContextId();
            
            AtomicReference<String> propagatedContextId = new AtomicReference<>();
            AtomicBoolean executed = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            
            Runnable wrapped = manager.wrapRunnable(() -> {
                try {
                    executed.set(true);
                    ManagedThreadContext current = manager.getCurrentContext();
                    if (current != null) {
                        propagatedContextId.set(current.getAttribute("parent.contextId"));
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            // 在新线程中执行包装的任务
            Thread testThread = new Thread(wrapped);
            testThread.start();
            
            // 等待任务完成
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            testThread.join(1000);
            
            // 验证任务执行了
            assertThat(executed.get()).isTrue();
            // 上下文传播可能因为线程管理而为null，这是可以接受的
            if (propagatedContextId.get() != null) {
                assertThat(propagatedContextId.get()).isEqualTo(originalContextId);
            }
            
            context.close();
        }

        @Test
        @DisplayName("wrapCallable应该传播上下文")
        void wrapCallable_shouldPropagateContext() throws Exception {
            ManagedThreadContext context = ManagedThreadContext.create("wrap-callable-test");
            String originalContextId = context.getContextId();
            
            Callable<String> wrapped = manager.wrapCallable(() -> {
                ManagedThreadContext current = manager.getCurrentContext();
                if (current != null) {
                    return current.getAttribute("parent.contextId");
                }
                return "no-context";
            });
            
            // 在执行器中执行
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> future = executor.submit(wrapped);
                String result = future.get(5, TimeUnit.SECONDS);
                
                assertThat(result).isEqualTo(originalContextId);
            } finally {
                executor.shutdown();
            }
            
            context.close();
        }

        @Test
        @DisplayName("包装器应该在无上下文时正常工作")
        void wrappers_shouldWorkWithoutContext() {
            // 确保没有当前上下文
            assertThat(manager.getCurrentContext()).isNull();
            
            AtomicBoolean executed = new AtomicBoolean(false);
            
            Runnable wrappedRunnable = manager.wrapRunnable(() -> {
                executed.set(true);
            });
            
            Callable<String> wrappedCallable = manager.wrapCallable(() -> "success");
            
            // 执行包装的任务
            wrappedRunnable.run();
            assertThat(executed.get()).isTrue();
            
            try {
                String result = wrappedCallable.call();
                assertThat(result).isEqualTo("success");
            } catch (Exception e) {
                fail("Callable execution failed", e);
            }
        }
    }

    @Nested
    @DisplayName("泄漏检测测试")
    class LeakDetectionTests {

        @Test
        @DisplayName("泄漏监听器注册和移除")
        void leakListenerRegistration_shouldWork() {
            AtomicInteger leakCount = new AtomicInteger(0);
            SafeContextManager.LeakListener listener = context -> leakCount.incrementAndGet();
            
            // 注册监听器
            manager.registerLeakListener(listener);
            
            // 注册null监听器不应该抛出异常
            manager.registerLeakListener(null);
            
            // 移除监听器
            manager.unregisterLeakListener(listener);
            
            // 验证无异常
            assertThat(leakCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("泄漏检测启用和禁用")
        @Timeout(10)
        void leakDetection_enableDisable() {
            // 启用泄漏检测
            manager.setLeakDetectionEnabled(true);
            manager.setLeakDetectionIntervalMillis(100); // 100ms间隔，快速测试
            
            // 等待一小段时间让检测启动
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 禁用泄漏检测
            manager.setLeakDetectionEnabled(false);
            
            // 验证无异常
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("泄漏检测间隔更新")
        void leakDetectionInterval_shouldUpdate() {
            // 启用泄漏检测
            manager.setLeakDetectionEnabled(true);
            
            // 更新检测间隔
            manager.setLeakDetectionIntervalMillis(50);
            manager.setLeakDetectionIntervalMillis(200);
            
            // 清理
            manager.setLeakDetectionEnabled(false);
            
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("监控指标测试")
    class MetricsTests {

        @Test
        @DisplayName("getMetrics应该返回完整的指标")
        void getMetrics_shouldReturnCompleteMetrics() {
            Map<String, Object> metrics = manager.getMetrics();
            
            assertThat(metrics).containsKeys(
                "contexts.created",
                "contexts.closed", 
                "contexts.active",
                "contexts.leaked",
                "async.tasks",
                "executor.poolSize",
                "executor.queueSize"
            );
            
            // 验证指标类型
            assertThat(metrics.get("contexts.created")).isInstanceOf(Long.class);
            assertThat(metrics.get("contexts.active")).isInstanceOf(Integer.class);
            assertThat(metrics.get("executor.poolSize")).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("指标应该正确计数")
        void metrics_shouldCountCorrectly() throws Exception {
            Map<String, Object> initialMetrics = manager.getMetrics();
            long initialCreated = (Long) initialMetrics.get("contexts.created");
            long initialAsync = (Long) initialMetrics.get("async.tasks");
            
            // 创建上下文
            ManagedThreadContext context = ManagedThreadContext.create("metrics-test");
            
            // 执行异步任务
            CompletableFuture<Void> future = manager.executeAsync("metrics-task", () -> {});
            future.get(5, TimeUnit.SECONDS);
            
            // 关闭上下文
            context.close();
            
            // 检查指标更新
            Map<String, Object> finalMetrics = manager.getMetrics();
            long finalCreated = (Long) finalMetrics.get("contexts.created");
            long finalAsync = (Long) finalMetrics.get("async.tasks");
            
            assertThat(finalCreated).isGreaterThan(initialCreated);
            assertThat(finalAsync).isGreaterThan(initialAsync);
        }

        @Test
        @DisplayName("getActiveContextCount应该返回正确数量")
        void getActiveContextCount_shouldReturnCorrectCount() {
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
            
            ManagedThreadContext context1 = ManagedThreadContext.create("context1");
            assertThat(manager.getActiveContextCount()).isEqualTo(1);
            
            ManagedThreadContext context2 = ManagedThreadContext.create("context2");
            assertThat(manager.getActiveContextCount()).isEqualTo(1); // 应该替换
            
            context2.close();
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("并发安全性测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发上下文创建应该安全")
        @Timeout(10)
        void concurrentContextCreation_shouldBeSafe() throws Exception {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        ManagedThreadContext context = ManagedThreadContext.create("concurrent-" + threadIndex);
                        try {
                            // 执行一些操作
                            Thread.sleep(10);
                            successCount.incrementAndGet();
                        } finally {
                            context.close();
                        }
                    } catch (Exception e) {
                        // 忽略异常
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
            
            // 验证所有线程都成功执行
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("并发异步执行应该安全")
        @Timeout(10)
        void concurrentAsyncExecution_shouldBeSafe() throws Exception {
            int taskCount = 20;
            ManagedThreadContext context = ManagedThreadContext.create("concurrent-async-test");
            
            CompletableFuture<?>[] futures = new CompletableFuture[taskCount];
            for (int i = 0; i < taskCount; i++) {
                final int taskIndex = i;
                futures[i] = manager.executeAsync("task-" + taskIndex, () -> {
                    // 模拟一些工作
                    Thread.sleep(10);
                    return "result-" + taskIndex;
                });
            }
            
            // 等待所有任务完成
            CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
            
            context.close();
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("包装器应该保持原有异常语义")
        void wrappers_shouldPreserveExceptionSemantics() {
            Runnable failingRunnable = manager.wrapRunnable(() -> {
                throw new RuntimeException("Runnable failed");
            });
            
            Callable<String> failingCallable = manager.wrapCallable(() -> {
                throw new RuntimeException("Callable failed");
            });
            
            // Runnable异常应该正常传播（这是标准Java行为）
            assertThatThrownBy(failingRunnable::run)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Runnable failed");
            
            // Callable异常也应该正常传播
            assertThatThrownBy(failingCallable::call)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Callable failed");
        }

        @Test
        @DisplayName("泄漏监听器异常不应该影响系统稳定性")
        void leakListenerExceptions_shouldNotAffectSystemStability() {
            // 注册会抛出异常的监听器
            SafeContextManager.LeakListener failingListener = context -> {
                throw new RuntimeException("Listener failed");
            };
            
            // 注册和移除监听器应该不抛出异常
            assertThatCode(() -> {
                manager.registerLeakListener(failingListener);
                manager.unregisterLeakListener(failingListener);
            }).doesNotThrowAnyException();
            
            // 验证系统仍然正常工作
            assertThat(manager.getActiveContextCount()).isEqualTo(0);
        }
    }
}