# TFI-MVP-261 并发测试

## 任务概述
验证多线程环境下的变更追踪数据隔离性和上下文传播的正确性，确保TFIAwareExecutor能正确传播上下文且变更归属正确。

## 核心目标
- [ ] 验证10~16线程并发修改不同对象的隔离性
- [ ] 验证子线程变更正确挂载到对应TaskNode
- [ ] 测试TFIAwareExecutor上下文传播
- [ ] 确保无交叉污染和随机失败
- [ ] 验证总耗时可接受

## 实现清单

### 1. 并发隔离测试主类
```java
package com.syy.taskflowinsight.core.concurrency;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.ChangeRecord;
import com.syy.taskflowinsight.core.executor.TFIAwareExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("并发隔离与归属正确性测试")
class ConcurrencyIsolationTest {
    
    @Test
    @DisplayName("10线程并发修改不同对象隔离性测试")
    void testConcurrentIsolation_10Threads() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        
        ConcurrentHashMap<String, List<ChangeRecord>> threadResults = new ConcurrentHashMap<>();
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 等待所有线程就绪
                    startLatch.await();
                    
                    String taskName = "concurrent-task-" + threadId;
                    ChangeTracker tracker = TFI.start(taskName);
                    
                    try {
                        // 每个线程处理自己的测试对象
                        TestOrder order = new TestOrder("ORDER-" + threadId, "PENDING");
                        tracker.track("order", order);
                        
                        // 模拟业务处理时间
                        Thread.sleep(10 + threadId);
                        
                        // 修改对象
                        order.setStatus("PROCESSED-" + threadId);
                        order.setAmount(100.0 + threadId);
                        order.setOrderId("UPDATED-ORDER-" + threadId);
                        
                        // 再次等待，模拟并发峰值
                        Thread.sleep(5);
                        
                        List<ChangeRecord> changes = tracker.getChanges();
                        threadResults.put(taskName, changes);
                        
                    } finally {
                        TFI.stop();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // 开始执行
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS), 
            "测试超时，可能存在死锁");
        executor.shutdown();
        
        // 验证结果
        assertEquals(0, errorCount.get(), "不应该有执行错误");
        assertEquals(threadCount, threadResults.size(), "所有线程都应该有结果");
        
        // 验证每个线程的变更独立
        for (int i = 0; i < threadCount; i++) {
            String taskName = "concurrent-task-" + i;
            List<ChangeRecord> changes = threadResults.get(taskName);
            
            assertNotNull(changes, "线程 " + i + " 应该有变更记录");
            assertEquals(3, changes.size(), "应该有3个字段变更"); // status, amount, orderId
            
            // 验证变更内容包含线程ID，确保无交叉污染
            boolean hasThreadSpecificChanges = changes.stream()
                .anyMatch(change -> change.getNewValueRepr().contains(String.valueOf(i)));
            assertTrue(hasThreadSpecificChanges, 
                "线程 " + i + " 的变更应该包含线程特定内容");
        }
    }
    
    @Test
    @DisplayName("16线程高并发测试")
    void testHighConcurrency_16Threads() throws InterruptedException {
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ConcurrencyTestResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Future<ConcurrencyTestResult> future = executor.submit(() -> {
                // 等待统一开始
                startLatch.await();
                
                long startTime = System.currentTimeMillis();
                String taskName = "high-concurrency-" + threadId;
                
                ChangeTracker tracker = TFI.start(taskName);
                try {
                    // 创建多个对象进行复杂操作
                    List<TestOrder> orders = new ArrayList<>();
                    for (int j = 0; j < 5; j++) {
                        TestOrder order = new TestOrder(
                            "ORDER-" + threadId + "-" + j, "PENDING");
                        orders.add(order);
                        tracker.track("order" + j, order);
                    }
                    
                    // 批量修改
                    for (int j = 0; j < orders.size(); j++) {
                        TestOrder order = orders.get(j);
                        order.setStatus("BATCH-PROCESSED");
                        order.setAmount(threadId * 100.0 + j);
                    }
                    
                    List<ChangeRecord> changes = tracker.getChanges();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    return new ConcurrencyTestResult(taskName, threadId, changes, duration);
                    
                } finally {
                    TFI.stop();
                }
            });
            futures.add(future);
        }
        
        // 开始执行
        startLatch.countDown();
        
        // 收集结果
        List<ConcurrencyTestResult> results = new ArrayList<>();
        for (Future<ConcurrencyTestResult> future : futures) {
            ConcurrencyTestResult result = future.get(30, TimeUnit.SECONDS);
            results.add(result);
        }
        
        executor.shutdown();
        
        // 验证结果
        assertEquals(threadCount, results.size());
        
        // 验证隔离性：每个线程应该有10个变更（5个对象×2个字段）
        for (ConcurrencyTestResult result : results) {
            assertEquals(10, result.getChanges().size(), 
                "线程 " + result.getThreadId() + " 应该有10个变更");
            
            // 验证性能：总耗时应该可接受（<1000ms）
            assertTrue(result.getDuration() < 1000, 
                "线程 " + result.getThreadId() + " 耗时过长: " + result.getDuration() + "ms");
        }
        
        // 验证无交叉污染
        Set<String> allTaskNames = new HashSet<>();
        for (ConcurrencyTestResult result : results) {
            allTaskNames.add(result.getTaskName());
        }
        assertEquals(threadCount, allTaskNames.size(), "所有任务名应该唯一");
    }
    
    @Test
    @DisplayName("TFIAwareExecutor上下文传播测试")
    void testTFIAwareExecutorPropagation() throws Exception {
        String parentTaskName = "parent-task";
        ChangeTracker parentTracker = TFI.start(parentTaskName);
        
        try {
            TestOrder parentOrder = new TestOrder("PARENT-ORDER", "PENDING");
            parentTracker.track("parentOrder", parentOrder);
            
            // 修改父任务对象
            parentOrder.setStatus("PARENT-PROCESSING");
            
            // 使用TFIAware执行器创建子任务
            TFIAwareExecutor tfiExecutor = new TFIAwareExecutor(
                Executors.newFixedThreadPool(4));
            
            List<Future<SubTaskResult>> childFutures = new ArrayList<>();
            
            for (int i = 0; i < 4; i++) {
                final int childId = i;
                Future<SubTaskResult> future = tfiExecutor.submit(() -> {
                    String childTaskName = "child-task-" + childId;
                    ChangeTracker childTracker = TFI.start(childTaskName);
                    
                    try {
                        TestOrder childOrder = new TestOrder(
                            "CHILD-ORDER-" + childId, "PENDING");
                        childTracker.track("childOrder", childOrder);
                        
                        // 修改子任务对象
                        childOrder.setStatus("CHILD-PROCESSED-" + childId);
                        childOrder.setAmount(childId * 50.0);
                        
                        List<ChangeRecord> childChanges = childTracker.getChanges();
                        return new SubTaskResult(childTaskName, childId, childChanges);
                        
                    } finally {
                        TFI.stop();
                    }
                });
                childFutures.add(future);
            }
            
            // 收集子任务结果
            List<SubTaskResult> childResults = new ArrayList<>();
            for (Future<SubTaskResult> future : childFutures) {
                SubTaskResult result = future.get(10, TimeUnit.SECONDS);
                childResults.add(result);
            }
            
            tfiExecutor.shutdown();
            
            // 验证子任务结果
            assertEquals(4, childResults.size());
            
            for (SubTaskResult result : childResults) {
                assertNotNull(result.getChanges());
                assertEquals(2, result.getChanges().size()); // status + amount
                
                // 验证变更归属正确
                boolean hasChildSpecificChanges = result.getChanges().stream()
                    .anyMatch(change -> 
                        change.getNewValueRepr().contains(String.valueOf(result.getChildId())));
                assertTrue(hasChildSpecificChanges, 
                    "子任务 " + result.getChildId() + " 的变更应该包含任务特定内容");
            }
            
            // 验证父任务变更仍然独立
            List<ChangeRecord> parentChanges = parentTracker.getChanges();
            assertEquals(1, parentChanges.size()); // 只有status变更
            assertEquals("PARENT-PROCESSING", 
                parentChanges.get(0).getNewValueRepr());
            
        } finally {
            TFI.stop();
        }
    }
    
    @RepeatedTest(5)
    @DisplayName("重复并发测试确保稳定性")
    void testRepeatedConcurrency() throws InterruptedException {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        Set<String> allChangeContents = ConcurrentHashMap.newKeySet();
        AtomicInteger totalChanges = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    ChangeTracker tracker = TFI.start("repeat-test-" + threadId);
                    try {
                        TestOrder order = new TestOrder("REPEAT-" + threadId, "PENDING");
                        tracker.track("order", order);
                        
                        // 随机延迟增加并发复杂性
                        Thread.sleep(new Random().nextInt(20));
                        
                        order.setStatus("REPEAT-DONE-" + threadId);
                        order.setAmount(threadId * 10.0);
                        
                        List<ChangeRecord> changes = tracker.getChanges();
                        totalChanges.addAndGet(changes.size());
                        
                        // 记录变更内容用于验证唯一性
                        for (ChangeRecord change : changes) {
                            allChangeContents.add(change.toString());
                        }
                        
                    } finally {
                        TFI.stop();
                    }
                } catch (Exception e) {
                    fail("重复测试失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // 验证结果
        assertEquals(threadCount * 2, totalChanges.get()); // 每个线程2个变更
        assertTrue(allChangeContents.size() >= threadCount * 2, 
            "所有变更应该是唯一的");
    }
    
    @Test
    @DisplayName("异常场景下的并发安全性")
    void testConcurrencyWithExceptions() throws InterruptedException {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    ChangeTracker tracker = TFI.start("exception-test-" + threadId);
                    try {
                        TestOrder order = new TestOrder("EXCEPTION-" + threadId, "PENDING");
                        tracker.track("order", order);
                        
                        if (threadId % 3 == 0) {
                            // 模拟部分线程抛出异常
                            throw new RuntimeException("模拟异常 " + threadId);
                        }
                        
                        order.setStatus("SUCCESS-" + threadId);
                        List<ChangeRecord> changes = tracker.getChanges();
                        
                        assertNotNull(changes);
                        successCount.incrementAndGet();
                        
                    } finally {
                        TFI.stop();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // 验证异常不影响其他线程
        assertEquals(4, successCount.get()); // 6个线程中4个成功
        assertEquals(2, exceptionCount.get()); // 2个异常
    }
    
    // 测试结果数据类
    private static class ConcurrencyTestResult {
        private final String taskName;
        private final int threadId;
        private final List<ChangeRecord> changes;
        private final long duration;
        
        public ConcurrencyTestResult(String taskName, int threadId, 
                                   List<ChangeRecord> changes, long duration) {
            this.taskName = taskName;
            this.threadId = threadId;
            this.changes = changes;
            this.duration = duration;
        }
        
        // Getters
        public String getTaskName() { return taskName; }
        public int getThreadId() { return threadId; }
        public List<ChangeRecord> getChanges() { return changes; }
        public long getDuration() { return duration; }
    }
    
    private static class SubTaskResult {
        private final String taskName;
        private final int childId;
        private final List<ChangeRecord> changes;
        
        public SubTaskResult(String taskName, int childId, List<ChangeRecord> changes) {
            this.taskName = taskName;
            this.childId = childId;
            this.changes = changes;
        }
        
        // Getters
        public String getTaskName() { return taskName; }
        public int getChildId() { return childId; }
        public List<ChangeRecord> getChanges() { return changes; }
    }
    
    // 测试数据类
    private static class TestOrder {
        private String orderId;
        private String status;
        private Double amount;
        
        public TestOrder(String orderId, String status) {
            this.orderId = orderId;
            this.status = status;
            this.amount = 0.0;
        }
        
        // Getters and Setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }
}
```

