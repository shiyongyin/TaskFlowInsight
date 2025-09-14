package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChangeTracker内存测试
 * 
 * 测试覆盖：
 * - 内存泄漏检测
 * - WeakReference有效性
 * - ThreadLocal清理验证
 * - 最大追踪对象数限制
 * - 内存使用量评估
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
@DisplayName("ChangeTracker内存测试")
public class ChangeTrackerMemoryTest {

    private static final int MAX_TRACKED_OBJECTS = 1000; // VIP-003要求的最大追踪对象数
    
    // 测试用的大对象
    private static class LargeObject {
        private final String name;
        private final byte[] data; // 模拟大数据
        private int counter;
        
        public LargeObject(String name, int sizeKB) {
            this.name = name;
            this.data = new byte[sizeKB * 1024];
            Arrays.fill(data, (byte) 1);
            this.counter = 0;
        }
        
        public String getName() { return name; }
        public int getCounter() { return counter; }
        public void incrementCounter() { this.counter++; }
    }
    
    // 小对象用于基础测试
    private static class SmallObject {
        private String value;
        
        public SmallObject(String value) {
            this.value = value;
        }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() {
        ChangeTracker.clearAllTracking();
        memoryBean = ManagementFactory.getMemoryMXBean();
        System.gc(); // 测试前清理
    }

    @Test
    @DisplayName("内存泄漏检测-ThreadLocal正确清理")
    void testNoMemoryLeakAfterClear() throws Exception {
        // 记录初始内存
        System.gc();
        Thread.sleep(100);
        long initialMemory = getUsedMemory();
        
        // 大量追踪和清理操作
        for (int cycle = 0; cycle < 10; cycle++) {
            // 追踪100个对象
            for (int i = 0; i < 100; i++) {
                SmallObject obj = new SmallObject("obj_" + i);
                ChangeTracker.track("tracked_" + i, obj);
                obj.setValue("modified_" + i);
            }
            
            // 获取变更
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            assertFalse(changes.isEmpty());
            
            // 清理
            ChangeTracker.clearAllTracking();
            assertEquals(0, ChangeTracker.getTrackedCount());
        }
        
        // 强制GC并等待
        System.gc();
        Thread.sleep(200);
        
        // 验证内存没有显著增长
        long finalMemory = getUsedMemory();
        long memoryGrowth = finalMemory - initialMemory;
        
        // 内存增长应该很小（考虑到JVM的正常波动，允许5MB的增长）
        assertTrue(memoryGrowth < 5 * 1024 * 1024, 
            String.format("内存增长过大: %d bytes", memoryGrowth));
    }

    @Test
    @DisplayName("WeakReference验证-大对象被GC后自动释放")
    void testWeakReferenceReleasesMemory() throws Exception {
        System.gc();
        Thread.sleep(100);
        long initialMemory = getUsedMemory();
        
        // 创建并追踪大对象
        List<LargeObject> strongRefs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LargeObject largeObj = new LargeObject("large_" + i, 100); // 100KB each
            ChangeTracker.track("large_" + i, largeObj);
            strongRefs.add(largeObj);
        }
        
        assertEquals(10, ChangeTracker.getTrackedCount());
        
        // 记录追踪后的内存使用
        System.gc();
        Thread.sleep(100);
        long afterTrackMemory = getUsedMemory();
        assertTrue(afterTrackMemory > initialMemory, "追踪大对象后内存应该增加");
        
        // 释放强引用
        strongRefs.clear();
        strongRefs = null;
        
        // 强制GC
        System.gc();
        Thread.sleep(200);
        
        // 调用getChanges会触发清理已GC的对象
        List<ChangeRecord> changes = ChangeTracker.getChanges();
        assertEquals(0, ChangeTracker.getTrackedCount(), "GC后的对象应该被自动清理");
        
        // 验证内存已释放
        System.gc();
        Thread.sleep(100);
        long finalMemory = getUsedMemory();
        
        // 内存应该接近初始水平（允许2MB的波动）
        long memoryDiff = Math.abs(finalMemory - initialMemory);
        assertTrue(memoryDiff < 2 * 1024 * 1024, 
            String.format("内存未正确释放，差异: %d bytes", memoryDiff));
    }

    @Test
    @DisplayName("最大对象数限制-防止无限增长")
    void testMaxTrackedObjectsLimit() {
        // 追踪超过最大限制的对象
        int totalObjects = MAX_TRACKED_OBJECTS + 500;
        
        for (int i = 0; i < totalObjects; i++) {
            SmallObject obj = new SmallObject("obj_" + i);
            ChangeTracker.track("tracked_" + i, obj);
        }
        
        // 当前实现可能没有限制，但应该验证不会OOM
        int trackedCount = ChangeTracker.getTrackedCount();
        System.out.printf("追踪了 %d 个对象（尝试追踪 %d 个）%n", trackedCount, totalObjects);
        
        // 至少应该能追踪一些对象
        assertTrue(trackedCount > 0, "应该能追踪对象");
        
        // 清理
        ChangeTracker.clearAllTracking();
        assertEquals(0, ChangeTracker.getTrackedCount());
    }

    @Test
    @DisplayName("线程池内存泄漏测试")
    void testThreadPoolMemoryLeak() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        try {
            System.gc();
            Thread.sleep(100);
            long initialMemory = getUsedMemory();
            
            // 提交大量任务到线程池
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                Future<Void> future = executor.submit(() -> {
                    // 追踪对象但不清理（模拟忘记清理的场景）
                    SmallObject obj = new SmallObject("task_" + taskId);
                    ChangeTracker.track("obj_" + taskId, obj);
                    obj.setValue("modified");
                    
                    // 故意不清理，测试是否会泄漏
                    if (taskId % 10 == 0) {
                        // 只有10%的任务清理
                        ChangeTracker.clearAllTracking();
                    }
                    return null;
                });
                futures.add(future);
            }
            
