package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.ComparePath;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompareResult真值合同，确保执行不完整、失败或禁用不会再被解释为相同。
 */
class CompareResultTruthContractTests {

    @Test
    void resultDoesNotRetainOrExposeComparedRootObjects() {
        Set<String> instanceFields = Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> builderMethods = legacyBuilderMethods();

        assertThat(instanceFields).doesNotContain("object1", "object2");
        assertThat(methods).doesNotContain("getObject1", "getObject2", "setObject1", "setObject2");
        assertThat(builderMethods).doesNotContain("object1", "object2");
    }

    @Test
    void resultDoesNotEmbedReportOrPatchProjectionOutputs() {
        Set<String> instanceFields = Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> builderMethods = legacyBuilderMethods();

        assertThat(instanceFields).doesNotContain("report", "patch");
        assertThat(methods).doesNotContain("getReport", "getPatch", "setReport", "setPatch");
        assertThat(builderMethods).doesNotContain("report", "patch");
    }

    @Test
    void resultUsesOnlyTypedOptionalSimilarity() {
        Set<String> instanceFields = Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> builderMethods = legacyBuilderMethods();

        assertThat(instanceFields).doesNotContain("similarity");
        assertThat(methods).doesNotContain("getSimilarity", "getSimilarityPercent", "setSimilarity");
        assertThat(builderMethods).doesNotContain("similarity");
    }

    @Test
    void resultDoesNotExposeLegacyExecutionMetadata() {
        Set<String> instanceFields = Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> builderMethods = legacyBuilderMethods();

        assertThat(instanceFields)
                .doesNotContain("algorithmUsed", "degradationReasons", "duplicateKeys");
        assertThat(methods).doesNotContain(
                "getAlgorithmUsed", "setAlgorithmUsed",
                "getDegradationReasons", "setDegradationReasons",
                "getDuplicateKeys", "setDuplicateKeys", "hasDuplicateKeys");
        assertThat(builderMethods)
                .doesNotContain("algorithmUsed", "degradationReasons", "duplicateKeys");
    }

    @Test
    void resultKeepsTimingOnlyInDiagnostics() {
        Set<String> instanceFields = Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> builderMethods = legacyBuilderMethods();

        assertThat(instanceFields).doesNotContain("compareTime", "compareTimeMs");
        assertThat(methods).doesNotContain(
                "getCompareTime", "setCompareTime", "getCompareTimeMs", "setCompareTimeMs");
        assertThat(builderMethods).doesNotContain("compareTime", "compareTimeMs");
        assertThat(CompareResult.identical().getDiagnostics().durationNanos()).isZero();
    }

    @Test
    void resultTruthHasNoLegacyBooleanOrAmbiguousChangeQuery() {
        Set<String> instanceFields = Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> builderMethods = legacyBuilderMethods();

        assertThat(instanceFields).doesNotContain("identical");
        assertThat(methods).doesNotContain("setIdentical", "hasChanges");
        assertThat(builderMethods).doesNotContain("identical");
        assertThat(CompareResult.identical()).satisfies(result -> {
            assertThat(result.isIdentical()).isTrue();
            assertThat(result.isDifferent()).isFalse();
            assertThat(result.hasChangeDetails()).isFalse();
        });
    }

