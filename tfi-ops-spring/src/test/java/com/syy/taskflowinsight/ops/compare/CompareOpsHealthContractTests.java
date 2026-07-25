package com.syy.taskflowinsight.ops.compare;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompareOpsHealthContractTests {

    @Test
    void should_report_current_graph_as_up_without_reading_last_result() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        CompareOperations operations = mock(CompareOperations.class);
        when(operations.compare("before", "after")).thenReturn(failedResult());
        CompareHealthIndicator health = new CompareHealthIndicator(runtime, operations);

        operations.compare("before", "after");

        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        assertThat(health.health().getDetails())
                .containsOnlyKeys("runtime", "operations", "policy")
                .containsEntry("runtime", "available")
                .containsEntry("operations", "available")
                .containsEntry("policy", "valid");
    }

    private static CompareResult failedResult() {
        return CompareResult.canonical(
                CompareOutcome.INDETERMINATE,
                CompareCompletion.FAILED,
                List.of(),
                List.of(new CompareProblem(
                        CompareProblemCode.DIFF_FAILED,
                        CompareStage.DIFF,
                        Optional.empty())),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
    }
}
