package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证runtime是policy、extension与engine对象图的唯一冻结边界。 */
class CompareRuntimeContractTests {

    @Test
    void runtimePublishesOneStableEngineAndItsPolicy() {
        ComparePolicy policy = ComparePolicy.builder().maxDepth(6).build();

        CompareRuntime runtime = CompareRuntime.builder().policy(policy).build();

        assertThat(runtime.policy()).isSameAs(policy);
        assertThat(runtime.engine()).isNotNull().isSameAs(runtime.engine());
    }

    @Test
    void builderCannotMutateExtensionGraphAfterBuild() {
        CompareRuntime.Builder builder = CompareRuntime.builder();
        builder.build();

        assertThatThrownBy(() -> builder.registerStrategy(
                String.class,
                AlgorithmId.of("test:string:v1"),
                new StringStrategy()))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.INVALID_INPUT_SHAPE));
    }

    @Test
    void engineValidatesExplicitOptionsBeforeAnyRootFastPath() {
        CompareRuntime runtime = CompareRuntime.builder().build();
        Object shared = new Object();

        assertThatThrownBy(() -> runtime.engine().compare(shared, shared, null))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.NULL_OPTIONS));

        ComparePolicy tighterPolicy = ComparePolicy.builder().maxDepth(2).build();
        CompareRuntime tighterRuntime = CompareRuntime.builder().policy(tighterPolicy).build();
        CompareOptions expanded = CompareOptions.defaults(ComparePolicy.defaults());

        assertThatThrownBy(() -> tighterRuntime.engine().compare(shared, shared, expanded))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.OPTION_OUT_OF_RANGE));
    }

    @Test
    void disabledPolicyShortCircuitsBeforeIdentityAndNoOptionsUsesRuntimePolicy() {
        ComparePolicy disabled = ComparePolicy.builder().enabled(false).build();
        CompareRuntime runtime = CompareRuntime.builder().policy(disabled).build();
        Object shared = new Object();

        CompareResult explicit = runtime.engine().compare(shared, shared, CompareOptions.defaults(disabled));
        CompareResult inherited = runtime.engine().compare(shared, shared);

        assertThat(explicit.getCompletion()).isEqualTo(CompareCompletion.DISABLED);
        assertThat(inherited.getCompletion()).isEqualTo(CompareCompletion.DISABLED);
        assertThat(explicit.isIdentical()).isFalse();
    }

    @Test
    void engineAndServiceHoldOnlyFinalFrozenGraphReferences() {
        assertThat(CompareEngine.class.getDeclaredFields())
                .filteredOn(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()))
                .noneMatch(field -> field.getType() == StrategyResolver.class);
        assertThat(CompareService.class.getDeclaredFields())
                .filteredOn(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()))
                .noneMatch(field -> Map.class.isAssignableFrom(field.getType())
                        || field.getType() == StrategyResolver.class);
    }

    @Test
    void runtimeBuilderIsTheOnlyPublicGraphConstructionBoundary() {
        assertThat(Arrays.stream(CompareEngine.class.getDeclaredConstructors()))
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers()));
    }

    @Test
    void compiledPathPatternsContributeToEffectiveFingerprint() {
        CompareRuntime left = CompareRuntime.builder()
                .policy(ComparePolicy.builder()
                        .includePathRules(List.of("PROPERTY:left"))
                        .build())
                .build();
        CompareRuntime right = CompareRuntime.builder()
                .policy(ComparePolicy.builder()
                        .includePathRules(List.of("PROPERTY:right"))
                        .build())
                .build();
        Object shared = new Object();

        String leftFingerprint = left.engine().compare(shared, shared).getDiagnostics()
                .effectivePolicyFingerprint().orElseThrow();
        String rightFingerprint = right.engine().compare(shared, shared).getDiagnostics()
                .effectivePolicyFingerprint().orElseThrow();

        assertThat(leftFingerprint).matches("sha256-v1:[0-9a-f]{64}");
        assertThat(rightFingerprint).isNotEqualTo(leftFingerprint);
    }

    @Test
    void pathRuleOrderDoesNotChangeSemanticFingerprint() {
        CompareRuntime first = CompareRuntime.builder()
                .policy(ComparePolicy.builder()
                        .excludePathRules(List.of("PROPERTY:a", "PROPERTY:b"))
                        .build())
                .build();
        CompareRuntime second = CompareRuntime.builder()
                .policy(ComparePolicy.builder()
                        .excludePathRules(List.of("PROPERTY:b", "PROPERTY:a"))
                        .build())
                .build();
        Object shared = new Object();

        assertThat(first.engine().compare(shared, shared).getDiagnostics()
                .effectivePolicyFingerprint())
                .isEqualTo(second.engine().compare(shared, shared).getDiagnostics()
                        .effectivePolicyFingerprint());
    }

    @Test
    void registeredAlgorithmVersionContributesToEffectiveFingerprint() {
        CompareRuntime v1 = CompareRuntime.builder()
                .registerStrategy(String.class, AlgorithmId.of("test:string:v1"), new StringStrategy())
                .build();
        CompareRuntime v2 = CompareRuntime.builder()
                .registerStrategy(String.class, AlgorithmId.of("test:string:v2"), new StringStrategy())
                .build();

        assertThat(v1.engine().compare("same", "same").getDiagnostics()
                .effectivePolicyFingerprint())
                .isNotEqualTo(v2.engine().compare("same", "same").getDiagnostics()
                        .effectivePolicyFingerprint());
    }

    static final class StringStrategy implements CompareStrategy<String> {
        @Override
        public CompareResult compare(String left, String right, CompareOptions options) {
            return left.equals(right)
                    ? CompareResult.identical()
                    : CompareResult.ofTypeDiff(left, Integer.valueOf(1));
        }

        @Override
        public String getName() {
            return "string";
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }
}
