# TFI-MVP-264 缓存验证

## 任务概述
验证反射元数据缓存的有效性，确保缓存命中能减少反射耗时，验证缓存容量上限策略，测试并发安全性。

## 核心目标
- [ ] 验证缓存命中有效减少反射耗时
- [ ] 测试缓存容量上限策略
- [ ] 验证并发下computeIfAbsent正确性
- [ ] 确保后续访问明显低于首次访问
- [ ] 验证缓存大小受控
- [ ] 确认性能改善可见

## 实现清单

### 1. 反射缓存验证测试主类
```java
package com.syy.taskflowinsight.core.cache;

import com.syy.taskflowinsight.core.reflection.ReflectionCache;
import com.syy.taskflowinsight.core.reflection.FieldMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("反射元数据缓存验证测试")
class ReflectionCacheVerificationTest {
    
    private ReflectionCache reflectionCache;
    
    @BeforeEach
    void setUp() {
        // 重置缓存实例
        reflectionCache = new ReflectionCache();
    }
    
    @Test
    @DisplayName("缓存命中性能测试")
    void testCacheHitPerformance() {
        Class<?> testClass = TestCacheObject.class;
        
        // 第一次访问（缓存未命中）
        long firstAccessStart = System.nanoTime();
        List<FieldMetadata> firstResult = reflectionCache.getFieldMetadata(testClass);
        long firstAccessDuration = System.nanoTime() - firstAccessStart;
        
        assertNotNull(firstResult, "第一次访问应该返回结果");
        assertFalse(firstResult.isEmpty(), "应该有字段元数据");
        
        // 多次后续访问（缓存命中）
        List<Long> subsequentDurations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long subsequentStart = System.nanoTime();
            List<FieldMetadata> subsequentResult = reflectionCache.getFieldMetadata(testClass);
            long subsequentDuration = System.nanoTime() - subsequentStart;
            subsequentDurations.add(subsequentDuration);
            
            // 验证结果一致性
            assertEquals(firstResult.size(), subsequentResult.size(), 
                "缓存结果应该与首次结果一致");
        }
        
        // 计算平均后续访问时间
        double avgSubsequentDuration = subsequentDurations.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
        
        // 验证性能改善（后续访问应该明显快于首次）
        double performanceRatio = (double) firstAccessDuration / avgSubsequentDuration;
        
        System.out.printf("首次访问: %,d ns%n", firstAccessDuration);
        System.out.printf("平均后续访问: %,.0f ns%n", avgSubsequentDuration);
        System.out.printf("性能提升倍数: %.2fx%n", performanceRatio);
        
        assertTrue(performanceRatio > 2.0, 
            "缓存命中应该至少快2倍，实际: " + performanceRatio + "x");
        assertTrue(avgSubsequentDuration < firstAccessDuration / 2, 
            "后续访问应该明显快于首次访问");
    }
    
    @Test
    @DisplayName("缓存命中计数验证")
    void testCacheHitCounting() {
        Class<?> testClass1 = TestCacheObject.class;
        Class<?> testClass2 = AnotherTestCacheObject.class;
        
        // 获取初始统计
        CacheStatistics initialStats = reflectionCache.getStatistics();
        long initialHits = initialStats.getHitCount();
        long initialMisses = initialStats.getMissCount();
        
        // 第一次访问testClass1（缓存未命中）
        reflectionCache.getFieldMetadata(testClass1);
        
        CacheStatistics afterFirstAccess = reflectionCache.getStatistics();
        assertEquals(initialMisses + 1, afterFirstAccess.getMissCount(), 
            "应该增加1次未命中");
        
        // 再次访问testClass1（缓存命中）
        reflectionCache.getFieldMetadata(testClass1);
        reflectionCache.getFieldMetadata(testClass1);
        
        CacheStatistics afterRepeatedAccess = reflectionCache.getStatistics();
        assertEquals(initialHits + 2, afterRepeatedAccess.getHitCount(), 
            "应该增加2次命中");
        
        // 访问testClass2（缓存未命中）
        reflectionCache.getFieldMetadata(testClass2);
        
        CacheStatistics finalStats = reflectionCache.getStatistics();
        assertEquals(afterRepeatedAccess.getMissCount() + 1, finalStats.getMissCount(), 
            "应该再增加1次未命中");
        
        // 验证命中率计算
        double hitRate = finalStats.getHitRate();
        double expectedHitRate = (double) finalStats.getHitCount() / 
            (finalStats.getHitCount() + finalStats.getMissCount());
        assertEquals(expectedHitRate, hitRate, 0.001, "命中率计算应该正确");
        
        System.out.printf("缓存统计 - 命中: %d, 未命中: %d, 命中率: %.2f%%%n", 
            finalStats.getHitCount(), finalStats.getMissCount(), hitRate * 100);
    }
    
    @Test
    @DisplayName("缓存容量上限策略测试")
    void testCacheCapacityLimit() {
        // 设置较小的缓存容量用于测试
        ReflectionCache limitedCache = new ReflectionCache(10); // 容量限制为10
        
        List<Class<?>> testClasses = Arrays.asList(
            TestClass1.class, TestClass2.class, TestClass3.class,
            TestClass4.class, TestClass5.class, TestClass6.class,
            TestClass7.class, TestClass8.class, TestClass9.class,
            TestClass10.class, TestClass11.class, TestClass12.class
        );
        
        // 填充超过容量限制的类
        for (Class<?> clazz : testClasses) {
            limitedCache.getFieldMetadata(clazz);
        }
        
        CacheStatistics stats = limitedCache.getStatistics();
        
        // 验证缓存大小受限
        assertTrue(stats.getCacheSize() <= 10, 
            "缓存大小应该受限于容量，实际: " + stats.getCacheSize());
        
        // 验证达到上限时的行为（应该有淘汰策略或拒绝新增）
        assertTrue(stats.getMissCount() >= testClasses.size(), 
            "应该有缓存未命中记录");
        
        System.out.printf("容量限制测试 - 缓存大小: %d, 最大容量: %d%n", 
            stats.getCacheSize(), 10);
    }
    
    @Test
    @DisplayName("并发安全性测试")
    void testConcurrentSafety() throws InterruptedException {
        int threadCount = 16;
        int accessPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalAccessTime = new AtomicLong(0);
        
        List<Class<?>> testClasses = Arrays.asList(
            TestCacheObject.class, AnotherTestCacheObject.class,
            TestClass1.class, TestClass2.class
        );
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 等待所有线程就绪
                    startLatch.await();
                    
                    Random random = new Random(threadId);
                    long threadStartTime = System.nanoTime();
                    
                    for (int j = 0; j < accessPerThread; j++) {
                        // 随机访问不同的类
                        Class<?> randomClass = testClasses.get(
                            random.nextInt(testClasses.size()));
                        
                        List<FieldMetadata> metadata = reflectionCache.getFieldMetadata(randomClass);
                        
                        // 验证结果合法性
                        assertNotNull(metadata, 
                            "线程 " + threadId + " 访问 " + j + " 应该返回结果");
                    }
                    
                    long threadDuration = System.nanoTime() - threadStartTime;
                    totalAccessTime.addAndGet(threadDuration);
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // 开始测试
        startLatch.countDown();
        
        // 等待完成
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS), 
            "并发测试超时");
        executor.shutdown();
        
        // 验证结果
        assertEquals(0, errorCount.get(), "不应该有并发错误");
        
        CacheStatistics finalStats = reflectionCache.getStatistics();
        System.out.printf("并发测试结果 - 总访问: %d, 命中: %d, 未命中: %d, 命中率: %.2f%%%n",
            threadCount * accessPerThread, finalStats.getHitCount(), 
            finalStats.getMissCount(), finalStats.getHitRate() * 100);
        
        // 验证命中率合理（应该有很高的命中率，因为重复访问相同的类）
        assertTrue(finalStats.getHitRate() > 0.8, 
            "并发访问应该有高命中率，实际: " + finalStats.getHitRate());
    }
    
    @RepeatedTest(5)
    @DisplayName("重复缓存性能验证")
    void testRepeatedCachePerformance() {
        Class<?> testClass = TestCacheObject.class;
        
        // 预热缓存
        reflectionCache.getFieldMetadata(testClass);
        
        // 测量连续访问性能
        int iterations = 1000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            List<FieldMetadata> result = reflectionCache.getFieldMetadata(testClass);
            assertNotNull(result, "缓存结果应该非空");
        }
        
        long totalDuration = System.nanoTime() - startTime;
        double avgDurationPerAccess = (double) totalDuration / iterations;
        
        System.out.printf("重复访问性能 - 总时间: %,d ns, 平均每次: %.0f ns%n", 
            totalDuration, avgDurationPerAccess);
        
        // 验证缓存访问性能（应该非常快）
        assertTrue(avgDurationPerAccess < 10000, // 10微秒
            "缓存访问应该很快，实际平均: " + avgDurationPerAccess + " ns");
    }
    
    @Test
    @DisplayName("缓存内容正确性验证")
    void testCacheContentCorrectness() {
        Class<?> testClass = ComplexTestObject.class;
        
        // 多次获取相同类的元数据
        List<FieldMetadata> first = reflectionCache.getFieldMetadata(testClass);
        List<FieldMetadata> second = reflectionCache.getFieldMetadata(testClass);
        List<FieldMetadata> third = reflectionCache.getFieldMetadata(testClass);
        
        // 验证引用相同（缓存返回相同对象）
        assertSame(first, second, "缓存应该返回相同的对象引用");
        assertSame(second, third, "缓存应该返回相同的对象引用");
        
        // 验证内容正确性
        assertFalse(first.isEmpty(), "应该有字段元数据");
        
        // 验证包含预期的字段
        Set<String> fieldNames = new HashSet<>();
        for (FieldMetadata metadata : first) {
            fieldNames.add(metadata.getFieldName());
        }
        
        assertTrue(fieldNames.contains("stringField"), "应该包含stringField");
        assertTrue(fieldNames.contains("intField"), "应该包含intField");
        assertTrue(fieldNames.contains("boolField"), "应该包含boolField");
        
        System.out.printf("缓存内容验证 - 发现字段: %s%n", fieldNames);
    }
    
    @Test
    @DisplayName("缓存失效和更新测试")
    void testCacheInvalidationAndUpdate() {
        Class<?> testClass = TestCacheObject.class;
        
        // 首次访问
        List<FieldMetadata> initialResult = reflectionCache.getFieldMetadata(testClass);
        assertNotNull(initialResult);
        
        // 获取初始缓存大小
        int initialCacheSize = reflectionCache.getStatistics().getCacheSize();
        
        // 清空缓存
        reflectionCache.clear();
        
        // 验证缓存被清空
        assertEquals(0, reflectionCache.getStatistics().getCacheSize(), 
            "缓存应该被清空");
        assertEquals(0, reflectionCache.getStatistics().getHitCount(), 
            "命中计数应该重置");
        assertEquals(0, reflectionCache.getStatistics().getMissCount(), 
            "未命中计数应该重置");
        
        // 再次访问（应该重新加载）
        long reloadStart = System.nanoTime();
        List<FieldMetadata> reloadResult = reflectionCache.getFieldMetadata(testClass);
        long reloadDuration = System.nanoTime() - reloadStart;
        
        // 验证重新加载的结果正确
        assertNotNull(reloadResult);
        assertEquals(initialResult.size(), reloadResult.size(), 
            "重新加载的结果大小应该一致");
        
        // 验证这次是缓存未命中
        assertEquals(1, reflectionCache.getStatistics().getMissCount(), 
            "应该有1次缓存未命中");
        
        System.out.printf("缓存重新加载耗时: %,d ns%n", reloadDuration);
    }
    
    @Test
    @DisplayName("大量类缓存压力测试")
    void testLargeScaleCaching() {
        // 创建大量测试类（模拟）
        List<Class<?>> manyClasses = Arrays.asList(
            TestClass1.class, TestClass2.class, TestClass3.class,
            TestClass4.class, TestClass5.class, TestClass6.class,
            TestClass7.class, TestClass8.class, TestClass9.class,
            TestClass10.class, TestCacheObject.class, AnotherTestCacheObject.class
        );
        
        long startTime = System.nanoTime();
        
        // 首次访问所有类
        for (Class<?> clazz : manyClasses) {
            List<FieldMetadata> metadata = reflectionCache.getFieldMetadata(clazz);
            assertNotNull(metadata, "每个类都应该有元数据");
        }
        
        long firstRoundDuration = System.nanoTime() - startTime;
        
        // 再次访问所有类（应该全部命中缓存）
        startTime = System.nanoTime();
        for (Class<?> clazz : manyClasses) {
            List<FieldMetadata> metadata = reflectionCache.getFieldMetadata(clazz);
            assertNotNull(metadata, "缓存结果应该非空");
        }
        long secondRoundDuration = System.nanoTime() - startTime;
        
        // 验证第二轮明显更快
        double speedImprovement = (double) firstRoundDuration / secondRoundDuration;
        
        System.out.printf("大规模缓存测试 - 首轮: %,d ns, 二轮: %,d ns, 提升: %.2fx%n",
            firstRoundDuration, secondRoundDuration, speedImprovement);
        
        assertTrue(speedImprovement > 2.0, 
            "大规模缓存应该显著提升性能，实际: " + speedImprovement + "x");
        
        CacheStatistics stats = reflectionCache.getStatistics();
        System.out.printf("最终统计 - 缓存大小: %d, 命中率: %.2f%%%n", 
            stats.getCacheSize(), stats.getHitRate() * 100);
    }
    
    // 测试数据类
    private static class TestCacheObject {
        private String name;
        private Integer age;
        private Boolean active;
        
        // Getters and Setters (省略)
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
    
    private static class AnotherTestCacheObject {
        private String description;
        private Double value;
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
    }
    
    private static class ComplexTestObject {
        private String stringField;
        private Integer intField;
        private Boolean boolField;
        private Date dateField;
        private List<String> listField;
        
        // Getters and Setters (省略实现)
        public String getStringField() { return stringField; }
        public void setStringField(String stringField) { this.stringField = stringField; }
        public Integer getIntField() { return intField; }
        public void setIntField(Integer intField) { this.intField = intField; }
        public Boolean getBoolField() { return boolField; }
        public void setBoolField(Boolean boolField) { this.boolField = boolField; }
        public Date getDateField() { return dateField; }
        public void setDateField(Date dateField) { this.dateField = dateField; }
        public List<String> getListField() { return listField; }
        public void setListField(List<String> listField) { this.listField = listField; }
    }
    
    // 辅助测试类
    private static class TestClass1 { private String field1; }
    private static class TestClass2 { private String field2; }
    private static class TestClass3 { private String field3; }
    private static class TestClass4 { private String field4; }
    private static class TestClass5 { private String field5; }
    private static class TestClass6 { private String field6; }
    private static class TestClass7 { private String field7; }
    private static class TestClass8 { private String field8; }
    private static class TestClass9 { private String field9; }
    private static class TestClass10 { private String field10; }
}
```

