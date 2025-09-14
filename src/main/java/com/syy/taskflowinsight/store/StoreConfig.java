package com.syy.taskflowinsight.store;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.github.benmanes.caffeine.cache.CacheLoader;

import java.time.Duration;

/**
 * 存储配置
 */
@Data
@Builder
@ConfigurationProperties(prefix = "tfi.store.caffeine")
public class StoreConfig {
    
    @Builder.Default
    private long maxSize = 10000;
    
    @Builder.Default
    private Duration defaultTtl = Duration.ofMinutes(60);
    
    @Builder.Default
    private Duration idleTimeout = Duration.ofMinutes(30);
    
    private Duration refreshAfterWrite;
    
    @Builder.Default
    private boolean useSoftValues = false;
    
    @Builder.Default
    private boolean logEvictions = false;
    
    @Builder.Default
    private boolean recordStats = true;
    
    private CacheLoader<?, ?> loader;
    
    /**
     * 创建默认配置
     * @return 默认配置
     */
    public static StoreConfig defaultConfig() {
        return StoreConfig.builder().build();
    }
    
    /**
     * 创建小容量配置（适用于L1缓存）
     * @return L1配置
     */
    public static StoreConfig l1Config() {
        return StoreConfig.builder()
            .maxSize(1000)
            .defaultTtl(Duration.ofMinutes(10))
            .idleTimeout(Duration.ofMinutes(5))
            .build();
    }
    
    /**
     * 创建大容量配置（适用于L2缓存）
     * @return L2配置
     */
    public static StoreConfig l2Config() {
        return StoreConfig.builder()
            .maxSize(10000)
            .defaultTtl(Duration.ofMinutes(60))
            .idleTimeout(Duration.ofMinutes(30))
            .build();
    }
}