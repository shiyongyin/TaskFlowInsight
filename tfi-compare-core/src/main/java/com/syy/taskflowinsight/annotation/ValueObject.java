package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * 标记没有实体身份、必须按字段事实比较的值对象。
 *
 * <p>该注解刻意不携带 equals/strategy 成员：ID 风格的 {@code equals()} 可能吞掉字段变化；
 * 特殊相等性只能通过 runtime 的 typed comparator 显式注册并进入语义指纹。</p>
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValueObject {
}
