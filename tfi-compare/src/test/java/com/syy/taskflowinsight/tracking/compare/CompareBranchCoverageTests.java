package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.Period;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Branch coverage tests for tracking/compare package.
 * Targets every if/else, switch, null check, and loop body in CompareEngine,
 * CompareService, CompareReportGenerator, StrategyResolver, and related types.
 *
 * @since 3.0.0
 */
@DisplayName("Compare Branch Coverage Tests")
class CompareBranchCoverageTests {

    private CompareService service;

    @BeforeEach
    void setUp() {
        service = new CompareService();
    }

    // ── CompareEngine paths via CompareService ──

    @Nested
    @DisplayName("CompareEngine — quick paths")
    class CompareEngineQuickPaths {

        @Test
        @DisplayName("same reference returns identical")
        void sameReference() {
            Map<String, Object> m = Map.of("a", 1);
            CompareResult r = service.compare(m, m);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null first returns null diff")
        void nullFirst() {
            CompareResult r = service.compare(null, Map.of("a", 1));
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("null second returns null diff")
        void nullSecond() {
            CompareResult r = service.compare(Map.of("a", 1), null);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("both null returns null diff")
        void bothNull() {
            CompareResult r = service.compare(null, null);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("type mismatch returns type diff")
        void typeMismatch() {
            CompareResult r = service.compare("a", 1);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }
    }

    @Nested
    @DisplayName("CompareEngine — List routing")
    class CompareEngineListRouting {

        @Test
        @DisplayName("List comparison triggers ListCompareExecutor")
        void listCompare() {
            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "c");
            CompareResult r = service.compare(a, b);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("empty lists identical")
        void emptyLists() {
            List<String> a = Collections.emptyList();
            List<String> b = Collections.emptyList();
            CompareResult r = service.compare(a, b);
            assertThat(r.isIdentical()).isTrue();
        }
    }

    @Nested
    @DisplayName("CompareEngine — deep fallback")
    class CompareEngineDeepFallback {

        @Test
        @DisplayName("POJO with no strategy uses deep fallback")
        void pojoDeepFallback() {
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = service.compare(new SimplePojo("a"), new SimplePojo("b"), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with excludeFields")
        void deepFallbackExcludeFields() {
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .excludeFields(List.of("ignored"))
                .build();
            CompareResult r = service.compare(
                Map.of("x", 1, "ignored", 99),
                Map.of("x", 2, "ignored", 99),
                opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with forcedObjectType")
        void deepFallbackForcedType() {
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .typeAwareEnabled(true)
                .forcedObjectType(com.syy.taskflowinsight.annotation.ObjectType.ENTITY)
                .build();
            CompareResult r = service.compare(Map.of("a", 1), Map.of("a", 2), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with forcedStrategy")
        void deepFallbackForcedStrategy() {
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .forcedStrategy(com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy.FIELDS)
                .build();
            CompareResult r = service.compare(Map.of("a", 1), Map.of("a", 2), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with includeNullChanges")
        void deepFallbackIncludeNullChanges() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("a", null);
            Map<String, Object> m2 = new HashMap<>();
            m2.put("a", null);
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .includeNullChanges(true)
                .build();
            CompareResult r = service.compare(m1, m2, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with ignoreFields")
        void deepFallbackIgnoreFields() {
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .ignoreFields(List.of("skip"))
                .build();
            CompareResult r = service.compare(
                Map.of("x", 1, "skip", 99),
                Map.of("x", 2, "skip", 1),
                opts);
            assertThat(r).isNotNull();
        }
    }

    @Nested
    @DisplayName("CompareService — compareBatch branches")
    class CompareBatchBranches {

        @Test
        @DisplayName("compareBatch below threshold serial")
        void compareBatchSerial() {
            List<Pair<Object, Object>> pairs = List.of(
                Pair.of(Map.of("a", 1), Map.of("a", 2)),
                Pair.of(Map.of("b", 1), Map.of("b", 2)));
            CompareOptions opts = CompareOptions.builder().parallelThreshold(10).build();
            List<CompareResult> results = service.compareBatch(pairs, opts);
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("compareBatch above threshold parallel")
        void compareBatchParallel() {
            List<Pair<Object, Object>> pairs = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                pairs.add(Pair.of(Map.of("k", i), Map.of("k", i + 1)));
            }
            CompareOptions opts = CompareOptions.builder().parallelThreshold(10).build();
            List<CompareResult> results = service.compareBatch(pairs, opts);
            assertThat(results).hasSize(15);
        }
    }

    @Nested
    @DisplayName("CompareService — compareThreeWay branches")
    class CompareThreeWayBranches {

        @Test
        @DisplayName("compareThreeWay with attemptAutoMerge and no conflicts")
        void threeWayAutoMergeNoConflicts() {
            Map<String, Object> base = Map.of("a", 1, "b", 2);
            Map<String, Object> left = Map.of("a", 2, "b", 2);
            Map<String, Object> right = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().attemptAutoMerge(true).build();
            MergeResult mr = service.compareThreeWay(base, left, right, opts);
            assertThat(mr).isNotNull();
            assertThat(mr.getConflicts()).isEmpty();
        }

        @Test
        @DisplayName("compareThreeWay with conflicts")
        void threeWayConflicts() {
            Map<String, Object> base = Map.of("x", 1);
            Map<String, Object> left = Map.of("x", 2);
            Map<String, Object> right = Map.of("x", 3);
            MergeResult mr = service.compareThreeWay(base, left, right);
            assertThat(mr.getConflicts()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("CompareReportGenerator — branches")
    class CompareReportGeneratorBranches {

        @Test
        @DisplayName("generateReport empty changes")
        void generateReportEmpty() {
            String r = CompareReportGenerator.generateReport(
                Collections.emptyList(),
                CompareOptions.builder().reportFormat(ReportFormat.TEXT).build());
            assertThat(r).isEqualTo("No changes detected.");
        }

        @Test
        @DisplayName("generateReport MARKDOWN format")
        void generateReportMarkdown() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.MARKDOWN).build());
            assertThat(r).contains("# Change Report").contains("| Field |");
        }

        @Test
        @DisplayName("generateReport JSON format")
        void generateReportJson() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.JSON).build());
            assertThat(r).contains("\"changes\"");
        }

        @Test
        @DisplayName("generateReport HTML format")
        void generateReportHtml() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.HTML).build());
            assertThat(r).contains("<div").contains("<table");
        }

        @Test
        @DisplayName("generateReport default TEXT")
        void generateReportDefault() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.XML).build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("generatePatch JSON_PATCH")
        void generatePatchJsonPatch() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("p").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String p = CompareReportGenerator.generatePatch(changes,
                CompareOptions.builder().patchFormat(PatchFormat.JSON_PATCH).build());
            assertThat(p).contains("\"op\"");
        }

        @Test
        @DisplayName("generatePatch MERGE_PATCH")
        void generatePatchMergePatch() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("m").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build());
            String p = CompareReportGenerator.generatePatch(changes,
                CompareOptions.builder().patchFormat(PatchFormat.MERGE_PATCH).build());
            assertThat(p).isNotNull();
        }

        @Test
        @DisplayName("toJsonValue null")
        void toJsonValueNull() {
            assertThat(CompareReportGenerator.toJsonValue(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("toJsonValue Number")
        void toJsonValueNumber() {
            assertThat(CompareReportGenerator.toJsonValue(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("toJsonValue Boolean")
        void toJsonValueBoolean() {
            assertThat(CompareReportGenerator.toJsonValue(true)).isEqualTo("true");
        }

        @Test
        @DisplayName("toJsonValue String with escape")
        void toJsonValueStringEscape() {
            String r = CompareReportGenerator.toJsonValue("a\"b\\c");
            assertThat(r).contains("\\\"").contains("\\\\");
        }

        @Test
        @DisplayName("toJsonObject Map")
        void toJsonObjectMap() {
            Map<String, Object> m = Map.of("k", "v");
            String r = CompareReportGenerator.toJsonObject(m);
            assertThat(r).contains("\"k\"");
        }
    }

    @Nested
    @DisplayName("StrategyResolver — branches")
    class StrategyResolverBranches {

        @Test
        @DisplayName("resolve null strategies returns null")
        void resolveNullStrategies() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(null, String.class)).isNull();
        }

        @Test
        @DisplayName("resolve empty strategies returns null")
        void resolveEmptyStrategies() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(Collections.emptyList(), String.class)).isNull();
        }

        @Test
        @DisplayName("resolve null targetType returns null")
        void resolveNullTargetType() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(List.of(new SetCompareStrategy()), null)).isNull();
        }

        @Test
        @DisplayName("resolve Set")
        void resolveSet() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new SetCompareStrategy(), new CollectionCompareStrategy()),
                HashSet.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("resolve Map")
        void resolveMap() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new MapCompareStrategy(), new CollectionCompareStrategy()),
                HashMap.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("resolve Date")
        @SuppressWarnings("deprecation")
        void resolveDate() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new DateCompareStrategy()),
                Date.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("clearCache")
        void clearCache() {
            StrategyResolver resolver = new StrategyResolver();
            resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            resolver.clearCache();
            assertThat(resolver.getCacheSize()).isZero();
        }
    }

