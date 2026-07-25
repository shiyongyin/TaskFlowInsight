package com.syy.taskflowinsight.exporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V1 导出契约的唯一确定性夹具。
 *
 * <p>三个 exporter 必须共享同一棵合法域任务树，否则各自维护样例会掩盖格式间的语义漂移。
 * 归一化仅处理运行时易变字段，业务值、插入顺序和 Java 标量类型均原样保留，确保 golden
 * 约束的是兼容契约而不是测试实现自行改写后的结果。
 */
final class ExportCompatibilityFixture {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SESSION_ID_LINE = Pattern.compile("(?m)^Session: .+$");
    private static final Pattern THREAD_LINE = Pattern.compile("(?m)^Thread:  .+$");
    private static final Pattern DURATION_TOKEN =
            Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:ms|s|m)\\b");
    private static final Pattern TIMESTAMP_TOKEN = Pattern.compile("@[^] ]+");

    private ExportCompatibilityFixture() {
    }

    static Session completedSession() {
        Session session = Session.create("golden-root");
        TaskNode root = session.getRootTask();
        root.addAttribute("text", "value");
        root.addAttribute("flag", true);
        root.addAttribute("attempts", 7);
        root.addAttribute("total", 9L);
        root.addAttribute("ratio", new BigDecimal("1.25"));
        root.addAttribute("nullable", null);
        root.addTag("first-tag");
        root.addTag("second-tag");

        TaskNode first = root.createChild("first-child");
        first.addInfo("process-message");
        first.complete();

        TaskNode second = root.createChild("second-child");
        second.addError("alert-message");
        second.complete();

        session.complete();
        return session;
    }

    static JsonNode normalizedJson(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        normalizeJsonNode(root, new int[] {0});
        return root;
    }

    static JsonNode normalizedMapValues(Map<String, Object> exported) throws IOException {
        Object normalized = normalizeMapValue(exported, null, new LinkedHashMap<>());
        /*
         * 值 golden 只约束 JSON 可观察值；先序列化再解析可消除 Integer/Long 的 Jackson
         * 节点实现差异。精确 Java 类型由独立的 mapTypeTree 契约负责，两个维度不能混用。
         */
        return MAPPER.readTree(MAPPER.writeValueAsBytes(normalized));
    }

    static JsonNode mapTypeTree(Map<String, Object> exported) {
        return MAPPER.valueToTree(toTypeTree(exported));
    }

    static JsonNode readJsonGolden(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return MAPPER.readTree(input);
        }
    }

    static String readTextGolden(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String normalizedConsole(String console) {
        String normalized = SESSION_ID_LINE.matcher(console).replaceFirst("Session: <session-id>");
        normalized = THREAD_LINE.matcher(normalized).replaceFirst("Thread:  <thread>");
        normalized = TIMESTAMP_TOKEN.matcher(normalized).replaceAll("@<timestamp>");
        return DURATION_TOKEN.matcher(normalized).replaceAll("<duration>");
    }

    private static InputStream resource(String name) {
        String path = "/golden/export/" + name;
        InputStream input = ExportCompatibilityFixture.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("Missing golden resource: " + path);
        }
        return input;
    }

    private static void normalizeJsonNode(JsonNode node, int[] nodeSequence) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if ("sessionId".equals(key)) {
                    object.put(key, "<session-id>");
                } else if ("nodeId".equals(key)) {
                    object.put(key, "<node-" + nodeSequence[0]++ + ">");
                } else if ("threadName".equals(key)) {
                    object.put(key, "<thread-name>");
                } else if ("threadId".equals(key)) {
                    object.put(key, 0);
                } else if (isVolatileNumber(key)) {
                    object.put(key, 0);
                } else {
                    normalizeJsonNode(entry.getValue(), nodeSequence);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> normalizeJsonNode(child, nodeSequence));
        }
    }

    private static Object normalizeMapValue(Object value, String key, Map<String, String> taskIds) {
        if ("sessionId".equals(key)) {
            return "<session-id>";
        }
        if ("taskId".equals(key)) {
            return taskIds.computeIfAbsent(String.valueOf(value), ignored -> "<task-" + taskIds.size() + ">");
        }
        if (isVolatileNumber(key)) {
            return 0L;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((childKey, childValue) -> copy.put(
                    String.valueOf(childKey), normalizeMapValue(childValue, String.valueOf(childKey), taskIds)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(child -> copy.add(normalizeMapValue(child, null, taskIds)));
            return copy;
        }
        return value;
    }

    private static Object toTypeTree(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> types = new LinkedHashMap<>();
            map.forEach((key, child) -> types.put(String.valueOf(key), toTypeTree(child)));
            return types;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ExportCompatibilityFixture::toTypeTree).toList();
        }
        return value.getClass().getName();
    }

    private static boolean isVolatileNumber(String key) {
        return key != null && (key.endsWith("Time")
                || key.endsWith("Millis")
                || key.endsWith("Nanos")
                || "createdAt".equals(key)
                || "endedAt".equals(key)
                || key.startsWith("duration")
                || key.endsWith("DurationMs")
                || "timestamp".equals(key));
    }
}
