package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TFI 注解切面实现（Flow-only）.
 *
 * <p>该切面位于 {@code tfi-flow-spring-starter}，只负责把 {@link TfiTask} 映射为
 * Flow Stage 的创建与消息输出，不包含 compare/change-tracking/micrometer 的编译期依赖。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>采样判断（{@link TfiTask#samplingRate()}）— 未采样则直接放行</li>
 *   <li>SpEL 条件求值（{@link TfiTask#condition()}）— 条件不满足则直接放行</li>
 *   <li>任务名解析（literal / SpEL / 默认方法名）</li>
 *   <li>创建 {@link TaskContext}（AutoCloseable Stage）</li>
 *   <li>记录参数 → 执行目标方法 → 记录返回值/异常</li>
 * </ol>
 *
 * <p>当 {@link TfiTask#deepTracking()} 为 {@code true} 时，本切面不会执行深度追踪逻辑；
 * 深度追踪由 {@code tfi-compare} 模块提供的可选切面实现。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Aspect
@Component
@Order(TfiAnnotationAspect.TFI_ASPECT_ORDER)
@ConditionalOnProperty(name = "tfi.annotation.enabled", havingValue = "true", matchIfMissing = false)
public class TfiAnnotationAspect {

    /** 切面优先级，确保在业务切面（如事务）之后执行. */
    static final int TFI_ASPECT_ORDER = 1000;

    private static final Logger logger = LoggerFactory.getLogger(TfiAnnotationAspect.class);

    // 消息模板常量
    private static final String MSG_RETURN_VALUE = "返回值: ";
    private static final String MSG_EXCEPTION = "方法执行异常: ";
    private static final String MSG_PARAM_PREFIX = "参数 ";
    private static final String MSG_PARAM_SEPARATOR = ": ";

    private final SafeSpELEvaluator spelEvaluator;
    private final UnifiedDataMasker dataMasker;

    /**
     * 构造函数.
     *
     * @param spelEvaluator 安全 SpEL 解析器，不可为 {@code null}
     * @param dataMasker    统一脱敏器，不可为 {@code null}
     * @throws NullPointerException 任意参数为 {@code null} 时
     */
    public TfiAnnotationAspect(SafeSpELEvaluator spelEvaluator, UnifiedDataMasker dataMasker) {
        this.spelEvaluator = Objects.requireNonNull(spelEvaluator, "spelEvaluator");
        this.dataMasker = Objects.requireNonNull(dataMasker, "dataMasker");
    }

    /**
     * {@link TfiTask} 注解的环绕通知.
     *
     * <p>拦截标注了 {@code @TfiTask} 的方法，按采样率和条件决定是否创建 Flow Stage。
     * 异常始终透传给调用方，TFI 内部异常不影响业务逻辑。
     *
     * @param pjp     切点（目标方法的执行上下文）
     * @param tfiTask 注解实例（包含追踪参数）
     * @return 目标方法的原始返回值
     * @throws Throwable 目标方法抛出的原始异常（不做包装）
     */
    @Around("@annotation(tfiTask)")
    public Object around(ProceedingJoinPoint pjp, TfiTask tfiTask) throws Throwable {
        // L1: 采样判断
        if (!shouldSample(tfiTask.samplingRate())) {
            return pjp.proceed();
        }

        // L2: 构建 SpEL 上下文并判断条件
        Map<String, Object> context = buildContext(pjp);
        if (!evaluateCondition(tfiTask.condition(), context)) {
            return pjp.proceed();
        }

        // L3: 解析任务名
        String taskName = resolveTaskName(tfiTask, pjp, context);

        // deepTracking 由 tfi-compare 的可选切面实现
        if (tfiTask.deepTracking() && logger.isDebugEnabled()) {
            logger.debug("TfiTask.deepTracking=true detected for {}.{}; handled by tfi-compare if present.",
                    ((MethodSignature) pjp.getSignature()).getDeclaringTypeName(),
                    pjp.getSignature().getName());
        }

        // L4: 创建 Stage 并执行
        try (TaskContext stage = TfiFlow.stage(taskName)) {
            if (tfiTask.logArgs()) {
                logArguments(pjp);
            }

            try {
                Object result = pjp.proceed();

                if (tfiTask.logResult() && result != null) {
                    String maskedResult = dataMasker.maskValue("result", result);
                    TfiFlow.message(MSG_RETURN_VALUE + maskedResult, MessageType.PROCESS);
                }

                return result;
            } catch (Throwable ex) {
                stage.fail(ex);

                if (tfiTask.logException()) {
                    String maskedMessage = dataMasker.maskValue("exception", ex.getMessage());
                    stage.error(MSG_EXCEPTION + maskedMessage, ex);
                }
                throw ex;
            }
        }
    }

    /**
     * 基于采样率决定是否追踪本次调用.
     *
     * @param samplingRate 采样率，范围 [0.0, 1.0]
     * @return {@code true} 表示应该追踪
     */
    private boolean shouldSample(double samplingRate) {
        if (samplingRate <= 0.0) {
            return false;
        }
        if (samplingRate >= 1.0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < samplingRate;
    }

    /**
     * 构建 SpEL 求值上下文（仅包含方法名和类名）.
     *
     * @param pjp 切点
     * @return 包含 {@code methodName} 和 {@code className} 的上下文 Map
     */
    private Map<String, Object> buildContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Map<String, Object> context = new HashMap<>(4);
        context.put("methodName", signature.getName());
        context.put("className", signature.getDeclaringTypeName());
        return context;
    }

    /**
     * 求值 SpEL 条件表达式.
     *
     * @param condition SpEL 条件表达式（空值视为 {@code true}）
     * @param context   求值上下文
     * @return 条件求值结果；求值异常时返回 {@code false}
     */
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        try {
            return spelEvaluator.evaluateCondition(condition, context);
        } catch (Exception e) {
            logger.debug("SpEL condition evaluation failed for '{}': {}", condition, e.getMessage());
            return false;
        }
    }

    /**
     * 解析任务名（优先级：value > name > SpEL 解析 > 方法签名名）.
     *
     * @param tfiTask 注解实例
     * @param pjp     切点
     * @param context SpEL 上下文
     * @return 解析后的任务名，保证非空
     */
    private String resolveTaskName(TfiTask tfiTask, ProceedingJoinPoint pjp, Map<String, Object> context) {
        String taskName = StringUtils.hasText(tfiTask.value()) ? tfiTask.value() : tfiTask.name();

        if (!StringUtils.hasText(taskName)) {
            return pjp.getSignature().getName();
        }

        if (taskName.contains("${") || taskName.contains("#{")) {
            try {
                String resolved = spelEvaluator.evaluateString(taskName, context);
                return StringUtils.hasText(resolved) ? resolved : pjp.getSignature().getName();
            } catch (Exception e) {
                logger.debug("SpEL task name resolution failed for '{}': {}", taskName, e.getMessage());
                return taskName;
            }
        }

        return taskName;
    }

    /**
     * 记录方法参数（自动脱敏）.
     *
     * @param pjp 切点
     */
    private void logArguments(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            String maskedValue = dataMasker.maskValue(paramName, paramValue);
            TfiFlow.message(MSG_PARAM_PREFIX + paramName + MSG_PARAM_SEPARATOR + maskedValue,
                    MessageType.PROCESS);
        }
    }
}
