package com.syy.taskflowinsight.tracking.compare.internal;

import org.junit.jupiter.api.Test;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.determinism.StableSorter;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.detector.DiffDetectorService;
import com.syy.taskflowinsight.tracking.detector.DiffFacade;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PathBuilder;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 锁定request-local differ的唯一内部owner，防止再次暴露可选择的diff图。 */
class CompareDiffOwnerArchitectureTests {

    @Test
    void compareDifferIsFinalAndPackagePrivate() throws ClassNotFoundException {
        Class<?> differ = Class.forName(
                RequestLocalCompareKernel.class.getPackageName() + ".CompareDiffer");
        int modifiers = differ.getModifiers();

        assertThat(Modifier.isFinal(modifiers)).isTrue();
        assertThat(Modifier.isPublic(modifiers)).isFalse();
        assertThat(Modifier.isProtected(modifiers)).isFalse();
        assertThat(Modifier.isPrivate(modifiers)).isFalse();
    }

    @Test
    void typedSnapshotDiffIsDeclaredOnlyByCompareDiffer() throws ClassNotFoundException {
        Class<?> differ = Class.forName(
                RequestLocalCompareKernel.class.getPackageName() + ".CompareDiffer");

        assertThat(Arrays.stream(differ.getDeclaredMethods()).map(method -> method.getName()))
                .contains("diff");
        assertThat(Arrays.stream(RequestLocalCompareKernel.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("diff", "appendChange");
    }

    @Test
    void injectableRuntimePathMatcherCachesAreAbsent() {
        String pathPackage = ComparePath.class.getPackageName();

        assertThatThrownBy(() -> Class.forName(pathPackage + ".PathMatcherCacheInterface"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(pathPackage + ".CaffeinePathMatcherCache"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(Arrays.stream(PathBuilder.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("ESCAPE_CACHE");
        assertThat(Arrays.stream(PathBuilder.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("clearCache", "getCacheSize");
    }

    @Test
    void resultSortingUsesTypedPathsWithoutASecondCache() {
        assertThat(StableSorter.class.getDeclaredFields()).isEmpty();
        assertThat(Arrays.stream(StableSorter.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("parseCached", "clearCache");

        FieldChange later = changeAtMapKey("z");
        FieldChange earlier = changeAtMapKey("a");
        assertThat(StableSorter.sortByFieldChange(List.of(later, earlier)))
                .containsExactly(earlier, later);
    }

    @Test
    void legacySemanticDedupIsNotSelectable() {
        assertThat(Arrays.stream(DiffDetector.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()))
                .doesNotContain("PathDeduplicator");
        assertThat(Arrays.stream(DiffDetector.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain(
                        "setEnhancedDeduplicationEnabled",
                        "isEnhancedDeduplicationEnabled",
                        "getDeduplicationStatistics",
                        "applyDedupIfNeeded",
                        "deduplicateByPathEnhanced",
                        "deduplicateByPath");
    }

    @Test
    void legacySemanticDedupGraphIsAbsent() {
        String pathPackage = ComparePath.class.getPackageName();

        for (String simpleName : List.of(
                "PathCache",
                "PathDeduplicator",
                "PathDeduplicationConfig",
                "PathArbiter",
                "PathCollector",
                "PriorityCalculator")) {
            assertThatThrownBy(() -> Class.forName(pathPackage + "." + simpleName))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void legacyStringPathMatchersAreAbsent() {
        String filterPackage = "com.syy.taskflowinsight.tracking.snapshot.filter";

        for (String simpleName : List.of(
                "PathMatcher",
                "PathLevelFilterEngine",
                "UnifiedFilterEngine")) {
            assertThatThrownBy(() -> Class.forName(filterPackage + "." + simpleName))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void compareEngineHasNoLegacySnapshotOrReferenceFallbackGraph() {
        assertThat(Arrays.stream(CompareEngine.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain(
                        "captureSnapshotInternal",
                        "detectShallowReferenceChanges",
                        "collectShallowReferenceChanges",
                        "detectEntityReferenceDetail",
                        "compareRegisteredProperties",
                        "isSuppressedByComparator",
                        "appendComparatorOnlyDifferences");
    }

    @Test
    void compatibilityFacadesHaveNoRuntimeSelectionState() {
        assertThat(DiffFacade.class.getDeclaredFields()).isEmpty();
        assertThat(DiffFacade.class.getDeclaredClasses()).isEmpty();
        assertThat(Arrays.stream(DiffFacade.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()))
                .containsExactly("diff");
    }

    @Test
    void legacyDetectorsAreStatelessResultAdapters() {
        assertThat(DiffDetector.class.getDeclaredFields()).isEmpty();
        assertThat(Arrays.stream(DiffDetector.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain(
                        "setPrecisionCompareEnabled",
                        "setPrecisionController",
                        "setDateTimeFormatter",
                        "setCurrentObjectClass",
                        "setCompatHeavyOptimizationsEnabled",
                        "isCompatHeavyOptimizationsEnabled",
                        "isPrecisionCompareEnabled",
                        "detectChangeTypeWithPrecision");

        assertThat(DiffDetectorService.class.getDeclaredFields()).isEmpty();
        assertThat(Arrays.stream(DiffDetectorService.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()))
                .containsExactly("diff");

        String detectorPackage = DiffDetector.class.getPackageName();
        for (String simpleName : List.of("ContainerValueEquality", "ChangeRecordComparator")) {
            assertThatThrownBy(() -> Class.forName(detectorPackage + "." + simpleName))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void descriptorAnnotationSetIsClosed() {
        String annotationPackage = ValueObject.class.getPackageName();
        for (String simpleName : List.of(
                "CustomComparator",
                "DateFormat",
                "NumericPrecision",
                "IgnoreDeclaredProperties",
                "IgnoreInheritedProperties",
                "ObjectType",
                "ValueObjectCompareStrategy")) {
            assertThatThrownBy(() -> Class.forName(annotationPackage + "." + simpleName))
                    .isInstanceOf(ClassNotFoundException.class);
        }
        assertThat(ValueObject.class.getDeclaredMethods()).isEmpty();
    }

    @Test
    void legacyToleranceOwnersAreAbsent() {
        String comparePackage = CompareEngine.class.getPackageName();
        for (String simpleName : List.of("NumericCompareStrategy", "EnhancedDateCompareStrategy")) {
            assertThatThrownBy(() -> Class.forName(comparePackage + "." + simpleName))
                    .isInstanceOf(ClassNotFoundException.class);
        }
        assertThatThrownBy(() -> Class.forName("com.syy.taskflowinsight.tracking.precision.PrecisionMetrics"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static FieldChange changeAtMapKey(String key) {
        ComparePath path = ComparePath.root().append(
                new MapKeySegment(ValueSnapshot.ofString(key, 64)));
        return FieldChange.at(ChangeKind.MODIFY, path, 1, 2);
    }
}
