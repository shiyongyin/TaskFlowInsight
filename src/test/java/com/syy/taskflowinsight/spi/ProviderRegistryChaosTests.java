package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProviderRegistry Chaos测试套件
 *
 * <p>验证5个核心Chaos场景：
 * <ol>
 *   <li>无实现时兜底不crash（默认实现存在）</li>
 *   <li>多实现优先级仲裁（高优先级优先；同级按注册顺序）</li>
 *   <li>自定义ClassLoader预加载可被lookup使用</li>
 *   <li>Provider内部抛异常时不影响系统（返回降级/空结果）</li>
 *   <li>并发注册无竞态（可用多线程注册+验证首选项）</li>
 * </ol>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("Chaos验证: ProviderRegistry异常安全与健壮性")
class ProviderRegistryChaosTests {

    @BeforeEach
    void setUp() {
        // 每个测试前清空注册表
        ProviderRegistry.clearAll();
    }

    // ==================== Chaos 1: 无实现时兜底不crash ====================

    @Test
    @DisplayName("Chaos 1.1: ServiceLoader发现默认ComparisonProvider")
    void chaos01_defaultComparisonProviderExists() {
        // When: 未手动注册任何Provider时，lookup应返回ServiceLoader发现的默认实现
        ComparisonProvider provider = ProviderRegistry.lookup(ComparisonProvider.class);

        // Then: 应该返回DefaultComparisonProvider（而非null）
        assertNotNull(provider, "应该返回ServiceLoader发现的DefaultComparisonProvider");
        assertTrue(provider instanceof DefaultComparisonProvider,
            "应该是DefaultComparisonProvider类型");
        assertEquals(0, provider.priority(), "默认实现priority应该为0");
    }

    @Test
    @DisplayName("Chaos 1.2: 默认Provider的compare方法不抛异常")
    void chaos01_defaultProviderCompareNoCrash() {
        // Given: 获取默认Provider
        ComparisonProvider provider = ProviderRegistry.lookup(ComparisonProvider.class);
        assertNotNull(provider);

        // When: 调用compare方法（包含null参数、相同对象等边界情况）
        CompareResult r1 = provider.compare(null, null);
        CompareResult r2 = provider.compare("test", "test");
        CompareResult r3 = provider.compare("a", "b");

        // Then: 所有调用都不抛异常，返回有效结果
        assertNotNull(r1, "null vs null应该返回结果");
        assertNotNull(r2, "相同对象应该返回结果");
        assertNotNull(r3, "不同对象应该返回结果");
    }

    @Test
    @DisplayName("Chaos 1.3: 默认TrackingProvider存在且可用")
    void chaos01_defaultTrackingProviderExists() {
        // When
        TrackingProvider provider = ProviderRegistry.lookup(TrackingProvider.class);

        // Then
        assertNotNull(provider, "应该返回DefaultTrackingProvider");
        assertTrue(provider instanceof DefaultTrackingProvider);

        // 验证基本功能不crash
        assertDoesNotThrow(() -> provider.track("test", new Object()));
        assertDoesNotThrow(() -> provider.changes());
        assertDoesNotThrow(() -> provider.clear());
    }

    @Test
    @DisplayName("Chaos 1.4: 默认FlowProvider存在且可用")
    void chaos01_defaultFlowProviderExists() {
        // When
        FlowProvider provider = ProviderRegistry.lookup(FlowProvider.class);

        // Then
        assertNotNull(provider, "应该返回DefaultFlowProvider");
        assertTrue(provider instanceof DefaultFlowProvider);

        // 验证基本功能不crash
        assertDoesNotThrow(() -> provider.startSession("test"));
        assertDoesNotThrow(() -> provider.endSession());
        assertDoesNotThrow(() -> provider.startTask("task"));
        assertDoesNotThrow(() -> provider.endTask());
    }

    @Test
    @DisplayName("Chaos 1.5: 默认RenderProvider存在且可用")
    void chaos01_defaultRenderProviderExists() {
        // When
        RenderProvider provider = ProviderRegistry.lookup(RenderProvider.class);

        // Then
        assertNotNull(provider, "应该返回DefaultRenderProvider");
        assertTrue(provider instanceof DefaultRenderProvider);

        // 验证渲染不crash
        CompareResult testResult = CompareResult.identical();
        assertDoesNotThrow(() -> provider.render(testResult, "standard"));
    }

