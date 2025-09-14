# TASK-018: 并发压力测试

## 背景

TaskFlowInsight设计目标支持1000+并发线程，在高并发场景下必须保证功能正确性和性能稳定性。需要设计全面的并发压力测试，验证系统在极限负载下的表现，发现潜在的并发问题和性能瓶颈。

## 目标

### 主要目标
1. **高并发验证**：验证1000+并发线程下的功能正确性
2. **压力极限测试**：找到系统性能的临界点和瓶颈
3. **竞态条件检测**：发现并发操作中的数据竞争问题
4. **资源争用分析**：分析高并发下的资源争用情况

### 次要目标
1. **故障注入测试**：模拟异常情况下的系统行为
2. **负载模式测试**：验证不同负载模式下的性能表现
3. **弹性恢复测试**：验证高负载后的系统恢复能力

## 实现方案

### 1. 并发测试框架
```java
public final class ConcurrentStressTestFramework {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentStressTestFramework.class);
    
    // 测试配置
    private final int maxConcurrentThreads;
    private final Duration testDuration;
    private final ExecutorService executorService;
    private final CountDownLatch completionLatch;
    private final ConcurrentHashMap<String, TestMetrics> metricsMap;
    
    public ConcurrentStressTestFramework(int maxThreads, Duration duration) {
        this.maxConcurrentThreads = maxThreads;
        this.testDuration = duration;
        this.executorService = Executors.newFixedThreadPool(maxThreads);
        this.completionLatch = new CountDownLatch(maxThreads);
        this.metricsMap = new ConcurrentHashMap<>();
    }
    
    public TestResult runStressTest(StressTestScenario scenario) {
        long startTime = System.currentTimeMillis();
        
        // 启动所有测试线程
        for (int i = 0; i < maxConcurrentThreads; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    runThreadScenario(scenario, threadIndex);
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // 等待测试完成
        boolean completed = awaitCompletion();
        long endTime = System.currentTimeMillis();
        
        return buildTestResult(startTime, endTime, completed);
    }
    
    private void runThreadScenario(StressTestScenario scenario, int threadIndex) {
        String threadName = "stress-test-" + threadIndex;
        TestMetrics metrics = new TestMetrics(threadName);
        metricsMap.put(threadName, metrics);
        
        long endTime = System.currentTimeMillis() + testDuration.toMillis();
        
        while (System.currentTimeMillis() < endTime) {
            try {
                long operationStart = System.nanoTime();
                scenario.executeOperation(threadIndex);
                long operationEnd = System.nanoTime();
                
                metrics.recordOperation(operationEnd - operationStart);
                
                // 可选的线程间协调
                if (scenario.requiresCoordination()) {
                    scenario.coordinateThreads(threadIndex);
                }
                
            } catch (Exception e) {
                metrics.recordError(e);
                LOGGER.warn("Error in thread {}: {}", threadIndex, e.getMessage());
            }
        }
    }
}
```

### 2. 压力测试场景定义
```java
@FunctionalInterface
public interface StressTestScenario {
    void executeOperation(int threadIndex) throws Exception;
    
    default boolean requiresCoordination() {
        return false;
    }
    
    default void coordinateThreads(int threadIndex) throws Exception {
        // 默认无协调
    }
}

public final class TFIStressTestScenarios {
    
    /**
     * 基础API调用压力测试
     */
    public static final StressTestScenario BASIC_API_STRESS = threadIndex -> {
        TaskContext ctx = TFI.start("stress-task-" + threadIndex);
        try {
            // 模拟业务逻辑
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
            TFI.message("Message from thread " + threadIndex);
        } finally {
            TFI.stop();
        }
    };
    
    /**
     * 嵌套任务压力测试
     */
    public static final StressTestScenario NESTED_TASKS_STRESS = threadIndex -> {
        TaskContext outer = TFI.start("outer-" + threadIndex);
        try {
            TaskContext inner1 = TFI.start("inner1-" + threadIndex);
            try {
                TaskContext inner2 = TFI.start("inner2-" + threadIndex);
                try {
                    TFI.message("Deep nested message");
                } finally {
                    TFI.stop(); // inner2
                }
            } finally {
                TFI.stop(); // inner1
            }
        } finally {
            TFI.stop(); // outer
        }
    };
    
    /**
     * 随机操作混合压力测试
     */
    public static final StressTestScenario MIXED_OPERATIONS_STRESS = threadIndex -> {
        Random random = ThreadLocalRandom.current();
        int operation = random.nextInt(4);
        
        switch (operation) {
            case 0: // 简单任务
                TaskContext ctx = TFI.start("simple-" + threadIndex);
                TFI.stop();
                break;
                
            case 1: // 带消息任务
                TaskContext ctx2 = TFI.start("message-task-" + threadIndex);
                TFI.message("Random message " + random.nextInt());
                TFI.stop();
                break;
                
            case 2: // 导出操作
                TFI.exportJson();
                break;
                
            case 3: // 打印树操作
                TFI.printTree();
                break;
        }
    };
}
```

