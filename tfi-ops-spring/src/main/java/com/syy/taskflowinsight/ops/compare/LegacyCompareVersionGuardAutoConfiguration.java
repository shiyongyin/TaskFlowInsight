package com.syy.taskflowinsight.ops.compare;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;

/**
 * 拒绝把 Ops 4.x 与仍暴露 legacy Engine、但没有 4.x typed Operations 的 Compare 3.x 混装。
 *
 * <p>条件全部使用类名，保证 Ops 在完全没有 Compare 时仍可独立启动；该 guard 只关闭已经进入
 * 不兼容半装配状态的组合，不把 optional Compare 变成强依赖。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(before = CompareObservationAutoConfiguration.class)
@ConditionalOnClass(name = "com.syy.taskflowinsight.tracking.compare.CompareEngine")
@ConditionalOnMissingClass("com.syy.taskflowinsight.api.CompareOperations")
public class LegacyCompareVersionGuardAutoConfiguration {

    /** 由 Boot 创建，应用代码不应绕过自动配置条件直接实例化。 */
    LegacyCompareVersionGuardAutoConfiguration() {
    }

    /**
     * 在任何业务流量进入前拒绝不兼容的 optional 组合。
     *
     * @return singleton 初始化完成时执行的版本边界校验
     */
    @Bean
    public SmartInitializingSingleton tfiLegacyCompareVersionGuard() {
        return () -> {
            throw new IllegalStateException(
                    "tfi-ops-spring 4.x is incompatible with tfi-compare 3.x");
        };
    }
}
