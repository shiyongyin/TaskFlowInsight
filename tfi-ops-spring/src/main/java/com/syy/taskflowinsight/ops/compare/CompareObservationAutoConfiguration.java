package com.syy.taskflowinsight.ops.compare;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Iterator;
import java.util.Map;

/**
 * 将可选 Compare 对象图连接到宿主 Ops 基础设施。
 *
 * <p>排序与 Compare 条件使用类名字符串，是为了让 Ops 在未携带 optional Compare artifact 时仍可加载；
 * 这不是运行期类查找，也不会引入 compare starter 依赖。实际装配只发生在嵌套配置的 typed bean 边界。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(afterName = {
        "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"
})
@ConditionalOnClass(name = "com.syy.taskflowinsight.api.CompareOperations")
public class CompareObservationAutoConfiguration {

    /** 只有宿主 Registry 与基础 Engine 同时存在时才开放 observed 路径。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(
            value = {CompareEngine.class, MeterRegistry.class},
            search = SearchStrategy.CURRENT)
    static class ObservationConfiguration {

        /**
         * 创建唯一 primary 装饰器；Engine bean 继续保留，供 tracking batch 走非观测内部路径。
         *
         * @param beanFactory 当前上下文 bean 目录
         * @return 每次只委托同一个 Engine 一次的 Operations
         */
        @Bean(name = "observedCompareOperations")
        @Primary
        @ConditionalOnMissingBean(
                name = "observedCompareOperations",
                search = SearchStrategy.CURRENT)
        public ObservedCompareOperations observedCompareOperations(
                final ListableBeanFactory beanFactory) {
            final CompareEngine engine = requireLocalBean(beanFactory, CompareEngine.class);
            final MeterRegistry registry = requireLocalBean(beanFactory, MeterRegistry.class);
            return new ObservedCompareOperations(engine, new CompareMetrics(registry));
        }

        /**
         * 验证 Ops 只发布一个直接委托本层 Engine 的 typed decorator。
         *
         * @param beanFactory 当前上下文 bean 目录
         * @return singleton 创建完成后执行的 owner-local 组合校验
         */
        @Bean
        public SmartInitializingSingleton tfiCompareObservationCompositionValidator(
                final ListableBeanFactory beanFactory) {
            return () -> validateObservationComposition(beanFactory);
        }

        private static void validateObservationComposition(
                final ListableBeanFactory beanFactory) {
            final Map<String, CompareOperationsDecorator> decorators =
                    beanFactory.getBeansOfType(CompareOperationsDecorator.class);
            final Iterator<CompareOperationsDecorator> iterator = decorators.values().iterator();
            if (!iterator.hasNext()) {
                reject("one CompareOperationsDecorator is required");
            }
            final CompareOperationsDecorator decorator = iterator.next();
            if (iterator.hasNext()) {
                reject("only one CompareOperationsDecorator is supported");
            }
            final CompareEngine engine = requireLocalBean(beanFactory, CompareEngine.class);
            final CompareOperations selected = requireLocalBean(
                    beanFactory, CompareOperations.class);
            if (selected != decorator || decorator.delegate() != engine) {
                reject("selected decorator must directly delegate the local Engine");
            }
        }

        private static void reject(final String reason) {
            throw new IllegalStateException(
                    "Invalid TFI Compare observation composition: " + reason);
        }
    }

    /** Health 与 Micrometer 正交；没有 Registry 时仍可报告当前 Compare 对象图。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(
            value = {CompareRuntime.class, CompareOperations.class},
            search = SearchStrategy.CURRENT)
    static class HealthConfiguration {

        /**
         * 创建不保存最近结果的 Compare 健康投影。
         *
         * @param beanFactory 当前上下文 bean 目录
         * @return 当前对象图健康指示器
         */
        @Bean(name = "compareHealthIndicator")
        @ConditionalOnMissingBean(
                name = "compareHealthIndicator",
                search = SearchStrategy.CURRENT)
        public CompareHealthIndicator compareHealthIndicator(
                final ListableBeanFactory beanFactory) {
            final CompareRuntime runtime = requireLocalBean(beanFactory, CompareRuntime.class);
            final CompareOperations operations = requireLocalBean(
                    beanFactory, CompareOperations.class);
            return new CompareHealthIndicator(runtime, operations);
        }
    }

    private static <T> T requireLocalBean(
            final ListableBeanFactory beanFactory,
            final Class<T> type) {
        if (beanFactory.getBeanNamesForType(type, true, false).length == 0) {
            throw new IllegalStateException("Missing local bean: " + type.getName());
        }
        return beanFactory.getBean(type);
    }
}
