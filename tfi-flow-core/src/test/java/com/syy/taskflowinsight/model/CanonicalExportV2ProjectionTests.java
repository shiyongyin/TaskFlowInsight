package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Canonical V2 Map tree 的 exact schema、值域和不可变性测试。 */
class CanonicalExportV2ProjectionTests {

    @Test
    void should_publish_exact_keys_types_and_nulls() {
        Session session = Session.create("schema-root");
        TaskNode root = session.getRootTask();
        root.addDebug("debug-message");
        root.createChild("first");
        root.createChild("second");

        Map<String, Object> projection = project(SessionExportSnapshot.capture(session));
        Map<String, Object> sessionMap = map(projection.get("session"));
        Map<String, Object> statistics = map(projection.get("statistics"));
        Map<String, Object> rootTask = map(projection.get("rootTask"));
        Map<String, Object> message = map(list(rootTask.get("messages")).get(0));
        List<Object> children = list(rootTask.get("children"));

        assertThat(projection.keySet()).containsExactly(
                "schemaVersion", "captureEpochMillis", "session", "statistics", "rootTask", "truncated");
        assertThat(projection.get("schemaVersion")).isEqualTo(2).isExactlyInstanceOf(Integer.class);
        assertThat(projection.get("captureEpochMillis")).isExactlyInstanceOf(Long.class);
        assertThat(projection.get("truncated")).isEqualTo(false).isExactlyInstanceOf(Boolean.class);

        assertThat(sessionMap.keySet()).containsExactly(
                "id", "name", "status", "threadId", "threadName",
                "startEpochMillis", "endEpochMillis", "durationNanos");
        assertThat(sessionMap)
                .containsEntry("name", "schema-root")
                .containsEntry("status", "RUNNING")
                .containsEntry("endEpochMillis", null)
                .containsEntry("durationNanos", null);
        assertThat(sessionMap.get("threadId")).isExactlyInstanceOf(String.class);
        assertThat(sessionMap.get("startEpochMillis")).isExactlyInstanceOf(Long.class);

        assertThat(statistics.keySet()).containsExactly("totalTasks", "maxDepth", "totalMessages");
        assertThat(statistics)
                .containsEntry("totalTasks", 3)
                .containsEntry("maxDepth", 1)
                .containsEntry("totalMessages", 1);
        assertThat(statistics.values()).allSatisfy(value ->
                assertThat(value).isExactlyInstanceOf(Integer.class));

        assertThat(rootTask.keySet()).containsExactly(
                "id", "name", "path", "threadName", "depth", "sequence", "status",
                "startEpochMillis", "endEpochMillis", "durationNanos", "selfDurationNanos",
                "accumulatedDurationNanos", "messages", "attributes", "tags", "children",
                "childrenTruncated");
        assertThat(rootTask)
                .containsEntry("name", "schema-root")
                .containsEntry("path", "schema-root")
                .containsEntry("depth", 0)
                .containsEntry("sequence", 0)
                .containsEntry("status", "RUNNING")
                .containsEntry("endEpochMillis", null)
                .containsEntry("durationNanos", null)
                .containsEntry("childrenTruncated", false);
        assertThat(rootTask.get("startEpochMillis")).isExactlyInstanceOf(Long.class);
        assertThat(rootTask.get("selfDurationNanos")).isExactlyInstanceOf(Long.class);
        assertThat(rootTask.get("accumulatedDurationNanos")).isExactlyInstanceOf(Long.class);
        assertThat(map(rootTask.get("attributes"))).isEmpty();
        assertThat(list(rootTask.get("tags"))).isEmpty();

        assertThat(message.keySet()).containsExactly(
                "type", "displayLabel", "severity", "customLabel", "content",
                "timestampEpochMillis", "threadName");
        assertThat(message)
                .containsEntry("type", "核心指标")
                .containsEntry("displayLabel", "核心指标")
                .containsEntry("severity", "DEBUG")
                .containsEntry("customLabel", null)
                .containsEntry("content", "debug-message");
        assertThat(message.get("timestampEpochMillis")).isExactlyInstanceOf(Long.class);
        assertThat(map(children.get(0))).containsEntry("sequence", 0);
        assertThat(map(children.get(1))).containsEntry("sequence", 1);
    }

