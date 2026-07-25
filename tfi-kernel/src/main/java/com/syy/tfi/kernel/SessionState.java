package com.syy.tfi.kernel;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.model.RecordType;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一个线程封闭 Session 的全部可变状态和配置快照。 */
final class SessionState implements FlowSession {
    /** 同时打开的 Stage 栈深度硬上限，包含根 Stage，且独立于 Stage 总数配额。 */
    private static final int MAX_STACK_DEPTH = 64;

    /** 创建并管理本 Session 的 Runtime；内部失败诊断不得落入静态默认实例。 */
    private final KernelRuntime owner;
    /** 非空白且不超过 128 个 UTF-16 code unit 的 Session 唯一标识。 */
    private final String sessionId;
    /** 链接父 Session 的标识；根 Session 为 null，且不会共享父 Session 的可变树。 */
    private final String parentSessionId;
    /** 非空白且不超过 256 个 UTF-16 code unit 的根业务流名称。 */
    private final String name;
    /** Session 创建时捕获的不可变配置快照，后续全局配置替换不会影响它。 */
    private final KernelConfig config;
    /** 创建 Session 的线程 ID；只有该 owner 线程可以读取预算或修改 Stage 树。 */
    private final long ownerThreadId;
    /** 与 Session 同名的根节点，也是整个只读 Stage 树的固定入口。 */
    private final NodeState root;
    /** 尚未关闭的 Stage LIFO 栈；栈顶是当前 Stage，冻结后为空。 */
    private final Deque<NodeState> stack = new ArrayDeque<>();
    /** 根句柄写入的有序 Session 属性 backing map，覆盖值时保留首次 key 位置。 */
    private final Map<String, Object> mutableAttrs = new LinkedHashMap<>();
    /** Session 属性当前完整 JSON entry 的字节成本，用于覆盖时原子调整预算。 */
    private final Map<String, Integer> attrEntryBytes = new LinkedHashMap<>();
    /** 与 Session 属性 backing map 共享内容的不可修改实时视图。 */
    private final Map<String, Object> attrs = Collections.unmodifiableMap(mutableAttrs);
    /** 去重且按枚举声明顺序输出的不完整原因集合，初始为空。 */
    private final EnumSet<IncompleteReason> reasons = EnumSet.noneOf(IncompleteReason.class);
    /** 以最终 canonical JSON UTF-8 bytes 为单位的 Session 原子预算账本。 */
    private final BudgetLedger budget;
    /** Session 生命周期状态；正常完成时取根 Stage 的 OK 或 ERROR 终态。 */
    private FlowStatus status = FlowStatus.RUNNING;
    /** 已成功接纳的 Stage 总数，初值 1 已包含根 Stage。 */
    private int stageCount = 1;
    /**
     * Session 全树当前属性槽位总数；仅同一节点覆盖同一 key 不增加，不同节点同名 key 分别计数。
     */
    private int attrCount;
    /** 是否仍允许追加事实；中途禁用或放弃后永久为 false，正常冻结另由 frozen 阻断。 */
    private boolean recording = true;
    /** 是否已经形成不可再修改的终态；正常关闭或放弃后永久为 true。 */
    private boolean frozen;

    SessionState(
            KernelRuntime owner, String sessionId, String parentSessionId, String name, KernelConfig config) {
        this.owner = owner;
        this.sessionId = sessionId;
        this.parentSessionId = parentSessionId;
        this.name = name;
        this.config = config;
        this.ownerThreadId = Thread.currentThread().threadId();
        this.root = new NodeState(name, config.clock());
        this.budget = new BudgetLedger(
                sessionId, parentSessionId, name, root.startMs(), config.maxSessionEncodedBytes());
        stack.push(root);
    }

    NodeState openStage(String stageName) {
        if (!recording || frozen || budget.exhausted()) {
            return null;
        }
        if (stageCount >= config.maxStages()) {
            addReason(IncompleteReason.STAGE_LIMIT);
            return null;
        }
        if (stack.size() >= MAX_STACK_DEPTH) {
            addReason(IncompleteReason.STACK_DEPTH_LIMIT);
            return null;
        }
        NodeState child = new NodeState(stageName, config.clock());
        int encodedBytes = BudgetLedger.stageBytes(
                stageName, child.startMs(), stack.peek().childCount() > 0);
        if (!budget.accept(encodedBytes)) {
            addReason(IncompleteReason.SESSION_BYTES_LIMIT);
            return null;
        }
        stack.peek().addChild(child);
        stack.push(child);
        stageCount++;
        return child;
    }

