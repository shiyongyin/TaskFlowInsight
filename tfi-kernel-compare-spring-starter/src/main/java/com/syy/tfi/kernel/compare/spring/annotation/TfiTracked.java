package com.syy.tfi.kernel.compare.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记由可选 Spring AOP 入口执行一次 Compare tracking 的公共业务方法。
 *
 * <p>operation 是受控类别，不得包含业务标识、凭据或用户输入。</p>
 *
 * @since 4.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TfiTracked {

    /**
     * 返回方法级受控 operation，必须满足 Starter 的 63 字符静态 grammar。
     *
     * @return 不含动态业务值的方法 operation
     */
    String operation();
}