    @Test
    void should_publish_terminal_epoch_and_nano_durations() {
        SessionExportSnapshot.TaskSnapshot root = new SessionExportSnapshot.TaskSnapshot(
                "root-id", "terminal-root", "terminal-root", 0, 0, "worker",
                TaskStatus.COMPLETED, 1_000L, 2_000_000L,
                1_001L, 4_000_000L, 1L, 2_000_000L,
                2L, 2_000_000L, 2L, 2_000_000L,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot snapshot = new SessionExportSnapshot(
                2_000L, 10_000_000L, "session-id", "terminal-root", "42", "worker",
                SessionStatus.COMPLETED, 1_000L, 1_000_000L,
                1_002L, 5_000_000L, 2L, 4_000_000L,
                SessionExportSnapshot.Limits.defaults(), root,
                new SessionExportSnapshot.Statistics(1, 0, 0), false);

        Map<String, Object> projection = project(snapshot);
        Map<String, Object> session = map(projection.get("session"));
        Map<String, Object> task = map(projection.get("rootTask"));

        assertThat(session)
                .containsEntry("endEpochMillis", 1_002L)
                .containsEntry("durationNanos", 4_000_000L);
        assertThat(task)
                .containsEntry("endEpochMillis", 1_001L)
                .containsEntry("durationNanos", 2_000_000L)
                .containsEntry("selfDurationNanos", 2_000_000L)
                .containsEntry("accumulatedDurationNanos", 2_000_000L);
    }

    @Test
    void should_preserve_exact_scalars_and_tag_special_values() {
        Session session = Session.create("values-root");
        TaskNode root = session.getRootTask();
        root.addAttribute("null", null);
        root.addAttribute("text", "value");
        root.addAttribute("boolean", true);
        root.addAttribute("character", 'x');
        root.addAttribute("byte", (byte) 1);
        root.addAttribute("short", (short) 2);
        root.addAttribute("integer", 3);
        root.addAttribute("long", 4L);
        root.addAttribute("float", 1.5F);
        root.addAttribute("double", 2.5D);
        root.addAttribute("bigInteger", new BigInteger("12345678901234567890"));
        root.addAttribute("bigDecimal", new BigDecimal("1234.50"));
        root.addAttribute("floatNaN", Float.NaN);
        root.addAttribute("negativeInfinity", Double.NEGATIVE_INFINITY);
        root.addAttribute("unsupported", new HostileValue());

        Map<String, Object> projection = project(SessionExportSnapshot.capture(session));
        Map<String, Object> attributes = map(map(projection.get("rootTask")).get("attributes"));

        assertThat(attributes.get("null")).isNull();
        assertScalar(attributes, "text", "value", String.class);
        assertScalar(attributes, "boolean", true, Boolean.class);
        assertScalar(attributes, "character", 'x', Character.class);
        assertScalar(attributes, "byte", (byte) 1, Byte.class);
        assertScalar(attributes, "short", (short) 2, Short.class);
        assertScalar(attributes, "integer", 3, Integer.class);
        assertScalar(attributes, "long", 4L, Long.class);
        assertScalar(attributes, "float", 1.5F, Float.class);
        assertScalar(attributes, "double", 2.5D, Double.class);
        assertScalar(attributes, "bigInteger", new BigInteger("12345678901234567890"), BigInteger.class);
        assertScalar(attributes, "bigDecimal", new BigDecimal("1234.50"), BigDecimal.class);

        Map<String, Object> nan = map(attributes.get("floatNaN"));
        assertThat(nan.keySet()).containsExactly("kind", "numberType", "value");
        assertThat(nan)
                .containsEntry("kind", "nonFiniteNumber")
                .containsEntry("numberType", "FLOAT")
                .containsEntry("value", "NaN");
        Map<String, Object> infinity = map(attributes.get("negativeInfinity"));
        assertThat(infinity.keySet()).containsExactly("kind", "numberType", "value");
        assertThat(infinity)
                .containsEntry("kind", "nonFiniteNumber")
                .containsEntry("numberType", "DOUBLE")
                .containsEntry("value", "-Infinity");
        Map<String, Object> unsupported = map(attributes.get("unsupported"));
        assertThat(unsupported.keySet()).containsExactly("kind", "className");
        assertThat(unsupported).containsEntry("kind", "unsupported")
                .containsEntry("className", HostileValue.class.getName());
    }

    @Test
    void should_return_identity_independent_deeply_unmodifiable_trees() {
        Session session = Session.create("immutable-root");
        TaskNode root = session.getRootTask();
        root.addInfo("message");
        root.addAttribute("nullable", null);
        root.addAttribute("nan", Double.NaN);
        root.addTag("tag");
        root.createChild("child");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);

        Map<String, Object> first = project(snapshot);
        Map<String, Object> second = project(snapshot);
        Map<String, Object> sessionMap = map(first.get("session"));
        Map<String, Object> statistics = map(first.get("statistics"));
        Map<String, Object> rootTask = map(first.get("rootTask"));
        List<Object> messages = list(rootTask.get("messages"));
        Map<String, Object> message = map(messages.get(0));
        Map<String, Object> attributes = map(rootTask.get("attributes"));
        Map<String, Object> marker = map(attributes.get("nan"));
        List<Object> tags = list(rootTask.get("tags"));
        List<Object> children = list(rootTask.get("children"));
        Map<String, Object> child = map(children.get(0));
        Map<String, Object> secondRoot = map(second.get("rootTask"));
        Map<String, Object> secondAttributes = map(secondRoot.get("attributes"));
        Map<String, Object> secondMarker = map(secondAttributes.get("nan"));

        assertThat(first).isEqualTo(second).isNotSameAs(second);
        assertThat(rootTask).isNotSameAs(secondRoot);
        assertThat(marker).isNotSameAs(secondMarker);
        assertThat(attributes).containsEntry("nullable", null);

        assertUnmodifiable(() -> first.put("extra", true));
        assertUnmodifiable(() -> sessionMap.put("extra", true));
        assertUnmodifiable(() -> statistics.put("extra", true));
        assertUnmodifiable(() -> rootTask.put("extra", true));
        assertUnmodifiable(() -> messages.add(Map.of()));
        assertUnmodifiable(() -> message.put("extra", true));
        assertUnmodifiable(() -> attributes.put("extra", true));
        assertUnmodifiable(() -> marker.put("extra", true));
        assertUnmodifiable(() -> tags.add("extra"));
        assertUnmodifiable(() -> children.add(Map.of()));
        assertUnmodifiable(() -> child.put("extra", true));
    }

