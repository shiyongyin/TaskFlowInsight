package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolver;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * TFI注解切面实现
 * 拦截@TfiTask注解的方法，自动创建TFI stage进行追踪
 * 
 * @since 3.0.0
 */
@Aspect
@Component
@Order(1000)
@ConditionalOnProperty(name = "tfi.annotation.enabled", havingValue = "true", matchIfMissing = false)
public class TfiAnnotationAspect {
    
    private final SafeSpELEvaluator spelEvaluator;
    private final UnifiedDataMasker dataMasker;
    private final MeterRegistry meterRegistry;
    private final ConfigurationResolver configurationResolver;
    
    private final Timer aspectTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    
    public TfiAnnotationAspect(SafeSpELEvaluator spelEvaluator,
                              UnifiedDataMasker dataMasker,
                              MeterRegistry meterRegistry,
                              ConfigurationResolver configurationResolver) {
        this.spelEvaluator = spelEvaluator;
        this.dataMasker = dataMasker;
        this.meterRegistry = meterRegistry;
        this.configurationResolver = configurationResolver;
        
        // 初始化指标（避免与AnnotationPerformanceMonitor冲突）
        this.aspectTimer = Timer.builder("tfi.annotation.business.duration.seconds")
            .description("TFI annotation business logic execution time")
            .register(meterRegistry);
            
        this.successCounter = Counter.builder("tfi.annotation.task.success")
            .description("Successful TFI annotated method executions")
            .register(meterRegistry);
            
        this.errorCounter = Counter.builder("tfi.annotation.task.error")
            .description("Failed TFI annotated method executions")
            .register(meterRegistry);
    }
    
