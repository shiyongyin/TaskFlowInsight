package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.FieldChange;

import java.util.List;
import java.util.Optional;

/**
 * 唯一请求级真值归并器；strategy只追加事实，不得自行拼装最终状态。
 *
 * <p>对外只开放typed failure窄入口，accumulator与完整reduce流程仍保持package-private，避免调用方伪造flag。</p>
 *
 * @since 4.0.0
 */
public final class CompareResultReducer {

    /** 无显式options入口的兼容change明细上限，防止旧strategy构造无界结果。 */
    private static final int DEFAULT_MAX_CHANGE_DETAILS = 1_000;

    /** 无显式options入口的兼容issue上限，仍满足reducer的三个预留槽。 */
    private static final int DEFAULT_MAX_ISSUES = 64;

    private CompareResultReducer() {
    }

    static CompareResult reduce(CompareResultAccumulator accumulator) {
        accumulator.ensureEvidenceOmissionLimitation();
        accumulator.ensureIncompleteBranchHasExplanation();
        CompareOutcome outcome;
        CompareCompletion completion;
        if (accumulator.disabled()) {
            outcome = CompareOutcome.INDETERMINATE;
            completion = CompareCompletion.DISABLED;
        } else if (accumulator.differenceFound()) {
            outcome = CompareOutcome.DIFFERENT;
            completion = accumulator.problemFound()
                    || accumulator.comparisonLimitationFound()
                    || accumulator.evidenceOmitted()
                    || accumulator.branchIncomplete()
                    ? CompareCompletion.PARTIAL : CompareCompletion.COMPLETE;
        } else if (accumulator.problemFound()) {
            outcome = CompareOutcome.INDETERMINATE;
            completion = CompareCompletion.FAILED;
        } else if (accumulator.comparisonLimitationFound()
                || accumulator.evidenceOmitted()
                || accumulator.branchIncomplete()) {
            outcome = CompareOutcome.INDETERMINATE;
            completion = CompareCompletion.PARTIAL;
        } else {
            outcome = CompareOutcome.EQUAL;
            completion = CompareCompletion.COMPLETE;
        }
        return CompareResult.canonical(
                outcome,
                completion,
                accumulator.changes(),
                accumulator.problems(),
                accumulator.limitations(),
                accumulator.diagnostics(),
                Optional.empty());
    }

    /**
     * 将功能开关短路归并为显式禁用状态。
     *
     * <p>禁用意味着比较没有执行，不能用空变更冒充对象相等；该入口确保 facade 与 builder
     * 共享同一真值表，避免各自拼装状态后再次产生 false-equal。</p>
     *
     * @return {@code INDETERMINATE + DISABLED} canonical结果
     */
    public static CompareResult disabled() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        accumulator.disable();
        return reduce(accumulator);
    }

    /**
     * 将单个非预期执行故障交给同一真值表归并，不保存异常对象、message或stack。
     *
     * @param code typed problem code
     * @param stage 故障发生的执行阶段
     * @return `INDETERMINATE + FAILED` canonical结果
     */
    public static CompareResult failure(CompareProblemCode code, CompareStage stage) {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        accumulator.addProblem(new CompareProblem(code, stage, Optional.empty()));
        return reduce(accumulator);
    }

    /**
     * 将单个已知执行边界交给同一真值表归并，避免strategy用空changes伪造不同或相等。
     *
     * @param code typed limitation code
     * @param stage 限制发生的执行阶段
     * @return `INDETERMINATE + PARTIAL` canonical结果
     */
    public static CompareResult limited(CompareLimitationCode code, CompareStage stage) {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        accumulator.addLimitation(new CompareLimitation(code, stage, Optional.empty()));
        return reduce(accumulator);
    }

    /**
     * 保留已确认差异并追加执行限制；限制不能覆盖或删除先前change事实。
     *
     * @param changes 已确认的canonical change facts
     * @param code typed limitation code
     * @param stage 限制发生的执行阶段
     * @return 无差异时indeterminate partial，有差异时different partial
     */
    public static CompareResult limited(
            List<FieldChange> changes,
            CompareLimitationCode code,
            CompareStage stage) {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(
                DEFAULT_MAX_CHANGE_DETAILS,
                DEFAULT_MAX_ISSUES);
        for (FieldChange change : List.copyOf(changes)) {
            accumulator.addChange(change);
        }
        accumulator.addLimitation(new CompareLimitation(code, stage, Optional.empty()));
        return reduce(accumulator);
    }

    /**
     * 归并一个完整执行分支的change facts，并应用默认结果明细上限。
     *
     * <p>默认值来自已接受参数矩阵；Policy落地后由同一入口接收effective limits，strategy仍不拥有真值表。</p>
     *
     * @param changes canonical执行顺序产生的change facts，不能为空
     * @return bounded canonical complete/partial结果
     */
    public static CompareResult complete(List<FieldChange> changes) {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(
                DEFAULT_MAX_CHANGE_DETAILS,
                DEFAULT_MAX_ISSUES);
        for (FieldChange change : List.copyOf(changes)) {
            accumulator.addChange(change);
        }
        return reduce(accumulator);
    }
}
