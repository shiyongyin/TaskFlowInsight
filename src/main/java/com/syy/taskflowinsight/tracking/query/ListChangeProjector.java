package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
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
 * List 容器事件投影器
 * <p>
 * 基于 CompareResult + 原始容器数据，生成结构化容器事件视图。
 * 依据算法类型路由：SIMPLE/AS_SET/LCS/LEVENSHTEIN/ENTITY
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
public final class ListChangeProjector {

    private static final String SIMPLE = "SIMPLE";
    private static final String AS_SET = "AS_SET";
    private static final String LCS = "LCS";
    private static final String LEVENSHTEIN = "LEVENSHTEIN";
    private static final String ENTITY = "ENTITY";

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
        String algorithm = listResult.getAlgorithmUsed();

        if (algorithm == null) {
            // 降级为 SIMPLE
            return projectSimple(leftList, rightList, containerPath);
        }

        return switch (algorithm) {
            case SIMPLE, AS_SET -> projectSimple(leftList, rightList, containerPath);
            case LCS, LEVENSHTEIN -> projectLcs(leftList, rightList, containerPath, opts);
            case ENTITY -> projectEntity(leftList, rightList, containerPath, listResult);
            default -> projectSimple(leftList, rightList, containerPath);
        };
    }

    /**
     * SIMPLE/AS_SET：基于索引与集合差/交集
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

    /**
     * LCS/LEVENSHTEIN：基于编辑脚本生成事件
     * <p>
     * 当 detectMoves=true 时，将可配对的 D/I 合并为 entry_moved
     * </p>
     */
    private static List<Map<String, Object>> projectLcs(
            List<?> left,
            List<?> right,
            String containerPath,
            CompareOptions opts) {

        List<Map<String, Object>> events = new ArrayList<>();
        Instant timestamp = Instant.now();

        // 简化实现：基于索引比较生成初步事件
        int maxSize = Math.max(left.size(), right.size());
        for (int i = 0; i < maxSize; i++) {
            Object leftVal = i < left.size() ? left.get(i) : null;
            Object rightVal = i < right.size() ? right.get(i) : null;

            if (leftVal == null && rightVal != null) {
                events.add(createEvent("entry_added", containerPath, timestamp, i, null, rightVal));
            } else if (leftVal != null && rightVal == null) {
                events.add(createEvent("entry_removed", containerPath, timestamp, i, leftVal, null));
            } else if (leftVal != null && rightVal != null && !Objects.equals(leftVal, rightVal)) {
                events.add(createEvent("entry_updated", containerPath, timestamp, i, leftVal, rightVal));
            }
        }

        // 移动检测
        if (opts != null && opts.isDetectMoves()) {
            detectAndMergeMoves(events, left, right, containerPath, timestamp);
        }

        return events;
    }

    /**
     * ENTITY：基于 EntityKey 对齐
     * <p>
     * 重复键使用 #idx 后缀
     * </p>
     */
    private static List<Map<String, Object>> projectEntity(
            List<?> left,
            List<?> right,
            String containerPath,
            CompareResult result) {

        List<Map<String, Object>> events = new ArrayList<>();
        Instant timestamp = Instant.now();
        Set<String> duplicateKeys = result.getDuplicateKeys();

        // 构建 Entity 键映射
        Map<String, Object> leftMap = buildEntityKeyMap(left);
        Map<String, Object> rightMap = buildEntityKeyMap(right);

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(leftMap.keySet());
        allKeys.addAll(rightMap.keySet());

        for (String key : allKeys) {
            Object leftVal = leftMap.get(key);
            Object rightVal = rightMap.get(key);

            String path = containerPath != null ? containerPath + "." : "";
            boolean isDuplicate = duplicateKeys != null && duplicateKeys.contains(key);

            if (isDuplicate) {
                // 重复键场景：使用索引后缀
                int idx = 0; // 简化处理
                path += PathUtils.buildEntityPath(key + "#" + idx);
            } else {
                path += PathUtils.buildEntityPath(key);
            }

            if (leftVal == null && rightVal != null) {
                Map<String, Object> event = createEventWithEntityKey("entry_added", path, timestamp,
                        null, null, rightVal, key);
                events.add(event);
            } else if (leftVal != null && rightVal == null) {
                Map<String, Object> event = createEventWithEntityKey("entry_removed", path, timestamp,
                        null, leftVal, null, key);
                events.add(event);
            } else if (leftVal != null && rightVal != null && !Objects.equals(leftVal, rightVal)) {
                Map<String, Object> event = createEventWithEntityKey("entry_updated", path, timestamp,
                        null, leftVal, rightVal, key);
                events.add(event);
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

    /**
     * 创建带 EntityKey 的事件
     */
    private static Map<String, Object> createEventWithEntityKey(
            String kind,
            String path,
            Instant timestamp,
            Integer index,
            Object oldValue,
            Object newValue,
            String entityKey) {

        Map<String, Object> event = new HashMap<>();
        event.put("kind", kind);
        event.put("object", "List");
        event.put("path", path);
        event.put("timestamp", timestamp);

        Map<String, Object> details = new HashMap<>();
        if (entityKey != null) {
            details.put("entityKey", entityKey);
        }
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

    /**
     * 移动检测：将可配对的 added/removed 合并为 moved
     */
    private static void detectAndMergeMoves(
            List<Map<String, Object>> events,
            List<?> left,
            List<?> right,
            String containerPath,
            Instant timestamp) {

        // 构建右侧值索引
        Map<Object, Integer> rightIndex = new HashMap<>();
        for (int i = 0; i < right.size(); i++) {
            Object val = right.get(i);
            if (val != null) {
                rightIndex.putIfAbsent(val, i);
            }
        }

        // 查找可配对的移动
        List<Map<String, Object>> moves = new ArrayList<>();
        List<Map<String, Object>> toRemove = new ArrayList<>();

        for (Map<String, Object> event : events) {
            if ("entry_removed".equals(event.get("kind"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) event.get("details");
                Object entryValue = details.get("entryValue");
                Integer targetIndex = rightIndex.get(entryValue);

                if (targetIndex != null) {
                    // 找到移动
                    Integer fromIndex = (Integer) details.get("index");
                    Map<String, Object> moveEvent = createMoveEvent(
                            containerPath, timestamp, fromIndex, targetIndex, entryValue);
                    moves.add(moveEvent);
                    toRemove.add(event);

                    // 移除对应的 added 事件
                    events.removeIf(e -> {
                        if ("entry_added".equals(e.get("kind"))) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> eDetails = (Map<String, Object>) e.get("details");
                            return Objects.equals(eDetails.get("entryValue"), entryValue);
                        }
                        return false;
                    });
                }
            }
        }

        events.removeAll(toRemove);
        events.addAll(moves);
    }

    /**
     * 创建移动事件
     */
    private static Map<String, Object> createMoveEvent(
            String containerPath,
            Instant timestamp,
            Integer fromIndex,
            Integer toIndex,
            Object entryValue) {

        Map<String, Object> event = new HashMap<>();
        event.put("kind", "entry_moved");
        event.put("object", "List");
        String path = (containerPath != null ? containerPath + "." : "") + PathUtils.buildListIndexPath(toIndex);
        event.put("path", path);
        event.put("timestamp", timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("fromIndex", fromIndex);
        details.put("toIndex", toIndex);
        details.put("entryValue", entryValue);
        event.put("details", details);

        return event;
    }

    /**
     * 构建 Entity 键映射
     */
    private static Map<String, Object> buildEntityKeyMap(List<?> list) {
        Map<String, Object> map = new HashMap<>();
        if (list == null) {
            return map;
        }

        for (Object item : list) {
            if (item != null) {
                String key = EntityKeyUtils.computeStableKeyOrUnresolved(item);
                if (!EntityKeyUtils.UNRESOLVED.equals(key)) {
                    map.put(key, item);
                }
            }
        }

        return map;
    }
}

