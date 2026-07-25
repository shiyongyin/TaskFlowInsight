package com.syy.tfi.kernel.compare.spring.benchmark;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** allocation 证据的纯内存裁决器，便于对失败路径做确定性单测。 */
final class CompareAllocationGatePolicy {

    private CompareAllocationGatePolicy() {
    }

    static void verify(
            Properties budget,
            Properties evidence,
            List<String> scenarios) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(scenarios, "scenarios");
        String expectedSchema = required(budget, "budget.schema");
        if (!expectedSchema.equals(required(evidence, "gate.schema"))) {
            throw new IllegalStateException("allocation gate schema does not match budget");
        }
        for (String scenario : scenarios) {
            double expectedMaximum = positiveFinite(
                    budget, scenario + ".maxBytesPerOp");
            double reportedMaximum = positiveFinite(
                    evidence, scenario + ".maxBytesPerOp");
            double mean = positiveFinite(
                    evidence, scenario + ".meanBytesPerOp");
            double observedMaximum = positiveFinite(
                    evidence, scenario + ".maxObservedBytesPerOp");
            if (Double.compare(expectedMaximum, reportedMaximum) != 0) {
                throw new IllegalStateException(
                        scenario + " allocation limit differs from checked-in budget");
            }
            if (!"B/op".equals(required(evidence, scenario + ".allocationUnit"))) {
                throw new IllegalStateException(scenario + " allocation unit must be B/op");
            }
            if (mean > observedMaximum) {
                throw new IllegalStateException(
                        scenario + " allocation mean cannot exceed observed maximum");
            }
            if (observedMaximum > expectedMaximum) {
                throw new IllegalStateException(
                        scenario + " allocation regression: observedMax=" + observedMaximum
                                + " B/op, maximum=" + expectedMaximum + " B/op");
            }
        }
    }

    private static double positiveFinite(Properties properties, String key) {
        final double value;
        try {
            value = Double.parseDouble(required(properties, key));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be numeric", exception);
        }
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalStateException(key + " must be finite and positive");
        }
        return value;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing allocation gate property: " + key);
        }
        return value;
    }
}