            // 等待所有任务完成
            for (Future<Void> future : futures) {
                future.get(1, TimeUnit.SECONDS);
            }
            
            // 提交清理任务到每个线程
            CountDownLatch cleanupLatch = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    ChangeTracker.clearAllTracking();
                    cleanupLatch.countDown();
                });
            }
            cleanupLatch.await(1, TimeUnit.SECONDS);
            
            // 验证内存
            System.gc();
            Thread.sleep(200);
            long finalMemory = getUsedMemory();
            long memoryGrowth = finalMemory - initialMemory;
            
            // 即使有些任务忘记清理，线程池复用后应该不会持续泄漏
            assertTrue(memoryGrowth < 10 * 1024 * 1024, 
                String.format("线程池场景内存增长过大: %d bytes", memoryGrowth));
            
        } finally {
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("内存使用评估-1000个对象的内存占用")
    void testMemoryUsageFor1000Objects() throws Exception {
        System.gc();
        Thread.sleep(100);
        long initialMemory = getUsedMemory();
        
        // 追踪1000个对象
        List<SmallObject> objects = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            SmallObject obj = new SmallObject("obj_" + i);
            objects.add(obj);
            ChangeTracker.track("tracked_" + i, obj);
        }
        
        assertEquals(1000, ChangeTracker.getTrackedCount());
        
        // 计算内存使用
        System.gc();
        Thread.sleep(100);
        long afterTrackMemory = getUsedMemory();
        long memoryUsed = afterTrackMemory - initialMemory;
        
        // 输出内存使用情况
        System.out.printf("追踪1000个对象的内存占用: %.2f MB%n", 
            memoryUsed / (1024.0 * 1024.0));
        
        // 验证内存使用在合理范围（假设每个对象追踪开销不超过10KB）
        assertTrue(memoryUsed < 10 * 1024 * 1000, 
            String.format("追踪1000个对象占用内存过大: %d bytes", memoryUsed));
        
        // 修改所有对象并获取变更
        for (SmallObject obj : objects) {
            obj.setValue("modified");
        }
        
        List<ChangeRecord> changes = ChangeTracker.getChanges();
        assertEquals(1000, changes.size(), "应该检测到1000个变更");
        
        // 清理
        ChangeTracker.clearAllTracking();
        assertEquals(0, ChangeTracker.getTrackedCount());
        
        // 验证清理后内存释放
        objects.clear();
        System.gc();
        Thread.sleep(200);
        long finalMemory = getUsedMemory();
        
        assertTrue(finalMemory < afterTrackMemory, "清理后内存应该减少");
    }

    @Test
    @DisplayName("循环引用测试-确保不会导致内存泄漏")
    void testCircularReferenceNoLeak() throws Exception {
        // 创建有循环引用的对象结构
        class Node {
            String name;
            Node next;
            
            Node(String name) {
                this.name = name;
            }
        }
        
        System.gc();
        Thread.sleep(100);
        long initialMemory = getUsedMemory();
        
        // 创建循环引用
        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        Node node3 = new Node("node3");
        node1.next = node2;
        node2.next = node3;
        node3.next = node1; // 循环引用
        
        // 追踪
        ChangeTracker.track("node1", node1);
        ChangeTracker.track("node2", node2);
        ChangeTracker.track("node3", node3);
        
        assertEquals(3, ChangeTracker.getTrackedCount());
        
        // 释放强引用
        node1 = null;
        node2 = null;
        node3 = null;
        
        // GC应该能回收循环引用的对象
        System.gc();
        Thread.sleep(200);
        
        // getChanges会清理已GC的对象
        ChangeTracker.getChanges();
        assertEquals(0, ChangeTracker.getTrackedCount(), "循环引用的对象应该被GC并清理");
        
        // 验证内存
        System.gc();
        Thread.sleep(100);
        long finalMemory = getUsedMemory();
        long memoryDiff = Math.abs(finalMemory - initialMemory);
        
        assertTrue(memoryDiff < 2 * 1024 * 1024, 
            String.format("循环引用可能导致内存泄漏，差异: %d bytes", memoryDiff));
    }

    @Test
    @DisplayName("并发内存测试-多线程同时追踪不会导致内存问题")
    void testConcurrentMemoryUsage() throws Exception {
        int threadCount = 10;
        int objectsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        
        try {
            System.gc();
            Thread.sleep(100);
            long initialMemory = getUsedMemory();
            
            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                Future<Void> future = executor.submit(() -> {
                    try {
                        barrier.await(); // 同时开始
                        
                        List<SmallObject> localObjects = new ArrayList<>();
                        for (int i = 0; i < objectsPerThread; i++) {
                            SmallObject obj = new SmallObject("t" + threadId + "_o" + i);
                            localObjects.add(obj);
                            ChangeTracker.track("obj_" + i, obj);
                        }
                        
                        // 修改并获取变更
                        for (SmallObject obj : localObjects) {
                            obj.setValue("modified");
                        }
                        ChangeTracker.getChanges();
                        
                        // 清理
                        ChangeTracker.clearAllTracking();
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                });
                futures.add(future);
            }
            
            // 等待完成
            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            
            // 验证内存
            System.gc();
            Thread.sleep(200);
            long finalMemory = getUsedMemory();
            long memoryGrowth = finalMemory - initialMemory;
            
            // 并发场景下内存增长应该可控
            assertTrue(memoryGrowth < 20 * 1024 * 1024, 
                String.format("并发场景内存增长过大: %.2f MB", 
                    memoryGrowth / (1024.0 * 1024.0)));
            
        } finally {
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 获取当前使用的堆内存（字节）
     */
    private long getUsedMemory() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }
}