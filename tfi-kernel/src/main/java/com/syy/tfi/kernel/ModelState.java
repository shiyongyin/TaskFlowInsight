package com.syy.tfi.kernel;

import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.model.Record;
import com.syy.tfi.kernel.model.RecordType;
import com.syy.tfi.kernel.model.StageNode;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内核私有的可变构建状态；只在 owner 线程内变化。 */
final class NodeState implements StageNode {
    /** 非空白且不超过 256 个 UTF-16 code unit 的 Stage 名称；根节点与 Session 同名。 */
    private final String name;
    /** Stage 创建时的墙钟时间，单位为 epoch 毫秒，仅用于跨系统对齐。 */
    private final long startMs;
    /** Stage 创建时的单调时钟快照，单位为纳秒，仅用于计算持续时间。 */
    private final long startNanos;
    /** owner 线程可修改的有序属性存储，覆盖值时保留 key 的首次插入位置。 */
    private final Map<String, Object> mutableAttrs = new LinkedHashMap<>();
    /** 每个属性当前占用的完整 JSON entry 字节数，用于替换时原子调整 Session 预算。 */
    private final Map<String, Integer> attrEntryBytes = new LinkedHashMap<>();
    /** 按成功接纳顺序保存的可变事实列表，仅允许 owner 线程追加。 */
    private final List<Record> mutableRecords = new ArrayList<>();
    /** 按创建顺序保存的可变子 Stage 列表，仅允许 owner 线程追加。 */
    private final List<StageNode> mutableChildren = new ArrayList<>();
    /** 与可变属性存储共享内容的不可修改实时视图，供只读模型与 writer 使用。 */
    private final Map<String, Object> attrs = Collections.unmodifiableMap(mutableAttrs);
    /** 与可变事实列表共享内容的不可修改实时视图。 */
    private final List<Record> records = Collections.unmodifiableList(mutableRecords);
    /** 与可变子 Stage 列表共享内容的不可修改实时视图。 */
    private final List<StageNode> children = Collections.unmodifiableList(mutableChildren);
    /** 当前 Stage 生命周期状态，初值 RUNNING；成功接纳 ERROR 事实后不可恢复为 OK。 */
    private FlowStatus status = FlowStatus.RUNNING;
    /**
     * 基于单调时钟差向下取整并钳制为非负数的持续毫秒；活动态及时钟失败回退时为 0。
     */
    private long durationMs;
    /** 是否已经完成或放弃；初值 false，置位后不再恢复，用于保证关闭幂等。 */
    private boolean closed;

    NodeState(String name, KernelClock clock) {
        this.name = name;
        this.startMs = clock.wallTimeMillis();
        this.startNanos = clock.monotonicNanos();
    }

    void addChild(NodeState child) {
        mutableChildren.add(child);
    }

    void addRecord(Record record) {
        mutableRecords.add(record);
        if (record.type() == RecordType.ERROR) {
            markError();
        }
    }

    void putAttr(String key, Object value, int encodedEntryBytes) {
        mutableAttrs.put(key, value);
        attrEntryBytes.put(key, encodedEntryBytes);
    }

    int attrEntryBytes(String key) {
        return attrEntryBytes.getOrDefault(key, 0);
    }

    int recordCount() {
        return mutableRecords.size();
    }

    int childCount() {
        return mutableChildren.size();
    }

    void markError() {
        if (status != FlowStatus.ABANDONED) {
            status = FlowStatus.ERROR;
        }
    }

    void finish(KernelClock clock) {
        if (closed) {
            return;
        }
        long elapsedNanos = clock.monotonicNanos() - startNanos;
        durationMs = Math.max(0L, elapsedNanos / 1_000_000L);
        if (status == FlowStatus.RUNNING) {
            status = FlowStatus.OK;
        }
        closed = true;
    }

    void finishWithoutClock() {
        if (closed) {
            return;
        }
        durationMs = 0L;
        if (status == FlowStatus.RUNNING) {
            status = FlowStatus.OK;
        }
        closed = true;
    }

    void abandon() {
        if (!closed) {
            status = FlowStatus.ABANDONED;
            closed = true;
        }
        for (StageNode child : mutableChildren) {
            ((NodeState) child).abandon();
        }
    }

    boolean closed() {
        return closed;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public FlowStatus status() {
        return status;
    }

    @Override
    public long startMs() {
        return startMs;
    }

    @Override
    public long durMs() {
        return durationMs;
    }

    @Override
    public Map<String, Object> attrs() {
        return attrs;
    }

    @Override
    public List<Record> records() {
        return records;
    }

    @Override
    public List<StageNode> children() {
        return children;
    }
}

/**
 * 接纳时已复制 data 的不可变事实。
 *
 * @param type 事实的稳定顶层分类；ERROR 会把所属 Stage 标记为错误
 * @param code 非空白且不超过 512 个 UTF-16 code unit 的稳定机器码
 * @param text 可选人读文本，可为 null，最长 65536 个 UTF-16 code unit，机器消费不得解析它
 * @param data 接纳时深复制且不可修改的有序 JSON-like Map，不为 null
 * @param atMs 事实成功接纳时的墙钟时间，单位为 epoch 毫秒
 */
record RecordValue(RecordType type, String code, String text, Map<String, Object> data, long atMs)
        implements Record {
}
