# TFI-MVP-262 生命周期测试

## 任务概述
测试TaskFlowInsight的三个清理时机（stop/close/endSession），确保清理逻辑一致且幂等，无内存泄漏和NPE风险。

## 核心目标
- [ ] 验证stop()后快照清空
- [ ] 验证ManagedThreadContext.close()后清空
- [ ] 验证TFI.endSession()兜底清空
- [ ] 确保三处清理一致且幂等
- [ ] 验证无NPE异常
- [ ] 确认无内存泄漏迹象

## 实现清单

### 1. 生命周期清理测试主类
```java
package com.syy.taskflowinsight.core.lifecycle;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.ChangeRecord;
import com.syy.taskflowinsight.core.context.ManagedThreadContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("生命周期清理测试")
class LifecycleCleanupTest {
    
    @BeforeEach
    void setUp() {
        // 确保测试开始前清理状态
        TFI.endSession();
    }
    
    @AfterEach
    void tearDown() {
        // 确保测试结束后清理状态
        TFI.endSession();
    }
    
    @Test
    @DisplayName("stop()方法清理测试")
    void testStopMethodCleanup() {
        // 创建追踪器并添加数据
        ChangeTracker tracker = TFI.start("stop-cleanup-test");
        TestObject obj = new TestObject("test");
        
        tracker.track("testObj", obj);
        obj.setValue("modified");
        
        // 验证有变更数据
        List<ChangeRecord> changesBeforeStop = tracker.getChanges();
        assertFalse(changesBeforeStop.isEmpty(), "stop前应该有变更数据");
        
        // 调用stop清理
        TFI.stop();
        
        // 验证清理后状态
        ChangeTracker newTracker = TFI.start("after-stop-test");
        try {
            List<ChangeRecord> changesAfterStop = newTracker.getChanges();
            assertTrue(changesAfterStop.isEmpty(), 
                "stop()后新tracker应该没有历史数据");
        } finally {
            TFI.stop();
        }
        
        // 验证重复调用stop幂等
        assertDoesNotThrow(() -> TFI.stop(), 
            "重复调用stop()不应该抛出异常");
        assertDoesNotThrow(() -> TFI.stop(), 
            "多次重复调用stop()不应该抛出异常");
    }
    
    @Test
    @DisplayName("ManagedThreadContext.close()清理测试")
    void testManagedContextCloseCleanup() {
        TestObject obj1 = new TestObject("obj1");
        TestObject obj2 = new TestObject("obj2");
        
        // 使用try-with-resources确保close被调用
        try (ManagedThreadContext context = new ManagedThreadContext("context-cleanup-test")) {
            ChangeTracker tracker = context.getTracker();
            
            tracker.track("obj1", obj1);
            tracker.track("obj2", obj2);
            
            obj1.setValue("modified1");
            obj2.setValue("modified2");
            
            // 验证context内有数据
            List<ChangeRecord> changesInContext = tracker.getChanges();
            assertEquals(2, changesInContext.size(), 
                "context内应该有2个变更");
            
        } // close()在这里被自动调用
        
        // 验证close后清理
        ChangeTracker newTracker = TFI.start("after-close-test");
        try {
            List<ChangeRecord> changesAfterClose = newTracker.getChanges();
            assertTrue(changesAfterClose.isEmpty(), 
                "close()后新tracker应该没有历史数据");
        } finally {
            TFI.stop();
        }
        
        // 验证重复close幂等性
        ManagedThreadContext context = new ManagedThreadContext("repeat-close-test");
        assertDoesNotThrow(() -> context.close(), 
            "首次close不应该抛出异常");
        assertDoesNotThrow(() -> context.close(), 
            "重复close不应该抛出异常");
    }
    
    @Test
    @DisplayName("TFI.endSession()兜底清理测试")
    void testEndSessionCleanup() {
        List<ChangeTracker> trackers = new ArrayList<>();
        
        // 创建多个tracker模拟复杂场景
        for (int i = 0; i < 5; i++) {
            ChangeTracker tracker = TFI.start("session-test-" + i);
            trackers.add(tracker);
            
            TestObject obj = new TestObject("obj-" + i);
            tracker.track("obj", obj);
            obj.setValue("session-modified-" + i);
        }
        
        // 验证有多个tracker的数据
        boolean hasData = false;
        for (ChangeTracker tracker : trackers) {
            if (!tracker.getChanges().isEmpty()) {
                hasData = true;
                break;
            }
        }
        assertTrue(hasData, "session清理前应该有数据");
        
        // 调用endSession兜底清理
        TFI.endSession();
        
        // 验证全部清理
        ChangeTracker newTracker = TFI.start("after-session-cleanup");
        try {
            List<ChangeRecord> changesAfterSession = newTracker.getChanges();
            assertTrue(changesAfterSession.isEmpty(), 
                "endSession()后应该没有历史数据");
        } finally {
            TFI.stop();
        }
        
        // 验证重复endSession幂等
        assertDoesNotThrow(() -> TFI.endSession(), 
            "重复调用endSession()不应该抛出异常");
        assertDoesNotThrow(() -> TFI.endSession(), 
            "多次重复调用endSession()不应该抛出异常");
    }
    
    @Test
    @DisplayName("三处清理一致性测试")
    void testCleanupConsistency() {
        // 测试场景1：stop -> endSession
        testConsistencyScenario("consistency-stop", () -> {
            TFI.stop();
            TFI.endSession();
        });
        
        // 测试场景2：close -> stop
        testConsistencyScenario("consistency-close", () -> {
            try (ManagedThreadContext context = new ManagedThreadContext("temp")) {
                // context会在这里自动close
            }
            TFI.stop();
        });
        
        // 测试场景3：endSession -> stop -> endSession
        testConsistencyScenario("consistency-mixed", () -> {
            TFI.endSession();
            TFI.stop();
            TFI.endSession();
        });
    }
    
    private void testConsistencyScenario(String scenarioName, Runnable cleanupAction) {
        // 准备测试数据
        ChangeTracker tracker = TFI.start(scenarioName);
        TestObject obj = new TestObject(scenarioName);
        tracker.track("obj", obj);
        obj.setValue("modified-" + scenarioName);
        
        // 验证有数据
        assertFalse(tracker.getChanges().isEmpty(), 
            scenarioName + ": 清理前应该有数据");
        
        // 执行清理
        assertDoesNotThrow(cleanupAction::run, 
            scenarioName + ": 清理操作不应该抛出异常");
        
        // 验证清理结果
        ChangeTracker newTracker = TFI.start(scenarioName + "-after");
        try {
            assertTrue(newTracker.getChanges().isEmpty(), 
                scenarioName + ": 清理后应该没有数据");
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("清理幂等性测试")
    void testCleanupIdempotency() {
        // 创建数据
        ChangeTracker tracker = TFI.start("idempotency-test");
        TestObject obj = new TestObject("idempotent");
        tracker.track("obj", obj);
        obj.setValue("modified");
        
        // 多次调用各种清理方法
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> TFI.stop(), 
                "第" + (i+1) + "次stop()调用不应该异常");
        }
        
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> TFI.endSession(), 
                "第" + (i+1) + "次endSession()调用不应该异常");
        }
        
        // 验证最终状态
        ChangeTracker finalTracker = TFI.start("final-check");
        try {
            assertTrue(finalTracker.getChanges().isEmpty(), 
                "多次清理后应该没有残留数据");
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("NPE防护测试")
    void testNPEPrevention() {
        // 在没有创建tracker的情况下调用清理方法
        assertDoesNotThrow(() -> TFI.stop(), 
            "无tracker时stop()不应该NPE");
        assertDoesNotThrow(() -> TFI.endSession(), 
            "无tracker时endSession()不应该NPE");
        
        // 创建tracker但不添加数据直接清理
        ChangeTracker emptyTracker = TFI.start("empty-tracker");
        assertDoesNotThrow(() -> TFI.stop(), 
            "空tracker时stop()不应该NPE");
        
        // 再次清理已清理的tracker
        assertDoesNotThrow(() -> TFI.stop(), 
            "已清理tracker再次stop()不应该NPE");
        
        // 在已清理状态下尝试操作
        assertDoesNotThrow(() -> {
            ChangeTracker newTracker = TFI.start("after-cleanup");
            newTracker.getChanges(); // 应该返回空列表而不是NPE
            TFI.stop();
        }, "清理后的操作不应该NPE");
    }
    
    @Test
    @DisplayName("内存泄漏预防测试")
    void testMemoryLeakPrevention() {
        // 创建大量数据模拟内存压力
        List<TestObject> objects = new ArrayList<>();
        
        for (int batch = 0; batch < 10; batch++) {
            ChangeTracker tracker = TFI.start("memory-test-" + batch);
            
            // 每个batch创建大量对象
            for (int i = 0; i < 100; i++) {
                TestObject obj = new TestObject("batch-" + batch + "-obj-" + i);
                objects.add(obj);
                tracker.track("obj" + i, obj);
                
                // 修改对象产生变更记录
                obj.setValue("large-string-" + "x".repeat(1000) + "-" + i);
            }
            
            // 验证有数据
            List<ChangeRecord> changes = tracker.getChanges();
            assertEquals(100, changes.size(), 
                "batch " + batch + " 应该有100个变更");
            
            // 清理当前batch
            TFI.stop();
            
            // 强制GC（仅用于测试，生产环境不推荐）
            if (batch % 3 == 0) {
                System.gc();
                try {
                    Thread.sleep(10); // 给GC一点时间
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // 最终清理
        TFI.endSession();
        
        // 验证清理完成
        ChangeTracker finalTracker = TFI.start("final-memory-check");
        try {
            assertTrue(finalTracker.getChanges().isEmpty(), 
                "最终应该没有残留数据");
        } finally {
            TFI.stop();
        }
        
        // 建议GC清理测试产生的对象
        objects.clear();
        System.gc();
    }
    
    @Test
    @DisplayName("异常场景下的清理测试")
    void testCleanupInExceptionScenarios() {
        // 场景1：track过程中异常
        assertDoesNotThrow(() -> {
            ChangeTracker tracker = TFI.start("exception-track");
            try {
                TestObject obj = new TestObject("exception");
                tracker.track("obj", obj);
                
                // 模拟异常
                throw new RuntimeException("模拟track异常");
                
            } catch (RuntimeException e) {
                // 异常处理，确保清理仍然有效
                assertEquals("模拟track异常", e.getMessage());
            } finally {
                TFI.stop(); // 应该能正常清理
            }
        }, "异常场景下清理不应该失败");
        
        // 场景2：getChanges过程中异常
        assertDoesNotThrow(() -> {
            ChangeTracker tracker = TFI.start("exception-getChanges");
            try {
                TestObject obj = new TestObject("exception");
                tracker.track("obj", obj);
                obj.setValue("modified");
                
                // 正常获取变更
                List<ChangeRecord> changes = tracker.getChanges();
                assertNotNull(changes);
                
                // 模拟后续异常
                throw new RuntimeException("模拟业务异常");
                
            } catch (RuntimeException e) {
                assertEquals("模拟业务异常", e.getMessage());
            } finally {
                TFI.stop();
            }
        }, "getChanges异常后清理不应该失败");
        
        // 验证异常后状态正常
        ChangeTracker newTracker = TFI.start("after-exception");
        try {
            assertTrue(newTracker.getChanges().isEmpty(), 
                "异常处理后应该没有残留数据");
        } finally {
            TFI.stop();
        }
    }
    
    // 测试辅助类
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

### 2. 内存泄漏检测测试
```java
package com.syy.taskflowinsight.core.lifecycle;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("内存泄漏检测测试")
class MemoryLeakDetectionTest {
    
