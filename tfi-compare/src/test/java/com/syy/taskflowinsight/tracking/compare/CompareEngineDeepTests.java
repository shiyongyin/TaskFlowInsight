package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompareEngine Deep Coverage Tests — maximizes code coverage for the tracking/compare package.
 * Pure unit tests, no Spring context.
 *
 * @author Senior Java Test Expert
 * @since 3.0.0
 */
@DisplayName("CompareEngine Deep Coverage Tests")
class CompareEngineDeepTests {

    private CompareService compareService;

    @BeforeEach
    void setUp() {
        compareService = new CompareService();
    }

    // ──────────────────────────────────────────────────────────────
    //  CompareResult
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareResult")
    class CompareResultTests {

        @Test
        @DisplayName("identical() factory")
        void identical_shouldCreateCorrectResult() {
            CompareResult r = CompareResult.identical();
            assertThat(r.isIdentical()).isTrue();
            assertThat(r.getChanges()).isEmpty();
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("ofNullDiff factory")
        void ofNullDiff_shouldCreateCorrectResult() {
            Object a = "left";
            Object b = "right";
            CompareResult r = CompareResult.ofNullDiff(a, b);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getObject1()).isEqualTo(a);
            assertThat(r.getObject2()).isEqualTo(b);
            assertThat(r.getSimilarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("ofTypeDiff factory")
        void ofTypeDiff_shouldCreateCorrectResult() {
            CompareResult r = CompareResult.ofTypeDiff("str", 42);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getSimilarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("builder with full fields")
        void builder_shouldCreateFullResult() {
            FieldChange fc = FieldChange.builder()
                .fieldName("x")
                .oldValue(1)
                .newValue(2)
                .changeType(ChangeType.UPDATE)
                .build();
            CompareResult r = CompareResult.builder()
                .object1("a")
                .object2("b")
                .changes(List.of(fc))
                .identical(false)
                .similarity(0.5)
                .duplicateKeys(Set.of("dup1"))
                .algorithmUsed("TEST")
                .degradationReasons(List.of("reason1"))
                .build();
            assertThat(r.getChangeCount()).isEqualTo(1);
            assertThat(r.getSimilarityPercent()).isEqualTo(50.0);
            assertThat(r.hasDuplicateKeys()).isTrue();
            assertThat(r.getAlgorithmUsed()).isEqualTo("TEST");
        }

        @Test
        @DisplayName("getChangeCount when changes null")
        void getChangeCount_nullChanges_returnsZero() {
            CompareResult r = CompareResult.builder().identical(true).build();
            assertThat(r.getChangeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("hasChanges")
        void hasChanges_shouldReflectState() {
            assertThat(CompareResult.identical().hasChanges()).isFalse();
            CompareResult withChanges = CompareResult.builder()
                .identical(false)
                .changes(List.of(FieldChange.builder().fieldName("x").changeType(ChangeType.UPDATE).build()))
                .build();
            assertThat(withChanges.hasChanges()).isTrue();
        }

        @Test
        @DisplayName("getSimilarityPercent when similarity null")
        void getSimilarityPercent_null_returnsZero() {
            CompareResult r = CompareResult.builder().identical(false).build();
            assertThat(r.getSimilarityPercent()).isEqualTo(0);
        }

        @Test
        @DisplayName("getChangesByType")
        void getChangesByType_shouldFilter() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("a").changeType(ChangeType.CREATE).build(),
                FieldChange.builder().fieldName("b").changeType(ChangeType.UPDATE).build(),
                FieldChange.builder().fieldName("c").changeType(ChangeType.DELETE).build()
            );
            CompareResult r = CompareResult.builder().changes(changes).identical(false).build();
            assertThat(r.getChangesByType(ChangeType.CREATE)).hasSize(1);
            assertThat(r.getChangesByType(ChangeType.UPDATE)).hasSize(1);
            assertThat(r.getChangesByType(ChangeType.CREATE, ChangeType.DELETE)).hasSize(2);
            assertThat(r.getChangesByType()).hasSize(3);
        }

        @Test
        @DisplayName("getReferenceChanges")
        void getReferenceChanges_shouldFilter() {
            FieldChange ref = FieldChange.builder().fieldName("ref").referenceChange(true).build();
            FieldChange normal = FieldChange.builder().fieldName("n").referenceChange(false).build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(ref, normal))
                .identical(false)
                .build();
            assertThat(r.getReferenceChanges()).hasSize(1);
        }

        @Test
        @DisplayName("getContainerChanges")
        void getContainerChanges_shouldFilter() {
            FieldChange.ContainerElementEvent evt = ContainerEvents.arrayAdd(0);
            FieldChange container = FieldChange.builder()
                .fieldName("arr")
                .elementEvent(evt)
                .build();
            FieldChange normal = FieldChange.builder().fieldName("n").build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(container, normal))
                .identical(false)
                .build();
            assertThat(r.getContainerChanges()).hasSize(1);
        }

        @Test
        @DisplayName("groupByObject")
        void groupByObject_shouldGroupByPath() {
            FieldChange fc1 = FieldChange.builder().fieldName("order.status").fieldPath("order.status").build();
            FieldChange fc2 = FieldChange.builder().fieldName("order.amount").fieldPath("order.amount").build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(fc1, fc2))
                .identical(false)
                .build();
            Map<String, List<FieldChange>> grouped = r.groupByObject();
            assertThat(grouped).isNotEmpty();
        }

        @Test
        @DisplayName("groupByProperty")
        void groupByProperty_shouldGroupByFieldName() {
            FieldChange fc1 = FieldChange.builder().fieldName("price").fieldPath("items[0].price").build();
            FieldChange fc2 = FieldChange.builder().fieldName("price").fieldPath("items[1].price").build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(fc1, fc2))
                .identical(false)
                .build();
            Map<String, List<FieldChange>> grouped = r.groupByProperty();
            assertThat(grouped.get("price")).hasSize(2);
        }

