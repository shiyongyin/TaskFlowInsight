package com.syy.taskflowinsight.compare.spring;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 在用户显式启用 Compare tracking 时校验可选 Flow starter 是否存在。
 *
 * <p>该配置不链接任何 Flow 类型，使缺依赖错误能够在容器启动期稳定暴露，而不是被
 * {@code ConditionalOnClass} 静默回退。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(before = TfiCompareTrackingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "tfi.compare.tracking", name = "enabled", havingValue = "true")
@ConditionalOnMissingClass("com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate")
public class TfiCompareTrackingPrerequisiteAutoConfiguration {

    /* default */ TfiCompareTrackingPrerequisiteAutoConfiguration() {
        // 由 Spring 负责实例化，包级可见性避免应用代码直接构造。
    }

    /**
     * 把用户显式启用 tracking 但缺少 Flow starter 的错误前置到容器启动期。
     *
     * @return 在 singleton 初始化完成时执行的依赖校验
     */
    @Bean
    public SmartInitializingSingleton tfiCompareTrackingPrerequisiteGuard() {
        return () -> {
            throw new IllegalStateException(
                    "tfi.compare.tracking.enabled=true requires tfi-flow-spring-starter");
        };
    }
}
