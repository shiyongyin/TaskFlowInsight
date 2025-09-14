# M2M1-061: 性能基线验证

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-061 |
| 任务名称 | 性能基线验证 |
| 所属模块 | 测试与质量 (Testing & Quality) |
| 优先级 | P1 |
| 预估工期 | M (3-4天) |
| 依赖任务 | M2M1-060 |

## 背景

TaskFlow Insight有明确的性能要求：2字段场景P95≤0.5ms，深度2嵌套P95≤2ms，100项集合P95≤5ms。需要通过JMH基准测试和长时间稳定性测试来验证这些性能指标。

## 目标

1. 实现JMH基准测试套件
2. 验证各场景性能基线
3. 执行2小时稳定性测试
4. 建立性能回归检测
5. 生成性能测试报告

## 非目标

- 不实现性能优化
- 不提供实时监控
- 不支持分布式压测
- 不实现自动调优

## 实现要点

### 1. JMH基准测试框架（标准化环境）

```java
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {
    "-Xms2G", "-Xmx2G",           // 标准堆大小
    "-XX:+UseG1GC",               // G1垃圾回收器
    "-XX:MaxGCPauseMillis=200",   // GC暂停目标
    "-Djava.version=21"           // Java 21
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class TfiBenchmark {
    
    private SnapshotFacade snapshotFacade;
    private DiffDetector diffDetector;
    private PathMatcherCache pathMatcherCache;
    private TestDataGenerator dataGenerator;
    
    // 测试数据
    private Object simpleObject;      // 2字段
    private Object nestedObject;      // 深度2
    private List<String> collection100; // 100项集合
    private Map<String, FieldSnapshot> snapshot1;
    private Map<String, FieldSnapshot> snapshot2;
    
    @Setup(Level.Trial)
    public void setup() {
        // 初始化Spring容器
        ApplicationContext context = new AnnotationConfigApplicationContext(
            TfiAutoConfiguration.class
        );
        
        snapshotFacade = context.getBean(SnapshotFacade.class);
        diffDetector = context.getBean(DiffDetector.class);
        pathMatcherCache = context.getBean(PathMatcherCache.class);
        dataGenerator = new TestDataGenerator();
        
        // 准备测试数据
        prepareTestData();
    }
    
    private void prepareTestData() {
        // 2字段对象（标准测试数据）
        simpleObject = new SimpleObject(
            "test-string-with-50-chars-padding-to-meet-requirement",  // 50字符
            123
        );
        
        // 深度2嵌套（标准嵌套结构）
        nestedObject = dataGenerator.nestedObject(2, 10);  // 深度2，每层10字段
        
        // 100项集合（标准集合大小）
        collection100 = IntStream.range(0, 100)
            .mapToObj(i -> "item-" + i + "-with-standard-string-length")
            .collect(Collectors.toList());
        
        // 差异检测数据
        snapshot1 = snapshotFacade.takeSnapshot(nestedObject, null);
        Object modified = dataGenerator.modifyObject(nestedObject, 0.1);  // 10%字段修改
        snapshot2 = snapshotFacade.takeSnapshot(modified, null);
    }
    
    /**
     * 标准测试环境规格
     */
    public static class TestEnvironment {
        public static final String JVM_VERSION = "OpenJDK 21";
        public static final String HEAP_SIZE = "2GB";
        public static final String GC_TYPE = "G1GC";
        public static final int CPU_CORES = 4;  // 标准测试机4核
        public static final String CPU_FREQ = "2.4GHz";
        public static final String MEMORY = "8GB";
        public static final String OS = "Linux/MacOS/Windows";
    }
}
```

### 2. 核心场景基准测试

