package com.syy.taskflowinsight.performance;

import com.syy.taskflowinsight.performance.monitor.*;
import com.syy.taskflowinsight.performance.dashboard.PerformanceDashboard;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeepOptimized;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.config.TfiConfig;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 综合性能测试套件 - TFI性能监控与基准测试完整验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>采用综合性能测试策略，覆盖实时监控、基准测试、Dashboard三大模块</li>
 *   <li>使用Spring Boot集成测试环境，验证完整的性能监控栈</li>
 *   <li>通过@Order注解确保测试执行顺序，避免相互干扰</li>
 *   <li>使用Optional依赖注入处理组件可选性，提高测试健壮性</li>
 *   <li>结合实际业务场景设计性能压力测试</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>实时监控：</strong>性能指标收集、SLA违规检测、内存压力监控</li>
 *   <li><strong>基准测试：</strong>深度快照、路径匹配缓存、多项基准测试执行</li>
 *   <li><strong>告警机制：</strong>SLA配置、告警监听器、违规检测</li>
 *   <li><strong>Dashboard：</strong>概览、报告、基准测试、告警信息</li>
 *   <li><strong>并发性能：</strong>10线程×100操作的并发性能测试</li>
 *   <li><strong>性能比较：</strong>基线与当前性能的对比分析</li>
 *   <li><strong>专项测试：</strong>深度快照、路径匹配等专项性能验证</li>
 * </ul>
 * 
 * <h2>性能基准要求：</h2>
 * <ul>
 *   <li><strong>深度快照：</strong>P95<10ms, P99<50ms（复杂对象5层深度）</li>
 *   <li><strong>路径匹配：</strong>平均<1μs（缓存命中场景）</li>
 *   <li><strong>并发处理：</strong>10线程×100操作，10秒内完成</li>
 *   <li><strong>SLA监控：</strong>实时检测延迟、吞吐量、错误率违规</li>
 *   <li><strong>内存监控：</strong>堆内存使用率实时跟踪</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>实时监控：</strong>100次操作+10%失败率，SLA违规告警</li>
 *   <li><strong>内存压力：</strong>创建100MB内存负载，监控堆使用率</li>
 *   <li><strong>基准测试：</strong>执行完整基准测试套件，生成文本和Markdown报告</li>
 *   <li><strong>深度快照：</strong>复杂对象快照性能，1000次迭代统计</li>
 *   <li><strong>路径缓存：</strong>100个路径×3种模式×10000次匹配</li>
 *   <li><strong>并发压力：</strong>10线程并发执行，性能数据聚合验证</li>
 *   <li><strong>Dashboard：</strong>概览、报告、基准、告警四大功能</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>监控准确：</strong>性能指标准确收集，告警及时触发</li>
 *   <li><strong>性能达标：</strong>所有性能基准都满足预设阈值</li>
 *   <li><strong>报告完整：</strong>基准测试报告格式正确，数据完整</li>
 *   <li><strong>并发稳定：</strong>多线程环境下性能监控稳定可靠</li>
 *   <li><strong>Dashboard正常：</strong>所有Dashboard功能正常工作</li>
 *   <li><strong>集成无误：</strong>各组件集成测试无异常</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tfi.performance.enabled=true",
    "tfi.performance.monitor.enabled=true",
    "tfi.performance.monitor.interval-ms=1000"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TFI性能测试套件")
public class PerformanceTestSuite {
    
    private final Optional<PerformanceMonitor> performanceMonitor;
    private final Optional<BenchmarkRunner> benchmarkRunner;
    private final Optional<PerformanceDashboard> dashboard;
    
    public PerformanceTestSuite(Optional<PerformanceMonitor> performanceMonitor,
                               Optional<BenchmarkRunner> benchmarkRunner,
                               Optional<PerformanceDashboard> dashboard) {
        this.performanceMonitor = performanceMonitor;
        this.benchmarkRunner = benchmarkRunner;
        this.dashboard = dashboard;
    }
    
    private static final int WARMUP_ITERATIONS = 100;
    private static final int TEST_ITERATIONS = 1000;
    
    @BeforeEach
    void setUp() {
        if (performanceMonitor.isPresent()) {
            performanceMonitor.get().resetAll();
        }
    }
    
    // ========== 实时监控测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("实时性能监控功能测试")
    void testRealtimeMonitoring() throws InterruptedException {
        assumeMonitorAvailable();
        
        // 执行一些操作并监控
        for (int i = 0; i < 100; i++) {
            try (PerformanceMonitor.Timer timer = performanceMonitor.get().startTimer("test_operation")) {
                Thread.sleep(1); // 模拟操作
                if (i % 10 == 9) {
                    timer.setSuccess(false); // 模拟10%失败率
                }
            }
        }
        
        // 等待监控收集数据
        Thread.sleep(1500);
        
        // 获取报告
        PerformanceReport report = performanceMonitor.get().getReport();
        assertThat(report).isNotNull();
        assertThat(report.getMetrics()).containsKey("test_operation");
        
        MetricSnapshot snapshot = report.getMetrics().get("test_operation");
        assertThat(snapshot.getTotalOps()).isGreaterThanOrEqualTo(100);
        assertThat(snapshot.getErrorRate()).isBetween(0.08, 0.12); // 约10%
    }
    
