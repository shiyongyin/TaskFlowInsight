package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.springframework.stereotype.Component;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerElementEvent;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerType;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ElementOperation;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;

import java.util.*;

/**
 * 无序列表比较策略（AS_SET）
 * 将列表视为Set进行比较，忽略元素顺序，仅输出CREATE/DELETE操作
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component("asSetListStrategy")
public class AsSetListStrategy implements ListCompareStrategy {
    
    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }
        
        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }
        
        // 转换为Set进行无序比较
        Set<Object> set1 = new HashSet<>(list1);
        Set<Object> set2 = new HashSet<>(list2);
        
        List<FieldChange> changes = new ArrayList<>();
        
        // 找出删除的元素
        Set<Object> deleted = new HashSet<>(set1);
        deleted.removeAll(set2);
        for (Object item : deleted) {
            int index = list1.indexOf(item); // 使用原始位置
            changes.add(FieldChange.builder()
                .fieldName("[" + index + "]")
                .oldValue(item)
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listRemove(index, resolveEntityKey(item))
                )
                .build());
        }
        
        // 找出新增的元素
        Set<Object> added = new HashSet<>(set2);
        added.removeAll(set1);
        for (Object item : added) {
            int index = list2.indexOf(item); // 使用新位置
            changes.add(FieldChange.builder()
                .fieldName("[" + index + "]")
                .oldValue(null)
                .newValue(item)
                .changeType(ChangeType.CREATE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listAdd(index, resolveEntityKey(item))
                )
                .build());
        }
        
        // 按照下标排序，确保输出顺序一致
        // 相同索引时，DELETE在前，CREATE在后
        changes.sort((a, b) -> {
            Integer indexA = extractIndex(a.getFieldName());
            Integer indexB = extractIndex(b.getFieldName());
            if (indexA != null && indexB != null) {
                int cmp = indexA.compareTo(indexB);
                if (cmp != 0) {
                    return cmp;
                }
                // 相同索引，按操作类型排序：DELETE < UPDATE < CREATE
                return getChangeTypePriority(a.getChangeType()) -
                       getChangeTypePriority(b.getChangeType());
            }
            return a.getFieldName().compareTo(b.getFieldName());
        });

        return CompareResult.builder()
            .object1(list1)
            .object2(list2)
            .changes(changes)
            .identical(changes.isEmpty())
            .algorithmUsed("AS_SET")
            .build();
    }
    
    /**
     * 从字段名中提取索引
     * 例如: "[1]" -> 1
     */
    private Integer extractIndex(String fieldName) {
        if (fieldName != null && fieldName.startsWith("[") && fieldName.endsWith("]")) {
            try {
                return Integer.parseInt(fieldName.substring(1, fieldName.length() - 1));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        return null;
    }

    /**
     * 获取变更类型的优先级（用于排序）
     * DELETE < UPDATE < CREATE
     */
    private int getChangeTypePriority(ChangeType type) {
        switch (type) {
            case DELETE:
                return 1;
            case UPDATE:
                return 2;
            case CREATE:
                return 3;
            case MOVE:
                return 4;
            default:
                return 5;
        }
    }

    @Override
    public boolean supportsMoveDetection() {
        return false; // AS_SET策略忽略顺序，不输出移动
    }
    
    @Override
    public String getStrategyName() {
        return "AS_SET";
    }
    
    @Override
    public int getMaxRecommendedSize() {
        return 10000; // Set操作相对高效
    }

    private String resolveEntityKey(Object val) {
        if (val == null) return null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(val);
        return EntityKeyUtils.UNRESOLVED.equals(key) ? null : key;
    }
}
