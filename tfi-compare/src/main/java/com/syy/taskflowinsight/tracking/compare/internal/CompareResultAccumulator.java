package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PathSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 单次请求的结果事实累加器。
 *
 * <p>真值flag独立于有界明细列表单调更新，确保容量耗尽后仍不会把已知差异或故障恢复成COMPLETE/EQUAL。</p>
 */
final class CompareResultAccumulator {

    /** 为首个anchor、problem、普通limitation与W2104保留的固定结果空间。 */
    private static final long RESERVED_RESULT_CHARS = 1_024;

    /** overlong path降级锚点只保留固定省略事实，不能复制原始值或path文本。 */
    private static final ValueSnapshot OMITTED_ANCHOR_VALUE = ValueSnapshot.ofString("anchor", 0);

    /** 最多保留的change明细；超出后仍通过differenceFound维持真值。 */
    private final int maxChangeDetails;

    /** problem与limitation共享的明细容量，预留槽保证首个关键事实可见。 */
    private final int maxIssues;

    /** 单条已发布typed path的canonical事实成本上限。 */
    private final int maxPathEncodedChars;

    /** 当前请求允许保留的全部code、path与value文本事实总量。 */
    private final long maxResultTotalChars;

    /** 兼容reducer无options入口不重复解释预算；请求内核必须启用精确记账。 */
    private final boolean enforceResultBudget;

    private final List<FieldChange> changes = new ArrayList<>();
    private final List<CompareProblem> problems = new ArrayList<>();
    private final List<CompareLimitation> limitations = new ArrayList<>();

    /** 是否发现过确定差异；不能由明细截断或后续故障清除。 */
    private boolean differenceFound;

    /** 是否发生过非预期能力故障，用于区分FAILED与普通限制。 */
    private boolean problemFound;

    /** 是否触达过预期执行边界，用于阻止空明细被归并为EQUAL。 */
    private boolean comparisonLimitationFound;

    /** 是否因结果容量丢弃过证据；丢弃后只能得到PARTIAL。 */
    private boolean evidenceOmitted;

    /** 是否存在未完成分支；即使没有明细也必须由reducer给出非完整结论。 */
    private boolean branchIncomplete;

    /** 是否在执行前被policy禁用；该状态与其他比较事实互斥。 */
    private boolean disabled;

    /** 未保留的确定change数量，供有界诊断解释明细与真值差异。 */
    private long omittedChanges;

    /** 未保留的problem数量，避免调用方把空列表理解为从未失败。 */
    private long omittedProblems;

    /** 未保留的limitation数量，避免容量耗尽掩盖资源边界。 */
    private long omittedLimitations;

    /** 因单路径预算降级或省略的exact path数量。 */
    private long omittedPaths;

    /** 当前结果已保留的canonical文本事实成本，追加前必须检查上限。 */
    private long retainedResultChars;

    /** 已使用的非首要issue共享槽数量，用于执行maxIssues的保留策略。 */
    private int extraIssuesRetained;

    /** 当前请求从创建state到最终归并的单调执行时长。 */
    private long durationNanos;

    /** 当前请求已准入的snapshot、diff与候选配对节点总数。 */
    private long comparedNodes;

    /** 当前请求已准入的Map entry、Collection或array成员总数。 */
    private long consumedElements;

    CompareResultAccumulator(int maxChangeDetails, int maxIssues) {
        this(maxChangeDetails, maxIssues, Integer.MAX_VALUE, Long.MAX_VALUE, false);
    }

    CompareResultAccumulator(CompareOptions options) {
        this(
                Objects.requireNonNull(options, "options").maxChangeDetails(),
                options.maxIssues(),
                options.maxPathEncodedChars(),
                options.maxResultTotalChars(),
                true);
    }

    private CompareResultAccumulator(
            int maxChangeDetails,
            int maxIssues,
            int maxPathEncodedChars,
            long maxResultTotalChars,
            boolean enforceResultBudget) {
        if (maxChangeDetails < 1 || maxIssues < 3) {
            throw new IllegalArgumentException("result detail limits do not satisfy reserved slots");
        }
        if (maxPathEncodedChars < 0 || maxResultTotalChars < 0) {
            throw new IllegalArgumentException("result fact limits must not be negative");
        }
        this.maxChangeDetails = maxChangeDetails;
        this.maxIssues = maxIssues;
        this.maxPathEncodedChars = maxPathEncodedChars;
        this.maxResultTotalChars = maxResultTotalChars;
        this.enforceResultBudget = enforceResultBudget;
    }

