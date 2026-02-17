package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.enums.TaskStatus;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link TaskNode} 单元测试
 *
 * <p>覆盖树形结构、状态转换、消息记录、路径计算和时间戳。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class TaskNodeTest {

    // ==================== 构造和基本属性 ====================

    @Test
    @DisplayName("根节点 - 正常创建")
    void createRootNode() {
        TaskNode root = new TaskNode("根任务");
        assertThat(root.getTaskName()).isEqualTo("根任务");
        assertThat(root.getParent()).isNull();
        assertThat(root.isRoot()).isTrue();
        assertThat(root.isLeaf()).isTrue();
        assertThat(root.getDepth()).isZero();
        assertThat(root.getTaskPath()).isEqualTo("根任务");
        assertThat(root.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(root.getNodeId()).isNotEmpty();
        assertThat(root.getThreadName()).isEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("构造 - null 名称抛出异常")
    void createWithNullNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskNode(null));
    }

    @Test
    @DisplayName("构造 - 空名称抛出异常")
    void createWithEmptyNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskNode(""));
    }

    @Test
    @DisplayName("构造 - 名称自动 trim")
    void nameIsTrimmed() {
        TaskNode node = new TaskNode("  test  ");
        assertThat(node.getTaskName()).isEqualTo("test");
    }

    // ==================== 树形结构 ====================

    @Test
    @DisplayName("子节点 - createChild 建立父子关系")
    void createChildEstablishesParentChild() {
        TaskNode root = new TaskNode("root");
        TaskNode child = root.createChild("child");

        assertThat(child.getParent()).isSameAs(root);
        assertThat(child.isRoot()).isFalse();
        assertThat(root.isLeaf()).isFalse();
        assertThat(root.getChildren()).containsExactly(child);
        assertThat(child.getDepth()).isEqualTo(1);
    }

    @Test
    @DisplayName("多级嵌套 - 路径正确计算")
    void nestedPathComputation() {
        TaskNode root = new TaskNode("A");
        TaskNode b = root.createChild("B");
        TaskNode c = b.createChild("C");

        assertThat(root.getTaskPath()).isEqualTo("A");
        assertThat(b.getTaskPath()).isEqualTo("A/B");
        assertThat(c.getTaskPath()).isEqualTo("A/B/C");
    }

    @Test
    @DisplayName("多个子节点 - children 列表有序")
    void multipleChildrenOrdered() {
        TaskNode root = new TaskNode("root");
        TaskNode c1 = root.createChild("c1");
        TaskNode c2 = root.createChild("c2");
        TaskNode c3 = root.createChild("c3");

        assertThat(root.getChildren()).containsExactly(c1, c2, c3);
    }

    @Test
    @DisplayName("getRoot - 从任意节点获取根节点")
    void getRootFromAnyNode() {
        TaskNode root = new TaskNode("root");
        TaskNode child = root.createChild("child");
        TaskNode grandchild = child.createChild("grandchild");

        assertThat(grandchild.getRoot()).isSameAs(root);
        assertThat(child.getRoot()).isSameAs(root);
        assertThat(root.getRoot()).isSameAs(root);
    }

    @Test
    @DisplayName("getChildren - 返回不可修改列表")
    void childrenListIsUnmodifiable() {
        TaskNode root = new TaskNode("root");
        root.createChild("child");

        List<TaskNode> children = root.getChildren();
        assertThatThrownBy(() -> children.add(new TaskNode("hack")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== 状态转换 ====================

    @Test
    @DisplayName("complete - 正常完成")
    void completeTask() {
        TaskNode node = new TaskNode("task");
        node.complete();

        assertThat(node.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(node.getCompletedMillis()).isNotNull();
        assertThat(node.getDurationMillis()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("fail - 标记失败")
    void failTask() {
        TaskNode node = new TaskNode("task");
        node.fail();

        assertThat(node.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("fail(String) - 带消息失败")
    void failTaskWithMessage() {
        TaskNode node = new TaskNode("task");
        node.fail("数据库错误");

        assertThat(node.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(node.getMessages()).isNotEmpty();
    }

    @Test
    @DisplayName("fail(Throwable) - 带异常失败")
    void failTaskWithThrowable() {
        TaskNode node = new TaskNode("task");
        node.fail(new RuntimeException("超时"));

        assertThat(node.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(node.getMessages()).isNotEmpty();
    }

    @Test
    @DisplayName("complete - 已完成再次完成抛出异常")
    void completeAlreadyCompletedThrows() {
        TaskNode node = new TaskNode("task");
        node.complete();
        assertThatIllegalStateException().isThrownBy(node::complete);
    }

    @Test
    @DisplayName("fail - 已完成再次失败抛出异常")
    void failAlreadyCompletedThrows() {
        TaskNode node = new TaskNode("task");
        node.complete();
        assertThatIllegalStateException().isThrownBy(node::fail);
    }

    // ==================== 消息记录 ====================

    @Test
    @DisplayName("addInfo - 添加信息消息")
    void addInfoMessage() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addInfo("测试消息");

        assertThat(msg).isNotNull();
        assertThat(msg.getContent()).isEqualTo("测试消息");
        assertThat(node.getMessages()).hasSize(1);
    }

    @Test
    @DisplayName("addError - 添加错误消息")
    void addErrorMessage() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addError("错误发生");

        assertThat(msg).isNotNull();
        assertThat(msg.isAlert()).isTrue();
    }

    @Test
    @DisplayName("addDebug - 添加调试消息")
    void addDebugMessage() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addDebug("调试信息");
        assertThat(msg).isNotNull();
    }

    @Test
    @DisplayName("addWarn - 添加警告消息")
    void addWarnMessage() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addWarn("警告信息");
        assertThat(msg).isNotNull();
    }

    @Test
    @DisplayName("addMessage(content, type) - 类型消息")
    void addTypedMessage() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addMessage("变更记录", MessageType.CHANGE);
        assertThat(msg.isChange()).isTrue();
    }

    @Test
    @DisplayName("addMessage(content, label) - 自定义标签")
    void addCustomLabelMessage() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addMessage("内容", "自定义标签");
        assertThat(msg.hasCustomLabel()).isTrue();
        assertThat(msg.getCustomLabel()).isEqualTo("自定义标签");
    }

    @Test
    @DisplayName("addMessage - null 内容抛出异常")
    void addMessageNullContentThrows() {
        TaskNode node = new TaskNode("task");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> node.addMessage(null, MessageType.PROCESS));
    }

    @Test
    @DisplayName("addMessage - null 类型抛出异常")
    void addMessageNullTypeThrows() {
        TaskNode node = new TaskNode("task");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> node.addMessage("content", (MessageType) null));
    }

    @Test
    @DisplayName("addError(Throwable) - 异常消息")
    void addErrorThrowable() {
        TaskNode node = new TaskNode("task");
        Message msg = node.addError(new RuntimeException("boom"));
        assertThat(msg.getContent()).contains("boom");
    }

    @Test
    @DisplayName("getMessages - 返回不可修改列表")
    void messagesListIsUnmodifiable() {
        TaskNode node = new TaskNode("task");
        node.addInfo("msg");
        List<Message> messages = node.getMessages();
        assertThatThrownBy(() -> messages.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== 时间戳和持续时长 ====================

    @Test
    @DisplayName("时间戳 - 创建时间正确")
    void timestampsAreSet() {
        long before = System.currentTimeMillis();
        TaskNode node = new TaskNode("task");
        long after = System.currentTimeMillis();

        assertThat(node.getCreatedMillis()).isBetween(before, after);
        assertThat(node.getCreatedNanos()).isPositive();
    }

    @Test
    @DisplayName("duration - 未完成时返回 null")
    void durationNullWhenNotCompleted() {
        TaskNode node = new TaskNode("task");
        assertThat(node.getDurationMillis()).isNull();
        assertThat(node.getDurationNanos()).isNull();
    }

    @Test
    @DisplayName("selfDurationNanos - 始终有值")
    void selfDurationAlwaysAvailable() {
        TaskNode node = new TaskNode("task");
        assertThat(node.getSelfDurationNanos()).isGreaterThanOrEqualTo(0);
        assertThat(node.getSelfDurationMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("accumulatedDuration - 包含子节点")
    void accumulatedDurationIncludesChildren() {
        TaskNode root = new TaskNode("root");
        root.createChild("child1");
        root.createChild("child2");
        assertThat(root.getAccumulatedDurationNanos()).isGreaterThanOrEqualTo(root.getSelfDurationNanos());
    }

    // ==================== equals / hashCode / toString ====================

    @Test
    @DisplayName("equals - 基于 nodeId")
    void equalsBasedOnNodeId() {
        TaskNode n1 = new TaskNode("same");
        TaskNode n2 = new TaskNode("same");
        assertThat(n1).isNotEqualTo(n2);
        assertThat(n1).isEqualTo(n1);
        assertThat(n1).isNotEqualTo(null);
    }

    @Test
    @DisplayName("toString - 包含路径和状态")
    void toStringContainsInfo() {
        TaskNode node = new TaskNode("test");
        assertThat(node.toString()).contains("test").contains("RUNNING");
    }
}
