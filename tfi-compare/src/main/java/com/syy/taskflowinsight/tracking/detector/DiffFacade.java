package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.List;
import java.util.Map;

/**
 * 旧静态差异入口的无状态兼容门面。
 *
 * <p>该类型只保留调用形状，不再发现 Spring Bean、保存线程状态或选择 fallback graph。
 * 所有请求直接进入 {@link DiffDetector}，避免同一调用因运行环境不同而获得不同语义。</p>
 *
 * @since 3.0.0
 */
public final class DiffFacade {

    private DiffFacade() {
    }

    /**
     * 通过唯一兼容适配器比较两份旧快照。
     *
     * @param objectName 记录中使用的对象上下文名
     * @param before 变更前快照，{@code null} 按空快照处理
     * @param after 变更后快照，{@code null} 按空快照处理
     * @return 按 canonical path 顺序映射的旧变更记录
     */
    public static List<ChangeRecord> diff(
            String objectName,
            Map<String, Object> before,
            Map<String, Object> after) {
        return DiffDetector.diff(objectName, before, after);
    }
}
