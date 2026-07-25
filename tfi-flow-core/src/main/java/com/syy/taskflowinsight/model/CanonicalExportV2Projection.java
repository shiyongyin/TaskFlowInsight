package com.syy.taskflowinsight.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将不可变 Session 快照投影为唯一 canonical V2 机器数据树。
 *
 * <p>schema owner 与快照模型共置，Map/JSON exporter 只负责捕获和编码边界。任务树使用显式栈展开，
 * 避免 framework 允许的深树把 formatter 重新变成递归遍历 owner。
 */
final class CanonicalExportV2Projection {

    private CanonicalExportV2Projection() {
    }

    static Map<String, Object> create(SessionExportSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
        projection.put("schemaVersion", 2);
        projection.put("captureEpochMillis", snapshot.captureMillis());
        projection.put("session", projectSession(snapshot));
        projection.put("statistics", projectStatistics(snapshot.statistics()));
        projection.put("rootTask", projectTasks(snapshot.root()));
        projection.put("truncated", snapshot.truncated());
        return deepFreeze(projection);
    }

    private static Map<String, Object> projectSession(SessionExportSnapshot snapshot) {
        LinkedHashMap<String, Object> session = new LinkedHashMap<>();
        session.put("id", snapshot.sessionId());
        session.put("name", snapshot.sessionName());
        session.put("status", snapshot.status().name());
        session.put("threadId", snapshot.threadId());
        session.put("threadName", snapshot.threadName());
        session.put("startEpochMillis", snapshot.createdMillis());
        session.put("endEpochMillis", snapshot.completedMillis());
        session.put("durationNanos", snapshot.durationNanos());
        return session;
    }

    private static Map<String, Object> projectStatistics(
            SessionExportSnapshot.Statistics statistics) {
        LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
        projection.put("totalTasks", statistics.totalTasks());
        projection.put("maxDepth", statistics.maxDepth());
        projection.put("totalMessages", statistics.totalMessages());
        return projection;
    }

    private static Map<String, Object> projectTasks(
            SessionExportSnapshot.TaskSnapshot root) {
        Map<String, Object> rootProjection = projectTask(root);
        ArrayDeque<TaskFrame> pending = new ArrayDeque<>();
        pending.push(new TaskFrame(root, rootProjection));
        while (!pending.isEmpty()) {
            TaskFrame frame = pending.pop();
            List<SessionExportSnapshot.TaskSnapshot> children = frame.snapshot().children();
            List<Map<String, Object>> childProjections = new ArrayList<>(children.size());
            for (SessionExportSnapshot.TaskSnapshot child : children) {
                Map<String, Object> childProjection = projectTask(child);
                childProjections.add(childProjection);
                frame.children().add(childProjection);
            }
            for (int index = children.size() - 1; index >= 0; index--) {
                pending.push(new TaskFrame(children.get(index), childProjections.get(index)));
            }
        }
        return rootProjection;
    }

    private static Map<String, Object> projectTask(
            SessionExportSnapshot.TaskSnapshot snapshot) {
        LinkedHashMap<String, Object> task = new LinkedHashMap<>();
        task.put("id", snapshot.nodeId());
        task.put("name", snapshot.taskName());
        task.put("path", snapshot.taskPath());
        task.put("threadName", snapshot.threadName());
        task.put("depth", snapshot.depth());
        task.put("sequence", snapshot.sequence());
        task.put("status", snapshot.status().name());
        task.put("startEpochMillis", snapshot.createdMillis());
        task.put("endEpochMillis", snapshot.completedMillis());
        task.put("durationNanos", snapshot.durationNanos());
        task.put("selfDurationNanos", snapshot.selfDurationNanos());
        task.put("accumulatedDurationNanos", snapshot.accumulatedDurationNanos());
        task.put("messages", projectMessages(snapshot.messages()));
        task.put("attributes", projectAttributes(snapshot.attributes()));
        task.put("tags", new ArrayList<>(snapshot.tags()));
        task.put("children", new ArrayList<>());
        task.put("childrenTruncated", snapshot.childrenTruncated());
        return task;
    }

