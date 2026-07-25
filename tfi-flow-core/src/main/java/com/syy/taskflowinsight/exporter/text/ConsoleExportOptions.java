package com.syy.taskflowinsight.exporter.text;

import java.util.Objects;

/**
 * 控制台诊断文本的两个正交选项。
 *
 * <p>style 只选择 TREE/SIMPLE 投影，showTimestamp 只控制消息时间戳；{@link ConsoleExporter}
 * 使用值对象而不是 exporter 可变字段，避免共享实例在并发导出时互相污染。
 *
 * @param style 诊断文本样式，不可为 null
 * @param showTimestamp 是否显示消息时间戳
 * @since 4.0.0
 */
public record ConsoleExportOptions(
        ConsoleStyle style,
        boolean showTimestamp) {

    /**
     * 校验控制台布局必须显式指定，避免共享导出器静默回退到另一种格式。
     */
    public ConsoleExportOptions {
        Objects.requireNonNull(style, "style");
    }

    /**
     * 选择控制台诊断文本的布局；样式选择不影响消息时间戳是否出现。
     *
     * <p>{@link ConsoleExportOptions} 只用该枚举决定 TREE/SIMPLE 布局，捕获边界与时间戳开关保持不变。
     *
     * @since 4.0.0
     */
    public enum ConsoleStyle {
        /** 使用 emoji 与连接线表达任务树。 */
        TREE,
        /** 使用纯缩进表达任务层级。 */
        SIMPLE
    }
}
