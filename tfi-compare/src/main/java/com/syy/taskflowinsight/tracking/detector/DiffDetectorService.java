package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.List;
import java.util.Map;

/**
 * 为兼容调用保留的无状态差异适配器。
 *
 * <p>Service 不再读取字段 annotation、配置容差或保存 comparator registry；Spring 与纯 Java
 * 调用都委托同一个 {@link DiffDetector}，因此容器环境不会产生第二套 equality graph。</p>
 *
 * @since 3.0.0
 */
public class DiffDetectorService {

    /**
     * 将两份旧快照交给 canonical runtime 比较。
     *
     * @param objectName 旧记录的对象上下文名
     * @param before 变更前快照，{@code null} 按空快照处理
     * @param after 变更后快照，{@code null} 按空快照处理
     * @return canonical 顺序的兼容变更记录
     */
    public List<ChangeRecord> diff(
            String objectName,
            Map<String, Object> before,
            Map<String, Object> after) {
        return DiffDetector.diff(objectName, before, after);
    }
}
