# DEV-013: API接口测试实现 - AI开发提示词

## 第一阶段：测试策略制定提示词

```markdown
你是一名资深测试架构师，精通TDD和BDD测试方法论，拥有10年测试框架设计经验。制定API接口的完整测试策略。

**输入材料：**
- 任务卡：docs/task/v1.0.0-mvp/api-implementation/TASK-013-APIInterfaceTests.md
- 设计文档：docs/develop/v1.0.0-mvp/design/api-implementation/DEV-013-API接口测试实现.md
- API实现：TFI、TaskContext、ExceptionSafeExecutor

**测试策略制定：**
1. 测试范围定义
   - 功能测试边界
   - 性能测试指标
   - 并发测试场景
   - 异常测试覆盖

2. 测试优先级
   - P0：核心API功能
   - P1：异常安全机制
   - P2：性能指标验证
   - P3：边界条件测试

3. 测试数据策略
   - 正常数据集
   - 边界数据集
   - 异常数据集
   - 性能数据集

4. 测试环境要求
   - JUnit5配置
   - Mock框架选择
   - 性能测试工具
   - 覆盖率工具

**输出格式：**
生成测试计划：docs/task/v1.0.0-mvp/api-implementation/DEV-013-TestPlan.md

包含内容：
- 测试矩阵（功能×场景）
- 测试用例清单
- 预期结果定义
- 验收标准
```

## 第二阶段：功能测试实现提示词

```markdown
你是一名JUnit5测试专家，精通Spring Boot测试和AssertJ断言库。实现TFI API的完整功能测试。

**角色定位：** 测试开发专家 + 质量保证专家 + 自动化专家

**测试实现目标：**
1. 100%方法覆盖
2. >95%分支覆盖
3. 所有边界条件
4. 完整异常路径

**核心测试类实现：**

### 1. TFIMainAPITest（主API测试）
```java
package com.syy.taskflowinsight.api;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TFI主API完整测试套件")
public class TFIMainAPITest {
    
    @BeforeEach
    void setUp() {
        TFI.cleanup();
        TFI.enable();
    }
    
    @Nested
    @DisplayName("系统控制测试")
    class SystemControlTests {
        
        @Test
        @DisplayName("启用和禁用系统")
        void testEnableDisable() {
            // 初始状态
            TFI.disable();
            assertThat(TFI.isEnabled()).isFalse();
            
            // 启用系统
            TFI.enable();
            assertThat(TFI.isEnabled()).isTrue();
            
            // 禁用后的快速返回
            TFI.disable();
            long start = System.nanoTime();
            TFI.startTask("should-return-fast");
            long duration = System.nanoTime() - start;
            assertThat(duration).isLessThan(1000); // <1μs
        }
        
        @Test
        @DisplayName("系统状态查询")
        void testSystemStatus() {
            SystemStatus status = TFI.getStatus();
            assertThat(status).isNotNull();
            assertThat(status.isEnabled()).isTrue();
            assertThat(status.getActiveThreads()).isGreaterThanOrEqualTo(0);
            assertThat(status.getTotalTasks()).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("任务管理测试")
    class TaskManagementTests {
        
        @Test
        @DisplayName("基本任务生命周期")
        void testTaskLifecycle() {
            // 开始任务
            TaskContext task = TFI.start("test-task");
            assertThat(task).isNotNull();
            assertThat(task.getTaskName()).isEqualTo("test-task");
            assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
            
            // 添加消息
            task.message("Processing step 1")
                .message("Processing step 2")
                .debug("Debug info");
            
            assertThat(task.getMessages()).hasSize(3);
            
            // 结束任务
            task.end();
            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.getDuration()).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("嵌套任务处理")
        void testNestedTasks() {
            try (TaskContext parent = TFI.start("parent")) {
                assertThat(parent.getDepth()).isEqualTo(1);
                
                try (TaskContext child1 = TFI.start("child1")) {
                    assertThat(child1.getDepth()).isEqualTo(2);
                    assertThat(child1.getParent()).isEqualTo(parent);
                    
                    try (TaskContext grandchild = TFI.start("grandchild")) {
                        assertThat(grandchild.getDepth()).isEqualTo(3);
                        assertThat(grandchild.getParent()).isEqualTo(child1);
                    }
                }
                
                try (TaskContext child2 = TFI.start("child2")) {
                    assertThat(child2.getDepth()).isEqualTo(2);
                    assertThat(parent.getChildren()).hasSize(2);
                }
            }
        }
        
        @Test
        @DisplayName("任务with-resources自动关闭")
        void testAutoCloseable() {
            TaskContext task = null;
            try (TaskContext t = TFI.start("auto-close-test")) {
                task = t;
                assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
            }
            // 自动关闭后
            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }
    }
    
    @Nested
    @DisplayName("消息记录测试")
    class MessageRecordingTests {
        
        @Test
        @DisplayName("不同类型消息记录")
        void testMessageTypes() {
            TaskContext task = TFI.start("message-test");
            
            task.message("Info message")
                .debug("Debug message")
                .warn("Warning message")
                .error("Error message", new Exception("Test"));
            
            List<Message> messages = task.getMessages();
            assertThat(messages).hasSize(4);
            
            assertThat(messages)
                .extracting(Message::getType)
                .containsExactly(
                    MessageType.INFO,
                    MessageType.DEBUG,
                    MessageType.WARN,
                    MessageType.ERROR
                );
        }
        
        @Test
        @DisplayName("格式化消息测试")
        void testFormattedMessages() {
            TaskContext task = TFI.start("format-test");
            
            task.message("Processing {} items", 100)
                .message("User: {}, Action: {}", "admin", "login");
            
            List<Message> messages = task.getMessages();
            assertThat(messages.get(0).getContent())
                .isEqualTo("Processing 100 items");
            assertThat(messages.get(1).getContent())
                .isEqualTo("User: admin, Action: login");
        }
    }
}
```

### 2. TaskContextImplTest（上下文测试）
```java
@DisplayName("TaskContext实现测试")
public class TaskContextImplTest {
    