        @Test
        @DisplayName("groupByContainerOperation")
        void groupByContainerOperation_shouldGroup() {
            FieldChange fc = FieldChange.builder()
                .fieldName("x")
                .elementEvent(ContainerEvents.arrayAdd(0))
                .build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(fc))
                .identical(false)
                .build();
            Map<FieldChange.ElementOperation, List<FieldChange>> grouped = r.groupByContainerOperation();
            assertThat(grouped).containsKey(FieldChange.ElementOperation.ADD);
        }

        @Test
        @DisplayName("groupByContainerOperationAsString deprecated")
        void groupByContainerOperationAsString_shouldWork() {
            FieldChange fc = FieldChange.builder()
                .fieldName("x")
                .elementEvent(ContainerEvents.arrayAdd(0))
                .build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(fc))
                .identical(false)
                .build();
            Map<String, List<FieldChange>> grouped = r.groupByContainerOperationAsString();
            assertThat(grouped).containsKey("ADD");
        }

        @Test
        @DisplayName("getContainerChangesByType")
        void getContainerChangesByType_shouldFilter() {
            FieldChange fc = FieldChange.builder()
                .fieldName("x")
                .elementEvent(ContainerEvents.arrayAdd(0))
                .build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(fc))
                .identical(false)
                .build();
            List<FieldChange> byType = r.getContainerChangesByType(FieldChange.ContainerType.ARRAY);
            assertThat(byType).hasSize(1);
        }

        @Test
        @DisplayName("getChangeCountByType")
        void getChangeCountByType_shouldCount() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("a").changeType(ChangeType.CREATE).build(),
                FieldChange.builder().fieldName("b").changeType(ChangeType.CREATE).build()
            );
            CompareResult r = CompareResult.builder().changes(changes).identical(false).build();
            Map<ChangeType, Long> counts = r.getChangeCountByType();
            assertThat(counts.get(ChangeType.CREATE)).isEqualTo(2L);
        }

        @Test
        @DisplayName("prettyPrint")
        void prettyPrint_shouldFormat() {
            FieldChange fc = FieldChange.builder()
                .fieldName("name")
                .changeType(ChangeType.UPDATE)
                .build();
            CompareResult r = CompareResult.builder()
                .changes(List.of(fc))
                .identical(false)
                .build();
            String out = r.prettyPrint();
            assertThat(out).contains("Change Summary");
            assertThat(out).contains("Total:");
        }

        @Test
        @DisplayName("prettyPrint empty changes")
        void prettyPrint_empty_returnsNoChanges() {
            CompareResult r = CompareResult.identical();
            String out = r.prettyPrint();
            assertThat(out).contains("No changes detected");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  FieldChange
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FieldChange")
    class FieldChangeTests {

        @Test
        @DisplayName("builder full")
        void builder_shouldCreateFullChange() {
            FieldChange fc = FieldChange.builder()
                .fieldName("price")
                .fieldPath("order.price")
                .oldValue(100.0)
                .newValue(90.0)
                .changeType(ChangeType.UPDATE)
                .valueType("Double")
                .collectionChange(true)
                .build();
            assertThat(fc.getFieldName()).isEqualTo("price");
            assertThat(fc.getOldValue()).isEqualTo(100.0);
            assertThat(fc.getNewValue()).isEqualTo(90.0);
        }

        @Test
        @DisplayName("getValueDescription")
        void getValueDescription_shouldFormat() {
            assertThat(FieldChange.builder().changeType(ChangeType.DELETE).oldValue("x").build().getValueDescription())
                .contains("deleted");
            assertThat(FieldChange.builder().changeType(ChangeType.CREATE).newValue("y").build().getValueDescription())
                .contains("new");
            assertThat(FieldChange.builder().changeType(ChangeType.UPDATE).oldValue("a").newValue("b").build().getValueDescription())
                .contains("a").contains("b");
        }

        @Test
        @DisplayName("isNullChange")
        void isNullChange_shouldDetect() {
            assertThat(FieldChange.builder().oldValue(null).newValue(null).changeType(ChangeType.UPDATE).build().isNullChange()).isTrue();
            assertThat(FieldChange.builder().oldValue(null).changeType(ChangeType.CREATE).build().isNullChange()).isTrue();
            assertThat(FieldChange.builder().newValue(null).changeType(ChangeType.DELETE).build().isNullChange()).isTrue();
        }

        @Test
        @DisplayName("isContainerElementChange")
        void isContainerElementChange_shouldDetect() {
            assertThat(FieldChange.builder().elementEvent(ContainerEvents.arrayAdd(0)).build().isContainerElementChange()).isTrue();
            assertThat(FieldChange.builder().build().isContainerElementChange()).isFalse();
        }

        @Test
        @DisplayName("getContainerIndex")
        void getContainerIndex_shouldReturnIndex() {
            FieldChange fc = FieldChange.builder().elementEvent(ContainerEvents.arrayAdd(5)).build();
            assertThat(fc.getContainerIndex()).isEqualTo(5);
        }

        @Test
        @DisplayName("getEntityKey")
        void getEntityKey_shouldReturnFromEvent() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .entityKey("order[1]")
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            assertThat(fc.getEntityKey()).isEqualTo("order[1]");
        }

        @Test
        @DisplayName("getMapKey")
        void getMapKey_shouldReturnFromEvent() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .mapKey("key1")
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            assertThat(fc.getMapKey()).isEqualTo("key1");
        }

        @Test
        @DisplayName("toTypedView")
        void toTypedView_shouldProduceMap() {
            FieldChange fc = FieldChange.builder()
                .fieldName("items[0]")
                .fieldPath("items[0]")
                .elementEvent(ContainerEvents.arrayAdd(0))
                .build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            assertThat(view).containsKey("kind");
            assertThat(view).containsKey("path");
        }

        @Test
        @DisplayName("toTypedView non-container returns null")
        void toTypedView_nonContainer_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("x").build();
            assertThat(fc.toTypedView()).isNull();
        }

        @Test
        @DisplayName("toReferenceChangeView")
        void toReferenceChangeView_shouldProduceMap() {
            FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("ref")
                .fieldPath("ref")
                .referenceChange(true)
                .referenceDetail(detail)
                .build();
            Map<String, Object> view = fc.toReferenceChangeView();
            assertThat(view).isNotNull();
            assertThat(view).containsKey("kind");
        }

        @Test
        @DisplayName("ReferenceDetail toMap")
        void referenceDetail_toMap() {
            FieldChange.ReferenceDetail d = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("a")
                .newEntityKey("b")
                .nullReferenceChange(false)
                .build();
            Map<String, Object> m = d.toMap();
            assertThat(m).containsEntry("oldKey", "a");
            assertThat(m).containsEntry("newKey", "b");
        }

        @Test
        @DisplayName("ReferenceDetail toJson")
        void referenceDetail_toJson() {
            FieldChange.ReferenceDetail d = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("a")
                .newEntityKey("b")
                .build();
            String json = d.toJson();
            assertThat(json).contains("oldKey");
        }

        @Test
        @DisplayName("CollectionChangeDetail builder")
        void collectionChangeDetail_builder() {
            FieldChange.CollectionChangeDetail d = FieldChange.CollectionChangeDetail.builder()
                .addedCount(2)
                .removedCount(1)
                .originalSize(5)
                .newSize(6)
                .build();
            assertThat(d.getAddedCount()).isEqualTo(2);
            assertThat(d.getRemovedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ContainerType enum")
        void containerType_enumValues() {
            assertThat(FieldChange.ContainerType.values()).contains(
                FieldChange.ContainerType.LIST,
                FieldChange.ContainerType.SET,
                FieldChange.ContainerType.MAP,
                FieldChange.ContainerType.ARRAY
            );
        }

        @Test
        @DisplayName("ElementOperation enum")
        void elementOperation_enumValues() {
            assertThat(FieldChange.ElementOperation.values()).contains(
                FieldChange.ElementOperation.ADD,
                FieldChange.ElementOperation.REMOVE,
                FieldChange.ElementOperation.MODIFY,
                FieldChange.ElementOperation.MOVE
            );
        }

        @Test
        @DisplayName("clock getter/setter")
        void clock_getterSetter() {
            java.time.Clock original = FieldChange.getClock();
            try {
                FieldChange.setClock(java.time.Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")));
                assertThat(FieldChange.getClock()).isNotNull();
            } finally {
                FieldChange.setClock(original);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CompareOptions
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareOptions")
    class CompareOptionsTests {

        @Test
        @DisplayName("DEFAULT")
        void default_shouldBeValid() {
            assertThat(CompareOptions.DEFAULT).isNotNull();
        }

        @Test
        @DisplayName("DEEP")
        void deep_shouldEnableDeep() {
            assertThat(CompareOptions.DEEP.isEnableDeepCompare()).isTrue();
        }

        @Test
        @DisplayName("WITH_REPORT")
        void withReport_shouldGenerateReport() {
            assertThat(CompareOptions.WITH_REPORT.isGenerateReport()).isTrue();
        }

        @Test
        @DisplayName("builder all options")
        void builder_shouldSupportAllOptions() {
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .maxDepth(10)
                .calculateSimilarity(true)
                .generateReport(true)
                .reportFormat(ReportFormat.JSON)
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
                .perfDegradationStrategy("SKIP")
                .build();
            assertThat(opts.isEnableDeepCompare()).isTrue();
            assertThat(opts.getReportFormat()).isEqualTo(ReportFormat.JSON);
            assertThat(opts.getPatchFormat()).isEqualTo(PatchFormat.MERGE_PATCH);
        }

        @Test
        @DisplayName("deep(int)")
        void deepStatic_shouldCreate() {
            CompareOptions opts = CompareOptions.deep(7);
            assertThat(opts.isEnableDeepCompare()).isTrue();
            assertThat(opts.getMaxDepth()).isEqualTo(7);
        }

        @Test
        @DisplayName("typeAware()")
        void typeAware_shouldCreate() {
            CompareOptions opts = CompareOptions.typeAware();
            assertThat(opts.isTypeAwareEnabled()).isTrue();
        }

        @Test
        @DisplayName("withReport(ReportFormat)")
        void withReportStatic_shouldCreate() {
            CompareOptions opts = CompareOptions.withReport(ReportFormat.HTML);
            assertThat(opts.getReportFormat()).isEqualTo(ReportFormat.HTML);
        }

        @Test
        @DisplayName("withPerfBudget")
        void withPerfBudget_shouldCreate() {
            CompareOptions opts = CompareOptions.withPerfBudget(1000, 500);
            assertThat(opts.getPerfTimeoutMs()).isEqualTo(1000);
            assertThat(opts.getPerfMaxElements()).isEqualTo(500);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Pair
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pair")
    class PairTests {

        @Test
        @DisplayName("of()")
        void of_shouldCreate() {
            Pair<String, Integer> p = Pair.of("a", 1);
            assertThat(p.getLeft()).isEqualTo("a");
            assertThat(p.getRight()).isEqualTo(1);
        }

        @Test
        @DisplayName("swap()")
        void swap_shouldSwap() {
            Pair<String, Integer> p = Pair.of("a", 1);
            Pair<Integer, String> swapped = p.swap();
            assertThat(swapped.getLeft()).isEqualTo(1);
            assertThat(swapped.getRight()).isEqualTo("a");
        }

        @Test
        @DisplayName("no-arg constructor")
        void noArgConstructor_shouldWork() {
            Pair<String, String> p = new Pair<>();
            assertThat(p.getLeft()).isNull();
            assertThat(p.getRight()).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CompareConstants
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareConstants")
    class CompareConstantsTests {

        @Test
        @DisplayName("list constants")
        void listConstants() {
            assertThat(CompareConstants.LIST_SIZE_DEGRADATION_THRESHOLD).isPositive();
            assertThat(CompareConstants.LIST_SIZE_FORCE_SIMPLE_THRESHOLD).isPositive();
        }

        @Test
        @DisplayName("map constants")
        void mapConstants() {
            assertThat(CompareConstants.MAP_KEY_RENAME_SIMILARITY_THRESHOLD).isBetween(0.0, 1.0);
            assertThat(CompareConstants.MAP_CANDIDATE_PAIRS_DEGRADATION_THRESHOLD).isPositive();
        }

        @Test
        @DisplayName("strategy names")
        void strategyNames() {
            assertThat(CompareConstants.STRATEGY_SIMPLE).isEqualTo("SIMPLE");
            assertThat(CompareConstants.STRATEGY_LCS).isEqualTo("LCS");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CompareReportGenerator
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareReportGenerator")
    class CompareReportGeneratorTests {

        private List<FieldChange> sampleChanges() {
            return List.of(
                FieldChange.builder().fieldName("name").oldValue("A").newValue("B").changeType(ChangeType.UPDATE).build(),
                FieldChange.builder().fieldName("price").oldValue(100).newValue(200).changeType(ChangeType.UPDATE).build()
            );
        }

        @Test
        @DisplayName("generateReport empty")
        void generateReport_empty_returnsNoChanges() {
            String r = CompareReportGenerator.generateReport(Collections.emptyList(),
                CompareOptions.builder().reportFormat(ReportFormat.TEXT).build());
            assertThat(r).isEqualTo("No changes detected.");
        }

        @Test
        @DisplayName("generateReport TEXT")
        void generateReport_text() {
            String r = CompareReportGenerator.generateReport(sampleChanges(),
                CompareOptions.builder().reportFormat(ReportFormat.TEXT).build());
            assertThat(r).contains("name").contains("price");
        }

        @Test
        @DisplayName("generateReport MARKDOWN")
        void generateReport_markdown() {
            String r = CompareReportGenerator.generateReport(sampleChanges(),
                CompareOptions.builder().reportFormat(ReportFormat.MARKDOWN).build());
            assertThat(r).contains("# Change Report");
        }

        @Test
        @DisplayName("generateReport JSON")
        void generateReport_json() {
            String r = CompareReportGenerator.generateReport(sampleChanges(),
                CompareOptions.builder().reportFormat(ReportFormat.JSON).build());
            assertThat(r).contains("\"changes\"");
        }

        @Test
        @DisplayName("generateReport HTML")
        void generateReport_html() {
            String r = CompareReportGenerator.generateReport(sampleChanges(),
                CompareOptions.builder().reportFormat(ReportFormat.HTML).build());
            assertThat(r).contains("<table>");
        }

        @Test
        @DisplayName("generatePatch JSON_PATCH")
        void generatePatch_jsonPatch() {
            String p = CompareReportGenerator.generatePatch(sampleChanges(),
                CompareOptions.builder().patchFormat(PatchFormat.JSON_PATCH).build());
            assertThat(p).contains("\"op\"");
        }

        @Test
        @DisplayName("generatePatch MERGE_PATCH")
        void generatePatch_mergePatch() {
            String p = CompareReportGenerator.generatePatch(sampleChanges(),
                CompareOptions.builder().patchFormat(PatchFormat.MERGE_PATCH).build());
            assertThat(p).contains("\"name\"");
        }

        @Test
        @DisplayName("generateMergePatch nested path")
        void generateMergePatch_nestedPath() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("order.items.price").oldValue(10).newValue(20).changeType(ChangeType.UPDATE).build()
            );
            String p = CompareReportGenerator.generateMergePatch(changes);
            assertThat(p).contains("order");
        }

        @Test
        @DisplayName("toJsonValue")
        void toJsonValue() {
            assertThat(CompareReportGenerator.toJsonValue(null)).isEqualTo("null");
            assertThat(CompareReportGenerator.toJsonValue(42)).isEqualTo("42");
            assertThat(CompareReportGenerator.toJsonValue(true)).isEqualTo("true");
            assertThat(CompareReportGenerator.toJsonValue("x")).isEqualTo("\"x\"");
        }

        @Test
        @DisplayName("toJsonObject Map")
        void toJsonObject_map() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", 1);
            m.put("b", "x");
            String s = CompareReportGenerator.toJsonObject(m);
            assertThat(s).contains("a").contains("b");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  MergeResult / MergeConflict / ConflictType
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MergeResult")
    class MergeResultTests {

        @Test
        @DisplayName("builder")
        void builder_shouldCreate() {
            MergeResult r = MergeResult.builder()
                .base("base")
                .left("left")
                .right("right")
                .leftChanges(List.of())
                .rightChanges(List.of())
                .conflicts(List.of())
                .merged("merged")
                .autoMergeSuccessful(true)
                .build();
            assertThat(r.getBase()).isEqualTo("base");
            assertThat(r.hasConflicts()).isFalse();
            assertThat(r.getTotalChanges()).isEqualTo(0);
        }

        @Test
        @DisplayName("hasConflicts")
        void hasConflicts_shouldDetect() {
            MergeConflict c = MergeConflict.builder()
                .fieldName("x")
                .leftValue("a")
                .rightValue("b")
                .conflictType(ConflictType.VALUE_CONFLICT)
                .build();
            MergeResult r = MergeResult.builder().conflicts(List.of(c)).build();
            assertThat(r.hasConflicts()).isTrue();
            assertThat(r.getConflictCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("canAutoMerge with strategy")
        void canAutoMerge_withStrategy() {
            MergeStrategy strategy = new MergeStrategy() {
                @Override
                public boolean canResolveConflicts() { return true; }
                @Override
                public Object resolveConflict(MergeConflict conflict) { return null; }
                @Override
                public String getName() { return "test"; }
                @Override
                public String getDescription() { return "desc"; }
            };
            MergeConflict c = MergeConflict.builder().fieldName("x").conflictType(ConflictType.VALUE_CONFLICT).build();
            MergeResult r = MergeResult.builder()
                .conflicts(List.of(c))
                .strategy(strategy)
                .build();
            assertThat(r.canAutoMerge()).isTrue();
        }
    }

    @Nested
    @DisplayName("MergeConflict")
    class MergeConflictTests {

        @Test
        @DisplayName("getSummary")
        void getSummary_shouldFormat() {
            MergeConflict c = MergeConflict.builder()
                .fieldName("status")
                .leftValue("A")
                .rightValue("B")
                .conflictType(ConflictType.VALUE_CONFLICT)
                .build();
            assertThat(c.getSummary()).contains("status").contains("A").contains("B");
        }
    }

    @Nested
    @DisplayName("ConflictType")
    class ConflictTypeTests {

        @Test
        @DisplayName("enum values")
        void enumValues() {
            assertThat(ConflictType.values()).contains(
                ConflictType.VALUE_CONFLICT,
                ConflictType.TYPE_CONFLICT,
                ConflictType.DELETE_CONFLICT,
                ConflictType.ADD_CONFLICT,
                ConflictType.STRUCTURE_CONFLICT
            );
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ReportFormat / PatchFormat
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReportFormat")
    class ReportFormatTests {

        @Test
        @DisplayName("enum values")
        void enumValues() {
            assertThat(ReportFormat.values()).contains(
                ReportFormat.TEXT,
                ReportFormat.MARKDOWN,
                ReportFormat.JSON,
                ReportFormat.HTML,
                ReportFormat.XML
            );
        }
    }

    @Nested
    @DisplayName("PatchFormat")
    class PatchFormatTests {

        @Test
        @DisplayName("enum values")
        void enumValues() {
            assertThat(PatchFormat.values()).contains(
                PatchFormat.JSON_PATCH,
                PatchFormat.MERGE_PATCH,
                PatchFormat.CUSTOM
            );
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  PropertyComparatorRegistry
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PropertyComparatorRegistry")
    class PropertyComparatorRegistryTests {

        @Test
        @DisplayName("register and find by path")
        void register_findByPath() {
            PropertyComparatorRegistry reg = new PropertyComparatorRegistry();
            PropertyComparator comp = (l, r, f) -> Objects.equals(l, r);
            reg.register("order.price", comp);
            assertThat(reg.findComparator("order.price", null)).isSameAs(comp);
        }

        @Test
        @DisplayName("register invalid path throws")
        void register_invalidPath_throws() {
            PropertyComparatorRegistry reg = new PropertyComparatorRegistry();
            assertThatThrownBy(() -> reg.register("", (l, r, f) -> true))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reg.register(".path", (l, r, f) -> true))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("findComparator miss returns null")
        void findComparator_miss_returnsNull() {
            PropertyComparatorRegistry reg = new PropertyComparatorRegistry();
            assertThat(reg.findComparator("unknown.path", null)).isNull();
        }

        @Test
        @DisplayName("getMetricsSnapshot")
        void getMetricsSnapshot() {
            PropertyComparatorRegistry reg = new PropertyComparatorRegistry();
            PropertyComparator comp = (l, r, f) -> true;
            reg.register("x", comp);
            reg.findComparator("x", null);
            PropertyComparatorRegistry.MetricsSnapshot snap = reg.getMetricsSnapshot();
            assertThat(snap.getPathHits()).isGreaterThanOrEqualTo(1);
            assertThat(snap.getTotalHits()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("resetMetrics")
        void resetMetrics() {
            PropertyComparatorRegistry reg = new PropertyComparatorRegistry();
            reg.register("x", (l, r, f) -> true);
            reg.findComparator("x", null);
            reg.resetMetrics();
            PropertyComparatorRegistry.MetricsSnapshot snap = reg.getMetricsSnapshot();
            assertThat(snap.getPathHits()).isEqualTo(0);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ContainerEvents
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ContainerEvents")
    class ContainerEventsTests {

        @Test
        @DisplayName("listAdd")
        void listAdd() {
            FieldChange.ContainerElementEvent e = ContainerEvents.listAdd(0, "entity[1]");
            assertThat(e.getContainerType()).isEqualTo(FieldChange.ContainerType.LIST);
            assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.ADD);
            assertThat(e.getIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("listRemove")
        void listRemove() {
            FieldChange.ContainerElementEvent e = ContainerEvents.listRemove(1, "e");
            assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.REMOVE);
        }

        @Test
        @DisplayName("listModify")
        void listModify() {
            FieldChange.ContainerElementEvent e = ContainerEvents.listModify(0, "e", "price");
            assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.MODIFY);
            assertThat(e.getPropertyPath()).isEqualTo("price");
        }

        @Test
        @DisplayName("listMove")
        void listMove() {
            FieldChange.ContainerElementEvent e = ContainerEvents.listMove(0, 2, "e");
            assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.MOVE);
            assertThat(e.getOldIndex()).isEqualTo(0);
            assertThat(e.getNewIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("mapEvent")
        void mapEvent() {
            FieldChange.ContainerElementEvent e = ContainerEvents.mapEvent(
                FieldChange.ElementOperation.ADD, "key", null, null);
            assertThat(e.getContainerType()).isEqualTo(FieldChange.ContainerType.MAP);
            assertThat(e.getMapKey()).isEqualTo("key");
        }

        @Test
        @DisplayName("setEvent")
        void setEvent() {
            FieldChange.ContainerElementEvent e = ContainerEvents.setEvent(
                FieldChange.ElementOperation.REMOVE, "entity[1]", null, false);
            assertThat(e.getContainerType()).isEqualTo(FieldChange.ContainerType.SET);
        }

        @Test
        @DisplayName("arrayModify")
        void arrayModify() {
            FieldChange.ContainerElementEvent e = ContainerEvents.arrayModify(3);
            assertThat(e.getContainerType()).isEqualTo(FieldChange.ContainerType.ARRAY);
            assertThat(e.getIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("arrayAdd")
        void arrayAdd() {
            FieldChange.ContainerElementEvent e = ContainerEvents.arrayAdd(1);
            assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.ADD);
        }

        @Test
        @DisplayName("arrayRemove")
        void arrayRemove() {
            FieldChange.ContainerElementEvent e = ContainerEvents.arrayRemove(2);
            assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.REMOVE);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  StrategyResolver
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("StrategyResolver")
    class StrategyResolverTests {

        @Test
        @DisplayName("resolve null strategies")
        void resolve_nullStrategies_returnsNull() {
            StrategyResolver r = new StrategyResolver();
            assertThat(r.resolve(null, String.class)).isNull();
        }

        @Test
        @DisplayName("resolve empty strategies")
        void resolve_emptyStrategies_returnsNull() {
            StrategyResolver r = new StrategyResolver();
            assertThat(r.resolve(Collections.emptyList(), String.class)).isNull();
        }

        @Test
        @DisplayName("resolve null targetType")
        void resolve_nullTarget_returnsNull() {
            StrategyResolver r = new StrategyResolver();
            List<CompareStrategy<?>> strategies = List.of(new DateCompareStrategy());
            assertThat(r.resolve(strategies, null)).isNull();
        }

        @Test
        @DisplayName("resolve Date strategy")
        void resolve_dateStrategy() {
            StrategyResolver r = new StrategyResolver();
            List<CompareStrategy<?>> strategies = List.of(new DateCompareStrategy());
            CompareStrategy<?> resolved = r.resolve(strategies, Date.class);
            assertThat(resolved).isNotNull();
            assertThat(resolved.getName()).isEqualTo("DateCompare");
        }

        @Test
        @DisplayName("clearCache")
        void clearCache() {
            StrategyResolver r = new StrategyResolver();
            r.resolve(List.of(new DateCompareStrategy()), Date.class);
            r.clearCache();
            assertThat(r.getCacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("getCacheHitRate no Caffeine")
        void getCacheHitRate_noCaffeine() {
            StrategyResolver r = new StrategyResolver();
            assertThat(r.getCacheHitRate()).isEqualTo(-1.0);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CompareService
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareService")
    class CompareServiceTests {

        @Test
        @DisplayName("compare same object")
        void compare_sameObject_identical() {
            Object o = "test";
            CompareResult r = compareService.compare(o, o);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare null vs object")
        void compare_nullVsObject() {
            CompareResult r = compareService.compare(null, "x");
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with options")
        void compare_withOptions() {
            CompareResult r = compareService.compare("a", "b", CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare with report and similarity")
        void compare_withReportAndSimilarity() {
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .calculateSimilarity(true)
                .build();
            CompareResult r = compareService.compare("hello", "world", opts);
            assertThat(r).isNotNull();
            assertThat(r.getReport()).isNotNull();
        }

        @Test
        @DisplayName("compareBatch empty")
        void compareBatch_empty() {
            List<CompareResult> results = compareService.compareBatch(Collections.emptyList());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("compareBatch single")
        void compareBatch_single() {
            List<Pair<Object, Object>> pairs = List.of(Pair.of(1, 2));
            List<CompareResult> results = compareService.compareBatch(pairs);
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("compareBatch parallel threshold")
        void compareBatch_parallelThreshold() {
            List<Pair<Object, Object>> pairs = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                pairs.add(Pair.of(i, i + 1));
            }
            CompareOptions opts = CompareOptions.builder().parallelThreshold(10).build();
            List<CompareResult> results = compareService.compareBatch(pairs, opts);
            assertThat(results).hasSize(15);
        }

        @Test
        @DisplayName("compareThreeWay")
        void compareThreeWay() {
            Object base = Map.of("a", 1);
            Object left = Map.of("a", 2);
            Object right = Map.of("a", 3);
            MergeResult r = compareService.compareThreeWay(base, left, right);
            assertThat(r).isNotNull();
            assertThat(r.hasConflicts()).isTrue();
        }

        @Test
        @DisplayName("compareThreeWay no conflict")
        void compareThreeWay_noConflict() {
            Object base = Map.of("a", 1, "b", 2);
            Object left = Map.of("a", 2, "b", 2);
            Object right = Map.of("a", 1, "b", 3);
            MergeResult r = compareService.compareThreeWay(base, left, right);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("registerStrategy")
        void registerStrategy() {
            CompareStrategy<String> strategy = new CompareStrategy<>() {
                @Override
                public CompareResult compare(String a, String b, CompareOptions opts) {
                    return CompareResult.identical();
                }
                @Override
                public String getName() { return "always-identical"; }
                @Override
                public boolean supports(Class<?> type) { return String.class.equals(type); }
            };
            compareService.registerStrategy(String.class, strategy);
            CompareResult r = compareService.compare("x", "y");
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("createDefault")
        void createDefault() {
            CompareService svc = CompareService.createDefault(CompareOptions.DEFAULT);
            assertThat(svc).isNotNull();
            CompareResult r = svc.compare("a", "b");
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("createDefault with registry")
        void createDefault_withRegistry() {
            PropertyComparatorRegistry reg = new PropertyComparatorRegistry();
            CompareService svc = CompareService.createDefault(CompareOptions.DEFAULT, reg);
            assertThat(svc).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ThreeWayMergeService
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ThreeWayMergeService")
    class ThreeWayMergeServiceTests {

        @Test
        @DisplayName("merge")
        void merge() {
            ThreeWayMergeService svc = new ThreeWayMergeService(compareService);
            Object base = Map.of("x", 1);
            Object left = Map.of("x", 2);
            Object right = Map.of("x", 3);
            MergeResult r = svc.merge(base, left, right, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.hasConflicts()).isTrue();
        }

        @Test
        @DisplayName("merge with attemptAutoMerge")
        void merge_withAutoMerge() {
            ThreeWayMergeService svc = new ThreeWayMergeService(compareService);
            Object base = Map.of("a", 1, "b", 2);
            Object left = Map.of("a", 2, "b", 2);
            Object right = Map.of("a", 2, "b", 3);
            CompareOptions opts = CompareOptions.builder().attemptAutoMerge(true).build();
            MergeResult r = svc.merge(base, left, right, opts);
            assertThat(r).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  MapCompareStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MapCompareStrategy")
    class MapCompareStrategyTests {

        private final MapCompareStrategy strategy = new MapCompareStrategy();

        @Test
        @DisplayName("supports Map")
        void supports_map() {
            assertThat(strategy.supports(Map.class)).isTrue();
            assertThat(strategy.supports(HashMap.class)).isTrue();
        }

        @Test
        @DisplayName("getName")
        void getName() {
            assertThat(strategy.getName()).isEqualTo("MapCompare");
        }

        @Test
        @DisplayName("compare same map")
        void compare_sameMap_identical() {
            Map<String, Integer> m = new HashMap<>();
            m.put("a", 1);
            CompareResult r = strategy.compare(m, m, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare null map")
        void compare_nullMap() {
            Map<String, Integer> m = Map.of("a", 1);
            CompareResult r = strategy.compare(null, m, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare different maps")
        void compare_differentMaps() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 3);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("compare empty maps")
        void compare_emptyMaps() {
            CompareResult r = strategy.compare(Map.of(), Map.of(), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("generateDetailedChangeRecords")
        void generateDetailedChangeRecords() {
            Map<String, Integer> oldMap = Map.of("a", 1);
            Map<String, Integer> newMap = Map.of("a", 2, "b", 3);
            var records = strategy.generateDetailedChangeRecords(
                "obj", "field", oldMap, newMap, "s1", "t1");
            assertThat(records).isNotEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  SetCompareStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SetCompareStrategy")
    class SetCompareStrategyTests {

        private final SetCompareStrategy strategy = new SetCompareStrategy();

        @Test
        @DisplayName("supports Set")
        void supports_set() {
            assertThat(strategy.supports(Set.class)).isTrue();
            assertThat(strategy.supports(HashSet.class)).isTrue();
        }

        @Test
        @DisplayName("getName")
        void getName() {
            assertThat(strategy.getName()).isEqualTo("SetCompare");
        }

        @Test
        @DisplayName("compare same set")
        void compare_sameSet_identical() {
            Set<String> s = Set.of("a", "b");
            CompareResult r = strategy.compare(s, s, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare different sets")
        void compare_differentSets() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "c");
            CompareResult r = strategy.compare(s1, s2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("compare with similarity")
        void compare_withSimilarity() {
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(Set.of(1, 2), Set.of(1, 3), opts);
            assertThat(r.getSimilarity()).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ArrayCompareStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ArrayCompareStrategy")
    class ArrayCompareStrategyTests {

        private final ArrayCompareStrategy strategy = new ArrayCompareStrategy();

        @Test
        @DisplayName("supports array")
        void supports_array() {
            assertThat(strategy.supports(int[].class)).isTrue();
            assertThat(strategy.supports(String[].class)).isTrue();
        }

        @Test
        @DisplayName("supports non-array returns false")
        void supports_nonArray() {
            assertThat(strategy.supports(String.class)).isFalse();
        }

        @Test
        @DisplayName("getName")
        void getName() {
            assertThat(strategy.getName()).isEqualTo("ArrayCompare");
        }

        @Test
        @DisplayName("compare same array")
        void compare_sameArray_identical() {
            int[] arr = {1, 2, 3};
            CompareResult r = strategy.compare(arr, arr, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare different arrays")
        void compare_differentArrays() {
            int[] a1 = {1, 2, 3};
            int[] a2 = {1, 9, 3};
            CompareResult r = strategy.compare(a1, a2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).hasSize(1);
        }

        @Test
        @DisplayName("compare arrays different length")
        void compare_arraysDifferentLength() {
            String[] a1 = {"a", "b"};
            String[] a2 = {"a", "b", "c"};
            CompareResult r = strategy.compare(a1, a2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getAlgorithmUsed()).isEqualTo("ARRAY_SIMPLE");
        }

        @Test
        @DisplayName("compare non-array type diff")
        void compare_nonArray_typeDiff() {
            CompareResult r = strategy.compare("x", 1, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  DateCompareStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DateCompareStrategy")
    class DateCompareStrategyTests {

        private final DateCompareStrategy strategy = new DateCompareStrategy();

        @Test
        @DisplayName("supports Date")
        void supports_date() {
            assertThat(strategy.supports(Date.class)).isTrue();
        }

        @Test
        @DisplayName("compare same date")
        void compare_sameDate_identical() {
            Date d = new Date();
            CompareResult r = strategy.compare(d, d, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare different dates")
        void compare_differentDates() {
            Date d1 = new Date(0);
            Date d2 = new Date(5000);
            CompareResult r = strategy.compare(d1, d2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with similarity and report")
        void compare_withSimilarityAndReport() {
            Date d1 = new Date(0);
            Date d2 = new Date(1000);
            CompareOptions opts = CompareOptions.builder()
                .calculateSimilarity(true)
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(d1, d2, opts);
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getReport()).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CollectionCompareStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CollectionCompareStrategy")
    class CollectionCompareStrategyTests {

        private final CollectionCompareStrategy strategy = new CollectionCompareStrategy();

        @Test
        @DisplayName("supports Collection")
        void supports_collection() {
            assertThat(strategy.supports(List.class)).isTrue();
            assertThat(strategy.supports(Set.class)).isTrue();
        }

        @Test
        @DisplayName("compare different collections")
        void compare_differentCollections() {
            List<String> c1 = List.of("a", "b");
            List<String> c2 = List.of("a", "c");
            CompareResult r = strategy.compare(c1, c2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with similarity")
        void compare_withSimilarity() {
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(List.of(1, 2), List.of(1, 2), opts);
            assertThat(r.isIdentical()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  EnhancedDateCompareStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EnhancedDateCompareStrategy")
    class EnhancedDateCompareStrategyTests {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareDates null both")
        void compareDates_bothNull() {
            assertThat(strategy.compareDates(null, null)).isTrue();
        }

        @Test
        @DisplayName("compareDates one null")
        void compareDates_oneNull() {
            assertThat(strategy.compareDates(new Date(), null)).isFalse();
        }

        @Test
        @DisplayName("compareDates same")
        void compareDates_same() {
            Date d = new Date();
            assertThat(strategy.compareDates(d, d)).isTrue();
        }

        @Test
        @DisplayName("compareDates with tolerance")
        void compareDates_withTolerance() {
            Date d1 = new Date(0);
            Date d2 = new Date(500);
            assertThat(strategy.compareDates(d1, d2, 1000)).isTrue();
        }

        @Test
        @DisplayName("compareInstants")
        void compareInstants() {
            Instant i = Instant.now();
            assertThat(strategy.compareInstants(i, i, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDateTimes")
        void compareLocalDateTimes() {
            LocalDateTime t = LocalDateTime.now();
            assertThat(strategy.compareLocalDateTimes(t, t, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDates")
        void compareLocalDates() {
            LocalDate d = LocalDate.now();
            assertThat(strategy.compareLocalDates(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDurations")
        void compareDurations() {
            Duration d = Duration.ofSeconds(10);
            assertThat(strategy.compareDurations(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("comparePeriods")
        void comparePeriods() {
            Period p = Period.ofDays(1);
            assertThat(strategy.comparePeriods(p, p)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Date")
        void compareTemporal_date() {
            Date d = new Date();
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("isTemporalType")
        void isTemporalType() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(new Date())).isTrue();
            assertThat(EnhancedDateCompareStrategy.isTemporalType(LocalDateTime.now())).isTrue();
            assertThat(EnhancedDateCompareStrategy.isTemporalType("x")).isFalse();
        }

        @Test
        @DisplayName("needsTemporalCompare")
        void needsTemporalCompare() {
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(new Date(), new Date())).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ComparePerfProperties
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ComparePerfProperties")
    class ComparePerfPropertiesTests {

        @Test
        @DisplayName("getters and setters")
        void gettersSetters() {
            ComparePerfProperties p = new ComparePerfProperties();
            p.setTimeoutMs(3000);
            p.setMaxElements(5000);
            p.setStrictMode(true);
            p.setDegradationStrategy("SKIP");
            assertThat(p.getTimeoutMs()).isEqualTo(3000);
            assertThat(p.getMaxElements()).isEqualTo(5000);
            assertThat(p.isStrictMode()).isTrue();
            assertThat(p.getDegradationStrategy()).isEqualTo("SKIP");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  PropertyComparisonException
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PropertyComparisonException")
    class PropertyComparisonExceptionTests {

        @Test
        @DisplayName("message constructor")
        void messageConstructor() {
            PropertyComparisonException e = new PropertyComparisonException("msg");
            assertThat(e.getMessage()).isEqualTo("msg");
        }

        @Test
        @DisplayName("message and cause constructor")
        void messageCauseConstructor() {
            RuntimeException cause = new RuntimeException("cause");
            PropertyComparisonException e = new PropertyComparisonException("msg", cause);
            assertThat(e.getCause()).isSameAs(cause);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CompareService compare with various types
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareService type coverage")
    class CompareServiceTypeCoverageTests {

        @Test
        @DisplayName("compare String")
        void compare_string() {
            CompareResult r = compareService.compare("a", "b");
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare Integer")
        void compare_integer() {
            CompareResult r = compareService.compare(1, 2);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare BigDecimal")
        void compare_bigDecimal() {
            CompareResult r = compareService.compare(BigDecimal.ONE, BigDecimal.TEN);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare Date")
        void compare_date() {
            CompareResult r = compareService.compare(new Date(0), new Date(1000));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare List")
        void compare_list() {
            CompareResult r = compareService.compare(List.of(1, 2), List.of(1, 3));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare Set")
        void compare_set() {
            CompareResult r = compareService.compare(Set.of(1, 2), Set.of(1, 3));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare Map")
        void compare_map() {
            CompareResult r = compareService.compare(Map.of("a", 1), Map.of("a", 2));
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare array")
        void compare_array() {
            CompareResult r = compareService.compare(new int[]{1, 2}, new int[]{1, 3});
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare POJO with deep compare")
        void compare_pojoDeep() {
            SimplePojo a = new SimplePojo("x", 10);
            SimplePojo b = new SimplePojo("y", 20);
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).maxDepth(5).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }
    }

    static class SimplePojo {
        String name;
        int value;

        SimplePojo(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public int getValue() { return value; }
    }
}
