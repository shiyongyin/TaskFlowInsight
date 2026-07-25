package com.syy.taskflowinsight.tracking.render;

import java.util.Objects;

/**
 * 单次诊断渲染的不可变布局选择。
 *
 * <p>该选项只在Markdown与Console之间选择展示外壳，不能选择projection字段、改变脱敏或重写值表示。</p>
 *
 * @param layout 诊断文本布局，不允许为null
 * @since 4.0.0
 */
public record RenderOptions(Layout layout) {

    /**
     * 保留诊断格式的闭集；调用方不得依赖ordinal做持久化协议。
     *
     * @since 4.0.0
     */
    public enum Layout {
        /** 使用Markdown代码块包装canonical JSON。 */
        MARKDOWN,

        /** 使用稳定行式文本展示同一canonical tree。 */
        CONSOLE
    }

    /**
     * 拒绝无布局的隐式fallback，避免同一次调用因入口不同选择不同格式。
     */
    public RenderOptions {
        Objects.requireNonNull(layout, "layout");
    }

    /**
     * 返回默认Markdown布局。
     *
     * @return 不可变Markdown选项
     */
    public static RenderOptions markdown() {
        return new RenderOptions(Layout.MARKDOWN);
    }

    /**
     * 返回Console布局。
     *
     * @return 不可变Console选项
     */
    public static RenderOptions console() {
        return new RenderOptions(Layout.CONSOLE);
    }

    /**
     * 返回唯一默认布局，避免各入口复制默认值。
     *
     * @return 与{@link #markdown()}相同的不可变选项
     */
    public static RenderOptions defaults() {
        return markdown();
    }
}