### 2. 缓存统计和监控
```java
package com.syy.taskflowinsight.core.cache;

import lombok.Data;
import lombok.Builder;

/**
 * 缓存统计信息
 */
@Data
@Builder
public class CacheStatistics {
    private final long hitCount;
    private final long missCount; 
    private final int cacheSize;
    private final int maxCapacity;
    private final double hitRate;
    
    /**
     * 计算命中率
     */
    public double getHitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }
    
    /**
     * 获取总访问次数
     */
    public long getTotalAccess() {
        return hitCount + missCount;
    }
    
    /**
     * 获取缓存使用率
     */
    public double getCacheUtilization() {
        return maxCapacity == 0 ? 0.0 : (double) cacheSize / maxCapacity;
    }
    
    @Override
    public String toString() {
        return String.format(
            "CacheStats{size=%d/%d, hits=%d, misses=%d, hitRate=%.2f%%, util=%.1f%%}",
            cacheSize, maxCapacity, hitCount, missCount, 
            getHitRate() * 100, getCacheUtilization() * 100
        );
    }
}
```

### 3. 字段元数据
```java
package com.syy.taskflowinsight.core.reflection;

import lombok.Data;
import lombok.Builder;
import java.lang.reflect.Field;

/**
 * 字段元数据信息
 */
@Data
@Builder
public class FieldMetadata {
    private final String fieldName;
    private final Class<?> fieldType;
    private final boolean isAccessible;
    private final Field field;
    
    /**
     * 获取字段值
     */
    public Object getValue(Object instance) throws IllegalAccessException {
        if (!isAccessible) {
            field.setAccessible(true);
        }
        return field.get(instance);
    }
    
    /**
     * 设置字段值
     */
    public void setValue(Object instance, Object value) throws IllegalAccessException {
        if (!isAccessible) {
            field.setAccessible(true);
        }
        field.set(instance, value);
    }
    
    /**
     * 获取字段类型名称
     */
    public String getFieldTypeName() {
        return fieldType.getSimpleName();
    }
    
    /**
     * 判断是否为基本类型
     */
    public boolean isPrimitiveType() {
        return fieldType.isPrimitive() || 
               fieldType == String.class ||
               Number.class.isAssignableFrom(fieldType) ||
               fieldType == Boolean.class ||
               fieldType == Character.class;
    }
}
```

