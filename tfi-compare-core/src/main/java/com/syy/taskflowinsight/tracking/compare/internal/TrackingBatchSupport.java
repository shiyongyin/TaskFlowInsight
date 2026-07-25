package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * default provider复用canonical snapshot/diff时的内部batch实现。
 *
 * <p>每个target保留独立frame与result accumulator，但同一phase复用ledger和deadline起点；这样既不把
 * 可变预算公开到SPI，也不会因切换target重置限制。</p>
 */
final class TrackingBatchSupport {

    private TrackingBatchSupport() {
    }

    static TrackingBatchScope open(
            List<TrackingExecutor.Target> targets,
            CompareOptions options,
            ComparePolicy policy,
            UnaryOperator<CompareResult> decorator) {
        List<Slot> slots = new ArrayList<>(targets.size());
        try {
            if (!policy.enabled()) {
                CompareResult disabled = CompareResultReducer.disabled();
                targets.forEach(target -> slots.add(Slot.terminal(target.name(), disabled)));
                return new DefaultBatchScope(slots, options, policy, decorator);
            }
            Phase baselinePhase = new Phase(options);
            for (TrackingExecutor.Target target : targets) {
                slots.add(captureBaseline(target, options, policy, baselinePhase));
            }
            return new DefaultBatchScope(slots, options, policy, decorator);
        } catch (Error fatal) {
            // scope冻结也属于begin；任何fatal都必须先逆序释放已建立baseline引用。
            closeReverse(slots);
            throw fatal;
        }
    }

    private static Slot captureBaseline(
            TrackingExecutor.Target target,
            CompareOptions options,
            ComparePolicy policy,
            Phase phase) {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(options);
        CompareRequestState state = phase.newRequest(accumulator);
        try {
            SnapshotResult snapshot = RequestLocalSnapshot.capture(
                    target.value(), options, policy, state);
            state.publishDiagnostics();
            CompareResult status = CompareResultReducer.reduce(accumulator);
            return snapshot.completion() == CompareCompletion.COMPLETE
                    ? Slot.active(target, snapshot, status.getDiagnostics())
                    : Slot.terminal(target.name(), status);
        } catch (RuntimeException exception) {
            return Slot.terminal(target.name(), infrastructureFailure());
        }
    }

    private static CompareResult infrastructureFailure() {
        return CompareResultReducer.failure(
                CompareProblemCode.TRACKING_CAPTURE_FAILED,
                CompareStage.TRACKING);
    }

    private static void closeReverse(List<Slot> slots) {
        for (int index = slots.size() - 1; index >= 0; index--) {
            slots.get(index).close();
        }
    }

    /** phase只共享预算和deadline；frame、visited与结果事实仍按target隔离。 */
    private static final class Phase {

        /** 当前phase唯一的节点/成员账本。 */
        private final BudgetLedger ledger;
        /** 单调时钟入口。 */
        private final LongSupplier nanoTime = System::nanoTime;
        /** phase deadline统一起点。 */
        private final long startedNanos;
        /** 所有target共享的effective options。 */
        private final CompareOptions options;

        private Phase(CompareOptions options) {
            this.options = options;
            this.ledger = new BudgetLedger(
                    options.maxComparedNodes(), options.maxElements());
            this.startedNanos = nanoTime.getAsLong();
        }

        private CompareRequestState newRequest(CompareResultAccumulator accumulator) {
            return CompareRequestState.createForPhase(
                    options, accumulator, ledger, nanoTime, startedNanos);
        }
    }

    /** 一个输入位置要么持有完整baseline，要么持有已规范化terminal结果。 */
    private static final class Slot {

        /** process-local关联名；不进入toString。 */
        private final String name;
        /** baseline失败或预期限制的最终结果。 */
        private final CompareResult terminalResult;
        /** baseline阶段归属于该target的诊断。 */
        private final CompareDiagnostics baselineDiagnostics;
        /** capture前必须保留的业务对象引用，close后释放。 */
        private Object target;
        /** 不含业务对象的不可变baseline事实，capture/close后释放。 */
        private SnapshotResult baseline;

        private Slot(
                String name,
                Object target,
                SnapshotResult baseline,
                CompareDiagnostics baselineDiagnostics,
                CompareResult terminalResult) {
            this.name = name;
            this.target = target;
            this.baseline = baseline;
            this.baselineDiagnostics = baselineDiagnostics;
            this.terminalResult = terminalResult;
        }

        private static Slot active(
                TrackingExecutor.Target target,
                SnapshotResult baseline,
                CompareDiagnostics diagnostics) {
            return new Slot(target.name(), target.value(), baseline, diagnostics, null);
        }

