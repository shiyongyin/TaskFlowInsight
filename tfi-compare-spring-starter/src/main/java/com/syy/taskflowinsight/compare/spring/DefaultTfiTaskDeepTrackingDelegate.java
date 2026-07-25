package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.InputViolation;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把 Flow 已激活的 TfiTask 调用适配到当前 context 的唯一 {@link TrackingExecutor}。
 *
 * <p>该实现不拥有 AOP、sampling、stage 关闭或全局 history；它只在启动期编译静态 annotation，
 * 并把单次业务调用权交给 final executor，避免 Spring 入口绕回 JVM default runtime。</p>
 *
 * @since 4.0.0
 */
public final class DefaultTfiTaskDeepTrackingDelegate
        implements TfiTaskDeepTrackingDelegate, SmartInitializingSingleton {

    /** 固定消息日志器，不输出字段规则或业务值。 */
    private static final Logger logger = LoggerFactory.getLogger(DefaultTfiTaskDeepTrackingDelegate.class);
    /** SUMMARY 迁移提示在单个 context 最多记录一次。 */
    private final AtomicBoolean summaryDeprecationLogged = new AtomicBoolean();
    /** 唯一业务 action sequencing 入口。 */
    private final TrackingExecutor executor;
    /** 启动期静态 annotation 目录；显式纯单元构造时为空。 */
    private final ListableBeanFactory beanFactory;
    /** 当前 context runtime 的不可变上界。 */
    private final ComparePolicy runtimePolicy;
    /** 启动期一次冻结的 annotation 选项。 */
    private volatile Map<OptionsKey, CompareOptions> validatedOptions = Map.of();

    /**
     * 使用显式 provider 构造无 Spring 扫描的适配器。
     *
     * @param provider 只创建 typed batch scope 的 provider，不允许为 {@code null}
     */
    public DefaultTfiTaskDeepTrackingDelegate(TrackingProvider provider) {
        this(provider, null, ComparePolicy.defaults());
    }

    DefaultTfiTaskDeepTrackingDelegate(
            TrackingProvider provider,
            ListableBeanFactory beanFactory,
            ComparePolicy runtimePolicy) {
        executor = new TrackingExecutor(Objects.requireNonNull(provider, "provider"));
        this.beanFactory = beanFactory;
        this.runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
    }

    /** 在业务流量进入前编译全部静态 deep-tracking 声明。 */
    @Override
    public void afterSingletonsInstantiated() {
        if (beanFactory == null) {
            return;
        }
        Map<OptionsKey, CompareOptions> compiled = new HashMap<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null) {
                continue;
            }
            ReflectionUtils.doWithMethods(beanType, method -> {
                TfiTask annotation = AnnotatedElementUtils.findMergedAnnotation(method, TfiTask.class);
                if (annotation != null && annotation.deepTracking()) {
                    OptionsKey key = OptionsKey.from(annotation);
                    compiled.computeIfAbsent(key, this::compileOptions);
                }
            });
        }
        validatedOptions = Map.copyOf(compiled);
    }

    /**
     * 在 Flow stage 内追踪复杂参数，并保持业务返回值或异常身份。
     *
     * @param annotation 已通过 Flow sampling/condition 的静态配置
     * @param method 被拦截的声明方法
     * @param arguments 按声明顺序浅复制的参数
     * @param activeStage 由 Flow 负责关闭的已激活 stage
     * @param invocation 由 Flow 限制为最多执行一次的业务入口
     * @return 业务方法返回的原始引用
     * @throws Throwable 业务异常或 fatal 基础设施错误的原始实例
     */
    @Override
    public Object execute(
            TfiTask annotation,
            Method method,
            Object[] arguments,
            TaskContext activeStage,
            Invocation invocation) throws Throwable {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(activeStage, "activeStage");
        Objects.requireNonNull(invocation, "invocation");
        CompareOptions options = optionsFor(Objects.requireNonNull(annotation, "annotation"));
        List<TrackingExecutor.Target> targets = targets(arguments, options);
        if (targets.isEmpty()) {
            return invocation.proceed();
        }
        TrackingExecutor.Execution<Object> execution = executor.execute(targets, options, invocation::proceed);
        publishSafely(activeStage);
        return execution.value();
    }

    private CompareOptions optionsFor(TfiTask annotation) {
        OptionsKey key = OptionsKey.from(annotation);
        if (beanFactory == null) {
            return compileOptions(key);
        }
        CompareOptions options = validatedOptions.get(key);
        if (options == null) {
            throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
        }
        return options;
    }

    private CompareOptions compileOptions(OptionsKey key) {
        ComparePolicy policy = ComparePolicy.builder()
                .maxDepth(key.maxDepth())
                .deadline(deadline(key.timeBudgetMs()))
                .includeCollectionContents(includeCollectionContents(key.collectionStrategy()))
                .includePathRules(propertyRules(key.includeFields()))
                .excludePathRules(propertyRules(key.excludeFields()))
                .build();
        CompareOptions options = CompareOptions.defaults(policy);
        options.validateAgainst(runtimePolicy);
        if (key.collectionStrategy().equals("SUMMARY")
                && summaryDeprecationLogged.compareAndSet(false, true)) {
            logger.warn("TfiTask SUMMARY now uses complete element comparison");
        }
        return options;
    }

    private static Duration deadline(long millis) {
        if (millis <= 0) {
            throw new CompareInputException(InputViolation.OPTION_OUT_OF_RANGE);
        }
        return Duration.ofMillis(millis);
    }

    private static boolean includeCollectionContents(String strategy) {
        return switch (Objects.requireNonNull(strategy, "collectionStrategy")) {
            case "ELEMENT", "SUMMARY" -> true;
            case "IGNORE" -> false;
            default -> throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
        };
    }

    private static List<String> propertyRules(List<String> fields) {
        return fields.stream().map(field -> "PROPERTY:" + Objects.requireNonNull(field, "field")).toList();
    }

    private static List<TrackingExecutor.Target> targets(Object[] arguments, CompareOptions options) {
        if (arguments == null || arguments.length == 0) {
            return List.of();
        }
        List<TrackingExecutor.Target> targets = new ArrayList<>();
        for (int ordinal = 0; ordinal < arguments.length; ordinal++) {
            Object argument = arguments[ordinal];
            if (argument != null && !ValueSnapshot.captureSupported(
                    argument, options.maxResultValueChars()).isScalar()) {
                targets.add(new TrackingExecutor.Target("arg-" + ordinal, argument));
            }
        }
        return List.copyOf(targets);
    }

    /** 普通发布失败不覆盖业务结果，fatal 仍按 Java 错误语义传播。 */
    private static void publishSafely(TaskContext activeStage) {
        try {
            activeStage.message("Deep tracking completed");
        } catch (RuntimeException exception) {
            logger.warn("Deep tracking publication failed");
        }
    }

    /** 启动期防御复制后的 annotation 配置身份。 */
    private record OptionsKey(
            /** Compare 对象图逻辑深度。 */ int maxDepth,
            /** 正数毫秒 deadline。 */ long timeBudgetMs,
            /** 只允许继续收紧的 property 规则。 */ List<String> includeFields,
            /** 显式排除的 property 规则。 */ List<String> excludeFields,
            /** 大小写敏感的闭集策略令牌。 */ String collectionStrategy) {

        private OptionsKey {
            includeFields = List.copyOf(includeFields);
            excludeFields = List.copyOf(excludeFields);
            Objects.requireNonNull(collectionStrategy, "collectionStrategy");
        }

        private static OptionsKey from(TfiTask annotation) {
            try {
                return new OptionsKey(
                        annotation.maxDepth(), annotation.timeBudgetMs(),
                        Arrays.asList(annotation.includeFields()),
                        Arrays.asList(annotation.excludeFields()), annotation.collectionStrategy());
            } catch (RuntimeException exception) {
                throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
            }
        }
    }
}
