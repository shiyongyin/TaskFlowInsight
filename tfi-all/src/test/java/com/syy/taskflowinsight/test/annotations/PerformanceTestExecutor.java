package com.syy.taskflowinsight.test.annotations;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.TimeUnit;

/**
 * 性能测试执行器
 * 自动化性能断言和数据收集
 */
public class PerformanceTestExecutor implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    
    private static final ExtensionContext.Namespace NAMESPACE = 
        ExtensionContext.Namespace.create("performance");
    
    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getStore(NAMESPACE).put("startTime", System.nanoTime());
        
        PerformanceTest annotation = getPerformanceAnnotation(context);
        if (annotation != null) {
            // 预热
            runWarmup(annotation.warmupIterations());
        }
    }
    
    @Override
    public void afterTestExecution(ExtensionContext context) {
        long startTime = context.getStore(NAMESPACE).get("startTime", Long.class);
        long duration = System.nanoTime() - startTime;
        
        PerformanceTest annotation = getPerformanceAnnotation(context);
        if (annotation != null) {
            validatePerformance(context, annotation, duration);
            recordPerformanceMetric(context, annotation, duration);
        }
    }
    
    private void validatePerformance(ExtensionContext context, PerformanceTest annotation, long durationNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        
        if (durationMs > annotation.maxDuration()) {
            throw new AssertionError(String.format(
                "Performance test [%s] failed: execution time %dms exceeded max %dms",
                context.getDisplayName(), durationMs, annotation.maxDuration()
            ));
        }
        
        // TPS验证（如果指定了迭代次数）
        if (annotation.iterations() > 0) {
            double actualTps = annotation.iterations() / (durationNanos / 1_000_000_000.0);
            if (actualTps < annotation.minTps()) {
                throw new AssertionError(String.format(
                    "Performance test [%s] failed: TPS %.2f below minimum %d",
                    context.getDisplayName(), actualTps, annotation.minTps()
                ));
            }
        }
    }
    
    private void recordPerformanceMetric(ExtensionContext context, PerformanceTest annotation, long durationNanos) {
        String testName = context.getTestClass().map(Class::getSimpleName).orElse("Unknown") 
            + "." + context.getTestMethod().map(m -> m.getName()).orElse("unknown");
        
        double durationMs = durationNanos / 1_000_000.0;
        double tps = annotation.iterations() > 0 ? 
            annotation.iterations() / (durationNanos / 1_000_000_000.0) : 0;
        
        System.out.printf("Performance [%s]: %.2f ms, TPS: %.2f%n", testName, durationMs, tps);
    }
    
    private PerformanceTest getPerformanceAnnotation(ExtensionContext context) {
        return context.getTestMethod()
            .map(m -> m.getAnnotation(PerformanceTest.class))
            .orElse(context.getTestClass()
                .map(c -> c.getAnnotation(PerformanceTest.class))
                .orElse(null));
    }
    
    private void runWarmup(int iterations) {
        // JVM预热
        for (int i = 0; i < iterations; i++) {
            System.nanoTime(); // 简单操作预热
        }
    }
}