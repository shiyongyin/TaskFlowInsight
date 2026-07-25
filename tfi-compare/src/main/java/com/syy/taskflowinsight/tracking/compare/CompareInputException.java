package com.syy.taskflowinsight.tracking.compare;

import java.util.Objects;

/**
 * 在 provider 或比较算法执行前报告 typed 输入拒绝。
 *
 * <p>消息只来自固定目录，不拼接原始 option、路径、类型成员或业务值；机器调用方通过
 * {@link #violation()} 获取稳定分类。</p>
 *
 * @since 4.0.0
 */
public final class CompareInputException extends IllegalArgumentException {

    /** 输入拒绝统一使用一个wire code，具体原因由typed violation表达。 */
    private static final String CODE = "CMP_E_1001";
    /** 稳定机器分类；异常消息只承担安全的人读提示。 */
    private final InputViolation violation;

    /**
     * 创建固定安全消息的输入异常。
     *
     * @param violation 输入拒绝类别，不能为空
     */
    public CompareInputException(InputViolation violation) {
        super(messageFor(Objects.requireNonNull(violation, "violation")));
        this.violation = violation;
    }

    /**
     * 返回执行前输入错误的稳定wire code。
     *
     * @return 固定的{@code CMP_E_1001}
     */
    public String code() {
        return CODE;
    }

    /**
     * 返回无需解析消息的typed拒绝原因。
     *
     * @return 输入拒绝类别
     */
    public InputViolation violation() {
        return violation;
    }

    private static String messageFor(InputViolation violation) {
        return switch (violation) {
            case NULL_OPTIONS -> "compare options must be provided";
            case INVALID_INPUT_SHAPE -> "compare input shape is invalid";
            case POLICY_OUT_OF_RANGE -> "compare policy is outside the supported range";
            case OPTION_OUT_OF_RANGE -> "compare options exceed the active policy";
            case INVALID_PATTERN -> "compare path pattern is invalid";
            case INVALID_SELECTOR -> "compare property selector is invalid";
            case INVALID_ALGORITHM_ID -> "compare algorithm identifier is invalid";
            case DUPLICATE_EXTENSION -> "compare extension registration is duplicated";
            case EXTENSION_LIMIT_EXCEEDED -> "compare extension registration limit is exceeded";
            case TRACKING_INPUT_INVALID -> "compare tracking input is invalid";
        };
    }
}
