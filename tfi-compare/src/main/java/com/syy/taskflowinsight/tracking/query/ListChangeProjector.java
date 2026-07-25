package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * List 容器事件投影器
 * <p>
 * 基于 CompareResult + 原始容器数据，按ordered-index生成结构化容器事件视图。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
public final class ListChangeProjector {

    private ListChangeProjector() {
    }

    /**
     * 投影 List 容器事件
     *
     * @param listResult    比较结果
     * @param left          左侧列表
     * @param right         右侧列表
     * @param opts          比较选项
     * @param containerPath 容器路径
     * @return 结构化事件列表
     */
    public static List<Map<String, Object>> project(
            CompareResult listResult,
            List<?> left,
            List<?> right,
            CompareOptions opts,
            String containerPath) {

        if (listResult == null) {
            return Collections.emptyList();
        }

        List<?> leftList = left != null ? left : Collections.emptyList();
        List<?> rightList = right != null ? right : Collections.emptyList();
        return projectSimple(leftList, rightList, containerPath);
    }

    /**
     * 普通List只按索引投影，不从显示层重新选择集合语义。
     */
    private static List<Map<String, Object>> projectSimple(
            List<?> left,
            List<?> right,
            String containerPath) {

        List<Map<String, Object>> events = new ArrayList<>();
        Instant timestamp = Instant.now();
        int maxSize = Math.max(left.size(), right.size());

        for (int i = 0; i < maxSize; i++) {
            Object leftVal = i < left.size() ? left.get(i) : null;
            Object rightVal = i < right.size() ? right.get(i) : null;

            if (leftVal == null && rightVal != null) {
                // entry_added
                events.add(createEvent("entry_added", containerPath, timestamp, i, null, rightVal));
            } else if (leftVal != null && rightVal == null) {
                // entry_removed
                events.add(createEvent("entry_removed", containerPath, timestamp, i, leftVal, null));
            } else if (leftVal != null && rightVal != null && !Objects.equals(leftVal, rightVal)) {
                // entry_updated
                events.add(createEvent("entry_updated", containerPath, timestamp, i, leftVal, rightVal));
            }
        }

        return events;
    }

    // ========== 辅助方法 ==========

    /**
     * 创建事件
     */
    private static Map<String, Object> createEvent(
            String kind,
            String containerPath,
            Instant timestamp,
            Integer index,
            Object oldValue,
            Object newValue) {

        Map<String, Object> event = new HashMap<>();
        event.put("kind", kind);
        event.put("object", "List");
        String path = (containerPath != null ? containerPath + "." : "") + PathUtils.buildListIndexPath(index);
        event.put("path", path);
        event.put("timestamp", timestamp);

        Map<String, Object> details = new HashMap<>();
        if (index != null) {
            details.put("index", index);
        }
        if (oldValue != null) {
            details.put("oldEntryValue", oldValue);
        }
        if (newValue != null) {
            details.put("newEntryValue", newValue);
        }
        if (newValue != null) {
            details.put("entryValue", newValue);
        } else if (oldValue != null) {
            details.put("entryValue", oldValue);
        }
        event.put("details", details);

        return event;
    }

}
