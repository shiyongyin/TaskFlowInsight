package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.IdGenerator;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

/**
 * 在当前 ApplicationContext 内选择唯一 Kernel owner，并冻结它的 local SPI 输入。
 *
 * <p>只有 Config 与 Runtime 可以整体 back-off；SPI 不会从父 context 借用，请求路径也不保存容器引用。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration(before = TfiCompareCoreAutoConfiguration.class)
@ConditionalOnClass(name = {
        "com.syy.tfi.kernel.KernelRuntime",
        "com.syy.taskflowinsight.tracking.compare.CompareRuntime",
        "com.syy.tfi.kernel.compare.KernelCompareRecorder"
})
@EnableConfigurationProperties(TfiKernelProperties.class)
public class TfiKernelRuntimeAutoConfiguration {

    /** 不允许应用替换的 Runtime 退役 Bean 保留名。 */
    static final String RUNTIME_RETIREMENT_BEAN_NAME = "tfiKernelRuntimeRetirement";

    /**
     * 用 properties 与当前 context 的 SPI 构造默认 Config。
     *
     * @param properties 已补齐并验证的 Kernel 配置
     * @param beanFactory 当前 context 的本地 bean 目录
     * @return 不与其他 context 共享的不可变配置
     */
    @Bean("tfiKernelConfig")
    @ConditionalOnMissingBean(
            value = {KernelConfig.class, KernelRuntime.class},
            search = SearchStrategy.CURRENT)
    public KernelConfig tfiKernelConfig(
            TfiKernelProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        KernelClock clock = localSingleOrDefault(
                beanFactory, KernelClock.class, KernelClock.system());
        Sampler sampler = localSingleOrDefault(
                beanFactory, Sampler.class, Sampler.always());
        IdGenerator idGenerator = localSingleOrDefault(
                beanFactory, IdGenerator.class, IdGenerator.ulid(clock));
        List<FlowSink> sinks = orderedLocalSinks(beanFactory);
        return properties.toConfig(sinks, sampler, idGenerator, clock);
    }

    /**
     * 从当前 context 唯一 Config 创建 Runtime；custom Runtime 存在时完整 back-off。
     *
     * @param beanFactory 当前 context 的本地 bean 目录
     * @return 当前 context 独占的 Kernel owner
     */
    @Bean("tfiKernelRuntime")
    @ConditionalOnMissingBean(value = KernelRuntime.class, search = SearchStrategy.CURRENT)
    public KernelRuntime tfiKernelRuntime(ConfigurableListableBeanFactory beanFactory) {
        return KernelRuntime.create(requireExactlyOne(beanFactory, KernelConfig.class));
    }

    /** 为三种合法 Kernel owner 模式创建同一个不可替换的 Spring 销毁终点。 */
    @Bean(RUNTIME_RETIREMENT_BEAN_NAME)
    public KernelRuntimeRetirement tfiKernelRuntimeRetirement(
            ConfigurableListableBeanFactory beanFactory) {
        return new KernelRuntimeRetirement(requireExactlyOne(beanFactory, KernelRuntime.class));
    }

    private static List<FlowSink> orderedLocalSinks(
            ConfigurableListableBeanFactory beanFactory) {
        return localBeans(beanFactory, FlowSink.class).entrySet().stream()
                .map(entry -> new OrderedSink(
                        entry.getKey(),
                        entry.getValue(),
                        sinkOrder(beanFactory, entry.getKey(), entry.getValue())))
                .sorted(Comparator.comparingInt(OrderedSink::order).thenComparing(OrderedSink::name))
                .map(OrderedSink::sink)
                .toList();
    }

    private static int sinkOrder(
            ConfigurableListableBeanFactory beanFactory,
            String beanName,
            FlowSink sink) {
        if (sink instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        Class<?> sinkType = beanFactory.getType(beanName);
        return sinkType == null
                ? Ordered.LOWEST_PRECEDENCE
                : OrderUtils.getOrder(sinkType, Ordered.LOWEST_PRECEDENCE);
    }

    private static <T> T localSingleOrDefault(
            ConfigurableListableBeanFactory beanFactory,
            Class<T> type,
            T defaultValue) {
        Map<String, T> beans = localBeans(beanFactory, type);
        if (beans.size() > 1) {
            throw compositionError("multiple local " + type.getSimpleName() + " beans");
        }
        return beans.isEmpty() ? defaultValue : beans.values().iterator().next();
    }

    private static <T> T requireExactlyOne(
            ConfigurableListableBeanFactory beanFactory,
            Class<T> type) {
        Map<String, T> beans = localBeans(beanFactory, type);
        if (beans.size() != 1) {
            throw compositionError("exactly one local " + type.getSimpleName() + " is required");
        }
        return beans.values().iterator().next();
    }

    private static <T> Map<String, T> localBeans(
            ConfigurableListableBeanFactory beanFactory,
            Class<T> type) {
        return beanFactory.getBeansOfType(type, true, false);
    }

    private static IllegalStateException compositionError(String reason) {
        return new IllegalStateException("KCS_E_1002: invalid Kernel composition: " + reason);
    }

    /** 已解析 order 的 local Sink，避免依赖 BeanFactory 的注册迭代顺序。 */
    private record OrderedSink(
            /** 当前 context 中用于稳定打破 order 平局的 Bean 名。 */ String name,
            /** 最终写入 KernelConfig 的 Sink 实例。 */ FlowSink sink,
            /** Ordered 或类级 Order 声明的数值，未声明时为最低优先级。 */ int order) {
    }
}