    void addChange(FieldChange change) {
        Objects.requireNonNull(change, "change");
        differenceFound = true;
        FieldChange boundedChange = enforcePathBudget(change);
        if (boundedChange == null) {
            return;
        }
        if (changes.size() < maxChangeDetails) {
            retainChangeWithinTotalBudget(boundedChange);
        } else {
            omittedChanges++;
            evidenceOmitted = true;
        }
    }

    void addProblem(CompareProblem problem) {
        Objects.requireNonNull(problem, "problem");
        problemFound = true;
        if (problems.contains(problem)) {
            return;
        }
        boolean requiredSlot = problems.isEmpty();
        if (!requiredSlot && !hasExtraIssueSlot()) {
            omittedProblems++;
            evidenceOmitted = true;
            return;
        }
        Optional<CompareProblem> fittedProblem = fitProblemToResultBudget(problem, requiredSlot);
        if (fittedProblem.isEmpty()) {
            omittedProblems++;
            evidenceOmitted = true;
            return;
        }
        problem = fittedProblem.orElseThrow();
        if (problems.contains(problem)) {
            return;
        }
        problems.add(problem);
        recordRetainedIssue(problem.code().wireCode(), problem.stage(), problem.path());
        if (!requiredSlot) {
            extraIssuesRetained++;
        }
    }