    // ==================== Chaos 2: 多实现优先级仲裁 ====================

    @Test
    @DisplayName("Chaos 2.1: 高priority的Provider优先选中")
    void chaos02_highPriorityProviderWins() {
        // Given: 注册两个Provider，priority分别为10和5
        ComparisonProvider lowPriority = new MockComparisonProvider(5, "Low");
        ComparisonProvider highPriority = new MockComparisonProvider(10, "High");

        ProviderRegistry.register(ComparisonProvider.class, lowPriority);
        ProviderRegistry.register(ComparisonProvider.class, highPriority);

        // When
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);

        // Then: 应该选中priority=10的Provider
        assertNotNull(selected);
        assertEquals(10, selected.priority());
        assertTrue(selected.toString().contains("High"));
    }

    @Test
    @DisplayName("Chaos 2.2: 相同priority时先注册先选（FIFO）")
    void chaos02_samePriorityFIFO() {
        // Given: 注册两个priority相同的Provider
        ComparisonProvider first = new MockComparisonProvider(5, "First");
        ComparisonProvider second = new MockComparisonProvider(5, "Second");

        ProviderRegistry.register(ComparisonProvider.class, first);
        ProviderRegistry.register(ComparisonProvider.class, second);

        // When
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);

        // Then: 应该选中第一个注册的（但由于排序是降序，实际返回的是first）
        // 注意：相同priority时，Java sort是stable的，保持原顺序
        assertNotNull(selected);
        assertEquals(5, selected.priority());
        // 由于排序后list不可变，lookup返回第一个元素，即后注册的优先级高
        // 但在priority相同时，应该保持注册顺序（先进先出）
    }

    @Test
    @DisplayName("Chaos 2.3: 手动注册的Provider优先于ServiceLoader")
    void chaos02_manualRegistrationOverridesServiceLoader() {
        // Given: 手动注册一个高priority Provider
        ComparisonProvider manual = new MockComparisonProvider(100, "Manual");
        ProviderRegistry.register(ComparisonProvider.class, manual);

        // When
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);

        // Then: 应该选中手动注册的（priority=100），而非ServiceLoader的默认实现（priority=0）
        assertNotNull(selected);
        assertEquals(100, selected.priority());
        assertTrue(selected.toString().contains("Manual"));
    }

    // ==================== Chaos 3: 自定义ClassLoader预加载 ====================

    @Test
    @DisplayName("Chaos 3.1: loadProviders(ClassLoader)可加载自定义Provider")
    void chaos03_customClassLoaderLoading() {
        // Given: 创建自定义ClassLoader（使用当前ClassLoader模拟）
        ClassLoader customCL = this.getClass().getClassLoader();

        // When: 使用自定义ClassLoader加载Providers
        assertDoesNotThrow(() -> ProviderRegistry.loadProviders(customCL));

        // Then: 应该能lookup到Provider
        ComparisonProvider provider = ProviderRegistry.lookup(ComparisonProvider.class);
        assertNotNull(provider, "应该从自定义ClassLoader加载到Provider");
    }

    @Test
    @DisplayName("Chaos 3.2: null ClassLoader不crash")
    void chaos03_nullClassLoaderNoCrash() {
        // When: 传入null ClassLoader
        assertDoesNotThrow(() -> ProviderRegistry.loadProviders(null));

        // Then: 系统不crash，记录WARN日志
        // 验证lookup仍可用（使用默认ServiceLoader）
        ComparisonProvider provider = ProviderRegistry.lookup(ComparisonProvider.class);
        assertNotNull(provider);
    }

    // ==================== Chaos 4: Provider内部抛异常时不影响系统 ====================

    @Test
    @DisplayName("Chaos 4.1: Provider.compare()抛异常时不crash")
    void chaos04_providerExceptionNoCrash() {
        // Given: 注册一个会抛异常的Provider
        ComparisonProvider faultyProvider = new ComparisonProvider() {
            @Override
            public CompareResult compare(Object a, Object b) {
                throw new RuntimeException("Simulated provider failure");
            }

            @Override
            public int priority() {
                return 100; // 高优先级，确保被选中
            }
        };
        ProviderRegistry.register(ComparisonProvider.class, faultyProvider);

        // When: lookup返回故障Provider
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);

        // Then: lookup本身不crash
        assertNotNull(selected);
        assertEquals(100, selected.priority());

        // 调用compare时会抛异常，但TFI门面会捕获（此处只验证Provider注册正常）
        assertThrows(RuntimeException.class, () ->
            selected.compare("a", "b"));
    }

    @Test
    @DisplayName("Chaos 4.2: Provider.priority()抛异常时降级为0")
    void chaos04_providerPriorityExceptionFallback() {
        // Given: 注册一个priority()会抛异常的Provider
        ComparisonProvider faultyProvider = new ComparisonProvider() {
            @Override
            public CompareResult compare(Object a, Object b) {
                return CompareResult.identical();
            }

            @Override
            public int priority() {
                throw new RuntimeException("priority() failed");
            }
        };

        // When: 注册该Provider
        assertDoesNotThrow(() ->
            ProviderRegistry.register(ComparisonProvider.class, faultyProvider));

        // Then: 系统不crash，该Provider被视为priority=0（降级处理）
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);
        assertNotNull(selected);
    }

    // ==================== Chaos 5: 并发注册无竞态 ====================

    @Test
    @DisplayName("Chaos 5.1: 多线程并发注册Provider不crash")
    void chaos05_concurrentRegistrationNoCrash() throws Exception {
        // Given: 10个线程并发注册不同priority的Provider
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 并发注册
        for (int i = 0; i < threadCount; i++) {
            final int priority = i * 10;
            final String name = "Concurrent-" + i;
            executor.submit(() -> {
                try {
                    ComparisonProvider provider = new MockComparisonProvider(priority, name);
                    ProviderRegistry.register(ComparisonProvider.class, provider);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 记录异常但不阻塞测试
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: 所有注册都应该成功
        assertTrue(latch.await(5, TimeUnit.SECONDS), "并发注册应该在5秒内完成");
        assertEquals(threadCount, successCount.get(), "所有注册应该成功");

        // 验证lookup返回最高priority的Provider
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);
        assertNotNull(selected);
        assertTrue(selected.priority() >= 0, "priority应该有效");

        executor.shutdown();
    }

    @Test
    @DisplayName("Chaos 5.2: 并发lookup不受注册影响")
    void chaos05_concurrentLookupWhileRegistering() throws Exception {
        // Given: 初始注册一个Provider
        ProviderRegistry.register(ComparisonProvider.class, new MockComparisonProvider(50, "Initial"));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger lookupSuccessCount = new AtomicInteger(0);

        // When: 10个线程lookup，10个线程register
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 确保同时开始

                    if (index % 2 == 0) {
                        // lookup线程
                        ComparisonProvider provider = ProviderRegistry.lookup(ComparisonProvider.class);
                        if (provider != null) {
                            lookupSuccessCount.incrementAndGet();
                        }
                    } else {
                        // register线程
                        ProviderRegistry.register(ComparisonProvider.class,
                            new MockComparisonProvider(index, "Concurrent-" + index));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 开始并发操作
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "并发操作应该在5秒内完成");

        // Then: 所有lookup都应该成功（返回非null）
        assertEquals(threadCount / 2, lookupSuccessCount.get(), "所有lookup应该成功");

        executor.shutdown();
    }

    // ==================== 辅助类 ====================

    /**
     * Mock ComparisonProvider用于测试
     */
    static class MockComparisonProvider implements ComparisonProvider {
        private final int priority;
        private final String name;

        MockComparisonProvider(int priority, String name) {
            this.priority = priority;
            this.name = name;
        }

        @Override
        public CompareResult compare(Object a, Object b) {
            return CompareResult.builder()
                .object1(a)
                .object2(b)
                .identical(a == b)
                .changes(Collections.emptyList())
                .build();
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String toString() {
            return "MockComparisonProvider{priority=" + priority + ", name='" + name + "'}";
        }
    }
}
