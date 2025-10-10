package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.*;

/**
 * PathCache功能测试
 * 验证缓存机制和统计功能
 */
@DisplayName("PathCache功能测试")
class PathCacheTest {
    
    private PathCache cache;
    private Object testObject1;
    private Object testObject2;
    
    @BeforeEach
    void setUp() {
        cache = new PathCache(true, 100); // 启用缓存，容量100
        testObject1 = new TestObject("obj1");
        testObject2 = new TestObject("obj2");
    }
    
    @Test
    @DisplayName("基本缓存功能：put和get")
    void shouldCacheAndRetrievePaths() {
        // Given: 缓存路径
        cache.put(testObject1, "path1");
        cache.put(testObject2, "path2");
        
        // When & Then: 应该能够检索到缓存的路径
        assertThat(cache.get(testObject1)).isEqualTo("path1");
        assertThat(cache.get(testObject2)).isEqualTo("path2");
        assertThat(cache.get(new TestObject("notCached"))).isNull();
    }
    
    @Test
    @DisplayName("缓存统计：命中率计算")
    void shouldTrackCacheStatistics() {
        // Given: 缓存一些路径
        cache.put(testObject1, "path1");
        cache.put(testObject2, "path2");
        
        // When: 执行缓存查询（命中和未命中）
        cache.get(testObject1); // 命中
        cache.get(testObject1); // 命中
        cache.get(testObject2); // 命中
        cache.get(new TestObject("notCached")); // 未命中
        cache.get(new TestObject("notCached2")); // 未命中
        
        // Then: 统计信息应该正确
        PathCache.CacheStatistics stats = cache.getStatistics();
        assertThat(stats.getHits()).isEqualTo(3);
        assertThat(stats.getMisses()).isEqualTo(2);
        assertThat(stats.getTotalRequests()).isEqualTo(5);
        assertThat(stats.getHitRate()).isCloseTo(60.0, within(0.1)); // 3/5 = 60%
        assertThat(stats.getCurrentSize()).isEqualTo(2);
        assertThat(stats.getMaxSize()).isEqualTo(100);
    }
    
    @Test
    @DisplayName("缓存容量限制：超限时自动清理")
    void shouldEvictWhenOverCapacity() {
        // Given: 小容量缓存
        PathCache smallCache = new PathCache(true, 3);
        
        // When: 添加超过容量的条目
        for (int i = 0; i < 5; i++) {
            smallCache.put(new TestObject("obj" + i), "path" + i);
        }
        
        // Then: 缓存大小应该不超过限制，并且有清理记录
        PathCache.CacheStatistics stats = smallCache.getStatistics();
        assertThat(stats.getCurrentSize()).isLessThanOrEqualTo(3);
        assertThat(stats.getPuts()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("缓存清空功能")
    void shouldClearCache() {
        // Given: 缓存一些路径
        cache.put(testObject1, "path1");
        cache.put(testObject2, "path2");
        
        // When: 清空缓存
        cache.clear();
        
        // Then: 缓存应该为空
        assertThat(cache.get(testObject1)).isNull();
        assertThat(cache.get(testObject2)).isNull();
        assertThat(cache.getStatistics().getCurrentSize()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("禁用缓存：所有操作无效")
    void shouldIgnoreOperationsWhenDisabled() {
        // Given: 禁用的缓存
        PathCache disabledCache = new PathCache(false, 100);
        
        // When: 尝试缓存操作
        disabledCache.put(testObject1, "path1");
        String result = disabledCache.get(testObject1);
        
        // Then: 应该无效
        assertThat(result).isNull();
        assertThat(disabledCache.isEnabled()).isFalse();
    }
    
    @Test
    @DisplayName("空值处理：null对象和null路径")
    void shouldHandleNullValues() {
        // When & Then: null值应该被正确处理
        cache.put(null, "path"); // 应该被忽略
        cache.put(testObject1, null); // 应该被忽略
        assertThat(cache.get(null)).isNull();
        assertThat(cache.get(testObject1)).isNull();
    }
    
    @Test
    @DisplayName("统计重置功能")
    void shouldResetStatistics() {
        // Given: 一些缓存操作
        cache.put(testObject1, "path1");
        cache.get(testObject1);
        cache.get(new TestObject("notFound"));
        
        // When: 重置统计
        cache.resetStatistics();
        
        // Then: 统计信息应该被重置
        PathCache.CacheStatistics stats = cache.getStatistics();
        assertThat(stats.getHits()).isEqualTo(0);
        assertThat(stats.getMisses()).isEqualTo(0);
        assertThat(stats.getPuts()).isEqualTo(0);
        assertThat(stats.getEvictions()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("内存使用估算")
    void shouldEstimateMemoryUsage() {
        // Given: 缓存一些路径
        cache.put(testObject1, "path1");
        cache.put(testObject2, "path2");
        
        // When & Then: 内存使用应该有合理估算
        PathCache.CacheStatistics stats = cache.getStatistics();
        assertThat(stats.getMemoryUsage()).isGreaterThan(0);
        assertThat(stats.getMemoryUsage()).isEqualTo(stats.getCurrentSize() * 100L); // 每个条目100字节
    }
    
    // 测试对象类
    private static class TestObject {
        private final String name;
        
        public TestObject(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return "TestObject{name='" + name + "'}";
        }
    }
}