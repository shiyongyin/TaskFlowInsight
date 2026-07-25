package com.syy.taskflowinsight.enums;

/**
 * 消息严重程度，用于表达 INFO、DEBUG、WARN、ERROR 的技术语义。
 *
 * <p>{@link MessageType} 描述消息所属的业务类别，severity 描述同一类别内的严重程度；
 * 二者分开存储可避免继续用内容前缀编码语义。该字段仅属于 Java model，V1 导出契约不增加字段。
 *
 * @since 4.0.0
 */
public enum MessageSeverity {
    /** 常规业务状态，不表示诊断异常。 */
    INFO,
    /** 仅用于调试定位的细节。 */
    DEBUG,
    /** 可继续执行但需要关注的异常状态。 */
    WARN,
    /** 当前操作已经失败的错误状态。 */
    ERROR
}
