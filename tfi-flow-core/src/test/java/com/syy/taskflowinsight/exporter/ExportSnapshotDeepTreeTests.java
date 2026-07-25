package com.syy.taskflowinsight.exporter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExportOptions;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.SessionExportSnapshot.TaskSnapshot;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.SIMPLE;
import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.TREE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/** 合法最大深度下所有 current public export route 的统一非递归与截断门禁。 */
class ExportSnapshotDeepTreeTests {

    private static final int MAX_DEPTH = FlowConfigDefaults.MAX_EXPORT_DEPTH;
    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(MAX_DEPTH * 3 + 100)
                            .build())
                    .build());

    @Test
    void allCurrentPublicRoutesBoundTheSameLegalDeepTreeWithoutRecursion() {
        assertTimeout(Duration.ofSeconds(30), () -> {
            Session session = createDeepSession();
            CanonicalSummary expected = new CanonicalSummary(
                    2, true, MAX_DEPTH + 1, MAX_DEPTH,
                    MAX_DEPTH, "level-" + MAX_DEPTH, true, MAX_DEPTH + 1);

            SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
            assertSnapshotBoundary(snapshot);

            ConsoleExporter console = new ConsoleExporter();
            String tree = console.export(session, new ConsoleExportOptions(TREE, false));
            String simple = console.export(session, new ConsoleExportOptions(SIMPLE, false));
            assertConsoleBoundary(tree, "TREE");
            assertConsoleBoundary(simple, "SIMPLE");

            CanonicalSummary mapSummary = summarize(MapExporter.export(session));
            assertThat(mapSummary).as("Map V2 boundary").isEqualTo(expected);

            JsonExporter json = new JsonExporter();
            CanonicalSummary stringSummary = summarize(MAPPER.readTree(json.export(session)));
            StringWriter writer = new StringWriter();
            json.export(session, writer);
            CanonicalSummary writerSummary = summarize(MAPPER.readTree(writer.toString()));

            assertThat(stringSummary).as("JSON String boundary").isEqualTo(expected);
            assertThat(writerSummary).as("JSON Writer boundary").isEqualTo(expected);
            assertThat(writerSummary)
                    .as("JSON routes compare schema/depth/truncation without capture time")
                    .isEqualTo(stringSummary);
        });
    }

    private static Session createDeepSession() {
        Session session = Session.create("deep-root");
        TaskNode current = session.getRootTask();
        for (int depth = 1; depth <= MAX_DEPTH + 1; depth++) {
            current = current.createChild("level-" + depth);
        }
        return session;
    }

    private static void assertSnapshotBoundary(SessionExportSnapshot snapshot) {
        assertThat(snapshot.truncated()).isTrue();
        assertThat(snapshot.statistics().totalTasks()).isEqualTo(MAX_DEPTH + 1);
        assertThat(snapshot.statistics().maxDepth()).isEqualTo(MAX_DEPTH);

        TaskSnapshot task = snapshot.root();
        for (int expectedDepth = 0; expectedDepth <= MAX_DEPTH; expectedDepth++) {
            assertThat(task.depth()).isEqualTo(expectedDepth);
            if (expectedDepth == MAX_DEPTH) {
                assertThat(task.taskName()).isEqualTo("level-" + MAX_DEPTH);
                assertThat(task.children()).isEmpty();
                assertThat(task.childrenTruncated()).isTrue();
            } else {
                assertThat(task.childrenTruncated()).isFalse();
                assertThat(task.children()).singleElement();
                task = task.children().getFirst();
            }
        }
    }

    private static void assertConsoleBoundary(String output, String route) {
        String marker = "children truncated at depth " + MAX_DEPTH;
        assertThat(output)
                .as(route + " legal deep-tree boundary")
                .contains("level-" + MAX_DEPTH, marker)
                .doesNotContain("level-" + (MAX_DEPTH + 1));
    }

    private static CanonicalSummary summarize(Map<String, Object> document) {
        Map<String, Object> statistics = map(document.get("statistics"));
        Map<String, Object> task = map(document.get("rootTask"));
        int visibleTasks = 0;
        while (true) {
            assertThat(integer(task.get("depth"))).isEqualTo(visibleTasks);
            visibleTasks++;
            List<Object> children = list(task.get("children"));
            if (children.isEmpty()) {
                break;
            }
            assertThat(children).singleElement();
            task = map(children.getFirst());
        }
        return new CanonicalSummary(
                integer(document.get("schemaVersion")),
                bool(document.get("truncated")),
                integer(statistics.get("totalTasks")),
                integer(statistics.get("maxDepth")),
                integer(task.get("depth")),
                (String) task.get("name"),
                bool(task.get("childrenTruncated")),
                visibleTasks);
    }

    private static CanonicalSummary summarize(JsonNode document) {
        JsonNode statistics = document.path("statistics");
        JsonNode task = document.path("rootTask");
        int visibleTasks = 0;
        while (true) {
            assertThat(task.isObject()).isTrue();
            assertThat(task.path("depth").intValue()).isEqualTo(visibleTasks);
            visibleTasks++;
            JsonNode children = task.path("children");
            assertThat(children.isArray()).isTrue();
            if (children.isEmpty()) {
                break;
            }
            assertThat(children.size()).isEqualTo(1);
            task = children.get(0);
        }
        return new CanonicalSummary(
                document.path("schemaVersion").intValue(),
                document.path("truncated").booleanValue(),
                statistics.path("totalTasks").intValue(),
                statistics.path("maxDepth").intValue(),
                task.path("depth").intValue(),
                task.path("name").textValue(),
                task.path("childrenTruncated").booleanValue(),
                visibleTasks);
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

    private static int integer(Object value) {
        assertThat(value).isExactlyInstanceOf(Integer.class);
        return (Integer) value;
    }

    private static boolean bool(Object value) {
        assertThat(value).isExactlyInstanceOf(Boolean.class);
        return (Boolean) value;
    }

    private record CanonicalSummary(
            int schemaVersion,
            boolean truncated,
            int totalTasks,
            int maxDepth,
            int deepestDepth,
            String deepestTaskName,
            boolean deepestChildrenTruncated,
            int visibleTasks) {
    }
}
