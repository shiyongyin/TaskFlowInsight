package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 routed TaskContext 在整个任务作用域内保留创建它的 FlowProvider。
 *
 * <p>ProviderRegistry 在任务存活期间可能变化，因此子任务与关闭不能重新解析全局 Provider；
 * 否则同一棵任务树的 start/end 会落入不同 owner，导致任务无法成对收栈。
 */
class TFIOwnerProviderTests {

    private RecordingFlowProvider owner;

    @BeforeEach
    void setUp() {
        TFI.clear();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.setProperty("tfi.api.routing.enabled", "true");
        owner = new RecordingFlowProvider();
        TFI.registerFlowProvider(owner);
        TFI.enable();
        TFI.setChangeTrackingEnabled(false);
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
        TFI.disable();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.api.routing.enabled");
    }

    @Test
    void routedContextUsesOwnerForSubtaskAndClose() {
        TaskContext parent = TFI.start("parent");
        TaskContext child = parent.subtask("child");

        child.close();
        parent.close();

        assertThat(owner.startedTasks()).containsExactly("parent", "child");
        assertThat(owner.endedTasks()).containsExactly("child", "parent");
        assertThat(owner.currentTask()).isNull();
    }

    @Test
    void outOfOrderCloseDoesNotPopUnrelatedTask() {
        TaskContext parent = TFI.start("parent");
        TaskContext child = parent.subtask("child");
        parent.close();
        TaskContext unrelated = TFI.start("unrelated");

        child.close();

        assertThat(owner.currentTask()).isNotNull();
        assertThat(owner.currentTask().getTaskName()).isEqualTo("unrelated");
        assertThat(owner.endedTasks()).containsExactly("child", "parent");
        unrelated.close();
    }

    @Test
    void repeatedCloseEndsEachTaskOnce() {
        TaskContext parent = TFI.start("parent");
        TaskContext child = parent.subtask("child");

        child.close();
        child.close();
        parent.close();
        parent.close();

        assertThat(owner.endedTasks()).containsExactly("child", "parent");
    }

    @Test
    void registeredFlowAndExportProvidersRouteThroughRealTfiFacade() {
        RecordingExportProvider firstExport = new RecordingExportProvider("first-epoch");
        TFI.registerExportProvider(firstExport);

        assertThat(TFI.startSession("first")).isNotNull();
        assertThat(TFI.exportToJson()).isEqualTo("first-epoch");
        TFI.endSession();

        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        RecordingFlowProvider replacement = new RecordingFlowProvider();
        RecordingExportProvider secondExport = new RecordingExportProvider("second-epoch");
        TFI.registerFlowProvider(replacement);
        TFI.registerExportProvider(secondExport);

        assertThat(TFI.startSession("second")).isNotNull();
        assertThat(TFI.exportToJson()).isEqualTo("second-epoch");
        assertThat(replacement.currentSession()).isNotNull();
        assertThat(secondExport.calls()).isEqualTo(1);
        TFI.endSession();
    }

    @Test
    void tfiOwnsNoSelectedProviderFields() {
        assertThat(TFI.class.getDeclaredFields())
            .extracting(java.lang.reflect.Field::getName)
            .doesNotContain(
                "cachedComparisonProvider",
                "cachedTrackingProvider",
                "cachedFlowProvider",
                "cachedRenderProvider",
                "cachedExportProvider");
    }

    /**
     * 以真实父子栈记录调用，避免 mock 只验证方法次数却遗漏 owner 的收栈语义。
     */
    private static final class RecordingFlowProvider implements FlowProvider {

        private final Deque<TaskNode> taskStack = new ArrayDeque<>();
        private final List<String> startedTasks = new ArrayList<>();
        private final List<String> endedTasks = new ArrayList<>();
        private Session session;

        @Override
        public String startSession(String sessionName) {
            session = Session.create(sessionName);
            return session.getSessionId();
        }

        @Override
        public void endSession() {
            if (session != null) {
                session.tryComplete();
                session = null;
            }
        }

        @Override
        public Session currentSession() {
            return session;
        }

        @Override
        public TaskNode startTask(String taskName) {
            TaskNode parent = taskStack.peekLast();
            if (parent == null) {
                parent = session.getRootTask();
            }
            TaskNode task = new TaskNode(parent, taskName);
            taskStack.addLast(task);
            startedTasks.add(taskName);
            return task;
        }

        @Override
        public void endTask() {
            TaskNode task = taskStack.pollLast();
            if (task != null) {
                task.tryComplete();
                endedTasks.add(task.getTaskName());
            }
        }

        @Override
        public TaskNode currentTask() {
            return taskStack.peekLast();
        }

        @Override
        public void message(String content, String label) {
            // 本 fixture 只观察任务所有权，消息行为不参与该契约。
        }

        @Override
        public void clear() {
            taskStack.clear();
            session = null;
        }

        @Override
        public int priority() {
            return Integer.MAX_VALUE;
        }

        private List<String> startedTasks() {
            return List.copyOf(startedTasks);
        }

        private List<String> endedTasks() {
            return List.copyOf(endedTasks);
        }
    }

    private static final class RecordingExportProvider implements ExportProvider {

        private final String json;
        private int calls;

        private RecordingExportProvider(String json) {
            this.json = json;
        }

        @Override
        public boolean exportToConsole(boolean showTimestamp) {
            return true;
        }

        @Override
        public String exportToJson() {
            calls++;
            return json;
        }

        @Override
        public Map<String, Object> exportToMap() {
            return Map.of("epoch", json);
        }

        @Override
        public int priority() {
            return Integer.MAX_VALUE;
        }

        private int calls() {
            return calls;
        }
    }
}
