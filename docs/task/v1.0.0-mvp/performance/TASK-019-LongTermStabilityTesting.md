# TASK-019: 长期稳定性测试

## 背景

TaskFlowInsight作为性能监控工具，需要具备长期稳定运行的能力。长期稳定性测试能够发现内存泄漏、性能退化、资源耗尽等在短期测试中不易暴露的问题，确保系统在生产环境中能够持续稳定地运行数小时甚至数天。

## 目标

### 主要目标
1. **内存稳定性验证**：确保长时间运行不出现内存泄漏
2. **性能稳定性测试**：性能指标在长期运行中保持稳定
3. **资源清理验证**：验证所有资源能够正确释放
4. **异常恢复测试**：长期运行中的异常恢复能力

### 次要目标
1. **压力适应性测试**：在持续压力下的适应能力
2. **系统资源监控**：长期运行的系统资源使用监控
3. **容错机制验证**：长期运行中的容错和恢复机制

## 技术实现

### 1. 长期稳定性测试框架
```java
public final class LongTermStabilityTestFramework {
    private static final Logger LOGGER = LoggerFactory.getLogger(LongTermStabilityTestFramework.class);
    
    // 测试配置
    private final Duration testDuration;
    private final Duration samplingInterval;
    private final int baselineConcurrency;
    private final ScheduledExecutorService monitoringExecutor;
    private final ScheduledExecutorService workloadExecutor;
    
    // 监控数据收集
    private final List<SystemSnapshot> snapshots = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isTestRunning = new AtomicBoolean(false);
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    public StabilityTestResult runStabilityTest(StabilityWorkload workload) {
        LOGGER.info("Starting long-term stability test for duration: {}", testDuration);
        
        long startTime = System.currentTimeMillis();
        isTestRunning.set(true);
        
        // 启动系统监控
        startSystemMonitoring();
        
        // 启动工作负载
        startWorkload(workload);
        
        try {
            // 等待测试完成
            Thread.sleep(testDuration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Stability test was interrupted");
        } finally {
            stopTest();
        }
        
        return buildTestResult(startTime);
    }
    
    private void startSystemMonitoring() {
        monitoringExecutor.scheduleAtFixedRate(
            this::captureSystemSnapshot,
            0,
            samplingInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }
    
    private void captureSystemSnapshot() {
        try {
            SystemSnapshot snapshot = SystemSnapshot.builder()
                .timestamp(System.currentTimeMillis())
                .memoryUsage(getMemoryUsage())
                .cpuUsage(getCpuUsage())
                .threadCount(getThreadCount())
                .heapUtilization(getHeapUtilization())
                .gcMetrics(getGcMetrics())
                .tfiMetrics(getTfiMetrics())
                .build();
                
            snapshots.add(snapshot);
            
            // 检查异常情况
            detectAnomalies(snapshot);
            
        } catch (Exception e) {
            LOGGER.error("Error capturing system snapshot", e);
        }
    }
    
    private void startWorkload(StabilityWorkload workload) {
        workloadExecutor.scheduleAtFixedRate(() -> {
            try {
                workload.executeWorkload();
                totalOperations.incrementAndGet();
            } catch (Exception e) {
                totalErrors.incrementAndGet();
                LOGGER.warn("Error in workload execution", e);
            }
        }, 0, workload.getExecutionInterval().toMillis(), TimeUnit.MILLISECONDS);
    }
    
    private void detectAnomalies(SystemSnapshot snapshot) {
        // 内存泄漏检测
        if (detectMemoryLeak(snapshot)) {
            LOGGER.warn("Potential memory leak detected at {}", snapshot.getTimestamp());
        }
        
        // 性能退化检测
        if (detectPerformanceDegradation(snapshot)) {
            LOGGER.warn("Performance degradation detected at {}", snapshot.getTimestamp());
        }
        
        // 资源耗尽检测
        if (detectResourceExhaustion(snapshot)) {
            LOGGER.error("Resource exhaustion detected at {}", snapshot.getTimestamp());
        }
    }
}
```

