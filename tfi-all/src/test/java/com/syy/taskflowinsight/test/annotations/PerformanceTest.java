package com.syy.taskflowinsight.test.annotations;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * 性能测试注解
 * 标记性能测试，包含基准和断言
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("performance")
@ExtendWith(PerformanceTestExecutor.class)
public @interface PerformanceTest {
    /**
     * 最大执行时间（毫秒）
     */
    long maxDuration() default 1000;
    
    /**
     * 最小TPS要求
     */
    int minTps() default 100;
    
    /**
     * 预热次数
     */
    int warmupIterations() default 100;
    
    /**
     * 测试迭代次数
     */
    int iterations() default 1000;
    
    /**
     * 性能基线分类
     */
    String baseline() default "default";
}