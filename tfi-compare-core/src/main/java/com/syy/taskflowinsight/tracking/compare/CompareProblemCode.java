package com.syy.taskflowinsight.tracking.compare;

/**
 * 非预期能力故障的闭集；wire code是schema合同，enum名称不对外。
 *
 * @since 4.0.0
 */
public enum CompareProblemCode {
    /** entity key违反descriptor或有界编码合同。 */
    ENTITY_KEY_INVALID("CMP_E_1101"),

    /** 同一类型的descriptor事实相互冲突，无法构造唯一计划。 */
    TYPE_DESCRIPTOR_CONFLICT("CMP_E_1102"),

    /** snapshot能力在捕获对象图时发生非预期失败。 */
    SNAPSHOT_FAILED("CMP_E_2001"),

    /** diff能力无法依据现有有界事实完成安全判定。 */
    DIFF_FAILED("CMP_E_2002"),

    /** Java模块或运行时访问规则拒绝读取目标字段。 */
    REFLECTION_ACCESS_DENIED("CMP_E_2003"),

    /** Registry或provider未能提供可执行的比较能力。 */
    PROVIDER_UNAVAILABLE("CMP_E_3001"),

    /** tracking阶段无法完成baseline或after事实捕获。 */
    TRACKING_CAPTURE_FAILED("CMP_E_4001"),

    /** 内核检测到不应由合法调用构造出的状态组合。 */
    INTERNAL_INVARIANT_VIOLATION("CMP_E_9001");

    /** 对外schema稳定编码，避免暴露实现异常类型或不稳定message。 */
    private final String wireCode;

    CompareProblemCode(String wireCode) {
        this.wireCode = wireCode;
    }

    /**
     * 返回machine schema稳定编码，避免把实现异常类名暴露给消费者。
     *
     * @return 固定的{@code CMP_E_*}编码
     */
    public String wireCode() {
        return wireCode;
    }
}
