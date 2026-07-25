package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 将单次direct compare的两侧snapshot、typed path diff和预算收口到同一个请求状态。
 *
 * <p>该门面位于internal包，是因为{@link CompareRequestState}不能进入Engine字段或公共SPI签名；
 * Engine只接收最终{@link CompareResult}，从结构上阻止strategy重置ledger或泄漏visited/frame。</p>
 *
 * @since 4.0.0
 */
public final class RequestLocalCompareKernel {

    private RequestLocalCompareKernel() {
    }

    /**
     * 为默认provider建立不公开mutable ledger的canonical tracking batch。
     *
     * @param targets 已校验且防御复制的有序目标
     * @param options 已按runtime校验的effective options
     * @param policy snapshot相等域所属runtime policy
     * @param decorator 为最终结果补充runtime级诊断事实
     * @return 线程封闭的single-capture scope
     */
    public static TrackingBatchScope openTrackingBatch(
            List<TrackingExecutor.Target> targets,
            CompareOptions options,
            ComparePolicy policy,
            UnaryOperator<CompareResult> decorator) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(decorator, "decorator");
        return TrackingBatchSupport.open(targets, options, policy, decorator);
    }

    /**
     * 使用一份request-global ledger比较两个同类型、非null且非同引用对象。
     *
     * <p>每个实际物化的单侧节点分别消费snapshot预算；diff按canonical path逐项判定，
     * 因而预算达到时可以在下一项业务判定前停止并发布typed limitation。</p>
     *
     * @param before 已通过Engine root fast-path校验的变更前对象
     * @param after 与before运行时类型相同的变更后对象
     * @param options 当前调用已由runtime policy验证的不可变选项
     * @return 经唯一accumulator/reducer归并的canonical结果
     */
    public static CompareResult compareObjects(
            Object before,
            Object after,
            CompareOptions options) {
        Objects.requireNonNull(options, "options");
        return compareObjects(
                before, after, options, options.getPolicy(), ignored -> Optional.empty());
    }

    /** 使用显式Runtime Policy捕获typed snapshot，避免Options来源改变相等域。 */
    public static CompareResult compareObjects(
            Object before,
            Object after,
            CompareOptions options,
            ComparePolicy policy) {
        return compareObjects(before, after, options, policy, ignored -> Optional.empty());
    }

    /**
     * 在typed diff node内应用字段级相等性覆盖。
     *
     * <p>覆盖函数只决定当前已配对路径是否相等，不接收request state，也不能重置预算；空值表示沿用
     * snapshot事实比较。Engine借此执行冻结的property comparator，而无需公开内核上下文。</p>
     *
     * @param before 已通过Engine root fast-path校验的变更前对象
     * @param after 与before运行时类型相同的变更后对象
     * @param options 当前调用已由runtime policy验证的不可变选项
     * @param equalityOverride path级相等性覆盖；无注册规则时返回empty
     * @return 经唯一accumulator/reducer归并的canonical结果
     */
    public static CompareResult compareObjects(
            Object before,
            Object after,
            CompareOptions options,
            Function<ComparePath, Optional<Boolean>> equalityOverride) {
        Objects.requireNonNull(options, "options");
        return compareObjects(before, after, options, options.getPolicy(), equalityOverride);
    }

    /** Runtime Policy与字段比较覆盖共同进入同一个request-local执行图。 */
    public static CompareResult compareObjects(
            Object before,
            Object after,
            CompareOptions options,
            ComparePolicy policy,
            Function<ComparePath, Optional<Boolean>> equalityOverride) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equalityOverride, "equalityOverride");
        CompareResultAccumulator accumulator = new CompareResultAccumulator(options);
        CompareRequestState state = CompareRequestState.create(options, accumulator);

        SnapshotResult beforeSnapshot = RequestLocalSnapshot.capture(before, options, policy, state);
        SnapshotResult afterSnapshot = RequestLocalSnapshot.capture(after, options, policy, state);
        CompareDiffer.diff(beforeSnapshot, afterSnapshot, state, options, equalityOverride);

        state.publishDiagnostics();
        return CompareResultReducer.reduce(accumulator);
    }

    /**
     * 将选中的strategy调用绑定到一个DIFF_NODE，扩展内部工作不得重复回写ledger。
     *
     * @param options 当前调用已验证的不可变选项
     * @param work 已完成唯一选择的strategy工作，只有准入成功才会执行
     * @return 保留strategy算法元数据并覆盖请求级预算诊断的canonical结果
     */
    public static CompareResult executeDiff(
            CompareOptions options,
            Supplier<CompareResult> work) {
        return execute(options, null, null, null, false, work);
    }

    /**
     * 在built-in兼容differ之前捕获两侧输入，并与回调共享同一ledger。
     *
     * <p>任一snapshot不完整时不执行旧differ，防止它重新读取完整原对象绕过预算。collection配对
     * 仍由后续KRN/COL任务替换，本卡只固定请求隔离和消费边界。</p>
     *
     * @param before 非null的built-in变更前输入
     * @param after 与before同类型的built-in变更后输入
     * @param options 当前调用已验证的不可变选项
     * @param work 两侧snapshot完整后执行一次的兼容differ
     * @return 合并snapshot限制、differ事实和请求诊断的canonical结果
     */
    public static CompareResult executeSnapshotDiff(
            Object before,
            Object after,
            CompareOptions options,
            Supplier<CompareResult> work) {
        Objects.requireNonNull(options, "options");
        return executeSnapshotDiff(before, after, options, options.getPolicy(), work);
    }

    /** 使用Runtime Policy执行兼容differ之前的两侧snapshot。 */
    public static CompareResult executeSnapshotDiff(
            Object before,
            Object after,
            CompareOptions options,
            ComparePolicy policy,
            Supplier<CompareResult> work) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        return execute(options, policy, before, after, true, work);
    }

    private static CompareResult execute(
            CompareOptions options,
            ComparePolicy policy,
            Object before,
            Object after,
            boolean captureInputs,
            Supplier<CompareResult> work) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(work, "work");
        CompareResultAccumulator accumulator = new CompareResultAccumulator(options);
        CompareRequestState state = CompareRequestState.create(options, accumulator);
        AtomicReference<CompareResult> delegated = new AtomicReference<>();
        boolean snapshotsComplete = true;
        if (captureInputs) {
            Objects.requireNonNull(policy, "policy");
            SnapshotResult beforeSnapshot = RequestLocalSnapshot.capture(before, options, policy, state);
            SnapshotResult afterSnapshot = RequestLocalSnapshot.capture(after, options, policy, state);
            snapshotsComplete = beforeSnapshot.completion() == CompareCompletion.COMPLETE
                    && afterSnapshot.completion() == CompareCompletion.COMPLETE;
        }

        if (snapshotsComplete) {
            if (state.deadlineReached()) {
                addLimitation(state, CompareLimitationCode.DEADLINE_REACHED, ComparePath.root());
            } else {
                try {
                    boolean admitted = state.admit(BudgetEvent.DIFF_NODE, () -> delegated.set(work.get()));
                    if (!admitted) {
                        addLimitation(state, CompareLimitationCode.NODE_BUDGET_REACHED, ComparePath.root());
                    } else if (delegated.get() == null) {
                        addProblem(state, CompareProblemCode.DIFF_FAILED, ComparePath.root());
                    }
                } catch (RuntimeException exception) {
                    addProblem(state, CompareProblemCode.DIFF_FAILED, ComparePath.root());
                }
                if (state.deadlineReached()) {
                    addLimitation(state, CompareLimitationCode.DEADLINE_REACHED, ComparePath.root());
                }
            }
        }

        CompareResult delegatedResult = delegated.get();
        if (delegatedResult != null) {
            merge(delegatedResult, accumulator);
        }
        state.publishDiagnostics();
        CompareResult reduced = CompareResultReducer.reduce(accumulator);
        return delegatedResult == null ? reduced : preserveMetadata(reduced, delegatedResult);
    }

    private static void addLimitation(
            CompareRequestState state,
            CompareLimitationCode code,
            ComparePath path) {
        state.accumulator().addLimitation(new CompareLimitation(
                code, CompareStage.DIFF, Optional.of(path)));
    }

    private static void addProblem(
            CompareRequestState state,
            CompareProblemCode code,
            ComparePath path) {
        state.accumulator().addProblem(new CompareProblem(
                code, CompareStage.DIFF, Optional.of(path)));
    }

    private static void merge(
            CompareResult result,
            CompareResultAccumulator accumulator) {
        result.getChanges().forEach(accumulator::addChange);
        result.getProblems().forEach(accumulator::addProblem);
        result.getLimitations().forEach(accumulator::addLimitation);
    }

    private static CompareResult preserveMetadata(
            CompareResult reduced,
            CompareResult delegated) {
        CompareDiagnostics execution = reduced.getDiagnostics();
        CompareDiagnostics original = delegated.getDiagnostics();
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                execution.durationNanos(),
                original.rootAlgorithmId(),
                original.appliedAlgorithmIds(),
                original.effectivePolicyFingerprint(),
                execution.comparedNodes(),
                execution.consumedElements(),
                execution.retainedResultChars(),
                ResultFactCost.saturatingAdd(
                        execution.omittedPaths(), original.omittedPaths()),
                ResultFactCost.saturatingAdd(
                        execution.omittedChanges(), original.omittedChanges()),
                ResultFactCost.saturatingAdd(
                        execution.omittedProblems(), original.omittedProblems()),
                ResultFactCost.saturatingAdd(
                        execution.omittedLimitations(), original.omittedLimitations()));
        return CompareResult.canonical(
                reduced.getOutcome(),
                reduced.getCompletion(),
                reduced.getChanges(),
                reduced.getProblems(),
                reduced.getLimitations(),
                diagnostics,
                reduced.getCompletion() == CompareCompletion.COMPLETE
                        ? delegated.similarity() : Optional.empty());
    }
}
