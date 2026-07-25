package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.render.RenderOptions;

/**
 * canonical projection的typed诊断渲染SPI。
 *
 * <p>SPI不接收raw result、业务对象或弱类型style，provider因此无法绕过projection安全边界。</p>
 *
 * @since 4.0.0
 */
public interface RenderProvider extends PrioritizedProvider {

    /**
     * 按不可变布局选项渲染已脱敏projection。
     *
     * @param projection schema v1字段树，不允许为null
     * @param options 只选择Markdown或Console外壳的不可变选项
     * @return 对应诊断文本
     */
    String render(CompareProjection projection, RenderOptions options);
}