### 2. 系统快照数据收集
```java
public class SystemSnapshot {
    private final long timestamp;
    private final MemoryUsage memoryUsage;
    private final double cpuUsage;
    private final int threadCount;
    private final HeapUtilization heapUtilization;
    private final GcMetrics gcMetrics;
    private final TfiMetrics tfiMetrics;
    
    public static class MemoryUsage {
        private final long heapUsed;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapMax;
        private final long directMemoryUsed;
        
        public double getHeapUtilizationRate() {
            return heapMax > 0 ? (double) heapUsed / heapMax : 0.0;
        }
        
        public long getAvailableHeap() {
            return heapMax - heapUsed;
        }
    }
    
    public static class GcMetrics {
        private final long youngGenCollections;
        private final long youngGenCollectionTime;
        private final long oldGenCollections;
        private final long oldGenCollectionTime;
        
        public double getGcOverhead() {
            long totalTime = youngGenCollectionTime + oldGenCollectionTime;
            return totalTime > 0 ? (double) totalTime / System.currentTimeMillis() : 0.0;
        }
    }
    
    public static class TfiMetrics {
        private final long activeSessions;
        private final long activeTasks;
        private final long totalMessages;
        private final long averageTaskDepth;
        private final double averageOperationTime;
        
        // TFI特定性能指标
        public boolean isWithinNormalRange() {
            return averageOperationTime < 1000000 && // < 1ms
                   activeTasks < 100000 &&
                   totalMessages < 1000000;
        }
    }
}
```

