package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Map/Set 容器事件投影器
 * <p>
 * 基于 CompareResult + 原始容器数据，生成结构化容器事件视图。
 * </p>
 * <ul>
 *   <li>Map: key 新增/删除 → entry_added/removed; 同 key 值变化 → entry_updated</li>
 *   <li>Set: 按 equals/hash 判断新增/删除; keyOrIndex 用稳定摘要（EntityKey 优先）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
public final class MapSetEntryProjector {

    private MapSetEntryProjector() {
    }

    /**
     * 投影 Map 容器事件
     *
     * @param mapResult     比较结果
     * @param left          左侧 Map
     * @param right         右侧 Map
     * @param containerPath 容器路径
     * @return 结构化事件列表
     */
    public static List<Map<String, Object>> projectMap(
            CompareResult mapResult,
            Map<?, ?> left,
            Map<?, ?> right,
            String containerPath) {

        Map<?, ?> leftMap = left != null ? left : Collections.emptyMap();
        Map<?, ?> rightMap = right != null ? right : Collections.emptyMap();

        List<Map<String, Object>> events = new ArrayList<>();
        Instant timestamp = Instant.now();

        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(leftMap.keySet());
        allKeys.addAll(rightMap.keySet());

        for (Object key : allKeys) {
            Object leftVal = leftMap.get(key);
            Object rightVal = rightMap.get(key);

            String keyStr = String.valueOf(key);
            String path = (containerPath != null ? containerPath + "." : "")
                    + PathUtils.buildMapValuePath(keyStr);

            if (leftVal == null && rightVal != null) {
                // entry_added
                events.add(createMapEvent("entry_added", path, timestamp, key, null, rightVal));
            } else if (leftVal != null && rightVal == null) {
                // entry_removed
                events.add(createMapEvent("entry_removed", path, timestamp, key, leftVal, null));
            } else if (leftVal != null && rightVal != null && !Objects.equals(leftVal, rightVal)) {
                // entry_updated
                events.add(createMapEvent("entry_updated", path, timestamp, key, leftVal, rightVal));
            }
        }

        return events;
    }

    /**
     * 投影 Set 容器事件
     *
     * @param setResult     比较结果
     * @param left          左侧 Set
     * @param right         右侧 Set
     * @param containerPath 容器路径
     * @return 结构化事件列表
     */
    public static List<Map<String, Object>> projectSet(
            CompareResult setResult,
            Set<?> left,
            Set<?> right,
            String containerPath) {

        Set<?> leftSet = left != null ? left : Collections.emptySet();
        Set<?> rightSet = right != null ? right : Collections.emptySet();

        List<Map<String, Object>> events = new ArrayList<>();
        Instant timestamp = Instant.now();

        // 新增元素
        for (Object item : rightSet) {
            if (!leftSet.contains(item)) {
                String keyOrIndex = computeStableKey(item);
                String path = containerPath != null ? containerPath : "Set";
                events.add(createSetEvent("entry_added", path, timestamp, keyOrIndex, item));
            }
        }

        // 删除元素
        for (Object item : leftSet) {
            if (!rightSet.contains(item)) {
                String keyOrIndex = computeStableKey(item);
                String path = containerPath != null ? containerPath : "Set";
                events.add(createSetEvent("entry_removed", path, timestamp, keyOrIndex, item));
            }
        }

        return events;
    }

    // ========== 辅助方法 ==========

    /**
     * 创建 Map 事件
     */
    private static Map<String, Object> createMapEvent(
            String kind,
            String path,
            Instant timestamp,
            Object mapKey,
            Object oldValue,
            Object newValue) {

        Map<String, Object> event = new HashMap<>();
        event.put("kind", kind);
        event.put("object", "Map");
        event.put("path", path);
        event.put("timestamp", timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("keyOrIndex", mapKey);
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

    /**
     * 创建 Set 事件
     */
    private static Map<String, Object> createSetEvent(
            String kind,
            String path,
            Instant timestamp,
            String keyOrIndex,
            Object entryValue) {

        Map<String, Object> event = new HashMap<>();
        event.put("kind", kind);
        event.put("object", "Set");
        event.put("path", path);
        event.put("timestamp", timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("keyOrIndex", keyOrIndex);
        details.put("entryValue", entryValue);
        event.put("details", details);

        return event;
    }

    /**
     * 计算稳定键（优先 EntityKey，其次字符串摘要）
     */
    private static String computeStableKey(Object item) {
        if (item == null) {
            return "null";
        }

        String entityKey = EntityKeyUtils.computeStableKeyOrUnresolved(item);
        if (!EntityKeyUtils.UNRESOLVED.equals(entityKey)) {
            return entityKey;
        }

        // 非 Entity，使用字符串表示
        return String.valueOf(item);
    }
}

