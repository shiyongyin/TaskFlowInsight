package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.exporter.change.CanonicalChangeJsonEncoder;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;

/**
 * 用Markdown代码块包装canonical masked JSON的诊断渲染器。
 *
 * <p>Markdown不解析path/value且不读取配置；escaping、redaction和ValueSnapshot wire全部来自同一projection。</p>
 *
 * @since 4.0.0
 */
public class MarkdownRenderer implements ChangeReportRenderer {

    /** 复用canonical JSON escaping，避免Markdown维护第二份值规则。 */
    private static final CanonicalChangeJsonEncoder ENCODER = new CanonicalChangeJsonEncoder();

    /**
     * 渲染已经脱敏的canonical projection。
     *
     * @param projection schema v1字段树，不允许为null
     * @return 包含同一compact JSON的Markdown文本
     */
    @Override
    public String render(CompareProjection projection) {
        return "# Compare Projection\n\n```json\n" + ENCODER.encode(projection) + "\n```\n";
    }
}