    @Test
    @EnabledIfSystemProperty(named = "memory.test", matches = "true")
    @DisplayName("长期运行内存稳定性测试")
    void testLongRunningMemoryStability() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // 记录初始内存使用
        System.gc();
        MemoryUsage initialMemory = memoryBean.getHeapMemoryUsage();
        long initialUsed = initialMemory.getUsed();
        
        System.out.println("初始内存使用: " + (initialUsed / 1024 / 1024) + " MB");
        
        // 执行大量操作
        for (int cycle = 0; cycle < 50; cycle++) {
            List<TestData> testDataList = new ArrayList<>();
            
            ChangeTracker tracker = TFI.start("memory-cycle-" + cycle);
            
            // 创建大量测试数据
            for (int i = 0; i < 200; i++) {
                TestData data = new TestData("data-" + cycle + "-" + i);
                data.setLargeData("x".repeat(1000)); // 1KB字符串
                
                testDataList.add(data);
                tracker.track("data" + i, data);
                
                // 修改数据
                data.setValue("modified-" + i);
                data.setLargeData("y".repeat(1500)); // 1.5KB字符串
            }
            
            // 获取变更（模拟正常使用）
            var changes = tracker.getChanges();
            assertEquals(200, changes.size());
            
            // 清理
            TFI.stop();
            
            // 清理测试数据
            testDataList.clear();
            
            // 定期检查内存
            if (cycle % 10 == 0) {
                System.gc();
                MemoryUsage currentMemory = memoryBean.getHeapMemoryUsage();
                long currentUsed = currentMemory.getUsed();
                long growth = currentUsed - initialUsed;
                
                System.out.printf("周期 %d: 当前内存 %d MB, 增长 %d MB%n", 
                    cycle, currentUsed / 1024 / 1024, growth / 1024 / 1024);
                
                // 内存增长不应该超过100MB（经验值）
                assertTrue(growth < 100 * 1024 * 1024, 
                    "周期 " + cycle + " 内存增长过大: " + (growth / 1024 / 1024) + " MB");
            }
        }
        
