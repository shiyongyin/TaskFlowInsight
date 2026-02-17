package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

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

        assertThat(output).contains("TaskFlow Insight Report");
        assertThat(output).contains("Session:");
        assertThat(output).contains("Status:");
    }

    // ===== emoji 树状风格（默认） =====

    @Test
    @DisplayName("export - 根节点使用 📋 emoji 图标")
    void exportRootHasSessionIcon() {
        Session session = createSimpleSession();
        String output = exporter.export(session);
        assertThat(output).contains("\uD83D\uDCCB ");
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
        assertThat(output).contains("\uD83D\uDD27 child1");
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
        child1.createChild("grandchild").complete();
        child1.complete();
        root.createChild("child2").complete();
        root.complete();
        session.activate();
        session.complete();

        String output = exporter.export(session);
        // grandchild 行应该以 "│   " 开头（因为 child1 不是最后一个节点）
        assertThat(output).contains("│   ");
        assertThat(output).contains("grandchild");
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

    // ===== print 方法 =====

    @Test
    @DisplayName("print(session, null PrintStream) - 不抛异常")
    void printWithNullStream() {
        Session session = createSimpleSession();
        assertThatCode(() -> exporter.print(session, null)).doesNotThrowAnyException();
    }

    // ===== 辅助方法 =====

    private Session createSimpleSession() {
        Session session = Session.create("testRoot");
        session.getRootTask().complete();
        session.activate();
        session.complete();
        return session;
    }
}
