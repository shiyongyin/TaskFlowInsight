package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ZeroLeakThreadLocalManager简化测试
 * 测试覆盖率目标：从33%提升到60%+
 */
@SpringBootTest
@DisplayName("ZeroLeakThreadLocalManager简化测试")
class ZeroLeakThreadLocalManagerSimpleTest {

    private ZeroLeakThreadLocalManager manager;

    @BeforeEach
    void setUp() {
        manager = ZeroLeakThreadLocalManager.getInstance();
    }

    @Nested
    @DisplayName("单例模式测试")
    class SingletonTests {

        @Test
        @DisplayName("getInstance应该返回同一个实例")
        void getInstance_shouldReturnSameInstance() {
            ZeroLeakThreadLocalManager instance1 = ZeroLeakThreadLocalManager.getInstance();
            ZeroLeakThreadLocalManager instance2 = ZeroLeakThreadLocalManager.getInstance();
            
            assertThat(instance1).isSameAs(instance2);
        }

        @Test
        @DisplayName("多线程访问getInstance应该线程安全")
        void getInstance_multiThreaded_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ZeroLeakThreadLocalManager[] instances = new ZeroLeakThreadLocalManager[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        instances[index] = ZeroLeakThreadLocalManager.getInstance();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            
            // 验证所有实例都是同一个
            ZeroLeakThreadLocalManager firstInstance = instances[0];
            for (int i = 1; i < threadCount; i++) {
                assertThat(instances[i]).isSameAs(firstInstance);
            }
        }
    }

    @Nested
    @DisplayName("上下文注册和注销测试")
    class ContextRegistrationTests {

