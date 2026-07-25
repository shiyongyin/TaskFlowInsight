package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** reducer顺序无关合同，防止后到失败覆盖已确认的业务差异。 */
class CompareReducerPermutationTests {

    @Test
    void changeAndProblemReduceToDifferentPartialInEitherOrder() {
        FieldChange change = modifyRoot();
        CompareProblem problem = new CompareProblem(
                CompareProblemCode.DIFF_FAILED, CompareStage.DIFF, Optional.empty());

        CompareResult changeFirst = reduce(change, problem, true);
        CompareResult problemFirst = reduce(change, problem, false);

        assertThat(changeFirst.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(changeFirst.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(changeFirst.getChanges()).containsExactly(change);
        assertThat(problemFirst.getOutcome()).isEqualTo(changeFirst.getOutcome());
        assertThat(problemFirst.getCompletion()).isEqualTo(changeFirst.getCompletion());
    }

    @Test
    void omittedChangeDetailsKeepDifferenceTruthAndPublishCapacityLimitation() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        accumulator.addChange(modifyRoot("a", "b"));
        accumulator.addChange(modifyRoot("b", "c"));

        CompareResult result = CompareResultReducer.reduce(accumulator);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getChanges()).hasSize(1);
        assertThat(result.getLimitations())
                .extracting(limitation -> limitation.code())
                .contains(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
        assertThat(result.getDiagnostics().omittedChanges()).isOne();
    }

    @Test
    void issueCategoriesCannotConsumeEachOthersReservedSlots() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        accumulator.addLimitation(limitation(CompareLimitationCode.DEPTH_LIMIT_REACHED));
        accumulator.addLimitation(limitation(CompareLimitationCode.DEADLINE_REACHED));
        accumulator.addLimitation(limitation(CompareLimitationCode.COLLECTION_LIMIT_REACHED));
        accumulator.addProblem(new CompareProblem(
                CompareProblemCode.DIFF_FAILED, CompareStage.DIFF, Optional.empty()));

        CompareResult result = CompareResultReducer.reduce(accumulator);

        assertThat(result.getProblems())
                .extracting(CompareProblem::code)
                .containsExactly(CompareProblemCode.DIFF_FAILED);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.DEPTH_LIMIT_REACHED,
                        CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
    }

    @Test
    void repeatedCapacityLimitationsCannotOverflowTheSharedIssueBudget() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        for (int index = 0; index < 4; index++) {
            accumulator.addLimitation(new CompareLimitation(
                    CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED,
                    CompareStage.DIFF,
                    Optional.of(ComparePath.root().append(new PropertySegment("field" + index)))));
        }
        accumulator.addProblem(new CompareProblem(
                CompareProblemCode.DIFF_FAILED, CompareStage.DIFF, Optional.empty()));
        accumulator.addLimitation(limitation(CompareLimitationCode.DEPTH_LIMIT_REACHED));

        CompareResult result = CompareResultReducer.reduce(accumulator);

        assertThat(result.getProblems()).hasSize(1);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(
                        CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED,
                        CompareLimitationCode.DEPTH_LIMIT_REACHED);
        assertThat(result.getProblems().size() + result.getLimitations().size()).isEqualTo(3);
        assertThat(result.getDiagnostics().omittedLimitations()).isEqualTo(3);
    }

    @Test
    void duplicateIssuesDoNotCreateFalseOmissionDiagnostics() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);
        CompareProblem problem = new CompareProblem(
                CompareProblemCode.DIFF_FAILED, CompareStage.DIFF, Optional.empty());
        CompareLimitation limitation = limitation(CompareLimitationCode.DEPTH_LIMIT_REACHED);

        accumulator.addProblem(problem);
        accumulator.addProblem(problem);
        accumulator.addLimitation(limitation);
        accumulator.addLimitation(limitation);
        CompareResult result = CompareResultReducer.reduce(accumulator);

        assertThat(result.getProblems()).containsExactly(problem);
        assertThat(result.getLimitations()).containsExactly(limitation);
        assertThat(result.getDiagnostics().omittedProblems()).isZero();
        assertThat(result.getDiagnostics().omittedLimitations()).isZero();
    }

    @Test
    void rootPolicyDisabledUsesDedicatedIndeterminateDisabledState() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);

        accumulator.disable();
        CompareResult result = CompareResultReducer.reduce(accumulator);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.DISABLED);
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.getProblems()).isEmpty();
        assertThat(result.similarity()).isEmpty();
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .containsExactly(CompareLimitationCode.POLICY_DISABLED);
    }

    @Test
    void unexplainedIncompleteBranchBecomesInternalProblem() {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(1, 3);

        accumulator.markBranchIncomplete();
        CompareResult result = CompareResultReducer.reduce(accumulator);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.FAILED);
        assertThat(result.getProblems())
                .extracting(CompareProblem::code)
                .containsExactly(CompareProblemCode.INTERNAL_INVARIANT_VIOLATION);
    }

    private static CompareResult reduce(
            FieldChange change, CompareProblem problem, boolean changeFirst) {
        CompareResultAccumulator accumulator = new CompareResultAccumulator(8, 4);
        if (changeFirst) {
            accumulator.addChange(change);
            accumulator.addProblem(problem);
        } else {
            accumulator.addProblem(problem);
            accumulator.addChange(change);
        }
        return CompareResultReducer.reduce(accumulator);
    }

    private static FieldChange modifyRoot() {
        return modifyRoot("a", "b");
    }

    private static FieldChange modifyRoot(String oldValue, String newValue) {
        ChangeSide before = new ChangeSide(
                ComparePath.root(), ValueSnapshot.ofString(oldValue, oldValue.length()));
        ChangeSide after = new ChangeSide(
                ComparePath.root(), ValueSnapshot.ofString(newValue, newValue.length()));
        return FieldChange.canonical(
                ChangeKind.MODIFY, Optional.of(before), Optional.of(after));
    }

    private static CompareLimitation limitation(CompareLimitationCode code) {
        return new CompareLimitation(code, CompareStage.DIFF, Optional.empty());
    }
}
