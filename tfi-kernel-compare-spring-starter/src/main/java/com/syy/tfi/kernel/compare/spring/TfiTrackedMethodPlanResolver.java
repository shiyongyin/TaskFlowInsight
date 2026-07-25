package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.ClassUtils;

/**
 * 从 invocation method 与实际 target class 解析静态 tracking 计划。
 *
 * <p>代理创建期与调用期共享同一份按 target class 隔离的计划缓存。ClassValue 使缓存生命周期跟随
 * target class，避免 Spring context 重载后由全局 Method key 持有旧 ClassLoader。</p>
 */
final class TfiTrackedMethodPlanResolver {

    /** 方法 operation 的完整 grammar，最长 63 个 ASCII 字符。 */
    private static final Pattern METHOD_OPERATION =
            Pattern.compile("[a-z][a-z0-9._-]{0,62}");
    /** 参数 target 名的完整 grammar，最长 64 个 ASCII 字符。 */
    private static final Pattern TARGET_NAME =
            Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    /** target class 退役时可一并回收的静态计划；非法声明不会写入缓存。 */
    private final ClassValue<ConcurrentMap<Method, Optional<TfiTrackedMethodPlan>>> plansByTargetClass =
            new ClassValue<>() {
                @Override
                protected ConcurrentMap<Method, Optional<TfiTrackedMethodPlan>> computeValue(
                        Class<?> targetClass) {
                    return new ConcurrentHashMap<>();
                }
            };

    /**
     * 解析 most-specific public method 及其接口声明，返回唯一一致的计划。
     *
     * @param invocationMethod 代理看到的调用方法
     * @param targetClass 实际 Spring target class
     * @return 无注解时为空，有注解时为不可变计划
     */
    Optional<TfiTrackedMethodPlan> resolve(Method invocationMethod, Class<?> targetClass) {
        Method bridgedInvocation = BridgeMethodResolver.findBridgedMethod(
                Objects.requireNonNull(invocationMethod, "invocationMethod"));
        Class<?> requiredTargetClass = Objects.requireNonNull(targetClass, "targetClass");
        ConcurrentMap<Method, Optional<TfiTrackedMethodPlan>> plans =
                plansByTargetClass.get(requiredTargetClass);
        Optional<TfiTrackedMethodPlan> cached = plans.get(bridgedInvocation);
        if (cached != null) {
            return cached;
        }
        Optional<TfiTrackedMethodPlan> resolved = resolveUncached(
                bridgedInvocation, requiredTargetClass);
        Optional<TfiTrackedMethodPlan> raced = plans.putIfAbsent(bridgedInvocation, resolved);
        return raced == null ? resolved : raced;
    }

    private static Optional<TfiTrackedMethodPlan> resolveUncached(
            Method bridgedInvocation, Class<?> requiredTargetClass) {
        Method specificMethod = mostSpecific(bridgedInvocation, requiredTargetClass);
        if (!Modifier.isPublic(specificMethod.getModifiers())) {
            return Optional.empty();
        }

        List<Method> declarations = declarations(
                bridgedInvocation, specificMethod, requiredTargetClass);
        TfiTrackedMethodPlan resolved = null;
        for (Method declaration : declarations) {
            Optional<TfiTrackedMethodPlan> candidate = planForDeclaration(declaration);
            if (candidate.isEmpty()) {
                continue;
            }
            if (resolved != null && !resolved.equals(candidate.get())) {
                throw invalid(declaration, null, "DECLARATIONS_MISMATCH");
            }
            resolved = candidate.get();
        }
        return Optional.ofNullable(resolved);
    }

    /** 构造不回显 target 值的调用期动态错误。 */
    static IllegalArgumentException invalidInvocation(
            Method method,
            int parameterIndex,
            String condition) {
        return invalid(method, parameterIndex, condition);
    }

    private static List<Method> declarations(
            Method invocationMethod,
            Method specificMethod,
            Class<?> targetClass) {
        Set<Method> declarations = new LinkedHashSet<>();
        declarations.add(invocationMethod);
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            for (Method interfaceMethod : interfaceType.getMethods()) {
                Method bridgedInterface = BridgeMethodResolver.findBridgedMethod(interfaceMethod);
                if (mapsTo(bridgedInterface, specificMethod, targetClass)) {
                    declarations.add(bridgedInterface);
                }
            }
        }
        declarations.add(specificMethod);
        return List.copyOf(declarations);
    }

    private static boolean mapsTo(
            Method interfaceMethod,
            Method specificMethod,
            Class<?> targetClass) {
        if (!interfaceMethod.getName().equals(specificMethod.getName())) {
            return false;
        }
        return mostSpecific(interfaceMethod, targetClass).equals(specificMethod);
    }

    private static Method mostSpecific(Method method, Class<?> targetClass) {
        Method specific = AopUtils.getMostSpecificMethod(
                BridgeMethodResolver.findBridgedMethod(method), targetClass);
        return BridgeMethodResolver.findBridgedMethod(specific);
    }

    private static Optional<TfiTrackedMethodPlan> planForDeclaration(Method method) {
        TfiTracked tracked = method.getDeclaredAnnotation(TfiTracked.class);
        List<TfiTrackedMethodPlan.TargetSlot> targets = targetSlots(method);
        if (tracked == null) {
            if (!targets.isEmpty()) {
                throw invalid(method, targets.getFirst().parameterIndex(), "TARGET_WITHOUT_METHOD");
            }
            return Optional.empty();
        }
        if (!METHOD_OPERATION.matcher(tracked.operation()).matches()) {
            throw invalid(method, null, "INVALID_METHOD_OPERATION");
        }
        if (targets.isEmpty()) {
            throw invalid(method, null, "MISSING_TARGET");
        }
        return Optional.of(new TfiTrackedMethodPlan(tracked.operation(), targets));
    }

    private static List<TfiTrackedMethodPlan.TargetSlot> targetSlots(Method method) {
        Parameter[] parameters = method.getParameters();
        List<TfiTrackedMethodPlan.TargetSlot> targets = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < parameters.length; index++) {
            TfiTrackTarget target = parameters[index].getDeclaredAnnotation(TfiTrackTarget.class);
            if (target == null) {
                continue;
            }
            if (!TARGET_NAME.matcher(target.value()).matches()) {
                throw invalid(method, index, "INVALID_TARGET_NAME");
            }
            if (!names.add(target.value())) {
                throw invalid(method, index, "DUPLICATE_TARGET_NAME");
            }
            targets.add(new TfiTrackedMethodPlan.TargetSlot(index, target.value()));
        }
        return List.copyOf(targets);
    }

    private static IllegalArgumentException invalid(
            Method method,
            Integer parameterIndex,
            String condition) {
        StringBuilder message = new StringBuilder("KCS_E_1102: invalid tracked method metadata")
                .append(" declaration=")
                .append(method.getDeclaringClass().getName())
                .append(" method=")
                .append(method.getName());
        if (parameterIndex != null) {
            message.append(" parameterIndex=").append(parameterIndex);
        }
        return new IllegalArgumentException(message.append(" condition=").append(condition).toString());
    }
}