    @Test
    void should_project_framework_max_depth_without_recursive_stack() {
        Session session = Session.create("deep-root");
        TaskNode current = session.getRootTask();
        for (int depth = 1; depth <= FlowConfigDefaults.MAX_EXPORT_DEPTH; depth++) {
            current = current.createChild("n");
        }
        current.createChild("beyond-limit");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);

        Map<String, Object> projection = project(snapshot);
        Map<String, Object> statistics = map(projection.get("statistics"));
        Map<String, Object> task = map(projection.get("rootTask"));
        for (int depth = 0; depth < FlowConfigDefaults.MAX_EXPORT_DEPTH; depth++) {
            assertThat(task).containsEntry("depth", depth).containsEntry("childrenTruncated", false);
            List<Object> children = list(task.get("children"));
            assertThat(children).singleElement();
            task = map(children.get(0));
        }

        assertThat(task)
                .containsEntry("depth", FlowConfigDefaults.MAX_EXPORT_DEPTH)
                .containsEntry("name", "n")
                .containsEntry("childrenTruncated", true);
        assertThat(list(task.get("children"))).isEmpty();
        assertThat(projection).containsEntry("truncated", true);
        assertThat(statistics)
                .containsEntry("totalTasks", FlowConfigDefaults.MAX_EXPORT_DEPTH + 1)
                .containsEntry("maxDepth", FlowConfigDefaults.MAX_EXPORT_DEPTH);
    }

    private static void assertUnmodifiable(Runnable mutation) {
        assertThatThrownBy(mutation::run).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertScalar(
            Map<String, Object> attributes, String key, Object expected, Class<?> type) {
        assertThat(attributes.get(key)).isEqualTo(expected).isExactlyInstanceOf(type);
    }

    private static Map<String, Object> project(SessionExportSnapshot snapshot) {
        return snapshot.toCanonicalV2();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    private static final class HostileValue {
        @Override
        public String toString() {
            throw new AssertionError("canonical projection must not call user toString");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("canonical projection must not call user hashCode");
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("canonical projection must not call user equals");
        }
    }
}
