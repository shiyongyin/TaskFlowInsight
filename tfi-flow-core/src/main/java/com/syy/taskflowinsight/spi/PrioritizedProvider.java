package com.syy.taskflowinsight.spi;

/**
 * 为可排序 Provider 定义唯一的类型化优先级能力契约。
 *
 * <p>{@link ProviderRegistry} 只在同一来源内比较该数值；手动注册来源始终先于
 * {@link java.util.ServiceLoader}。把能力建模为类型可以避免 Registry 通过同名方法反射猜测语义，也让
 * Core 与 Compare 的 Provider 使用同一个默认值和失败边界。
 *
 * <p>实现应返回稳定、无副作用且可快速读取的值。第三方实现抛出异常或错误时，Registry 会将其降级为
 * 默认优先级 {@code 0}，避免单个 Provider 中断整体选择。
 *
 * @since 4.0.0
 */
public interface PrioritizedProvider {

    /**
     * 返回 Provider 在同一来源内的选择优先级。
     *
     * @return 优先级；数值越大越优先，默认值为 0
     */
    default int priority() {
        return 0;
    }
}