```java
public class CoreBenchmarks extends TfiBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Map<String, FieldSnapshot> testSimpleSnapshot() {
        // 基线要求: P95 ≤ 500μs (0.5ms)
        // 目标: P50 ≤ 200μs, P99 ≤ 1000μs
        return snapshotFacade.takeSnapshot(simpleObject, null);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Map<String, FieldSnapshot> testNestedSnapshot() {
        // 基线要求: P95 ≤ 2ms
        // 目标: P50 ≤ 1ms, P99 ≤ 3ms
        return snapshotFacade.takeSnapshot(nestedObject, null);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Summary testCollectionSummary() {
        // 基线要求: P95 ≤ 5ms (size-only模式)
        // 目标: P50 ≤ 2ms, P99 ≤ 8ms
        CollectionConfig config = new CollectionConfig();
        config.setStrategy(CollectionStrategy.ALWAYS_SUMMARY);
        config.setSizeOnlyThreshold(1000);
        
        CollectionSummary summary = new CollectionSummary(config);
        return summary.summarize(collection100, config);
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public boolean testPathMatching() {
        // 额外测试：路径匹配性能
        return pathMatcherCache.match("user.**.field", "user.profile.settings.field");
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public List<DiffResult> testDiffDetection() {
        // 额外测试：差异检测性能
        return diffDetector.detect(snapshot1, snapshot2);
    }
}
```

### 3. 稳定性测试

```java
@Component
public class StabilityTest {
    private final SnapshotFacade snapshotFacade;
    private final CaffeineStore store;
    private final TestDataGenerator dataGenerator;
    private final MetricRegistry metricRegistry;
    
    public StabilityTestResult runStabilityTest(Duration duration) {
        long endTime = System.currentTimeMillis() + duration.toMillis();
        StabilityTestResult result = new StabilityTestResult();
        
        // 性能采样器
        DescriptiveStatistics simpleStats = new DescriptiveStatistics();
        DescriptiveStatistics nestedStats = new DescriptiveStatistics();
        DescriptiveStatistics collectionStats = new DescriptiveStatistics();
        
        // 错误计数
        AtomicInteger errorCount = new AtomicInteger();
        AtomicLong operationCount = new AtomicLong();
        
        // 并发执行器
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        
        // 提交测试任务
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> {
                while (System.currentTimeMillis() < endTime) {
                    try {
                        // 执行测试操作
                        executeTestOperation(simpleStats, nestedStats, collectionStats);
                        operationCount.incrementAndGet();
                        
                        // 随机延迟模拟真实负载
                        Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        log.error("Test operation failed", e);
                    }
                }
            }));
        }
        
        // 等待完成
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                log.error("Task execution failed", e);
            }
        });
        
        // 收集结果
        result.setDuration(duration);
        result.setTotalOperations(operationCount.get());
        result.setErrorCount(errorCount.get());
        result.setSimpleP95(simpleStats.getPercentile(95));
        result.setNestedP95(nestedStats.getPercentile(95));
        result.setCollectionP95(collectionStats.getPercentile(95));
        
        // 验证内存稳定性
        result.setMemoryLeakDetected(checkMemoryLeak());
        
        return result;
    }
    
    private void executeTestOperation(
            DescriptiveStatistics simpleStats,
            DescriptiveStatistics nestedStats,
            DescriptiveStatistics collectionStats) {
        
        // 测试简单对象
        Object simple = dataGenerator.simpleObject();
        long start = System.nanoTime();
        snapshotFacade.takeSnapshot(simple, null);
        simpleStats.addValue((System.nanoTime() - start) / 1_000_000.0);
        
        // 测试嵌套对象
        Object nested = dataGenerator.nestedObject(2);
        start = System.nanoTime();
        snapshotFacade.takeSnapshot(nested, null);
        nestedStats.addValue((System.nanoTime() - start) / 1_000_000.0);
        
        // 测试集合
        List<?> collection = dataGenerator.generateList(100);
        start = System.nanoTime();
        new CollectionSummary().summarize(collection);
        collectionStats.addValue((System.nanoTime() - start) / 1_000_000.0);
    }
    
    /**
     * 使用WeakReference检测内存泄漏（可靠方案）
     */
    private boolean checkMemoryLeak() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        List<WeakReference<Object>> refs = new ArrayList<>();
        
        // 创建对象并注册WeakReference
        for (int i = 0; i < 1000; i++) {
            Object snapshot = snapshotFacade.takeSnapshot(
                dataGenerator.simpleObject(), null
            );
            refs.add(new WeakReference<>(snapshot, queue));
        }
        
        // 清理ThreadLocal
        ThreadLocalManager.clearContext();
        
        // 触发GC
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 检查回收情况
        int collected = 0;
        while (queue.poll() != null) {
            collected++;
        }
        
        // 如果大部分对象未被回收，可能存在泄漏
        boolean leakDetected = collected < 900;  // 90%回收率
        
        if (leakDetected) {
            log.warn("Potential memory leak detected: {}/{} objects collected", 
                    collected, refs.size());
        }
        
        return leakDetected;
    }
        return (memoryAfter - memoryBefore) > 100 * 1024 * 1024;
    }
}
```

