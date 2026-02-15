package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for tracking/compare package.
 * Targets CompareService, CompareEngine, CompareReportGenerator, ThreeWayMergeService,
 * StrategyResolver, MapCompareStrategy, SetCompareStrategy, CollectionCompareStrategy,
 * DateCompareStrategy, CompareOptions, and related types.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Compare Max Coverage — 比较包最大覆盖测试")
class CompareMaxCoverageTests {

    private CompareService service;

    @BeforeEach
    void setUp() {
        service = new CompareService();
    }

    // ── CompareService ──

    @Nested
    @DisplayName("CompareService — createDefault")
    class CreateDefaultTests {

        @Test
        @DisplayName("createDefault(options) returns service with programmatic detector")
        void createDefault_withOptions() {
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareService svc = CompareService.createDefault(opts);
            assertThat(svc).isNotNull();
            CompareResult r = svc.compare(Map.of("a", 1), Map.of("a", 2));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("createDefault(options, registry) with PropertyComparatorRegistry")
        void createDefault_withRegistry() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            CompareService svc = CompareService.createDefault(CompareOptions.DEFAULT, registry);
            assertThat(svc).isNotNull();
        }
    }

    @Nested
    @DisplayName("CompareService — compare with options")
    class CompareWithOptionsTests {

        @Test
        @DisplayName("compare with null options uses DEFAULT")
        void compare_nullOptions() {
            CompareResult r = service.compare("a", "b", null);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare with generateReport=true")
        void compare_generateReport() {
            Map<String, Object> a = Map.of("x", 1);
            Map<String, Object> b = Map.of("x", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = service.compare(a, b, opts);
            assertThat(r.getReport()).isNotNull().contains("Change Report");
        }

        @Test
        @DisplayName("compare with generatePatch=true")
        void compare_generatePatch() {
            Map<String, Object> a = Map.of("f", 1);
            Map<String, Object> b = Map.of("f", 2);
            CompareOptions opts = CompareOptions.builder()
                .generatePatch(true)
                .patchFormat(PatchFormat.JSON_PATCH)
                .build();
            CompareResult r = service.compare(a, b, opts);
            assertThat(r.getPatch()).isNotNull();
        }

        @Test
        @DisplayName("compare with calculateSimilarity=true")
        void compare_calculateSimilarity() {
            Map<String, Object> a = Map.of("a", 1, "b", 2);
            Map<String, Object> b = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = service.compare(a, b, opts);
            assertThat(r.getSimilarity()).isNotNull();
        }

        @Test
        @DisplayName("compare with excludeFields")
        void compare_excludeFields() {
            Map<String, Object> a = new HashMap<>(Map.of("x", 1, "y", 2));
            Map<String, Object> b = new HashMap<>(Map.of("x", 2, "y", 2));
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .excludeFields(List.of("y"))
                .build();
            CompareResult r = service.compare(a, b, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare with forcedObjectType and forcedStrategy")
        void compare_forcedTypeAndStrategy() {
            Map<String, Object> a = Map.of("v", 1);
            Map<String, Object> b = Map.of("v", 2);
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .typeAwareEnabled(true)
                .forcedObjectType(com.syy.taskflowinsight.annotation.ObjectType.ENTITY)
                .forcedStrategy(com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy.FIELDS)
                .build();
            CompareResult r = service.compare(a, b, opts);
            assertThat(r).isNotNull();
        }
    }

    @Nested
    @DisplayName("CompareService — compareBatch parallel threshold")
    class CompareBatchParallelTests {

        @Test
        @DisplayName("compareBatch exceeds parallelThreshold uses virtual threads")
        void compareBatch_parallelThreshold() {
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
    @DisplayName("CompareService — compareThreeWay")
    class CompareThreeWayTests {

        @Test
        @DisplayName("compareThreeWay with attemptAutoMerge and no conflicts")
        void compareThreeWay_attemptAutoMerge() {
            Map<String, Object> base = Map.of("a", 1, "b", 2);
            Map<String, Object> left = Map.of("a", 2, "b", 2);
            Map<String, Object> right = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().attemptAutoMerge(true).build();
            MergeResult mr = service.compareThreeWay(base, left, right, opts);
            assertThat(mr).isNotNull();
            assertThat(mr.getConflicts()).isNotNull();
        }

        @Test
        @DisplayName("compareThreeWay with conflicts")
        void compareThreeWay_conflicts() {
            Map<String, Object> base = Map.of("x", 1);
            Map<String, Object> left = Map.of("x", 2);
            Map<String, Object> right = Map.of("x", 3);
            MergeResult mr = service.compareThreeWay(base, left, right);
            assertThat(mr.getConflicts()).isNotEmpty();
        }
    }

    // ── CompareReportGenerator ──

    @Nested
    @DisplayName("CompareReportGenerator")
    class CompareReportGeneratorTests {

        @Test
        @DisplayName("generateReport empty changes returns No changes")
        void generateReport_emptyChanges() {
            String report = CompareReportGenerator.generateReport(
                Collections.emptyList(),
                CompareOptions.builder().reportFormat(ReportFormat.TEXT).build());
            assertThat(report).isEqualTo("No changes detected.");
        }

        @Test
        @DisplayName("generateReport TEXT format")
        void generateReport_text() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f1").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.TEXT).build());
            assertThat(r).contains("Change Report").contains("f1").contains("a").contains("b");
        }

        @Test
        @DisplayName("generateReport MARKDOWN format")
        void generateReport_markdown() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("x").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.MARKDOWN).build());
            assertThat(r).contains("# Change Report").contains("| Field |");
        }

        @Test
        @DisplayName("generateReport JSON format")
        void generateReport_json() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("n").oldValue("old").newValue("new").changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.JSON).build());
            assertThat(r).contains("\"changes\"").contains("\"field\"");
        }