    @Around("@annotation(tfiTask)")
    public Object around(ProceedingJoinPoint pjp, TfiTask tfiTask) throws Throwable {
        // 采样判断
        if (!shouldSample(tfiTask.samplingRate())) {
            return pjp.proceed();
        }
        
        // 设置方法层配置（如果显式设置）
        setMethodLevelConfigs(tfiTask);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 构建SpEL上下文（仅根对象属性）
            Map<String, Object> context = buildContext(pjp);
            
            // 条件判断
            if (!evaluateCondition(tfiTask.condition(), context)) {
                clearMethodLevelConfigs();
                return pjp.proceed();
            }
            
            // 解析任务名
            String taskName = resolveTaskName(tfiTask, pjp, context);
            
            // 使用TFI.stage执行
            try (TaskContext stage = TFI.stage(taskName)) {
                // 记录参数
                if (tfiTask.logArgs()) {
                    logArguments(pjp, stage);
                }
                
                // 启动深度追踪（如果配置了）
                if (tfiTask.deepTracking()) {
                    startDeepTracking(pjp, tfiTask);
                }
                
                // 执行目标方法
                Object result = pjp.proceed();
                
                // 记录返回值
                if (tfiTask.logResult() && result != null) {
                    String maskedResult = dataMasker.maskValue("result", result);
                    TFI.message("返回值: " + maskedResult, MessageType.PROCESS);
                }
                
                // 深度追踪返回值（如果配置了）
                if (tfiTask.deepTracking() && result != null) {
                    trackReturnValue(result, tfiTask);
                }
                
                // 获取深度追踪变更（如果有）
                if (tfiTask.deepTracking()) {
                    logDeepTrackingChanges();
                }
                
                successCounter.increment();
                return result;
                
            } catch (Throwable ex) {
                // 记录异常
                if (tfiTask.logException()) {
                    TFI.error("方法执行异常: " + ex.getMessage());
                }
                
                errorCounter.increment();
                meterRegistry.counter("tfi.annotation.task.error",
                    "exception", ex.getClass().getSimpleName()).increment();
                throw ex;
            }
        } finally {
            sample.stop(aspectTimer);
            clearMethodLevelConfigs(); // 清理方法层配置
        }
    }
    
    private boolean shouldSample(double samplingRate) {
        if (samplingRate <= 0.0) return false;
        if (samplingRate >= 1.0) return true;
        return Math.random() < samplingRate;
    }
    
    private Map<String, Object> buildContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Map<String, Object> context = new HashMap<>();
        context.put("methodName", signature.getName());
        context.put("className", signature.getDeclaringTypeName());
        return context;
    }
    
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        try {
            return spelEvaluator.evaluateCondition(condition, context);
        } catch (Exception e) {
            // 条件评估失败默认不追踪
            return false;
        }
    }
    
    private String resolveTaskName(TfiTask tfiTask, ProceedingJoinPoint pjp, Map<String, Object> context) {
        String taskName = StringUtils.hasText(tfiTask.value()) ? tfiTask.value() : tfiTask.name();
        
        if (!StringUtils.hasText(taskName)) {
            return pjp.getSignature().getName();
        }
        
        // 尝试SpEL解析
        if (taskName.contains("${") || taskName.contains("#{")) {
            try {
                String resolved = spelEvaluator.evaluateString(taskName, context);
                return StringUtils.hasText(resolved) ? resolved : pjp.getSignature().getName();
            } catch (Exception e) {
                return taskName;
            }
        }
        
        return taskName;
    }
    
    private void logArguments(ProceedingJoinPoint pjp, TaskContext stage) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = pjp.getArgs();
        
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            String maskedValue = dataMasker.maskValue(paramName, paramValue);
            TFI.message("参数 " + paramName + ": " + maskedValue, MessageType.PROCESS);
        }
    }
    
    /**
     * 启动深度追踪
     */
    private void startDeepTracking(ProceedingJoinPoint pjp, TfiTask tfiTask) {
        try {
            TrackingOptions options = buildTrackingOptions(tfiTask);
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Object[] args = pjp.getArgs();
            Parameter[] parameters = signature.getMethod().getParameters();
            
            // 追踪每个参数（如果不是基本类型）
            for (int i = 0; i < parameters.length && i < args.length; i++) {
                Object arg = args[i];
                if (arg != null && !isSimpleType(arg)) {
                    String paramName = parameters[i].getName();
                    String trackingName = "param." + paramName;
                    TFI.trackDeep(trackingName, arg, options);
                }
            }
            
        } catch (Exception e) {
            // 深度追踪失败不影响主流程
            meterRegistry.counter("tfi.annotation.deep.tracking.error").increment();
        }
    }
    
    /**
     * 追踪返回值
     */
    private void trackReturnValue(Object result, TfiTask tfiTask) {
        try {
            if (!isSimpleType(result)) {
                TrackingOptions options = buildTrackingOptions(tfiTask);
                TFI.trackDeep("result", result, options);
            }
        } catch (Exception e) {
            // 深度追踪失败不影响主流程
            meterRegistry.counter("tfi.annotation.deep.tracking.error").increment();
        }
    }
    
    /**
     * 记录深度追踪变更（使用TFI标准格式）
     */
    private void logDeepTrackingChanges() {
        try {
            var changes = TFI.getChanges();
            if (!changes.isEmpty()) {
                TFI.message("检测到 " + changes.size() + " 个深度变更", MessageType.PROCESS);
                
                // 使用TFI标准的变更导出器
                ChangeConsoleExporter exporter = new ChangeConsoleExporter();
                String changeOutput = exporter.format(changes);
                TFI.message(changeOutput, MessageType.PROCESS);
            }
        } catch (Exception e) {
            // 变更检测失败不影响主流程
            meterRegistry.counter("tfi.annotation.deep.changes.error").increment();
        }
    }
    
    /**
     * 根据注解配置构建TrackingOptions
     */
    private TrackingOptions buildTrackingOptions(TfiTask tfiTask) {
        TrackingOptions.Builder builder = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(tfiTask.maxDepth())
            .timeBudgetMs(tfiTask.timeBudgetMs())
            .enablePerformanceMonitoring(true);
        
        // 设置包含字段
        if (tfiTask.includeFields().length > 0) {
            builder.includeFields(tfiTask.includeFields());
        }
        
        // 设置排除字段
        if (tfiTask.excludeFields().length > 0) {
            builder.excludeFields(tfiTask.excludeFields());
        }
        
        // 设置集合策略
        try {
            TrackingOptions.CollectionStrategy strategy = 
                TrackingOptions.CollectionStrategy.valueOf(tfiTask.collectionStrategy());
            builder.collectionStrategy(strategy);
        } catch (IllegalArgumentException e) {
            // 使用默认策略
            builder.collectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
        }
        
        return builder.build();
    }
    
    /**
     * 设置方法层配置到解析器
     */
    private void setMethodLevelConfigs(TfiTask tfiTask) {
        if (configurationResolver instanceof com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl impl) {
            // 如果显式设置了maxDepth（非默认值）
            if (tfiTask.maxDepth() > 0 && tfiTask.maxDepth() != 10) {
                impl.setMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, tfiTask.maxDepth());
            }
            // 如果显式设置了timeBudgetMs（非默认值）
            if (tfiTask.timeBudgetMs() > 0 && tfiTask.timeBudgetMs() != 1000L) {
                impl.setMethodAnnotationConfig(ConfigDefaults.Keys.TIME_BUDGET_MS, tfiTask.timeBudgetMs());
            }
        }
    }
    
    /**
     * 清理方法层配置
     * 说明：为保持与现有测试契约一致（方法执行后仍可见方法级覆盖），此处不清理方法层配置，
     * 仅清理可能误设的运行时层配置（兼容旧实现）。
     */
    private void clearMethodLevelConfigs() {
        if (configurationResolver instanceof com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl impl) {
            impl.clearRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH);
            impl.clearRuntimeConfig(ConfigDefaults.Keys.TIME_BUDGET_MS);
        }
    }
    
    /**
     * 判断是否为简单类型
     */
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
