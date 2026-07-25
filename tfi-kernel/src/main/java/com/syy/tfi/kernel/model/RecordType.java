package com.syy.tfi.kernel.model;

/**
 * Record 的稳定机器分类；具体语义由 code 和结构化 data 共同表达。
 */
public enum RecordType {
    /** 不改变 Stage 状态的人读或内核消息事实。 */
    MESSAGE,
    /** 显式变化事实；内置 MANUAL_CHANGE 使用 path/before/after，通用 record 可提供自定义结构。 */
    CHANGE,
    /** 会把所属 Stage 标记为 ERROR，并在关闭时向祖先传播的错误事实。 */
    ERROR
}