    @Test
    @DisplayName("链式调用测试")
    void testMethodChaining() {
        TaskContext context = new TaskContextImpl(...);
        
        TaskContext result = context
            .message("Step 1")
            .message("Step 2")
            .debug("Debug")
            .warn("Warning");
            
        assertThat(result).isSameAs(context);
        assertThat(context.getMessages()).hasSize(4);
    }
    
    @Test
    @DisplayName("父子关系管理")
    void testParentChildRelationship() {
        TaskContextImpl parent = new TaskContextImpl(...);
        TaskContextImpl child = new TaskContextImpl(..., parent);
        
        assertThat(child.getParent()).isEqualTo(parent);
        assertThat(parent.getChildren()).contains(child);
        
        child.close();
        assertThat(parent.getChildren()).doesNotContain(child);
    }
}
```

**测试数据构建器：**
```java
public class TestDataBuilder {
    
    public static TaskNode createTaskNode(String name) {
        return TaskNode.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .status(TaskStatus.RUNNING)
            .startTime(System.currentTimeMillis())
            .build();
    }
    
    public static Message createMessage(String content, MessageType type) {
        return Message.builder()
            .content(content)
            .type(type)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}
```

**输出要求：**
1. TFIMainAPITest.java（完整）
2. TaskContextImplTest.java
3. TestDataBuilder.java
4. 测试工具类
```

## 第三阶段：异常和边界测试提示词

```markdown
你是一名异常测试专家，精通边界条件测试和故障注入。实现API的异常安全和边界测试。

**测试场景设计：**

### 1. ExceptionSafetyTest（异常安全测试）
```java
@DisplayName("异常安全机制测试")
public class ExceptionSafetyTest {
    
    @Test
    @DisplayName("空参数处理")
    void testNullParameters() {
        assertDoesNotThrow(() -> {
            TFI.start(null);
            TFI.message(null);
            TFI.debug(null);
            TFI.warn(null);
            TFI.error(null, null);
        });
    }
    
    @Test
    @DisplayName("内部异常隔离")
    void testExceptionIsolation() {
        // 注入异常
        ContextManager mockManager = mock(ContextManager.class);
        when(mockManager.startTask(any()))
            .thenThrow(new RuntimeException("Internal error"));
        
        // 替换为mock
        setField(TFI.class, "contextManager", mockManager);
        
        // 验证异常不传播
        assertDoesNotThrow(() -> {
            TaskContext task = TFI.start("test");
            assertThat(task).isInstanceOf(NullTaskContext.class);
        });
    }
    
    @Test
    @DisplayName("资源耗尽处理")
    void testResourceExhaustion() {
        // 模拟OOM
        simulateOutOfMemory();
        
        // 系统应该降级但不崩溃
        TaskContext task = TFI.start("degraded");
        assertThat(task).isInstanceOf(DegradedTaskContext.class);
    }
}
```

### 2. BoundaryConditionTest（边界条件测试）
```java
@DisplayName("边界条件测试")
public class BoundaryConditionTest {
    
