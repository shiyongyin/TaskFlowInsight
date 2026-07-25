package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Iterator;
import java.util.Map;

/**
 * 在业务流量进入前验证 Compare bean 图只有一个真实执行路径。
 *
 * <p>Spring 的条件装配只能决定 bean 是否创建，无法证明 custom Policy、Runtime、Engine 与 Operations
 * 的身份关系；因此在全部 singleton 创建后统一验证对象身份，非法组合直接阻断当前 context。</p>
 */
final class TfiCompareCompositionValidator implements SmartInitializingSingleton {

    /** 当前 context 的 bean 目录；只在启动校验阶段读取。 */
    private final ListableBeanFactory beanFactory;

    TfiCompareCompositionValidator(final ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 验证 Runtime、Engine、Operations 与 MaskingPolicy 的唯一性和身份不变量。
     */
    @Override
    public void afterSingletonsInstantiated() {
        final Map<String, CompareRuntime> runtimes = beanFactory.getBeansOfType(CompareRuntime.class);
        if (runtimes.size() != 1) {
            reject("exactly one CompareRuntime is required");
        }
        final CompareRuntime runtime = runtimes.values().iterator().next();

        final Map<String, ComparePolicy> policies = beanFactory.getBeansOfType(ComparePolicy.class);
        if (policies.size() > 1
                || policies.size() == 1 && policies.values().iterator().next() != runtime.policy()) {
            reject("custom ComparePolicy and CompareRuntime are mutually exclusive");
        }

        final Map<String, CompareEngine> engines = beanFactory.getBeansOfType(CompareEngine.class);
        if (engines.size() != 1 || engines.values().iterator().next() != runtime.engine()) {
            reject("CompareEngine must be exported from the final CompareRuntime");
        }

        final Map<String, CompareOperations> operations = beanFactory.getBeansOfType(CompareOperations.class);
        if (operations.size() == 1) {
            if (operations.values().iterator().next() != runtime.engine()) {
                reject("custom CompareOperations is not a supported composition mode");
            }
        } else if (operations.size() == 2) {
            final CompareOperationsDecorator decorator = onlyDecorator();
            if (!operations.containsValue(runtime.engine())
                    || !operations.containsValue(decorator)
                    || decorator.delegate() != runtime.engine()
                    || selectedOperations() != decorator) {
                reject("one selected CompareOperationsDecorator must directly delegate the Engine");
            }
        } else {
            reject("only Engine and one Ops decorator are supported CompareOperations beans");
        }

        final Map<String, MaskingPolicy> maskingPolicies = beanFactory.getBeansOfType(MaskingPolicy.class);
        if (maskingPolicies.size() != 1) {
            reject("exactly one complete MaskingPolicy is required");
        }
        if (maskingPolicies.values().iterator().next().includesSensitiveValues()) {
            reject("Spring MaskingPolicy cannot include sensitive values");
        }
    }

    private static void reject(final String reason) {
        throw new IllegalStateException("Invalid TFI Compare composition: " + reason);
    }

    private CompareOperations selectedOperations() {
        try {
            return beanFactory.getBean(CompareOperations.class);
        } catch (RuntimeException exception) {
            reject("CompareOperations selection must be unambiguous");
            throw exception;
        }
    }

    private CompareOperationsDecorator onlyDecorator() {
        final Map<String, CompareOperationsDecorator> decorators =
                beanFactory.getBeansOfType(CompareOperationsDecorator.class);
        final Iterator<CompareOperationsDecorator> iterator = decorators.values().iterator();
        if (!iterator.hasNext()) {
            reject("one CompareOperationsDecorator is required for a decorated graph");
        }
        final CompareOperationsDecorator decorator = iterator.next();
        if (iterator.hasNext()) {
            reject("only one CompareOperationsDecorator is supported");
        }
        return decorator;
    }
}