    private static List<Object> projectMessages(
            List<SessionExportSnapshot.MessageSnapshot> messages) {
        List<Object> projection = new ArrayList<>(messages.size());
        for (SessionExportSnapshot.MessageSnapshot snapshot : messages) {
            LinkedHashMap<String, Object> message = new LinkedHashMap<>();
            message.put("type", snapshot.wireType());
            message.put("displayLabel", snapshot.displayLabel());
            message.put("severity", snapshot.severity().name());
            message.put("customLabel", snapshot.customLabel());
            message.put("content", snapshot.content());
            message.put("timestampEpochMillis", snapshot.timestampMillis());
            message.put("threadName", snapshot.threadName());
            projection.add(message);
        }
        return projection;
    }

    private static Map<String, Object> projectAttributes(Map<String, Object> attributes) {
        LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            projection.put(attribute.getKey(), projectAttributeValue(attribute.getValue()));
        }
        return projection;
    }

    private static Object projectAttributeValue(Object value) {
        if (value instanceof SessionExportSnapshot.NonFiniteNumber marker) {
            LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
            projection.put("kind", "nonFiniteNumber");
            projection.put("numberType", marker.numberKind().name());
            projection.put("value", marker.value());
            return projection;
        }
        if (value instanceof SessionExportSnapshot.UnsupportedValue marker) {
            LinkedHashMap<String, Object> projection = new LinkedHashMap<>();
            projection.put("kind", "unsupported");
            projection.put("className", marker.className());
            return projection;
        }
        return value;
    }

    /**
     * 以显式后序栈冻结 staging tree；identity lookup 避免任何值对象回调。
     */
    private static Map<String, Object> deepFreeze(Map<String, Object> root) {
        IdentityHashMap<Object, Object> frozen = new IdentityHashMap<>();
        ArrayDeque<FreezeFrame> pending = new ArrayDeque<>();
        pending.push(new FreezeFrame(root, false));
        while (!pending.isEmpty()) {
            FreezeFrame frame = pending.pop();
            Object source = frame.source();
            if (frozen.containsKey(source)) {
                continue;
            }
            if (!frame.expanded()) {
                pending.push(new FreezeFrame(source, true));
                pushNestedContainers(source, pending, frozen);
                continue;
            }
            frozen.put(source, freezeContainer(source, frozen));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) frozen.get(root);
        return result;
    }

    private static void pushNestedContainers(
            Object source,
            ArrayDeque<FreezeFrame> pending,
            IdentityHashMap<Object, Object> frozen) {
        if (source instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                pushContainer(value, pending, frozen);
            }
            return;
        }
        if (source instanceof List<?> list) {
            for (Object value : list) {
                pushContainer(value, pending, frozen);
            }
        }
    }

    private static void pushContainer(
            Object value,
            ArrayDeque<FreezeFrame> pending,
            IdentityHashMap<Object, Object> frozen) {
        if ((value instanceof Map<?, ?> || value instanceof List<?>)
                && !frozen.containsKey(value)) {
            pending.push(new FreezeFrame(value, false));
        }
    }

    private static Object freezeContainer(
            Object source, IdentityHashMap<Object, Object> frozen) {
        if (source instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put((String) entry.getKey(), frozenValue(entry.getValue(), frozen));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (source instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object value : list) {
                copy.add(frozenValue(value, frozen));
            }
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalStateException("Canonical staging value is not a container");
    }

    private static Object frozenValue(
            Object value, IdentityHashMap<Object, Object> frozen) {
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
            return value;
        }
        Object result = frozen.get(value);
        if (result == null) {
            throw new IllegalStateException("Canonical nested container was not frozen");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private record TaskFrame(
            SessionExportSnapshot.TaskSnapshot snapshot,
            Map<String, Object> projection) {

        private List<Object> children() {
            return (List<Object>) projection.get("children");
        }
    }

    private record FreezeFrame(Object source, boolean expanded) {
    }
}