        // 最终清理和检查
        TFI.endSession();
        System.gc();
        Thread.yield(); // 让GC有机会运行
        
        MemoryUsage finalMemory = memoryBean.getHeapMemoryUsage();
        long finalUsed = finalMemory.getUsed();
        long totalGrowth = finalUsed - initialUsed;
        
        System.out.printf("最终内存使用: %d MB, 总增长: %d MB%n", 
            finalUsed / 1024 / 1024, totalGrowth / 1024 / 1024);
        
        // 总内存增长应该在合理范围内
        assertTrue(totalGrowth < 50 * 1024 * 1024, 
            "总内存增长过大: " + (totalGrowth / 1024 / 1024) + " MB");
    }
    
    @Test
    @DisplayName("ThreadLocal清理验证")
    void testThreadLocalCleanup() {
        // 创建多个线程，每个线程使用TFI
        Thread[] threads = new Thread[5];
        final Object[] threadResults = new Object[threads.length];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    ChangeTracker tracker = TFI.start("thread-" + threadIndex);
                    
                    TestData data = new TestData("thread-data-" + threadIndex);
                    tracker.track("data", data);
                    data.setValue("thread-modified-" + threadIndex);
                    
                    var changes = tracker.getChanges();
                    threadResults[threadIndex] = changes.size();
                    
                } finally {
                    TFI.stop(); // 清理当前线程的ThreadLocal
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("线程等待被中断");
            }
        }
        
        // 验证每个线程都有结果
        for (int i = 0; i < threadResults.length; i++) {
            assertEquals(1, threadResults[i], "线程 " + i + " 应该有1个变更");
        }
        
        // 主线程验证清理
        ChangeTracker mainTracker = TFI.start("main-after-threads");
        try {
            assertTrue(mainTracker.getChanges().isEmpty(), 
                "主线程应该没有其他线程的数据");
        } finally {
            TFI.stop();
        }
    }
    
    private static class TestData {
        private String name;
        private String value;
        private String largeData;
        
        public TestData(String name) {
            this.name = name;
            this.value = "initial";
            this.largeData = "";
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getLargeData() { return largeData; }
        public void setLargeData(String largeData) { this.largeData = largeData; }
    }
}
```

### 3. 清理完整性验证测试
```java
package com.syy.taskflowinsight.core.lifecycle;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("清理完整性验证测试")
class CleanupCompletenessTest {
    
