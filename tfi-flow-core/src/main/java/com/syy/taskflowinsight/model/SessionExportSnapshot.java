package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageSeverity;
import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一次 Session 导出的深度不可变语义快照。
 *
 * <p>该类型是 4.0 formatter 的唯一模型输入：顶层构造器验证完整树、时间元组、统计值与有限预算，
 * nested records 只接受无需用户回调即可投影的冻结值。捕获遍历由同包内部组件负责，公共快照本身不持有
 * mutable {@link Session}、{@link TaskNode} 或 {@link Message} 引用。
 *
 * @param captureMillis 捕获线性化点的 wall-clock 毫秒值
 * @param captureNanos 捕获线性化点的 monotonic 纳秒值
 * @param sessionId Session 唯一标识
 * @param sessionName 与根任务名称一致的 Session 名称
 * @param threadId Session 创建线程标识
 * @param threadName Session 创建线程名称，允许 JVM 合法的空名称
 * @param status 捕获时 Session 状态
 * @param createdMillis Session wall-clock 创建时间
 * @param createdNanos Session monotonic 创建时间
 * @param completedMillis Session wall-clock 完成时间，运行态为 null
 * @param completedNanos Session monotonic 完成时间，运行态为 null
 * @param durationMillis Session wall-clock 时长，运行态为 null
 * @param durationNanos Session monotonic 时长，运行态为 null
 * @param limits 构造和后续投影共同遵守的有限预算
 * @param root 已冻结的可见根任务
 * @param statistics 可见任务树的精确统计
 * @param truncated 可见树是否因 depth/node 预算截断 children
 * @since 4.0.0
 */
