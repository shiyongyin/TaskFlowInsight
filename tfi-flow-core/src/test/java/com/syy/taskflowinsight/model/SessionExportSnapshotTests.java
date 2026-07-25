package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageSeverity;
import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionExportSnapshotTests {

    private static final long CAPTURE_MILLIS = 10_000L;
    private static final long CAPTURE_NANOS = 10_000_000L;
    private static final long SESSION_CREATED_MILLIS = 1_000L;
    private static final long SESSION_CREATED_NANOS = 1_000_000L;
    private static final long TASK_CREATED_MILLIS = 1_100L;
    private static final long TASK_CREATED_NANOS = 2_000_000L;
    private static final SessionExportSnapshot.Limits GENEROUS_LIMITS =
            new SessionExportSnapshot.Limits(10, 100, 1_000, 100_000L);

    @Test
    void limitsDefaultsMatchPublicBudgetsAndRejectOutOfRangeValues() {
        assertThat(SessionExportSnapshot.Limits.defaults()).isEqualTo(
                new SessionExportSnapshot.Limits(
                        FlowConfigDefaults.MAX_EXPORT_DEPTH,
                        FlowConfigDefaults.MAX_EXPORT_NODES,
                        FlowConfigDefaults.MAX_EXPORT_PAYLOAD_ENTRIES,
                        FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS));
        assertThatThrownBy(() -> new SessionExportSnapshot.Limits(-1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Export limits are out of range");
        assertThatThrownBy(() -> new SessionExportSnapshot.Limits(
                1, FlowConfigDefaults.MAX_EXPORT_NODES + 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Export limits are out of range");
        assertThatThrownBy(() -> new SessionExportSnapshot.Limits(
                1, 1, FlowConfigDefaults.MAX_EXPORT_PAYLOAD_ENTRIES + 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Export limits are out of range");
        assertThatThrownBy(() -> new SessionExportSnapshot.Limits(
                1, 1, 1, FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Export limits are out of range");
    }

    @Test
    void publicConstructorsDefensivelyCopyAndRejectMutableValues() {
        List<SessionExportSnapshot.MessageSnapshot> messages = new ArrayList<>();
        messages.add(message(TASK_CREATED_NANOS + 1));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("nullable", null);
        attributes.put("count", 1);
        List<String> tags = new ArrayList<>(List.of("stable"));
        List<SessionExportSnapshot.TaskSnapshot> children = new ArrayList<>();

        SessionExportSnapshot.TaskSnapshot task = runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                messages, attributes, tags, children, false);
        messages.clear();
        attributes.clear();
        tags.clear();
        children.add(runningTask("late", "late", "root/late", 1, 0,
                TASK_CREATED_NANOS + 1, List.of(), Map.of(), List.of(), List.of(), false));

        assertThat(task.messages()).hasSize(1);
        assertThat(task.attributes()).containsEntry("nullable", null).containsEntry("count", 1);
        assertThat(task.tags()).containsExactly("stable");
        assertThat(task.children()).isEmpty();
        assertThatThrownBy(() -> task.messages().add(message(TASK_CREATED_NANOS + 2)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> task.attributes().put("other", 2))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(), Map.of("raw", new StringBuilder("mutable")),
                List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen attribute");
    }

    @Test
    void publicConstructorRejectsNullRoot() {
        assertThatThrownBy(() -> new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", "root", "1", "thread",
                SessionStatus.RUNNING, SESSION_CREATED_MILLIS, SESSION_CREATED_NANOS,
                null, null, null, null, GENEROUS_LIMITS, null,
                new SessionExportSnapshot.Statistics(1, 0, 0), false))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("root");
    }

    @Test
    void publicConstructorRejectsInconsistentSessionTerminalTuple() {
        SessionExportSnapshot.TaskSnapshot root = runningRoot();

        assertThatThrownBy(() -> new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", "root", "1", "thread",
                SessionStatus.RUNNING, SESSION_CREATED_MILLIS, SESSION_CREATED_NANOS,
                CAPTURE_MILLIS, null, null, null, GENEROUS_LIMITS, root,
                statistics(root), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUNNING session");
        assertThatThrownBy(() -> new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", "root", "1", "thread",
                SessionStatus.COMPLETED, SESSION_CREATED_MILLIS, SESSION_CREATED_NANOS,
                null, null, null, null, GENEROUS_LIMITS, root,
                statistics(root), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal session");
    }

    @Test
    void taskSnapshotConstructorRejectsInconsistentTerminalTuple() {
        assertThatThrownBy(() -> task(
                "n", "root", "root", 0, 0, TASK_CREATED_MILLIS, TASK_CREATED_NANOS,
                TaskStatus.RUNNING, TASK_CREATED_MILLIS + 1, null, null, null,
                0, 1, 0, 1, List.of(), Map.of(), List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUNNING task");
        assertThatThrownBy(() -> task(
                "n", "root", "root", 0, 0, TASK_CREATED_MILLIS, TASK_CREATED_NANOS,
                TaskStatus.COMPLETED, null, null, null, null,
                0, 1, 0, 1, List.of(), Map.of(), List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal task");
    }

    @Test
    void publicConstructorRejectsTerminalSessionWithRunningRoot() {
        SessionExportSnapshot.TaskSnapshot root = runningRoot();

        assertThatThrownBy(() -> terminalSnapshot(SessionStatus.COMPLETED, root, GENEROUS_LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal root");
    }

    @Test
    void publicConstructorRejectsRootCompletionAfterSessionCompletion() {
        SessionExportSnapshot.TaskSnapshot root = terminalTask(
                "n", "root", "root", 0, 0, TaskStatus.COMPLETED,
                TASK_CREATED_NANOS, 9_000_001L, List.of());

        assertThatThrownBy(() -> terminalSnapshot(SessionStatus.COMPLETED, root, GENEROUS_LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after session completion");

        SessionExportSnapshot.TaskSnapshot equal = terminalTask(
                "n", "root", "root", 0, 0, TaskStatus.COMPLETED,
                TASK_CREATED_NANOS, 9_000_000L, List.of());
        assertThat(terminalSnapshot(SessionStatus.COMPLETED, equal, GENEROUS_LIMITS).root())
                .isSameAs(equal);
    }

    @Test
    void publicConstructorRejectsInconsistentTaskTreeStatisticsAndTruncation() {
        SessionExportSnapshot.TaskSnapshot root = runningRoot();

        assertThatThrownBy(() -> runningSnapshot(
                root, GENEROUS_LIMITS, new SessionExportSnapshot.Statistics(2, 0, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statistics");
        assertThatThrownBy(() -> runningSnapshot(root, GENEROUS_LIMITS, statistics(root), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("truncated");

        SessionExportSnapshot.TaskSnapshot truncatedRoot = runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(), Map.of(), List.of(), List.of(), true);
        assertThatThrownBy(() -> runningSnapshot(
                truncatedRoot, GENEROUS_LIMITS, statistics(truncatedRoot), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    void publicConstructorRejectsInvalidDepthSequenceAndAccumulatedDurations() {
        SessionExportSnapshot.TaskSnapshot wrongDepth = runningTask(
                "c", "child", "root/child", 2, 0, TASK_CREATED_NANOS + 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot root = runningTaskWithChildren(List.of(wrongDepth));
        assertThatThrownBy(() -> runningSnapshot(root, GENEROUS_LIMITS, statistics(root), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");

        assertThatThrownBy(() -> task(
                "n", "root", "root", 0, 0, TASK_CREATED_MILLIS, TASK_CREATED_NANOS,
                TaskStatus.RUNNING, null, null, null, null,
                8, 8_000_000L, 7, 7_000_000L,
                List.of(), Map.of(), List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accumulated");
    }

    @Test
    void publicConstructorRejectsDagDuplicateNodeIdsAndInvalidPaths() {
        SessionExportSnapshot.TaskSnapshot child = runningTask(
                "c", "child", "root/child", 1, 0, TASK_CREATED_NANOS + 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot dagRoot = runningTaskWithChildren(List.of(child, child));
        assertThatThrownBy(() -> runningSnapshot(
                dagRoot, GENEROUS_LIMITS, new SessionExportSnapshot.Statistics(3, 1, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAG");

        SessionExportSnapshot.TaskSnapshot first = runningTask(
                "same", "first", "root/first", 1, 0, TASK_CREATED_NANOS + 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot second = runningTask(
                "same", "second", "root/second", 1, 1, TASK_CREATED_NANOS + 2,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot duplicateRoot = runningTaskWithChildren(List.of(first, second));
        assertThatThrownBy(() -> runningSnapshot(
                duplicateRoot, GENEROUS_LIMITS, new SessionExportSnapshot.Statistics(3, 1, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node ID");

        SessionExportSnapshot.TaskSnapshot invalidPath = runningTask(
                "c", "child", "wrong", 1, 0, TASK_CREATED_NANOS + 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot pathRoot = runningTaskWithChildren(List.of(invalidPath));
        assertThatThrownBy(() -> runningSnapshot(
                pathRoot, GENEROUS_LIMITS, new SessionExportSnapshot.Statistics(2, 1, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void publicConstructorEnforcesAttachedNodePayloadAndTextLimits() {
        SessionExportSnapshot.TaskSnapshot child = runningTask(
                "c", "child", "root/child", 1, 0, TASK_CREATED_NANOS + 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot rootWithChild = runningTaskWithChildren(List.of(child));

        assertThatThrownBy(() -> runningSnapshot(
                rootWithChild, new SessionExportSnapshot.Limits(0, 10, 10, 1_000),
                new SessionExportSnapshot.Statistics(2, 1, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth limit");
        assertThatThrownBy(() -> runningSnapshot(
                rootWithChild, new SessionExportSnapshot.Limits(2, 1, 10, 1_000),
                new SessionExportSnapshot.Statistics(2, 1, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("node limit");

        SessionExportSnapshot.TaskSnapshot payloadRoot = runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(message(TASK_CREATED_NANOS + 1)), Map.of("key", 1),
                List.of(), List.of(), false);
        assertThatThrownBy(() -> runningSnapshot(
                payloadRoot, new SessionExportSnapshot.Limits(2, 10, 1, 1_000),
                statistics(payloadRoot), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Export payload entry limit exceeded: 1");
    }

    @Test
    void publicConstructorAppliesTheSameTextBoundaryToManualGraphs() {
        SessionExportSnapshot.TaskSnapshot root = runningRoot();
        SessionExportSnapshot.Limits exact = new SessionExportSnapshot.Limits(1, 1, 1, 27);
        SessionExportSnapshot.Limits tooSmall = new SessionExportSnapshot.Limits(1, 1, 1, 26);

        assertThat(runningSnapshot(root, exact, statistics(root), false).root()).isSameAs(root);
        assertThatThrownBy(() -> runningSnapshot(root, tooSmall, statistics(root), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Export text character limit exceeded: 26");
    }

    @Test
    void publicConstructorRejectsIncorrectWallClockDurationEvenWhenNegativeIsLegal() {
        SessionExportSnapshot.TaskSnapshot root = terminalTaskWithWallClock(
                2_000L, 1_500L, -500L, TASK_CREATED_NANOS, 9_000_000L);
        SessionExportSnapshot valid = new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", "root", "1", "thread",
                SessionStatus.COMPLETED, 2_000L, SESSION_CREATED_NANOS,
                1_000L, 9_000_000L, -1_000L, 8_000_000L,
                GENEROUS_LIMITS, root, statistics(root), false);
        assertThat(valid.durationMillis()).isEqualTo(-1_000L);

        assertThatThrownBy(() -> new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", "root", "1", "thread",
                SessionStatus.COMPLETED, 2_000L, SESSION_CREATED_NANOS,
                1_000L, 9_000_000L, -999L, 8_000_000L,
                GENEROUS_LIMITS, root, statistics(root), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wall-clock duration");
    }

    @Test
    void publicConstructorRejectsPostCaptureTaskSessionAndMessageNanos() {
        SessionExportSnapshot.TaskSnapshot terminalRoot = terminalTask(
                "n", "root", "root", 0, 0, TaskStatus.COMPLETED,
                TASK_CREATED_NANOS, CAPTURE_NANOS + 1, List.of());
        assertThatThrownBy(() -> runningSnapshot(
                terminalRoot, GENEROUS_LIMITS, statistics(terminalRoot), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capture");

        SessionExportSnapshot.TaskSnapshot futureTask = runningTask(
                "n", "root", "root", 0, 0, CAPTURE_NANOS + 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        assertThatThrownBy(() -> runningSnapshot(
                futureTask, GENEROUS_LIMITS, statistics(futureTask), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("created after capture");

        SessionExportSnapshot.TaskSnapshot earlyChild = runningTask(
                "c", "child", "root/child", 1, 0, TASK_CREATED_NANOS - 1,
                List.of(), Map.of(), List.of(), List.of(), false);
        SessionExportSnapshot.TaskSnapshot parent = runningTaskWithChildren(List.of(earlyChild));
        assertThatThrownBy(() -> runningSnapshot(
                parent, GENEROUS_LIMITS, new SessionExportSnapshot.Statistics(2, 1, 0), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before parent");

        SessionExportSnapshot.TaskSnapshot earlyMessage = runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(message(TASK_CREATED_NANOS - 1)), Map.of(), List.of(), List.of(), false);
        assertThatThrownBy(() -> runningSnapshot(
                earlyMessage, GENEROUS_LIMITS, statistics(earlyMessage), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message timestamp");

        SessionExportSnapshot.TaskSnapshot lateMessage = runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(message(CAPTURE_NANOS + 1)), Map.of(), List.of(), List.of(), false);
        assertThatThrownBy(() -> runningSnapshot(
                lateMessage, GENEROUS_LIMITS, statistics(lateMessage), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message timestamp");
    }

    @Test
    void compactConstructorRejectsRawBigNumberSubclasses() {
        assertThatThrownBy(() -> runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(), Map.of("big", new HostileBigInteger()),
                List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen attribute");
        assertThatThrownBy(() -> runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(), Map.of("decimal", new HostileBigDecimal()),
                List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen attribute");
    }

    private static SessionExportSnapshot runningSnapshot(
            SessionExportSnapshot.TaskSnapshot root,
            SessionExportSnapshot.Limits limits,
            SessionExportSnapshot.Statistics statistics,
            boolean truncated) {
        return new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", root.taskName(), "1", "thread",
                SessionStatus.RUNNING, SESSION_CREATED_MILLIS, SESSION_CREATED_NANOS,
                null, null, null, null, limits, root, statistics, truncated);
    }

    private static SessionExportSnapshot terminalSnapshot(
            SessionStatus status,
            SessionExportSnapshot.TaskSnapshot root,
            SessionExportSnapshot.Limits limits) {
        return new SessionExportSnapshot(
                CAPTURE_MILLIS, CAPTURE_NANOS, "s", root.taskName(), "1", "thread",
                status, SESSION_CREATED_MILLIS, SESSION_CREATED_NANOS,
                9_000L, 9_000_000L, 8_000L, 8_000_000L,
                limits, root, statistics(root), false);
    }

    private static SessionExportSnapshot.TaskSnapshot runningRoot() {
        return runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(), Map.of(), List.of(), List.of(), false);
    }

    private static SessionExportSnapshot.TaskSnapshot runningTaskWithChildren(
            List<SessionExportSnapshot.TaskSnapshot> children) {
        return runningTask(
                "n", "root", "root", 0, 0, TASK_CREATED_NANOS,
                List.of(), Map.of(), List.of(), children, false);
    }

    private static SessionExportSnapshot.TaskSnapshot runningTask(
            String id,
            String name,
            String path,
            int depth,
            int sequence,
            long createdNanos,
            List<SessionExportSnapshot.MessageSnapshot> messages,
            Map<String, Object> attributes,
            List<String> tags,
            List<SessionExportSnapshot.TaskSnapshot> children,
            boolean childrenTruncated) {
        long selfNanos = Math.max(0L, CAPTURE_NANOS - createdNanos);
        long accumulatedNanos = selfNanos;
        for (SessionExportSnapshot.TaskSnapshot child : children) {
            accumulatedNanos += child.accumulatedDurationNanos();
        }
        return task(
                id, name, path, depth, sequence, TASK_CREATED_MILLIS, createdNanos,
                TaskStatus.RUNNING, null, null, null, null,
                selfNanos / 1_000_000L, selfNanos,
                accumulatedNanos / 1_000_000L, accumulatedNanos,
                messages, attributes, tags, children, childrenTruncated);
    }

    private static SessionExportSnapshot.TaskSnapshot terminalTask(
            String id,
            String name,
            String path,
            int depth,
            int sequence,
            TaskStatus status,
            long createdNanos,
            long completedNanos,
            List<SessionExportSnapshot.TaskSnapshot> children) {
        long durationNanos = completedNanos - createdNanos;
        long accumulatedNanos = durationNanos;
        for (SessionExportSnapshot.TaskSnapshot child : children) {
            accumulatedNanos += child.accumulatedDurationNanos();
        }
        return task(
                id, name, path, depth, sequence, TASK_CREATED_MILLIS, createdNanos,
                status, 8_000L, completedNanos, 6_900L, durationNanos,
                durationNanos / 1_000_000L, durationNanos,
                accumulatedNanos / 1_000_000L, accumulatedNanos,
                List.of(), Map.of(), List.of(), children, false);
    }

    private static SessionExportSnapshot.TaskSnapshot terminalTaskWithWallClock(
            long createdMillis,
            long completedMillis,
            long durationMillis,
            long createdNanos,
            long completedNanos) {
        long durationNanos = completedNanos - createdNanos;
        return task(
                "n", "root", "root", 0, 0, createdMillis, createdNanos,
                TaskStatus.COMPLETED, completedMillis, completedNanos,
                durationMillis, durationNanos,
                durationNanos / 1_000_000L, durationNanos,
                durationNanos / 1_000_000L, durationNanos,
                List.of(), Map.of(), List.of(), List.of(), false);
    }

    private static SessionExportSnapshot.TaskSnapshot task(
            String id,
            String name,
            String path,
            int depth,
            int sequence,
            long createdMillis,
            long createdNanos,
            TaskStatus status,
            Long completedMillis,
            Long completedNanos,
            Long durationMillis,
            Long durationNanos,
            long selfDurationMillis,
            long selfDurationNanos,
            long accumulatedDurationMillis,
            long accumulatedDurationNanos,
            List<SessionExportSnapshot.MessageSnapshot> messages,
            Map<String, Object> attributes,
            List<String> tags,
            List<SessionExportSnapshot.TaskSnapshot> children,
            boolean childrenTruncated) {
        return new SessionExportSnapshot.TaskSnapshot(
                id, name, path, depth, sequence, "thread", status,
                createdMillis, createdNanos, completedMillis, completedNanos,
                durationMillis, durationNanos, selfDurationMillis, selfDurationNanos,
                accumulatedDurationMillis, accumulatedDurationNanos,
                messages, attributes, tags, children, childrenTruncated);
    }

    private static SessionExportSnapshot.MessageSnapshot message(long timestampNanos) {
        return new SessionExportSnapshot.MessageSnapshot(
                "PROCESS", "process", MessageSeverity.INFO, null,
                "message", TASK_CREATED_MILLIS, timestampNanos, "thread");
    }

    private static SessionExportSnapshot.Statistics statistics(
            SessionExportSnapshot.TaskSnapshot root) {
        int count = 0;
        int maxDepth = 0;
        int messages = 0;
        List<SessionExportSnapshot.TaskSnapshot> pending = new ArrayList<>(List.of(root));
        while (!pending.isEmpty()) {
            SessionExportSnapshot.TaskSnapshot task = pending.remove(pending.size() - 1);
            count++;
            maxDepth = Math.max(maxDepth, task.depth());
            messages += task.messages().size();
            pending.addAll(task.children());
        }
        return new SessionExportSnapshot.Statistics(count, maxDepth, messages);
    }

    private static final class HostileBigInteger extends BigInteger {
        private HostileBigInteger() {
            super("1");
        }

        @Override
        public String toString() {
            throw new AssertionError("must not stringify hostile BigInteger");
        }
    }

    private static final class HostileBigDecimal extends BigDecimal {
        private HostileBigDecimal() {
            super("1.0");
        }

        @Override
        public String toString() {
            throw new AssertionError("must not stringify hostile BigDecimal");
        }
    }
}
