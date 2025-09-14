package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CaffeineStore测试
 */
class CaffeineStoreTest {
    
    private CaffeineStore<String, String> store;
    
    @BeforeEach
    void setUp() {
        store = new CaffeineStore<>();
    }
    
    @Test
    void testBasicOperations() {
        // 测试存储
        store.put("key1", "value1");
        assertThat(store.get("key1")).contains("value1");
        assertThat(store.size()).isEqualTo(1);
        
        // 测试更新
        store.put("key1", "value2");
        assertThat(store.get("key1")).contains("value2");
        assertThat(store.size()).isEqualTo(1);
        
        // 测试移除
        store.remove("key1");
        assertThat(store.get("key1")).isEmpty();
        assertThat(store.size()).isEqualTo(0);
    }
    
    @Test
    void testNullHandling() {
        // 测试空键
        assertThatThrownBy(() -> store.put(null, "value"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key cannot be null");
        
        // 测试空值
        assertThatThrownBy(() -> store.put("key", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Value cannot be null");
        
        // 测试获取空键
        assertThat(store.get(null)).isEmpty();
    }
    
    @Test
    void testBatchOperations() {
        // 批量存储
        Map<String, String> entries = new HashMap<>();
        entries.put("key1", "value1");
        entries.put("key2", "value2");
        entries.put("key3", "value3");
        store.putAll(entries);
        
        assertThat(store.size()).isEqualTo(3);
        
        // 批量获取
        Map<String, String> retrieved = store.getAll(entries.keySet());
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get("key1")).isEqualTo("value1");
        assertThat(retrieved.get("key2")).isEqualTo("value2");
        assertThat(retrieved.get("key3")).isEqualTo("value3");
        
        // 批量移除
        store.removeAll(entries.keySet());
        assertThat(store.size()).isEqualTo(0);
    }
    
    @Test
    void testClear() {
        // 添加数据
        store.put("key1", "value1");
        store.put("key2", "value2");
        assertThat(store.size()).isEqualTo(2);
        
        // 清空
        store.clear();
        assertThat(store.size()).isEqualTo(0);
        assertThat(store.get("key1")).isEmpty();
        assertThat(store.get("key2")).isEmpty();
    }
    
    @Test
    void testContainsKey() {
        store.put("key1", "value1");
        
        assertThat(store.containsKey("key1")).isTrue();
        assertThat(store.containsKey("key2")).isFalse();
        assertThat(store.containsKey(null)).isFalse();
    }
    
    @Test
    void testStats() {
        // 启用统计的存储
        StoreConfig config = StoreConfig.builder()
            .maxSize(100)
            .recordStats(true)
            .build();
        CaffeineStore<String, String> statsStore = new CaffeineStore<>(config);
        
        // 执行操作
        statsStore.put("key1", "value1");
        statsStore.get("key1"); // 命中
        statsStore.get("key2"); // 未命中
        
        // 检查统计
        StoreStats stats = statsStore.getStats();
        assertThat(stats.getHitCount()).isEqualTo(1);
        assertThat(stats.getMissCount()).isEqualTo(1);
        assertThat(stats.getEstimatedSize()).isEqualTo(1);
        assertThat(stats.getHitRate()).isEqualTo(0.5);
    }
    
    @Test
    void testExpiration() throws InterruptedException {
        // 配置短TTL
        StoreConfig config = StoreConfig.builder()
            .maxSize(100)
            .defaultTtl(Duration.ofMillis(100))
            .build();
        CaffeineStore<String, String> ttlStore = new CaffeineStore<>(config);
        
        // 存储值
        ttlStore.put("key1", "value1");
        assertThat(ttlStore.get("key1")).contains("value1");
        
        // 等待过期
        TimeUnit.MILLISECONDS.sleep(150);
        ttlStore.cleanUp();
        
        // 检查过期
        assertThat(ttlStore.get("key1")).isEmpty();
    }
    
    @Test
    void testMaxSize() {
        // 配置小容量
        StoreConfig config = StoreConfig.builder()
            .maxSize(2)
            .build();
        CaffeineStore<String, String> smallStore = new CaffeineStore<>(config);
        
        // 添加超过容量的数据
        smallStore.put("key1", "value1");
        smallStore.put("key2", "value2");
        smallStore.put("key3", "value3");
        
        // 触发清理
        smallStore.cleanUp();
        
        // 检查容量限制
        assertThat(smallStore.size()).isLessThanOrEqualTo(2);
    }
}