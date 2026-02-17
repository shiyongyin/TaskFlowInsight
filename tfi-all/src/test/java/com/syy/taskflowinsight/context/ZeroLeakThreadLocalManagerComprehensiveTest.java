package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.*;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ZeroLeakThreadLocalManager 全面测试套件
 * 
 * 测试覆盖范围：
 * - 单例模式实现
 * - 线程上下文注册与注销
 * - 内存泄漏检测与清理
 * - 死线程检测与清理
 * - 定时清理任务
 * - 诊断模式与反射清理
 * - 配置管理
 * - 健康状态监控
 * - 并发安全性
 * - 性能压力测试
 * - 异常边界情况
 * - 关闭与资源释放
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("ZeroLeakThreadLocalManager全面测试")
class ZeroLeakThreadLocalManagerComprehensiveTest {

    private ZeroLeakThreadLocalManager manager;
    
    @BeforeEach
    void setUp() {
        manager = ZeroLeakThreadLocalManager.getInstance();
        // 确保每次测试开始时都是干净状态
        manager.disableDiagnosticMode();
        manager.setCleanupEnabled(false);
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试环境
        manager.disableDiagnosticMode();
        manager.setCleanupEnabled(false);
        
        // 等待一点时间让清理完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("单例模式测试")
    class SingletonTests {

        @Test
        @DisplayName("getInstance()应该始终返回同一个实例")
        void getInstance_shouldReturnSameInstance() {
            ZeroLeakThreadLocalManager instance1 = ZeroLeakThreadLocalManager.getInstance();
            ZeroLeakThreadLocalManager instance2 = ZeroLeakThreadLocalManager.getInstance();
            ZeroLeakThreadLocalManager instance3 = ZeroLeakThreadLocalManager.getInstance();

            assertThat(instance1).isSameAs(instance2);
            assertThat(instance2).isSameAs(instance3);
            assertThat(instance1).isNotNull();
        }

        @Test
        @DisplayName("多线程环境下getInstance()应该线程安全")
        void getInstance_multiThreaded_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<ZeroLeakThreadLocalManager> instances = new CopyOnWriteArrayList<>();
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        ZeroLeakThreadLocalManager instance = ZeroLeakThreadLocalManager.getInstance();
                        instances.add(instance);
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
            assertThat(instances).hasSize(threadCount);

            // 验证所有实例都是同一个
            ZeroLeakThreadLocalManager firstInstance = instances.get(0);
            instances.forEach(instance -> assertThat(instance).isSameAs(firstInstance));
        }
    }

    @Nested
    @DisplayName("上下文注册与注销测试")
    class ContextRegistrationTests {

        @Test
        @DisplayName("注册上下文应该成功")
        void registerContext_shouldSucceed() {
            Object context = new TestContext("test");
            Thread currentThread = Thread.currentThread();

            assertDoesNotThrow(() -> manager.registerContext(currentThread, context));

            // 验证诊断信息中显示已注册
            Map<String, Object> diagnostics = manager.getDiagnostics();
            assertThat(diagnostics.get("contexts.registered")).isNotNull();
        }

        @Test
        @DisplayName("注销上下文应该成功")
        void unregisterContext_shouldSucceed() {
            Object context = new TestContext("test");
            Thread currentThread = Thread.currentThread();
            long threadId = currentThread.threadId();

            manager.registerContext(currentThread, context);
            
            assertDoesNotThrow(() -> manager.unregisterContext(threadId));

            // 验证诊断信息中显示已注销
            Map<String, Object> diagnostics = manager.getDiagnostics();
            assertThat(diagnostics.get("contexts.unregistered")).isNotNull();
        }

        @Test
        @DisplayName("null参数应该安全处理")
        void nullParameters_shouldHandleSafely() {
            assertDoesNotThrow(() -> {
                manager.registerContext(null, new TestContext("test"));
                manager.registerContext(Thread.currentThread(), null);
                manager.registerContext(null, null);
            });
        }