    @Test
    @DisplayName("超长字符串处理")
    void testLongStrings() {
        String longName = "x".repeat(10000);
        TaskContext task = TFI.start(longName);
        
        // 应该截断或处理
        assertThat(task.getTaskName().length())
            .isLessThanOrEqualTo(1000);
    }
    
    @Test
    @DisplayName("深度嵌套限制")
    void testDeepNesting() {
        List<TaskContext> tasks = new ArrayList<>();
        
        // 创建100层嵌套
        for (int i = 0; i < 100; i++) {
            TaskContext task = TFI.start("level-" + i);
            tasks.add(task);
        }
        
        // 验证深度限制
        TaskContext lastTask = tasks.get(99);
        assertThat(lastTask.getDepth())
            .isLessThanOrEqualTo(MAX_DEPTH);
    }
    
    @Test
    @DisplayName("大量消息处理")
    void testManyMessages() {
        TaskContext task = TFI.start("bulk-messages");
        
        // 添加10000条消息
        for (int i = 0; i < 10000; i++) {
            task.message("Message " + i);
        }
        
        // 验证内存控制
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        assertThat(usedMemory).isLessThan(100 * 1024 * 1024); // <100MB
    }
}
```

**故障注入工具：**
```java
public class FaultInjector {
    
    public static void injectException(Class<? extends Throwable> type) {
        // 使用字节码操作注入异常
    }
    
    public static void simulateSlowOperation(long delayMs) {
        // 模拟慢操作
    }
    
    public static void simulateMemoryPressure() {
        // 模拟内存压力
    }
}
```

**输出要求：**
1. ExceptionSafetyTest.java
2. BoundaryConditionTest.java
3. FaultInjector.java
4. 异常场景矩阵文档
```

## 第四阶段：性能和并发测试提示词

```markdown
你是一名性能测试专家，精通JMH基准测试和并发测试。实现API的性能验证测试。

**性能测试实现：**

### 1. PerformanceBenchmarkTest（性能基准测试）
```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class PerformanceBenchmarkTest {
    
    @Setup
    public void setup() {
        TFI.enable();
    }
    
    @Benchmark
    @DisplayName("任务创建性能")
    public TaskContext benchmarkTaskCreation() {
        return TFI.start("benchmark-task");
    }
    
    @Benchmark
    @DisplayName("消息记录性能")
    public void benchmarkMessageRecording(Blackhole blackhole) {
        TaskContext task = TFI.start("message-bench");
        task.message("Test message");
        blackhole.consume(task);
    }
    
    @Benchmark
    @DisplayName("禁用状态性能")
    public void benchmarkDisabledState() {
        TFI.disable();
        TFI.start("disabled-task");
        TFI.message("disabled-message");
    }
    
    @Test
    @DisplayName("性能指标验证")
    public void verifyPerformanceTargets() throws Exception {
        Options opt = new OptionsBuilder()
            .include(PerformanceBenchmarkTest.class.getSimpleName())
            .build();
            
        Collection<RunResult> results = new Runner(opt).run();
        
        for (RunResult result : results) {
            double score = result.getPrimaryResult().getScore();
            String benchmark = result.getParams().getBenchmark();
            
            if (benchmark.contains("TaskCreation")) {
                assertThat(score).isLessThan(1000); // <1μs
            } else if (benchmark.contains("MessageRecording")) {
                assertThat(score).isLessThan(100); // <100ns
            } else if (benchmark.contains("DisabledState")) {
                assertThat(score).isLessThan(10); // <10ns
            }
        }
    }
}
```

### 2. ConcurrencyTest（并发测试）
```java
@DisplayName("并发安全测试")
public class ConcurrencyTest {
    
    @Test
    @DisplayName("多线程任务创建")
    void testConcurrentTaskCreation() throws Exception {
        int threadCount = 100;
        int tasksPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<Future<List<TaskContext>>> futures = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<TaskContext> tasks = new ArrayList<>();
                
                for (int j = 0; j < tasksPerThread; j++) {
                    TaskContext task = TFI.start("thread-" + threadId + "-task-" + j);
                    task.message("Processing");
                    tasks.add(task);
                }
                
                endLatch.countDown();
                return tasks;
            }));
        }
        
        // 同时启动所有线程
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        
        // 验证结果
        Set<String> uniqueTaskIds = new HashSet<>();
        for (Future<List<TaskContext>> future : futures) {
            List<TaskContext> tasks = future.get();
            assertThat(tasks).hasSize(tasksPerThread);
            
            for (TaskContext task : tasks) {
                assertThat(uniqueTaskIds.add(task.getTaskId())).isTrue();
            }
        }
        