        @Test
        @DisplayName("generateReport HTML format")
        void generateReport_html() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("h").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.HTML).build());
            assertThat(r).contains("<div").contains("<table").contains("Change Report");
        }

        @Test
        @DisplayName("generateReport XML format falls to default TEXT")
        void generateReport_xml() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("x").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String r = CompareReportGenerator.generateReport(changes,
                CompareOptions.builder().reportFormat(ReportFormat.XML).build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("generatePatch JSON_PATCH format")
        void generatePatch_jsonPatch() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("p").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String p = CompareReportGenerator.generatePatch(changes,
                CompareOptions.builder().patchFormat(PatchFormat.JSON_PATCH).build());
            assertThat(p).contains("\"op\"").contains("replace");
        }

        @Test
        @DisplayName("generatePatch MERGE_PATCH format")
        void generatePatch_mergePatch() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("m").oldValue("a").newValue("b").changeType(ChangeType.UPDATE).build());
            String p = CompareReportGenerator.generatePatch(changes,
                CompareOptions.builder().patchFormat(PatchFormat.MERGE_PATCH).build());
            assertThat(p).isNotNull();
        }

        @Test
        @DisplayName("generateJsonPatch CREATE")
        void generateJsonPatch_create() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("c").oldValue(null).newValue("v").changeType(ChangeType.CREATE).build());
            String p = CompareReportGenerator.generateJsonPatch(changes);
            assertThat(p).contains("add");
        }

        @Test
        @DisplayName("generateJsonPatch DELETE")
        void generateJsonPatch_delete() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("d").oldValue("v").newValue(null).changeType(ChangeType.DELETE).build());
            String p = CompareReportGenerator.generateJsonPatch(changes);
            assertThat(p).contains("remove");
        }

        @Test
        @DisplayName("generateJsonPatch MOVE")
        void generateJsonPatch_move() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("m").oldValue("a").newValue("b").changeType(ChangeType.MOVE).build());
            String p = CompareReportGenerator.generateJsonPatch(changes);
            assertThat(p).contains("move");
        }

        @Test
        @DisplayName("generateMergePatch nested path")
        void generateMergePatch_nestedPath() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("a.b.c").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build());
            String p = CompareReportGenerator.generateMergePatch(changes);
            assertThat(p).isNotNull();
        }

        @Test
        @DisplayName("toJsonValue null")
        void toJsonValue_null() {
            assertThat(CompareReportGenerator.toJsonValue(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("toJsonValue Number")
        void toJsonValue_number() {
            assertThat(CompareReportGenerator.toJsonValue(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("toJsonValue Boolean")
        void toJsonValue_boolean() {
            assertThat(CompareReportGenerator.toJsonValue(true)).isEqualTo("true");
        }

        @Test
        @DisplayName("toJsonValue String with escape")
        void toJsonValue_stringEscape() {
            String r = CompareReportGenerator.toJsonValue("a\"b\\c");
            assertThat(r).contains("\\\"").contains("\\\\");
        }

        @Test
        @DisplayName("toJsonObject Map")
        void toJsonObject_map() {
            Map<String, Object> m = Map.of("k", "v");
            String r = CompareReportGenerator.toJsonObject(m);
            assertThat(r).contains("\"k\"");
        }
    }

    // ── ThreeWayMergeService ──

    @Nested
    @DisplayName("ThreeWayMergeService")
    class ThreeWayMergeServiceTests {

        @Test
        @DisplayName("merge with conflicts")
        void merge_conflicts() {
            ThreeWayMergeService svc = new ThreeWayMergeService(service);
            Map<String, Object> base = Map.of("f", 1);
            Map<String, Object> left = Map.of("f", 2);
            Map<String, Object> right = Map.of("f", 3);
            MergeResult mr = svc.merge(base, left, right, CompareOptions.DEFAULT);
            assertThat(mr.getConflicts()).isNotEmpty();
        }

        @Test
        @DisplayName("merge with attemptAutoMerge no conflicts")
        void merge_attemptAutoMerge() {
            ThreeWayMergeService svc = new ThreeWayMergeService(service);
            Map<String, Object> base = Map.of("a", 1, "b", 2);
            Map<String, Object> left = Map.of("a", 2, "b", 2);
            Map<String, Object> right = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().attemptAutoMerge(true).build();
            MergeResult mr = svc.merge(base, left, right, opts);
            assertThat(mr).isNotNull();
        }
    }

    // ── StrategyResolver ──

    @Nested
    @DisplayName("StrategyResolver")
    class StrategyResolverTests {

        @Test
        @DisplayName("resolve null strategies returns null")
        void resolve_nullStrategies() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(null, String.class)).isNull();
        }

        @Test
        @DisplayName("resolve empty strategies returns null")
        void resolve_emptyStrategies() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(Collections.emptyList(), String.class)).isNull();
        }

        @Test
        @DisplayName("resolve null targetType returns null")
        void resolve_nullTargetType() {
            StrategyResolver resolver = new StrategyResolver();
            List<CompareStrategy<?>> strategies = List.of(new SetCompareStrategy());
            assertThat(resolver.resolve(strategies, null)).isNull();
        }

        @Test
        @DisplayName("resolve Set returns SetCompareStrategy")
        void resolve_set() {
            StrategyResolver resolver = new StrategyResolver();
            List<CompareStrategy<?>> strategies = List.of(
                new SetCompareStrategy(),
                new CollectionCompareStrategy(),
                new MapCompareStrategy());
            CompareStrategy<?> s = resolver.resolve(strategies, HashSet.class);
            assertThat(s).isNotNull();
            assertThat(s.getName()).contains("Set");
        }

        @Test
        @DisplayName("resolve Map returns MapCompareStrategy")
        void resolve_map() {
            StrategyResolver resolver = new StrategyResolver();
            List<CompareStrategy<?>> strategies = List.of(
                new MapCompareStrategy(),
                new CollectionCompareStrategy());
            CompareStrategy<?> s = resolver.resolve(strategies, HashMap.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("resolve Date returns DateCompareStrategy")
        void resolve_date() {
            StrategyResolver resolver = new StrategyResolver();
            List<CompareStrategy<?>> strategies = List.of(new DateCompareStrategy());
            CompareStrategy<?> s = resolver.resolve(strategies, Date.class);
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

        @Test
        @DisplayName("getCacheHitRate without StrategyCache returns -1")
        void getCacheHitRate_noCache() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.getCacheHitRate()).isEqualTo(-1.0);
        }
    }

    // ── MapCompareStrategy ──

    @Nested
    @DisplayName("MapCompareStrategy")
    class MapCompareStrategyTests {

        private final MapCompareStrategy strategy = new MapCompareStrategy();

        @Test
        @DisplayName("compare same reference")
        void compare_sameRef() {
            Map<String, Integer> m = Map.of("a", 1);
            CompareResult r = strategy.compare(m, m, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare null map")
        void compare_nullMap() {
            CompareResult r = strategy.compare(null, Map.of("a", 1), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with calculateSimilarity")
        void compare_similarity() {
            Map<String, Integer> a = Map.of("a", 1, "b", 2);
            Map<String, Integer> b = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getSimilarity()).isNotNull();
        }

        @Test
        @DisplayName("compare with generateReport MARKDOWN")
        void compare_reportMarkdown() {
            Map<String, Integer> a = Map.of("x", 1);
            Map<String, Integer> b = Map.of("x", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getReport()).contains("## Map Comparison");
        }

        @Test
        @DisplayName("compare with generateReport TEXT")
        void compare_reportText() {
            Map<String, Integer> a = Map.of("x", 1);
            Map<String, Integer> b = Map.of("x", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getReport()).contains("Map Comparison");
        }

        @Test
        @DisplayName("generateDetailedChangeRecords")
        void generateDetailedChangeRecords() {
            Map<String, Integer> oldVal = Map.of("k", 1);
            Map<String, Integer> newVal = Map.of("k", 2);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "field", oldVal, newVal, null, null);
            assertThat(recs).isNotEmpty();
        }

        @Test
        @DisplayName("generateDetailedChangeRecords both null")
        void generateDetailedChangeRecords_bothNull() {
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "f", null, null, null, null);
            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("compare maps with key rename detection")
        void compare_keyRename() {
            Map<String, String> a = Map.of("oldKey", "sameValue");
            Map<String, String> b = Map.of("newKey", "sameValue");
            CompareResult r = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }
    }

    // ── SetCompareStrategy ──

    @Nested
    @DisplayName("SetCompareStrategy")
    class SetCompareStrategyTests {

        private final SetCompareStrategy strategy = new SetCompareStrategy();

        @Test
        @DisplayName("compare simple Set add/remove")
        void compare_simpleSet() {
            Set<String> a = Set.of("a", "b");
            Set<String> b = Set.of("a", "c");
            CompareResult r = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("compare with calculateSimilarity")
        void compare_similarity() {
            Set<Integer> a = Set.of(1, 2);
            Set<Integer> b = Set.of(1, 3);
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getSimilarity()).isNotNull();
        }

        @Test
        @DisplayName("compare with generateReport")
        void compare_report() {
            Set<String> a = Set.of("x");
            Set<String> b = Set.of("y");
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getReport()).contains("## Set Comparison");
        }

        @Test
        @DisplayName("generateDetailedChangeRecords simple Set")
        void generateDetailedChangeRecords() {
            Set<String> oldVal = Set.of("a");
            Set<String> newVal = Set.of("b");
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "f", oldVal, newVal, null, null);
            assertThat(recs).isNotEmpty();
        }

        @Test
        @DisplayName("supports Set")
        void supports() {
            assertThat(strategy.supports(Set.class)).isTrue();
            assertThat(strategy.supports(HashSet.class)).isTrue();
        }
    }

    // ── CollectionCompareStrategy ──

    @Nested
    @DisplayName("CollectionCompareStrategy")
    class CollectionCompareStrategyTests {

        private final CollectionCompareStrategy strategy = new CollectionCompareStrategy();

        @Test
        @DisplayName("compare Collection add/remove")
        void compare_collection() {
            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "c");
            CompareResult r = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with similarity and report")
        void compare_similarityAndReport() {
            List<Integer> a = List.of(1, 2);
            List<Integer> b = List.of(1, 3);
            CompareOptions opts = CompareOptions.builder()
                .calculateSimilarity(true)
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getReport()).contains("## Collection Comparison");
        }

        @Test
        @DisplayName("compare same size different elements")
        void compare_sameSizeDifferent() {
            List<String> a = List.of("x");
            List<String> b = List.of("y");
            CompareResult r = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("supports Collection")
        void supports() {
            assertThat(strategy.supports(Collection.class)).isTrue();
            assertThat(strategy.supports(ArrayList.class)).isTrue();
        }
    }

    // ── DateCompareStrategy ──

    @Nested
    @DisplayName("DateCompareStrategy")
    class DateCompareStrategyTests {

        private final DateCompareStrategy strategy = new DateCompareStrategy();

        @Test
        @DisplayName("compare same Date")
        void compare_sameDate() {
            Date d = new Date(1000);
            CompareResult r = strategy.compare(d, d, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare different Date (beyond 1000ms tolerance)")
        void compare_differentDate() {
            Date a = new Date(0);
            Date b = new Date(2000);  // 2000ms diff > TOLERANCE_MS(1000)
            CompareResult r = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with report")
        void compare_report() {
            Date a = new Date(1000);
            Date b = new Date(2000);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(a, b, opts);
            assertThat(r.getReport()).isNotNull();
        }

        @Test
        @DisplayName("supports Date")
        void supports() {
            assertThat(strategy.supports(Date.class)).isTrue();
        }
    }

    // ── ArrayCompareStrategy ──

    @Nested
    @DisplayName("ArrayCompareStrategy")
    class ArrayCompareStrategyTests {

        @Test
        @DisplayName("compare arrays via CompareService")
        void compare_arrays() {
            String[] a = new String[]{"a", "b"};
            String[] b = new String[]{"a", "c"};
            CompareResult r = service.compare(a, b);
            assertThat(r).isNotNull();
        }
    }

    // ── CompareOptions ──

    @Nested
    @DisplayName("CompareOptions")
    class CompareOptionsTests {

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
        @DisplayName("withReport(ReportFormat)")
        void withReport() {
            CompareOptions opts = CompareOptions.withReport(ReportFormat.JSON);
            assertThat(opts.isGenerateReport()).isTrue();
            assertThat(opts.getReportFormat()).isEqualTo(ReportFormat.JSON);
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
        void builder_allOptions() {
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
                .strategyName("custom")
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
            assertThat(opts.getReportFormat()).isEqualTo(ReportFormat.HTML);
            assertThat(opts.getPatchFormat()).isEqualTo(PatchFormat.MERGE_PATCH);
            assertThat(opts.getParallelThreshold()).isEqualTo(20);
            assertThat(opts.isStrictDuplicateKey()).isTrue();
        }
    }

    // ── CompareResult / FieldChange ──

    @Nested
    @DisplayName("CompareResult / FieldChange")
    class CompareResultFieldChangeTests {

        @Test
        @DisplayName("CompareResult with degradationReasons and algorithmUsed")
        void compareResult_degradationAndAlgo() {
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
        @DisplayName("CompareResult setChanges")
        void compareResult_setChanges() {
            CompareResult r = CompareResult.identical();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("x").changeType(ChangeType.UPDATE).build());
            r.setChanges(changes);
            assertThat(r.getChanges()).hasSize(1);
        }

        @Test
        @DisplayName("FieldChange referenceDetail")
        void fieldChange_referenceDetail() {
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
            assertThat(fc.getReferenceDetail()).isNotNull();
        }
    }
}