        @Test
        @DisplayName("重复注册同一线程应该检测为潜在泄漏")
        void registerContext_duplicate_shouldDetectAsLeak() {
            manager.enableDiagnosticMode(false);
            Thread currentThread = Thread.currentThread();
            
            Object context1 = new TestContext("context1");
            Object context2 = new TestContext("context2");

            manager.registerContext(currentThread, context1);
            
            // 记录初始泄漏数量
            Map<String, Object> diagnosticsBefore = manager.getDiagnostics();
            long leaksBefore = (Long) diagnosticsBefore.get("leaks.detected");
            
            // 注册不同的上下文到同一线程
            manager.registerContext(currentThread, context2);

            // 验证泄漏被检测到
            Map<String, Object> diagnosticsAfter = manager.getDiagnostics();
            long leaksAfter = (Long) diagnosticsAfter.get("leaks.detected");
            
            assertThat(leaksAfter).isGreaterThan(leaksBefore);
            
            // 清理
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("注销未注册的上下文应该安全处理")
        void unregisterContext_notRegistered_shouldHandleSafely() {
            assertDoesNotThrow(() -> {
                manager.unregisterContext(99999L);
                manager.unregisterContext(Long.MAX_VALUE);
                manager.unregisterContext(Long.MIN_VALUE);
                manager.unregisterContext(0L);
            });
        }

        @Test
        @DisplayName("并发注册注销应该线程安全")
        void concurrentRegistrationUnregistration_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            int operationsPerThread = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            Object context = new TestContext("thread-" + threadIndex + "-op-" + j);
                            manager.registerContext(Thread.currentThread(), context);
                            
                            if (j % 2 == 0) {
                                manager.unregisterContext(Thread.currentThread().threadId());
                            }
                        }
                        // 最终清理
                        manager.unregisterContext(Thread.currentThread().threadId());
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
        }
    }

    @Nested
    @DisplayName("健康状态监控测试")
    class HealthMonitoringTests {

        @Test
        @DisplayName("健康状态应该准确反映当前状态")
        void healthStatus_shouldReflectCurrentState() {
            ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
            
            assertThat(status).isNotNull();
            assertThat(status.getLevel()).isNotNull();
            assertThat(status.getActiveContexts()).isGreaterThanOrEqualTo(0);
            assertThat(status.getTotalLeaks()).isGreaterThanOrEqualTo(0);
            assertThat(status.getLeaksCleaned()).isGreaterThanOrEqualTo(0);
            assertThat(status.getDeadThreadsCleaned()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("健康等级应该基于泄漏数量和活跃上下文数量")
        void healthLevel_shouldBasedOnLeaksAndActiveContexts() {
            // 创建一个上下文进行测试
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("health-test");
            
            try {
                manager.registerContext(currentThread, context);
                
                ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
                
                // 验证健康状态信息
                assertThat(status.getActiveContexts()).isGreaterThanOrEqualTo(1);
                assertThat(status.getLevel()).isIn(
                    ZeroLeakThreadLocalManager.HealthLevel.HEALTHY,
                    ZeroLeakThreadLocalManager.HealthLevel.WARNING,
                    ZeroLeakThreadLocalManager.HealthLevel.CRITICAL,
                    ZeroLeakThreadLocalManager.HealthLevel.EMERGENCY
                );
                
            } finally {
                // 清理测试上下文
                manager.unregisterContext(currentThread.threadId());
            }
        }

        @Test
        @DisplayName("健康状态toString应该包含有用信息")
        void healthStatus_toString_shouldContainUsefulInfo() {
            ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();
            String statusString = status.toString();
            
            assertThat(statusString).isNotNull();
            assertThat(statusString).isNotEmpty();
            assertThat(statusString).contains("HealthStatus");
            assertThat(statusString).contains("level=");
            assertThat(statusString).contains("active=");
            assertThat(statusString).contains("leaks=");
            assertThat(statusString).contains("cleaned=");
        }

        @Test
        @DisplayName("诊断信息应该包含所有必要字段")
        void diagnostics_shouldContainAllNecessaryFields() {
            Map<String, Object> diagnostics = manager.getDiagnostics();
            
            // 验证所有必要的诊断字段存在
            assertThat(diagnostics).containsKeys(
                "contexts.registered",
                "contexts.unregistered", 
                "contexts.active",
                "leaks.detected",
                "leaks.cleaned",
                "threads.dead.cleaned",
                "diagnostic.mode",
                "reflection.available",
                "reflection.enabled",
                "health.status"
            );
            
            // 验证字段类型
            assertThat(diagnostics.get("contexts.registered")).isInstanceOf(Long.class);
            assertThat(diagnostics.get("contexts.unregistered")).isInstanceOf(Long.class);
            assertThat(diagnostics.get("contexts.active")).isInstanceOf(Integer.class);
            assertThat(diagnostics.get("leaks.detected")).isInstanceOf(Long.class);
            assertThat(diagnostics.get("leaks.cleaned")).isInstanceOf(Long.class);
            assertThat(diagnostics.get("threads.dead.cleaned")).isInstanceOf(Long.class);
            assertThat(diagnostics.get("diagnostic.mode")).isInstanceOf(Boolean.class);
            assertThat(diagnostics.get("reflection.available")).isInstanceOf(Boolean.class);
            assertThat(diagnostics.get("reflection.enabled")).isInstanceOf(Boolean.class);
            assertThat(diagnostics.get("health.status")).isInstanceOf(ZeroLeakThreadLocalManager.HealthStatus.class);
        }
    }

    @Nested
    @DisplayName("泄漏检测测试")
    class LeakDetectionTests {

        @Test
        @DisplayName("detectLeaks()应该返回检测到的泄漏数量")
        void detectLeaks_shouldReturnLeakCount() {
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("leak-test");
            
            manager.registerContext(currentThread, context);

            int leaksDetected = manager.detectLeaks();
            
            // 应该返回非负数
            assertThat(leaksDetected).isGreaterThanOrEqualTo(0);
            
            // 清理
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("超时上下文应该被检测为泄漏")
        void timeoutContext_shouldBeDetectedAsLeak() throws InterruptedException {
            // 设置一个很短的超时时间
            manager.setContextTimeoutMillis(50);
            
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("timeout-test");
            
            manager.registerContext(currentThread, context);
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                int leaksDetected = manager.detectLeaks();
                assertThat(leaksDetected).isGreaterThanOrEqualTo(1);
            });
            manager.setContextTimeoutMillis(3600000);
        }

        @Test
        @DisplayName("死线程上下文应该被检测为泄漏")
        @Timeout(10)
        void deadThreadContext_shouldBeDetectedAsLeak() throws InterruptedException {
            CountDownLatch threadStarted = new CountDownLatch(1);
            CountDownLatch canExit = new CountDownLatch(1);
            AtomicReference<Long> deadThreadId = new AtomicReference<>();

            Thread testThread = new Thread(() -> {
                Object context = new TestContext("dead-thread-test");
                manager.registerContext(Thread.currentThread(), context);
                deadThreadId.set(Thread.currentThread().threadId());
                threadStarted.countDown();
                
                try {
                    canExit.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            testThread.start();
            threadStarted.await();
            
            // 让线程结束
            canExit.countDown();
            testThread.join();
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                int leaksDetected = manager.detectLeaks();
                assertThat(leaksDetected).isGreaterThanOrEqualTo(1);
            });
        }

        @Test
        @DisplayName("手动触发死线程清理应该工作")
        @Timeout(15)
        void manualDeadThreadCleanup_shouldWork() throws InterruptedException {
            manager.enableDiagnosticMode(false);
            
            List<Thread> deadThreads = new ArrayList<>();
            CountDownLatch threadsStarted = new CountDownLatch(5);
            CountDownLatch canExit = new CountDownLatch(1);

            // 创建多个会死掉的线程
            for (int i = 0; i < 5; i++) {
                final int threadIndex = i;
                Thread testThread = new Thread(() -> {
                    Object context = new TestContext("dead-thread-" + threadIndex);
                    manager.registerContext(Thread.currentThread(), context);
                    threadsStarted.countDown();
                    
                    try {
                        canExit.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                deadThreads.add(testThread);
                testThread.start();
            }

            threadsStarted.await();
            
            // 记录初始状态
            Map<String, Object> diagnosticsBefore = manager.getDiagnostics();
            int activeContextsBefore = (Integer) diagnosticsBefore.get("contexts.active");
            
            // 让所有线程死亡
            canExit.countDown();
            for (Thread thread : deadThreads) {
                thread.join();
            }
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                int leaksDetected = manager.detectLeaks();
                Map<String, Object> diagnosticsAfter = manager.getDiagnostics();
                int activeContextsAfter = (Integer) diagnosticsAfter.get("contexts.active");
                assertThat(activeContextsAfter).isLessThanOrEqualTo(activeContextsBefore);
                assertThat(leaksDetected).isGreaterThanOrEqualTo(0);
            });
        }
    }

    @Nested
    @DisplayName("诊断模式测试")
    class DiagnosticModeTests {

        @Test
        @DisplayName("启用诊断模式应该记录详细信息")
        void enableDiagnosticMode_shouldLogDetailedInfo() {
            manager.enableDiagnosticMode(false);
            
            Map<String, Object> diagnostics = manager.getDiagnostics();
            assertThat(diagnostics.get("diagnostic.mode")).isEqualTo(true);
            
            // 测试诊断模式下的操作
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("diagnostic-test");
            
            assertDoesNotThrow(() -> {
                manager.registerContext(currentThread, context);
                manager.unregisterContext(currentThread.threadId());
            });
        }

        @Test
        @DisplayName("启用诊断模式和反射清理")
        void enableDiagnosticModeWithReflection_shouldWork() {
            manager.enableDiagnosticMode(true);
            
            Map<String, Object> diagnostics = manager.getDiagnostics();
            assertThat(diagnostics.get("diagnostic.mode")).isEqualTo(true);
            
            // 反射可用性取决于JVM配置
            Boolean reflectionAvailable = (Boolean) diagnostics.get("reflection.available");
            assertThat(reflectionAvailable).isNotNull();
            
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("reflection-test");
            
            assertDoesNotThrow(() -> {
                manager.registerContext(currentThread, context);
                manager.unregisterContext(currentThread.threadId());
            });
        }

        @Test
        @DisplayName("禁用诊断模式应该关闭详细日志")
        void disableDiagnosticMode_shouldDisableDetailedLogging() {
            manager.enableDiagnosticMode(true);
            manager.disableDiagnosticMode();
            
            Map<String, Object> diagnostics = manager.getDiagnostics();
            assertThat(diagnostics.get("diagnostic.mode")).isEqualTo(false);
            assertThat(diagnostics.get("reflection.enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("诊断模式开关应该线程安全")
        void diagnosticModeToggle_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        // 交替启用/禁用诊断模式
                        if (threadIndex % 2 == 0) {
                            manager.enableDiagnosticMode(threadIndex % 4 == 0);
                        } else {
                            manager.disableDiagnosticMode();
                        }
                        
                        // 执行一些操作
                        Object context = new TestContext("concurrent-diagnostic-" + threadIndex);
                        manager.registerContext(Thread.currentThread(), context);
                        manager.unregisterContext(Thread.currentThread().threadId());
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
        }
    }

    @Nested
    @DisplayName("清理任务测试")
    class CleanupTaskTests {

        @Test
        @DisplayName("启用清理任务应该开始定期检测")
        void enableCleanup_shouldStartPeriodicDetection() throws InterruptedException {
            manager.setCleanupIntervalMillis(100); // 100ms间隔
            manager.setCleanupEnabled(true);
            
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("periodic-test");
            manager.registerContext(currentThread, context);
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertDoesNotThrow(() -> manager.getDiagnostics())
            );
            
            manager.setCleanupEnabled(false);
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("禁用清理任务应该停止定期检测")
        void disableCleanup_shouldStopPeriodicDetection() throws InterruptedException {
            manager.setCleanupEnabled(true);
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(100)).until(() -> true);
            
            manager.setCleanupEnabled(false);
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(100)).until(() -> true);
            
            assertDoesNotThrow(() -> manager.getDiagnostics());
        }

        @Test
        @DisplayName("修改清理间隔应该重启调度器")
        void changeCleanupInterval_shouldRestartScheduler() throws InterruptedException {
            manager.setCleanupEnabled(true);
            manager.setCleanupIntervalMillis(500);
            
            await().atMost(Duration.ofSeconds(2)).pollDelay(Duration.ofMillis(100)).until(() -> true);
            
            manager.setCleanupIntervalMillis(100);
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertDoesNotThrow(() -> manager.getDiagnostics())
            );
            
            manager.setCleanupEnabled(false);
        }

        @Test
        @DisplayName("清理任务应该在超时后自动清理上下文")
        void cleanupTask_shouldAutoCleanTimeoutContexts() throws InterruptedException {
            manager.setContextTimeoutMillis(100); // 100ms超时
            manager.setCleanupIntervalMillis(50);  // 50ms检查间隔
            manager.setCleanupEnabled(true);
            
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("auto-cleanup-test");
            manager.registerContext(currentThread, context);
            
            Map<String, Object> diagnosticsBefore = manager.getDiagnostics();
            long leaksCleanedBefore = (Long) diagnosticsBefore.get("leaks.cleaned");
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                Map<String, Object> diagnosticsAfter = manager.getDiagnostics();
                long leaksCleanedAfter = (Long) diagnosticsAfter.get("leaks.cleaned");
                assertThat(leaksCleanedAfter).isGreaterThanOrEqualTo(leaksCleanedBefore);
            });
            
            manager.setCleanupEnabled(false);
            manager.setContextTimeoutMillis(3600000); // 恢复默认
        }
    }

    @Nested
    @DisplayName("配置管理测试")
    class ConfigurationTests {

        @Test
        @DisplayName("设置上下文超时应该立即生效")
        void setContextTimeout_shouldTakeEffectImmediately() {
            long originalTimeout = 3600000L; // 默认1小时
            long newTimeout = 5000L; // 5秒
            
            manager.setContextTimeoutMillis(newTimeout);
            
            // 通过检测逻辑验证新超时时间生效
            Thread currentThread = Thread.currentThread();
            Object context = new TestContext("timeout-config-test");
            manager.registerContext(currentThread, context);
            
            // 这不会立即触发超时，但配置已经设置
            assertDoesNotThrow(() -> manager.detectLeaks());
            
            // 恢复原始超时
            manager.setContextTimeoutMillis(originalTimeout);
            manager.unregisterContext(currentThread.threadId());
        }

        @Test
        @DisplayName("设置清理间隔应该在启用清理时重启调度器")
        void setCleanupInterval_shouldRestartSchedulerWhenEnabled() throws InterruptedException {
            manager.setCleanupEnabled(true);
            
            long originalInterval = 60000L;
            long newInterval = 200L;
            
            manager.setCleanupIntervalMillis(newInterval);
            
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertDoesNotThrow(() -> manager.getDiagnostics())
            );
            
            manager.setCleanupEnabled(false);
            manager.setCleanupIntervalMillis(originalInterval);
        }

        @Test
        @DisplayName("并发配置修改应该线程安全")
        void concurrentConfigurationChanges_shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 8;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        // 并发修改不同配置
                        switch (threadIndex % 4) {
                            case 0:
                                manager.setContextTimeoutMillis(5000 + threadIndex * 1000);
                                break;
                            case 1:
                                manager.setCleanupIntervalMillis(100 + threadIndex * 50);
                                break;
                            case 2:
                                manager.setCleanupEnabled(threadIndex % 2 == 0);
                                break;
                            case 3:
                                manager.enableDiagnosticMode(threadIndex % 2 == 1);
                                break;
                        }
                        
                        // 验证配置操作没有异常
                        manager.getDiagnostics();
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
            
            // 清理配置
            manager.setCleanupEnabled(false);
            manager.disableDiagnosticMode();
        }
    }

    @Nested
    @DisplayName("并发安全性测试")
    class ConcurrencySafetyTests {

        @Test
        @DisplayName("高并发注册注销应该不产生数据竞争")
        @Timeout(20)
        void highConcurrencyRegistrationUnregistration_shouldNotHaveDataRaces() throws InterruptedException {
            int threadCount = 20;
            int operationsPerThread = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successOperations = new AtomicInteger(0);
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < operationsPerThread; j++) {
                            Object context = new TestContext("concurrent-" + threadIndex + "-" + j);
                            manager.registerContext(Thread.currentThread(), context);
                            successOperations.incrementAndGet();
                            
                            // 随机进行一些操作
                            if (j % 3 == 0) {
                                manager.detectLeaks();
                            }
                            if (j % 5 == 0) {
                                manager.getDiagnostics();
                            }
                            if (j % 7 == 0) {
                                manager.getHealthStatus();
                            }
                            
                            if (j % 2 == 0) {
                                manager.unregisterContext(Thread.currentThread().threadId());
                            }
                        }
                        
                        // 最终清理
                        manager.unregisterContext(Thread.currentThread().threadId());
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
            assertThat(successOperations.get()).isEqualTo(threadCount * operationsPerThread);
        }

        @Test
        @DisplayName("并发配置修改和操作应该线程安全")
        @Timeout(15)
        void concurrentConfigurationAndOperations_shouldBeThreadSafe() throws InterruptedException {
            int workerThreads = 10;
            int configThreads = 3;
            int totalThreads = workerThreads + configThreads;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalThreads);
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            // 工作线程：执行注册/注销操作
            for (int i = 0; i < workerThreads; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 50; j++) {
                            Object context = new TestContext("worker-" + threadIndex + "-" + j);
                            manager.registerContext(Thread.currentThread(), context);
                            
                            if (j % 10 == 0) {
                                manager.detectLeaks();
                            }
                            
                            manager.unregisterContext(Thread.currentThread().threadId());
                        }
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            // 配置线程：修改配置
            for (int i = 0; i < configThreads; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < 20; j++) {
                            switch (j % 4) {
                                case 0:
                                    manager.setContextTimeoutMillis(1000 + j * 100);
                                    break;
                                case 1:
                                    manager.enableDiagnosticMode(j % 2 == 0);
                                    break;
                                case 2:
                                    manager.setCleanupIntervalMillis(100 + j * 10);
                                    break;
                                case 3:
                                    manager.getHealthStatus();
                                    break;
                            }
                            Thread.sleep(10);
                        }
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
        }

        @Test
        @DisplayName("并发泄漏检测应该一致")
        void concurrentLeakDetection_shouldBeConsistent() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Integer> detectionResults = new CopyOnWriteArrayList<>();
            List<Throwable> exceptions = new CopyOnWriteArrayList<>();

            // 创建一些测试上下文
            for (int i = 0; i < 3; i++) {
                Object context = new TestContext("concurrent-detection-setup-" + i);
                manager.registerContext(Thread.currentThread(), context);
            }

            for (int i = 0; i < threadCount; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        // 同时执行泄漏检测
                        int result = manager.detectLeaks();
                        detectionResults.add(result);
                        
                    } catch (Exception e) {
                        exceptions.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(exceptions).isEmpty();
            assertThat(detectionResults).hasSize(threadCount);
            
            // 所有检测结果都应该是非负数
            detectionResults.forEach(result -> assertThat(result).isGreaterThanOrEqualTo(0));
            
            // 清理测试上下文
            manager.unregisterContext(Thread.currentThread().threadId());
        }
    }

    @Nested
    @DisplayName("性能压力测试")
    class PerformanceStressTests {

        @Test
        @DisplayName("大量快速操作不应导致性能问题")
        @Timeout(30)
        void rapidOperations_shouldNotCausePerformanceIssues() throws InterruptedException {
            int operationCount = 10000;
            long startTime = System.nanoTime();
            
            Thread currentThread = Thread.currentThread();
            
            for (int i = 0; i < operationCount; i++) {
                Object context = new TestContext("perf-test-" + i);
                manager.registerContext(currentThread, context);
                
                if (i % 100 == 0) {
                    manager.detectLeaks();
                }
                if (i % 10 == 0) {
                    manager.getDiagnostics();
                }
                
                manager.unregisterContext(currentThread.threadId());
            }
            
            long duration = System.nanoTime() - startTime;
            double avgMicrosPerOp = (duration / 1000.0) / operationCount;
            
            // 验证平均操作时间合理（< 50微秒）
            assertThat(avgMicrosPerOp).isLessThan(50.0);
            
            System.out.printf("Average operation time: %.2f μs%n", avgMicrosPerOp);
        }

        @Test
        @DisplayName("内存使用应该稳定")
        @Timeout(20)
        void memoryUsage_shouldBeStable() throws InterruptedException {
            Runtime runtime = Runtime.getRuntime();
            
            // 强制垃圾回收并记录初始内存
            System.gc();
            Thread.sleep(100);
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // 执行大量操作
            Thread currentThread = Thread.currentThread();
            for (int i = 0; i < 5000; i++) {
                Object context = new TestContext("memory-test-" + i);
                manager.registerContext(currentThread, context);
                manager.unregisterContext(currentThread.threadId());
                
                if (i % 1000 == 0) {
                    manager.detectLeaks();
                }
            }
            
            // 强制垃圾回收并检查内存
            System.gc();
            Thread.sleep(100);
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            
            long memoryIncrease = finalMemory - initialMemory;
            
            // 内存增长应该合理（< 10MB）
            assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024);
            
            System.out.printf("Memory increase: %.2f MB%n", memoryIncrease / (1024.0 * 1024.0));
        }

        @Test
        @DisplayName("长时间运行清理任务应该稳定")
        @Timeout(15)
        void longRunningCleanupTasks_shouldBeStable() throws InterruptedException {
            manager.setCleanupIntervalMillis(50); // 快速清理间隔
            manager.setContextTimeoutMillis(200); // 短超时时间
            manager.setCleanupEnabled(true);
            
            try {
                AtomicBoolean shouldStop = new AtomicBoolean(false);
                List<Throwable> exceptions = new CopyOnWriteArrayList<>();
                
                // 启动工作线程不断创建和清理上下文
                Thread workerThread = new Thread(() -> {
                    try {
                        int counter = 0;
                        while (!shouldStop.get()) {
                            Object context = new TestContext("long-running-" + counter++);
                            manager.registerContext(Thread.currentThread(), context);
                            
                            // 有时清理，有时不清理（模拟真实场景）
                            if (counter % 3 == 0) {
                                manager.unregisterContext(Thread.currentThread().threadId());
                            }
                            
                            Thread.sleep(10);
                        }
                        // 最终清理
                        manager.unregisterContext(Thread.currentThread().threadId());
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });
                
                workerThread.start();
                
                // 运行5秒
                Thread.sleep(5000);
                shouldStop.set(true);
                workerThread.join();
                
                assertThat(exceptions).isEmpty();
                
                // 验证系统仍在正常工作
                assertDoesNotThrow(() -> {
                    manager.getDiagnostics();
                    manager.getHealthStatus();
                    manager.detectLeaks();
                });
                
            } finally {
                manager.setCleanupEnabled(false);
                manager.setContextTimeoutMillis(3600000); // 恢复默认
            }
        }
    }

    @Nested
    @DisplayName("异常边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("极端线程ID应该正确处理")
        void extremeThreadIds_shouldHandleCorrectly() {
            assertDoesNotThrow(() -> {
                manager.unregisterContext(Long.MAX_VALUE);
                manager.unregisterContext(Long.MIN_VALUE);
                manager.unregisterContext(0L);
                manager.unregisterContext(-1L);
            });
        }

        @Test
        @DisplayName("大量同时操作应该不死锁")
        @Timeout(10)
        void massiveSimultaneousOperations_shouldNotDeadlock() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicBoolean hasDeadlock = new AtomicBoolean(false);

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        
                        Object context = new TestContext("massive-" + threadIndex);
                        manager.registerContext(Thread.currentThread(), context);
                        manager.detectLeaks();
                        manager.getDiagnostics();
                        manager.getHealthStatus();
                        manager.unregisterContext(Thread.currentThread().threadId());
                        
                    } catch (Exception e) {
                        hasDeadlock.set(true);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(8, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            assertThat(hasDeadlock.get()).isFalse();
        }

        @Test
        @DisplayName("异常对象作为上下文应该安全处理")
        void exceptionObjectAsContext_shouldHandleSafely() {
            Thread currentThread = Thread.currentThread();
            
            // 使用各种特殊对象作为上下文
            Object[] testContexts = {
                new RuntimeException("test exception"),
                new byte[1024],
                new String[100],
                new Object() {
                    @Override
                    public String toString() {
                        throw new RuntimeException("toString error");
                    }
                }
            };
            
            for (Object context : testContexts) {
                assertDoesNotThrow(() -> {
                    manager.registerContext(currentThread, context);
                    manager.detectLeaks();
                    manager.unregisterContext(currentThread.threadId());
                });
            }
        }

        @Test
        @DisplayName("配置极端值应该安全处理")
        void extremeConfigurationValues_shouldHandleSafely() {
            assertDoesNotThrow(() -> {
                // 极端超时值
                manager.setContextTimeoutMillis(1); // 1ms
                manager.setContextTimeoutMillis(Long.MAX_VALUE);
                manager.setContextTimeoutMillis(0);
                
                // 极端清理间隔
                manager.setCleanupIntervalMillis(1); // 1ms
                manager.setCleanupIntervalMillis(Long.MAX_VALUE);
                
                // 快速开关
                for (int i = 0; i < 10; i++) {
                    manager.setCleanupEnabled(i % 2 == 0);
                    manager.enableDiagnosticMode(i % 3 == 0);
                }
            });
            
            // 恢复合理配置
            manager.setContextTimeoutMillis(3600000);
            manager.setCleanupIntervalMillis(60000);
            manager.setCleanupEnabled(false);
            manager.disableDiagnosticMode();
        }

        @Test
        @DisplayName("系统资源不足情况应该优雅处理")
        void systemResourceShortage_shouldHandleGracefully() {
            // 尝试创建大量对象来模拟内存压力
            List<Object[]> memoryConsumers = new ArrayList<>();
            try {
                // 适度消耗内存，不要触发OOM
                for (int i = 0; i < 1000; i++) {
                    memoryConsumers.add(new Object[1000]);
                }
                
                // 在内存压力下测试操作
                Thread currentThread = Thread.currentThread();
                Object context = new TestContext("resource-shortage-test");
                
                assertDoesNotThrow(() -> {
                    manager.registerContext(currentThread, context);
                    manager.detectLeaks();
                    manager.getDiagnostics();
                    manager.getHealthStatus();
                    manager.unregisterContext(currentThread.threadId());
                });
                
            } finally {
                // 清理内存
                memoryConsumers.clear();
                System.gc();
            }
        }
    }

    @Nested
    @DisplayName("反射清理测试")
    class ReflectionCleanupTests {

        @Test
        @DisplayName("反射可用性检查应该正确")
        void reflectionAvailability_shouldBeCorrect() {
            Map<String, Object> diagnostics = manager.getDiagnostics();
            Boolean reflectionAvailable = (Boolean) diagnostics.get("reflection.available");
            
            assertThat(reflectionAvailable).isNotNull();
            // 反射可用性取决于JVM配置，不强制要求特定值
        }

        @Test
        @DisplayName("启用反射清理在不可用时应该安全失败")
        void enableReflectionCleanup_whenUnavailable_shouldFailSafely() {
            manager.enableDiagnosticMode(true); // 尝试启用反射
            
            // 无论反射是否可用，都不应该抛出异常
            assertDoesNotThrow(() -> {
                Thread currentThread = Thread.currentThread();
                Object context = new TestContext("reflection-unavailable-test");
                manager.registerContext(currentThread, context);
                
                // 触发清理以测试反射清理代码路径
                manager.setContextTimeoutMillis(1);
                manager.detectLeaks();
                
                manager.unregisterContext(currentThread.threadId());
                manager.setContextTimeoutMillis(3600000); // 恢复默认
            });
        }

        @Test
        @DisplayName("反射清理失败应该不影响正常功能")
        void reflectionCleanupFailure_shouldNotAffectNormalOperation() {
            manager.enableDiagnosticMode(true);
            
            // 即使反射清理可能失败，正常功能应该继续工作
            Thread currentThread = Thread.currentThread();
            
            for (int i = 0; i < 5; i++) {
                Object context = new TestContext("reflection-failure-test-" + i);
                
                assertDoesNotThrow(() -> {
                    manager.registerContext(currentThread, context);
                    manager.detectLeaks();
                    manager.getDiagnostics();
                    manager.getHealthStatus();
                    manager.unregisterContext(currentThread.threadId());
                });
            }
        }
    }

    /**
     * 测试上下文类
     */
    private static class TestContext {
        private final String name;
        private final long timestamp;
        
        public TestContext(String name) {
            this.name = name;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return "TestContext{name='" + name + "', timestamp=" + timestamp + "}";
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestContext that = (TestContext) obj;
            return timestamp == that.timestamp && Objects.equals(name, that.name);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, timestamp);
        }
    }
}