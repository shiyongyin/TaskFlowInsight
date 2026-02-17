package com.syy.taskflowinsight.store;

import lombok.Builder;
import lombok.Data;

/**
 * 存储统计信息 DTO。
 * <p>
 * 使用 Lombok {@code @Builder} 构建，包含命中率、驱逐数、加载时间等指标。
 * 字段说明：
 * <ul>
 *   <li>{@code hitCount} - 命中次数</li>
 *   <li>{@code missCount} - 未命中次数</li>
 *   <li>{@code loadSuccessCount} - 加载成功次数</li>
 *   <li>{@code loadFailureCount} - 加载失败次数</li>
 *   <li>{@code evictionCount} - 驱逐次数</li>
 *   <li>{@code totalLoadTime} - 总加载时间（纳秒）</li>
 *   <li>{@code estimatedSize} - 预估存储项数量</li>
 *   <li>{@code hitRate} / {@code missRate} - 命中率/未命中率（0-1）</li>
 * </ul>
 *
 * @since 3.0.0
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