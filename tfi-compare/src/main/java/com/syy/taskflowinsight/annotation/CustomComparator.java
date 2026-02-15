package com.syy.taskflowinsight.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级自定义比较器注解。
 * 用于声明某个字段采用特定的 PropertyComparator 实现进行比较。
 * 优先级高于数值/时间精度注解。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomComparator {
    /** 比较器类型（需有无参构造函数）。 */
    Class<? extends com.syy.taskflowinsight.tracking.compare.PropertyComparator> value();

    /** 是否缓存比较器实例（默认 true）。 */
    boolean cached() default true;

    /** 可选描述，用于日志/诊断。 */
    String description() default "";
}