### 2. 上下文传播专项测试
```java
package com.syy.taskflowinsight.core.executor;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.ChangeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TFIAware执行器上下文传播测试")
class TFIAwareExecutorTest {
    
    @Test
    @DisplayName("简单上下文传播测试")
    void testSimpleContextPropagation() throws Exception {
        ChangeTracker mainTracker = TFI.start("main-task");
        
        try {
            TestObject mainObj = new TestObject("main");
            mainTracker.track("mainObj", mainObj);
            mainObj.setValue("main-modified");
            
            TFIAwareExecutor executor = new TFIAwareExecutor(
                Executors.newSingleThreadExecutor());
            
            Future<List<ChangeRecord>> future = executor.submit(() -> {
                ChangeTracker subTracker = TFI.start("sub-task");
                try {
                    TestObject subObj = new TestObject("sub");
                    subTracker.track("subObj", subObj);
                    subObj.setValue("sub-modified");
                    
                    return subTracker.getChanges();
                } finally {
                    TFI.stop();
                }
            });
            
            List<ChangeRecord> subChanges = future.get(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertNotNull(subChanges);
            assertEquals(1, subChanges.size());
            assertEquals("sub-modified", subChanges.get(0).getNewValueRepr());
            
            // 验证主任务变更独立
            List<ChangeRecord> mainChanges = mainTracker.getChanges();
            assertEquals(1, mainChanges.size());
            assertEquals("main-modified", mainChanges.get(0).getNewValueRepr());
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("嵌套任务上下文传播测试")
    void testNestedContextPropagation() throws Exception {
        ChangeTracker rootTracker = TFI.start("root-task");
        
        try {
            TFIAwareExecutor executor = new TFIAwareExecutor(
                Executors.newFixedThreadPool(2));
            
            // 第一层子任务
            Future<String> level1Future = executor.submit(() -> {
                ChangeTracker level1Tracker = TFI.start("level1-task");
                try {
                    TestObject level1Obj = new TestObject("level1");
                    level1Tracker.track("level1Obj", level1Obj);
                    level1Obj.setValue("level1-value");
                    
                    // 第二层子任务
                    Future<String> level2Future = executor.submit(() -> {
                        ChangeTracker level2Tracker = TFI.start("level2-task");
                        try {
                            TestObject level2Obj = new TestObject("level2");
                            level2Tracker.track("level2Obj", level2Obj);
                            level2Obj.setValue("level2-value");
                            
                            List<ChangeRecord> level2Changes = level2Tracker.getChanges();
                            return "Level2: " + level2Changes.size() + " changes";
                        } finally {
                            TFI.stop();
                        }
                    });
                    
                    String level2Result = level2Future.get();
                    List<ChangeRecord> level1Changes = level1Tracker.getChanges();
                    return "Level1: " + level1Changes.size() + " changes, " + level2Result;
                    
                } finally {
                    TFI.stop();
                }
            });
            
            String result = level1Future.get(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertTrue(result.contains("Level1: 1 changes"));
            assertTrue(result.contains("Level2: 1 changes"));
            
        } finally {
            TFI.stop();
        }
    }
    
    private static class TestObject {
        private String name;
        private String value;
        
        public TestObject(String name) {
            this.name = name;
            this.value = "initial";
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
```

