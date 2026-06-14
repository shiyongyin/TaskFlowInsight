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

    /** 嵌套 stage 最大深度。 */
    public static final int NESTED_STAGE_MAX_DEPTH = 20;

    /** 嵌套 stage 批量清理上限。 */
    public static final int NESTED_CLEANUP_BATCH_SIZE = 100;

    private FlowConfigDefaults() {
        // Utility class.
    }
}
