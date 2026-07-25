package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 在应用显式提供标准 Boot AOP feature dependency 且开启 property 时创建唯一 Advisor。
 *
 * <p>缺 AspectJ marker 的 fail-fast 由不静态引用 optional 类型的基础 composition validator 负责。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(after = TfiKernelCompareAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.aspectj.weaver.Advice",
        "org.springframework.aop.Advisor",
        "org.aopalliance.intercept.MethodInterceptor"
})
@ConditionalOnProperty(
        prefix = "tfi.kernel-compare",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "tfi.kernel-compare.aop",
        name = "enabled",
        havingValue = "true")
public class TfiKernelCompareAopAutoConfiguration {

    /** 固定包裹 Spring 默认事务 Advisor 的顺序，不暴露可漂移配置。 */
    static final int ADVISOR_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /** 创建同时承担代理创建期静态校验和调用期单一入口的不可替换 Advisor。 */
    @Bean("tfiKernelCompareAdvisor")
    public Advisor tfiKernelCompareAdvisor(
            KernelRuntime kernelRuntime,
            CompareRuntime compareRuntime,
            TrackingExecutor trackingExecutor,
            KernelCompareRecorder recorder) {
        TfiTrackedMethodPlanResolver resolver = new TfiTrackedMethodPlanResolver();
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
                new TfiTrackedPointcut(resolver),
                new TfiTrackedMethodInterceptor(
                        resolver,
                        kernelRuntime,
                        compareRuntime,
                        trackingExecutor,
                        recorder));
        advisor.setOrder(ADVISOR_ORDER);
        return advisor;
    }
}
