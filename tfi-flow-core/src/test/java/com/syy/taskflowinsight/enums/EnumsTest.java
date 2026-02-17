package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 枚举类（{@link MessageType}、{@link SessionStatus}、{@link TaskStatus}）单元测试
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class EnumsTest {

    // ==================== MessageType ====================

    @Nested
    @DisplayName("MessageType")
    class MessageTypeTests {

        @Test
        @DisplayName("枚举值数量")
        void enumValuesCount() {
            assertThat(MessageType.values()).hasSize(4);
        }

        @Test
        @DisplayName("displayName 非空")
        void displayNameNotEmpty() {
            for (MessageType type : MessageType.values()) {
                assertThat(type.getDisplayName()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("level 正确排序")
        void levelsAreOrdered() {
            assertThat(MessageType.PROCESS.getLevel()).isEqualTo(1);
            assertThat(MessageType.METRIC.getLevel()).isEqualTo(2);
            assertThat(MessageType.CHANGE.getLevel()).isEqualTo(3);
            assertThat(MessageType.ALERT.getLevel()).isEqualTo(4);
        }

        @Test
        @DisplayName("isAlert / isProcess / isMetric / isChange")
        void typeChecks() {
            assertThat(MessageType.ALERT.isAlert()).isTrue();
            assertThat(MessageType.PROCESS.isProcess()).isTrue();
            assertThat(MessageType.METRIC.isMetric()).isTrue();
            assertThat(MessageType.CHANGE.isChange()).isTrue();

            assertThat(MessageType.PROCESS.isAlert()).isFalse();
            assertThat(MessageType.ALERT.isProcess()).isFalse();
        }

        @Test
        @DisplayName("isImportant - 级别 >= 3")
        void isImportant() {
            assertThat(MessageType.CHANGE.isImportant()).isTrue();
            assertThat(MessageType.ALERT.isImportant()).isTrue();
            assertThat(MessageType.PROCESS.isImportant()).isFalse();
            assertThat(MessageType.METRIC.isImportant()).isFalse();
        }

        @Test
        @DisplayName("compareLevel - 正确比较")
        void compareLevel() {
            assertThat(MessageType.ALERT.compareLevel(MessageType.PROCESS)).isPositive();
            assertThat(MessageType.PROCESS.compareLevel(MessageType.ALERT)).isNegative();
            assertThat(MessageType.PROCESS.compareLevel(MessageType.PROCESS)).isZero();
        }

        @Test
        @DisplayName("compareLevel - null 抛出异常")
        void compareLevelNullThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> MessageType.ALERT.compareLevel(null));
        }

        @Test
        @DisplayName("toString 返回 displayName")
        void toStringReturnsDisplayName() {
            assertThat(MessageType.PROCESS.toString()).isEqualTo(MessageType.PROCESS.getDisplayName());
        }
    }

    // ==================== SessionStatus ====================

    @Nested
    @DisplayName("SessionStatus")
    class SessionStatusTests {

        @Test
        @DisplayName("枚举值数量")
        void enumValuesCount() {
            assertThat(SessionStatus.values()).hasSize(3);
        }

        @Test
        @DisplayName("isActive - 仅 RUNNING 为 true")
        void isActive() {
            assertThat(SessionStatus.RUNNING.isActive()).isTrue();
            assertThat(SessionStatus.COMPLETED.isActive()).isFalse();
            assertThat(SessionStatus.ERROR.isActive()).isFalse();
        }

        @Test
        @DisplayName("isTerminated - COMPLETED 和 ERROR 为 true")
        void isTerminated() {
            assertThat(SessionStatus.RUNNING.isTerminated()).isFalse();
            assertThat(SessionStatus.COMPLETED.isTerminated()).isTrue();
            assertThat(SessionStatus.ERROR.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("isCompleted / isError")
        void isCompletedAndIsError() {
            assertThat(SessionStatus.COMPLETED.isCompleted()).isTrue();
            assertThat(SessionStatus.ERROR.isError()).isTrue();
            assertThat(SessionStatus.RUNNING.isCompleted()).isFalse();
            assertThat(SessionStatus.RUNNING.isError()).isFalse();
        }

        @Test
        @DisplayName("canTransitionTo - 状态转换规则")
        void canTransitionTo() {
            assertThat(SessionStatus.RUNNING.canTransitionTo(SessionStatus.COMPLETED)).isTrue();
            assertThat(SessionStatus.RUNNING.canTransitionTo(SessionStatus.ERROR)).isTrue();
            assertThat(SessionStatus.RUNNING.canTransitionTo(SessionStatus.RUNNING)).isFalse();
            assertThat(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.RUNNING)).isFalse();
            assertThat(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.ERROR)).isFalse();
            assertThat(SessionStatus.ERROR.canTransitionTo(SessionStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("canTransitionTo - null 抛出异常")
        void canTransitionToNullThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SessionStatus.RUNNING.canTransitionTo(null));
        }
    }

    // ==================== TaskStatus ====================

    @Nested
    @DisplayName("TaskStatus")
    class TaskStatusTests {

        @Test
        @DisplayName("枚举值数量")
        void enumValuesCount() {
            assertThat(TaskStatus.values()).hasSize(3);
        }

        @Test
        @DisplayName("isActive - 仅 RUNNING 为 true")
        void isActive() {
            assertThat(TaskStatus.RUNNING.isActive()).isTrue();
            assertThat(TaskStatus.COMPLETED.isActive()).isFalse();
            assertThat(TaskStatus.FAILED.isActive()).isFalse();
        }

        @Test
        @DisplayName("isTerminated - COMPLETED 和 FAILED 为 true")
        void isTerminated() {
            assertThat(TaskStatus.RUNNING.isTerminated()).isFalse();
            assertThat(TaskStatus.COMPLETED.isTerminated()).isTrue();
            assertThat(TaskStatus.FAILED.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("isSuccessful / isFailed")
        void isSuccessfulAndIsFailed() {
            assertThat(TaskStatus.COMPLETED.isSuccessful()).isTrue();
            assertThat(TaskStatus.FAILED.isFailed()).isTrue();
            assertThat(TaskStatus.RUNNING.isSuccessful()).isFalse();
            assertThat(TaskStatus.RUNNING.isFailed()).isFalse();
        }

        @Test
        @DisplayName("canTransitionTo - 状态转换规则")
        void canTransitionTo() {
            assertThat(TaskStatus.RUNNING.canTransitionTo(TaskStatus.COMPLETED)).isTrue();
            assertThat(TaskStatus.RUNNING.canTransitionTo(TaskStatus.FAILED)).isTrue();
            assertThat(TaskStatus.RUNNING.canTransitionTo(TaskStatus.RUNNING)).isFalse();
            assertThat(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.RUNNING)).isFalse();
            assertThat(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.FAILED)).isFalse();
            assertThat(TaskStatus.FAILED.canTransitionTo(TaskStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("canTransitionTo - null 抛出异常")
        void canTransitionToNullThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TaskStatus.RUNNING.canTransitionTo(null));
        }
    }
}
