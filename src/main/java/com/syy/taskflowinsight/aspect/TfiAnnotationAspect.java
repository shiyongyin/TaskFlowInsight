package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
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
import java.util.HashMap;
import java.util.Map;

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
    
    private final Timer aspectTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    
    public TfiAnnotationAspect(SafeSpELEvaluator spelEvaluator,
                              UnifiedDataMasker dataMasker,
                              MeterRegistry meterRegistry) {
        this.spelEvaluator = spelEvaluator;
        this.dataMasker = dataMasker;
        this.meterRegistry = meterRegistry;
        
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
        
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 构建SpEL上下文（仅根对象属性）
            Map<String, Object> context = buildContext(pjp);
            
            // 条件判断
            if (!evaluateCondition(tfiTask.condition(), context)) {
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
                
                // 执行目标方法
                Object result = pjp.proceed();
                
                // 记录返回值
                if (tfiTask.logResult() && result != null) {
                    String maskedResult = dataMasker.maskValue("result", result);
                    TFI.message("返回值: " + maskedResult, MessageType.PROCESS);
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
}