public record SessionExportSnapshot(
        long captureMillis,
        long captureNanos,
        String sessionId,
        String sessionName,
        String threadId,
        String threadName,
        SessionStatus status,
        long createdMillis,
        long createdNanos,
        Long completedMillis,
        Long completedNanos,
        Long durationMillis,
        Long durationNanos,
        Limits limits,
        TaskSnapshot root,
        Statistics statistics,
        boolean truncated) {

    /**
     * 使用 framework 默认预算在线性化 gate 内捕获 Session。
     *
     * @param session 要冻结的 Session
     * @return 深度不可变快照
     */
    public static SessionExportSnapshot capture(Session session) {
        return capture(session, Limits.defaults());
    }

    /**
     * 使用调用方收紧后的有限预算在线性化 gate 内捕获 Session。
     *
     * @param session 要冻结的 Session
     * @param limits 不得超过 framework 硬上限的预算
     * @return 深度不可变快照
     */
    public static SessionExportSnapshot capture(Session session, Limits limits) {
        return SessionSnapshotCapturer.capture(session, limits, CaptureClock.system());
    }

    /**
     * 将当前不可变语义快照投影为唯一 canonical V2 Map tree。
     *
     * @return 深度不可修改且保留合法 null 的 canonical V2 projection
     */
    public Map<String, Object> toCanonicalV2() {
        return CanonicalExportV2Projection.create(this);
    }

    /**
     * 验证手工构造的快照与运行时捕获结果遵守同一组全图不变量。
     */
    public SessionExportSnapshot {
        requireText(sessionId, "sessionId");
        requireText(sessionName, "sessionName");
        requireText(threadId, "threadId");
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(statistics, "statistics");

        validateSessionTuple(
                status, createdMillis, createdNanos, completedMillis, completedNanos,
                durationMillis, durationNanos, captureNanos);
        validateGraph(
                captureNanos, sessionName, createdNanos, completedNanos,
                status, limits, root, statistics, truncated,
                sessionId, threadId, threadName);
    }

    private static void validateSessionTuple(
            SessionStatus status,
            long createdMillis,
            long createdNanos,
            Long completedMillis,
            Long completedNanos,
            Long durationMillis,
            Long durationNanos,
            long captureNanos) {
        boolean noTerminalTuple = completedMillis == null && completedNanos == null
                && durationMillis == null && durationNanos == null;
        boolean fullTerminalTuple = completedMillis != null && completedNanos != null
                && durationMillis != null && durationNanos != null;
        if (status == SessionStatus.RUNNING) {
            if (!noTerminalTuple) {
                throw new IllegalArgumentException("RUNNING session cannot have a terminal tuple");
            }
            return;
        }
        if (!fullTerminalTuple) {
            throw new IllegalArgumentException("A terminal session requires a complete terminal tuple");
        }
        if (completedNanos < createdNanos || completedNanos > captureNanos) {
            throw new IllegalArgumentException("Session completion must be within the capture interval");
        }
        if (durationNanos != subtractExact(completedNanos, createdNanos, "session duration")) {
            throw new IllegalArgumentException("Session monotonic duration is inconsistent");
        }
        if (durationMillis != subtractExact(completedMillis, createdMillis, "session wall-clock duration")) {
            throw new IllegalArgumentException("Session wall-clock duration is inconsistent");
        }
    }

    private static void validateGraph(
            long captureNanos,
            String sessionName,
            long sessionCreatedNanos,
            Long sessionCompletedNanos,
            SessionStatus sessionStatus,
            Limits limits,
            TaskSnapshot root,
            Statistics statistics,
            boolean truncated,
            String sessionId,
            String threadId,
            String threadName) {
        if (!sessionName.equals(root.taskName())
                || root.depth() != 0
                || root.sequence() != 0
                || !root.taskPath().equals(root.taskName())) {
            throw new IllegalArgumentException("Root task identity, depth, sequence, or path is inconsistent");
        }
        if (captureNanos < sessionCreatedNanos || root.createdNanos() < sessionCreatedNanos) {
            throw new IllegalArgumentException("Root or capture time precedes Session creation");
        }
        if (sessionStatus != SessionStatus.RUNNING) {
            if (root.status() == TaskStatus.RUNNING) {
                throw new IllegalArgumentException("A terminal session requires a terminal root");
            }
            if (root.completedNanos() > sessionCompletedNanos) {
                throw new IllegalArgumentException("Root completion is after session completion");
            }
        }

        Set<TaskSnapshot> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> nodeIds = new HashSet<>();
        ArrayDeque<GraphFrame> pending = new ArrayDeque<>();
        pending.push(new GraphFrame(root, null, 0));
        int taskCount = 0;
        int maxDepth = 0;
        int messageCount = 0;
        long payloadEntries = 0L;
        long textChars = textLength(sessionId, sessionName, threadId, threadName);
        boolean graphTruncated = false;

        while (!pending.isEmpty()) {
            GraphFrame frame = pending.pop();
            TaskSnapshot task = frame.task();
            if (!identities.add(task)) {
                throw new IllegalArgumentException("Task snapshot graph cannot contain a DAG");
            }
            if (!nodeIds.add(task.nodeId())) {
                throw new IllegalArgumentException("Duplicate node ID: " + task.nodeId());
            }

            taskCount = addExact(taskCount, 1, "task count");
            if (taskCount > limits.maxNodes()) {
                throw new IllegalArgumentException(
                        "Export node limit exceeded: " + limits.maxNodes());
            }
            if (task.depth() > limits.maxDepth()) {
                throw new IllegalArgumentException(
                        "Export depth limit exceeded: " + limits.maxDepth());
            }
            maxDepth = Math.max(maxDepth, task.depth());
            messageCount = addExact(messageCount, task.messages().size(), "message count");
            payloadEntries = addExact(payloadEntries, task.messages().size(), "payload entries");
            payloadEntries = addExact(payloadEntries, task.attributes().size(), "payload entries");
            payloadEntries = addExact(payloadEntries, task.tags().size(), "payload entries");
            if (payloadEntries > limits.maxPayloadEntries()) {
                throw new IllegalArgumentException(
                        "Export payload entry limit exceeded: " + limits.maxPayloadEntries());
            }

            validateTaskBounds(task, frame, captureNanos);
            textChars = addText(textChars, textLength(
                    task.nodeId(), task.taskName(), task.taskPath(), task.threadName()), limits);
            for (MessageSnapshot message : task.messages()) {
                if (message.timestampNanos() < task.createdNanos()
                        || message.timestampNanos() > captureNanos) {
                    throw new IllegalArgumentException("Message timestamp is outside its task capture interval");
                }
                textChars = addText(textChars, textLength(
                        message.wireType(), message.displayLabel(), message.customLabel(),
                        message.content(), message.threadName()), limits);
            }
            for (String tag : task.tags()) {
                textChars = addText(textChars, tag.length(), limits);
            }
            for (Map.Entry<String, Object> attribute : task.attributes().entrySet()) {
                textChars = addText(textChars, attribute.getKey().length(), limits);
                textChars = addText(textChars, frozenValueTextLength(attribute.getValue()), limits);
            }

            long accumulated = task.selfDurationNanos();
            List<TaskSnapshot> children = task.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                TaskSnapshot child = children.get(index);
                accumulated = addExact(
                        accumulated, child.accumulatedDurationNanos(), "accumulated duration");
                pending.push(new GraphFrame(child, task, index));
            }
            if (accumulated != task.accumulatedDurationNanos()) {
                throw new IllegalArgumentException("Task accumulated duration is inconsistent");
            }
            graphTruncated |= task.childrenTruncated();
        }

        if (textChars > limits.maxTextChars()) {
            throw new IllegalArgumentException(
                    "Export text character limit exceeded: " + limits.maxTextChars());
        }
        Statistics actual = new Statistics(taskCount, maxDepth, messageCount);
        if (!actual.equals(statistics)) {
            throw new IllegalArgumentException("Task tree statistics are inconsistent");
        }
        if (graphTruncated != truncated) {
            throw new IllegalArgumentException("Top-level truncated flag is inconsistent");
        }
    }

    private static void validateTaskBounds(
            TaskSnapshot task, GraphFrame frame, long captureNanos) {
        if (task.createdNanos() > captureNanos) {
            throw new IllegalArgumentException("Task was created after capture");
        }
        if (task.status() == TaskStatus.RUNNING) {
            long expectedSelf = Math.max(0L,
                    subtractExact(captureNanos, task.createdNanos(), "running task duration"));
            if (task.selfDurationNanos() != expectedSelf) {
                throw new IllegalArgumentException("Running task duration is inconsistent with capture");
            }
        } else if (task.completedNanos() > captureNanos) {
            throw new IllegalArgumentException("Task completion is after capture");
        }

        TaskSnapshot parent = frame.parent();
        if (parent == null) {
            return;
        }
        if (task.depth() != parent.depth() + 1) {
            throw new IllegalArgumentException("Child task depth is inconsistent");
        }
        if (task.sequence() != frame.sequence()) {
            throw new IllegalArgumentException("Child task sequence is inconsistent");
        }
        String expectedPath = parent.taskPath() + "/" + task.taskName();
        if (!expectedPath.equals(task.taskPath())) {
            throw new IllegalArgumentException("Child task path is inconsistent");
        }
        if (task.createdNanos() < parent.createdNanos()) {
            throw new IllegalArgumentException("Child task was created before parent");
        }
    }

    private static long textLength(String... values) {
        long total = 0L;
        for (String value : values) {
            if (value != null) {
                total = addExact(total, value.length(), "text characters");
            }
        }
        return total;
    }

    private static long addText(long current, long increment, Limits limits) {
        long next = addExact(current, increment, "text characters");
        if (next > limits.maxTextChars()) {
            throw new IllegalArgumentException(
                    "Export text character limit exceeded: " + limits.maxTextChars());
        }
        return next;
    }

    static long frozenValueTextLength(Object value) {
        if (value == null) {
            return 4L;
        }
        Class<?> type = value.getClass();
        if (type == String.class) {
            return ((String) value).length();
        }
        if (type == Boolean.class) {
            return 5L;
        }
        if (type == Character.class) {
            return 1L;
        }
        if (type == Byte.class) {
            return 4L;
        }
        if (type == Short.class) {
            return 6L;
        }
        if (type == Integer.class) {
            return 11L;
        }
        if (type == Long.class) {
            return 20L;
        }
        if (type == Float.class) {
            return 15L;
        }
        if (type == Double.class) {
            return 24L;
        }
        if (type == BigInteger.class) {
            return bigIntegerTextBound((BigInteger) value);
        }
        if (type == BigDecimal.class) {
            return bigDecimalTextBound((BigDecimal) value);
        }
        if (type == NonFiniteNumber.class) {
            NonFiniteNumber marker = (NonFiniteNumber) value;
            return marker.numberKind().name().length() + marker.value().length();
        }
        if (type == UnsupportedValue.class) {
            return ((UnsupportedValue) value).className().length();
        }
        throw new IllegalArgumentException("Attribute value is not frozen: " + type.getName());
    }

    static long estimateTextCharacters(SessionExportSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        long textChars = textLength(
                snapshot.sessionId(), snapshot.sessionName(),
                snapshot.threadId(), snapshot.threadName());
        ArrayDeque<TaskSnapshot> pending = new ArrayDeque<>();
        pending.push(snapshot.root());
        while (!pending.isEmpty()) {
            TaskSnapshot task = pending.pop();
            textChars = addExact(textChars, textLength(
                    task.nodeId(), task.taskName(), task.taskPath(), task.threadName()),
                    "text characters");
            for (MessageSnapshot message : task.messages()) {
                textChars = addExact(textChars, textLength(
                        message.wireType(), message.displayLabel(), message.customLabel(),
                        message.content(), message.threadName()), "text characters");
            }
            for (String tag : task.tags()) {
                textChars = addExact(textChars, tag.length(), "text characters");
            }
            for (Map.Entry<String, Object> attribute : task.attributes().entrySet()) {
                textChars = addExact(textChars, attribute.getKey().length(), "text characters");
                textChars = addExact(
                        textChars, frozenValueTextLength(attribute.getValue()), "text characters");
            }
            List<TaskSnapshot> children = task.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                pending.push(children.get(index));
            }
        }
        return textChars;
    }

    private static long bigIntegerTextBound(BigInteger value) {
        int bitLength = value.bitLength();
        long digits = bitLength == 0
                ? 1L
                : addExact((long) bitLength * 30_103L, 99_999L, "BigInteger text") / 100_000L;
        return value.signum() < 0 ? addExact(digits, 1L, "BigInteger sign") : digits;
    }

    private static long bigDecimalTextBound(BigDecimal value) {
        long bound = addExact(value.precision(), Math.abs((long) value.scale()), "BigDecimal text");
        bound = addExact(bound, 10L, "BigDecimal notation");
        return value.signum() < 0 ? addExact(bound, 1L, "BigDecimal sign") : bound;
    }

    static boolean isFrozenAttributeValue(Object value) {
        if (value == null) {
            return true;
        }
        Class<?> type = value.getClass();
        if (type == Float.class) {
            return Float.isFinite((Float) value);
        }
        if (type == Double.class) {
            return Double.isFinite((Double) value);
        }
        return type == String.class || type == Boolean.class || type == Character.class
                || type == Byte.class || type == Short.class || type == Integer.class
                || type == Long.class || type == BigInteger.class || type == BigDecimal.class
                || type == NonFiniteNumber.class || type == UnsupportedValue.class;
    }

    private static long subtractExact(long left, long right, String label) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " overflows long", overflow);
        }
    }

    private static long addExact(long left, long right, String label) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " overflows long", overflow);
        }
    }

    private static int addExact(int left, int right, String label) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " overflows int", overflow);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    /**
     * 限制一次快照可见图与 payload/text 工作量的四维预算。
     *
     * @param maxDepth root 为 0 的最大可见深度
     * @param maxNodes 最大可见任务数
     * @param maxPayloadEntries message、attribute、tag 的最大合计条目数
     * @param maxTextChars callback-free UTF-16 文本估算上限
     */
    public record Limits(int maxDepth, int maxNodes, int maxPayloadEntries, long maxTextChars) {

        /**
         * 拒绝超过 framework 公开硬上限的自定义预算。
         */
        public Limits {
            if (maxDepth < 0 || maxDepth > FlowConfigDefaults.MAX_EXPORT_DEPTH
                    || maxNodes <= 0 || maxNodes > FlowConfigDefaults.MAX_EXPORT_NODES
                    || maxPayloadEntries <= 0
                    || maxPayloadEntries > FlowConfigDefaults.MAX_EXPORT_PAYLOAD_ENTRIES
                    || maxTextChars <= 0 || maxTextChars > FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS) {
                throw new IllegalArgumentException("Export limits are out of range");
            }
        }

        /**
         * 返回 framework 统一的导出预算。
         *
         * @return 默认四维预算
         */
        public static Limits defaults() {
            return new Limits(
                    FlowConfigDefaults.MAX_EXPORT_DEPTH,
                    FlowConfigDefaults.MAX_EXPORT_NODES,
                    FlowConfigDefaults.MAX_EXPORT_PAYLOAD_ENTRIES,
                    FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        }
    }

    /**
     * 描述当前可见快照树的精确统计。
     *
     * @param totalTasks 可见任务总数
     * @param maxDepth 可见最大深度
     * @param totalMessages 可见消息总数
     */
    public record Statistics(int totalTasks, int maxDepth, int totalMessages) {

        /**
         * 拒绝无法表示真实树统计的负值。
         */
        public Statistics {
            if (totalTasks < 0 || maxDepth < 0 || totalMessages < 0) {
                throw new IllegalArgumentException("Statistics cannot be negative");
            }
        }
    }

    /**
     * 一个已冻结任务节点及其可见 children。
     *
     * @param nodeId 节点唯一标识
     * @param taskName 任务名称
     * @param taskPath 从 root 推导的完整路径
     * @param depth root 为 0 的深度
     * @param sequence 在父节点 children 中的零基序号
     * @param threadName 创建任务的线程名称，允许 JVM 合法的空名称
     * @param status 捕获时任务状态
     * @param createdMillis wall-clock 创建时间
     * @param createdNanos monotonic 创建时间
     * @param completedMillis wall-clock 完成时间，运行态为 null
     * @param completedNanos monotonic 完成时间，运行态为 null
     * @param durationMillis wall-clock 时长，运行态为 null
     * @param durationNanos monotonic 时长，运行态为 null
     * @param selfDurationMillis 自身 monotonic 时长的毫秒投影
     * @param selfDurationNanos 自身 monotonic 时长
     * @param accumulatedDurationMillis 可见子树累计时长的毫秒投影
     * @param accumulatedDurationNanos 可见子树累计时长
     * @param messages 冻结消息
     * @param attributes callback-free 冻结属性
     * @param tags 冻结标签
     * @param children 可见子任务
     * @param childrenTruncated 是否省略了真实 child
     */
    public record TaskSnapshot(
            String nodeId,
            String taskName,
            String taskPath,
            int depth,
            int sequence,
            String threadName,
            TaskStatus status,
            long createdMillis,
            long createdNanos,
            Long completedMillis,
            Long completedNanos,
            Long durationMillis,
            Long durationNanos,
            long selfDurationMillis,
            long selfDurationNanos,
            long accumulatedDurationMillis,
            long accumulatedDurationNanos,
            List<MessageSnapshot> messages,
            Map<String, Object> attributes,
            List<String> tags,
            List<TaskSnapshot> children,
            boolean childrenTruncated) {

        /**
         * 冻结集合并验证无需父节点或 capture clock 即可判断的本地不变量。
         */
        public TaskSnapshot {
            requireText(nodeId, "nodeId");
            requireText(taskName, "taskName");
            requireText(taskPath, "taskPath");
            Objects.requireNonNull(threadName, "threadName");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(attributes, "attributes");
            Objects.requireNonNull(tags, "tags");
            Objects.requireNonNull(children, "children");
            if (depth < 0 || sequence < 0 || selfDurationNanos < 0
                    || accumulatedDurationNanos < selfDurationNanos) {
                throw new IllegalArgumentException("Task depth, sequence, or accumulated duration is invalid");
            }
            if (selfDurationMillis != selfDurationNanos / 1_000_000L
                    || accumulatedDurationMillis != accumulatedDurationNanos / 1_000_000L) {
                throw new IllegalArgumentException("Task duration millisecond projection is inconsistent");
            }
            validateTaskTuple(
                    status, createdMillis, createdNanos, completedMillis, completedNanos,
                    durationMillis, durationNanos, selfDurationNanos);

            messages = List.copyOf(messages);
            tags = List.copyOf(tags);
            children = List.copyOf(children);
            LinkedHashMap<String, Object> frozenAttributes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
                String key = Objects.requireNonNull(attribute.getKey(), "attribute key");
                if (!isFrozenAttributeValue(attribute.getValue())) {
                    throw new IllegalArgumentException(
                            "Attribute value is not a frozen attribute: "
                                    + attribute.getValue().getClass().getName());
                }
                frozenAttributes.put(key, attribute.getValue());
            }
            attributes = Collections.unmodifiableMap(frozenAttributes);
        }

        private static void validateTaskTuple(
                TaskStatus status,
                long createdMillis,
                long createdNanos,
                Long completedMillis,
                Long completedNanos,
                Long durationMillis,
                Long durationNanos,
                long selfDurationNanos) {
            boolean noTerminalTuple = completedMillis == null && completedNanos == null
                    && durationMillis == null && durationNanos == null;
            boolean fullTerminalTuple = completedMillis != null && completedNanos != null
                    && durationMillis != null && durationNanos != null;
            if (status == TaskStatus.RUNNING) {
                if (!noTerminalTuple) {
                    throw new IllegalArgumentException("RUNNING task cannot have a terminal tuple");
                }
                return;
            }
            if (!fullTerminalTuple) {
                throw new IllegalArgumentException("A terminal task requires a complete terminal tuple");
            }
            if (completedNanos < createdNanos) {
                throw new IllegalArgumentException("Task completion precedes creation");
            }
            long exactNanos = subtractExact(completedNanos, createdNanos, "task duration");
            long exactMillis = subtractExact(completedMillis, createdMillis, "task wall-clock duration");
            if (durationNanos != exactNanos || selfDurationNanos != exactNanos) {
                throw new IllegalArgumentException("Task monotonic duration is inconsistent");
            }
            if (durationMillis != exactMillis) {
                throw new IllegalArgumentException("Task wall-clock duration is inconsistent");
            }
        }
    }

    /**
     * 捕获时已经冻结业务类型、显示标签与 L6 severity 的消息。
     *
     * @param wireType 既有业务类型文本，可为 null
     * @param displayLabel 人类可读显示标签
     * @param severity 冻结的技术严重程度
     * @param customLabel 自定义标签，可为 null
     * @param content 消息内容
     * @param timestampMillis wall-clock 记录时间
     * @param timestampNanos monotonic 记录时间
     * @param threadName 记录消息的线程名称，允许 JVM 合法的空名称
     */
    public record MessageSnapshot(
            String wireType,
            String displayLabel,
            MessageSeverity severity,
            String customLabel,
            String content,
            long timestampMillis,
            long timestampNanos,
            String threadName) {

        /**
         * 拒绝无法形成稳定诊断与 canonical V2 字段的局部消息。
         */
        public MessageSnapshot {
            requireText(displayLabel, "displayLabel");
            Objects.requireNonNull(severity, "severity");
            requireText(content, "content");
            Objects.requireNonNull(threadName, "threadName");
        }
    }

    /** 非有限数的原始 Java 数值宽度。 */
    public enum NumberKind {
        /** 原始值来自 32-bit {@link Float}。 */
        FLOAT,
        /** 原始值来自 64-bit {@link Double}。 */
        DOUBLE
    }

    /**
     * 非有限 Float/Double 的可逆 tagged marker。
     *
     * @param numberKind 原始数值宽度
     * @param value `NaN`、`Infinity` 或 `-Infinity`
     */
    public record NonFiniteNumber(NumberKind numberKind, String value) {

        /** 验证 marker 只表达 canonical V2 支持的三个 token。 */
        public NonFiniteNumber {
            Objects.requireNonNull(numberKind, "numberKind");
            if (!"NaN".equals(value) && !"Infinity".equals(value) && !"-Infinity".equals(value)) {
                throw new IllegalArgumentException("Unsupported non-finite value: " + value);
            }
        }

        static NonFiniteNumber from(Float number) {
            return new NonFiniteNumber(NumberKind.FLOAT, token(number.doubleValue()));
        }

        static NonFiniteNumber from(Double number) {
            return new NonFiniteNumber(NumberKind.DOUBLE, token(number));
        }

        private static String token(double number) {
            if (Double.isNaN(number)) {
                return "NaN";
            }
            return number > 0 ? "Infinity" : "-Infinity";
        }
    }

    /**
     * 不执行用户回调即可表达 unsupported attribute 的 tagged marker。
     *
     * @param className 原始值的精确运行时类名
     */
    public record UnsupportedValue(String className) {

        /** 拒绝无法定位原始类型的空类名。 */
        public UnsupportedValue {
            requireText(className, "className");
        }
    }

    private record GraphFrame(TaskSnapshot task, TaskSnapshot parent, int sequence) {
    }
}
