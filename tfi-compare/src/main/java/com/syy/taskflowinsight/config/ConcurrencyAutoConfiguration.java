package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 并发配置自动装配
 * 
 * 将配置属性注入到工具类中
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ConcurrencyConfig.class)
public class ConcurrencyAutoConfiguration {
    
    private final ConcurrencyConfig concurrencyConfig;
    
    @Autowired(required = false)
    private TfiMetrics tfiMetrics;
    
    public ConcurrencyAutoConfiguration(ConcurrencyConfig concurrencyConfig) {
        this.concurrencyConfig = concurrencyConfig;
    }
    
    @PostConstruct
    public void initializeConcurrencySettings() {
        // 配置CME重试参数
        ConcurrencyConfig.CmeRetry cmeRetry = concurrencyConfig.getCmeRetry();
        ConcurrentRetryUtil.setDefaultRetryParams(
            cmeRetry.getMaxAttempts(),
            cmeRetry.getBaseDelayMs()
        );
        
        // 集成TfiMetrics（如果可用）
        if (tfiMetrics != null) {
            ConcurrentRetryUtil.setTfiMetrics(tfiMetrics);
            log.info("TfiMetrics integrated with ConcurrentRetryUtil");
        }
        
        log.info("Concurrency configuration initialized: " +
            "cmeRetry.maxAttempts={}, cmeRetry.baseDelayMs={}, " +
            "threadLocalCleanup.enabled={}, fifoCache.enabled={}, asyncMetrics.enabled={}",
            cmeRetry.getMaxAttempts(),
            cmeRetry.getBaseDelayMs(),
            concurrencyConfig.getThreadLocalCleanup().isEnabled(),
            concurrencyConfig.getFifoCache().isEnabled(),
            concurrencyConfig.getAsyncMetrics().isEnabled()
        );
    }
    
    /**
     * 获取并发配置
     */
    public ConcurrencyConfig getConcurrencyConfig() {
        return concurrencyConfig;
    }
}