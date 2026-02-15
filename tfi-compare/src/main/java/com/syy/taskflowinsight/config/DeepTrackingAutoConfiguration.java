package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.aspect.TfiDeepTrackingAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * deepTracking 的自动装配（compare 模块）.
 *
 * <p>仅当 Spring AOP 可用且启用了注解能力时，注册 {@link TfiDeepTrackingAspect}。
 * 该切面用于实现 {@code @TfiTask(deepTracking = true)} 的深度追踪与差异输出。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = {
    "org.aspectj.lang.ProceedingJoinPoint",
    "org.aspectj.lang.annotation.Aspect",
    "org.springframework.aop.framework.AopInfrastructureBean"
})
@ConditionalOnProperty(name = "tfi.annotation.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(prefix = "tfi.change-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeepTrackingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TfiDeepTrackingAspect tfiDeepTrackingAspect() {
        return new TfiDeepTrackingAspect();
    }
}