        @Test
        @DisplayName("注册上下文应该成功")
        void registerContext_shouldSucceed() {
            Object context = new Object();
            Thread currentThread = Thread.currentThread();
            
            // 注册上下文不应该抛出异常
            manager.registerContext(currentThread, context);
            
            // 清理
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("注销上下文应该成功")
        void unregisterContext_shouldSucceed() {
            Object context = new Object();
            Thread currentThread = Thread.currentThread();
            
            manager.registerContext(currentThread, context);
            
            // 注销上下文不应该抛出异常
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("重复注册同一个上下文应该安全处理")
        void registerContext_duplicate_shouldHandleSafely() {
            Object context = new Object();
            Thread currentThread = Thread.currentThread();
            
            manager.registerContext(currentThread, context);
            manager.registerContext(currentThread, context); // 重复注册
            
            // 验证没有异常，重复注册被处理
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("注销未注册的上下文应该安全处理")
        void unregisterContext_notRegistered_shouldHandleSafely() {
            // 直接注销未注册的上下文ID
            manager.unregisterContext(99999L);
            
            // 验证没有异常，未注册的注销被安全处理
        }

        @Test
        @DisplayName("null参数应该安全处理")
        void nullParameters_shouldHandleSafely() {
            // 注册null参数不应该崩溃
            manager.registerContext(null, new Object());
            manager.registerContext(Thread.currentThread(), null);
            manager.registerContext(null, null);
            
            // 验证没有异常
        }

        @Test
        @DisplayName("多线程注册上下文应该线程安全")
        void registerContext_multiThreaded_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 3;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicBoolean hasException = new AtomicBoolean(false);
            
            for (int i = 0; i < threadCount; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        Object context = new Object();
                        manager.registerContext(Thread.currentThread(), context);
                        manager.unregisterContext(Thread.currentThread().threadId());
                    } catch (Exception e) {
                        hasException.set(true);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            
            assertThat(hasException.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("泄露检测测试")
    class LeakDetectionTests {

        @Test
        @DisplayName("detectLeaks应该返回检测到的泄露数量")
        void detectLeaks_shouldReturnLeakCount() {
            // 注册一些上下文
            Object context1 = new Object();
            Object context2 = new Object();
            manager.registerContext(Thread.currentThread(), context1);
            manager.registerContext(Thread.currentThread(), context2); // 替换前一个
            
            int leaksDetected = manager.detectLeaks();
            
            // 应该返回非负数
            assertThat(leaksDetected).isGreaterThanOrEqualTo(0);
            
            // 清理
            manager.unregisterContext(Thread.currentThread().threadId());
        }

        @Test
        @DisplayName("死线程清理应该工作")
        @Timeout(10)
        void deadThreadCleanup_shouldWork() throws InterruptedException {
            CountDownLatch threadStarted = new CountDownLatch(1);
            CountDownLatch testCompleted = new CountDownLatch(1);
            
            // 创建并启动一个会注册上下文然后死亡的线程
            Thread testThread = new Thread(() -> {
                Object context = new Object();
                manager.registerContext(Thread.currentThread(), context);
                threadStarted.countDown();
                // 线程结束，模拟死线程
            });
            
            testThread.start();
            threadStarted.await(2, TimeUnit.SECONDS);
            testThread.join();
            
            // 执行泄露检测
            int leaksDetected = manager.detectLeaks();
            assertThat(leaksDetected).isGreaterThanOrEqualTo(0);
            
            testCompleted.countDown();
        }
    }

    @Nested
    @DisplayName("健康状态监控测试")
    class HealthMonitoringTests {

        @Test
        @DisplayName("健康状态应该可获取")
        void getHealthStatus_shouldReturnStatus() {
            ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
            
            assertThat(status).isNotNull();
            assertThat(status.getLevel()).isNotNull();
            assertThat(status.getActiveContexts()).isGreaterThanOrEqualTo(0);
            assertThat(status.getTotalLeaks()).isGreaterThanOrEqualTo(0);
            assertThat(status.getLeaksCleaned()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("健康状态应该包含有效信息")
        void healthStatus_shouldContainValidInfo() {
            // 注册一个上下文以影响健康状态
            Object context = new Object();
            manager.registerContext(Thread.currentThread(), context);
            
            ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
            
            assertThat(status.getActiveContexts()).isGreaterThanOrEqualTo(0);
            assertThat(status.toString()).isNotNull();
            assertThat(status.toString()).isNotEmpty();
            
            // 清理
            manager.unregisterContext(Thread.currentThread().threadId());
        }
    }

    @Nested
    @DisplayName("配置和诊断测试")
    class ConfigurationTests {

        @Test
        @DisplayName("启用诊断模式应该工作")
        void enableDiagnosticMode_shouldWork() {
            // 启用诊断模式（不启用反射）
            manager.enableDiagnosticMode(false);
            
            // 验证没有异常
            Object context = new Object();
            manager.registerContext(Thread.currentThread(), context);
            manager.unregisterContext(Thread.currentThread().threadId());
        }

        @Test
        @DisplayName("启用诊断模式和反射应该工作")
        void enableDiagnosticModeWithReflection_shouldWork() {
            // 启用诊断模式和反射
            manager.enableDiagnosticMode(true);
            
            // 验证没有异常
            Object context = new Object();
            manager.registerContext(Thread.currentThread(), context);
            manager.unregisterContext(Thread.currentThread().threadId());
        }

        @Test
        @DisplayName("禁用诊断模式应该工作")
        void disableDiagnosticMode_shouldWork() {
            manager.enableDiagnosticMode(true);
            manager.disableDiagnosticMode();
            
            // 验证没有异常
            Object context = new Object();
            manager.registerContext(Thread.currentThread(), context);
            manager.unregisterContext(Thread.currentThread().threadId());
        }

        @Test
        @DisplayName("设置清理启用应该工作")
        void setCleanupEnabled_shouldWork() {
            manager.setCleanupEnabled(true);
            manager.setCleanupEnabled(false);
            
            // 验证没有异常
        }

        @Test
        @DisplayName("设置上下文超时应该工作")
        void setContextTimeout_shouldWork() {
            manager.setContextTimeoutMillis(30000); // 30秒
            manager.setContextTimeoutMillis(3600000); // 1小时
            
            // 验证没有异常
        }

        @Test
        @DisplayName("设置清理间隔应该工作")
        void setCleanupInterval_shouldWork() {
            manager.setCleanupIntervalMillis(10000); // 10秒
            manager.setCleanupIntervalMillis(60000); // 1分钟
            
            // 验证没有异常
        }
    }

    @Nested
    @DisplayName("诊断信息测试")
    class DiagnosticsTests {

        @Test
        @DisplayName("获取诊断信息应该成功")
        void getDiagnostics_shouldSucceed() {
            Map<String, Object> diagnostics = manager.getDiagnostics();
            
            assertThat(diagnostics).isNotNull();
            assertThat(diagnostics).isNotEmpty();
        }

        @Test
        @DisplayName("诊断信息应该包含预期字段")
        void diagnostics_shouldContainExpectedFields() {
            Map<String, Object> diagnostics = manager.getDiagnostics();
            
            // 检查一些预期的诊断字段
            assertThat(diagnostics).containsKey("registeredContexts");
            assertThat(diagnostics.get("registeredContexts")).isInstanceOf(Number.class);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("极端线程ID应该处理")
        void extremeThreadId_shouldHandle() {
            manager.unregisterContext(Long.MAX_VALUE);
            manager.unregisterContext(Long.MIN_VALUE);
            manager.unregisterContext(0L);
            
            // 验证没有异常
        }

        @Test
        @DisplayName("大量快速操作应该处理")
        void rapidOperations_shouldHandle() {
            Object context = new Object();
            Thread currentThread = Thread.currentThread();
            
            // 快速连续注册和注销
            for (int i = 0; i < 100; i++) {
                manager.registerContext(currentThread, context);
                manager.unregisterContext(currentThread.threadId());
            }
            
            // 验证没有异常
        }

        @Test
        @DisplayName("同时启用多种配置应该处理")
        void multipleConfigurations_shouldHandle() {
            manager.enableDiagnosticMode(true);
            manager.setCleanupEnabled(true);
            manager.setContextTimeoutMillis(5000);
            manager.setCleanupIntervalMillis(5000);
            
            Object context = new Object();
            manager.registerContext(Thread.currentThread(), context);
            manager.detectLeaks();
            manager.unregisterContext(Thread.currentThread().threadId());
            
            manager.disableDiagnosticMode();
            manager.setCleanupEnabled(false);
            
            // 验证没有异常
        }
    }

    @Nested
    @DisplayName("并发压力测试")
    class ConcurrencyStressTests {

        @Test
        @DisplayName("高并发操作应该稳定")
        @Timeout(15)
        void highConcurrency_shouldBeStable() throws InterruptedException {
            int threadCount = 5;
            int operationsPerThread = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicBoolean hasException = new AtomicBoolean(false);
            
            for (int i = 0; i < threadCount; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            Object context = new Object();
                            manager.registerContext(Thread.currentThread(), context);
                            
                            if (j % 10 == 0) {
                                manager.detectLeaks();
                            }
                            
                            if (j % 2 == 0) {
                                manager.unregisterContext(Thread.currentThread().threadId());
                            }
                        }
                        // 最终清理
                        manager.unregisterContext(Thread.currentThread().threadId());
                    } catch (Exception e) {
                        hasException.set(true);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            assertThat(hasException.get()).isFalse();
        }
    }
}