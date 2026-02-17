package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link Message} 单元测试
 *
 * <p>覆盖工厂方法、不可变性、类型判断和显示标签。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class MessageTest {

    // ==================== 工厂方法 ====================

    @Test
    @DisplayName("withType - 正常创建")
    void withTypeNormal() {
        Message msg = Message.withType("内容", MessageType.PROCESS);
        assertThat(msg.getContent()).isEqualTo("内容");
        assertThat(msg.getType()).isEqualTo(MessageType.PROCESS);
        assertThat(msg.getMessageId()).isNotEmpty();
        assertThat(msg.getTimestampMillis()).isPositive();
        assertThat(msg.getTimestampNanos()).isPositive();
        assertThat(msg.getThreadName()).isNotEmpty();
        assertThat(msg.hasCustomLabel()).isFalse();
    }

    @Test
    @DisplayName("withType - null 内容抛出异常")
    void withTypeNullContentThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.withType(null, MessageType.PROCESS));
    }

    @Test
    @DisplayName("withType - 空内容抛出异常")
    void withTypeEmptyContentThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.withType("", MessageType.PROCESS));
    }

    @Test
    @DisplayName("withType - null 类型抛出异常")
    void withTypeNullTypeThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.withType("content", null));
    }

    @Test
    @DisplayName("withType - 内容自动 trim")
    void withTypeTrimsContent() {
        Message msg = Message.withType("  hello  ", MessageType.PROCESS);
        assertThat(msg.getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("withLabel - 正常创建")
    void withLabelNormal() {
        Message msg = Message.withLabel("内容", "自定义");
        assertThat(msg.getContent()).isEqualTo("内容");
        assertThat(msg.getCustomLabel()).isEqualTo("自定义");
        assertThat(msg.hasCustomLabel()).isTrue();
        assertThat(msg.getType()).isNull();
    }

    @Test
    @DisplayName("withLabel - null 标签抛出异常")
    void withLabelNullLabelThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.withLabel("content", null));
    }

    @Test
    @DisplayName("withLabel - 空标签抛出异常")
    void withLabelEmptyLabelThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.withLabel("content", ""));
    }

    // ==================== 快捷工厂方法 ====================

    @Test
    @DisplayName("info - 创建信息消息")
    void infoMessage() {
        Message msg = Message.info("信息");
        assertThat(msg.getContent()).isEqualTo("信息");
        assertThat(msg.getType()).isEqualTo(MessageType.PROCESS);
        assertThat(msg.isProcess()).isTrue();
    }

    @Test
    @DisplayName("debug - 创建调试消息")
    void debugMessage() {
        Message msg = Message.debug("调试");
        assertThat(msg.getContent()).isEqualTo("调试");
        assertThat(msg.getType()).isEqualTo(MessageType.METRIC);
        assertThat(msg.isMetric()).isTrue();
    }

    @Test
    @DisplayName("error(String) - 创建错误消息")
    void errorStringMessage() {
        Message msg = Message.error("错误");
        assertThat(msg.getContent()).isEqualTo("错误");
        assertThat(msg.getType()).isEqualTo(MessageType.ALERT);
        assertThat(msg.isAlert()).isTrue();
    }

    @Test
    @DisplayName("warn - 创建警告消息")
    void warnMessage() {
        Message msg = Message.warn("警告");
        assertThat(msg.getContent()).isEqualTo("警告");
        assertThat(msg.isAlert()).isTrue();
    }

    @Test
    @DisplayName("error(Throwable) - 从异常创建")
    void errorThrowableMessage() {
        Message msg = Message.error(new RuntimeException("boom"));
        assertThat(msg.getContent()).isEqualTo("boom");
        assertThat(msg.isAlert()).isTrue();
    }

    @Test
    @DisplayName("error(Throwable) - null message 使用类名")
    void errorThrowableNullMessage() {
        Message msg = Message.error(new RuntimeException());
        assertThat(msg.getContent()).isEqualTo("RuntimeException");
    }

    @Test
    @DisplayName("error(Throwable) - null 抛出异常")
    void errorNullThrowableThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.error((Throwable) null));
    }

    @Test
    @DisplayName("info - null 内容抛出异常")
    void infoNullContentThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Message.info(null));
    }

    // ==================== 类型判断 ====================

    @Test
    @DisplayName("isAlert - ALERT 类型返回 true")
    void isAlertForAlertType() {
        assertThat(Message.error("err").isAlert()).isTrue();
        assertThat(Message.info("info").isAlert()).isFalse();
    }

    @Test
    @DisplayName("isProcess - PROCESS 类型返回 true")
    void isProcessForProcessType() {
        assertThat(Message.info("info").isProcess()).isTrue();
    }

    @Test
    @DisplayName("isMetric - METRIC 类型返回 true")
    void isMetricForMetricType() {
        assertThat(Message.debug("debug").isMetric()).isTrue();
    }

    @Test
    @DisplayName("isChange - CHANGE 类型返回 true")
    void isChangeForChangeType() {
        Message msg = Message.withType("change", MessageType.CHANGE);
        assertThat(msg.isChange()).isTrue();
    }

    @Test
    @DisplayName("自定义标签消息 - 类型判断均为 false")
    void customLabelTypeChecksAreFalse() {
        Message msg = Message.withLabel("content", "custom");
        assertThat(msg.isAlert()).isFalse();
        assertThat(msg.isProcess()).isFalse();
        assertThat(msg.isMetric()).isFalse();
        assertThat(msg.isChange()).isFalse();
    }

    // ==================== 显示标签 ====================

    @Test
    @DisplayName("getDisplayLabel - 优先自定义标签")
    void displayLabelPrefersCustom() {
        Message msg = Message.withLabel("content", "My Label");
        assertThat(msg.getDisplayLabel()).isEqualTo("My Label");
    }

    @Test
    @DisplayName("getDisplayLabel - 无自定义则使用 type 名称")
    void displayLabelFallsBackToType() {
        Message msg = Message.withType("content", MessageType.PROCESS);
        assertThat(msg.getDisplayLabel()).isEqualTo(MessageType.PROCESS.getDisplayName());
    }

    // ==================== equals / hashCode / toString ====================

    @Test
    @DisplayName("equals - 基于 messageId")
    void equalsBasedOnMessageId() {
        Message m1 = Message.info("same");
        Message m2 = Message.info("same");
        assertThat(m1).isNotEqualTo(m2);
        assertThat(m1).isEqualTo(m1);
        assertThat(m1).isNotEqualTo(null);
    }

    @Test
    @DisplayName("toString - 包含标签和内容")
    void toStringContainsInfo() {
        Message msg = Message.info("hello");
        String str = msg.toString();
        assertThat(str).contains("hello");
    }
}
