package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChangeTracker并发测试
 * 
 * 测试覆盖：
 * - 多线程隔离性
 * - 线程池场景清理
 * - 并发追踪无污染
 * - WeakReference防泄漏
 * - ThreadLocal清理验证
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
@DisplayName("ChangeTracker并发测试")
public class ChangeTrackerConcurrencyTest {

    // 测试用的简单对象
    public static class TestObject {
        public String name;
        public int value;
        
        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    @BeforeEach
    void setUp() {
        // 确保每个测试开始前清理干净
        ChangeTracker.clearAllTracking();
    }

    @Test
    @DisplayName("线程隔离性-10线程并发追踪无污染")
    void testThreadIsolation() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        ConcurrentMap<String, List<ChangeRecord>> resultMap = new ConcurrentHashMap<>();

        try {
            // 提交10个并发任务
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 等待统一开始
                        
                        // 每个线程追踪自己的对象
                        String objName = "obj_" + threadId;
                        TestObject obj = new TestObject("thread_" + threadId, threadId);
                        
                        ChangeTracker.track(objName, obj);
                        
                        // 修改对象
                        obj.setName("modified_" + threadId);
                        obj.setValue(threadId * 100);
                        
                        // 获取变更
                        List<ChangeRecord> changes = ChangeTracker.getChanges();
                        resultMap.put(Thread.currentThread().getName(), new ArrayList<>(changes));
                        
                        // 清理
                        ChangeTracker.clearAllTracking();
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            // 统一开始
            startLatch.countDown();
            
            // 等待所有线程完成
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS));

