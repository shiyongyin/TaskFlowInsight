package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import java.util.List;

/**
 * 详细比较策略接口
 * 支持返回ChangeRecord列表，用于集成到DiffDetector
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public interface DetailedCompareStrategy<T> extends CompareStrategy<T> {

    /**
     * 生成详细的变更记录列表
     *
     * @param objectName 对象名称
     * @param fieldName 字段名称
     * @param oldValue 旧值
     * @param newValue 新值
     * @param sessionId 会话ID
     * @param taskPath 任务路径
     * @return 详细的变更记录列表
     */
    List<ChangeRecord> generateDetailedChangeRecords(
        String objectName,
        String fieldName,
        T oldValue,
        T newValue,
        String sessionId,
        String taskPath
    );

    /**
     * 判断是否需要生成详细变更记录
     *
     * @param oldValue 旧值
     * @param newValue 新值
     * @return 如果需要生成详细变更记录返回true
     */
    default boolean needsDetailedChanges(T oldValue, T newValue) {
        // 默认情况下，如果值不相同就需要详细变更
        CompareResult result = compare(oldValue, newValue, CompareOptions.builder().build());
        return !result.isIdentical() && !result.getChanges().isEmpty();
    }
}