        private static Slot terminal(String name, CompareResult result) {
            return new Slot(name, null, null, CompareDiagnostics.empty(), result);
        }

        private TrackingExecutor.Item capture(
                CompareOptions options,
                ComparePolicy policy,
                Phase phase) {
            if (terminalResult != null) {
                return new TrackingExecutor.Item(name, terminalResult);
            }
            CompareResultAccumulator accumulator = new CompareResultAccumulator(options);
            CompareRequestState state = phase.newRequest(accumulator);
            try {
                SnapshotResult after = RequestLocalSnapshot.capture(target, options, policy, state);
                CompareDiffer.diff(baseline, after, state, options, ignored -> Optional.empty());
            } catch (RuntimeException exception) {
                accumulator.addProblem(new CompareProblem(
                        CompareProblemCode.TRACKING_CAPTURE_FAILED,
                        CompareStage.TRACKING,
                        Optional.empty()));
            }
            state.publishDiagnostics();
            CompareResult result = combineDiagnostics(
                    CompareResultReducer.reduce(accumulator), baselineDiagnostics);
            close();
            return new TrackingExecutor.Item(name, result);
        }

        private void close() {
            target = null;
            baseline = null;
        }
    }

    /** single-capture、input-order与逆序幂等close的标准scope。 */
    private static final class DefaultBatchScope implements TrackingBatchScope {

        /** 按输入顺序冻结的slot。 */
        private final List<Slot> slots;
        /** 当前batch唯一的effective options。 */
        private final CompareOptions options;
        /** snapshot相等域所属runtime policy。 */
        private final ComparePolicy policy;
        /** Engine提供的fingerprint装饰器。 */
        private final UnaryOperator<CompareResult> decorator;
        /** capture消费状态。 */
        private boolean captured;
        /** close幂等状态。 */
        private boolean closed;

        private DefaultBatchScope(
                List<Slot> slots,
                CompareOptions options,
                ComparePolicy policy,
                UnaryOperator<CompareResult> decorator) {
            this.slots = List.copyOf(slots);
            this.options = options;
            this.policy = policy;
            this.decorator = decorator;
        }

        /**
         * 为全部active slot创建一份fresh after phase，防止target切换重置预算。
         *
         * @return 按输入顺序冻结的tracking items
         */
        @Override
        public List<TrackingExecutor.Item> capture() {
            if (captured || closed) {
                throw new IllegalStateException("tracking batch is already consumed");
            }
            captured = true;
            Phase afterPhase = new Phase(options);
            List<TrackingExecutor.Item> items = new ArrayList<>(slots.size());
            for (Slot slot : slots) {
                TrackingExecutor.Item item = slot.capture(options, policy, afterPhase);
                items.add(new TrackingExecutor.Item(
                        item.name(), decorator.apply(item.result())));
            }
            return List.copyOf(items);
        }

        /** 按slot逆序幂等释放baseline引用，不完成外部Session或Task。 */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeReverse(slots);
        }

        /** @return 仅含有界target数量、不含名称或结果的安全文本 */
        @Override
        public String toString() {
            return "TrackingBatchScope[targetCount=" + slots.size() + "]";
        }
    }

    /** baseline与after+diff诊断做饱和合计，action wall time从未进入任一state。 */
    private static CompareResult combineDiagnostics(
            CompareResult result,
            CompareDiagnostics baseline) {
        CompareDiagnostics current = result.getDiagnostics();
        CompareDiagnostics combined = new CompareDiagnostics(
                saturatingAdd(baseline.durationNanos(), current.durationNanos()),
                current.rootAlgorithmId(),
                current.appliedAlgorithmIds(),
                current.effectivePolicyFingerprint(),
                saturatingAdd(baseline.comparedNodes(), current.comparedNodes()),
                saturatingAdd(baseline.consumedElements(), current.consumedElements()),
                saturatingAdd(baseline.retainedResultChars(), current.retainedResultChars()),
                saturatingAdd(baseline.omittedPaths(), current.omittedPaths()),
                saturatingAdd(baseline.omittedChanges(), current.omittedChanges()),
                saturatingAdd(baseline.omittedProblems(), current.omittedProblems()),
                saturatingAdd(baseline.omittedLimitations(), current.omittedLimitations()));
        return CompareResult.canonical(
                result.getOutcome(),
                result.getCompletion(),
                result.getChanges(),
                result.getProblems(),
                result.getLimitations(),
                combined,
                result.similarity());
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