### 4. 性能回归检测

```java
@Component
public class PerformanceRegressionDetector {
    private final Map<String, PerformanceBaseline> baselines;
    
    public PerformanceRegressionDetector() {
        // 加载性能基线
        baselines = loadBaselines();
    }
    
    private Map<String, PerformanceBaseline> loadBaselines() {
        Map<String, PerformanceBaseline> map = new HashMap<>();
        map.put("simple", new PerformanceBaseline("simple", 0.5, 0.3));
        map.put("nested", new PerformanceBaseline("nested", 2.0, 1.5));
        map.put("collection", new PerformanceBaseline("collection", 5.0, 3.0));
        return map;
    }
    
    public RegressionReport detectRegression(BenchmarkResult current) {
        RegressionReport report = new RegressionReport();
        
        for (Map.Entry<String, PerformanceBaseline> entry : baselines.entrySet()) {
            String scenario = entry.getKey();
            PerformanceBaseline baseline = entry.getValue();
            double currentP95 = current.getP95(scenario);
            
            if (currentP95 > baseline.getP95Threshold()) {
                report.addRegression(new Regression(
                    scenario,
                    baseline.getP95Threshold(),
                    currentP95,
                    (currentP95 - baseline.getP95Threshold()) / baseline.getP95Threshold() * 100
                ));
            }
        }
        
        return report;
    }
}
```

### 5. 性能报告生成

```java
@Component
public class PerformanceReporter {
    
    public void generateReport(
            BenchmarkResult benchmarkResult,
            StabilityTestResult stabilityResult,
            RegressionReport regressionReport) {
        
        StringBuilder report = new StringBuilder();
        report.append("# TaskFlow Insight Performance Report\n\n");
        report.append("Generated: ").append(Instant.now()).append("\n\n");
        
        // 基准测试结果
        report.append("## Benchmark Results\n\n");
        report.append("| Scenario | P50 (ms) | P95 (ms) | P99 (ms) | Baseline | Status |\n");
        report.append("|----------|----------|----------|----------|----------|--------|\n");
        
        appendBenchmarkRow(report, "Simple (2 fields)", 
            benchmarkResult.getSimpleStats(), 0.5);
        appendBenchmarkRow(report, "Nested (depth 2)", 
            benchmarkResult.getNestedStats(), 2.0);
        appendBenchmarkRow(report, "Collection (100)", 
            benchmarkResult.getCollectionStats(), 5.0);
        
        // 稳定性测试结果
        report.append("\n## Stability Test Results\n\n");
        report.append("- Duration: ").append(stabilityResult.getDuration()).append("\n");
        report.append("- Total Operations: ").append(stabilityResult.getTotalOperations()).append("\n");
        report.append("- Error Rate: ").append(
            String.format("%.2f%%", stabilityResult.getErrorRate() * 100)).append("\n");
        report.append("- Memory Leak: ").append(
            stabilityResult.isMemoryLeakDetected() ? "DETECTED" : "None").append("\n");
        
        // 回归检测
        if (!regressionReport.getRegressions().isEmpty()) {
            report.append("\n## ⚠️ Performance Regressions Detected\n\n");
            for (Regression regression : regressionReport.getRegressions()) {
                report.append("- **").append(regression.getScenario()).append("**: ");
                report.append(String.format("%.2fms -> %.2fms (%.1f%% slower)\n",
                    regression.getBaseline(),
                    regression.getCurrent(),
                    regression.getDegradation()));
            }
        }
        
        // 保存报告
        saveReport(report.toString());
    }
    
    private void appendBenchmarkRow(StringBuilder report, String scenario,
                                   Statistics stats, double baseline) {
        String status = stats.getP95() <= baseline ? "✅ PASS" : "❌ FAIL";
        report.append(String.format("| %s | %.3f | %.3f | %.3f | %.1f | %s |\n",
            scenario,
            stats.getP50(),
            stats.getP95(),
            stats.getP99(),
            baseline,
            status));
    }
}
```