    @Test
    void resultHasOneImmutableCanonicalConstructionSurface() {
        Set<String> methods = Arrays.stream(CompareResult.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> nestedTypes = Arrays.stream(CompareResult.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(Arrays.stream(CompareResult.class.getDeclaredConstructors()))
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
        assertThat(Arrays.stream(CompareResult.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(methods).noneMatch(name -> name.startsWith("set"));
        assertThat(methods).doesNotContain("builder");
        assertThat(nestedTypes).doesNotContain("CompareResultBuilder");
    }

    @Test
    void fieldChangeKeepsOnlyImmutableBoundedCanonicalFacts() {
        Set<String> instanceFields = Arrays.stream(FieldChange.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(FieldChange.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> nestedTypes = Arrays.stream(FieldChange.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(instanceFields).containsExactlyInAnyOrder("kind", "before", "after");
        assertThat(Arrays.stream(FieldChange.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(FieldChange.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers())))
                .noneMatch(field -> field.getType().equals(java.time.Clock.class));
        assertThat(Arrays.stream(FieldChange.class.getDeclaredConstructors()))
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
        assertThat(methods).noneMatch(name -> name.startsWith("set"));
        assertThat(methods).doesNotContain("builder", "getOldValue", "getNewValue");
        assertThat(nestedTypes).doesNotContain("FieldChangeBuilder");

        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(
                        ComparePath.root(), ValueSnapshot.ofString("sensitive-before", 16))),
                Optional.of(new ChangeSide(
                        ComparePath.root(), ValueSnapshot.ofString("sensitive-after", 15))));

        assertThat(change.toString())
                .doesNotContain("sensitive-before", "sensitive-after");
    }

    private static Set<String> legacyBuilderMethods() {
        return Arrays.stream(CompareResult.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CompareResultBuilder"))
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
    }

    @Test
    void identicalFactoryMeansEqualAndComplete() {
        CompareResult result = CompareResult.identical();

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.getDiagnostics().rootAlgorithmId())
                .contains(AlgorithmId.of("tfi:identity:v1"));
        assertThat(result.similarity()).contains(
                new SimilarityScore(AlgorithmId.of("tfi:identity:v1"), 1.0));
    }

    @Test
    void problemAndLimitationWireCodesUseDisjointClosedSets() {
        Set<String> problemCodes = Arrays.stream(CompareProblemCode.values())
                .map(CompareProblemCode::wireCode)
                .collect(Collectors.toSet());
        Set<String> limitationCodes = Arrays.stream(CompareLimitationCode.values())
                .map(CompareLimitationCode::wireCode)
                .collect(Collectors.toSet());

        assertThat(problemCodes).containsExactlyInAnyOrder(
                "CMP_E_1101", "CMP_E_1102", "CMP_E_2001", "CMP_E_2002",
                "CMP_E_2003", "CMP_E_3001", "CMP_E_4001", "CMP_E_9001");
        assertThat(limitationCodes).containsExactlyInAnyOrder(
                "CMP_W_2101", "CMP_W_2102", "CMP_W_2103", "CMP_W_2104",
                "CMP_W_2105", "CMP_W_2201", "CMP_W_3101");
        assertThat(problemCodes).doesNotContainAnyElementsOf(limitationCodes);
    }

    @Test
    void issuesCarryOnlyTypedStageAndOptionalCanonicalPath() {
        CompareProblem problem = new CompareProblem(
                CompareProblemCode.SNAPSHOT_FAILED,
                CompareStage.SNAPSHOT,
                Optional.of(ComparePath.root()));
        CompareLimitation limitation = new CompareLimitation(
                CompareLimitationCode.DEPTH_LIMIT_REACHED,
                CompareStage.SNAPSHOT,
                Optional.empty());

        assertThat(problem.path()).contains(ComparePath.root());
        assertThat(limitation.stage()).isEqualTo(CompareStage.SNAPSHOT);
    }

    @Test
    void similarityScoreRequiresFiniteNormalizedValue() {
        SimilarityScore score = new SimilarityScore(AlgorithmId.of("tfi:scalar:v1"), 0.5);

        assertThat(score.value()).isEqualTo(0.5);
        assertThatThrownBy(() -> new SimilarityScore(AlgorithmId.of("tfi:scalar:v1"), Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimilarityScore(AlgorithmId.of("tfi:scalar:v1"), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimilarityScore(AlgorithmId.of("tfi:scalar:v1"), 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void diagnosticsDefensivelyDeduplicatesAppliedAlgorithms() {
        AlgorithmId algorithmId = AlgorithmId.of("tfi:scalar:v1");
        List<AlgorithmId> applied = new ArrayList<>(List.of(algorithmId, algorithmId));
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                0, Optional.of(algorithmId), applied, Optional.empty(),
                0, 0, 0, 0, 0, 0, 0);

        applied.clear();

        assertThat(diagnostics.appliedAlgorithmIds()).containsExactly(algorithmId);
    }

    @Test
    void diagnosticsKeepsFingerprintAndNullAlgorithmValidationStrict() {
        String validFingerprint = "sha256-v1:" + "0".repeat(64);
        List<AlgorithmId> containingNull = new ArrayList<>();
        containingNull.add(null);

        CompareDiagnostics diagnostics = new CompareDiagnostics(
                0, Optional.empty(), List.of(), Optional.of(validFingerprint),
                0, 0, 0, 0, 0, 0, 0);

        assertThat(diagnostics.effectivePolicyFingerprint()).contains(validFingerprint);
        assertThatThrownBy(() -> new CompareDiagnostics(
                0, Optional.empty(), List.of(), Optional.of(validFingerprint.substring(0, 73) + "G"),
                0, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompareDiagnostics(
                0, Optional.empty(), containingNull, Optional.empty(),
                0, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("appliedAlgorithmIds contains null");
    }

    @Test
    void nullnessChangeKeepsPresentNullSidesAtTheSamePath() {
        ChangeSide before = new ChangeSide(ComparePath.root(), ValueSnapshot.exactNull());
        ChangeSide after = new ChangeSide(ComparePath.root(), ValueSnapshot.ofString("value", 5));

        FieldChange change = FieldChange.canonical(
                ChangeKind.NULLNESS, Optional.of(before), Optional.of(after));

        assertThat(change.kind()).isEqualTo(ChangeKind.NULLNESS);
        assertThat(change.before()).contains(before);
        assertThatThrownBy(() -> FieldChange.canonical(
                ChangeKind.NULLNESS, Optional.empty(), Optional.of(after)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledResultCannotBeConstructedWithoutPolicyDisabledLimitation() {
        assertThatThrownBy(() -> CompareResult.canonical(
                CompareOutcome.INDETERMINATE,
                CompareCompletion.DISABLED,
                List.of(),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalCompleteRejectsAnyProblemEvidence() {
        CompareProblem problem = new CompareProblem(
                CompareProblemCode.DIFF_FAILED, CompareStage.DIFF, Optional.empty());

        assertThatThrownBy(() -> CompareResult.canonical(
                CompareOutcome.EQUAL,
                CompareCompletion.COMPLETE,
                List.of(),
                List.of(problem),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedResultRequiresPublishedOrOmittedProblemEvidence() {
        assertThatThrownBy(() -> CompareResult.canonical(
                CompareOutcome.INDETERMINATE,
                CompareCompletion.FAILED,
                List.of(),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialResultRequiresProblemLimitationOrOmittedEvidence() {
        ChangeSide before = new ChangeSide(ComparePath.root(), ValueSnapshot.ofString("a", 1));
        ChangeSide after = new ChangeSide(ComparePath.root(), ValueSnapshot.ofString("b", 1));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY, Optional.of(before), Optional.of(after));

        assertThatThrownBy(() -> CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.PARTIAL,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indeterminateResultRejectsConfirmedChangeFacts() {
        ChangeSide before = new ChangeSide(ComparePath.root(), ValueSnapshot.ofString("a", 1));
        ChangeSide after = new ChangeSide(ComparePath.root(), ValueSnapshot.ofString("b", 1));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY, Optional.of(before), Optional.of(after));
        CompareLimitation limitation = new CompareLimitation(
                CompareLimitationCode.DEPTH_LIMIT_REACHED,
                CompareStage.SNAPSHOT,
                Optional.of(ComparePath.root()));

        assertThatThrownBy(() -> CompareResult.canonical(
                CompareOutcome.INDETERMINATE,
                CompareCompletion.PARTIAL,
                List.of(change),
                List.of(),
                List.of(limitation),
                CompareDiagnostics.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAndTypeMismatchFactoriesPublishTypedRootChanges() {
        CompareResult nullMismatch = CompareResult.ofNullDiff(null, "secret");
        CompareResult typeMismatch = CompareResult.ofTypeDiff("secret", 42);

        assertThat(nullMismatch.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(nullMismatch.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(nullMismatch.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(ChangeKind.NULLNESS);
            assertThat(change.before()).get().extracting(ChangeSide::path).isEqualTo(ComparePath.root());
            assertThat(change.before()).get().extracting(side -> side.value().typeCode()).isEqualTo("null");
            assertThat(change.after()).get().extracting(side -> side.value().typeCode()).isEqualTo("type-metadata");
        });
        assertThat(typeMismatch.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(ChangeKind.TYPE_MISMATCH);
            assertThat(change.before()).get().extracting(side -> side.value().typeCode()).isEqualTo("type-metadata");
            assertThat(change.after()).get().extracting(side -> side.value().typeCode()).isEqualTo("type-metadata");
        });
        assertThat(nullMismatch.getDiagnostics().rootAlgorithmId())
                .contains(AlgorithmId.of("tfi:nullness:v1"));
        assertThat(typeMismatch.getDiagnostics().rootAlgorithmId())
                .contains(AlgorithmId.of("tfi:type-mismatch:v1"));
    }

    @Test
    void strategyFailureReturnsTypedFailedResultInsteadOfFalseEqual() {
        CompareStrategy<String> failingStrategy = new CompareStrategy<>() {
            @Override
            public CompareResult compare(String obj1, String obj2, CompareOptions options) {
                throw new IllegalStateException("sensitive failure detail");
            }

            @Override
            public String getName() {
                return "failing";
            }

            @Override
            public boolean supports(Class<?> type) {
                return type == String.class;
            }
        };
        CompareRuntime runtime = CompareRuntime.builder()
                .registerStrategy(String.class, AlgorithmId.of("test:failing:v1"), failingStrategy)
                .build();

        CompareResult result = runtime.engine().execute("before", "after", CompareOptions.builder().build());

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.FAILED);
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.getProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo(CompareProblemCode.DIFF_FAILED);
            assertThat(problem.stage()).isEqualTo(CompareStage.DIFF);
        });
        assertThat(result.getChanges()).isEmpty();
        assertThat(result.similarity()).isEmpty();
    }

    @Test
    void compareServicePreservesCanonicalIdentityResult() {
        Object same = new Object();

        CompareResult result = new CompareService().compare(same, same, CompareOptions.builder().build());

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.getDiagnostics().rootAlgorithmId())
                .contains(AlgorithmId.of("tfi:identity:v1"));
    }

    @Test
    void mapStrategyPublishesCanonicalEqualAndDifferentOutcomes() {
        MapCompareStrategy strategy = new MapCompareStrategy();

        CompareResult equal = strategy.compare(Map.of("key", 1), Map.of("key", 1), CompareOptions.builder().build());
        CompareResult different = strategy.compare(Map.of("key", 1), Map.of("key", 2), CompareOptions.builder().build());

        assertThat(equal.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
        assertThat(equal.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(different.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(different.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(different.getChanges()).isNotEmpty();
    }
}
