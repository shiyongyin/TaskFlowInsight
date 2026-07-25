package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 把最终 Compare owner 图与 bridge Recorder 组合，不实现比较或 Kernel 业务语义。
 *
 * @since 4.0.0
 */
@AutoConfiguration(after = TfiCompareCoreAutoConfiguration.class)
@ConditionalOnClass(name = {
        "com.syy.tfi.kernel.KernelRuntime",
        "com.syy.taskflowinsight.tracking.compare.CompareRuntime",
        "com.syy.tfi.kernel.compare.KernelCompareRecorder"
})
@EnableConfigurationProperties(TfiKernelCompareProperties.class)
public class TfiKernelCompareAutoConfiguration {

    /** 创建 integration detail 上限；应用可整体提供另一份合法策略。 */
    @Bean("tfiKernelCompareRecordPolicy")
    @ConditionalOnProperty(
            prefix = "tfi.kernel-compare",
            name = "enabled",
            matchIfMissing = true)
    @ConditionalOnMissingBean(
            value = KernelCompareRecordPolicy.class,
            search = SearchStrategy.CURRENT)
    public KernelCompareRecordPolicy tfiKernelCompareRecordPolicy(
            TfiKernelCompareProperties properties) {
        return properties.toRecordPolicy();
    }

    /** 创建只依赖最终 owner 图的无状态 Recorder；该派生 Bean 不允许 back-off。 */
    @Bean("tfiKernelCompareRecorder")
    @ConditionalOnProperty(
            prefix = "tfi.kernel-compare",
            name = "enabled",
            matchIfMissing = true)
    public KernelCompareRecorder tfiKernelCompareRecorder(
            ConfigurableListableBeanFactory beanFactory) {
        MaskingPolicy maskingPolicy = requireExactlyOne(beanFactory, MaskingPolicy.class);
        if (maskingPolicy.includesSensitiveValues()) {
            throw compositionError("MaskingPolicy must retain the safe projection floor");
        }
        return new KernelCompareRecorder(
                requireExactlyOne(beanFactory, CompareOperations.class),
                requireExactlyOne(beanFactory, CompareProjectionFactory.class),
                maskingPolicy,
                requireExactlyOne(beanFactory, KernelCompareRecordPolicy.class));
    }

    /** 在流量进入前验证当前 context 的 owner 与派生身份闭合。 */
    @Bean
    public SmartInitializingSingleton tfiKernelCompareCompositionValidator(
            ConfigurableListableBeanFactory beanFactory,
            TfiKernelCompareProperties properties) {
        return new TfiKernelCompareCompositionValidator(beanFactory, properties);
    }

    private static <T> T requireExactlyOne(
            ConfigurableListableBeanFactory beanFactory,
            Class<T> type) {
        Map<String, T> beans = beanFactory.getBeansOfType(type, true, false);
        if (beans.size() != 1) {
            throw compositionError("exactly one local " + type.getSimpleName() + " is required");
        }
        return beans.values().iterator().next();
    }

    private static IllegalStateException compositionError(String reason) {
        return new IllegalStateException("KCS_E_1002: invalid integration composition: " + reason);
    }
}
