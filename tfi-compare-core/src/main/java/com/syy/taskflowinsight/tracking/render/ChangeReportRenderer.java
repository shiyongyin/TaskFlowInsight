package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;

/**
 * 已脱敏canonical projection的诊断渲染边界。
 *
 * <p>接口刻意不接收raw result或布局弱类型；跨格式选择只由{@link RenderOptions}在provider边界完成。</p>
 *
 * @since 4.0.0
 */
public interface ChangeReportRenderer {

    /**
     * 渲染prebuilt projection，不重建schema或masking。
     *
     * @param projection schema v1字段树，不允许为null
     * @return 诊断文本
     */
    String render(CompareProjection projection);
}
