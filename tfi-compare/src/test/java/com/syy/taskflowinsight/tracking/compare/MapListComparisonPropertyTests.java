package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Map/List 的确定性与 no-false-equal 性质测试，覆盖单个示例测试难以证明的输入顺序变化。
 */
class MapListComparisonPropertyTests {

    private final CompareRuntime runtime = CompareRuntime.builder().build();

    @Test
    void mapFactsAreStableAcrossInsertionOrdersAndCollidingStringHashes() {
        List<Object> beforeKeys = Arrays.asList("Aa", "BB", 10, null);
        List<Object> afterKeys = Arrays.asList("new", "BB", 10, null);
        List<ChangeFact> expected = null;

        for (int seed = 0; seed < 40; seed++) {
            List<Object> beforeOrder = new ArrayList<>(beforeKeys);
            List<Object> afterOrder = new ArrayList<>(afterKeys);
            Collections.shuffle(beforeOrder, new Random(seed));
            Collections.shuffle(afterOrder, new Random(seed * 31L + 7));

            CompareResult result = runtime.engine().compare(
                    mapFor(beforeOrder, true), mapFor(afterOrder, false));
            List<ChangeFact> actual = facts(result);
            if (expected == null) {
                expected = actual;
            }

            assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Test
    void largeOrdinaryListStillReportsTailDifferenceByIndex() {
        List<Integer> before = IntStream.range(0, 400)
                .boxed()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<Integer> after = new ArrayList<>(before);
        after.set(after.size() - 1, -1);
        List<ChangeFact> expected = null;

        for (int run = 0; run < 20; run++) {
            CompareResult result = runtime.engine().compare(before, after);
            List<ChangeFact> actual = facts(result);
            if (expected == null) {
                expected = actual;
            }

            assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
            assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
            assertThat(actual).isEqualTo(expected).hasSize(1);
        }
    }

    @Test
    void equalSummaryRepresentationCannotProveLongListElementsEqual() {
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxResultValueChars(64)
                .build();
        String sharedPrefix = "x".repeat(100);

        CompareResult result = runtime.engine().compare(
                List.of(sharedPrefix + "before"),
                List.of(sharedPrefix + "change"),
                options);

        assertThat(result.getOutcome()).isNotEqualTo(CompareOutcome.EQUAL);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
    }

    @Test
    void elementBudgetStopsBeforeUnobservedTailAndPublishesPartial() {
        CompareOptions options = CompareOptions.builder(runtime.policy())
                .maxElements(3)
                .build();

        CompareResult result = runtime.engine().compare(
                List.of("same", "before"),
                List.of("same", "after"),
                options);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getDiagnostics().consumedElements()).isEqualTo(3);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.COLLECTION_LIMIT_REACHED);
    }