    @Test
    @Order(2)
    @DisplayName("SLA违规检测测试")
    void testSLAViolationDetection() throws InterruptedException {
        assumeMonitorAvailable();
        
        // 配置严格的SLA
        SLAConfig strictSLA = SLAConfig.builder()
            .metricName("slow_operation")
            .maxLatencyMs(1)  // 1ms
            .minThroughput(1000)
            .maxErrorRate(0.01)
            .build();
        
        performanceMonitor.get().configureSLA("slow_operation", strictSLA);
        
        // 注册告警监听器
        List<Alert> receivedAlerts = new ArrayList<>();
        performanceMonitor.get().registerAlertListener(receivedAlerts::add);
        
        // 执行会违反SLA的操作
        for (int i = 0; i < 50; i++) {
            try (PerformanceMonitor.Timer timer = performanceMonitor.get().startTimer("slow_operation")) {
                Thread.sleep(5); // 5ms，超过SLA
            }
        }
        
        // 等待告警触发
        Thread.sleep(1000);
        
        // 验证告警
        assertThat(receivedAlerts).isNotEmpty();
        assertThat(receivedAlerts).anyMatch(a -> a.getKey().contains("slow_operation.latency"));
    }
    
    @Test
    @Order(3)
    @DisplayName("内存压力监控测试")
    void testMemoryPressureMonitoring() throws InterruptedException {
        assumeMonitorAvailable();
        
        List<Alert> alerts = new ArrayList<>();
        performanceMonitor.get().registerAlertListener(alerts::add);
        
        // 创建内存压力
        List<byte[]> memoryHog = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                memoryHog.add(new byte[1024 * 1024]); // 1MB块
                
                try (PerformanceMonitor.Timer timer = performanceMonitor.get().startTimer("memory_test")) {
                    // 操作
                }
            }
        } catch (OutOfMemoryError e) {
            // 预期可能发生
        }
        
        // 等待监控检测
        Thread.sleep(1500);
        
        PerformanceReport report = performanceMonitor.get().getReport();
        assertThat(report.getHeapUsagePercent()).isGreaterThan(0);
        
        // 清理
        memoryHog.clear();
        System.gc();
    }
    
    // ========== 基准测试 ==========
    
    @Test
    @Order(4)
    @DisplayName("基准测试运行器功能测试")
    void testBenchmarkRunner() {
        assumeBenchmarkRunnerAvailable();
        
        BenchmarkReport report = benchmarkRunner.get().runAll();
        assertThat(report).isNotNull();
        assertThat(report.getResults()).isNotEmpty();
        
        // 验证各项测试都执行了
        assertThat(report.getResults()).containsKeys(
            "change_tracking",
            "object_snapshot",
            "path_matching",
            "collection_summary",
            "concurrent_tracking"
        );
        
        // 生成报告
        String textReport = report.generateTextReport();
        assertThat(textReport).contains("TFI Performance Benchmark Report");
        
        String markdownReport = report.generateMarkdownReport();
        assertThat(markdownReport).contains("# TFI Performance Benchmark Report");
    }
    
    @Test
    @Order(5)
    @DisplayName("深度快照性能测试")
    void testDeepSnapshotPerformance() {
        SnapshotConfig config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(5);
        ObjectSnapshotDeepOptimized snapshot = new ObjectSnapshotDeepOptimized(config);
        
        // 预热
        ComplexTestObject obj = createComplexObject();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            snapshot.captureDeep(obj, 5, Collections.emptySet(), Collections.emptySet());
        }
        
        // 性能测试
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            snapshot.captureDeep(obj, 5, Collections.emptySet(), Collections.emptySet());
            durations.add(System.nanoTime() - start);
        }
        
        // 计算统计
        Collections.sort(durations);
        double p95 = durations.get((int) (durations.size() * 0.95)) / 1_000_000.0; // 转换为毫秒
        double p99 = durations.get((int) (durations.size() * 0.99)) / 1_000_000.0;
        
        System.out.printf("Deep Snapshot Performance - P95: %.2fms, P99: %.2fms%n", p95, p99);
        
        // 验证性能达标
        assertThat(p95).isLessThan(10); // P95 < 10ms
        assertThat(p99).isLessThan(50); // P99 < 50ms
    }
    
    @Test
    @Order(6)
    @DisplayName("路径匹配缓存性能测试")
    void testPathMatcherCachePerformance() {
        TfiConfig mockConfig = createMockTfiConfig();
        CaffeinePathMatcherCache cache = new CaffeinePathMatcherCache(mockConfig, null);
        
        List<String> paths = IntStream.range(0, 100)
            .mapToObj(i -> "user.profile" + i + ".settings.privacy")
            .toList();
        
        List<String> patterns = Arrays.asList("user.**", "*.settings.*", "**.privacy");
        
        // 预热缓存
        for (String path : paths) {
            for (String pattern : patterns) {
                cache.matches(path, pattern);
            }
        }
        
        // 测试缓存命中性能
        long start = System.nanoTime();
        int matches = 0;
        for (int i = 0; i < 10000; i++) {
            String path = paths.get(i % paths.size());
            String pattern = patterns.get(i % patterns.size());
            if (cache.matches(path, pattern)) {
                matches++;
            }
        }
        long duration = System.nanoTime() - start;
        
        double avgMicros = duration / 10000.0 / 1000.0;
        System.out.printf("Path Matcher Cache - Avg: %.2fμs, Matches: %d%n", avgMicros, matches);
        
        // 验证缓存性能
        assertThat(avgMicros).isLessThan(1); // 平均 < 1μs
    }
    
    // ========== Dashboard测试 ==========
    
    @Test
    @Order(7)
    @DisplayName("性能Dashboard功能测试")
    void testPerformanceDashboard() {
        assumeDashboardAvailable();
        
        // 获取概览
        Map<String, Object> overview = dashboard.get().overview();
        assertThat(overview).containsKeys("status", "timestamp", "metrics_summary", "system_health");
        
        // 获取报告
        Map<String, Object> realtimeReport = dashboard.get().report("realtime");
        assertThat(realtimeReport).isNotEmpty();
        
        // 运行基准测试
        Map<String, Object> benchmarkResult = dashboard.get().benchmark("snapshot");
        assertThat(benchmarkResult).containsKey("status");
        
        // 获取告警信息
        Map<String, Object> alerts = dashboard.get().alerts();
        assertThat(alerts).containsKeys("total_alerts", "recent_alerts");
    }
    
    @Test
    @Order(8)
    @DisplayName("并发性能测试")
    void testConcurrentPerformance() throws InterruptedException {
        assumeMonitorAvailable();
        
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String operation = "thread_" + threadId;
                        try (PerformanceMonitor.Timer timer = performanceMonitor.get().startTimer(operation)) {
                            // 模拟操作
                            Thread.sleep(0, 100000); // 0.1ms
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executor.shutdown();
        
        // 验证所有线程的操作都被记录
        Thread.sleep(1500); // 等待监控收集
        PerformanceReport report = performanceMonitor.get().getReport();
        
        for (int t = 0; t < threadCount; t++) {
            String operation = "thread_" + t;
            assertThat(report.getMetrics()).containsKey(operation);
            MetricSnapshot snapshot = report.getMetrics().get(operation);
            assertThat(snapshot.getTotalOps()).isGreaterThanOrEqualTo(operationsPerThread);
        }
    }
    
    @Test
    @Order(9)
    @DisplayName("性能比较测试")
    void testPerformanceComparison() {
        assumeBenchmarkRunnerAvailable();
        
        // 运行基线测试
        BenchmarkReport baseline = benchmarkRunner.get().runAll();
        
        // 运行当前测试
        BenchmarkReport current = benchmarkRunner.get().runAll();
        
        // 比较结果
        String comparison = BenchmarkReport.compare(baseline, current);
        assertThat(comparison).contains("Performance Comparison");
        
        System.out.println(comparison);
    }
    
    // ========== 辅助方法 ==========
    
    private void assumeMonitorAvailable() {
        Assumptions.assumeTrue(performanceMonitor.isPresent(), 
            "Performance monitor not available");
    }
    
    private void assumeBenchmarkRunnerAvailable() {
        Assumptions.assumeTrue(benchmarkRunner.isPresent(), 
            "Benchmark runner not available");
    }
    
    private void assumeDashboardAvailable() {
        Assumptions.assumeTrue(dashboard.isPresent(), 
            "Performance dashboard not available");
    }
    
    private ComplexTestObject createComplexObject() {
        ComplexTestObject obj = new ComplexTestObject();
        obj.id = UUID.randomUUID().toString();
        obj.name = "Test Object";
        obj.values = IntStream.range(0, 100).boxed().toList();
        obj.properties = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            obj.properties.put("key" + i, "value" + i);
        }
        obj.nested = new ComplexTestObject();
        obj.nested.id = UUID.randomUUID().toString();
        obj.nested.name = "Nested Object";
        return obj;
    }
    
    private TfiConfig createMockTfiConfig() {
        return new TfiConfig(true, null, null, null, null);
    }
    
    static class ComplexTestObject {
        String id;
        String name;
        List<Integer> values;
        Map<String, String> properties;
        ComplexTestObject nested;
    }
}