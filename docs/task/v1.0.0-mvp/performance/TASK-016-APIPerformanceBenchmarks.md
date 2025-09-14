# TASK-016: API性能基准测试

## 背景

TaskFlowInsight作为性能分析工具，自身性能至关重要。需要建立完整的性能基准测试体系，验证TFI API调用的性能开销符合<5%的目标，并确保在高并发场景下的稳定性。

## 目标

### 主要目标
1. **CPU开销验证**：确保TFI API调用总开销<5%业务逻辑执行时间
2. **内存使用基准**：建立内存使用基线，监控内存泄漏
3. **并发性能测试**：验证1000+并发线程场景下的性能表现
4. **延迟分析**：测量API调用的P50/P95/P99延迟分布

### 次要目标
1. **回归测试基准**：建立性能基准线，检测性能衰退
2. **负载特征分析**：分析不同使用模式下的性能表现
3. **资源消耗监控**：CPU、内存、GC压力的全面监控

## 实现方案

### 1. 性能测试框架
```java
// JMH (Java Microbenchmark Harness) 基准测试
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class TFIPerformanceBenchmarks {
    
    @Benchmark
    public void benchmarkStartStop() {
        TaskContext ctx = TFI.start("test-task");
        TFI.stop();
    }
    
    @Benchmark
    public void benchmarkNestedTasks() {
        TaskContext ctx1 = TFI.start("outer-task");
        TaskContext ctx2 = TFI.start("inner-task");
        TFI.stop(); // inner
        TFI.stop(); // outer
    }
    
    @Benchmark
    public void benchmarkWithMessages() {
        TaskContext ctx = TFI.start("message-task");
        TFI.message("test message");
        TFI.stop();
    }
}
```

### 2. 并发性能测试
```java
@Test
public void testConcurrentPerformance() {
    int threadCount = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicLong totalTime = new AtomicLong();
    
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            long start = System.nanoTime();
            TaskContext ctx = TFI.start("concurrent-task-" + Thread.currentThread().getId());
            TFI.message("message from thread " + Thread.currentThread().getId());
            TFI.stop();
            totalTime.addAndGet(System.nanoTime() - start);
            latch.countDown();
        });
    }
    
    // 验证平均响应时间和吞吐量
}
```

### 3. 内存使用监控
```java
public class MemoryProfiler {
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    public MemorySnapshot captureSnapshot() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        long totalGcTime = gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
            
        return new MemorySnapshot(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            totalGcTime
        );
    }
}
```

## 测试标准

### 性能基准
1. **API调用延迟**（更现实的目标）：
   - P50 < 1μs (1000ns)
   - P95 < 5μs (5000ns)
   - P99 < 10μs (10000ns)
   - P999 < 50μs (50000ns)

2. **CPU开销**：
   - 单线程场景：<3%
   - 并发场景：<5%
   - 极限负载：<10%

3. **内存使用**：
   - 基础内存开销：<1MB
   - 1000线程场景：<10MB
   - 无内存泄漏（24小时运行测试）

## 验收标准

### 功能验收
- [ ] JMH基准测试套件完整覆盖所有API
- [ ] 并发性能测试支持1000+线程
- [ ] 内存泄漏检测机制完善
- [ ] 性能回归检测自动化

### 性能验收
- [ ] 所有性能指标满足基准要求
- [ ] CPU开销<5%目标达成
- [ ] 内存使用<10MB目标达成
- [ ] 99.9%的API调用延迟<50μs

## 依赖关系

### 前置依赖
- TASK-010: TFI主API实现
- TASK-007: ContextManager实现
- TASK-008: ThreadLocal内存管理

### 后置依赖
- TASK-017: 内存泄漏检测
- TASK-018: 并发压力测试

## 实施计划

### 第7周（5天）
- **Day 1-2**: JMH基准测试框架搭建
- **Day 3**: 单API性能基准测试实现
- **Day 4**: 并发性能测试框架
- **Day 5**: 内存监控和分析工具

## 交付物

1. **JMH基准测试套件** (`src/jmh/java/`)
2. **并发性能测试类** (`PerformanceConcurrentTest.java`)
3. **内存分析工具类** (`MemoryProfiler.java`)
4. **性能报告生成器** (`PerformanceReporter.java`)
5. **CI性能回归检测** (`.github/workflows/performance.yml`)
6. **性能基准文档** (`docs/performance/benchmarks.md`)