## 测试要求

### 性能基线

1. **必须达标**
   - 2字段: P95 ≤ 0.5ms
   - 深度2: P95 ≤ 2ms
   - 100项: P95 ≤ 5ms

2. **稳定性要求**
   - 2小时运行无崩溃
   - 错误率 < 0.1%
   - 无内存泄漏

3. **回归检测**
   - 性能退化 < 10%
   - 自动告警机制

## 验收标准

### 功能验收

- [ ] JMH测试完整
- [ ] 性能基线达标
- [ ] 稳定性测试通过
- [ ] 回归检测可用
- [ ] 报告生成正常

### 性能验收

- [ ] 所有P95指标达标
- [ ] 2小时稳定运行
- [ ] 内存使用稳定

### 质量验收

- [ ] 测试可重复
- [ ] 报告清晰
- [ ] CI/CD集成

## 风险评估

### 技术风险

1. **R040: 性能不达标**
   - 缓解：优化热点
   - 备选：调整基线

2. **R041: 测试不稳定**
   - 缓解：隔离环境
   - 控制：多次运行

3. **R042: 内存泄漏**
   - 缓解：profiler分析
   - 监控：堆转储

### 依赖风险

- JMH框架版本

## 需要澄清

1. 性能基线是否可调整
2. 稳定性测试时长
3. 回归阈值设定

## 代码示例

### Maven配置

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.35</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.35</version>
    <scope>test</scope>
</dependency>
```

### 运行命令

```bash
# 运行JMH基准测试
mvn clean test -Dtest=TfiBenchmark

# 运行稳定性测试
mvn test -Dtest=StabilityTest -Dduration=2h

# 生成性能报告
mvn test -Dtest=PerformanceTestSuite -Dreport=true
```

### 测试输出示例

```
Benchmark                          Mode  Cnt   Score   Error  Units
CoreBenchmarks.testSimpleSnapshot  p95    20   0.423 ± 0.012  ms
CoreBenchmarks.testNestedSnapshot  p95    20   1.856 ± 0.045  ms
CoreBenchmarks.testCollectionSum   p95    20   4.234 ± 0.089  ms

Stability Test: PASSED
Duration: 2h
Operations: 1,234,567
Error Rate: 0.02%
Memory Stable: YES

Performance Status: ✅ ALL BASELINES MET
```

## 实施计划

### Day 1: JMH集成
- 基准测试框架
- 核心场景测试
- 数据准备

### Day 2: 稳定性测试
- 长时间运行
- 内存监控
- 错误统计

### Day 3: 回归检测
- 基线定义
- 比较逻辑
- 告警机制

### Day 4: 报告集成
- 报告生成
- CI/CD集成
- 文档完善

## 参考资料

1. JMH官方文档
2. 性能测试最佳实践
3. 内存泄漏检测指南

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发