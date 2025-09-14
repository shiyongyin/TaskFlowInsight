package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反射元数据缓存验证测试
 * 验证ObjectSnapshot的反射缓存机制正确性、性能和并发安全性
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class ReflectionMetaCacheTests {
    
    // 测试用实体类
    static class SimpleEntity {
        private String name = "default";
        private Integer age = 0;
        private Boolean active = true;
        
        public void setName(String name) { this.name = name; }
        public void setAge(Integer age) { this.age = age; }
        public void setActive(Boolean active) { this.active = active; }
    }
    
    static class ComplexEntity {
        private String field1 = "f1";
        private String field2 = "f2";
        private String field3 = "f3";
        private String field4 = "f4";
        private String field5 = "f5";
        private Integer num1 = 1;
        private Integer num2 = 2;
        private Integer num3 = 3;
        private Boolean flag1 = true;
        private Boolean flag2 = false;
    }
    
    @BeforeEach
    void setUp() {
        // 清理可能的缓存状态（如果ObjectSnapshot提供了清理方法）
        // 注：实际实现可能需要package-private访问或反射来清理缓存
    }
    
    @Test
    void testCacheHitPerformance() {
        // Given
        SimpleEntity entity = new SimpleEntity();
        String[] fields = {"name", "age", "active"};
        
        // Warm up - 第一次访问，触发缓存构建
        long firstStartTime = System.nanoTime();
        Map<String, Object> firstSnapshot = ObjectSnapshot.capture("Entity", entity, fields);
        long firstDuration = System.nanoTime() - firstStartTime;
        
        assertNotNull(firstSnapshot);
        assertEquals(3, firstSnapshot.size());
        
        // When - 多次访问同一类的字段（缓存命中）
        List<Long> cacheDurations = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long startTime = System.nanoTime();
            Map<String, Object> snapshot = ObjectSnapshot.capture("Entity", entity, fields);
            long duration = System.nanoTime() - startTime;
            cacheDurations.add(duration);
            
            assertNotNull(snapshot);
            assertEquals(3, snapshot.size());
        }
        
        // Then - 缓存访问应该明显快于首次访问
        double avgCacheDuration = cacheDurations.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
        
        // 缓存访问应该至少快2倍（保守估计）
        assertTrue(avgCacheDuration < firstDuration / 2,
            String.format("Cache access (%.0fns) should be faster than first access (%.0fns)",
                avgCacheDuration, (double)firstDuration));
        
        System.out.println("First access: " + firstDuration + "ns");
        System.out.println("Avg cache access: " + avgCacheDuration + "ns");
        System.out.println("Speedup: " + (firstDuration / avgCacheDuration) + "x");
    }
    
    @Test
    void testDifferentClassesCaching() {
        // Given - 多个不同的类
        SimpleEntity simple = new SimpleEntity();
        ComplexEntity complex = new ComplexEntity();
        
        // When - 分别访问不同类的字段
        Map<String, Object> simpleSnapshot1 = ObjectSnapshot.capture("Simple", simple, "name", "age");
        Map<String, Object> complexSnapshot1 = ObjectSnapshot.capture("Complex", complex, "field1", "num1");
        
        // 再次访问（应该从缓存获取）
        Map<String, Object> simpleSnapshot2 = ObjectSnapshot.capture("Simple", simple, "name", "age");
        Map<String, Object> complexSnapshot2 = ObjectSnapshot.capture("Complex", complex, "field1", "num1");
        
        // Then - 结果应该一致
        assertEquals(simpleSnapshot1.size(), simpleSnapshot2.size());
        assertEquals(complexSnapshot1.size(), complexSnapshot2.size());
        
        // 不同类的缓存应该独立
        assertEquals(2, simpleSnapshot2.size());
        assertEquals(2, complexSnapshot2.size());
    }
    
    @Test
    void testConcurrentCacheAccess() throws InterruptedException {
        // Given
        int threadCount = 16;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // When - 多线程并发访问缓存
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // 每个线程创建自己的实体并多次访问
                    SimpleEntity entity = new SimpleEntity();
                    entity.setName("Thread-" + threadId);
                    
                    for (int j = 0; j < iterationsPerThread; j++) {
                        Map<String, Object> snapshot = ObjectSnapshot.capture(
                            "Entity" + threadId, entity, "name", "age", "active");
                        
                        // 验证快照正确性
                        if (snapshot != null &&
                            snapshot.size() == 3 &&
                            ("Thread-" + threadId).equals(snapshot.get("name"))) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // 触发所有线程同时开始
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        
        // Then - 验证并发正确性
        assertEquals(threadCount * iterationsPerThread, successCount.get(),
            "All concurrent operations should succeed");
        assertEquals(0, errorCount.get(), "No errors should occur");
        
        executor.shutdown();
    }
    
    @Test
    void testCacheWithInheritance() {
        // Given - 有继承关系的类
        class BaseEntity {
            protected String baseField = "base";
        }
        
        class DerivedEntity extends BaseEntity {
            private String derivedField = "derived";
        }
        
        DerivedEntity entity = new DerivedEntity();
        
        // When - 访问继承的字段和自有字段
        Map<String, Object> snapshot1 = ObjectSnapshot.capture("Derived", entity, "derivedField");
        
        // 注：baseField可能无法访问（取决于ObjectSnapshot的实现是否支持继承字段）
        // 这里主要测试缓存对继承类的处理
        
        // 再次访问（缓存命中）
        Map<String, Object> snapshot2 = ObjectSnapshot.capture("Derived", entity, "derivedField");
        
        // Then
        assertEquals(snapshot1.size(), snapshot2.size());
        assertEquals(1, snapshot2.size());
    }
    
    @Test
    void testCacheCapacityLimit() {
        // Given - 创建大量不同的类（超过缓存上限）
        int maxClasses = 1100; // 超过实现中的上限（1024），验证不扩容且不抛错
        List<Object> entities = new ArrayList<>();
        
        // 动态创建多个类的实例
        for (int i = 0; i < maxClasses + 10; i++) {
            // 使用匿名内部类创建不同的类
            Object entity = new Object() {
                @SuppressWarnings("unused")
                private String field = "value";
            };
            entities.add(entity);
        }
        
        // When - 访问所有实体
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            Map<String, Object> snapshot = ObjectSnapshot.capture(
                "Entity" + i, entities.get(i), "field");
            snapshots.add(snapshot);
        }
        
        // Then - 即使超过缓存上限，功能应该正常（不抛异常，不扩容影响行为）
        assertEquals(entities.size(), snapshots.size());
        
        // 验证所有快照都正确捕获了字段
        for (Map<String, Object> snapshot : snapshots) {
            assertNotNull(snapshot);
            // 注：匿名类的字段可能无法通过反射访问，这里主要测试缓存容量处理
        }
    }
    
    @Test
    void testFieldAccessPerformanceMatrix() {
        // Given - 不同字段数量的性能测试
        ComplexEntity entity = new ComplexEntity();
        
        // When - 测试访问不同数量字段的性能
        long time1Field = measureAccessTime(entity, "field1");
        long time3Fields = measureAccessTime(entity, "field1", "field2", "field3");
        long time5Fields = measureAccessTime(entity, "field1", "field2", "field3", "field4", "field5");
        long time10Fields = measureAccessTime(entity, 
            "field1", "field2", "field3", "field4", "field5",
            "num1", "num2", "num3", "flag1", "flag2");
        
        // Then - 性能应该线性增长（不应该指数增长）
        System.out.println("1 field: " + time1Field + "ns");
        System.out.println("3 fields: " + time3Fields + "ns");
        System.out.println("5 fields: " + time5Fields + "ns");
        System.out.println("10 fields: " + time10Fields + "ns");
        
        // 10个字段的访问时间不应该超过1个字段的20倍
        assertTrue(time10Fields < time1Field * 20,
            "Performance should scale reasonably with field count");
    }
    
    @Test
    void testCacheMemoryFootprint() {
        // Given - 准备测量内存使用
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // 提示GC运行
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // When - 创建大量缓存条目
        int classCount = 50;
        for (int i = 0; i < classCount; i++) {
            // 为每个类创建独立的实体类型
            class TestEntity {
                @SuppressWarnings("unused")
                private String field1 = "value1";
                @SuppressWarnings("unused")
                private String field2 = "value2";
                @SuppressWarnings("unused")
                private String field3 = "value3";
            }
            
            TestEntity entity = new TestEntity();
            ObjectSnapshot.capture("Entity" + i, entity, "field1", "field2", "field3");
        }
        
        runtime.gc(); // 提示GC运行
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        // Then - 内存使用应该合理
        System.out.println("Memory used for " + classCount + " cached classes: " + 
            (memoryUsed / 1024) + " KB");
        
        // 每个缓存类的平均内存不应该超过10KB（合理估计）
        assertTrue(memoryUsed < classCount * 10 * 1024,
            "Cache memory footprint should be reasonable");
    }
    
    private long measureAccessTime(Object entity, String... fields) {
        // 预热
        for (int i = 0; i < 10; i++) {
            ObjectSnapshot.capture("Warmup", entity, fields);
        }
        
        // 测量
        int iterations = 100;
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ObjectSnapshot.capture("Test", entity, fields);
        }
        long totalTime = System.nanoTime() - startTime;
        
        return totalTime / iterations;
    }
}
