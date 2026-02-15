package com.syy.taskflowinsight.tracking.compare;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 比较缓存配置属性（M3新增）
 * <p>
 * 绑定YAML配置: tfi.diff.cache.*
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "tfi.diff.cache")
public class CompareCacheProperties {

    /**
     * 策略缓存配置
     */
    private CacheConfig strategy = new CacheConfig(true, 10000, 300000);

    /**
     * 反射元数据缓存配置
     */
    private CacheConfig reflection = new CacheConfig(true, 10000, 300000);

    /**
     * 缓存配置项
     */
    @Data
    public static class CacheConfig {
        /**
         * 是否启用缓存
         */
        private boolean enabled;

        /**
         * 最大缓存条目数
         */
        private long maxSize;

        /**
         * 过期时间（毫秒）
         */
        private long ttlMs;

        public CacheConfig() {
            this(true, 10000, 300000);
        }

        public CacheConfig(boolean enabled, long maxSize, long ttlMs) {
            this.enabled = enabled;
            this.maxSize = maxSize;
            this.ttlMs = ttlMs;
        }
    }
}
