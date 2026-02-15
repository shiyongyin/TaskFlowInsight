package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * 数组比较策略（产出 ContainerType.ARRAY 的容器事件）
 *
 * 策略说明：
 * - 位置对齐比较，按索引输出 MODIFY/ADD/REMOVE（不做移动合并）
 * - 支持任意数组类型（包含原始类型数组），通过反射访问元素
 * - 生成的 FieldChange 带有 elementEvent（containerType=ARRAY, index, operation）
 */
public class ArrayCompareStrategy implements CompareStrategy<Object> {

    @Override
    public CompareResult compare(Object arr1, Object arr2, CompareOptions options) {
        if (arr1 == arr2) {
            return CompareResult.identical();
        }
        if (arr1 == null || arr2 == null) {
            return CompareResult.ofNullDiff(arr1, arr2);
        }
        if (!arr1.getClass().isArray() || !arr2.getClass().isArray()) {
            return CompareResult.ofTypeDiff(arr1, arr2);
        }

        int len1 = Array.getLength(arr1);
        int len2 = Array.getLength(arr2);
        int min = Math.min(len1, len2);

        List<FieldChange> changes = new ArrayList<>();

        // 比较共同索引的元素
        for (int i = 0; i < min; i++) {
            Object v1 = Array.get(arr1, i);
            Object v2 = Array.get(arr2, i);
            if (!java.util.Objects.equals(v1, v2)) {
                changes.add(FieldChange.builder()
                    .fieldName("[" + i + "]")
                    .oldValue(v1)
                    .newValue(v2)
                    .changeType(ChangeType.UPDATE)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents.arrayModify(i)
                    )
                    .build());
            }
        }

        // 新增元素（arr2 更长）
        for (int i = min; i < len2; i++) {
            Object v2 = Array.get(arr2, i);
            changes.add(FieldChange.builder()
                .fieldName("[" + i + "]")
                .oldValue(null)
                .newValue(v2)
                .changeType(ChangeType.CREATE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents.arrayAdd(i)
                )
                .build());
        }

        // 删除元素（arr1 更长）
        for (int i = min; i < len1; i++) {
            Object v1 = Array.get(arr1, i);
            changes.add(FieldChange.builder()
                .fieldName("[" + i + "]")
                .oldValue(v1)
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents.arrayRemove(i)
                )
                .build());
        }

        return CompareResult.builder()
            .object1(arr1)
            .object2(arr2)
            .changes(changes)
            .identical(changes.isEmpty())
            .algorithmUsed("ARRAY_SIMPLE")
            .build();
    }

    @Override
    public String getName() {
        return "ArrayCompare";
    }

    @Override
    public boolean supports(Class<?> type) {
        return type != null && type.isArray();
    }
}
