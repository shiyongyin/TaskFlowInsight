package com.syy.taskflowinsight.exporter;

import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导出期任务耗时缓存测试。
 */
class TaskDurationCacheTest {

    @Test
    @DisplayName("TaskDurationCache - 每个节点累计耗时只计算一次")
    void accumulatedDurationsAreComputedOncePerNode() {
        Session session = createTree();

        TaskDurationCache cache = TaskDurationCache.from(session.getRootTask());

        assertThat(cache.size()).isEqualTo(4);
        assertThat(cache.getAccumulatedDurationMillis(session.getRootTask())).isGreaterThanOrEqualTo(0);
        assertThat(cache.getAccumulatedDurationMillis(session.getRootTask().getChildren().get(0)))
            .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("JsonExporter - 使用导出期累计耗时缓存且保留输出字段")
    void jsonExporterKeepsAccumulatedDurationField() {
        Session session = createTree();

        String json = new JsonExporter().export(session);

        assertThat(json).contains("\"accDurationMs\":");
        assertThat(json).contains("\"children\":");
    }

    @Test
    @DisplayName("ConsoleExporter - 使用导出期累计耗时缓存且保留任务输出")
    void consoleExporterKeepsTaskOutput() {
        Session session = createTree();

        String output = new ConsoleExporter().export(session, false);

        assertThat(output).contains("child-a");
        assertThat(output).contains("child-b");
        assertThat(output).containsPattern("\\(\\d+ms\\)");
    }

    private Session createTree() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        TaskNode childA = root.createChild("child-a");
        TaskNode grandchild = childA.createChild("grandchild");
        grandchild.complete();
        childA.complete();
        root.createChild("child-b").complete();
        root.complete();
        session.activate();
        session.complete();
        return session;
    }
}
