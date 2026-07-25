package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.IdGenerator;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

/**
 * 在 context ready 前证明 owner 与派生 Bean 形成一套 current-context 对象图。
 *
 * <p>Bean 条件只能决定是否创建，不能证明 custom owner 没有与 Config、SPI 或派生入口混用；
 * 因此校验器在 singleton 完成后统一裁决，并且从不进入请求路径。</p>
 */
final class TfiKernelCompareCompositionValidator implements SmartInitializingSingleton {

    /** 只有应用显式添加标准 Boot AOP feature dependency 才存在的稳定 marker。 */
    private static final String AOP_FEATURE_MARKER = "org.aspectj.weaver.Advice";
    /** 第五 AutoConfiguration 创建且不允许应用替换的 Advisor 保留名。 */
    private static final String AOP_ADVISOR_BEAN_NAME = "tfiKernelCompareAdvisor";
    /** Spring AOP 为当前 context 注册代理处理器时使用的固定 infrastructure Bean 名。 */
    private static final String AOP_AUTO_PROXY_CREATOR_BEAN_NAME =
            "org.springframework.aop.config.internalAutoProxyCreator";

    /** 当前 context 的本地 BeanFactory，不使用 ancestor 查询工具。 */
    private final ConfigurableListableBeanFactory beanFactory;
    /** 已完成配置组合校验的 integration 开关。 */
    private final TfiKernelCompareProperties integrationProperties;

    TfiKernelCompareCompositionValidator(
            ConfigurableListableBeanFactory beanFactory,
            TfiKernelCompareProperties integrationProperties) {
        this.beanFactory = beanFactory;
        this.integrationProperties = integrationProperties;
    }

    /** 校验 Kernel、Compare、bridge 三段图，任一歧义都阻止 context ready。 */
    @Override
    public void afterSingletonsInstantiated() {
        validateKernelOwner();
        validateCompareOwner();
        validateIntegration();
        validateAopFeature();
    }

    private void validateKernelOwner() {
        Map<String, KernelRuntime> runtimes = localBeans(KernelRuntime.class);
        if (runtimes.size() != 1) {
            reject("exactly one KernelRuntime is required");
        }
        Map<String, KernelConfig> configs = localBeans(KernelConfig.class);
        boolean starterRuntime = isStarterBean(
                "tfiKernelRuntime", TfiKernelRuntimeAutoConfiguration.class, "tfiKernelRuntime");
        if (starterRuntime) {
            if (configs.size() != 1) {
                reject("starter KernelRuntime requires exactly one KernelConfig");
            }
            boolean starterConfig = isStarterBean(
                    "tfiKernelConfig", TfiKernelRuntimeAutoConfiguration.class, "tfiKernelConfig");
            if (starterConfig) {
                requireAtMostOne(Sampler.class);
                requireAtMostOne(IdGenerator.class);
                requireAtMostOne(KernelClock.class);
            } else {
                rejectKernelSpiWithCustomOwner();
            }
        } else {
            if (!configs.isEmpty()) {
                reject("custom KernelRuntime cannot be combined with KernelConfig");
            }
            rejectKernelSpiWithCustomOwner();
        }
        registerRetirementDependencies(runtimes.keySet().iterator().next());
    }

    private void registerRetirementDependencies(String runtimeBeanName) {
        String retirementBeanName = TfiKernelRuntimeAutoConfiguration.RUNTIME_RETIREMENT_BEAN_NAME;
        if (!beanFactory.containsLocalBean(retirementBeanName)) {
            reject("KernelRuntime retirement bean is required");
        }
        beanFactory.registerDependentBean(runtimeBeanName, retirementBeanName);
        for (String sinkBeanName : localBeans(FlowSink.class).keySet()) {
            beanFactory.registerDependentBean(sinkBeanName, retirementBeanName);
        }
    }

    private void rejectKernelSpiWithCustomOwner() {
        if (!localBeans(Sampler.class).isEmpty()
                || !localBeans(IdGenerator.class).isEmpty()
                || !localBeans(KernelClock.class).isEmpty()
                || !localBeans(FlowSink.class).isEmpty()) {
            reject("custom KernelConfig or KernelRuntime cannot be combined with Kernel SPI beans");
        }
    }

    private void validateCompareOwner() {
        Map<String, CompareRuntime> runtimes = localBeans(CompareRuntime.class);
        if (runtimes.size() != 1) {
            reject("exactly one CompareRuntime is required");
        }
        CompareRuntime runtime = runtimes.values().iterator().next();
        validateComparePolicyOwner(runtime);
        validateCompareExecutionIdentity(runtime);
        validateCompareSupportBeans();
    }

