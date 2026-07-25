package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.api.ComparatorBuilder;
import com.syy.taskflowinsight.api.TfiListDiff;
import com.syy.taskflowinsight.api.TfiListDiffFacade;
import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证4.0 Policy/Runtime收敛后不再发布旧可变配置与运行期fallback入口。 */
class CompareLegacyApiRemovalContractTests {

    @Test
    void legacyPolicyAndFallbackApiIsRemoved() {
        assertNoFields(CompareOptions.class, "DEFAULT", "DEEP", "WITH_REPORT");
        assertNoMethods(CompareOptions.class,
                "deep", "typeAware", "withPerfBudget", "withReport",
                "getExcludeFields", "getForcedObjectType", "getForcedStrategy", "getIgnoreFields",
                "getParallelThreshold", "getPatchFormat", "getPerf", "getPerfDegradationStrategy",
                "getPerfMaxElements", "getPerfTimeoutMs", "getReportFormat", "getStrategyName",
                "isAttemptAutoMerge", "isCalculateSimilarity", "isDetectMoves", "isEnableDeepCompare",
                "isGeneratePatch", "isGenerateReport", "isIncludeNullChanges", "isPerfStrictMode",
                "isStrictDuplicateKey", "isTrackEntityKeyAttributes", "isTypeAwareEnabled",
                "setAttemptAutoMerge", "setCalculateSimilarity", "setDetectMoves", "setEnableDeepCompare",
                "setExcludeFields", "setForcedObjectType", "setForcedStrategy", "setGeneratePatch",
                "setGenerateReport", "setIgnoreFields", "setIncludeNullChanges", "setMaxDepth",
                "setParallelThreshold", "setPatchFormat", "setPerf", "setPerfDegradationStrategy",
                "setPerfMaxElements", "setPerfStrictMode", "setPerfTimeoutMs", "setReportFormat",
                "setStrategyName", "setStrictDuplicateKey", "setTrackEntityKeyAttributes",
                "setTypeAwareEnabled");
        assertNoMethods(CompareOptions.CompareOptionsBuilder.class,
                "attemptAutoMerge", "calculateSimilarity", "detectMoves", "enableDeepCompare",
                "excludeFields", "forcedObjectType", "forcedStrategy", "generatePatch", "generateReport",
                "ignoreFields", "includeNullChanges", "parallelThreshold", "patchFormat", "perf",
                "perfDegradationStrategy", "perfMaxElements", "perfStrictMode", "perfTimeoutMs",
                "reportFormat", "strategyName", "strictDuplicateKey", "trackEntityKeyAttributes",
                "typeAwareEnabled");
        assertNoMethods(ComparatorBuilder.class,
                "detectMoves", "exclude", "forceObjectType", "forceStrategy", "ignoring", "includeNulls",
                "typeAware", "withParallelThreshold", "withPatch", "withReport", "withStrategyName",
                "withStrictDuplicateKey", "withTrackEntityKeyAttributes");
        assertNoMethods(DiffBuilder.class, "withExcludePatterns", "withPropertyComparator");
        assertNoMethods(TfiListDiff.class, "diff(java.util.List,java.util.List,java.lang.String)",
                "diffEntities(java.util.List,java.util.List,java.lang.String)");
        assertNoMethods(TfiListDiffFacade.class, "diff(java.util.List,java.util.List,java.lang.String)",
                "diffEntities(java.util.List,java.util.List,java.lang.String)");
        assertNoMethods(CompareService.class,
                "createDefault(com.syy.taskflowinsight.tracking.compare.CompareOptions,"
                        + "com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry)",
                "registerNamedStrategy", "registerStrategy");
        assertThat(Arrays.stream(CompareService.class.getConstructors()))
                .noneMatch(constructor -> constructor.getParameterCount() == 6
                        && constructor.getParameterTypes()[0] == ListCompareExecutor.class);
        assertNoMethods(ComparisonProvider.class, "similarity");
        assertNoMethods(DefaultComparisonProvider.class, "similarity");
    }

    @Test
    void stringParsedEntityPathApiIsRemoved() {
        assertNoMethods(EntityListStrategy.class,
                "extractPureEntityKey(java.lang.String)",
                "extractDuplicateIndex(java.lang.String)");
    }

