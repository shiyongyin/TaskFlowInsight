package com.syy.taskflowinsight.internal;

/**
 * Flow-core 自有配置默认值。
 *
 * <p>仅承载 session/context/export 等 flow 内核关注的默认值，避免运行时代码依赖
 * compare/tracking 配置集合。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.1
 */
public final class FlowConfigDefaults {

    /**
     * 导出器遍历任务树的最大深度。
     *
     * <p>模型层不限制树深度，导出器递归/迭代遍历必须设上限，
     * 防止异常构造的深树把 StackOverflowError 抛入用户线程。超限子树被截断并标记。
     */
    public static final int MAX_EXPORT_DEPTH = 1000;

    /** 一次导出允许纳入不可变快照的最大任务节点数。 */
    public static final int MAX_EXPORT_NODES = 100_000;

    /** 一次导出允许纳入的 message、attribute 与 tag 合计条目数。 */
    public static final int MAX_EXPORT_PAYLOAD_ENTRIES = 1_000_000;

    /** 一次导出的 callback-free 文本估算上限，单位为 UTF-16 code unit。 */
    public static final long MAX_EXPORT_TEXT_CHARS = 10_000_000L;

    /**
     * 单个任务节点的消息数量软上限。
     *
     * <p>超限后丢弃新消息并追加一条哨兵告警，防止长驻会话下 messages 列表无界增长导致 OOM。
     */
    public static final int MAX_MESSAGES_PER_NODE = 10_000;

    private FlowConfigDefaults() {
        // Utility class.
    }
}
