package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 在当前 ApplicationContext 中冻结 {@code Policy -> Runtime -> Engine} 唯一执行图。
 *
 * <p>应用只能整体替换 Policy、Runtime 或安全 MaskingPolicy；派生对象始终由最终 Runtime 图创建。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(
        after = TfiKernelRuntimeAutoConfiguration.class,
        before = TfiKernelCompareAutoConfiguration.class)
@ConditionalOnClass(name = {
        "com.syy.tfi.kernel.KernelRuntime",
        "com.syy.taskflowinsight.tracking.compare.CompareRuntime",
        "com.syy.tfi.kernel.compare.KernelCompareRecorder"
})
@EnableConfigurationProperties(TfiCompareCoreProperties.class)
public class TfiCompareCoreAutoConfiguration {

    /** 从完整 properties 构造默认 Policy；custom Runtime 存在时不发布平行 Policy。 */
    @Bean("tfiComparePolicy")
    @ConditionalOnMissingBean(
            value = {ComparePolicy.class, CompareRuntime.class},
            search = SearchStrategy.CURRENT)
    public ComparePolicy tfiComparePolicy(TfiCompareCoreProperties properties) {
        try {
            return properties.toPolicyBuilder().build();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "KCS_E_1003: tfi.compare cannot construct ComparePolicy",
                    exception);
        }
    }

    /** 使用当前 context 唯一 Policy 冻结 Runtime；custom Runtime 存在时完整 back-off。 */
    @Bean("tfiCompareRuntime")
    @ConditionalOnMissingBean(value = CompareRuntime.class, search = SearchStrategy.CURRENT)
    public CompareRuntime tfiCompareRuntime(ConfigurableListableBeanFactory beanFactory) {
        ComparePolicy policy = requireExactlyOne(beanFactory, ComparePolicy.class);
        return CompareRuntime.builder().policy(policy).build();
    }

    /** 从最终 Runtime 导出同一 Engine 实例，禁止平行执行入口。 */
    @Bean("tfiCompareEngine")
    public CompareEngine tfiCompareEngine(ConfigurableListableBeanFactory beanFactory) {
        return requireExactlyOne(beanFactory, CompareRuntime.class).engine();
    }

    /** 将 tracking action 绑定到最终 Engine 的批次入口。 */
    @Bean("tfiTrackingExecutor")
    public TrackingExecutor tfiTrackingExecutor(ConfigurableListableBeanFactory beanFactory) {
        CompareEngine engine = requireExactlyOne(beanFactory, CompareEngine.class);
        return new TrackingExecutor(engine::beginTracking);
    }

    /** 创建无状态 canonical projection 工厂；该派生 Bean 不允许应用替换。 */
    @Bean("tfiCompareProjectionFactory")
    public CompareProjectionFactory tfiCompareProjectionFactory() {
        return new CompareProjectionFactory();
    }

    /** 创建不可弱化的安全 MaskingPolicy；应用可整体提供另一份安全 owner。 */
    @Bean("tfiMaskingPolicy")
    @ConditionalOnMissingBean(value = MaskingPolicy.class, search = SearchStrategy.CURRENT)
    public MaskingPolicy tfiMaskingPolicy(TfiCompareCoreProperties properties) {
        return properties.toMaskingPolicy();
    }

    private static <T> T requireExactlyOne(
            ConfigurableListableBeanFactory beanFactory,
            Class<T> type) {
        Map<String, T> beans = beanFactory.getBeansOfType(type, true, false);
        if (beans.size() != 1) {
            throw new IllegalStateException(
                    "KCS_E_1002: exactly one local " + type.getSimpleName() + " is required");
        }
        return beans.values().iterator().next();
    }
}