    void addLimitation(CompareLimitation limitation) {
        Objects.requireNonNull(limitation, "limitation");
        comparisonLimitationFound = true;
        branchIncomplete = true;
        if (limitations.contains(limitation)) {
            return;
        }
        boolean capacityLimitation = limitation.code()
                == CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED;
        boolean firstCapacityLimitation = capacityLimitation && limitations.stream().noneMatch(item ->
                item.code() == CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
        boolean firstComparisonLimitation = !capacityLimitation && limitations.stream().noneMatch(item ->
                item.code() != CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
        // 三个保留槽分别只保护首个problem、普通执行边界和容量告警，不能让同类告警绕过总上限。
        boolean requiredSlot = firstCapacityLimitation || firstComparisonLimitation;
        if (!requiredSlot && !hasExtraIssueSlot()) {
            omittedLimitations++;
            evidenceOmitted = true;
            return;
        }
        Optional<CompareLimitation> fittedLimitation = fitLimitationToResultBudget(
                limitation, requiredSlot);
        if (fittedLimitation.isEmpty()) {
            omittedLimitations++;
            evidenceOmitted = true;
            return;
        }
        limitation = fittedLimitation.orElseThrow();
        if (limitations.contains(limitation)) {
            return;
        }
        limitations.add(limitation);
        recordRetainedIssue(limitation.code().wireCode(), limitation.stage(), limitation.path());
        if (!requiredSlot) {
            extraIssuesRetained++;
        }
    }

    private int issueCount() {
        return problems.size() + limitations.size();
    }

    private boolean hasExtraIssueSlot() {
        return extraIssuesRetained < maxIssues - 3;
    }

    void disable() {
        if (differenceFound || problemFound || comparisonLimitationFound
                || !changes.isEmpty() || !problems.isEmpty() || !limitations.isEmpty()) {
            throw new IllegalStateException("root disabled must be recorded before comparison facts");
        }
        if (disabled) {
            return;
        }
        disabled = true;
        limitations.add(new CompareLimitation(
                CompareLimitationCode.POLICY_DISABLED,
                CompareStage.PLAN,
                Optional.empty()));
    }

    void markBranchIncomplete() {
        branchIncomplete = true;
    }

    void ensureIncompleteBranchHasExplanation() {
        if (branchIncomplete && !problemFound && !comparisonLimitationFound
                && !evidenceOmitted && !disabled) {
            addProblem(new CompareProblem(
                    CompareProblemCode.INTERNAL_INVARIANT_VIOLATION,
                    CompareStage.INTERNAL,
                    Optional.empty()));
        }
    }

    void ensureEvidenceOmissionLimitation() {
        if (!evidenceOmitted || limitations.stream().anyMatch(limitation ->
                limitation.code() == CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED)) {
            return;
        }
        comparisonLimitationFound = true;
        branchIncomplete = true;
        if (issueCount() < maxIssues) {
            CompareLimitation limitation = new CompareLimitation(
                    CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED,
                    CompareStage.INTERNAL,
                    Optional.of(ComparePath.root()));
            limitations.add(limitation);
            recordRetainedIssue(limitation.code().wireCode(), limitation.stage(), limitation.path());
        }
    }

    boolean differenceFound() {
        return differenceFound;
    }

    boolean problemFound() {
        return problemFound;
    }

    boolean comparisonLimitationFound() {
        return comparisonLimitationFound;
    }

    boolean evidenceOmitted() {
        return evidenceOmitted;
    }

    boolean branchIncomplete() {
        return branchIncomplete;
    }

    boolean disabled() {
        return disabled;
    }

    List<FieldChange> changes() {
        return List.copyOf(changes);
    }

    List<CompareProblem> problems() {
        return List.copyOf(problems);
    }

    List<CompareLimitation> limitations() {
        return List.copyOf(limitations);
    }

    void recordExecutionDiagnostics(
            long durationNanos,
            long comparedNodes,
            long consumedElements) {
        if (durationNanos < 0 || comparedNodes < 0 || consumedElements < 0) {
            throw new IllegalArgumentException("execution diagnostics must not be negative");
        }
        this.durationNanos = durationNanos;
        this.comparedNodes = comparedNodes;
        this.consumedElements = consumedElements;
    }

    CompareDiagnostics diagnostics() {
        return new CompareDiagnostics(
                durationNanos, Optional.empty(), List.of(), Optional.empty(),
                comparedNodes, consumedElements, retainedResultChars, omittedPaths,
                omittedChanges, omittedProblems, omittedLimitations);
    }

    private void retainChangeWithinTotalBudget(FieldChange change) {
        long changeCost = ResultFactCost.change(change);
        if (!enforceResultBudget || canRetainOrdinary(changeCost)) {
            changes.add(change);
            recordRetainedCost(changeCost);
            return;
        }

        omittedChanges++;
        evidenceOmitted = true;
        if (!changes.isEmpty()) {
            return;
        }
        FieldChange anchor = differenceAnchor(change);
        long anchorCost = ResultFactCost.change(anchor);
        if (!canRetainRequired(anchorCost)) {
            throw new IllegalStateException("result budget cannot retain required difference anchor");
        }
        changes.add(anchor);
        recordRetainedCost(anchorCost);
    }

    private FieldChange differenceAnchor(FieldChange change) {
        ComparePath path = change.before().or(() -> change.after()).orElseThrow().path();
        ComparePath anchorPath = nearestBoundedAncestor(path);
        ChangeSide anchorSide = new ChangeSide(anchorPath, OMITTED_ANCHOR_VALUE);
        return FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(anchorSide),
                Optional.of(anchorSide));
    }

    private boolean canRetainOrdinary(long cost) {
        long reserve = Math.min(RESERVED_RESULT_CHARS, maxResultTotalChars);
        return cost <= maxResultTotalChars - reserve - retainedResultChars;
    }

    private boolean canRetainRequired(long cost) {
        return cost <= maxResultTotalChars - retainedResultChars;
    }

    private void recordRetainedIssue(
            String wireCode,
            CompareStage stage,
            Optional<ComparePath> path) {
        if (!enforceResultBudget) {
            return;
        }
        long cost = ResultFactCost.issue(wireCode, stage, path);
        if (!canRetainRequired(cost)) {
            throw new IllegalStateException("result budget cannot retain admitted issue");
        }
        recordRetainedCost(cost);
    }

    private void recordRetainedCost(long cost) {
        if (enforceResultBudget) {
            retainedResultChars = ResultFactCost.saturatingAdd(retainedResultChars, cost);
        }
    }

    private FieldChange enforcePathBudget(FieldChange change) {
        Optional<ChangeSide> before = change.before();
        Optional<ChangeSide> after = change.after();
        boolean beforeOverLimit = before
                .map(ChangeSide::path)
                .map(ComparePath::canonicalFactCost)
                .orElse(0) > maxPathEncodedChars;
        boolean afterOverLimit = after
                .map(ChangeSide::path)
                .map(ComparePath::canonicalFactCost)
                .orElse(0) > maxPathEncodedChars;
        if (!beforeOverLimit && !afterOverLimit) {
            return change;
        }

        ComparePath beforePath = before.map(ChangeSide::path).orElse(null);
        ComparePath afterPath = after.map(ChangeSide::path).orElse(null);
        omittedPaths += beforeOverLimit ? 1 : 0;
        if (afterOverLimit && (!beforeOverLimit || !Objects.equals(beforePath, afterPath))) {
            omittedPaths++;
        }
        omittedChanges++;
        evidenceOmitted = true;
        if (!changes.isEmpty()) {
            return null;
        }

        ComparePath overlongPath = beforeOverLimit ? beforePath : afterPath;
        ComparePath anchorPath = nearestBoundedAncestor(overlongPath);
        ChangeSide anchorSide = new ChangeSide(anchorPath, OMITTED_ANCHOR_VALUE);
        return FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(anchorSide),
                Optional.of(anchorSide));
    }