    boolean closeStage(NodeState target) {
        if (frozen || target.closed() || !stack.contains(target)) {
            return false;
        }
        if (stack.peek() != target) {
            addReason(IncompleteReason.NON_LIFO_CLOSE);
        }
        Error fatalFailure = null;
        while (!stack.isEmpty()) {
            NodeState closed = stack.pop();
            try {
                closed.finish(config.clock());
            } catch (RuntimeException | Error failure) {
                closed.finishWithoutClock();
                if (KernelRuntime.isFatal(failure)) {
                    Error currentFailure = (Error) failure;
                    if (fatalFailure == null) {
                        fatalFailure = currentFailure;
                    } else if (fatalFailure != currentFailure) {
                        fatalFailure.addSuppressed(currentFailure);
                    }
                } else {
                    addReason(IncompleteReason.RECORDING_FAILURE);
                    owner.diagnose(DiagnosticCode.RECORDING_FAILURE, this, failure);
                }
            }
            if (closed.status() == FlowStatus.ERROR) {
                for (NodeState ancestor : stack) {
                    ancestor.markError();
                }
            }
            if (closed == target) {
                break;
            }
        }
        boolean completed = stack.isEmpty();
        if (completed) {
            status = root.status();
            frozen = true;
        }
        if (fatalFailure != null) {
            throw fatalFailure;
        }
        return completed;
    }

    boolean putAttr(NodeState node, String key, Object value) {
        if (!recording || frozen || budget.exhausted()) {
            return false;
        }
        Map<String, Object> target = node == root ? mutableAttrs : node.attrs();
        boolean existing = target.containsKey(key);
        if (!existing && attrCount >= config.maxAttrs()) {
            addReason(IncompleteReason.ATTR_LIMIT);
            return false;
        }
        FrozenValue frozenValue = DataCodec.freezeScalar(value);
        if (frozenValue.encodedBytes() > config.maxRecordEncodedBytes()) {
            throw DataFailure.recordLimit();
        }
        boolean needsComma = existing
                ? !target.keySet().iterator().next().equals(key)
                : !target.isEmpty();
        int candidateBytes = BudgetLedger.attrBytes(key, frozenValue.encodedBytes(), needsComma);
        int previousBytes = node == root
                ? attrEntryBytes.getOrDefault(key, 0)
                : node.attrEntryBytes(key);
        boolean accepted = existing
                ? budget.replace(previousBytes, candidateBytes)
                : budget.accept(candidateBytes);
        if (!accepted) {
            addReason(IncompleteReason.SESSION_BYTES_LIMIT);
            return false;
        }
        if (node == root) {
            mutableAttrs.put(key, frozenValue.value());
            attrEntryBytes.put(key, candidateBytes);
        } else {
            node.putAttr(key, frozenValue.value(), candidateBytes);
        }
        if (!existing) {
            attrCount++;
        }
        return true;
    }

    boolean addRecord(NodeState node, RecordType type, String code, String text, Map<String, ?> data) {
        if (!recording || frozen || budget.exhausted()) {
            return false;
        }
        FrozenValue frozenData = DataCodec.freezeStructuredData(data, config.maxRecordEncodedBytes());
        return addFrozenRecord(node, type, code, text, frozenData);
    }

    boolean addChange(NodeState node, String path, Object before, Object after) {
        if (!recording || frozen || budget.exhausted()) {
            return false;
        }
        Map<String, FrozenValue> values = new LinkedHashMap<>();
        values.put("path", new FrozenValue(path, DataCodec.stringBytes(path)));
        values.put("before", DataCodec.freezeScalar(before));
        values.put("after", DataCodec.freezeScalar(after));
        FrozenValue data = DataCodec.orderedMap(values);
        if (data.encodedBytes() > config.maxRecordEncodedBytes()) {
            throw DataFailure.recordLimit();
        }
        return addFrozenRecord(node, RecordType.CHANGE, "MANUAL_CHANGE", null, data);
    }

