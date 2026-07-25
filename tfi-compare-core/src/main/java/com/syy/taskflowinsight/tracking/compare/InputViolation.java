package com.syy.taskflowinsight.tracking.compare;

/**
 * 比较调用在执行前可拒绝的输入类别闭集。
 *
 * <p>调用方应依赖该类型而不是解析异常消息；闭集同时阻止 option、extension 和 tracking
 * 边界各自发明不兼容的字符串错误码。</p>
 *
 * @since 4.0.0
 */
public enum InputViolation {
    /** 显式options缺失；无options重载应在调用前自行选择policy defaults。 */
    NULL_OPTIONS,
    /** 输入对象、batch或action之间的结构关系不满足调用合同。 */
    INVALID_INPUT_SHAPE,
    /** runtime policy越过framework hard ceiling或最低保留预算。 */
    POLICY_OUT_OF_RANGE,
    /** 单次options试图扩大当前runtime policy允许的语义或资源上限。 */
    OPTION_OUT_OF_RANGE,
    /** path pattern不符合有界segment grammar。 */
    INVALID_PATTERN,
    /** property selector无法唯一定位声明字段或指向禁止扩展的字段。 */
    INVALID_SELECTOR,
    /** algorithm id不符合版本化grammar或长度边界。 */
    INVALID_ALGORITHM_ID,
    /** runtime内出现重复selector、target class或algorithm id。 */
    DUPLICATE_EXTENSION,
    /** strategy与comparator注册总量超过policy hard ceiling。 */
    EXTENSION_LIMIT_EXCEEDED,
    /** tracking target、name、options或action不满足执行前合同。 */
    TRACKING_INPUT_INVALID
}
