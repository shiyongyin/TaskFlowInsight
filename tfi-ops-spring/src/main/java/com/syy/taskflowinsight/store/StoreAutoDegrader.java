package com.syy.taskflowinsight.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存储自动降级器
 */
@Slf4j
@Component
public class StoreAutoDegrader {
    
    @Value("${tfi.store.degrade.hit-rate-threshold:0.2}")
    private double minHitRate;
    
    @Value("${tfi.store.degrade.max-evictions:10000}")
    private long maxEvictions;
    
    @Value("${tfi.store.degrade.recovery-threshold:0.5}")
    private double recoveryHitRate;
    
    @Value("${tfi.store.degrade.recovery-count:5}")
    private int recoveryCount;
    
    private final AtomicBoolean degraded = new AtomicBoolean(false);
    private final AtomicLong consecutiveGoodChecks = new AtomicLong(0);
    private final AtomicLong degradationCount = new AtomicLong(0);
    
    /**
     * 检查是否降级
     * @return 是否降级
     */
    public boolean isDegraded() {
        return degraded.get();
    }
    
    /**
     * 获取降级次数
     * @return 降级次数
     */
    public long getDegradationCount() {
        return degradationCount.get();
    }
    
    /**
     * 评估存储状态
     * @param stats 存储统计
     */
    public void evaluate(StoreStats stats) {
        boolean shouldDegrade = shouldDegrade(stats);
        
        if (shouldDegrade && !degraded.get()) {
            // 进入降级
            degraded.set(true);
            degradationCount.incrementAndGet();
            consecutiveGoodChecks.set(0);
            log.warn("Cache degraded due to poor performance: hitRate={}, evictions={}", 
                stats.getHitRate(), stats.getEvictionCount());
        } else if (!shouldDegrade && degraded.get()) {
            // 检查是否可以恢复
            if (canRecover(stats)) {
                long goodChecks = consecutiveGoodChecks.incrementAndGet();
                if (goodChecks >= recoveryCount) {
                    // 恢复正常
                    degraded.set(false);
                    consecutiveGoodChecks.set(0);
                    log.info("Cache recovered after {} consecutive good checks", goodChecks);
                }
            } else {
                // 重置恢复计数
                consecutiveGoodChecks.set(0);
            }
        }
    }
    
    /**
     * 判断是否应该降级
     * @param stats 统计信息
     * @return 是否降级
     */
    private boolean shouldDegrade(StoreStats stats) {
        // 命中率过低或驱逐次数过多
        return stats.getHitRate() < minHitRate || stats.getEvictionCount() > maxEvictions;
    }
    
    /**
     * 判断是否可以恢复
     * @param stats 统计信息
     * @return 是否可以恢复
     */
    private boolean canRecover(StoreStats stats) {
        // 命中率恢复且驱逐次数正常
        return stats.getHitRate() >= recoveryHitRate && stats.getEvictionCount() <= maxEvictions;
    }
    
    /**
     * 强制降级
     */
    public void forceDegrade() {
        degraded.set(true);
        degradationCount.incrementAndGet();
        consecutiveGoodChecks.set(0);
        log.warn("Cache forcibly degraded");
    }
    
    /**
     * 强制恢复
     */
    public void forceRecover() {
        degraded.set(false);
        consecutiveGoodChecks.set(0);
        log.info("Cache forcibly recovered");
    }
    
    /**
     * 重置状态
     */
    public void reset() {
        degraded.set(false);
        consecutiveGoodChecks.set(0);
        degradationCount.set(0);
        log.info("Degrader state reset");
    }
}