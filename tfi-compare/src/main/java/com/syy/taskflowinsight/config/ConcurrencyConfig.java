package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 并发配置类
 * 
 * 绑定 tfi.change-tracking.concurrency.* 配置
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Data
@Component
@ConfigurationProperties(prefix = "tfi.change-tracking.concurrency")
public class ConcurrencyConfig {
    
    /**
     * CME重试配置
     */
    private CmeRetry cmeRetry = new CmeRetry();
    
    /**
     * ThreadLocal清理配置
     */
    private ThreadLocalCleanup threadLocalCleanup = new ThreadLocalCleanup();
    
    /**
     * FIFO缓存配置
     */
    private FifoCache fifoCache = new FifoCache();
    
    /**
     * 异步指标配置
     */
    private AsyncMetrics asyncMetrics = new AsyncMetrics();
    
    @Data
    public static class CmeRetry {
        /** 最大重试次数（默认1次，符合卡片要求） */
        private int maxAttempts = ConfigDefaults.CONCURRENT_RETRY_MAX_ATTEMPTS;
        
        /** 基础延迟毫秒数 */
        private long baseDelayMs = ConfigDefaults.CONCURRENT_RETRY_BASE_DELAY_MS;
        
        /** 是否启用重试 */
        private boolean enabled = false;
    }
    
    @Data
    public static class ThreadLocalCleanup {
        /** 是否启用自动清理 */
        private boolean enabled = false;
        
        /** 清理间隔（毫秒） */
        private long intervalMs = 60000L;
        
        /** 上下文超时时间（毫秒） */
        private long timeoutMs = ConfigDefaults.MAX_CONTEXT_AGE_MILLIS;
    }
    
    @Data
    public static class FifoCache {
        /** 是否启用FIFO缓存 */
        private boolean enabled = false;
        
        /** 默认缓存大小 */
        private int defaultSize = ConfigDefaults.FIFO_CACHE_DEFAULT_SIZE;
    }
    
    @Data
    public static class AsyncMetrics {
        /** 是否启用异步指标收集 */
        private boolean enabled = false;
        
        /** 缓冲区大小 */
        private int bufferSize = ConfigDefaults.METRICS_BUFFER_SIZE;
        
        /** 刷新间隔（秒） */
        private int flushIntervalSeconds = ConfigDefaults.METRICS_FLUSH_INTERVAL_SECONDS;
    }
}