    @Nested
    @DisplayName("CompareOptions — branches")
    class CompareOptionsBranches {

        @Test
        @DisplayName("deep(int)")
        void deep() {
            CompareOptions opts = CompareOptions.deep(5);
            assertThat(opts.isEnableDeepCompare()).isTrue();
            assertThat(opts.getMaxDepth()).isEqualTo(5);
        }

        @Test
        @DisplayName("typeAware()")
        void typeAware() {
            CompareOptions opts = CompareOptions.typeAware();
            assertThat(opts.isTypeAwareEnabled()).isTrue();
        }

        @Test
        @DisplayName("withReport")
        void withReport() {
            CompareOptions opts = CompareOptions.withReport(ReportFormat.JSON);
            assertThat(opts.isGenerateReport()).isTrue();
        }

        @Test
        @DisplayName("withPerfBudget")
        void withPerfBudget() {
            CompareOptions opts = CompareOptions.withPerfBudget(1000, 500);
            assertThat(opts.getPerfTimeoutMs()).isEqualTo(1000);
            assertThat(opts.getPerfMaxElements()).isEqualTo(500);
        }

        @Test
        @DisplayName("builder with all options")
        void builderAllOptions() {
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .maxDepth(10)
                .calculateSimilarity(true)
                .generateReport(true)
                .reportFormat(ReportFormat.HTML)
                .generatePatch(true)
                .patchFormat(PatchFormat.MERGE_PATCH)
                .includeNullChanges(true)
                .ignoreFields(List.of("x"))
                .excludeFields(List.of("y"))
                .parallelThreshold(20)
                .strategyName("SIMPLE")
                .attemptAutoMerge(true)
                .typeAwareEnabled(true)
                .detectMoves(true)
                .trackEntityKeyAttributes(true)
                .strictDuplicateKey(true)
                .perfTimeoutMs(3000)
                .perfMaxElements(5000)
                .perfStrictMode(true)
                .perfDegradationStrategy("STRICT")
                .build();
            assertThat(opts.isEnableDeepCompare()).isTrue();
            assertThat(opts.getMaxDepth()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Value types — primitive, wrapper, temporal")
    class ValueTypeBranches {

        @Test
        @DisplayName("BigDecimal comparison")
        void bigDecimal() {
            CompareResult r = service.compare(
                Map.of("bd", new BigDecimal("1.00")),
                Map.of("bd", new BigDecimal("1.01")));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Date comparison")
        void date() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            CompareResult r = service.compare(Map.of("d", d1), Map.of("d", d2));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("LocalDateTime comparison")
        void localDateTime() {
            CompareResult r = service.compare(
                Map.of("ldt", LocalDateTime.of(2025, 1, 1, 1, 1)),
                Map.of("ldt", LocalDateTime.of(2025, 1, 1, 1, 2)));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("LocalDate comparison")
        void localDate() {
            CompareResult r = service.compare(
                Map.of("ld", LocalDate.of(2025, 1, 1)),
                Map.of("ld", LocalDate.of(2025, 1, 2)));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Instant comparison")
        void instant() {
            CompareResult r = service.compare(
                Map.of("i", Instant.EPOCH),
                Map.of("i", Instant.EPOCH.plusSeconds(1)));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("ZonedDateTime comparison")
        void zonedDateTime() {
            CompareResult r = service.compare(
                Map.of("z", ZonedDateTime.now()),
                Map.of("z", ZonedDateTime.now().plusSeconds(1)));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Duration comparison")
        void duration() {
            CompareResult r = service.compare(
                Map.of("dur", Duration.ofSeconds(1)),
                Map.of("dur", Duration.ofSeconds(2)));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Period comparison")
        void period() {
            CompareResult r = service.compare(
                Map.of("p", Period.ofDays(1)),
                Map.of("p", Period.ofDays(2)));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Enum comparison")
        void enumCompare() {
            CompareResult r = service.compare(
                Map.of("e", ChangeType.CREATE),
                Map.of("e", ChangeType.DELETE));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Array comparison")
        void arrayCompare() {
            CompareResult r = service.compare(
                new String[]{"a", "b"},
                new String[]{"a", "c"});
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Empty array comparison")
        void emptyArrayCompare() {
            CompareResult r = service.compare(new String[0], new String[0]);
            assertThat(r).isNotNull();
        }
    }

    @Nested
    @DisplayName("CompareResult / FieldChange")
    class CompareResultFieldChangeBranches {

        @Test
        @DisplayName("CompareResult with degradationReasons")
        void compareResultDegradation() {
            CompareResult r = CompareResult.builder()
                .object1("a")
                .object2("b")
                .changes(Collections.emptyList())
                .identical(false)
                .degradationReasons(List.of("timeout"))
                .algorithmUsed("LCS")
                .build();
            assertThat(r.getDegradationReasons()).contains("timeout");
            assertThat(r.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("FieldChange referenceDetail")
        void fieldChangeReferenceDetail() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("ref")
                .referenceChange(true)
                .referenceDetail(rd)
                .build();
            assertThat(fc.isReferenceChange()).isTrue();
        }
    }

    // Simple POJO for deep fallback testing
    static class SimplePojo {
        private final String value;

        SimplePojo(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
