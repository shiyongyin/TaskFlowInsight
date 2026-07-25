package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.aspect.TfiAnnotationAspect;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.annotation.Bean;

/**
 * 将可选 Flow hook 连接到当前 context 的 Compare Runtime。
 *
 * <p>该配置只在显式开启 tracking 时生效，不创建第二个 Aspect，也不复用 JVM default provider。
 * 因此普通 Spring 比较与 TfiTask tracking 共享同一个 Policy 和 Engine 边界。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(after = TfiCompareAutoConfiguration.class)
@ConditionalOnClass(TfiTaskDeepTrackingDelegate.class)
@ConditionalOnProperty(prefix = "tfi.compare.tracking", name = "enabled", havingValue = "true")
@ConditionalOnBean(value = CompareRuntime.class, search = SearchStrategy.CURRENT)
public class TfiCompareTrackingAutoConfiguration {

    /** 显式 tracking 只能由当前上下文的一项 Flow advice 消费。 */
    private static final int ASPECT_COUNT = 1;

    /**
     * 验证显式 tracking 确实由当前上下文的唯一 Flow advice 消费。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return singleton 创建完成后执行的本层 advice 校验
     */
    @Bean
    public SmartInitializingSingleton tfiCompareTrackingAspectGuard(
            final ListableBeanFactory beanFactory) {
        return () -> {
            if (beanFactory.getBeanNamesForType(
                    TfiAnnotationAspect.class, true, false).length
                    != ASPECT_COUNT) {
                throw new IllegalStateException(
                        "tfi.compare.tracking.enabled=true requires one local active "
                                + "TfiAnnotationAspect");
            }
        };
    }

    /**
     * 为 Flow delegate 提供当前 context Engine 的 batch scope。
     *
     * <p>该 bean 不对自定义 {@link TrackingProvider} back-off；Spring tracking 必须与最终
     * {@link CompareRuntime} 共用对象图，额外 provider 会以 bean 冲突阻断启动。</p>
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 不持有业务 action 或全局 history 的 provider
     */
    @Bean
    public TrackingProvider tfiCompareTrackingProvider(final ListableBeanFactory beanFactory) {
        final CompareRuntime runtime = TfiCompareAutoConfiguration.requireLocalBean(
                beanFactory, CompareRuntime.class);
        return runtime.engine()::beginTracking;
    }

    /**
     * 把 Flow 的唯一调用权交给当前 context 的 TrackingExecutor。
     *
     * @param beanFactory 当前上下文 bean 目录及启动期静态 TfiTask 声明目录
     * @return 不拥有 AOP 或 session 状态的 delegate
     */
    @Bean
    @ConditionalOnMissingBean(
            value = TfiTaskDeepTrackingDelegate.class,
            search = SearchStrategy.CURRENT)
    public TfiTaskDeepTrackingDelegate tfiTaskDeepTrackingDelegate(
            final ListableBeanFactory beanFactory) {
        final TrackingProvider provider = TfiCompareAutoConfiguration.requireLocalBean(
                beanFactory, TrackingProvider.class);
        final CompareRuntime runtime = TfiCompareAutoConfiguration.requireLocalBean(
                beanFactory, CompareRuntime.class);
        return new DefaultTfiTaskDeepTrackingDelegate(provider, beanFactory, runtime.policy());
    }
}