        // 验证总数
        assertThat(uniqueTaskIds).hasSize(threadCount * tasksPerThread);
    }
    
    @Test
    @DisplayName("线程上下文隔离")
    void testThreadContextIsolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                TaskContext task = TFI.start("thread-" + threadId);
                Thread.sleep(100); // 模拟处理
                return task.getTaskName();
            }));
        }
        
        // 验证每个线程的上下文独立
        Set<String> taskNames = new HashSet<>();
        for (Future<String> future : futures) {
            String name = future.get();
            assertThat(taskNames.add(name)).isTrue();
            assertThat(name).startsWith("thread-");
        }
    }
    
    @Test
    @DisplayName("竞态条件测试")
    void testRaceConditions() {
        // 使用jcstress或自定义竞态检测
        RaceConditionDetector detector = new RaceConditionDetector();
        
        detector.test(() -> {
            TFI.enable();
            TFI.disable();
        }, () -> {
            TFI.start("race-test");
        });
        
        assertThat(detector.hasRaceCondition()).isFalse();
    }
}
```

### 3. StressTest（压力测试）
```java
@DisplayName("压力测试")
public class StressTest {
    
    @Test
    @Timeout(60)
    @DisplayName("持续高负载测试")
    void testSustainedLoad() {
        long startTime = System.currentTimeMillis();
        long duration = 30000; // 30秒
        AtomicLong operations = new AtomicLong(0);
        
        while (System.currentTimeMillis() - startTime < duration) {
            TaskContext task = TFI.start("stress-test");
            task.message("Processing")
                .debug("Debug info")
                .warn("Warning");
            task.close();
            operations.incrementAndGet();
        }
        
        // 验证吞吐量
        double throughput = operations.get() * 1000.0 / duration;
        assertThat(throughput).isGreaterThan(10000); // >10k ops/sec
        
        // 验证内存稳定
        System.gc();
        long usedMemory = Runtime.getRuntime().totalMemory() 
            - Runtime.getRuntime().freeMemory();
        assertThat(usedMemory).isLessThan(200 * 1024 * 1024); // <200MB
    }
}
```

**输出要求：**
1. PerformanceBenchmarkTest.java
2. ConcurrencyTest.java
3. StressTest.java
4. 性能测试报告模板
```

## 综合执行提示词（完整测试套件）

```markdown
你是TaskFlowInsight的测试负责人，需要实现完整的API测试套件。

**完整测试实现计划：**

## Phase 1: 测试框架搭建（15分钟）
建立测试基础设施：
1. 测试配置类
2. 测试数据构建器
3. 测试工具类
4. Mock配置

## Phase 2: 功能测试实现（30分钟）
实现核心功能测试：
1. TFIMainAPITest - 25个测试用例
2. TaskContextImplTest - 15个测试用例
3. MessageRecordingTest - 10个测试用例

关键测试用例：
```java
// 测试套件结构
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TFITestSuite {
    
    @Nested
    class BasicFunctionality { }
    
    @Nested
    class ExceptionHandling { }
    
    @Nested
    class Performance { }
    
    @Nested
    class Concurrency { }
    
    @Nested
    class BoundaryConditions { }
}
```

## Phase 3: 异常测试实现（20分钟）
实现异常和边界测试：
1. 异常隔离验证
2. 降级机制测试
3. 资源清理验证
4. 边界条件处理

## Phase 4: 性能测试实现（20分钟）
实现性能验证：
1. JMH基准测试
2. 并发安全测试
3. 压力测试
4. 内存泄漏检测

## Phase 5: 测试报告生成（15分钟）
生成测试报告：
1. 覆盖率报告（>95%）
2. 性能基准报告
3. 测试执行报告
4. 问题和建议

**质量标准：**
1. 代码覆盖率>95%
2. 分支覆盖率>90%
3. 所有测试通过
4. 性能指标达标
5. 无内存泄漏

**最终交付：**
- 测试类（10个）
- 测试工具（3个）
- 测试数据（2个）
- 测试报告（1个）
- CI/CD配置

**测试执行命令：**
```bash
# 运行所有测试
./mvnw test

# 运行特定测试
./mvnw test -Dtest=TFIMainAPITest

# 生成覆盖率报告
./mvnw test jacoco:report

# 运行性能测试
./mvnw test -Dtest=PerformanceBenchmarkTest

# 运行压力测试
./mvnw test -Dtest=StressTest -DforkCount=0
```
```