### 4. 反射缓存实现
```java
package com.syy.taskflowinsight.core.reflection;

import com.syy.taskflowinsight.core.cache.CacheStatistics;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 反射元数据缓存
 */
public class ReflectionCache {
    
    private final ConcurrentHashMap<Class<?>, List<FieldMetadata>> cache;
    private final int maxCapacity;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    
    public ReflectionCache() {
        this(1000); // 默认容量1000
    }
    
    public ReflectionCache(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.cache = new ConcurrentHashMap<>(Math.min(maxCapacity, 256));
    }
    
    /**
     * 获取类的字段元数据
     */
    public List<FieldMetadata> getFieldMetadata(Class<?> clazz) {
        List<FieldMetadata> metadata = cache.get(clazz);
        
        if (metadata != null) {
            hitCount.incrementAndGet();
            return metadata;
        }
        
        // 缓存未命中，计算元数据
        missCount.incrementAndGet();
        return cache.computeIfAbsent(clazz, this::computeFieldMetadata);
    }
    
    /**
     * 计算字段元数据
     */
    private List<FieldMetadata> computeFieldMetadata(Class<?> clazz) {
        // 检查缓存容量
        if (cache.size() >= maxCapacity) {
            // 简单的容量控制：如果超过容量，不加入缓存
            return computeFieldMetadataInternal(clazz);
        }
        
        return computeFieldMetadataInternal(clazz);
    }
    
    /**
     * 内部计算字段元数据
     */
    private List<FieldMetadata> computeFieldMetadataInternal(Class<?> clazz) {
        List<FieldMetadata> metadataList = new ArrayList<>();
        
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            // 跳过静态字段和合成字段
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                field.isSynthetic()) {
                continue;
            }
            
            FieldMetadata metadata = FieldMetadata.builder()
                .fieldName(field.getName())
                .fieldType(field.getType())
                .isAccessible(field.isAccessible())
                .field(field)
                .build();
                
            metadataList.add(metadata);
        }
        
        return Collections.unmodifiableList(metadataList);
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getStatistics() {
        return CacheStatistics.builder()
            .hitCount(hitCount.get())
            .missCount(missCount.get())
            .cacheSize(cache.size())
            .maxCapacity(maxCapacity)
            .build();
    }
    
    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
        hitCount.set(0);
        missCount.set(0);
    }
    
    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * 检查是否包含指定类的缓存
     */
    public boolean contains(Class<?> clazz) {
        return cache.containsKey(clazz);
    }
}
```

## 验证步骤
- [ ] 缓存命中性能测试通过
- [ ] 后续访问明显快于首次访问
- [ ] 缓存命中计数统计正确
- [ ] 缓存容量上限策略有效
- [ ] 并发访问computeIfAbsent正确性
- [ ] 缓存大小受控在合理范围
- [ ] 性能改善在基准测试中可见

## 完成标准
- [ ] 所有缓存验证测试用例通过
- [ ] 性能基准测试中有可见改善
- [ ] 缓存容量上限保障机制有效
- [ ] 并发场景下无数据竞争问题
- [ ] 不引入第三方依赖（M0阶段）
- [ ] 缓存统计信息准确完整