    boolean addThrowableRecord(NodeState node, String code, String text, Throwable error) {
        if (!recording || frozen || budget.exhausted()) {
            return false;
        }
        Map<String, FrozenValue> values = new LinkedHashMap<>();
        String type = error.getClass().getName();
        String message = error.getMessage();
        values.put("errorType", new FrozenValue(type, DataCodec.stringBytes(type)));
        values.put("errorMessage", DataCodec.freezeScalar(message));
        FrozenValue data = DataCodec.orderedMap(values);
        if (data.encodedBytes() > config.maxRecordEncodedBytes()) {
            throw DataFailure.recordLimit();
        }
        return addFrozenRecord(node, RecordType.ERROR, code, text, data);
    }

    private boolean addFrozenRecord(
            NodeState node, RecordType type, String code, String text, FrozenValue data) {
        if (!recording || frozen || budget.exhausted()) {
            return false;
        }
        if (text != null) {
            DataCodec.stringBytes(text);
        }
        long atMs = config.clock().wallTimeMillis();
        int encodedBytes = BudgetLedger.recordBytes(
                type, code, text, data.encodedBytes(), atMs, node.recordCount() > 0);
        if (encodedBytes - (node.recordCount() > 0 ? 1 : 0) > config.maxRecordEncodedBytes()) {
            addReason(IncompleteReason.RECORD_BYTES_LIMIT);
            return false;
        }
        if (!budget.accept(encodedBytes)) {
            addReason(IncompleteReason.SESSION_BYTES_LIMIT);
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenData = (Map<String, Object>) data.value();
        node.addRecord(new RecordValue(type, code, text, frozenData, atMs));
        return true;
    }

    void disableRecording() {
        if (recording && !frozen) {
            recording = false;
            addReason(IncompleteReason.DISABLED_MID_SESSION);
        }
    }

    void abandon() {
        stack.clear();
        root.abandon();
        status = FlowStatus.ABANDONED;
        recording = false;
        frozen = true;
    }

    void addReason(IncompleteReason reason) {
        reasons.add(reason);
    }

    boolean active() {
        return !frozen && !stack.isEmpty();
    }

    boolean recording() {
        return recording;
    }

    long ownerThreadId() {
        return ownerThreadId;
    }

    NodeState currentStage() {
        return stack.peek();
    }

    NodeState rootState() {
        return root;
    }

    KernelConfig config() {
        return config;
    }

    int remainingEncodedBytes() {
        return budget.remaining();
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public String parentSessionId() {
        return parentSessionId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> attrs() {
        return attrs;
    }

    @Override
    public FlowStatus status() {
        return status;
    }

    @Override
    public long startMs() {
        return root.startMs();
    }

    @Override
    public long durMs() {
        return root.durMs();
    }

    @Override
    public boolean truncated() {
        return !reasons.isEmpty();
    }

    @Override
    public List<String> incompleteReasons() {
        return reasons.stream().map(Enum::name).toList();
    }

    @Override
    public NodeState root() {
        return root;
    }
}

/** 输出不完整原因是稳定机器事实，消费方不得从人读 WARN 反推截断原因。 */
enum IncompleteReason {
    /** Stage 总数已达到配置上限；计数包含根 Stage，当前候选未创建。 */
    STAGE_LIMIT,
    /** 同时打开的 Stage 栈已达到 64 层硬上限，当前嵌套候选未创建。 */
    STACK_DEPTH_LIMIT,
    /** 当前候选会超过 Session canonical JSON 的 UTF-8 字节预算，且后续追加均被拒绝。 */
    SESSION_BYTES_LIMIT,
    /** 单条事实、结构化 data 或属性冻结值超过单 Record 字节上限。 */
    RECORD_BYTES_LIMIT,
    /** Session 全树属性槽位数已达配置上限；仅覆盖同一节点已有 key 仍可尝试。 */
    ATTR_LIMIT,
    /** 候选输入含非法 Unicode、非有限数值，或结构化 data 含循环、非 String key、过深嵌套。 */
    STRUCTURED_DATA_INVALID,
    /** 单个字符串或数值文本超过 65,536 个 UTF-16 code unit 的预编码上限。 */
    INPUT_TOO_LARGE,
    /** Session 运行期间观察到任一启用开关关闭，后续记录永久停止。 */
    DISABLED_MID_SESSION,
    /** Stage 未按 LIFO 顺序关闭，内核已从栈顶自动收束至目标 Stage。 */
    NON_LIFO_CLOSE,
    /** 固化或关闭流程发生非致命内部失败，业务继续但输出事实可能缺失。 */
    RECORDING_FAILURE
}