            // 验证每个线程只看到自己的变更
            assertEquals(threadCount, resultMap.size());
            for (Map.Entry<String, List<ChangeRecord>> entry : resultMap.entrySet()) {
                List<ChangeRecord> changes = entry.getValue();
                assertTrue(changes.size() >= 1 && changes.size() <= 2, 
                    "每个线程应该检测到1-2个变更，实际: " + changes.size());
                
                // 验证变更内容属于同一个对象
                String objectName = changes.get(0).getObjectName();
                assertTrue(objectName.startsWith("obj_"));
                for (ChangeRecord change : changes) {
                    assertEquals(objectName, change.getObjectName());
                }
            }
            
        } finally {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("线程池场景-清理后无残留")
    void testThreadPoolCleanup() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch phase1 = new CountDownLatch(2);
        CountDownLatch phase2 = new CountDownLatch(2);

        try {
            // 第一阶段：两个任务追踪对象
            Future<Integer> task1 = executor.submit(() -> {
                ChangeTracker.track("task1", new TestObject("first", 1));
                int count = ChangeTracker.getTrackedCount();
                phase1.countDown();
                return count;
            });

            Future<Integer> task2 = executor.submit(() -> {
                ChangeTracker.track("task2", new TestObject("second", 2));
                int count = ChangeTracker.getTrackedCount();
                phase1.countDown();
                return count;
            });

            phase1.await(2, TimeUnit.SECONDS);
            assertEquals(1, task1.get(), "每个线程应该只看到自己的追踪对象");
            assertEquals(1, task2.get(), "每个线程应该只看到自己的追踪对象");

            // 第二阶段：相同线程池复用，验证清理效果
            Future<Integer> task3 = executor.submit(() -> {
                // 先清理
                ChangeTracker.clearAllTracking();
                // 验证清理后无残留
                int beforeCount = ChangeTracker.getTrackedCount();
                assertEquals(0, beforeCount, "清理后应该无追踪对象");
                
                // 新的追踪
                ChangeTracker.track("task3", new TestObject("third", 3));
                phase2.countDown();
                return ChangeTracker.getTrackedCount();
            });

            Future<Integer> task4 = executor.submit(() -> {
                // 先清理
                ChangeTracker.clearAllTracking();
                // 验证清理后无残留
                int beforeCount = ChangeTracker.getTrackedCount();
                assertEquals(0, beforeCount, "清理后应该无追踪对象");
                
                // 新的追踪
                ChangeTracker.track("task4", new TestObject("fourth", 4));
                phase2.countDown();
                return ChangeTracker.getTrackedCount();
            });

            phase2.await(2, TimeUnit.SECONDS);
            assertEquals(1, task3.get(), "新任务应该只有自己的追踪对象");
            assertEquals(1, task4.get(), "新任务应该只有自己的追踪对象");

        } finally {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @RepeatedTest(3)
    @DisplayName("高并发场景-16线程并发修改")
    void testHighConcurrency() throws Exception {
        int threadCount = 16;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Future<Void> future = executor.submit(() -> {
                    try {
                        barrier.await(); // 等待所有线程就绪
                        
                        for (int j = 0; j < operationsPerThread; j++) {
                            TestObject obj = new TestObject("obj_" + threadId + "_" + j, j);
                            
                            // 追踪
                            ChangeTracker.track("tracked_" + j, obj);
                            
                            // 修改
                            obj.setName("modified_" + j);
                            obj.setValue(j * 10);
                            
                            // 获取变更
                            List<ChangeRecord> changes = ChangeTracker.getChanges();
                            
                            if (!changes.isEmpty()) {
                                successCount.incrementAndGet();
                            }
                            
                            // 每10次操作清理一次
                            if (j % 10 == 9) {
                                ChangeTracker.clearAllTracking();
                            }
                        }
                        
                        // 最终清理
                        ChangeTracker.clearAllTracking();
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        e.printStackTrace();
                    }
                    return null;
                });
                futures.add(future);
            }

            // 等待所有任务完成
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            // 验证
            assertEquals(0, errorCount.get(), "不应该有错误发生");
            assertTrue(successCount.get() > 0, "应该有成功的变更检测");
            System.out.printf("高并发测试完成：%d个成功检测%n", successCount.get());

        } finally {
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("WeakReference验证-对象被GC后自动清理")
    void testWeakReferenceCleanup() throws Exception {
        // 追踪一个对象
        TestObject obj = new TestObject("weak", 1);
        ChangeTracker.track("weakObj", obj);
        assertEquals(1, ChangeTracker.getTrackedCount());

        // 修改并获取变更
        obj.setName("modified");
        List<ChangeRecord> changes1 = ChangeTracker.getChanges();
        assertEquals(1, changes1.size());
        assertEquals(1, ChangeTracker.getTrackedCount(), "对象应该仍被追踪");

        // 释放强引用，建议GC
        obj = null;
        System.gc();
        Thread.sleep(100); // 给GC一些时间

        // 再次获取变更，应该自动清理已GC的对象
        List<ChangeRecord> changes2 = ChangeTracker.getChanges();
        assertTrue(changes2.isEmpty(), "GC后的对象不应产生变更");
        
        // 被GC的对象应该被自动移除
        assertEquals(0, ChangeTracker.getTrackedCount(), "GC后的对象应该被自动清理");
    }

    @Test
    @DisplayName("异常场景-追踪过程中的异常不影响其他对象")
    void testExceptionIsolation() {
        // 追踪多个对象
        TestObject obj1 = new TestObject("obj1", 1);
        TestObject obj2 = new TestObject("obj2", 2);
        TestObject obj3 = new TestObject("obj3", 3);
        
        ChangeTracker.track("obj1", obj1);
        ChangeTracker.track("obj2", obj2);
        ChangeTracker.track("obj3", obj3);
        
        assertEquals(3, ChangeTracker.getTrackedCount());
        
        // 修改对象
        obj1.setName("modified1");
        obj2.setName("modified2");
        obj3.setName("modified3");
        
        // 模拟obj2被意外置null（但WeakReference仍存在）
        obj2 = null;
        System.gc();
        
        // 获取变更，obj2应该被跳过但不影响其他对象
        List<ChangeRecord> changes = ChangeTracker.getChanges();
        
        // 应该至少有obj1和obj3的变更
        assertTrue(changes.size() >= 2, "应该至少检测到2个对象的变更");
        
        // 验证obj1和obj3的变更存在
        Set<String> changedObjects = new HashSet<>();
        for (ChangeRecord change : changes) {
            changedObjects.add(change.getObjectName());
        }
        assertTrue(changedObjects.contains("obj1"));
        assertTrue(changedObjects.contains("obj3"));
    }

    @Test
    @DisplayName("清理验证-clearAllTracking彻底清理ThreadLocal")
    void testCompleteCleanup() {
        // 追踪多个对象
        for (int i = 0; i < 10; i++) {
            ChangeTracker.track("obj" + i, new TestObject("name" + i, i));
        }
        assertEquals(10, ChangeTracker.getTrackedCount());
        
        // 清理
        ChangeTracker.clearAllTracking();
        
        // 验证彻底清理
        assertEquals(0, ChangeTracker.getTrackedCount(), "清理后应该没有追踪对象");
        
        // 新的追踪应该从空开始
        ChangeTracker.track("newObj", new TestObject("new", 100));
        assertEquals(1, ChangeTracker.getTrackedCount(), "新追踪应该只有1个对象");
    }

    @Test
    @DisplayName("线程传播测试-父子线程隔离")
    void testParentChildThreadIsolation() throws Exception {
        // 父线程追踪对象
        TestObject parentObj = new TestObject("parent", 1);
        ChangeTracker.track("parentObj", parentObj);
        assertEquals(1, ChangeTracker.getTrackedCount());
        
        // 创建子线程
        CompletableFuture<Integer> childFuture = CompletableFuture.supplyAsync(() -> {
            // 子线程应该看不到父线程的追踪对象
            int initialCount = ChangeTracker.getTrackedCount();
            
            // 子线程追踪自己的对象
            TestObject childObj = new TestObject("child", 2);
            ChangeTracker.track("childObj", childObj);
            
            return initialCount;
        });
        
        // 验证子线程看不到父线程的对象
        assertEquals(0, childFuture.get(1, TimeUnit.SECONDS), 
            "子线程不应该看到父线程的追踪对象");
        
        // 父线程仍然有自己的对象
        assertEquals(1, ChangeTracker.getTrackedCount(), 
            "父线程的追踪对象不受子线程影响");
    }
}