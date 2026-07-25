package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TaskContext;

import java.lang.reflect.Method;

/**
 * 将已激活的Flow任务可选交给深度追踪实现的最小边界。
 *
 * <p>Flow starter拥有该接口，是为了让sampling、condition与stage生命周期保持单一owner；实现方只能围绕传入的
 * {@link Invocation} 增加观测，不能再创建AOP advice或重新执行采样。</p>
 *
 * <p>同一Spring上下文最多存在一个实现。接口不提供chain、order或共享状态，避免多个实现共同竞争业务调用权。</p>
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface TfiTaskDeepTrackingDelegate {

    /**
     * 在已激活的stage内执行一次业务调用及其深度追踪。
     *
     * @param annotation 当前方法的声明式追踪配置
     * @param method 被拦截的声明方法，不依赖参数名元数据
     * @param arguments 按声明顺序浅复制的调用参数
     * @param activeStage 已由Flow创建并负责关闭的任务stage
     * @param invocation 由Flow约束为最多调用一次的业务入口
     * @return 业务方法返回的原始引用
     * @throws Throwable 业务调用或fatal基础设施错误的原始异常
     */
    Object execute(
            TfiTask annotation,
            Method method,
            Object[] arguments,
            TaskContext activeStage,
            Invocation invocation) throws Throwable;

    /**
     * 把业务调用权从Flow advice移交给唯一delegate的单次入口。
     *
     * <p>实现方不得保存或跨线程调用该对象；Flow会在后续合同中强制最多调用一次。</p>
     *
     * @since 4.0.0
     */
    @FunctionalInterface
    interface Invocation {

        /**
         * 执行业务方法并保留其返回值或异常身份。
         *
         * @return 业务方法返回的原始引用
         * @throws Throwable 业务方法抛出的原始异常
         */
        Object proceed() throws Throwable;
    }
}
