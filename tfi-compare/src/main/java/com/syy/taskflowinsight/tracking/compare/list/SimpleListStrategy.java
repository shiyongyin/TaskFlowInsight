package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import org.springframework.stereotype.Component;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerElementEvent;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerType;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ElementOperation;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 简单列表比较策略
 * 基于位置进行比较，不支持移动检测
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component("simpleListStrategy")
public class SimpleListStrategy implements ListCompareStrategy {
    
    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }
        
        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }
        
        List<FieldChange> changes = new ArrayList<>();
        
        int minSize = Math.min(list1.size(), list2.size());
        
        // 比较共同位置的元素
        for (int i = 0; i < minSize; i++) {
            Object item1 = list1.get(i);
            Object item2 = list2.get(i);
            
            if (!Objects.equals(item1, item2)) {
                // 受控深度对比：仅当启用深度对比时，展开为子字段级变更，填充 propertyPath
                if (options.isEnableDeepCompare() && item1 != null && item2 != null) {
                    changes.addAll(deepCompareListElement(i, item1, item2));
                } else {
                    changes.add(FieldChange.builder()
                        .fieldName("[" + i + "]")
                        .oldValue(item1)
                        .newValue(item2)
                        .changeType(ChangeType.UPDATE)
                        .elementEvent(
                            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                                .listModify(i, resolveEntityKey(item1, item2), null)
                        )
                        .build());
                }
            }
        }
        
        // 处理新增元素
        for (int i = minSize; i < list2.size(); i++) {
            Object newVal = list2.get(i);
            changes.add(FieldChange.builder()
                .fieldName("[" + i + "]")
                .oldValue(null)
                .newValue(newVal)
                .changeType(ChangeType.CREATE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listAdd(i, resolveEntityKey(null, newVal))
                )
                .build());
        }
        
        // 处理删除元素
        for (int i = minSize; i < list1.size(); i++) {
            Object oldVal = list1.get(i);
            changes.add(FieldChange.builder()
                .fieldName("[" + i + "]")
                .oldValue(oldVal)
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listRemove(i, resolveEntityKey(oldVal, null))
                )
                .build());
        }
        
        return CompareResult.builder()
            .object1(list1)
            .object2(list2)
            .changes(changes)
            .identical(changes.isEmpty())
            .algorithmUsed("SIMPLE")
            .build();
    }
    
    @Override
    public boolean supportsMoveDetection() {
        return false; // SIMPLE策略不支持移动检测
    }
    
    @Override
    public String getStrategyName() {
        return "SIMPLE";
    }
    
    @Override
    public int getMaxRecommendedSize() {
        return Integer.MAX_VALUE; // SIMPLE策略无大小限制
    }

    private String resolveEntityKey(Object oldVal, Object newVal) {
        Object pick = newVal != null ? newVal : oldVal;
        if (pick == null) return null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(pick);
        return EntityKeyUtils.UNRESOLVED.equals(key) ? null : key;
    }

    /**
     * 深度对比列表元素，输出子字段级 MODIFY 变更（带 propertyPath）。
     */
    private java.util.List<FieldChange> deepCompareListElement(int index, Object v1, Object v2) {
        java.util.List<FieldChange> out = new java.util.ArrayList<>();
        try {
            com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig config = new com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig();
            config.setMaxDepth(10);
            com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep snapshot =
                new com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep(config);
            java.util.Set<String> empty = java.util.Collections.emptySet();
            java.util.Map<String,Object> s1 = snapshot.captureDeep(v1, 10, empty, empty);
            java.util.Map<String,Object> s2 = snapshot.captureDeep(v2, 10, empty, empty);
            String prefix = PathUtils.buildListIndexPath(index);
            java.util.List<com.syy.taskflowinsight.tracking.model.ChangeRecord> records =
                com.syy.taskflowinsight.tracking.detector.DiffFacade.diff(prefix, s1, s2);
            for (com.syy.taskflowinsight.tracking.model.ChangeRecord rec : records) {
                FieldChange fc = FieldChange.builder()
                    .fieldName("[" + index + "]" + "." + rec.getFieldName())
                    .fieldPath(prefix + "." + rec.getFieldName())
                    .oldValue(rec.getOldValue())
                    .newValue(rec.getNewValue())
                    .changeType(rec.getChangeType())
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listModify(index, resolveEntityKey(v1, v2), rec.getFieldName())
                    )
                    .build();
                out.add(fc);
            }
        } catch (Exception e) {
            // 失败回退为整元素 UPDATE（不带 propertyPath）
            out.add(FieldChange.builder()
                .fieldName("[" + index + "]")
                .oldValue(v1)
                .newValue(v2)
                .changeType(ChangeType.UPDATE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listModify(index, resolveEntityKey(v1, v2), null)
                )
                .build());
        }
        return out;
    }
}