### 3. 长期稳定性测试用例
```java
@TestMethodOrder(OrderAnnotation.class)
public class LongTermStabilityTest {
    
    private static final Duration SHORT_STABILITY_TEST = Duration.ofMinutes(30);
    private static final Duration MEDIUM_STABILITY_TEST = Duration.ofHours(2);
    private static final Duration LONG_STABILITY_TEST = Duration.ofHours(8);
    
    private LongTermStabilityTestFramework testFramework;
    
    @BeforeEach
    void setUp() {
        testFramework = new LongTermStabilityTestFramework(
            SHORT_STABILITY_TEST,
            Duration.ofSeconds(30), // 采样间隔
            10 // 基准并发数
        );
    }
    
    @Test
    @Order(1)
    @DisplayName("短期稳定性测试 - 30分钟")
    void testShortTermStability() {
        StabilityWorkload workload = new TypicalWorkload();
        StabilityTestResult result = testFramework.runStabilityTest(workload);
        
        // 验证测试结果
        assertTrue(result.isMemoryStable(), "Memory usage should be stable");
        assertTrue(result.isPerformanceStable(), "Performance should be stable");
        assertTrue(result.getErrorRate() < 0.001, "Error rate should be < 0.1%");
        
        System.out.println("Short-term stability test completed successfully");
        System.out.println("Total operations: " + result.getTotalOperations());
        System.out.println("Average memory usage: " + result.getAverageMemoryUsage() + " MB");
        System.out.println("Performance variance: " + result.getPerformanceVariance() + "%");
    }
    
    @Test
    @Order(2)
    @DisplayName("中期稳定性测试 - 2小时")
    @Disabled("Long running test - enable for full stability verification")
    void testMediumTermStability() {
        testFramework = new LongTermStabilityTestFramework(
            MEDIUM_STABILITY_TEST,
            Duration.ofMinutes(1),
            20
        );
        
        StabilityWorkload workload = new IntensiveWorkload();
        StabilityTestResult result = testFramework.runStabilityTest(workload);
        
        assertTrue(result.isMemoryStable(), "Memory should remain stable over 2 hours");
        assertTrue(result.isPerformanceStable(), "Performance should not degrade significantly");
        assertTrue(result.getMaxMemoryIncrease() < 50_000_000, "Memory increase should be < 50MB");
        
        // 验证GC行为
        assertTrue(result.getGcOverhead() < 0.05, "GC overhead should be < 5%");
        
        System.out.println("Medium-term stability test completed successfully");
        printDetailedResults(result);
    }
    
    @Test
    @Order(3)
    @DisplayName("长期稳定性测试 - 8小时")
    @Disabled("Very long running test - enable for production readiness verification")
    void testLongTermStability() {
        testFramework = new LongTermStabilityTestFramework(
            LONG_STABILITY_TEST,
            Duration.ofMinutes(5),
            50
        );
        
        StabilityWorkload workload = new ProductionSimulationWorkload();
        StabilityTestResult result = testFramework.runStabilityTest(workload);
        
        // 长期稳定性验证
        assertTrue(result.isMemoryStable(), "Memory should be stable over 8 hours");
        assertTrue(result.isPerformanceStable(), "Performance should remain consistent");
        assertTrue(result.getMemoryLeakRate() < 1000000, "Memory leak rate should be < 1MB/hour");
        
        // 验证系统恢复能力
        assertTrue(result.getRecoveryEffectiveness() > 0.99, "System should recover from 99% of errors");
        
        System.out.println("Long-term stability test completed successfully");
        printComprehensiveResults(result);
    }
    
    @Test
    @Order(4)
    @DisplayName("内存泄漏专项测试")
    void testMemoryLeakDetection() {
        Duration testDuration = Duration.ofMinutes(60);
        StabilityWorkload leakyWorkload = new MemoryIntensiveWorkload();
        
        testFramework = new LongTermStabilityTestFramework(
            testDuration,
            Duration.ofSeconds(10),
            5
        );
        
        StabilityTestResult result = testFramework.runStabilityTest(leakyWorkload);
        
        // 内存泄漏检测
        List<SystemSnapshot> snapshots = result.getSnapshots();
        long initialMemory = snapshots.get(0).getMemoryUsage().getHeapUsed();
        long finalMemory = snapshots.get(snapshots.size() - 1).getMemoryUsage().getHeapUsed();
        
        long memoryIncrease = finalMemory - initialMemory;
        assertTrue(memoryIncrease < 100_000_000, 
            "Memory increase over 1 hour should be < 100MB, but was: " + memoryIncrease);
        
        // 验证内存增长趋势
        double memoryGrowthRate = result.calculateMemoryGrowthRate();
        assertTrue(memoryGrowthRate < 1000000, // < 1MB per minute
            "Memory growth rate should be < 1MB/min, but was: " + memoryGrowthRate);
    }
    
    @Test
    @Order(5)
    @DisplayName("性能退化检测测试")
    void testPerformanceDegradationDetection() {
        Duration testDuration = Duration.ofMinutes(45);
        StabilityWorkload workload = new PerformanceTestWorkload();
        
        testFramework = new LongTermStabilityTestFramework(
            testDuration,
            Duration.ofSeconds(15),
            10
        );
        
        StabilityTestResult result = testFramework.runStabilityTest(workload);
        
        // 性能退化分析
        List<Double> performanceSamples = result.getPerformanceSamples();
        double initialPerformance = performanceSamples.subList(0, 10).stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        double finalPerformance = performanceSamples.subList(
            performanceSamples.size() - 10, 
            performanceSamples.size()
        ).stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        double performanceDegradation = (initialPerformance - finalPerformance) / initialPerformance;
        assertTrue(performanceDegradation < 0.1, 
            "Performance degradation should be < 10%, but was: " + (performanceDegradation * 100) + "%");
    }
    
    @Test
    @Order(6)
    @DisplayName("资源清理验证测试")
    void testResourceCleanupVerification() {
        // 测试前状态
        System.gc();
        long initialThreadCount = Thread.activeCount();
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 执行稳定性测试
        Duration testDuration = Duration.ofMinutes(20);
        StabilityWorkload workload = new ResourceIntensiveWorkload();
        
        testFramework = new LongTermStabilityTestFramework(
            testDuration,
            Duration.ofSeconds(5),
            15
        );
        
        StabilityTestResult result = testFramework.runStabilityTest(workload);
        
        // 强制清理和垃圾回收
        TFI.cleanup();
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 验证资源清理
        long finalThreadCount = Thread.activeCount();
        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        assertTrue(finalThreadCount <= initialThreadCount + 5, 
            "Thread count should return to baseline (±5)");
        assertTrue(finalMemory - initialMemory < 50_000_000, 
            "Memory usage should return close to baseline (< 50MB difference)");
        
        System.out.println("Resource cleanup verification completed");
        System.out.println("Thread count change: " + (finalThreadCount - initialThreadCount));
        System.out.println("Memory change: " + (finalMemory - initialMemory) / 1024 / 1024 + " MB");
    }
    
    private void printDetailedResults(StabilityTestResult result) {
        System.out.println("=== Medium-term Stability Test Results ===");
        System.out.println("Test Duration: " + result.getTestDuration());
        System.out.println("Total Operations: " + result.getTotalOperations());
        System.out.println("Operations/Second: " + result.getOperationsPerSecond());
        System.out.println("Error Rate: " + (result.getErrorRate() * 100) + "%");
        System.out.println("Average Memory Usage: " + result.getAverageMemoryUsage() + " MB");
        System.out.println("Peak Memory Usage: " + result.getPeakMemoryUsage() + " MB");
        System.out.println("GC Overhead: " + (result.getGcOverhead() * 100) + "%");
        System.out.println("Performance Variance: " + result.getPerformanceVariance() + "%");
    }
    
    private void printComprehensiveResults(StabilityTestResult result) {
        System.out.println("=== Long-term Stability Test Results ===");
        printDetailedResults(result);
        System.out.println("Memory Leak Rate: " + result.getMemoryLeakRate() + " bytes/hour");
        System.out.println("System Recovery Rate: " + (result.getRecoveryEffectiveness() * 100) + "%");
        System.out.println("Resource Utilization Efficiency: " + result.getResourceEfficiency());
        System.out.println("Stability Score: " + result.getOverallStabilityScore());
        
        if (result.getDetectedAnomalies().size() > 0) {
            System.out.println("Detected Anomalies:");
            result.getDetectedAnomalies().forEach(anomaly -> 
                System.out.println("  - " + anomaly.getDescription() + " at " + anomaly.getTimestamp()));
        }
    }
}
```