    @Test
    void retiredKernelSelectionTypesAreAbsent() {
        // 已删除类型无法import；精确FQN字符串同时作为manifest消费者的运行时证据。
        List<String> retiredTypes = List.of(
                "com.syy.taskflowinsight.annotation.CustomComparator",
                "com.syy.taskflowinsight.annotation.DateFormat",
                "com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties",
                "com.syy.taskflowinsight.annotation.IgnoreInheritedProperties",
                "com.syy.taskflowinsight.annotation.NumericPrecision",
                "com.syy.taskflowinsight.annotation.ObjectType",
                "com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy",
                "com.syy.taskflowinsight.registry.DiffRegistry",
                "com.syy.taskflowinsight.registry.ObjectTypeResolver",
                "com.syy.taskflowinsight.registry.ValueObjectStrategyResolver",
                "com.syy.taskflowinsight.tracking.compare.CompareCacheConfig",
                "com.syy.taskflowinsight.tracking.compare.CompareCacheProperties",
                "com.syy.taskflowinsight.tracking.compare.CompareCacheProperties$CacheConfig",
                "com.syy.taskflowinsight.tracking.compare.ComparePerfProperties",
                "com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy",
                "com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy",
                "com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy$CompareMethod",
                "com.syy.taskflowinsight.tracking.compare.PerfOptions",
                "com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry",
                "com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry$MetricsSnapshot",
                "com.syy.taskflowinsight.tracking.detector.ChangeRecordComparator",
                "com.syy.taskflowinsight.tracking.detector.DiffFacade$AppContextInjector",
                "com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache",
                "com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache$MatcherCacheConfig$MatcherCacheConfigBuilder",
                "com.syy.taskflowinsight.tracking.path.PathArbiter",
                "com.syy.taskflowinsight.tracking.path.PathArbiter$AccessType",
                "com.syy.taskflowinsight.tracking.path.PathArbiter$PathCandidate",
                "com.syy.taskflowinsight.tracking.path.PathCache",
                "com.syy.taskflowinsight.tracking.path.PathCache$CacheStatistics",
                "com.syy.taskflowinsight.tracking.path.PathCollector",
                "com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig",
                "com.syy.taskflowinsight.tracking.path.PathDeduplicator",
                "com.syy.taskflowinsight.tracking.path.PathDeduplicator$DeduplicationStatistics",
                "com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface",
                "com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface$CacheStats",
                "com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface$CacheStats$Builder",
                "com.syy.taskflowinsight.tracking.path.PriorityCalculator",
                "com.syy.taskflowinsight.tracking.precision.PrecisionController",
                "com.syy.taskflowinsight.tracking.precision.PrecisionController$PrecisionSettings",
                "com.syy.taskflowinsight.tracking.precision.PrecisionController$PrecisionSettings$Builder",
                "com.syy.taskflowinsight.tracking.precision.PrecisionController$ValidationResult",
                "com.syy.taskflowinsight.tracking.precision.PrecisionController$ValidationResult$Builder",
                "com.syy.taskflowinsight.tracking.precision.PrecisionMetrics",
                "com.syy.taskflowinsight.tracking.precision.PrecisionMetrics$MetricsSnapshot",
                "com.syy.taskflowinsight.tracking.snapshot.FacadeSnapshotProvider",
                "com.syy.taskflowinsight.tracking.snapshot.ShallowReferenceMode",
                "com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders$AppContextInjector",
                "com.syy.taskflowinsight.tracking.snapshot.filter.PathLevelFilterEngine",
                "com.syy.taskflowinsight.tracking.snapshot.filter.PathMatcher",
                "com.syy.taskflowinsight.tracking.snapshot.filter.UnifiedFilterEngine");

        retiredTypes.forEach(typeName -> assertThatThrownBy(() -> Class.forName(typeName))
                .isInstanceOf(ClassNotFoundException.class));
    }

    private static void assertNoFields(Class<?> type, String... removedNames) {
        Set<String> actual = Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
        assertThat(actual).doesNotContain(removedNames);
    }

    private static void assertNoMethods(Class<?> type, String... removedNames) {
        Set<String> actual = Arrays.stream(type.getDeclaredMethods())
                .map(CompareLegacyApiRemovalContractTests::signature)
                .collect(Collectors.toSet());
        for (String removedName : removedNames) {
            if (removedName.contains("(")) {
                assertThat(actual).doesNotContain(removedName);
            } else {
                assertThat(actual).noneMatch(signature -> signature.startsWith(removedName + "("));
            }
        }
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }
}
