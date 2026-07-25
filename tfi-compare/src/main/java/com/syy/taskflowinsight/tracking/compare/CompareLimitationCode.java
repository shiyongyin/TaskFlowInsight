package com.syy.taskflowinsight.tracking.compare;

/**
 * policy、预算或证据发布边界的闭集；与problem code在类型上隔离。
 *
 * @since 4.0.0
 */
public enum CompareLimitationCode {
    /** 协作式检查发现请求deadline已到，后续业务回调不得开始。 */
    DEADLINE_REACHED("CMP_W_2101"),

    /** 继续下钻会超过当前请求允许的逻辑深度。 */
    DEPTH_LIMIT_REACHED("CMP_W_2102"),

    /** 下一容器成员会超过两侧共享的element预算。 */
    COLLECTION_LIMIT_REACHED("CMP_W_2103"),

    /** 结果仍有事实但明细容量不足，不能据空列表恢复为完整。 */
    RESULT_DETAIL_LIMIT_REACHED("CMP_W_2104"),

    /** 下一snapshot、diff或候选事件会超过共享node预算。 */
    NODE_BUDGET_REACHED("CMP_W_2105"),

    /** key或候选身份无法在有界事实内唯一解析，禁止覆盖或猜测配对。 */
    KEY_AMBIGUOUS("CMP_W_2201"),

    /** policy在比较计划运行前显式禁用能力。 */
    POLICY_DISABLED("CMP_W_3101");

    /** 对外schema稳定编码；enum名称可在不破坏wire合同的前提下演进。 */
    private final String wireCode;

    CompareLimitationCode(String wireCode) {
        this.wireCode = wireCode;
    }

    /**
     * 返回machine schema稳定编码，消费者不得使用enum ordinal作为协议值。
     *
     * @return 固定的{@code CMP_W_*}编码
     */
    public String wireCode() {
        return wireCode;
    }
}
