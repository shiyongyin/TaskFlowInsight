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
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TFI 注解切面实现（Flow-only）.
 *
 * <p>该切面位于 {@code tfi-flow-spring-starter}，只负责把 {@link TfiTask} 映射为
 * Flow Stage 的创建与消息输出，不包含 compare/change-tracking/micrometer 的编译期依赖。
 *
 * <h2>处理流程</h2>
 * <ol>
 *   <li>采样判断（{@link TfiTask#samplingRate()}）— 未采样则直接放行</li>
 *   <li>SpEL 条件求值（{@link TfiTask#condition()}）— 条件不满足则直接放行</li>
 *   <li>任务名解析（literal / SpEL / 默认方法名）</li>
 *   <li>创建 {@link TaskContext}（AutoCloseable Stage）</li>
 *   <li>记录参数 → 执行目标方法 → 记录返回值/异常</li>
 * </ol>
 *
 * <p>当 {@link TfiTask#deepTracking()} 为 {@code true} 时，本切面只向最多一个
 * {@link TfiTaskDeepTrackingDelegate} 移交业务调用；实现方不能新增第二个切面或重复采样。
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

    /** 标识业务返回值消息，实际值在写入Flow前统一脱敏。 */
    private static final String MSG_RETURN_VALUE = "返回值: ";
    /** 标识业务异常消息，避免观测层自行改变异常传播语义。 */
    private static final String MSG_EXCEPTION = "方法执行异常: ";
    /** 参数日志前缀；参数值只允许使用脱敏后的表示。 */
    private static final String MSG_PARAM_PREFIX = "参数 ";
    /** 参数名与脱敏值之间的稳定分隔符。 */
    private static final String MSG_PARAM_SEPARATOR = ": ";

    private final SafeSpELEvaluator spelEvaluator;
    private final UnifiedDataMasker dataMasker;
    /** 可选delegate在构造期解析一次，多实现由Spring按配置错误拒绝。 */
    private final TfiTaskDeepTrackingDelegate deepTrackingDelegate;

    /**
     * 构造函数.
     *
     * @param spelEvaluator 安全 SpEL 解析器，不可为 {@code null}
     * @param dataMasker    统一脱敏器，不可为 {@code null}
     * @throws NullPointerException 任意参数为 {@code null} 时
     */
    public TfiAnnotationAspect(SafeSpELEvaluator spelEvaluator, UnifiedDataMasker dataMasker) {
        this(spelEvaluator, dataMasker, (TfiTaskDeepTrackingDelegate) null);
    }

    /**
     * 构造支持0..1深度追踪实现的Flow advice。
     *
     * @param spelEvaluator 安全SpEL解析器，不可为{@code null}
     * @param dataMasker 统一脱敏器，不可为{@code null}
     * @param delegateProvider 当前上下文的可选唯一delegate；多实现必须使启动失败
     * @throws NullPointerException 任意基础设施参数为{@code null}
     * @throws NoUniqueBeanDefinitionException 上下文存在多个delegate，即使其中一个标记为primary
     */
    @Autowired
    public TfiAnnotationAspect(
            SafeSpELEvaluator spelEvaluator,
            UnifiedDataMasker dataMasker,
            ObjectProvider<TfiTaskDeepTrackingDelegate> delegateProvider) {
        this(spelEvaluator, dataMasker, resolveUniqueDelegate(delegateProvider));
    }

    private TfiAnnotationAspect(
            SafeSpELEvaluator spelEvaluator,
            UnifiedDataMasker dataMasker,
            TfiTaskDeepTrackingDelegate deepTrackingDelegate) {
        this.spelEvaluator = Objects.requireNonNull(spelEvaluator, "spelEvaluator");
        this.dataMasker = Objects.requireNonNull(dataMasker, "dataMasker");
        this.deepTrackingDelegate = deepTrackingDelegate;
    }

    /**
     * 严格解析0..1个delegate，不允许Spring的primary规则把多实现歧义变成隐式路由。
     *
     * @param delegateProvider 当前上下文的delegate延迟目录
     * @return 唯一delegate；未配置时返回{@code null}
     * @throws NullPointerException provider为{@code null}
     * @throws NoUniqueBeanDefinitionException 实际存在多个delegate
     */
    private static TfiTaskDeepTrackingDelegate resolveUniqueDelegate(
            ObjectProvider<TfiTaskDeepTrackingDelegate> delegateProvider) {
        List<TfiTaskDeepTrackingDelegate> delegates = Objects.requireNonNull(
                delegateProvider, "delegateProvider").stream().limit(2).toList();
        if (delegates.size() > 1) {
            throw new NoUniqueBeanDefinitionException(
                    TfiTaskDeepTrackingDelegate.class,
                    delegates.size(),
                    "deep tracking requires zero or one delegate; @Primary cannot resolve multiple owners");
        }
        return delegates.isEmpty() ? null : delegates.getFirst();
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
        // Flow关闭时不创建任何观测语义，避免把no-op stage误当成已激活追踪上下文。
        if (!TfiFlow.isEnabled()) {
            return pjp.proceed();
        }
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

        // L4: 创建 Stage 并执行
        try (TaskContext stage = TfiFlow.stage(taskName)) {
            if (tfiTask.logArgs()) {
                logArguments(pjp);
            }

            try {
                Object result = proceedWithOptionalDeepTracking(pjp, tfiTask, stage);

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

    /** Flow先完成stage激活，再向唯一delegate移交一次业务调用权。 */
    private Object proceedWithOptionalDeepTracking(
            ProceedingJoinPoint pjp,
            TfiTask tfiTask,
            TaskContext activeStage) throws Throwable {
        if (!tfiTask.deepTracking() || deepTrackingDelegate == null) {
            return pjp.proceed();
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Object[] sourceArguments = pjp.getArgs();
        Object[] copiedArguments = sourceArguments == null
                ? new Object[0] : Arrays.copyOf(sourceArguments, sourceArguments.length);
        return deepTrackingDelegate.execute(
                tfiTask,
                signature.getMethod(),
                copiedArguments,
                activeStage,
                new SingleInvocation(pjp));
    }

    /** Flow在移交调用权后仍强制单次消费，避免delegate实现错误重跑业务方法。 */
    private static final class SingleInvocation implements TfiTaskDeepTrackingDelegate.Invocation {

        /** 当前advice调用对应的原始连接点。 */
        private final ProceedingJoinPoint joinPoint;
        /** 线程封闭的消费状态；它表达调用基数，不承担跨线程同步。 */
        private boolean proceeded;

        private SingleInvocation(ProceedingJoinPoint joinPoint) {
            this.joinPoint = joinPoint;
        }

        /**
         * 消费当前advice唯一的业务调用权；先置位再执行，异常路径同样不能重试。
         *
         * @return 业务方法返回的原始引用
         * @throws Throwable 业务方法抛出的原始异常
         */
        @Override
        public Object proceed() throws Throwable {
            if (proceeded) {
                throw new IllegalStateException("deep tracking invocation already proceeded");
            }
            proceeded = true;
            return joinPoint.proceed();
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
