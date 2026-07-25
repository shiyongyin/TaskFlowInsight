package com.syy.taskflowinsight.spi.fixtures;

import com.syy.taskflowinsight.spi.FlowProvider;

/**
 * Provider type 容量与并发测试使用的无状态接口集合。
 *
 * @since 4.0.0
 */
public final class ProviderCapacityTestTypes {

    private ProviderCapacityTestTypes() {
    }

    /** 无 ServiceLoader 声明的第一个空类型。 */
    public interface EmptyA extends FlowProvider {
    }

    /** 无 ServiceLoader 声明的第二个空类型。 */
    public interface EmptyB extends FlowProvider {
    }

    /** 使用 blocking fixture 的第一个竞态类型。 */
    public interface RaceA extends FlowProvider {
    }

    /** 使用 blocking fixture 的第二个竞态类型。 */
    public interface RaceB extends FlowProvider {
    }
}
