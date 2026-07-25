package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在 Session 的唯一 write-side gate 内读取 mutable model，并在锁外组装语义快照。
 *
 * <p>锁内结果只能包含 framework-owned raw container 与不可变标量，不能携带 Session、TaskNode、Message
 * 引用；锁释放后才构造并校验 public snapshot。该实现保持 package-private，避免 formatter 成为第二个
 * traversal owner。属性值只按精确运行时类读取；未知对象仅访问 final {@link Class#getName()} 元数据。
 */
final class SessionSnapshotCapturer {

    private SessionSnapshotCapturer() {
    }

    static SessionExportSnapshot capture(
            Session session, SessionExportSnapshot.Limits limits, CaptureClock clock) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(clock, "clock");
        RawCapture raw = session.captureExport(() -> {
            long captureMillis = clock.currentTimeMillis();
            long captureNanos = clock.nanoTime();
            return captureWhileWriteLocked(
                    session, limits, captureMillis, captureNanos);
        });
        return assembleSnapshot(raw);
    }

    private static RawCapture captureWhileWriteLocked(
            Session session,
            SessionExportSnapshot.Limits limits,
            long captureMillis,
            long captureNanos) {
        CaptureBudget budget = new CaptureBudget(limits);
        String sessionId = session.getSessionId();
        String threadId = session.getThreadId();
        String threadName = session.getThreadName();
        budget.addText(sessionId, threadId, threadName);

        TaskNode rootSource = session.getRootTask();
        RawNode root = new RawNode(0, 0);
        List<RawNode> nodes = captureTree(rootSource, root, limits, captureNanos, budget);
        budget.addText(root.taskName);

        return new RawCapture(
                captureMillis,
                captureNanos,
                sessionId,
                threadId,
                threadName,
                session.getStatus(),
                session.getCreatedMillis(),
                session.getCreatedNanos(),
                session.getCompletedMillis(),
                session.getCompletedNanos(),
                session.getDurationMillis(),
                session.getDurationNanos(),
                limits,
                root,
                nodes);
    }

    private static SessionExportSnapshot assembleSnapshot(RawCapture raw) {
        IdentityHashMap<RawNode, SessionExportSnapshot.TaskSnapshot> built =
                new IdentityHashMap<>();
        int totalMessages = 0;
        int maxDepth = 0;
        boolean truncated = false;
        for (int index = raw.nodes.size() - 1; index >= 0; index--) {
            RawNode rawNode = raw.nodes.get(index);
            List<SessionExportSnapshot.TaskSnapshot> children =
                    new ArrayList<>(rawNode.children.size());
            long accumulatedNanos = rawNode.selfDurationNanos;
            for (RawNode child : rawNode.children) {
                SessionExportSnapshot.TaskSnapshot childSnapshot = built.get(child);
                children.add(childSnapshot);
                accumulatedNanos = addExact(
                        accumulatedNanos,
                        childSnapshot.accumulatedDurationNanos(),
                        "accumulated duration");
            }
            SessionExportSnapshot.TaskSnapshot snapshot = new SessionExportSnapshot.TaskSnapshot(
                    rawNode.nodeId,
                    rawNode.taskName,
                    rawNode.taskPath,
                    rawNode.depth,
                    rawNode.sequence,
                    rawNode.threadName,
                    rawNode.status,
                    rawNode.createdMillis,
                    rawNode.createdNanos,
                    rawNode.completedMillis,
                    rawNode.completedNanos,
                    rawNode.durationMillis,
                    rawNode.durationNanos,
                    rawNode.selfDurationNanos / 1_000_000L,
                    rawNode.selfDurationNanos,
                    accumulatedNanos / 1_000_000L,
                    accumulatedNanos,
                    rawNode.messages,
                    rawNode.attributes,
                    rawNode.tags,
                    children,
                    rawNode.childrenTruncated);
            built.put(rawNode, snapshot);
            totalMessages = addExact(totalMessages, rawNode.messages.size(), "message count");
            maxDepth = Math.max(maxDepth, rawNode.depth);
            truncated |= rawNode.childrenTruncated;
        }

        SessionExportSnapshot.TaskSnapshot rootSnapshot = built.get(raw.root);
        return new SessionExportSnapshot(
                raw.captureMillis,
                raw.captureNanos,
                raw.sessionId,
                rootSnapshot.taskName(),
                raw.threadId,
                raw.threadName,
                raw.status,
                raw.createdMillis,
                raw.createdNanos,
                raw.completedMillis,
                raw.completedNanos,
                raw.durationMillis,
                raw.durationNanos,
                raw.limits,
                rootSnapshot,
                new SessionExportSnapshot.Statistics(raw.nodes.size(), maxDepth, totalMessages),
                truncated);
    }

    private static List<RawNode> captureTree(
            TaskNode rootSource,
            RawNode root,
            SessionExportSnapshot.Limits limits,
            long captureNanos,
            CaptureBudget budget) {
        List<RawNode> nodes = new ArrayList<>();
        nodes.add(root);
        ArrayDeque<TraversalFrame> pending = new ArrayDeque<>();
        pending.push(new TraversalFrame(rootSource, root));

        while (!pending.isEmpty()) {
            TraversalFrame frame = pending.peek();
            if (!frame.initialized) {
                frame.node.capture(frame.source, captureNanos, budget);
                frame.childCount = frame.source.captureChildCount();
                frame.initialized = true;
                if (frame.node.depth >= limits.maxDepth()) {
                    frame.node.childrenTruncated = frame.childCount > 0;
                    pending.pop();
                    continue;
                }
            }

            if (frame.nextChild >= frame.childCount) {
                pending.pop();
                continue;
            }
            if (nodes.size() >= limits.maxNodes()) {
                frame.node.childrenTruncated = true;
                pending.pop();
                continue;
            }

            int sequence = frame.nextChild++;
            TaskNode childSource = frame.source.captureChildAt(sequence);
            RawNode child = new RawNode(frame.node.depth + 1, sequence);
            frame.node.children.add(child);
            nodes.add(child);
            pending.push(new TraversalFrame(childSource, child));
        }
        return nodes;
    }

    private static Object freezeAttribute(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> type = value.getClass();
        if (type == String.class || type == Boolean.class || type == Character.class
                || type == Byte.class || type == Short.class || type == Integer.class
                || type == Long.class || type == BigInteger.class || type == BigDecimal.class) {
            return value;
        }
        if (type == Float.class) {
            Float number = (Float) value;
            return Float.isFinite(number)
                    ? number
                    : SessionExportSnapshot.NonFiniteNumber.from(number);
        }
        if (type == Double.class) {
            Double number = (Double) value;
            return Double.isFinite(number)
                    ? number
                    : SessionExportSnapshot.NonFiniteNumber.from(number);
        }
        return new SessionExportSnapshot.UnsupportedValue(type.getName());
    }

    private static List<SessionExportSnapshot.MessageSnapshot> freezeMessages(
            List<Message> messages, CaptureBudget budget) {
        List<SessionExportSnapshot.MessageSnapshot> frozen = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            MessageType type = message.getType();
            String wireType = type == null ? null : type.getDisplayName();
            String customLabel = message.getCustomLabel();
            String displayLabel = customLabel != null
                    ? customLabel
                    : wireType != null ? wireType : "未知";
            SessionExportSnapshot.MessageSnapshot snapshot =
                    new SessionExportSnapshot.MessageSnapshot(
                            wireType,
                            displayLabel,
                            message.getSeverity(),
                            customLabel,
                            message.getContent(),
                            message.getTimestampMillis(),
                            message.getTimestampNanos(),
                            message.getThreadName());
            budget.addText(
                    snapshot.wireType(), snapshot.displayLabel(), snapshot.customLabel(),
                    snapshot.content(), snapshot.threadName());
            frozen.add(snapshot);
        }
        return List.copyOf(frozen);
    }

    private static Map<String, Object> freezeAttributes(
            Map<String, Object> attributes, CaptureBudget budget) {
        LinkedHashMap<String, Object> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            Object value = freezeAttribute(attribute.getValue());
            budget.addText(attribute.getKey());
            budget.addText(SessionExportSnapshot.frozenValueTextLength(value));
            frozen.put(attribute.getKey(), value);
        }
        return frozen;
    }

    private static long addExact(long left, long right, String label) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException(label + " overflows long", overflow);
        }
    }

    private static int addExact(int left, int right, String label) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException(label + " overflows int", overflow);
        }
    }

    private static final class CaptureBudget {
        private final SessionExportSnapshot.Limits limits;
        private long payloadEntries;
        private long textChars;

        private CaptureBudget(SessionExportSnapshot.Limits limits) {
            this.limits = limits;
        }

        private void addPayload(int count) {
            payloadEntries = addExact(payloadEntries, count, "payload entries");
            if (payloadEntries > limits.maxPayloadEntries()) {
                throw new IllegalStateException(
                        "Export payload entry limit exceeded: " + limits.maxPayloadEntries());
            }
        }

        private void addText(String... values) {
            for (String value : values) {
                if (value != null) {
                    addText(value.length());
                }
            }
        }

        private void addText(long count) {
            textChars = addExact(textChars, count, "text characters");
            if (textChars > limits.maxTextChars()) {
                throw new IllegalStateException(
                        "Export text character limit exceeded: " + limits.maxTextChars());
            }
        }
    }

    /**
     * 写锁内完成的 Session 标量与任务树 raw capture。
     *
     * <p>该 carrier 只持有 framework-owned container 和不可变值；锁外 assembly 因而无需重新访问
     * mutable model。nodes 保留捕获时的 post-order 构造输入，不形成第二次模型遍历。
     */
    private static final class RawCapture {
        private final long captureMillis;
        private final long captureNanos;
        private final String sessionId;
        private final String threadId;
        private final String threadName;
        private final SessionStatus status;
        private final long createdMillis;
        private final long createdNanos;
        private final Long completedMillis;
        private final Long completedNanos;
        private final Long durationMillis;
        private final Long durationNanos;
        private final SessionExportSnapshot.Limits limits;
        private final RawNode root;
        private final List<RawNode> nodes;

        private RawCapture(
                long captureMillis,
                long captureNanos,
                String sessionId,
                String threadId,
                String threadName,
                SessionStatus status,
                long createdMillis,
                long createdNanos,
                Long completedMillis,
                Long completedNanos,
                Long durationMillis,
                Long durationNanos,
                SessionExportSnapshot.Limits limits,
                RawNode root,
                List<RawNode> nodes) {
            this.captureMillis = captureMillis;
            this.captureNanos = captureNanos;
            this.sessionId = sessionId;
            this.threadId = threadId;
            this.threadName = threadName;
            this.status = status;
            this.createdMillis = createdMillis;
            this.createdNanos = createdNanos;
            this.completedMillis = completedMillis;
            this.completedNanos = completedNanos;
            this.durationMillis = durationMillis;
            this.durationNanos = durationNanos;
            this.limits = limits;
            this.root = root;
            this.nodes = nodes;
        }
    }

    /** 锁内逐字段填充、锁外只读的节点 raw value；不得保留 source model 引用。 */
    private static final class RawNode {
        private final int depth;
        private final int sequence;
        private final List<RawNode> children = new ArrayList<>();
        private String nodeId;
        private String taskName;
        private String taskPath;
        private String threadName;
        private TaskStatus status;
        private long createdMillis;
        private long createdNanos;
        private Long completedMillis;
        private Long completedNanos;
        private Long durationMillis;
        private Long durationNanos;
        private long selfDurationNanos;
        private List<SessionExportSnapshot.MessageSnapshot> messages;
        private Map<String, Object> attributes;
        private List<String> tags;
        private boolean childrenTruncated;

        private RawNode(int depth, int sequence) {
            this.depth = depth;
            this.sequence = sequence;
        }

        private void capture(TaskNode source, long captureNanos, CaptureBudget budget) {
            nodeId = source.getNodeId();
            taskName = source.getTaskName();
            taskPath = source.getTaskPath();
            threadName = source.getThreadName();
            status = source.getStatus();
            createdMillis = source.getCreatedMillis();
            createdNanos = source.getCreatedNanos();
            completedMillis = source.getCompletedMillis();
            completedNanos = source.getCompletedNanos();
            durationMillis = source.getDurationMillis();
            durationNanos = source.getDurationNanos();
            selfDurationNanos = status == TaskStatus.RUNNING
                    ? Math.max(0L, captureNanos - createdNanos)
                    : durationNanos;

            TaskNode.CapturePayloadSizes payloadSizes = source.capturePayloadSizes();
            budget.addPayload(payloadSizes.messages());
            budget.addPayload(payloadSizes.attributes());
            budget.addPayload(payloadSizes.tags());

            List<Message> sourceMessages = source.getMessages();
            Map<String, Object> sourceAttributes = source.getAttributes();
            List<String> sourceTags = source.getTags();
            budget.addText(nodeId, taskName, taskPath, threadName);
            messages = freezeMessages(sourceMessages, budget);
            attributes = freezeAttributes(sourceAttributes, budget);
            for (String tag : sourceTags) {
                budget.addText(tag);
            }
            tags = List.copyOf(sourceTags);
        }
    }

    /** 只存在于 write-lock callback 栈内的遍历 frame，source 不进入 RawCapture。 */
    private static final class TraversalFrame {
        private final TaskNode source;
        private final RawNode node;
        private int childCount;
        private int nextChild;
        private boolean initialized;

        private TraversalFrame(TaskNode source, RawNode node) {
            this.source = source;
            this.node = node;
        }
    }
}

/**
 * 为快照捕获提供可确定性替换的双时钟来源。
 */
interface CaptureClock {

    /** @return 当前 wall-clock 毫秒值 */
    long currentTimeMillis();

    /** @return 当前 monotonic 纳秒值 */
    long nanoTime();

    /** @return 生产环境系统双时钟 */
    static CaptureClock system() {
        return SystemCaptureClock.INSTANCE;
    }
}

/** 生产环境唯一系统双时钟实现，保持 package-private 避免测试能力进入公共 API。 */
final class SystemCaptureClock implements CaptureClock {
    static final SystemCaptureClock INSTANCE = new SystemCaptureClock();

    private SystemCaptureClock() {
    }

    /** @return 当前系统 wall-clock 毫秒值 */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /** @return 当前系统 monotonic 纳秒值 */
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