### 3. 并发正确性验证
```java
public final class ConcurrencyCorrectnessValidator {
    
    /**
     * 验证线程隔离性
     */
    @Test
    public void testThreadIsolation() throws InterruptedException {
        int threadCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentHashMap<Long, String> threadSessions = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    // 每个线程执行独立的会话
                    TaskContext ctx = TFI.start("isolation-test-" + threadIndex);
                    
                    // 记录当前线程的会话信息
                    String sessionInfo = TFI.exportJson();
                    threadSessions.put(Thread.currentThread().threadId(), sessionInfo);
                    
                    // 验证会话信息包含正确的线程标识
                    if (sessionInfo.contains("isolation-test-" + threadIndex)) {
                        successCount.incrementAndGet();
                    }
                    
                    TFI.stop();
                    
                } catch (Exception e) {
                    LOGGER.error("Thread isolation test failed for thread {}", threadIndex, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        
        // 验证所有线程都成功完成
        assertEquals(threadCount, successCount.get());
        
        // 验证没有会话信息被混淆
        Set<String> uniqueSessions = new HashSet<>(threadSessions.values());
        assertEquals(threadCount, uniqueSessions.size());
    }
    
    /**
     * 验证资源竞争安全
     */
    @Test
    public void testResourceContentionSafety() throws InterruptedException {
        int threadCount = 500;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong();
        AtomicLong successfulOperations = new AtomicLong();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int op = 0; op < operationsPerThread; op++) {
                        totalOperations.incrementAndGet();
                        
                        TaskContext ctx = TFI.start("contention-test");
                        TFI.message("Operation " + op);
                        TFI.stop();
                        
                        successfulOperations.incrementAndGet();
                    }
                } catch (Exception e) {
                    LOGGER.error("Resource contention test failed", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(60, TimeUnit.SECONDS);
        
        // 验证所有操作都成功完成
        long expected = (long) threadCount * operationsPerThread;
        assertEquals(expected, totalOperations.get());
        assertEquals(expected, successfulOperations.get());
    }
}
```

## 测试标准

### 并发性能要求
1. **基础并发**：100线程下错误率<0.1%
2. **高并发**：1000线程下错误率<1%
3. **极限并发**：2000线程下系统不崩溃
4. **吞吐量保持**：高并发下吞吐量降幅<50%

### 正确性验证
1. **线程隔离**：100%线程间数据隔离
2. **数据一致性**：所有并发操作数据保持一致
3. **资源安全**：无死锁、无资源泄漏
4. **异常安全**：异常情况下系统状态正确

### 稳定性要求
1. **长期压力**：1小时高并发测试无崩溃
2. **内存稳定**：压力测试后内存使用恢复正常
3. **性能恢复**：高负载后性能快速恢复
4. **错误恢复**：异常后系统能继续正常工作

## 验收标准

### 功能验收
- [ ] 支持1000+并发线程测试
- [ ] 多种压力测试场景覆盖
- [ ] 并发正确性验证完整
- [ ] 故障注入测试机制完善

### 性能验收
- [ ] 1000并发错误率<1%
- [ ] 线程隔离100%正确
- [ ] 高并发吞吐量降幅<50%
- [ ] 1小时压力测试通过

## 依赖关系

### 前置依赖
- TASK-010: TFI主API实现
- TASK-016: API性能基准测试
- TASK-017: 内存泄漏检测

### 后置依赖
- TASK-019: 长期稳定性测试

## 实施计划

### 第7周（3天）
- **Day 1**: 并发测试框架和基础场景
- **Day 2**: 正确性验证和故障注入测试
- **Day 3**: 性能监控和压力测试报告

## 交付物

1. **并发测试框架** (`ConcurrentStressTestFramework.java`)
2. **压力测试场景** (`TFIStressTestScenarios.java`)
3. **正确性验证器** (`ConcurrencyCorrectnessValidator.java`)
4. **故障注入测试** (`FaultInjectionStressTest.java`)
5. **测试度量工具** (`TestMetrics.java`)
6. **压力测试报告生成器** (`StressTestReporter.java`)