    private void validateComparePolicyOwner(CompareRuntime runtime) {
        Map<String, ComparePolicy> policies = localBeans(ComparePolicy.class);
        boolean starterRuntime = isStarterBean(
                "tfiCompareRuntime", TfiCompareCoreAutoConfiguration.class, "tfiCompareRuntime");
        if (starterRuntime) {
            if (policies.size() != 1 || policies.values().iterator().next() != runtime.policy()) {
                reject("starter CompareRuntime must own the selected ComparePolicy");
            }
        } else if (!policies.isEmpty()) {
            reject("custom CompareRuntime cannot be combined with ComparePolicy");
        }
    }

    private void validateCompareExecutionIdentity(CompareRuntime runtime) {
        Map<String, CompareEngine> engines = localBeans(CompareEngine.class);
        if (engines.size() != 1 || engines.values().iterator().next() != runtime.engine()) {
            reject("CompareEngine must be the final CompareRuntime engine");
        }
        Map<String, CompareOperations> operations = localBeans(CompareOperations.class);
        if (operations.size() != 1 || operations.values().iterator().next() != runtime.engine()) {
            reject("CompareOperations must resolve only to the final CompareEngine");
        }
    }

    private void validateCompareSupportBeans() {
        requireExactlyOne(TrackingExecutor.class);
        requireExactlyOne(CompareProjectionFactory.class);
        Map<String, MaskingPolicy> maskingPolicies = localBeans(MaskingPolicy.class);
        if (maskingPolicies.size() != 1
                || maskingPolicies.values().iterator().next().includesSensitiveValues()) {
            reject("exactly one non-sensitive MaskingPolicy is required");
        }
    }

    private void validateIntegration() {
        Map<String, KernelCompareRecordPolicy> policies = localBeans(KernelCompareRecordPolicy.class);
        Map<String, KernelCompareRecorder> recorders = localBeans(KernelCompareRecorder.class);
        if (integrationProperties.enabled()) {
            if (policies.size() != 1 || recorders.size() != 1) {
                reject("enabled integration requires one RecordPolicy and one Recorder");
            }
        } else if (!policies.isEmpty() || !recorders.isEmpty()) {
            reject("disabled integration cannot publish RecordPolicy or Recorder");
        }
    }

    private void validateAopFeature() {
        boolean enabled = integrationProperties.aop().enabled();
        boolean advisorPresent = beanFactory.containsLocalBean(AOP_ADVISOR_BEAN_NAME);
        if (!enabled && advisorPresent) {
            rejectAop("advisor is present while AOP is disabled");
        }
        if (!enabled) {
            return;
        }
        if (!ClassUtils.isPresent(AOP_FEATURE_MARKER, beanFactory.getBeanClassLoader())) {
            rejectAop("optional feature dependency is required");
        }
        if (!advisorPresent) {
            rejectAop("AOP advisor is required");
        }
        if (!beanFactory.containsLocalBean(AOP_AUTO_PROXY_CREATOR_BEAN_NAME)) {
            rejectAop("Spring auto-proxy creator is required");
        }
    }

    private <T> void requireAtMostOne(Class<T> type) {
        if (localBeans(type).size() > 1) {
            reject("multiple local " + type.getSimpleName() + " beans");
        }
    }

    private <T> void requireExactlyOne(Class<T> type) {
        if (localBeans(type).size() != 1) {
            reject("exactly one local " + type.getSimpleName() + " is required");
        }
    }

    private <T> Map<String, T> localBeans(Class<T> type) {
        return beanFactory.getBeansOfType(type, true, false);
    }

    private boolean isStarterBean(String beanName, Class<?> configurationType, String factoryMethod) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition definition = beanFactory.getMergedBeanDefinition(beanName);
        String factoryBeanName = definition.getFactoryBeanName();
        Class<?> factoryType = factoryBeanName == null ? null : beanFactory.getType(factoryBeanName);
        return factoryMethod.equals(definition.getFactoryMethodName())
                && factoryType != null
                && configurationType.isAssignableFrom(factoryType);
    }

    private static void reject(String reason) {
        throw new IllegalStateException("KCS_E_1002: invalid TFI composition: " + reason);
    }

    private static void rejectAop(String reason) {
        throw new IllegalStateException("KCS_E_1101: invalid TFI AOP composition: " + reason);
    }
}
