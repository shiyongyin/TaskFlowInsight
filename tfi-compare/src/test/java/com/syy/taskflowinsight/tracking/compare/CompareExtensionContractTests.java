package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证runtime extension使用exact selector与全局唯一versioned identity。 */
class CompareExtensionContractTests {

    @Test
    void selectorResolvesOnlyFieldDeclaredByExactClass() {
        PropertySelector selector = PropertySelector.of(ChildValue.class, "value");

        assertThat(selector.declaringClass()).isEqualTo(ChildValue.class);
        assertThat(selector.fieldName()).isEqualTo("value");
        assertThat(selector.fieldType()).isEqualTo(String.class);

        assertThatThrownBy(() -> PropertySelector.of(ChildValue.class, "inherited"))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.INVALID_SELECTOR));
    }

    @Test
    void algorithmIdIsUniqueAcrossStrategyAndPropertyComparator() {
        AlgorithmId duplicated = AlgorithmId.of("test:duplicate:v1");

        CompareRuntime.Builder builder = CompareRuntime.builder()
                .registerStrategy(ChildValue.class, duplicated, new ChildStrategy())
                .registerComparator(
                        PropertySelector.of(ChildValue.class, "value"),
                        duplicated,
                        (left, right, field) -> true);

        assertBuildViolation(builder, InputViolation.DUPLICATE_EXTENSION);
    }

    @Test
    void duplicateTargetClassAndSelectorAreRejectedAtFreezePoint() {
        CompareRuntime.Builder duplicateTarget = CompareRuntime.builder()
                .registerStrategy(ChildValue.class, AlgorithmId.of("test:first:v1"), new ChildStrategy())
                .registerStrategy(ChildValue.class, AlgorithmId.of("test:second:v1"), new ChildStrategy());
        assertBuildViolation(duplicateTarget, InputViolation.DUPLICATE_EXTENSION);

        PropertySelector selector = PropertySelector.of(ChildValue.class, "value");
        CompareRuntime.Builder duplicateSelector = CompareRuntime.builder()
                .registerComparator(selector, AlgorithmId.of("test:field-one:v1"), (a, b, f) -> true)
                .registerComparator(selector, AlgorithmId.of("test:field-two:v1"), (a, b, f) -> false);
        assertBuildViolation(duplicateSelector, InputViolation.DUPLICATE_EXTENSION);
    }

    @Test
    void registeredPropertyComparatorParticipatesInComparison() {
        PropertySelector selector = PropertySelector.of(ChildValue.class, "value");
        CompareRuntime equivalentRuntime = CompareRuntime.builder()
                .registerComparator(selector, AlgorithmId.of("test:case-fold:v1"),
                        (left, right, field) -> String.valueOf(left).equalsIgnoreCase(String.valueOf(right)))
                .build();

        CompareResult equivalent = equivalentRuntime.engine().compare(
                new ChildValue("VALUE"),
                new ChildValue("value"));

        assertThat(equivalent.isIdentical()).isTrue();

        CompareRuntime differentRuntime = CompareRuntime.builder()
                .registerComparator(selector, AlgorithmId.of("test:force-different:v1"),
                        (left, right, field) -> false)
                .build();

        CompareResult different = differentRuntime.engine().compare(
                new ChildValue("same"),
                new ChildValue("same"));

        assertThat(different.isIdentical()).isFalse();
        assertThat(different.getChanges()).hasSize(1);
    }

    private static void assertBuildViolation(CompareRuntime.Builder builder, InputViolation violation) {
        assertThatThrownBy(builder::build)
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(violation));
    }

    static class ParentValue {
        String inherited;
    }

    static final class ChildValue extends ParentValue {
        String value;

        ChildValue() {
        }

        ChildValue(String value) {
            this.value = value;
        }
    }

    static final class ChildStrategy implements CompareStrategy<ChildValue> {
        @Override
        public CompareResult compare(ChildValue left, ChildValue right, CompareOptions options) {
            return CompareResult.identical();
        }

        @Override
        public String getName() {
            return "child";
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == ChildValue.class;
        }
    }
}
