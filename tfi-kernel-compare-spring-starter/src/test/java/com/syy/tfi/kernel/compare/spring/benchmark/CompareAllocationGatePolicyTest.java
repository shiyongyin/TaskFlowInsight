package com.syy.tfi.kernel.compare.spring.benchmark;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** allocation gate 的通过、超限和证据漂移合同。 */
class CompareAllocationGatePolicyTest {

    @Test
    void acceptsEvidenceAtTheCheckedInMaximum() {
        Properties budget = budget(100.0d);
        Properties evidence = evidence(100.0d, 100.0d, "B/op");

        assertThatNoException().isThrownBy(() ->
                CompareAllocationGatePolicy.verify(budget, evidence, List.of("scenario")));
    }

    @Test
    void rejectsAllocationAboveTheMaximum() {
        Properties budget = budget(100.0d);
        Properties evidence = evidence(100.1d, 100.0d, "B/op");

        assertThatThrownBy(() ->
                CompareAllocationGatePolicy.verify(budget, evidence, List.of("scenario")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allocation regression")
                .hasMessageContaining("observedMax=100.1")
                .hasMessageContaining("maximum=100.0");
    }

    @Test
    void rejectsChangedLimitsAndUnits() {
        Properties budget = budget(100.0d);

        assertThatThrownBy(() -> CompareAllocationGatePolicy.verify(
                budget, evidence(90.0d, 101.0d, "B/op"), List.of("scenario")))
                .hasMessageContaining("differs from checked-in budget");
        assertThatThrownBy(() -> CompareAllocationGatePolicy.verify(
                budget, evidence(90.0d, 100.0d, "KB/op"), List.of("scenario")))
                .hasMessageContaining("unit must be B/op");
    }

    @Test
    void rejectsEvidenceWhoseMeanExceedsItsObservedMaximum() {
        Properties budget = budget(100.0d);
        Properties evidence = evidence(90.0d, 100.0d, "B/op");
        evidence.setProperty("scenario.meanBytesPerOp", "90.1");

        assertThatThrownBy(() ->
                CompareAllocationGatePolicy.verify(budget, evidence, List.of("scenario")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mean cannot exceed observed maximum");
    }

    private static Properties budget(double maximum) {
        Properties properties = new Properties();
        properties.setProperty("budget.schema", "TFI_COMPARE_ALLOCATION_BUDGET_V1");
        properties.setProperty("scenario.maxBytesPerOp", Double.toString(maximum));
        return properties;
    }

    private static Properties evidence(double observed, double maximum, String unit) {
        Properties properties = new Properties();
        properties.setProperty("gate.schema", "TFI_COMPARE_ALLOCATION_BUDGET_V1");
        properties.setProperty("scenario.meanBytesPerOp", Double.toString(observed));
        properties.setProperty("scenario.maxObservedBytesPerOp", Double.toString(observed));
        properties.setProperty("scenario.maxBytesPerOp", Double.toString(maximum));
        properties.setProperty("scenario.allocationUnit", unit);
        return properties;
    }
}