### 3. 压力测试
```java
package com.syy.taskflowinsight.core.concurrency;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("并发压力测试")
class ConcurrencyStressTest {
    
    @Test
    @EnabledIfSystemProperty(named = "stress.test", matches = "true")
    @DisplayName("大量并发任务压力测试")
    void testHighVolumeConcurrency() throws InterruptedException {
        int threadCount = 50;
        int tasksPerThread = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalDuration = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    long threadStart = System.currentTimeMillis();
                    
                    for (int j = 0; j < tasksPerThread; j++) {
                        String taskName = "stress-" + threadId + "-" + j;
                        ChangeTracker tracker = TFI.start(taskName);
                        
                        try {
                            StressTestObject obj = new StressTestObject(threadId, j);
                            tracker.track("obj", obj);
                            
                            // 多次修改
                            obj.setField1("modified-" + j);
                            obj.setField2(threadId * 1000 + j);
                            obj.setField3(j % 2 == 0);
                            
                            tracker.getChanges();
                            totalOperations.incrementAndGet();
                            
                        } finally {
                            TFI.stop();
                        }
                    }
                    
                    long threadDuration = System.currentTimeMillis() - threadStart;
                    totalDuration.addAndGet(threadDuration);
                    
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS), "压力测试超时");
        executor.shutdown();
        
        long totalTime = System.currentTimeMillis() - startTime;
        long expectedOperations = (long) threadCount * tasksPerThread;
        
        assertEquals(expectedOperations, totalOperations.get());
        
        System.out.printf("压力测试完成: %d 操作, 总时间: %d ms, 平均: %.2f ms/op%n",
            totalOperations.get(), totalTime, 
            (double) totalTime / totalOperations.get());
        
        // 验证性能指标
        double avgTimePerOp = (double) totalTime / totalOperations.get();
        assertTrue(avgTimePerOp < 10.0, 
            "平均操作时间过长: " + avgTimePerOp + "ms");
    }
    
    private static class StressTestObject {
        private int threadId;
        private int taskId;
        private String field1;
        private Integer field2;
        private Boolean field3;
        
        public StressTestObject(int threadId, int taskId) {
            this.threadId = threadId;
            this.taskId = taskId;
            this.field1 = "initial";
            this.field2 = 0;
            this.field3 = false;
        }
        
        // Getters and Setters
        public int getThreadId() { return threadId; }
        public int getTaskId() { return taskId; }
        public String getField1() { return field1; }
        public void setField1(String field1) { this.field1 = field1; }
        public Integer getField2() { return field2; }
        public void setField2(Integer field2) { this.field2 = field2; }
        public Boolean getField3() { return field3; }
        public void setField3(Boolean field3) { this.field3 = field3; }
    }
}
```

## 验证步骤
- [ ] 10线程并发测试隔离性验证通过
- [ ] 16线程高并发测试无异常
- [ ] TFIAwareExecutor上下文传播正确
- [ ] 无交叉污染现象
- [ ] 无随机失败情况
- [ ] 总耗时在可接受范围
- [ ] 压力测试稳定通过

## 完成标准
- [ ] 所有并发测试用例稳定重复通过
- [ ] 日志输出证明数据隔离正确
- [ ] Console/JSON导出片段验证归属关系
- [ ] 性能指标符合预期
- [ ] 异常场景下系统行为正常