    @Test
    @DisplayName("HeapDump模拟验证清理完整性")
    void testCleanupCompleteness() {
        // 创建大量数据
        ChangeTracker tracker = TFI.start("completeness-test");
        
        for (int i = 0; i < 1000; i++) {
            SimpleTestObject obj = new SimpleTestObject("obj-" + i);
            tracker.track("obj" + i, obj);
            obj.setValue("modified-" + i);
        }
        
        // 验证有大量变更
        var changesBefore = tracker.getChanges();
        assertEquals(1000, changesBefore.size(), "应该有1000个变更");
        
        // 执行清理
        TFI.stop();
        
        // 验证完全清理
        ChangeTracker newTracker = TFI.start("after-cleanup");
        try {
            var changesAfter = newTracker.getChanges();
            assertTrue(changesAfter.isEmpty(), "清理后应该没有任何残留数据");
            
            // 验证可以正常使用
            SimpleTestObject newObj = new SimpleTestObject("new");
            newTracker.track("newObj", newObj);
            newObj.setValue("new-modified");
            
            var newChanges = newTracker.getChanges();
            assertEquals(1, newChanges.size(), "新操作应该正常工作");
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("计数器验证清理效果")
    void testCounterBasedCleanupVerification() {
        // 模拟计数器（实际实现中可能有内部计数器）
        int operationCount = 0;
        
        ChangeTracker tracker = TFI.start("counter-test");
        
        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 50; i++) {
                SimpleTestObject obj = new SimpleTestObject("batch" + batch + "-obj" + i);
                tracker.track("obj" + operationCount, obj);
                obj.setValue("value" + operationCount);
                operationCount++;
            }
            
            // 验证当前状态
            var currentChanges = tracker.getChanges();
            assertEquals(operationCount, currentChanges.size(), 
                "当前应该有 " + operationCount + " 个变更");
        }
        
        // 最终验证
        assertEquals(500, operationCount, "总共应该有500次操作");
        var finalChangesBefore = tracker.getChanges();
        assertEquals(500, finalChangesBefore.size(), "清理前应该有500个变更");
        
        // 清理
        TFI.stop();
        
        // 验证计数器重置
        ChangeTracker newTracker = TFI.start("counter-after-cleanup");
        try {
            var changesAfterCleanup = newTracker.getChanges();
            assertEquals(0, changesAfterCleanup.size(), "清理后计数器应该重置为0");
        } finally {
            TFI.stop();
        }
    }
    
    private static class SimpleTestObject {
        private String name;
        private String value;
        
        public SimpleTestObject(String name) {
            this.name = name;
            this.value = "initial";
        }
        
        public String getName() { return name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
```

## 验证步骤
- [ ] stop()清理测试通过
- [ ] ManagedThreadContext.close()清理测试通过
- [ ] TFI.endSession()兜底清理测试通过
- [ ] 三处清理一致性验证
- [ ] 清理幂等性验证
- [ ] NPE防护测试通过
- [ ] 内存泄漏检测无异常
- [ ] 异常场景下清理正常

## 完成标准
- [ ] 所有生命周期测试用例通过
- [ ] 日志输出仅INFO/WARN级别
- [ ] HeapDump/计数器验证无残留
- [ ] 重复调用清理方法幂等
- [ ] 异常场景下清理机制正常工作
- [ ] 无内存泄漏迹象