package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AopStaticPlanContractTests {

    /** operation 的合法最大长度 fixture，单位为 ASCII 字符。 */
    private static final String OPERATION_63 =
            "aaaaaaaaaa" + "aaaaaaaaaa" + "aaaaaaaaaa"
                    + "aaaaaaaaaa" + "aaaaaaaaaa" + "aaaaaaaaaa" + "aaa";
    /** target 名的合法最大长度 fixture，单位为 ASCII 字符。 */
    private static final String TARGET_64 =
            "bbbbbbbbbb" + "bbbbbbbbbb" + "bbbbbbbbbb"
                    + "bbbbbbbbbb" + "bbbbbbbbbb" + "bbbbbbbbbb" + "bbbb";
    /** operation 的首个越界长度 fixture，单位为 ASCII 字符。 */
    private static final String OPERATION_64 = OPERATION_63 + "a";
    /** target 名的首个越界长度 fixture，单位为 ASCII 字符。 */
    private static final String TARGET_65 = TARGET_64 + "b";

    private final TfiTrackedMethodPlanResolver resolver = new TfiTrackedMethodPlanResolver();

    @Test
    void publicAnnotationsExposeOnlyTheSpecifiedRuntimeTargets() {
        Target trackedTarget = TfiTracked.class.getAnnotation(Target.class);
        Retention trackedRetention = TfiTracked.class.getAnnotation(Retention.class);
        Target targetTarget = TfiTrackTarget.class.getAnnotation(Target.class);
        Retention targetRetention = TfiTrackTarget.class.getAnnotation(Retention.class);

        assertThat(trackedTarget.value()).containsExactly(ElementType.METHOD);
        assertThat(trackedRetention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetTarget.value()).containsExactly(ElementType.PARAMETER);
        assertThat(targetRetention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(TfiTracked.class.getDeclaredMethods()).extracting(Method::getName)
                .containsExactly("operation");
        assertThat(TfiTrackTarget.class.getDeclaredMethods()).extracting(Method::getName)
                .containsExactly("value");
    }

    @Test
    void exactOperationAndTargetBoundariesProduceAnImmutableOrderedPlan() throws Exception {
        Method method = BoundaryFixtures.class.getDeclaredMethod(
                "atBoundary", Object.class, Object.class);

        TfiTrackedMethodPlan plan = resolver.resolve(method, BoundaryFixtures.class).orElseThrow();

        assertThat(plan.methodOperation()).hasSize(63).isEqualTo(OPERATION_63);
        assertThat(plan.targets()).containsExactly(
                new TfiTrackedMethodPlan.TargetSlot(0, TARGET_64),
                new TfiTrackedMethodPlan.TargetSlot(1, "second"));
        assertThatThrownBy(() -> plan.targets().add(
                new TfiTrackedMethodPlan.TargetSlot(2, "third")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anyStaticBoundaryOrGrammarViolationFailsWithoutEchoingValues() throws Exception {
        assertInvalid("operationTooLong", OPERATION_64, null);
        assertInvalid("targetTooLong", TARGET_65, 0);
        assertInvalid("invalidOperationGrammar", "Secret.Operation", null);
        assertInvalid("invalidTargetGrammar", "secret.target", 0);
        assertInvalid("missingTarget", null, null);
        assertInvalid("targetWithoutMethod", "orphan", 0);
        assertInvalid("duplicateTarget", "same_secret_value", 1);
    }

    @Test
    void unannotatedMethodProducesNoPlan() throws Exception {
        Method method = BoundaryFixtures.class.getDeclaredMethod("untracked", Object.class);

        Optional<TfiTrackedMethodPlan> plan = resolver.resolve(method, BoundaryFixtures.class);

        assertThat(plan).isEmpty();
    }

    @Test
    void repeatedResolutionReusesTheTargetClassPlan() throws Exception {
        Method method = BoundaryFixtures.class.getDeclaredMethod(
                "atBoundary", Object.class, Object.class);

        TfiTrackedMethodPlan first = resolver.resolve(method, BoundaryFixtures.class).orElseThrow();
        TfiTrackedMethodPlan second = resolver.resolve(method, BoundaryFixtures.class).orElseThrow();

        assertThat(second).isSameAs(first);
    }

    private void assertInvalid(String methodName, String forbiddenValue, Integer parameterIndex)
            throws Exception {
        Method method = switch (methodName) {
            case "duplicateTarget" -> BoundaryFixtures.class.getDeclaredMethod(
                    methodName, Object.class, Object.class);
            default -> BoundaryFixtures.class.getDeclaredMethod(methodName, Object.class);
        };

        var assertion = assertThatThrownBy(() -> resolver.resolve(method, BoundaryFixtures.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KCS_E_1102")
                .hasMessageContaining(BoundaryFixtures.class.getName())
                .hasMessageContaining(methodName);
        if (parameterIndex != null) {
            assertion.hasMessageContaining("parameterIndex=" + parameterIndex);
        }
        if (forbiddenValue != null) {
            assertion.hasMessageNotContaining(forbiddenValue);
        }
    }

    private static final class BoundaryFixtures {

        @TfiTracked(operation = OPERATION_63)
        public Object atBoundary(
                @TfiTrackTarget(TARGET_64) Object first,
                @TfiTrackTarget("second") Object second) {
            return first;
        }

        @TfiTracked(operation = OPERATION_64)
        public Object operationTooLong(@TfiTrackTarget("target") Object target) {
            return target;
        }

        @TfiTracked(operation = "valid")
        public Object targetTooLong(@TfiTrackTarget(TARGET_65) Object target) {
            return target;
        }

        @TfiTracked(operation = "Secret.Operation")
        public Object invalidOperationGrammar(@TfiTrackTarget("target") Object target) {
            return target;
        }

        @TfiTracked(operation = "valid")
        public Object invalidTargetGrammar(@TfiTrackTarget("secret.target") Object target) {
            return target;
        }

        @TfiTracked(operation = "missing.target")
        public Object missingTarget(Object target) {
            return target;
        }

        public Object targetWithoutMethod(@TfiTrackTarget("orphan") Object target) {
            return target;
        }

        @TfiTracked(operation = "duplicate.target")
        public Object duplicateTarget(
                @TfiTrackTarget("same_secret_value") Object first,
                @TfiTrackTarget("same_secret_value") Object second) {
            return first;
        }

        public Object untracked(Object target) {
            return target;
        }
    }
}