    @Test
    void lossyCollectionRoutingSurfaceIsRemoved() {
        // 已删除类型无法import；类名字符串用于证明运行时产物中不存在兼容壳。
        List<String> removedTypes = List.of(
                "com.syy.taskflowinsight.tracking.algo.edit.LevenshteinEditDistance",
                "com.syy.taskflowinsight.tracking.algo.edit.LevenshteinEditDistance$Options",
                "com.syy.taskflowinsight.tracking.algo.seq.LongestCommonSubsequence",
                "com.syy.taskflowinsight.tracking.algo.seq.LongestCommonSubsequence$Options",
                "com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy",
                "com.syy.taskflowinsight.tracking.compare.list.CompareRoutingProperties",
                "com.syy.taskflowinsight.tracking.compare.list.CompareRoutingProperties$EntityConfig",
                "com.syy.taskflowinsight.tracking.compare.list.CompareRoutingProperties$LcsConfig",
                "com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy",
                "com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy",
                "com.syy.taskflowinsight.tracking.perf.PerfGuard",
                "com.syy.taskflowinsight.tracking.perf.PerfGuard$PerfDecision",
                "com.syy.taskflowinsight.tracking.perf.PerfGuard$PerfOptions",
                "com.syy.taskflowinsight.tracking.perf.PerfGuardConfig",
                "com.syy.taskflowinsight.tracking.compare.CompareCacheConfig",
                "com.syy.taskflowinsight.tracking.compare.CompareCacheProperties",
                "com.syy.taskflowinsight.tracking.compare.ComparePerfProperties",
                "com.syy.taskflowinsight.tracking.compare.PerfOptions",
                "com.syy.taskflowinsight.tracking.rename.RenameHeuristics",
                "com.syy.taskflowinsight.tracking.rename.RenameHeuristics$Options",
                "com.syy.taskflowinsight.tracking.compare.PerfOptions$AlgoConfig",
                "com.syy.taskflowinsight.tracking.compare.PerfOptions$AlgoConfig$EditDistanceConfig",
                "com.syy.taskflowinsight.tracking.compare.PerfOptions$AlgoConfig$LcsConfig",
                "com.syy.taskflowinsight.tracking.compare.PerfOptions$AlgoConfig$RenameConfig",
                "com.syy.taskflowinsight.tracking.monitoring.DegradationConfig",
                "com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine");
        removedTypes.forEach(typeName ->
                assertThatThrownBy(() -> Class.forName(typeName))
                        .isInstanceOf(ClassNotFoundException.class));

        assertThat(publicFieldNames(CompareConstants.class)).doesNotContain(
                "AS_SET_MAX_RECOMMENDED_SIZE",
                "DEGRADATION_REASON_BUSINESS_HINT", "DEGRADATION_REASON_FORCE_SIMPLE",
                "DEGRADATION_REASON_K_PAIRS_EXCEEDED", "DEGRADATION_REASON_MAP_CANDIDATES",
                "DEGRADATION_REASON_SIZE_EXCEEDED",
                "LCS_MAX_RECOMMENDED_SIZE", "LEVENSHTEIN_MAX_RECOMMENDED_SIZE",
                "LIST_SIZE_DEGRADATION_THRESHOLD", "LIST_SIZE_FORCE_SIMPLE_THRESHOLD",
                "STRATEGY_AS_SET", "STRATEGY_LCS", "STRATEGY_LEVENSHTEIN",
                "MAP_KEY_RENAME_SIMILARITY_THRESHOLD", "MAP_CANDIDATE_PAIRS_DEGRADATION_THRESHOLD");
        assertThat(publicFieldNames(ConfigDefaults.class)).doesNotContain(
                "LIST_SIZE_THRESHOLD", "K_PAIRS_THRESHOLD");
        assertThat(publicFieldNames(ConfigDefaults.Keys.class)).doesNotContain(
                "LIST_SIZE_THRESHOLD", "K_PAIRS_THRESHOLD");
    }

    @Test
    void lossyCollectionRoutingConfigurationIsNotPublished() throws Exception {
        Path root = repositoryRoot();
        Path compareMetadata = root.resolve(
                "tfi-compare/src/main/resources/META-INF/additional-spring-configuration-metadata.json");
        Path starterMetadata = root.resolve(
                "tfi-compare-spring-starter/src/main/resources/META-INF/"
                        + "additional-spring-configuration-metadata.json");

        assertThat(compareMetadata)
                .as("纯Compare产物不再拥有Spring配置元数据")
                .doesNotExist();
        assertThat(Files.readString(starterMetadata)).doesNotContain(
                "tfi.compare.auto-route",
                "tfi.diff.perf",
                "tfi.perf-guard",
                "list-size-threshold",
                "k-pairs-threshold",
                "max-candidates");
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("tfi-compare/pom.xml"))) {
            candidate = candidate.getParent();
        }
        assertThat(candidate).as("reactor root must be discoverable").isNotNull();
        return candidate;
    }

    private static Map<Object, Object> mapFor(List<Object> keyOrder, boolean before) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (Object key : keyOrder) {
            if (key == null) {
                map.put(null, "stable-null");
            } else if (key.equals("Aa")) {
                map.put(key, null);
            } else if (key.equals("BB")) {
                map.put(key, before ? "before" : "after");
            } else if (key.equals(10)) {
                map.put(key, before ? List.of("x", "before") : List.of("x", "after"));
            } else {
                map.put(key, "added");
            }
        }
        return map;
    }

    private static List<ChangeFact> facts(CompareResult result) {
        return result.getChanges().stream()
                .map(change -> new ChangeFact(change.kind(), change.before(), change.after()))
                .toList();
    }

    private static List<String> publicFieldNames(Class<?> type) {
        return Arrays.stream(type.getFields()).map(Field::getName).toList();
    }

    /** Canonical change facts excluding execution diagnostics such as elapsed time. */
    private record ChangeFact(
            ChangeKind kind,
            Optional<ChangeSide> before,
            Optional<ChangeSide> after) {
    }
}
