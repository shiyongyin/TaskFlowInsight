package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * 一次compare调用独占的可变执行状态。
 *
 * <p>状态不进入runtime字段，也不使用ThreadLocal。active-path按对象身份只描述当前祖先链，离开分支即删除，
 * 因而共享DAG会按值再次遍历而不会被误判为cycle或相等。</p>
 */
final class CompareRequestState {

    /** 常见对象图祖先深度的初始容量，超出时仍由 IdentityHashMap 正常扩容。 */
    private static final int PATH_CAPACITY = 4;

    /** 常见浅层容器的初始待处理 frame 数，单位为 frame。 */
    private static final int FRAME_CAPACITY = 4;

    /** 当前请求唯一的节点与容器成员预算owner。 */
    private final BudgetLedger ledger;

    /** 当前祖先对象身份到typed path的映射，只用于区分cycle与共享DAG。 */
    private final Map<Object, ComparePath> activePath =
            new IdentityHashMap<>(PATH_CAPACITY);

    /** 显式遍历frame栈，替代会受JVM栈深限制的递归调用链。 */
    private final Deque<TraversalFrame> frames = new ArrayDeque<>(FRAME_CAPACITY);

    /** 当前请求的单调结果事实owner，后续snapshot失败直接归入同一reducer。 */
    private final CompareResultAccumulator accumulator;

    /** 单调时钟入口；生产使用nanoTime，测试可注入确定性时间。 */
    private final LongSupplier nanoTime;

    /** 当前target开始执行基础设施工作的时钟读数，只用于该target duration。 */
    private final long requestStartedNanos;

    /** 当前phase统一的deadline起点；Tracking target切换不得重置。 */
    private final long deadlineStartedNanos;

    /** 当前target进入共享ledger前的节点计数，用于把诊断归属到单个结果。 */
    private final int initialComparedNodes;

    /** 当前target进入共享ledger前的成员计数，用于把诊断归属到单个结果。 */
    private final int initialContainerMembers;

    /** 当前options允许的协作式执行时长。 */
    private final long deadlineBudgetNanos;

    private CompareRequestState(
            BudgetLedger ledger,
            CompareResultAccumulator accumulator,
            LongSupplier nanoTime,
            long requestStartedNanos,
            long deadlineStartedNanos,
            int initialComparedNodes,
            int initialContainerMembers,
            long deadlineBudgetNanos) {
        this.ledger = ledger;
        this.accumulator = accumulator;
        this.nanoTime = nanoTime;
        this.requestStartedNanos = requestStartedNanos;
        this.deadlineStartedNanos = deadlineStartedNanos;
        this.initialComparedNodes = initialComparedNodes;
        this.initialContainerMembers = initialContainerMembers;
        this.deadlineBudgetNanos = deadlineBudgetNanos;
    }

    static CompareRequestState create(
            CompareOptions options, CompareResultAccumulator accumulator) {
        return create(options, accumulator, System::nanoTime);
    }

    static CompareRequestState create(
            CompareOptions options,
            CompareResultAccumulator accumulator,
            LongSupplier nanoTime) {
        Objects.requireNonNull(options, "options");
        LongSupplier clock = Objects.requireNonNull(nanoTime, "nanoTime");
        BudgetLedger ledger = new BudgetLedger(
                options.maxComparedNodes(), options.maxElements());
        long startedNanos = clock.getAsLong();
        return new CompareRequestState(
                ledger,
                Objects.requireNonNull(accumulator, "accumulator"),
                clock,
                startedNanos,
                startedNanos,
                ledger.comparedNodes(),
                ledger.containerMembers(),
                options.deadline().toNanos());
    }

    /**
     * 为Tracking phase创建target局部frame/accumulator，同时复用phase唯一ledger与deadline起点。
     */
    static CompareRequestState createForPhase(
            CompareOptions options,
            CompareResultAccumulator accumulator,
            BudgetLedger phaseLedger,
            LongSupplier nanoTime,
            long phaseStartedNanos) {
        Objects.requireNonNull(options, "options");
        BudgetLedger ledger = Objects.requireNonNull(phaseLedger, "phaseLedger");
        LongSupplier clock = Objects.requireNonNull(nanoTime, "nanoTime");
        return new CompareRequestState(
                ledger,
                Objects.requireNonNull(accumulator, "accumulator"),
                clock,
                clock.getAsLong(),
                phaseStartedNanos,
                ledger.comparedNodes(),
                ledger.containerMembers(),
                options.deadline().toNanos());
    }

    boolean admit(BudgetEvent event, Runnable callback) {
        return ledger.admit(event, callback);
    }

    boolean deadlineReached() {
        return nanoTime.getAsLong() - deadlineStartedNanos >= deadlineBudgetNanos;
    }

    void publishDiagnostics() {
        long durationNanos = Math.max(0L, nanoTime.getAsLong() - requestStartedNanos);
        accumulator.recordExecutionDiagnostics(
                durationNanos,
                ledger.comparedNodes() - initialComparedNodes,
                ledger.containerMembers() - initialContainerMembers);
    }

    boolean enterActivePath(Object value) {
        return enterActivePath(value, ComparePath.root());
    }

    boolean enterActivePath(Object value, ComparePath path) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(path, "path");
        if (activePath.containsKey(value)) {
            return false;
        }
        activePath.put(value, path);
        return true;
    }

    Optional<ComparePath> activePath(Object value) {
        return Optional.ofNullable(activePath.get(Objects.requireNonNull(value, "value")));
    }

    void leaveActivePath(Object value) {
        activePath.remove(Objects.requireNonNull(value, "value"));
    }

    void pushFrame(TraversalFrame frame) {
        frames.push(Objects.requireNonNull(frame, "frame"));
    }

    TraversalFrame pollFrame() {
        return frames.poll();
    }

    boolean hasFrames() {
        return !frames.isEmpty();
    }

    int pendingContainerFrameLimit() {
        int remaining = Math.min(
                ledger.remainingComparedNodes(),
                ledger.remainingContainerMembers());
        return remaining == Integer.MAX_VALUE ? Integer.MAX_VALUE : remaining + 1;
    }

    CompareResultAccumulator accumulator() {
        return accumulator;
    }
}
