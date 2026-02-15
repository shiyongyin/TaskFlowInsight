package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Parameter;
import java.util.List;

/**
 * {@link TfiTask#deepTracking()} 的 Spring AOP 实现（compare 模块）.
 *
 * <p>该切面仅在引入 {@code tfi-compare} 且 Spring AOP 生效时启用，用于：
 * <ul>
 *   <li>在方法执行前对参数对象建立基线快照</li>
 *   <li>在方法执行后计算差异并输出到 Flow session 的 message</li>
 * </ul>
 *
 * <p>注意：为避免与 flow-starter 的 stage 创建切面冲突，本切面默认 {@code @Order(1100)}，
 * 使其在 {@code tfi-flow-spring-starter} 的 {@code @Order(1000)} 之内执行（即 stage 已创建）。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@Aspect
@Order(1100)
public class TfiDeepTrackingAspect {

    private static final Logger logger = LoggerFactory.getLogger(TfiDeepTrackingAspect.class);

    private final ChangeConsoleExporter consoleExporter;

    public TfiDeepTrackingAspect() {
        this(new ChangeConsoleExporter());
    }

    public TfiDeepTrackingAspect(ChangeConsoleExporter consoleExporter) {
        this.consoleExporter = consoleExporter;
    }

    @Around("@annotation(tfiTask)")
    public Object around(ProceedingJoinPoint pjp, TfiTask tfiTask) throws Throwable {
        if (tfiTask == null || !tfiTask.deepTracking()) {
            return pjp.proceed();
        }

        // 若调用方已存在追踪数据，则避免在 finally 中清空，降低副作用
        int trackedBefore = ChangeTracker.getTrackedCount();

        TrackingOptions options = buildTrackingOptions(tfiTask);
        trackArguments(pjp, options);

        try {
            Object result = pjp.proceed();

            // 追踪返回值（注意：对“新创建对象”无法产生前后差异，仅作为后续比较基线）
            if (result != null && !isSimpleType(result)) {
                ChangeTracker.track("result", result, options);
            }

            List<ChangeRecord> changes = ChangeTracker.getChanges();
            if (changes != null && !changes.isEmpty()) {
                TfiFlow.message("检测到 " + changes.size() + " 个深度变更", MessageType.PROCESS);
                TfiFlow.message(consoleExporter.format(changes), MessageType.PROCESS);
            }

            return result;
        } finally {
            if (trackedBefore == 0) {
                // 仅在本次调用是“最外层追踪”的情况下清理，避免误清除外部追踪
                ChangeTracker.clearAllTracking();
            }
        }
    }

    private void trackArguments(ProceedingJoinPoint pjp, TrackingOptions options) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Parameter[] parameters = signature.getMethod().getParameters();
            Object[] args = pjp.getArgs();

            for (int i = 0; i < parameters.length && i < args.length; i++) {
                Object arg = args[i];
                if (arg == null || isSimpleType(arg)) {
                    continue;
                }
                String paramName = parameters[i].getName();
                String trackingName = "param." + paramName;
                ChangeTracker.track(trackingName, arg, options);
            }
        } catch (Throwable t) {
            // 深度追踪失败不影响主流程
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to start deep tracking: {}", t.getMessage(), t);
            } else {
                logger.info("Failed to start deep tracking: {}", t.getMessage());
            }
        }
    }

    private TrackingOptions buildTrackingOptions(TfiTask tfiTask) {
        TrackingOptions.Builder builder = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(tfiTask.maxDepth())
            .timeBudgetMs(tfiTask.timeBudgetMs())
            .enableCycleDetection(true)
            .enablePerformanceMonitoring(true);

        if (tfiTask.includeFields() != null && tfiTask.includeFields().length > 0) {
            builder.includeFields(tfiTask.includeFields());
        }
        if (tfiTask.excludeFields() != null && tfiTask.excludeFields().length > 0) {
            builder.excludeFields(tfiTask.excludeFields());
        }

        try {
            TrackingOptions.CollectionStrategy strategy =
                TrackingOptions.CollectionStrategy.valueOf(tfiTask.collectionStrategy());
            builder.collectionStrategy(strategy);
        } catch (IllegalArgumentException e) {
            builder.collectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
        }

        return builder.build();
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String ||
            obj instanceof Number ||
            obj instanceof Boolean ||
            obj instanceof Character ||
            obj instanceof java.util.Date ||
            obj instanceof Enum ||
            obj.getClass().isPrimitive();
    }
}

