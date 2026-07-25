package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.projection.CompareProjection;

import java.util.Map;

/**
 * canonical Compare projection的深度不可修改Map导出入口。
 *
 * <p>该类型只编码prebuilt projection，不保留旧分组、统计或可变配置路径。</p>
 *
 * @since 4.0.0
 */
public class ChangeMapExporter {

    /** schema与容器冻结逻辑由唯一无状态encoder拥有。 */
    private static final CanonicalChangeMapEncoder ENCODER = new CanonicalChangeMapEncoder();

    private ChangeMapExporter() {
    }

    /**
     * 编码已经脱敏的canonical projection。
     *
     * @param projection schema v1字段树，不允许为null
     * @return 所有嵌套Map/List均不可修改的有序tree
     */
    public static Map<String, Object> export(CompareProjection projection) {
        return ENCODER.encode(projection);
    }
}
