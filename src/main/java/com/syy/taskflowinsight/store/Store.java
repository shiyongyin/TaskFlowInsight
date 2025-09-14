package com.syy.taskflowinsight.store;

import java.util.Optional;

/**
 * 通用存储接口
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface Store<K, V> {
    
    /**
     * 存储键值对
     * @param key 键
     * @param value 值
     */
    void put(K key, V value);
    
    /**
     * 获取值
     * @param key 键
     * @return 值（可选）
     */
    Optional<V> get(K key);
    
    /**
     * 移除键值对
     * @param key 键
     */
    void remove(K key);
    
    /**
     * 清空存储
     */
    void clear();
    
    /**
     * 获取存储大小
     * @return 存储项数量
     */
    long size();
    
    /**
     * 检查键是否存在
     * @param key 键
     * @return 是否存在
     */
    default boolean containsKey(K key) {
        return get(key).isPresent();
    }
    
    /**
     * 获取统计信息
     * @return 统计信息
     */
    default StoreStats getStats() {
        return StoreStats.builder()
            .estimatedSize(size())
            .build();
    }
}