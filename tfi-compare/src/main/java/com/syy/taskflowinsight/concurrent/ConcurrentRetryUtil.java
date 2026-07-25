package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.function.Supplier;

/**
 * 并发重试工具类
 * 
 * 标准化CME重试机制，提供统一的重试策略
 * 支持通过配置动态调整重试参数
 * 
 * @author TaskFlow Insight Team  
 * @version 3.0.0
 * @since 2025-01-24
 */
public final class ConcurrentRetryUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentRetryUtil.class);
    
    // 可配置的重试参数（启动期设置）
    private static volatile int defaultMaxAttempts = ConfigDefaults.CONCURRENT_RETRY_MAX_ATTEMPTS;
    private static volatile long defaultBaseDelayMs = ConfigDefaults.CONCURRENT_RETRY_BASE_DELAY_MS;
    
    // 全局重试统计
    private static final RetryStats globalStats = new RetryStats();
    
    private ConcurrentRetryUtil() {
        // 工具类不允许实例化
    }
    
    /**
     * 设置默认重试参数（仅用于启动期配置）
     * 
     * @param maxAttempts 最大重试次数
     * @param baseDelayMs 基础延迟时间
     */
    public static void setDefaultRetryParams(int maxAttempts, long baseDelayMs) {
        if (maxAttempts > 0) {
            defaultMaxAttempts = maxAttempts;
        }
        if (baseDelayMs > 0) {
            defaultBaseDelayMs = baseDelayMs;
        }
        logger.info("CME retry params updated: maxAttempts={}, baseDelayMs={}", 
            defaultMaxAttempts, defaultBaseDelayMs);
    }
    
    /**
     * 执行带CME重试的操作（使用配置的默认参数）
     * 
     * @param operation 操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws ConcurrentModificationException 重试耗尽后抛出
     */
    public static <T> T executeWithRetry(Supplier<T> operation) {
        return executeWithRetry(operation, defaultMaxAttempts, defaultBaseDelayMs);
    }
    
    /**
     * 执行带CME重试的操作（自定义参数）
     * 
     * @param operation 操作
     * @param maxAttempts 最大重试次数
     * @param baseDelayMs 基础延迟时间（毫秒）
     * @param <T> 返回类型  
     * @return 操作结果
     * @throws ConcurrentModificationException 重试耗尽后抛出
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxAttempts, long baseDelayMs) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        
        if (maxAttempts <= 0) {
            maxAttempts = 1; // 至少执行一次
        }
        
        ConcurrentModificationException lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 成功执行，记录统计
                globalStats.recordRetry(attempt, true);
                return operation.get();
                
            } catch (ConcurrentModificationException e) {
                lastException = e;
                
                if (attempt == maxAttempts) {
                    logger.warn("CME retry exhausted after {} attempts", maxAttempts);
                    globalStats.recordRetry(attempt, false);
                    break;
                }
                
                // 指数退避延迟
                long delayMs = calculateDelay(baseDelayMs, attempt);
                
                if (logger.isDebugEnabled()) {
                    logger.debug("CME retry attempt {}/{}, delay={}ms", attempt, maxAttempts, delayMs);
                }
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during CME retry", ie);
                }
            }
        }
        
        throw lastException;
    }
    
    /**
     * 执行带CME重试的Runnable操作（使用配置的默认参数）
     * 
     * @param operation 操作
     */
    public static void executeWithRetry(Runnable operation) {
        executeWithRetry(operation, defaultMaxAttempts, defaultBaseDelayMs);
    }
    
    /**
     * 执行带CME重试的Runnable操作（自定义参数）
     * 
     * @param operation 操作
     * @param maxAttempts 最大重试次数
     * @param baseDelayMs 基础延迟时间（毫秒）
     */
    public static void executeWithRetry(Runnable operation, int maxAttempts, long baseDelayMs) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxAttempts, baseDelayMs);
    }
    
    /**
     * 计算重试延迟（指数退避 + 抖动）
     * 
     * @param baseDelayMs 基础延迟
     * @param attempt 重试次数（从1开始）
     * @return 延迟时间（毫秒）
     */
    private static long calculateDelay(long baseDelayMs, int attempt) {
        // 指数退避：base * 2^(attempt-1)
        long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
        
        // 添加20%的随机抖动，避免惊群效应
        double jitter = 0.8 + (Math.random() * 0.4); // 0.8-1.2之间
        
        return Math.round(exponentialDelay * jitter);
    }
    
    /**
     * 检查是否为可重试的CME异常
     * 
     * @param throwable 异常
     * @return 是否可重试
     */
    public static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof ConcurrentModificationException) {
            return true;
        }
        
        // 检查原因链中是否有CME
        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof ConcurrentModificationException) {
                return true;
            }
            cause = cause.getCause();
        }
        
        return false;
    }
    
    /**
     * 获取全局重试统计
     */
    public static RetryStats getGlobalStats() {
        return globalStats;
    }
    
    /**
     * 重试统计信息
     */
    public static class RetryStats {
        /** 已记录的调用总数。 */
        private volatile int totalRetries = 0;
        /** 首次失败后重试成功的调用数。 */
        private volatile int successAfterRetry = 0;
        /** 用尽显式重试次数仍失败的调用数。 */
        private volatile int maxAttemptsExhausted = 0;
        
        public synchronized void recordRetry(int attemptCount, boolean success) {
            totalRetries++;
            if (success && attemptCount > 1) {
                successAfterRetry++;
            } else if (!success) {
                maxAttemptsExhausted++;
            }
        }
        
        public double getSuccessRate() {
            return totalRetries == 0 ? 0.0 : 
                (double) (totalRetries - maxAttemptsExhausted) / totalRetries;
        }
        
        @Override
        public String toString() {
            return String.format("RetryStats{total=%d, successAfterRetry=%d, exhausted=%d, rate=%.2f%%}",
                totalRetries, successAfterRetry, maxAttemptsExhausted, getSuccessRate() * 100);
        }
    }
}
