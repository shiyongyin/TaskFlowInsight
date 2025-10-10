package com.syy.taskflowinsight.tracking.compare.list;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 列表比较自动路由配置
 *
 * 配置项：
 * tfi.compare.auto-route.entity.enabled = true|false
 * tfi.compare.auto-route.lcs.* (M3新增)
 */
@Data
@Component
@ConfigurationProperties(prefix = "tfi.compare.auto-route")
public class CompareRoutingProperties {
    /**
     * Entity列表自动路由配置
     */
    private EntityConfig entity = new EntityConfig();

    /**
     * LCS列表自动路由配置（M3新增）
     */
    private LcsConfig lcs = new LcsConfig();

    @Data
    public static class EntityConfig {
        /**
         * 是否启用实体列表自动路由到 ENTITY 策略
         */
        private boolean enabled = true;
    }

    @Data
    public static class LcsConfig {
        /**
         * 是否启用LCS策略
         */
        private boolean enabled = true;

        /**
         * 当detectMoves=true时，是否优先使用LCS策略
         */
        private boolean preferLcsWhenDetectMoves = true;
    }
}

