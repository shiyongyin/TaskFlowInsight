package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderOptions;

import java.util.Objects;

/**
 * 复用目标Markdown/Console formatter的默认typed渲染Provider。
 *
 * <p>实现不捕获异常或返回伪成功降级文本；输入错误和formatter失败保持原语义交给调用边界处理。</p>
 *
 * @since 4.0.0
 */
public class DefaultRenderProvider implements RenderProvider {

    /** Markdown布局的唯一目标formatter。 */
    private final MarkdownRenderer markdownRenderer;

    /** Console布局的唯一目标formatter。 */
    private final ChangeConsoleExporter consoleExporter;

    /**
     * 使用无状态目标formatter创建默认provider。
     */
    public DefaultRenderProvider() {
        this.markdownRenderer = new MarkdownRenderer();
        this.consoleExporter = new ChangeConsoleExporter();
    }

    /**
     * 按闭集布局渲染同一prebuilt projection。
     *
     * @param projection schema v1字段树，不允许为null
     * @param options 只选择诊断外壳的不可变选项
     * @return Markdown或Console文本
     */
    @Override
    public String render(CompareProjection projection, RenderOptions options) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(options, "options");
        return switch (options.layout()) {
            case MARKDOWN -> markdownRenderer.render(projection);
            case CONSOLE -> consoleExporter.format(projection);
        };
    }

    /**
     * 返回默认provider优先级，不覆盖显式注册实现。
     *
     * @return 固定0
     */
    @Override
    public int priority() {
        return 0;
    }

    /**
     * 只输出结构身份，不展开projection内容。
     *
     * @return 安全摘要
     */
    @Override
    public String toString() {
        return "DefaultRenderProvider{priority=0, layouts=MARKDOWN|CONSOLE}";
    }
}