### 4. 工作负载定义
```java
public abstract class StabilityWorkload {
    protected final Duration executionInterval;
    
    public StabilityWorkload(Duration executionInterval) {
        this.executionInterval = executionInterval;
    }
    
    public abstract void executeWorkload() throws Exception;
    
    public Duration getExecutionInterval() {
        return executionInterval;
    }
}

public class TypicalWorkload extends StabilityWorkload {
    public TypicalWorkload() {
        super(Duration.ofMillis(100));
    }
    
    @Override
    public void executeWorkload() throws Exception {
        // 模拟典型的TFI使用场景
        try (TaskContext task = TFI.start("stability-test-task")) {
            task.message("Processing item %d", System.currentTimeMillis());
            
            // 模拟一些处理逻辑
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
            
            // 嵌套子任务
            try (TaskContext subTask = task.startSubTask("sub-processing")) {
                subTask.message("Sub-task processing");
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
            }
        }
    }
}

public class IntensiveWorkload extends StabilityWorkload {
    public IntensiveWorkload() {
        super(Duration.ofMillis(50));
    }
    
    @Override
    public void executeWorkload() throws Exception {
        // 高强度工作负载
        for (int i = 0; i < 10; i++) {
            try (TaskContext task = TFI.start("intensive-task-" + i)) {
                for (int j = 0; j < 5; j++) {
                    task.message("Intensive processing %d-%d", i, j);
                    
                    try (TaskContext subTask = task.startSubTask("intensive-sub-" + j)) {
                        subTask.message("Sub processing");
                        // 模拟计算密集操作
                        double result = Math.random() * Math.PI;
                    }
                }
            }
        }
    }
}

public class MemoryIntensiveWorkload extends StabilityWorkload {
    public MemoryIntensiveWorkload() {
        super(Duration.ofMillis(200));
    }
    
    @Override
    public void executeWorkload() throws Exception {
        // 内存密集型工作负载，用于测试内存泄漏
        List<String> tempData = new ArrayList<>();
        
        try (TaskContext task = TFI.start("memory-intensive-task")) {
            // 创建临时数据
            for (int i = 0; i < 1000; i++) {
                tempData.add("Temporary data item " + i + " " + UUID.randomUUID());
            }
            
            task.message("Created %d temporary items", tempData.size());
            
            // 处理数据
            tempData.stream()
                .filter(s -> s.length() > 20)
                .map(String::toUpperCase)
                .forEach(s -> task.message("Processed: %s", s.substring(0, Math.min(20, s.length()))));
        }
        
        // 数据应该在这里被回收
        tempData.clear();
    }
}
```

## 关键验证点

### 内存稳定性验证
1. **内存泄漏检测**：长期运行中内存使用量不应持续增长
2. **垃圾回收效率**：GC开销应保持在合理范围内
3. **堆外内存管理**：DirectMemory等资源正确释放

### 性能稳定性验证
1. **响应时间稳定性**：API调用响应时间不应显著退化
2. **吞吐量稳定性**：系统吞吐量应保持在预期范围内
3. **资源使用效率**：CPU和内存使用效率应保持稳定

### 异常处理稳定性
1. **异常恢复能力**：系统应能从各种异常中正确恢复
2. **错误率稳定性**：错误率应保持在可接受的低水平
3. **优雅降级**：在异常情况下系统应能优雅降级

## 验收标准

### 短期稳定性（30分钟）
- [ ] 内存使用增长 < 20MB
- [ ] 性能退化 < 5%
- [ ] 错误率 < 0.1%
- [ ] GC开销 < 3%

### 中期稳定性（2小时）
- [ ] 内存使用增长 < 50MB
- [ ] 性能退化 < 10%
- [ ] 错误率 < 0.5%
- [ ] GC开销 < 5%

### 长期稳定性（8小时）
- [ ] 内存泄漏率 < 1MB/小时
- [ ] 性能退化 < 15%
- [ ] 系统恢复率 > 99%
- [ ] 整体稳定性评分 > 0.95

## 依赖关系

### 前置依赖
- TASK-016: API性能基准测试
- TASK-017: 内存泄漏检测机制
- TASK-018: 并发压力测试

### 后置依赖
- TASK-020~023: 性能优化任务

## 实现文件

1. **长期稳定性测试框架** (`LongTermStabilityTestFramework.java`)
2. **系统快照收集** (`SystemSnapshot.java`)
3. **稳定性测试用例** (`LongTermStabilityTest.java`)
4. **工作负载定义** (`StabilityWorkload.java`)
5. **测试结果分析** (`StabilityTestResult.java`)
6. **异常检测器** (`StabilityAnomalyDetector.java`)
7. **稳定性报告生成** (`StabilityReportGenerator.java`)