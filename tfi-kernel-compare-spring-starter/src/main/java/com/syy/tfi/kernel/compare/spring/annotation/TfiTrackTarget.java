package com.syy.tfi.kernel.compare.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 {@link TfiTracked} 方法中需要在 action 前后捕获的参数位置。
 *
 * <p>target 名只用于稳定区分同一次调用中的多个受控目标，不得承载业务值。</p>
 *
 * @since 4.0.0
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TfiTrackTarget {

    /**
     * 返回参数级受控 target 名，必须满足 Starter 的 64 字符静态 grammar。
     *
     * @return 在同一方法内唯一的 target 名
     */
    String value();
}
