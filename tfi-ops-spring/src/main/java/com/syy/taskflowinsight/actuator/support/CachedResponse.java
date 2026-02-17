package com.syy.taskflowinsight.actuator.support;

import java.util.Map;

/**
 * 端点响应缓存条目。
 *
 * <p>存储一次端点响应的完整内容及其生成时的时间戳，
 * 用于在 {@code cacheTtlMs} 窗口内避免重复计算。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public class CachedResponse {

    /** 缓存的响应内容 */
    private final Map<String, Object> response;

    /** 缓存生成时间（epoch millis） */
    private final long timestamp;

    /**
     * 创建缓存条目。
     *
     * @param response 端点响应数据
     * @param timestamp 生成时间（epoch millis）
     */
    public CachedResponse(Map<String, Object> response, long timestamp) {
        this.response = response;
        this.timestamp = timestamp;
    }

    /**
     * 获取缓存的响应内容。
     *
     * @return 响应 Map
     */
    public Map<String, Object> getResponse() {
        return response;
    }

    /**
     * 获取缓存生成时间。
     *
     * @return epoch millis
     */
    public long getTimestamp() {
        return timestamp;
    }
}
