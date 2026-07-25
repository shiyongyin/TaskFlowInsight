package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.TfiListDiffFacade;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 为单个 Spring 上下文组装不可变 Compare 对象图。
 *
 * <p>装配严格沿 {@code Policy -> Runtime -> Engine} 单向进行，Engine 只能从最终 Runtime 导出。
 * 本配置不接触 Core Registry，因此上下文创建和关闭不会改变 JVM 静态入口或其他上下文。</p>
 *
 * @since 4.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(TfiCompareProperties.class)
public class TfiCompareAutoConfiguration {

    /**
     * 从当前上下文配置构造完整 Policy。
     *
     * <p>存在用户 Policy 或完整 Runtime 时不创建默认 Policy，避免在 custom Runtime 模式下发布一份未被执行的
     * 平行配置。</p>
     *
     * @param beanFactory 当前上下文 bean 目录
     * @param environment 只在启动期解析有限旧 key 的当前上下文环境
     * @return 已验证且不可变的比较策略
     */
    @Bean
    @ConditionalOnMissingBean(
            value = {ComparePolicy.class, CompareRuntime.class},
            search = SearchStrategy.CURRENT)
    public ComparePolicy tfiComparePolicy(
            final ListableBeanFactory beanFactory,
            final Environment environment) {
        final TfiCompareProperties properties = requireLocalBean(
                beanFactory, TfiCompareProperties.class);
        return new TfiComparePropertyAliases(environment).toPolicy(properties);
    }

    /**
     * 使用当前上下文唯一 Policy 冻结比较对象图。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 不写入任何全局 Registry 的不可变运行时
     */
    @Bean
    @ConditionalOnMissingBean(value = CompareRuntime.class, search = SearchStrategy.CURRENT)
    public CompareRuntime tfiCompareRuntime(final ListableBeanFactory beanFactory) {
        final ComparePolicy policy = requireLocalBean(beanFactory, ComparePolicy.class);
        return CompareRuntime.builder()
                .policy(policy)
                .build();
    }

    /**
     * 从最终 Runtime 导出唯一执行入口。
     *
     * <p>{@link CompareEngine} 已直接实现最小 Operations 合同，因此无需额外包装器；后续 Ops 装饰器也只能委托
     * 该实例一次。</p>
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return Runtime 持有的同一个 Engine 实例
     */
    @Bean
    @ConditionalOnMissingBean(value = CompareEngine.class, search = SearchStrategy.CURRENT)
    public CompareEngine tfiCompareEngine(final ListableBeanFactory beanFactory) {
        final CompareRuntime runtime = requireLocalBean(beanFactory, CompareRuntime.class);
        return runtime.engine();
    }

    /**
     * 构造当前上下文的完整安全脱敏策略。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 已编译全部规则的不可变脱敏策略
     */
    @Bean
    @ConditionalOnMissingBean(value = MaskingPolicy.class, search = SearchStrategy.CURRENT)
    public MaskingPolicy tfiCompareMaskingPolicy(final ListableBeanFactory beanFactory) {
        final TfiCompareProperties properties = requireLocalBean(
                beanFactory, TfiCompareProperties.class);
        return MaskingPolicy.safeDefaultsWithAdditionalRules(
                properties.masking().additionalRules());
    }

    /**
     * 把旧列表门面绑定到当前上下文最终选择的Operations与安全脱敏策略。
     *
     * <p>门面本身保持纯Java且不查找容器；在这里装配可确保它不会绕过custom Runtime建立默认执行图。</p>
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 只委托当前上下文对象图的列表门面
     */
    @Bean
    @ConditionalOnMissingBean(value = TfiListDiffFacade.class, search = SearchStrategy.CURRENT)
    public TfiListDiffFacade tfiListDiffFacade(final ListableBeanFactory beanFactory) {
        final CompareOperations operations = requireLocalBean(
                beanFactory, CompareOperations.class);
        final MaskingPolicy maskingPolicy = requireLocalBean(beanFactory, MaskingPolicy.class);
        return new TfiListDiffFacade(operations, maskingPolicy, new MarkdownRenderer());
    }

    /**
     * 在全部 singleton 可见后验证唯一对象图，避免条件装配掩盖非法 custom 组合。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @return 只执行一次启动校验的回调
     */
    @Bean
    public SmartInitializingSingleton tfiCompareCompositionValidator(
            final ListableBeanFactory beanFactory) {
        return new TfiCompareCompositionValidator(beanFactory);
    }

    /**
     * 先证明当前 BeanFactory 存在候选，再保留本层 {@code @Primary} 选择语义。
     *
     * @param beanFactory 当前上下文 bean 目录
     * @param type 必须由当前上下文拥有的领域类型
     * @param <T> 领域类型
     * @return 当前上下文选择的唯一候选
     */
    /* default */ static <T> T requireLocalBean(
            final ListableBeanFactory beanFactory,
            final Class<T> type) {
        if (beanFactory.getBeanNamesForType(type, true, false).length == 0) {
            throw new IllegalStateException("Missing local bean: " + type.getName());
        }
        return beanFactory.getBean(type);
    }
}
