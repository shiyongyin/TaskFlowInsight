package com.syy.tfi.kernel.compare.spring;

import java.lang.reflect.Method;
import java.util.Objects;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

/** 在 Spring 原生代理候选匹配阶段解析并校验静态 tracking 元数据。 */
final class TfiTrackedPointcut extends StaticMethodMatcherPointcut {

    /** 与调用期共享且按 target class 隔离缓存的方法计划解析器。 */
    private final TfiTrackedMethodPlanResolver resolver;

    TfiTrackedPointcut(TfiTrackedMethodPlanResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** 非法元数据直接阻止代理创建；合法注解方法才进入 Advisor。 */
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return resolver.resolve(method, targetClass).isPresent();
    }
}
