package com.syy.taskflowinsight.store;

import lombok.Builder;
import lombok.Data;

/**
 * 存储统计信息
 */
@Data
@Builder
public class StoreStats {
    private long hitCount;
    private long missCount;
    private long loadSuccessCount;
    private long loadFailureCount;
    private long evictionCount;
    private long totalLoadTime;
    private long estimatedSize;
    private double hitRate;
    private double missRate;
    
    /**
     * 计算命中率
     * @return 命中率(0-1)
     */
    public double calculateHitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }
    
    /**
     * 计算未命中率
     * @return 未命中率(0-1)
     */
    public double calculateMissRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) missCount / total;
    }
    
    /**
     * 获取平均加载时间
     * @return 平均加载时间（纳秒）
     */
    public long getAverageLoadTime() {
        return loadSuccessCount == 0 ? 0 : totalLoadTime / loadSuccessCount;
    }
}