package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.SIMPLE;
import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.TREE;
import static org.assertj.core.api.Assertions.*;

/**
 * {@link ConsoleExporter} 单元测试
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class ConsoleExporterTest {

    private final ConsoleExporter exporter = new ConsoleExporter();

    // ===== null / 空场景 =====

    @Test
    @DisplayName("export - null session 返回空字符串")
    void exportNullSession() {
        assertThat(exporter.export(null)).isEmpty();
    }

    @Test
    @DisplayName("exportSimple - null session 返回空字符串")
    void exportSimpleNullSession() {
        assertThat(exporter.exportSimple(null, false)).isEmpty();
    }

    // ===== 会话头部 =====

    @Test
    @DisplayName("export - 包含会话头部信息")
    void exportContainsHeader() {
        Session session = createSimpleSession();
        String output = exporter.export(session);

        assertThat(output)
                .contains("TaskFlow Insight Report")
                .contains("Session: " + session.getSessionId() + "\n")
                .contains("Thread:  " + session.getThreadId()
                        + " (" + session.getThreadName() + ")\n")
                .contains("Status:  " + session.getStatus() + "\n");
    }

    // ===== emoji 树状风格（默认） =====

    @Test
    @DisplayName("export - 根节点使用 📋 emoji 图标")
    void exportRootHasSessionIcon() {
        Session session = createSimpleSession();
        String output = exporter.export(session);
        assertThat(output)
                .contains("\uD83D\uDCCB ")
                .doesNotContain("\uD83D\uDCAC ");
    }

    @Test
    @DisplayName("export - 子任务使用 🔧 emoji 图标")
    void exportChildHasTaskIcon() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        root.createChild("child1").complete();
        root.complete();
        session.activate();
        session.complete();

        String output = exporter.export(session);
        assertThat(output).contains("\u2514\u2500\u2500 \uD83D\uDD27 child1");
    }

    @Test
    @DisplayName("export - 消息使用 💬 emoji 图标")
    void exportMessageHasIcon() {
        Session session = Session.create("root");
        session.getRootTask().addInfo("测试消息");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String output = exporter.export(session);
        // addInfo 创建的是 PROCESS 类型，displayLabel 为 "业务流程"
        assertThat(output).contains("\uD83D\uDCAC");
        assertThat(output).contains("测试消息");
    }

    @Test
    @DisplayName("export - 包含 ├── 和 └── 树形连接线")
    void exportContainsTreeBranches() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        root.createChild("child1").complete();
        root.createChild("child2").complete();
        root.complete();
        session.activate();
        session.complete();

        String output = exporter.export(session);
        assertThat(output).contains("├── ");
        assertThat(output).contains("└── ");
    }

    @Test
    @DisplayName("export - 嵌套子任务使用 │ 垂直连接线")
    void exportNestedHasVerticalLine() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        TaskNode child1 = root.createChild("child1");
        TaskNode grandchild = child1.createChild("grandchild");
        grandchild.addDebug("grandchild-message");
        grandchild.complete();
        child1.complete();
        root.createChild("child2").complete();
        root.complete();
        session.activate();
        session.complete();

        String output = exporter.export(session);
        // grandchild 行应该以 "│   " 开头（因为 child1 不是最后一个节点）
        assertThat(output)
                .contains("│   └── \uD83D\uDD27 grandchild")
                .contains("│       └── \uD83D\uDCAC [核心指标] grandchild-message");
    }

    @Test
    @DisplayName("export - 包含任务状态 [COMPLETED]")
    void exportContainsStatus() {
        Session session = createSimpleSession();
        String output = exporter.export(session);
        assertThat(output).contains("[COMPLETED]");
    }

    @Test
    @DisplayName("export - 包含耗时信息")
    void exportContainsDuration() {
        Session session = createSimpleSession();
        String output = exporter.export(session);
        // 根节点行应包含 "ms)" 或类似格式
        assertThat(output).containsPattern("\\(\\d+ms\\)");
    }

    @Test
    @DisplayName("export - 完整树结构验证（多层嵌套+消息）")
    void exportFullTreeStructure() {
        Session session = Session.create("订单处理");
        TaskNode root = session.getRootTask();

        TaskNode verify = root.createChild("验证库存");
        verify.addInfo("库存充足");
        verify.complete();

        TaskNode deduct = root.createChild("扣减库存");
        deduct.addInfo("扣减成功");
        deduct.complete();

        TaskNode create = root.createChild("创建订单");
        create.addInfo("订单号: ORD-001");
        create.addWarn("库存低于阈值");
        create.complete();

        root.complete();
        session.activate();
        session.complete();

        String output = exporter.export(session, false);

        // 验证根节点（📋 emoji）
        assertThat(output).contains("\uD83D\uDCCB 订单处理 [COMPLETED]");
        // 验证子任务带 emoji（🔧）
        assertThat(output).contains("\uD83D\uDD27 验证库存 [COMPLETED]");
        assertThat(output).contains("\uD83D\uDD27 扣减库存 [COMPLETED]");
        assertThat(output).contains("\uD83D\uDD27 创建订单 [COMPLETED]");
        // 验证消息带 emoji（💬）+ 实际显示标签
        assertThat(output).contains("\uD83D\uDCAC [业务流程] 库存充足");
        assertThat(output).contains("\uD83D\uDCAC [业务流程] 扣减成功");
        assertThat(output).contains("\uD83D\uDCAC [业务流程] 订单号: ORD-001");
        // addWarn 创建 ALERT 类型，displayLabel 为 "⚠️异常提示"
        assertThat(output).contains("库存低于阈值");
        // 验证树形连接线
        assertThat(output).contains("├── ");
        assertThat(output).contains("└── ");
    }

    // ===== 简化模式 =====

    @Test
    @DisplayName("exportSimple - 使用缩进而非树形连接线")
    void exportSimpleUsesIndent() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        root.createChild("child").complete();
        root.complete();
        session.activate();
        session.complete();

        String output = exporter.exportSimple(session, false);
        assertThat(output).contains("    child");
        // 简化模式不应包含 emoji 树形图标
        assertThat(output).doesNotContain("\uD83D\uDD27");
    }

    @Test
    @DisplayName("exportSimple(true) - 显示时间戳")
    void exportSimpleWithTimestamp() {
        Session session = Session.create("root");
        session.getRootTask().addInfo("msg");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String output = exporter.exportSimple(session, true);
        assertThat(output).contains("@");
    }

    @Test
    @DisplayName("export(boolean) - boolean 只控制 TREE 消息时间戳")
    void showTimestampFlagControlsOnlyTreeMessageTimestamp() {
        Session session = Session.create("timestamp-root");
        session.getRootTask().addInfo("timestamp-message");

        String withoutTimestamp = exporter.export(session, false);
        String withTimestamp = exporter.export(session, true);

        assertThat(withoutTimestamp)
                .contains("\uD83D\uDCCB timestamp-root", "[业务流程] timestamp-message")
                .doesNotContain("[业务流程 @");
        assertThat(withTimestamp)
                .contains("\uD83D\uDCCB timestamp-root", "[业务流程 @")
                .doesNotContain(", self ");
    }

    // ===== print 方法 =====

    @Test
    @DisplayName("print(session, null PrintStream) - 不抛异常")
    void printWithNullStream() {
        Session session = createSimpleSession();
        assertThatCode(() -> exporter.print(session, null)).doesNotThrowAnyException();
    }

    // ===== snapshot 捕获边界 =====

    @Test
    @DisplayName("export seam - null session 在 capturer 前返回")
    void should_skip_capturer_when_tree_session_is_null() {
        AtomicInteger captures = new AtomicInteger();

        String output = invokeExport(null, TREE, false, session -> {
            captures.incrementAndGet();
            return SessionExportSnapshot.capture(session);
        });

        assertThat(output).as("null tree export output").isEmpty();
        assertThat(captures).as("null tree export capture count").hasValue(0);
    }

    @Test
    @DisplayName("exportSimple seam - null session 在 capturer 前返回")
    void should_skip_capturer_when_simple_session_is_null() {
        AtomicInteger captures = new AtomicInteger();

        String output = invokeExport(null, SIMPLE, false, session -> {
            captures.incrementAndGet();
            return SessionExportSnapshot.capture(session);
        });

        assertThat(output).as("null simple export output").isEmpty();
        assertThat(captures).as("null simple export capture count").hasValue(0);
    }

    @Test
    @DisplayName("export seam - 非空 session 只捕获一次")
    void should_capture_once_when_rendering_tree() {
        Session session = createSimpleSession();
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        AtomicInteger captures = new AtomicInteger();

        String output = invokeExport(session, TREE, false, ignored -> {
            captures.incrementAndGet();
            return snapshot;
        });

        assertThat(output).as("tree snapshot output").contains("testRoot");
        assertThat(captures).as("tree snapshot capture count").hasValue(1);
    }

    @Test
    @DisplayName("exportSimple seam - 非空 session 只捕获一次")
    void should_capture_once_when_rendering_simple_text() {
        Session session = createSimpleSession();
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        AtomicInteger captures = new AtomicInteger();

        String output = invokeExport(session, SIMPLE, false, ignored -> {
            captures.incrementAndGet();
            return snapshot;
        });

        assertThat(output).as("simple snapshot output").contains("testRoot");
        assertThat(captures).as("simple snapshot capture count").hasValue(1);
    }

    @Test
    @DisplayName("export seam - 只渲染 capturer 返回的冻结树")
    void should_render_frozen_tree_when_model_changes_after_capture() {
        Session session = Session.create("frozen-root");
        TaskNode root = session.getRootTask();
        root.addInfo("before-capture");
        root.createChild("frozen-child");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        root.addInfo("after-capture");
        root.createChild("late-child");

        String output = invokeExport(session, TREE, false, ignored -> snapshot);

        assertThat(output)
                .as("tree output from prebuilt snapshot")
                .contains("before-capture", "frozen-child")
                .doesNotContain("after-capture", "late-child");
    }

    @Test
    @DisplayName("exportSimple seam - 只渲染 capturer 返回的冻结树")
    void should_render_frozen_simple_text_when_model_changes_after_capture() {
        Session session = Session.create("frozen-root");
        TaskNode root = session.getRootTask();
        root.addInfo("before-capture");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        root.addInfo("after-capture");

        String output = invokeExport(session, SIMPLE, false, ignored -> snapshot);

        assertThat(output)
                .as("simple output from prebuilt snapshot")
                .contains("before-capture")
                .doesNotContain("after-capture");
    }

    @Test
    @DisplayName("capturer 失败 - 原异常直接传播且不返回文本")
    void should_propagate_same_failure_when_capture_fails() {
        Session session = Session.create("failure-root");
        IllegalStateException failure = new IllegalStateException("capture failed");

        assertThatThrownBy(() -> invokeExport(session, TREE, false, ignored -> {
            throw failure;
        })).as("tree capture failure").isSameAs(failure);
        assertThatThrownBy(() -> invokeExport(session, SIMPLE, false, ignored -> {
            throw failure;
        })).as("simple capture failure").isSameAs(failure);
    }

    @Test
    @DisplayName("print - capture 失败前不写入任何输出字节")
    void should_write_no_bytes_when_capture_fails() {
        Session session = Session.create("oversized-root");
        int textLimit = Math.toIntExact(FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        String expectedMessage = "Export text character limit exceeded: " + textLimit;
        session.getRootTask().addInfo("x".repeat(textLimit + 1));
        PrintStream original = System.out;
        ByteArrayOutputStream standardBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream streamBytes = new ByteArrayOutputStream();

        try (PrintStream standard = new PrintStream(standardBytes, true, StandardCharsets.UTF_8);
             PrintStream stream = new PrintStream(streamBytes, true, StandardCharsets.UTF_8)) {
            System.setOut(standard);
            assertThatThrownBy(() -> exporter.print(session))
                    .as("standard output capture failure")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(expectedMessage);
            assertThatThrownBy(() -> exporter.print(session, stream))
                    .as("provided stream capture failure")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(expectedMessage);
            assertThatThrownBy(() -> exporter.print(session, null))
                    .as("null stream still follows the non-null Session capture path")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(expectedMessage);
        } finally {
            System.setOut(original);
        }

        assertThat(standardBytes.toByteArray()).as("standard output bytes").isEmpty();
        assertThat(streamBytes.toByteArray()).as("provided stream bytes").isEmpty();
    }

    @Test
    @DisplayName("迭代 renderer - 可处理 framework 最大深度并显示截断")
    void should_render_max_depth_snapshot_without_recursive_stack() {
        Session session = Session.create("deep-root");
        TaskNode current = session.getRootTask();
        for (int depth = 1; depth <= FlowConfigDefaults.MAX_EXPORT_DEPTH; depth++) {
            current = current.createChild("n");
        }
        current.createChild("beyond-limit");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        String marker = "children truncated at depth " + FlowConfigDefaults.MAX_EXPORT_DEPTH;

        String tree = invokeExport(session, TREE, false, ignored -> snapshot);
        String simple = invokeExport(session, SIMPLE, false, ignored -> snapshot);

        assertThat(tree).as("max-depth tree output").contains(marker).doesNotContain("beyond-limit");
        assertThat(simple).as("max-depth simple output").contains(marker).doesNotContain("beyond-limit");
    }

    // ===== 辅助方法 =====

    private String invokeExport(
            Session session,
            ConsoleExportOptions.ConsoleStyle style,
            boolean showTimestamp,
            Function<Session, SessionExportSnapshot> capturer) {
        return exporter.export(
                session, new ConsoleExportOptions(style, showTimestamp), capturer);
    }

    private Session createSimpleSession() {
        Session session = Session.create("testRoot");
        session.getRootTask().complete();
        session.activate();
        session.complete();
        return session;
    }
}
