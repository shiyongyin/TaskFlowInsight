package com.syy.taskflowinsight.exporter.map;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link MapExporter} 单元测试
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class MapExporterTest {

    @Test
    @DisplayName("export - null session 返回空 Map")
    void exportNullSession() {
        Map<String, Object> result = MapExporter.export(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("export - 基本字段正确")
    void exportBasicFields() {
        Session session = Session.create("testSession");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        Map<String, Object> result = MapExporter.export(session);

        assertThat(result).containsKey("sessionId");
        assertThat(result).containsKey("sessionName");
        assertThat(result).containsKey("status");
        assertThat(result).containsKey("startTime");
        assertThat(result).containsKey("statistics");
        assertThat(result).containsKey("task");
        assertThat(result).containsKey("tasks");
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(result.get("sessionName")).isEqualTo("testSession");
    }

    @Test
    @DisplayName("export - 统计信息正确")
    void exportStatistics() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        root.createChild("child1").complete();
        root.createChild("child2").complete();
        root.complete();
        session.activate();
        session.complete();

        Map<String, Object> result = MapExporter.export(session);

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.get("statistics");
        assertThat(stats).isNotNull();
        assertThat((int) stats.get("totalTasks")).isEqualTo(3); // root + 2 children
        assertThat((int) stats.get("maxDepth")).isEqualTo(1);
    }

    @Test
    @DisplayName("export - 任务包含消息")
    void exportTaskWithMessages() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        root.addMessage("测试消息", MessageType.PROCESS);
        root.complete();
        session.activate();
        session.complete();

        Map<String, Object> result = MapExporter.export(session);

        @SuppressWarnings("unchecked")
        Map<String, Object> task = (Map<String, Object>) result.get("task");
        assertThat(task).containsKey("messages");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) task.get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("content")).isEqualTo("测试消息");
    }

    @Test
    @DisplayName("export - 嵌套子任务")
    void exportNestedChildren() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        TaskNode child = root.createChild("child");
        child.createChild("grandchild").complete();
        child.complete();
        root.complete();
        session.activate();
        session.complete();

        Map<String, Object> result = MapExporter.export(session);

        @SuppressWarnings("unchecked")
        Map<String, Object> task = (Map<String, Object>) result.get("task");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) task.get("children");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).get("taskName")).isEqualTo("child");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grandchildren = (List<Map<String, Object>>) children.get(0).get("children");
        assertThat(grandchildren).hasSize(1);
        assertThat(grandchildren.get(0).get("taskName")).isEqualTo("grandchild");
    }
}
