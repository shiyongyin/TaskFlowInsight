# TASK-013: API接口单元测试

## 任务概述

为TaskFlowInsight的核心API接口实现全面的单元测试，验证所有API接口的正确性、健壮性和异常安全性。确保TFI核心API、TaskContext接口以及相关功能模块在各种使用场景下的稳定性。

## 需求分析

1. 对TFI核心API进行单元测试，验证所有公共方法的正确性
2. 对TaskContext接口进行全面的功能测试，验证任务生命周期操作
3. 实现异常安全测试，验证异常情况下API的稳健性
4. 实现性能基准测试，确保API性能满足要求
5. 实现并发安全测试，验证多线程环境下API的正确性

## 技术实现

### 1. TFI核心API单元测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class TFIMainAPITest {
    
    @BeforeEach
    void setUp() {
        // 清理之前的状态，确保测试独立性
        TFI.cleanup();
    }
    
    @AfterEach
    void tearDown() {
        // 测试后清理
        TFI.cleanup();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试基本API流程")
    void testBasicAPIFlow() {
        // 测试正常的start -> message -> stop流程
        TaskContext task = TFI.start("test-task");
        assertNotNull(task);
        assertEquals("test-task", task.getTaskName());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        
        // 添加消息
        TFI.message("Test message");
        
        // 结束任务
        TFI.stop();
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }
    
    @Test
    @Order(2)
    @DisplayName("测试嵌套任务")
    void testNestedTasks() {
        TaskContext rootTask = TFI.start("root-task");
        assertNotNull(rootTask);
        assertEquals(1, rootTask.getDepth());
        
        // 开始子任务
        TaskContext childTask = TFI.start("child-task");
        assertNotNull(childTask);
        assertEquals(2, childTask.getDepth());
        assertEquals(rootTask, childTask.getParent());
        
        // 开始孙子任务
        TaskContext grandChildTask = TFI.start("grandchild-task");
        assertNotNull(grandChildTask);
        assertEquals(3, grandChildTask.getDepth());
        assertEquals(childTask, grandChildTask.getParent());
        
        // 按顺序结束任务
        TFI.stop(); // 结束孙子任务
        assertEquals(TaskStatus.COMPLETED, grandChildTask.getStatus());
        
        TFI.stop(); // 结束子任务
        assertEquals(TaskStatus.COMPLETED, childTask.getStatus());
        
        TFI.stop(); // 结束根任务
        assertEquals(TaskStatus.COMPLETED, rootTask.getStatus());
    }
    
    @Test
    @Order(3)
    @DisplayName("测试消息记录功能")
    void testMessageRecording() {
        TaskContext task = TFI.start("message-test");
        
        // 添加不同类型的消息
        TFI.message("Simple message");
        TFI.message("Formatted message: %s", "param1");
        TFI.message("Multiple params: %s, %d, %b", "param1", 42, true);
        
        // 验证消息记录
        List<Message> messages = task.getMessages();
        assertEquals(3, messages.size());
        
        assertEquals("Simple message", messages.get(0).getContent());
        assertEquals("Formatted message: param1", messages.get(1).getContent());
        assertEquals("Multiple params: param1, 42, true", messages.get(2).getContent());
        
        TFI.stop();
    }
    
    @Test
    @Order(4)
    @DisplayName("测试printTree功能")
    void testPrintTreeFunctionality() {
        // 重定向标准输出以捕获printTree输出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            // 创建嵌套任务结构
            TaskContext rootTask = TFI.start("root");
            TFI.message("Root message");
            
            TaskContext childTask = TFI.start("child");
            TFI.message("Child message");
            
            // 调用printTree
            TFI.printTree();
            
            // 验证输出包含期望的内容
            String output = outputStream.toString();
            assertTrue(output.contains("root"));
            assertTrue(output.contains("child"));
            
            TFI.stop(); // child
            TFI.stop(); // root
            
        } finally {
            System.setOut(originalOut);
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("测试exportJson功能")
    void testExportJsonFunctionality() {
        // 创建任务结构
        TaskContext rootTask = TFI.start("json-test");
        TFI.message("Root message");
        
        TaskContext childTask = TFI.start("child-task");
        TFI.message("Child message");
        TFI.stop(); // child
        
        // 导出JSON
        String jsonResult = TFI.exportJson();
        assertNotNull(jsonResult);
        assertFalse(jsonResult.isEmpty());
        
        // 验证JSON包含期望的结构
        assertTrue(jsonResult.contains("json-test"));
        assertTrue(jsonResult.contains("child-task"));
        assertTrue(jsonResult.contains("Root message"));
        assertTrue(jsonResult.contains("Child message"));
        
        TFI.stop(); // root
    }
    
    @Test
    @Order(6)
    @DisplayName("测试enable/disable功能")
    void testEnableDisableFunctionality() {
        // 测试启用状态
        assertTrue(TFI.isEnabled());
        
        TaskContext enabledTask = TFI.start("enabled-test");
        assertNotNull(enabledTask);
        assertEquals(TaskStatus.RUNNING, enabledTask.getStatus());
        TFI.stop();
        
        // 禁用TFI
        TFI.disable();
        assertFalse(TFI.isEnabled());
        
        // 禁用状态下的操作应该返回禁用的TaskContext
        TaskContext disabledTask = TFI.start("disabled-test");
        assertNotNull(disabledTask);
        assertTrue(disabledTask instanceof DisabledTaskContext);
        
        // 重新启用
        TFI.enable();
        assertTrue(TFI.isEnabled());
        
        TaskContext reenabledTask = TFI.start("reenabled-test");
        assertNotNull(reenabledTask);
        assertEquals(TaskStatus.RUNNING, reenabledTask.getStatus());
        TFI.stop();
    }
    
    @Test
    @Order(7)
    @DisplayName("测试异常情况处理")
    void testExceptionHandling() {
        // 测试null参数处理
        assertDoesNotThrow(() -> {
            TaskContext task = TFI.start(null);
            assertNotNull(task);
            TFI.stop();
        });
        
        assertDoesNotThrow(() -> {
            TFI.start("exception-test");
            TFI.message(null);
            TFI.message(null, (Object[]) null);
            TFI.stop();
        });
        
        // 测试空栈情况
        assertDoesNotThrow(() -> {
            TFI.stop(); // 没有开始任务就结束
        });
        
        // 测试多次stop
        assertDoesNotThrow(() -> {
            TFI.start("multi-stop-test");
            TFI.stop();
            TFI.stop(); // 重复stop
            TFI.stop(); // 再次重复stop
        });
    }
}
```

### 2. TaskContext接口单元测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class TaskContextTest {
    
    @BeforeEach
    void setUp() {
        TFI.cleanup();
    }
    
    @AfterEach
    void tearDown() {
        TFI.cleanup();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试TaskContext基本属性")
    void testTaskContextBasicProperties() {
        TaskContext task = TFI.start("property-test");
        
        assertNotNull(task.getTaskId());
        assertEquals("property-test", task.getTaskName());
        assertEquals(1, task.getDepth());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertTrue(task.getStartTime() > 0);
        assertEquals(0, task.getEndTime()); // 未结束时应为0
        assertTrue(task.getDuration() > 0);
        
        TFI.stop();
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertTrue(task.getEndTime() > 0);
        assertTrue(task.getDuration() > 0);
    }
    
    @Test
    @Order(2)
    @DisplayName("测试TaskContext消息功能")
    void testTaskContextMessageFunctionality() {
        TaskContext task = TFI.start("message-test");
        
        // 测试链式调用
        TaskContext returnValue = task.message("Message 1")
                                      .message("Message 2")
                                      .message("Formatted: %s", "param");
        
        assertEquals(task, returnValue); // 验证链式调用返回自身
        
        List<Message> messages = task.getMessages();
        assertEquals(3, messages.size());
        assertEquals("Message 1", messages.get(0).getContent());
        assertEquals("Message 2", messages.get(1).getContent());
        assertEquals("Formatted: param", messages.get(2).getContent());
        
        TFI.stop();
    }
    
    @Test
    @Order(3)
    @DisplayName("测试TaskContext子任务功能")
    void testTaskContextSubTaskFunctionality() {
        TaskContext parentTask = TFI.start("parent-task");
        
        // 创建子任务
        TaskContext childTask1 = parentTask.startSubTask("child-1");
        TaskContext childTask2 = parentTask.startSubTask("child-2");
        
        assertNotNull(childTask1);
        assertNotNull(childTask2);
        assertEquals(2, childTask1.getDepth());
        assertEquals(2, childTask2.getDepth());
        assertEquals(parentTask, childTask1.getParent());
        assertEquals(parentTask, childTask2.getParent());
        
        // 验证父任务的子任务列表
        List<TaskContext> children = parentTask.getChildren();
        assertEquals(2, children.size());
        assertTrue(children.contains(childTask1));
        assertTrue(children.contains(childTask2));
        
        // 结束子任务
        childTask1.end();
        childTask2.end();
        assertEquals(TaskStatus.COMPLETED, childTask1.getStatus());
        assertEquals(TaskStatus.COMPLETED, childTask2.getStatus());
        
        TFI.stop();
    }
    
    @Test
    @Order(4)
    @DisplayName("测试TaskContext自动资源管理")
    void testTaskContextAutoResourceManagement() {
        TaskContext parentTask = TFI.start("resource-test");
        
        // 使用try-with-resources
        try (TaskContext subTask = parentTask.startSubTask("auto-close-test")) {
            subTask.message("Test message in try-with-resources");
            assertEquals(TaskStatus.RUNNING, subTask.getStatus());
        } // subTask应该自动关闭
        
        // 验证子任务已关闭
        List<TaskContext> children = parentTask.getChildren();
        assertEquals(1, children.size());
        assertEquals(TaskStatus.COMPLETED, children.get(0).getStatus());
        
        TFI.stop();
    }
    
    @Test
    @Order(5)
    @DisplayName("测试TaskContext异常处理")
    void testTaskContextExceptionHandling() {
        TaskContext task = TFI.start("exception-test");
        
        // 测试null参数处理
        assertDoesNotThrow(() -> {
            task.message(null);
            task.message(null, (Object[]) null);
            task.startSubTask(null);
            task.startSubTask("");
            task.startSubTask("   ");
        });
        
        // 测试已关闭任务的操作
        task.end();
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        
        // 在已关闭的任务上进行操作应该安全
        assertDoesNotThrow(() -> {
            TaskContext result = task.message("Message after close");
            assertEquals(task, result);
            
            TaskContext subTask = task.startSubTask("subtask-after-close");
            assertNotNull(subTask);
            assertTrue(subTask instanceof DisabledTaskContext);
        });
        
        // 多次end应该安全
        assertDoesNotThrow(() -> {
            task.end();
            task.end();
            task.close();
            task.close();
        });
    }
    
    @Test
    @Order(6)
    @DisplayName("测试TaskContext深度嵌套")
    void testTaskContextDeepNesting() {
        TaskContext currentTask = TFI.start("level-0");
        
        // 创建深度嵌套的任务结构
        for (int i = 1; i <= 100; i++) {
            TaskContext nextTask = currentTask.startSubTask("level-" + i);
            assertEquals(i + 1, nextTask.getDepth());
            assertEquals(currentTask, nextTask.getParent());
            currentTask = nextTask;
        }
        
        // 从最深层开始结束任务
        for (int i = 100; i >= 0; i--) {
            if (currentTask != null) {
                TaskContext parent = currentTask.getParent();
                currentTask.end();
                assertEquals(TaskStatus.COMPLETED, currentTask.getStatus());
                currentTask = parent;
            }
        }
        
        // 确保根任务也结束
        if (currentTask != null) {
            currentTask.end();
        }
    }
}
```

### 3. 异常安全测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class APISafetyTest {
    
    @BeforeEach
    void setUp() {
        TFI.enable(); // 确保TFI启用
        TFI.cleanup();
    }
    
    @AfterEach
    void tearDown() {
        TFI.cleanup();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试API异常安全性")
    void testAPIExceptionSafety() {
        // 模拟各种异常情况，验证API不会抛出未捕获的异常
        assertDoesNotThrow(() -> {
            // 极端参数测试
            TFI.start("");
            TFI.start("   ");
            TFI.start("a".repeat(10000)); // 很长的任务名
            TFI.stop();
            TFI.stop();
            TFI.stop();
        });
        
        assertDoesNotThrow(() -> {
            // 消息异常测试
            TFI.start("message-exception-test");
            TFI.message(null);
            TFI.message("");
            TFI.message("a".repeat(100000)); // 很长的消息
            TFI.message("Format test: %s %d %s", null, null, null);
            TFI.message("Bad format: %s %d", "only-one-param");
            TFI.stop();
        });
        
        assertDoesNotThrow(() -> {
            // 状态不一致测试
            TaskContext task1 = TFI.start("task1");
            TaskContext task2 = TFI.start("task2");
            
            // 直接结束任务而不通过TFI.stop()
            task1.end();
            task2.end();
            
            // 尝试TFI.stop()应该安全
            TFI.stop();
            TFI.stop();
        });
    }
    
    @Test
    @Order(2)
    @DisplayName("测试并发安全性")
    void testConcurrentAPISafety() throws InterruptedException {
        final int THREAD_COUNT = 20;
        final int OPERATIONS_PER_THREAD = 100;
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        String taskName = "thread_" + threadId + "_task_" + j;
                        
                        TaskContext task = TFI.start(taskName);
                        task.message("Message from thread " + threadId);
                        
                        // 随机创建子任务
                        if (j % 3 == 0) {
                            TaskContext subTask = task.startSubTask("subtask_" + j);
                            subTask.message("Sub message");
                            subTask.end();
                        }
                        
                        task.end();
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        
        assertEquals(THREAD_COUNT * OPERATIONS_PER_THREAD, successCount.get());
        assertEquals(0, errorCount.get());
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(3)
    @DisplayName("测试内存泄漏防护")
    void testMemoryLeakProtection() {
        Runtime runtime = Runtime.getRuntime();
        
        // 获取基线内存使用
        runtime.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 创建大量任务但不正确清理（模拟用户错误使用）
        for (int i = 0; i < 1000; i++) {
            TaskContext task = TFI.start("leak-test-" + i);
            task.message("Message " + i);
            
            for (int j = 0; j < 10; j++) {
                TaskContext subTask = task.startSubTask("subtask-" + j);
                subTask.message("Sub message " + j);
                // 故意不调用end()，模拟内存泄漏情况
            }
            
            // 只结束部分任务
            if (i % 2 == 0) {
                task.end();
            }
        }
        
        // 强制清理
        TFI.cleanup();
        
        // 强制垃圾回收
        for (int i = 0; i < 5; i++) {
            runtime.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - baselineMemory;
        
        // 验证内存增长在合理范围内（小于10MB）
        assertTrue(memoryIncrease < 10 * 1024 * 1024, 
            "Memory increase should be less than 10MB, but was: " + memoryIncrease + " bytes");
    }
    
    @Test
    @Order(4)
    @DisplayName("测试系统降级处理")
    void testSystemDegradation() {
        // 测试正常状态
        assertTrue(TFI.isEnabled());
        TaskContext normalTask = TFI.start("normal-task");
        assertTrue(normalTask instanceof TaskContextImpl);
        normalTask.end();
        
        // 触发系统降级
        TFI.disable();
        assertFalse(TFI.isEnabled());
        
        // 降级状态下的操作应该返回降级实现
        TaskContext degradedTask = TFI.start("degraded-task");
        assertTrue(degradedTask instanceof DisabledTaskContext);
        
        // 降级状态下的操作应该安全但无实际效果
        assertDoesNotThrow(() -> {
            degradedTask.message("This should be safe");
            TaskContext subTask = degradedTask.startSubTask("degraded-subtask");
            assertTrue(subTask instanceof DisabledTaskContext);
            
            TFI.message("Global message in degraded mode");
            TFI.printTree();
            String json = TFI.exportJson();
            assertNotNull(json);
            assertTrue(json.contains("disabled") || json.contains("degraded"));
        });
        
        // 恢复正常状态
        TFI.enable();
        assertTrue(TFI.isEnabled());
        
        TaskContext recoveredTask = TFI.start("recovered-task");
        assertTrue(recoveredTask instanceof TaskContextImpl);
        recoveredTask.end();
    }
}
```

### 4. 性能基准测试
```java
@TestMethodOrder(OrderAnnotation.class)
public class APIPerformanceTest {
    
    @BeforeEach
    void setUp() {
        TFI.cleanup();
        // JVM预热
        warmUp();
    }
    
    private void warmUp() {
        for (int i = 0; i < 1000; i++) {
            TaskContext task = TFI.start("warmup");
            task.message("warmup message");
            task.end();
        }
        TFI.cleanup();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试API性能基准")
    void testAPIPerformanceBenchmark() {
        final int ITERATIONS = 10000;
        
        // 测试任务创建和销毁性能
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            TaskContext task = TFI.start("perf-test-" + i);
            task.message("Performance test message " + i);
            task.end();
        }
        
        long duration = System.nanoTime() - startTime;
        long averageOperationTime = duration / (ITERATIONS * 3); // 3 operations per iteration
        
        System.out.println("API Performance Benchmark:");
        System.out.println("Total time: " + duration / 1_000_000 + " ms");
        System.out.println("Average operation time: " + averageOperationTime + " ns");
        System.out.println("Operations per second: " + (1_000_000_000L / averageOperationTime));
        
        // 验证性能要求：平均操作时间应小于2微秒
        assertTrue(averageOperationTime < 2000, 
            "Average operation time should be less than 2μs, but was: " + averageOperationTime + "ns");
    }
    
    @Test
    @Order(2)
    @DisplayName("测试嵌套任务性能")
    void testNestedTaskPerformance() {
        final int DEPTH = 1000;
        
        long startTime = System.nanoTime();
        
        // 创建深度嵌套任务
        TaskContext currentTask = TFI.start("root");
        for (int i = 1; i < DEPTH; i++) {
            currentTask = currentTask.startSubTask("level-" + i);
        }
        
        // 从最深层开始结束
        for (int i = DEPTH - 1; i >= 0; i--) {
            currentTask.end();
            if (currentTask.getParent() != null) {
                currentTask = currentTask.getParent();
            }
        }
        
        long duration = System.nanoTime() - startTime;
        long averageNestingTime = duration / (DEPTH * 2); // create + end
        
        System.out.println("Nested Task Performance (" + DEPTH + " levels):");
        System.out.println("Total time: " + duration / 1_000_000 + " ms");
        System.out.println("Average nesting operation time: " + averageNestingTime + " ns");
        
        // 验证深度嵌套性能
        assertTrue(averageNestingTime < 5000, 
            "Average nesting operation time should be less than 5μs, but was: " + averageNestingTime + "ns");
    }
    
    @Test
    @Order(3)
    @DisplayName("测试消息处理性能")
    void testMessagePerformance() {
        final int MESSAGE_COUNT = 100000;
        
        TaskContext task = TFI.start("message-performance-test");
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            task.message("Performance test message %d with value %s", i, "test-value-" + i);
        }
        
        long duration = System.nanoTime() - startTime;
        long averageMessageTime = duration / MESSAGE_COUNT;
        
        System.out.println("Message Performance (" + MESSAGE_COUNT + " messages):");
        System.out.println("Total time: " + duration / 1_000_000 + " ms");
        System.out.println("Average message time: " + averageMessageTime + " ns");
        System.out.println("Messages per second: " + (1_000_000_000L / averageMessageTime));
        
        // 验证消息处理性能
        assertTrue(averageMessageTime < 1000, 
            "Average message time should be less than 1μs, but was: " + averageMessageTime + "ns");
        
        task.end();
    }
}
```

## 关键特性

### 测试覆盖特性
1. **功能覆盖特性**：所有API接口的完整功能测试
2. **边界测试特性**：边界条件和极端情况验证
3. **异常覆盖特性**：异常场景和错误处理测试
4. **集成覆盖特性**：API间协作和集成测试

### 安全性测试特性
1. **异常安全特性**：异常情况下API稳定性验证
2. **并发安全特性**：多线程环境下API正确性验证
3. **内存安全特性**：内存泄漏防护和资源管理验证
4. **降级安全特性**：系统降级时的安全性验证

### 性能验证特性
1. **响应时间特性**：API调用响应时间基准测试
2. **吞吐量特性**：高负载下的吞吐量验证
3. **资源效率特性**：CPU和内存使用效率测试
4. **扩展性特性**：深度嵌套和大数据量场景测试

## 验收标准

### 功能验收
- [ ] TFI核心API所有方法测试通过率100%
- [ ] TaskContext接口所有方法测试通过率100%
- [ ] 嵌套任务功能正确性验证通过
- [ ] 消息记录和导出功能正确性验证通过

### 安全性验收
- [ ] 异常情况下API不抛出未捕获异常
- [ ] 并发测试中无竞争条件和数据不一致
- [ ] 内存泄漏防护机制有效
- [ ] 系统降级时API安全可用

### 性能验收
- [ ] 单个API调用平均响应时间小于2微秒
- [ ] 消息处理平均时间小于1微秒
- [ ] 支持1000层深度嵌套无性能问题
- [ ] 并发测试下无显著性能下降

## 依赖关系

### 前置依赖
- TASK-010: TFI主API实现
- TASK-011: TaskContext接口实现
- TASK-012: API异常安全处理实现

### 后置依赖
- 无

## 开发计划

### 分阶段开发计划
- **Day 1**: TFI主API和TaskContext基础功能测试
- **Day 2**: 异常安全和并发安全测试
- **Day 3**: 性能基准测试和测试报告生成

## 风险评估

### 技术风险
1. **测试复杂度风险**：API测试涉及多个组件，测试场景复杂
   - 缓解措施：分模块独立测试，逐步集成
2. **性能测试准确性**：JVM特性可能影响性能测试结果
   - 缓解措施：充分预热，多次测试取平均值

### 业务风险
1. **测试覆盖度风险**：可能遗漏某些边界情况
   - 缓解措施：基于用户使用场景设计测试用例
2. **测试维护成本**：大量测试用例的维护工作量大
   - 缓解措施：测试用例模块化，自动化测试流程

## 实现文件

1. **TFI主API测试** (`TFIMainAPITest.java`)
2. **TaskContext接口测试** (`TaskContextTest.java`)
3. **API安全性测试** (`APISafetyTest.java`)
4. **API性能测试** (`APIPerformanceTest.java`)
5. **测试工具类** (`TestUtils.java`)
6. **测试配置** (`test-application.yml`)
7. **测试报告模板** (`api-test-report-template.md`)