    private Optional<CompareProblem> fitProblemToResultBudget(
            CompareProblem problem,
            boolean requiredSlot) {
        ResultIssuePathFit pathFit = fitIssuePathToResultBudget(
                problem.code().wireCode(), problem.stage(), problem.path(), requiredSlot);
        if (!pathFit.admitted()) {
            return Optional.empty();
        }
        CompareProblem boundedProblem = pathFit.path().equals(problem.path())
                ? problem
                : new CompareProblem(problem.code(), problem.stage(), pathFit.path());
        return Optional.of(boundedProblem);
    }

    private Optional<CompareLimitation> fitLimitationToResultBudget(
            CompareLimitation limitation,
            boolean requiredSlot) {
        ResultIssuePathFit pathFit = fitIssuePathToResultBudget(
                limitation.code().wireCode(), limitation.stage(), limitation.path(), requiredSlot);
        if (!pathFit.admitted()) {
            return Optional.empty();
        }
        CompareLimitation boundedLimitation = pathFit.path().equals(limitation.path())
                ? limitation
                : new CompareLimitation(limitation.code(), limitation.stage(), pathFit.path());
        return Optional.of(boundedLimitation);
    }

    private ResultIssuePathFit fitIssuePathToResultBudget(
            String wireCode,
            CompareStage stage,
            Optional<ComparePath> path,
            boolean requiredSlot) {
        if (!enforceResultBudget) {
            return new ResultIssuePathFit(true, path);
        }
        long fixedCost = ResultFactCost.issue(wireCode, stage, Optional.empty());
        long reserve = requiredSlot ? 0 : Math.min(RESERVED_RESULT_CHARS, maxResultTotalChars);
        long available = maxResultTotalChars - reserve - retainedResultChars;
        if (fixedCost > available) {
            return new ResultIssuePathFit(false, Optional.empty());
        }
        if (path.isEmpty()) {
            return new ResultIssuePathFit(true, path);
        }
        long availablePathFacts = available - fixedCost;
        long pathCeiling = Math.min(
                maxPathEncodedChars,
                availablePathFacts == 0 ? 0 : availablePathFacts - 1);
        ComparePath boundedPath = nearestBoundedAncestor(path.orElseThrow(), pathCeiling);
        if (!boundedPath.equals(path.orElseThrow())) {
            omittedPaths++;
            evidenceOmitted = true;
        }
        return new ResultIssuePathFit(true, Optional.of(boundedPath));
    }

    private ComparePath nearestBoundedAncestor(ComparePath path) {
        return nearestBoundedAncestor(path, maxPathEncodedChars);
    }

    private ComparePath nearestBoundedAncestor(ComparePath path, long costCeiling) {
        ComparePath bounded = ComparePath.root();
        for (PathSegment segment : path.segments()) {
            ComparePath candidate = bounded.append(segment);
            if (candidate.canonicalFactCost() > costCeiling) {
                break;
            }
            bounded = candidate;
        }
        return bounded;
    }

}
