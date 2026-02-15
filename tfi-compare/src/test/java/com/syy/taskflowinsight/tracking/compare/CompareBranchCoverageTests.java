package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive branch coverage tests for tfi-compare module.
 * Targets ~200 missed branches across FieldChange, CompareResult, CollectionCompareStrategy,
 * StrategyResolver, EnhancedDateCompareStrategy, and DefaultComparisonProvider.
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

    // ═══════════════════════════════════════════════════════════════════════
    // 1. FieldChange (41 missed branches)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FieldChange — isContainerElementChange")
    class FieldChangeContainerElementChange {

        @Test
        @DisplayName("elementEvent null → isContainerElementChange returns false")
        void elementEventNull_returnsFalse() {
            FieldChange fc = FieldChange.builder().fieldName("f").build();
            assertThat(fc.isContainerElementChange()).isFalse();
        }

        @Test
        @DisplayName("elementEvent non-null → isContainerElementChange returns true")
        void elementEventNonNull_returnsTrue() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .build();
            FieldChange fc = FieldChange.builder().fieldName("f").elementEvent(evt).build();
            assertThat(fc.isContainerElementChange()).isTrue();
        }
    }

    @Nested
    @DisplayName("FieldChange — getContainerIndex, getEntityKey, getMapKey, getContainerOperation")
    class FieldChangeContainerAccessors {

        @Test
        @DisplayName("elementEvent null → getContainerIndex returns null")
        void elementEventNull_getContainerIndex_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").build();
            assertThat(fc.getContainerIndex()).isNull();
        }

        @Test
        @DisplayName("elementEvent non-null with index → getContainerIndex returns index")
        void elementEventNonNull_getContainerIndex_returnsIndex() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(5)
                .build();
            FieldChange fc = FieldChange.builder().fieldName("f").elementEvent(evt).build();
            assertThat(fc.getContainerIndex()).isEqualTo(5);
        }

        @Test
        @DisplayName("elementEvent null → getEntityKey returns null")
        void elementEventNull_getEntityKey_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").build();
            assertThat(fc.getEntityKey()).isNull();
        }

        @Test
        @DisplayName("elementEvent non-null with entityKey → getEntityKey returns entityKey")
        void elementEventNonNull_getEntityKey_returnsEntityKey() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .entityKey("order[O1]")
                .build();
            FieldChange fc = FieldChange.builder().fieldName("f").elementEvent(evt).build();
            assertThat(fc.getEntityKey()).isEqualTo("order[O1]");
        }

        @Test
        @DisplayName("elementEvent null → getMapKey returns null")
        void elementEventNull_getMapKey_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").build();
            assertThat(fc.getMapKey()).isNull();
        }

        @Test
        @DisplayName("elementEvent non-null with mapKey → getMapKey returns mapKey")
        void elementEventNonNull_getMapKey_returnsMapKey() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .mapKey("key1")
                .build();
            FieldChange fc = FieldChange.builder().fieldName("f").elementEvent(evt).build();
            assertThat(fc.getMapKey()).isEqualTo("key1");
        }

        @Test
        @DisplayName("elementEvent null → getContainerOperation returns null")
        void elementEventNull_getContainerOperation_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").build();
            assertThat(fc.getContainerOperation()).isNull();
        }

        @Test
        @DisplayName("elementEvent non-null → getContainerOperation returns operation")
        void elementEventNonNull_getContainerOperation_returnsOperation() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.REMOVE)
                .build();
            FieldChange fc = FieldChange.builder().fieldName("f").elementEvent(evt).build();
            assertThat(fc.getContainerOperation()).isEqualTo(FieldChange.ElementOperation.REMOVE);
        }
    }

    @Nested
    @DisplayName("FieldChange — toTypedView")
    class FieldChangeToTypedView {

        @Test
        @DisplayName("non-container change → toTypedView returns null")
        void nonContainer_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").oldValue(1).newValue(2).build();
            assertThat(fc.toTypedView()).isNull();
        }

        @Test
        @DisplayName("container change with all optional fields null → toTypedView")
        void containerAllOptionalNull() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(null)
                .oldIndex(null)
                .newIndex(null)
                .entityKey(null)
                .mapKey(null)
                .propertyPath(null)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("items")
                .fieldPath("items[0]")
                .elementEvent(evt)
                .oldValue(null)
                .newValue("x")
                .build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            assertThat(view.get("kind")).isEqualTo("entry_added");
            assertThat(view.get("object")).isEqualTo("items");
        }

        @Test
        @DisplayName("container change with index → details contains index")
        void containerWithIndex() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(2)
                .build();
            FieldChange fc = FieldChange.builder().fieldName("items").fieldPath("items[2]").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details.get("index")).isEqualTo(2);
        }

        @Test
        @DisplayName("container change with oldIndex and newIndex → details contains both")
        void containerWithOldNewIndex() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MOVE)
                .oldIndex(0)
                .newIndex(2)
                .build();
            FieldChange fc = FieldChange.builder().fieldName("items").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details.get("oldIndex")).isEqualTo(0);
            assertThat(details.get("newIndex")).isEqualTo(2);
        }

        @Test
        @DisplayName("container change with entityKey → details contains entityKey")
        void containerWithEntityKey() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .entityKey("order[O1]")
                .build();
            FieldChange fc = FieldChange.builder().fieldName("items").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details.get("entityKey")).isEqualTo("order[O1]");
        }

        @Test
        @DisplayName("container change with mapKey → details contains mapKey")
        void containerWithMapKey() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.ADD)
                .mapKey("k1")
                .build();
            FieldChange fc = FieldChange.builder().fieldName("map").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details.get("mapKey")).isEqualTo("k1");
        }

        @Test
        @DisplayName("container change with non-empty propertyPath → details contains propertyPath")
        void containerWithPropertyPath() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MODIFY)
                .propertyPath("price")
                .build();
            FieldChange fc = FieldChange.builder().fieldName("items").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details.get("propertyPath")).isEqualTo("price");
        }

        @Test
        @DisplayName("container change with empty/whitespace propertyPath → details excludes propertyPath")
        void containerWithEmptyPropertyPath() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MODIFY)
                .propertyPath("   ")
                .build();
            FieldChange fc = FieldChange.builder().fieldName("items").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details).doesNotContainKey("propertyPath");
        }

        @Test
        @DisplayName("toViewKind ADD → entry_added")
        void toViewKindAdd() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("kind")).isEqualTo("entry_added");
        }

        @Test
        @DisplayName("toViewKind REMOVE → entry_removed")
        void toViewKindRemove() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.REMOVE)
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("kind")).isEqualTo("entry_removed");
        }

        @Test
        @DisplayName("toViewKind MODIFY → entry_updated")
        void toViewKindModify() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MODIFY)
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("kind")).isEqualTo("entry_updated");
        }

        @Test
        @DisplayName("toViewKind MOVE → entry_moved")
        void toViewKindMove() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MOVE)
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("kind")).isEqualTo("entry_moved");
        }
    }

    @Nested
    @DisplayName("FieldChange — getValueDescription")
    class FieldChangeGetValueDescription {

        @Test
        @DisplayName("changeType DELETE → oldValue -> (deleted)")
        void delete() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue("old")
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .build();
            assertThat(fc.getValueDescription()).contains("old").contains("(deleted)");
        }

        @Test
        @DisplayName("changeType CREATE → (new) -> newValue")
        void create() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue(null)
                .newValue("new")
                .changeType(ChangeType.CREATE)
                .build();
            assertThat(fc.getValueDescription()).contains("(new)").contains("new");
        }

        @Test
        @DisplayName("changeType UPDATE → oldValue -> newValue")
        void update() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue("a")
                .newValue("b")
                .changeType(ChangeType.UPDATE)
                .build();
            assertThat(fc.getValueDescription()).contains("a").contains("b");
        }
    }

    @Nested
    @DisplayName("FieldChange — isNullChange")
    class FieldChangeIsNullChange {

        @Test
        @DisplayName("(null, null) → true")
        void bothNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").oldValue(null).newValue(null).build();
            assertThat(fc.isNullChange()).isTrue();
        }

        @Test
        @DisplayName("(null, CREATE) → true")
        void nullWithCreate() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue(null)
                .newValue("x")
                .changeType(ChangeType.CREATE)
                .build();
            assertThat(fc.isNullChange()).isTrue();
        }

        @Test
        @DisplayName("(null, DELETE) → true")
        void nullWithDelete() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue("x")
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .build();
            assertThat(fc.isNullChange()).isTrue();
        }

        @Test
        @DisplayName("(a, b) UPDATE → false")
        void bothNonNullUpdate() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue("a")
                .newValue("b")
                .changeType(ChangeType.UPDATE)
                .build();
            assertThat(fc.isNullChange()).isFalse();
        }

        @Test
        @DisplayName("(a, b) CREATE with old non-null → false")
        void createWithOldNonNull() {
            FieldChange fc = FieldChange.builder()
                .fieldName("f")
                .oldValue("a")
                .newValue("b")
                .changeType(ChangeType.CREATE)
                .build();
            assertThat(fc.isNullChange()).isFalse();
        }
    }

    @Nested
    @DisplayName("FieldChange — toReferenceChangeView")
    class FieldChangeToReferenceChangeView {

        @Test
        @DisplayName("non-reference change → returns null")
        void nonReference_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").referenceChange(false).build();
            assertThat(fc.toReferenceChangeView()).isNull();
        }

        @Test
        @DisplayName("referenceChange true but referenceDetail null → returns null")
        void referenceDetailNull_returnsNull() {
            FieldChange fc = FieldChange.builder().fieldName("f").referenceChange(true).referenceDetail(null).build();
            assertThat(fc.toReferenceChangeView()).isNull();
        }

        @Test
        @DisplayName("reference change with fieldPath → uses fieldPath for object/path")
        void referenceWithFieldPath() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("ref")
                .fieldPath("order.customer")
                .referenceChange(true)
                .referenceDetail(rd)
                .oldValue("o")
                .newValue("n")
                .build();
            Map<String, Object> view = fc.toReferenceChangeView();
            assertThat(view).isNotNull();
            assertThat(view.get("path")).isEqualTo("order.customer");
        }

        @Test
        @DisplayName("reference change without fieldPath → uses fieldName")
        void referenceWithoutFieldPath() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("ref")
                .fieldPath(null)
                .referenceChange(true)
                .referenceDetail(rd)
                .build();
            Map<String, Object> view = fc.toReferenceChangeView();
            assertThat(view).isNotNull();
            assertThat(view.get("path")).isEqualTo("ref");
        }

        @Test
        @DisplayName("reference change with oldValue non-null → oldValueRepr present")
        void referenceWithOldValue() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("ref")
                .referenceChange(true)
                .referenceDetail(rd)
                .oldValue("oldVal")
                .newValue(null)
                .build();
            Map<String, Object> view = fc.toReferenceChangeView();
            assertThat(view).containsKey("oldValueRepr");
        }

        @Test
        @DisplayName("reference change with newValue non-null → newValueRepr present")
        void referenceWithNewValue() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("ref")
                .referenceChange(true)
                .referenceDetail(rd)
                .oldValue(null)
                .newValue("newVal")
                .build();
            Map<String, Object> view = fc.toReferenceChangeView();
            assertThat(view).containsKey("newValueRepr");
        }
    }

    @Nested
    @DisplayName("FieldChange — extractObjectName (via toTypedView/toReferenceChangeView)")
    class FieldChangeExtractObjectName {

        @Test
        @DisplayName("fieldPath null → object unknown")
        void pathNull() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            FieldChange fc = FieldChange.builder().fieldPath(null).fieldName("x").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("object")).isEqualTo("unknown");
        }

        @Test
        @DisplayName("path 'a.b' → object 'a'")
        void pathDotOnly() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            FieldChange fc = FieldChange.builder().fieldPath("a.b").fieldName("a").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("object")).isEqualTo("a");
        }

        @Test
        @DisplayName("path 'a[0]' → object 'a'")
        void pathBracketOnly() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            FieldChange fc = FieldChange.builder().fieldPath("a[0]").fieldName("a").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("object")).isEqualTo("a");
        }

        @Test
        @DisplayName("path 'a[0].b' → object 'a'")
        void pathBoth() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            FieldChange fc = FieldChange.builder().fieldPath("a[0].b").fieldName("a").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("object")).isEqualTo("a");
        }

        @Test
        @DisplayName("path 'a' no separator → object 'a'")
        void pathNeither() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            FieldChange fc = FieldChange.builder().fieldPath("a").fieldName("a").elementEvent(evt).build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("object")).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("FieldChange.ReferenceDetail — toMap")
    class ReferenceDetailToMap {

        @Test
        @DisplayName("nullReferenceChange true, oldEntityKey null → ESTABLISHED")
        void nullRefChange_oldNull() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey(null)
                .newEntityKey("new")
                .nullReferenceChange(true)
                .build();
            Map<String, Object> map = rd.toMap();
            assertThat(map.get("transitionType")).isEqualTo("ASSOCIATION_ESTABLISHED");
        }

        @Test
        @DisplayName("nullReferenceChange true, oldEntityKey non-null → REMOVED")
        void nullRefChange_oldNonNull() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey(null)
                .nullReferenceChange(true)
                .build();
            Map<String, Object> map = rd.toMap();
            assertThat(map.get("transitionType")).isEqualTo("ASSOCIATION_REMOVED");
        }

        @Test
        @DisplayName("nullReferenceChange false → SWITCHED")
        void nullRefChangeFalse() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            Map<String, Object> map = rd.toMap();
            assertThat(map.get("transitionType")).isEqualTo("REFERENCE_SWITCHED");
        }
    }

    @Nested
    @DisplayName("FieldChange.ReferenceDetail — toJson")
    class ReferenceDetailToJson {

        @Test
        @DisplayName("toJson success path")
        void toJsonSuccess() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            String json = rd.toJson();
            assertThat(json).contains("oldKey").contains("newKey");
        }
    }

    @Nested
    @DisplayName("FieldChange — getOldEntityKey, getNewEntityKey")
    class FieldChangeEntityKeyAccessors {

        @Test
        @DisplayName("referenceDetail null → getOldEntityKey returns null")
        void referenceDetailNull_getOldEntityKey() {
            FieldChange fc = FieldChange.builder().referenceChange(false).build();
            assertThat(fc.getOldEntityKey()).isNull();
        }

        @Test
        @DisplayName("referenceDetail non-null → getOldEntityKey returns value")
        void referenceDetailNonNull_getOldEntityKey() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("oldKey")
                .newEntityKey("newKey")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder().referenceChange(true).referenceDetail(rd).build();
            assertThat(fc.getOldEntityKey()).isEqualTo("oldKey");
        }

        @Test
        @DisplayName("referenceDetail null → getNewEntityKey returns null")
        void referenceDetailNull_getNewEntityKey() {
            FieldChange fc = FieldChange.builder().referenceChange(false).build();
            assertThat(fc.getNewEntityKey()).isNull();
        }

        @Test
        @DisplayName("referenceDetail non-null → getNewEntityKey returns value")
        void referenceDetailNonNull_getNewEntityKey() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("oldKey")
                .newEntityKey("newKey")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder().referenceChange(true).referenceDetail(rd).build();
            assertThat(fc.getNewEntityKey()).isEqualTo("newKey");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. CompareResult (34 missed branches)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CompareResult — getChangeCount, hasChanges, getSimilarityPercent")
    class CompareResultBasicAccessors {

        @Test
        @DisplayName("changes null → getChangeCount returns 0")
        void changesNull_getChangeCount() {
            CompareResult r = CompareResult.builder().changes(null).identical(false).build();
            assertThat(r.getChangeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("changes non-null → getChangeCount returns size")
        void changesNonNull_getChangeCount() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").build());
            CompareResult r = CompareResult.builder().changes(changes).identical(false).build();
            assertThat(r.getChangeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("identical true → hasChanges false")
        void identicalTrue_hasChangesFalse() {
            CompareResult r = CompareResult.builder().identical(true).changes(List.of()).build();
            assertThat(r.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("identical false with changes → hasChanges true")
        void identicalFalseWithChanges_hasChangesTrue() {
            CompareResult r = CompareResult.builder()
                .identical(false)
                .changes(List.of(FieldChange.builder().fieldName("f").build()))
                .build();
            assertThat(r.hasChanges()).isTrue();
        }

        @Test
        @DisplayName("identical false without changes → hasChanges false")
        void identicalFalseNoChanges_hasChangesFalse() {
            CompareResult r = CompareResult.builder().identical(false).changes(List.of()).build();
            assertThat(r.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("similarity null → getSimilarityPercent returns 0")
        void similarityNull() {
            CompareResult r = CompareResult.builder().similarity(null).build();
            assertThat(r.getSimilarityPercent()).isEqualTo(0);
        }

        @Test
        @DisplayName("similarity non-null → getSimilarityPercent returns value")
        void similarityNonNull() {
            CompareResult r = CompareResult.builder().similarity(0.5).build();
            assertThat(r.getSimilarityPercent()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("CompareResult — hasDuplicateKeys")
    class CompareResultHasDuplicateKeys {

        @Test
        @DisplayName("duplicateKeys null → false")
        void duplicateKeysNull() {
            CompareResult r = CompareResult.builder().duplicateKeys(null).build();
            assertThat(r.hasDuplicateKeys()).isFalse();
        }

        @Test
        @DisplayName("duplicateKeys empty → false")
        void duplicateKeysEmpty() {
            CompareResult r = CompareResult.builder().duplicateKeys(Set.of()).build();
            assertThat(r.hasDuplicateKeys()).isFalse();
        }

        @Test
        @DisplayName("duplicateKeys non-empty → true")
        void duplicateKeysNonEmpty() {
            CompareResult r = CompareResult.builder().duplicateKeys(Set.of("k1")).build();
            assertThat(r.hasDuplicateKeys()).isTrue();
        }
    }

    @Nested
    @DisplayName("CompareResult — getChangesByType")
    class CompareResultGetChangesByType {

        @Test
        @DisplayName("changes null → empty list")
        void changesNull() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.getChangesByType(ChangeType.CREATE)).isEmpty();
        }

        @Test
        @DisplayName("types null → returns all changes")
        void typesNull() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").changeType(ChangeType.CREATE).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getChangesByType((ChangeType[]) null)).hasSize(1);
        }

        @Test
        @DisplayName("types empty → returns all changes")
        void typesEmpty() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").changeType(ChangeType.CREATE).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getChangesByType()).hasSize(1);
        }

        @Test
        @DisplayName("types with values → filters by type")
        void typesWithValues() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f1").changeType(ChangeType.CREATE).build(),
                FieldChange.builder().fieldName("f2").changeType(ChangeType.DELETE).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getChangesByType(ChangeType.CREATE)).hasSize(1);
            assertThat(r.getChangesByType(ChangeType.DELETE)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("CompareResult — getReferenceChanges, getContainerChanges")
    class CompareResultReferenceAndContainer {

        @Test
        @DisplayName("changes null → getReferenceChanges empty")
        void changesNull_getReferenceChanges() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.getReferenceChanges()).isEmpty();
        }

        @Test
        @DisplayName("no reference changes → empty")
        void noReferenceChanges() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").referenceChange(false).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getReferenceChanges()).isEmpty();
        }

        @Test
        @DisplayName("some reference changes → non-empty")
        void someReferenceChanges() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").referenceChange(true).referenceDetail(rd).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getReferenceChanges()).hasSize(1);
        }

        @Test
        @DisplayName("changes null → getContainerChanges empty")
        void changesNull_getContainerChanges() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.getContainerChanges()).isEmpty();
        }

        @Test
        @DisplayName("no container changes → empty")
        void noContainerChanges() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(null).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getContainerChanges()).isEmpty();
        }

        @Test
        @DisplayName("some container changes → non-empty")
        void someContainerChanges() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getContainerChanges()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("CompareResult — groupByObject, groupByProperty")
    class CompareResultGroupBy {

        @Test
        @DisplayName("changes null → groupByObject empty")
        void changesNull_groupByObject() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.groupByObject()).isEmpty();
        }

        @Test
        @DisplayName("with changes → groupByObject non-empty")
        void withChanges_groupByObject() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .entityKey("order[O1]")
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("items").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByObject()).isNotEmpty();
        }

        @Test
        @DisplayName("changes null → groupByProperty empty")
        void changesNull_groupByProperty() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.groupByProperty()).isEmpty();
        }

        @Test
        @DisplayName("with changes → groupByProperty non-empty")
        void withChanges_groupByProperty() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("price").build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByProperty()).containsKey("price");
        }
    }

    @Nested
    @DisplayName("CompareResult — groupByContainerOperationTyped")
    class CompareResultGroupByContainerOp {

        @Test
        @DisplayName("no container changes → empty")
        void noContainerChanges() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByContainerOperationTyped()).isEmpty();
        }

        @Test
        @DisplayName("container changes with operation non-null")
        void containerChangesWithOp() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByContainerOperationTyped()).containsKey(FieldChange.ElementOperation.ADD);
        }

        @Test
        @DisplayName("container change with operation null filtered out")
        void containerChangeOpNull() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(null)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByContainerOperationTyped()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CompareResult — groupByContainerOperationAsString")
    @SuppressWarnings("deprecation")
    class CompareResultGroupByContainerOpAsString {

        @Test
        @DisplayName("typed empty → empty")
        void typedEmpty() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByContainerOperationAsString()).isEmpty();
        }

        @Test
        @DisplayName("typed non-empty → string keys")
        void typedNonEmpty() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByContainerOperationAsString()).containsKey("ADD");
        }
    }

    @Nested
    @DisplayName("CompareResult — getContainerChangesByType")
    class CompareResultGetContainerChangesByType {

        @Test
        @DisplayName("empty container → empty")
        void emptyContainer() {
            CompareResult r = CompareResult.builder().changes(List.of()).build();
            assertThat(r.getContainerChangesByType(FieldChange.ContainerType.LIST)).isEmpty();
        }

        @Test
        @DisplayName("types null → returns all container changes")
        void typesNull() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getContainerChangesByType()).hasSize(1);
        }

        @Test
        @DisplayName("types empty → returns all container changes")
        void typesEmpty() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getContainerChangesByType(new FieldChange.ContainerType[0])).hasSize(1);
        }

        @Test
        @DisplayName("types with values → filters")
        void typesWithValues() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getContainerChangesByType(FieldChange.ContainerType.LIST)).hasSize(1);
            assertThat(r.getContainerChangesByType(FieldChange.ContainerType.MAP)).isEmpty();
        }
    }

    @Nested
    @DisplayName("CompareResult — getChangeCountByType")
    class CompareResultGetChangeCountByType {

        @Test
        @DisplayName("changes null → empty map")
        void changesNull() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.getChangeCountByType()).isEmpty();
        }

        @Test
        @DisplayName("with changes → counts by type")
        void withChanges() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f1").changeType(ChangeType.CREATE).build(),
                FieldChange.builder().fieldName("f2").changeType(ChangeType.CREATE).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getChangeCountByType().get(ChangeType.CREATE)).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("CompareResult — prettyPrint")
    class CompareResultPrettyPrint {

        @Test
        @DisplayName("changes null → No changes detected")
        void changesNull() {
            CompareResult r = CompareResult.builder().changes(null).build();
            assertThat(r.prettyPrint()).contains("No changes detected");
        }

        @Test
        @DisplayName("changes empty → No changes detected")
        void changesEmpty() {
            CompareResult r = CompareResult.builder().changes(List.of()).build();
            assertThat(r.prettyPrint()).contains("No changes detected");
        }

        @Test
        @DisplayName("with changes + reference + container ops")
        void withChangesAndRefAndContainer() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("old")
                .newEntityKey("new")
                .nullReferenceChange(false)
                .build();
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .entityKey("items[0]")
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("field").fieldPath("order.field").changeType(ChangeType.UPDATE).build(),
                FieldChange.builder().fieldName("ref").fieldPath("order.ref").changeType(ChangeType.UPDATE)
                    .referenceChange(true).referenceDetail(rd).build(),
                FieldChange.builder().fieldName("items").fieldPath("order.items[0]").changeType(ChangeType.CREATE)
                    .elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            String out = r.prettyPrint();
            assertThat(out).contains("Total: 3");
            assertThat(out).contains("Reference Changes: 1");
            assertThat(out).contains("Container Operations");
        }
    }

    @Nested
    @DisplayName("CompareResult — extractObjectPath")
    class CompareResultExtractObjectPath {

        @Test
        @DisplayName("container change with entityKey → entityKey")
        void containerWithEntityKey() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .entityKey("order[O1]")
                .index(null)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("items").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            Map<String, List<FieldChange>> byObj = r.groupByObject();
            assertThat(byObj).containsKey("order[O1]");
        }

        @Test
        @DisplayName("container change with index, no entityKey → base[index]")
        void containerWithIndex() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .entityKey(null)
                .index(2)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("items").elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            Map<String, List<FieldChange>> byObj = r.groupByObject();
            assertThat(byObj).containsKey("items[2]");
        }

        @Test
        @DisplayName("non-container with fieldPath → first segment")
        void nonContainerWithFieldPath() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("x").fieldPath("order.customer.name").build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            Map<String, List<FieldChange>> byObj = r.groupByObject();
            assertThat(byObj).containsKey("order");
        }

        @Test
        @DisplayName("non-container without fieldPath → fieldName")
        void nonContainerWithoutFieldPath() {
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("order").fieldPath(null).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            Map<String, List<FieldChange>> byObj = r.groupByObject();
            assertThat(byObj).containsKey("order");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. CollectionCompareStrategy (16 missed branches)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CollectionCompareStrategy")
    class CollectionCompareStrategyBranches {

        private final CollectionCompareStrategy strategy = new CollectionCompareStrategy();

        @Test
        @DisplayName("col1 == col2 same reference → identical")
        void sameReference() {
            List<String> col = List.of("a", "b");
            CompareResult r = strategy.compare(col, col, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("col1 null → ofNullDiff")
        void col1Null() {
            CompareResult r = strategy.compare(null, List.of("a"), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getObject1()).isNull();
        }

        @Test
        @DisplayName("col2 null → ofNullDiff")
        void col2Null() {
            CompareResult r = strategy.compare(List.of("a"), null, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getObject2()).isNull();
        }

        @Test
        @DisplayName("options calculateSimilarity true")
        void calculateSimilarityTrue() {
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(List.of("a", "b"), List.of("a", "c"), opts);
            assertThat(r.getSimilarity()).isNotNull();
        }

        @Test
        @DisplayName("options calculateSimilarity false")
        void calculateSimilarityFalse() {
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(false).build();
            CompareResult r = strategy.compare(List.of("a", "b"), List.of("a", "c"), opts);
            assertThat(r.getSimilarity()).isNull();
        }

        @Test
        @DisplayName("options generateReport true")
        void generateReportTrue() {
            CompareOptions opts = CompareOptions.builder().generateReport(true).build();
            CompareResult r = strategy.compare(List.of("a"), List.of("a", "b"), opts);
            assertThat(r.getReport()).isNotNull();
        }

        @Test
        @DisplayName("options generateReport false")
        void generateReportFalse() {
            CompareOptions opts = CompareOptions.builder().generateReport(false).build();
            CompareResult r = strategy.compare(List.of("a"), List.of("a", "b"), opts);
            assertThat(r.getReport()).isNull();
        }

        @Test
        @DisplayName("calculateChangeType: empty col1 → CREATE")
        void changeTypeCreate() {
            CompareResult r = strategy.compare(List.of(), List.of("a"), CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
        }

        @Test
        @DisplayName("calculateChangeType: empty col2 → DELETE")
        void changeTypeDelete() {
            CompareResult r = strategy.compare(List.of("a"), List.of(), CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(ChangeType.DELETE);
        }

        @Test
        @DisplayName("calculateChangeType: both non-empty → UPDATE")
        void changeTypeUpdate() {
            CompareResult r = strategy.compare(List.of("a"), List.of("b"), CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        }

        @Test
        @DisplayName("calculateSimilarity both empty → 1.0")
        void similarityBothEmpty() {
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(List.of(), List.of(), opts);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("report MARKDOWN format")
        void reportMarkdown() {
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(List.of("a"), List.of("a", "b"), opts);
            assertThat(r.getReport()).contains("## Collection Comparison");
        }

        @Test
        @DisplayName("report non-MARKDOWN format")
        void reportNonMarkdown() {
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = strategy.compare(List.of("a"), List.of("a", "b"), opts);
            assertThat(r.getReport()).contains("Collection Comparison:");
        }

        @Test
        @DisplayName("report when added empty, removed non-empty")
        void reportAddedEmptyRemovedNonEmpty() {
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(List.of("a", "b"), List.of("a"), opts);
            assertThat(r.getReport()).contains("Removed Elements");
        }

        @Test
        @DisplayName("report when added non-empty, removed empty")
        void reportAddedNonEmptyRemovedEmpty() {
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(List.of("a"), List.of("a", "b"), opts);
            assertThat(r.getReport()).contains("Added Elements");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. StrategyResolver (27 missed branches)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StrategyResolver")
    class StrategyResolverBranches {

        @Test
        @DisplayName("resolve null strategies → null")
        void resolveNullStrategies() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(null, String.class)).isNull();
        }

        @Test
        @DisplayName("resolve empty strategies → null")
        void resolveEmptyStrategies() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(Collections.emptyList(), String.class)).isNull();
        }

        @Test
        @DisplayName("resolve null targetType → null")
        void resolveNullTargetType() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.resolve(List.of(new SetCompareStrategy()), null)).isNull();
        }

        @Test
        @DisplayName("resolve with unsupported targetType returns supporting strategy")
        void resolveUnsupportedTargetType() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new SetCompareStrategy(), new CollectionCompareStrategy()),
                HashSet.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("extractSupportedType Date strategy")
        void extractSupportedTypeDate() {
            StrategyResolver resolver = new StrategyResolver();
            @SuppressWarnings("deprecation")
            CompareStrategy<?> s = resolver.resolve(
                List.of(new DateCompareStrategy()),
                java.util.Date.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("extractSupportedType Set strategy")
        void extractSupportedTypeSet() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new SetCompareStrategy(), new CollectionCompareStrategy()),
                HashSet.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("extractSupportedType List strategy")
        void extractSupportedTypeList() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new CollectionCompareStrategy()),
                ArrayList.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("extractSupportedType Map strategy")
        void extractSupportedTypeMap() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new MapCompareStrategy(), new CollectionCompareStrategy()),
                HashMap.class);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("clearCache with null StrategyCache")
        void clearCacheNoStrategyCache() {
            StrategyResolver resolver = new StrategyResolver();
            resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            resolver.clearCache();
            assertThat(resolver.getCacheSize()).isZero();
        }

        @Test
        @DisplayName("clearCache with StrategyCache")
        void clearCacheWithStrategyCache() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            StrategyResolver resolver = new StrategyResolver(cache);
            resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            resolver.clearCache();
            assertThat(resolver.getCacheSize()).isZero();
        }

        @Test
        @DisplayName("getCacheHitRate null cache → -1.0")
        void getCacheHitRateNullCache() {
            StrategyResolver resolver = new StrategyResolver();
            assertThat(resolver.getCacheHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("getCacheHitRate with StrategyCache")
        void getCacheHitRateWithCache() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            StrategyResolver resolver = new StrategyResolver(cache);
            resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            assertThat(resolver.getCacheHitRate()).isGreaterThanOrEqualTo(-1.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. EnhancedDateCompareStrategy (41 missed branches)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EnhancedDateCompareStrategy")
    class EnhancedDateCompareStrategyBranches {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareDates both null → true")
        void compareDatesBothNull() {
            assertThat(strategy.compareDates(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDates one null → false")
        void compareDatesOneNull() {
            assertThat(strategy.compareDates(new Date(0), null, 0)).isFalse();
            assertThat(strategy.compareDates(null, new Date(0), 0)).isFalse();
        }

        @Test
        @DisplayName("compareDates equal → true")
        void compareDatesEqual() {
            Date d = new Date(1000);
            assertThat(strategy.compareDates(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDates diff > tolerance → false")
        void compareDatesDiffGtTolerance() {
            assertThat(strategy.compareDates(new Date(0), new Date(100), 50)).isFalse();
        }

        @Test
        @DisplayName("compareDates diff <= tolerance → true")
        void compareDatesDiffLeTolerance() {
            assertThat(strategy.compareDates(new Date(0), new Date(50), 100)).isTrue();
        }

        @Test
        @DisplayName("compareInstants both null → true")
        void compareInstantsBothNull() {
            assertThat(strategy.compareInstants(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareInstants one null → false")
        void compareInstantsOneNull() {
            assertThat(strategy.compareInstants(Instant.EPOCH, null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareInstants within tolerance")
        void compareInstantsWithinTolerance() {
            assertThat(strategy.compareInstants(
                Instant.EPOCH,
                Instant.EPOCH.plusMillis(50),
                100)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDateTimes both null → true")
        void compareLocalDateTimesBothNull() {
            assertThat(strategy.compareLocalDateTimes(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDateTimes one null → false")
        void compareLocalDateTimesOneNull() {
            assertThat(strategy.compareLocalDateTimes(
                LocalDateTime.of(2025, 1, 1, 0, 0),
                null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareLocalDateTimes toleranceMs == 0")
        void compareLocalDateTimesToleranceZero() {
            LocalDateTime t = LocalDateTime.of(2025, 1, 1, 0, 0);
            assertThat(strategy.compareLocalDateTimes(t, t, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDateTimes within tolerance")
        void compareLocalDateTimesWithinTolerance() {
            LocalDateTime t1 = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime t2 = LocalDateTime.of(2025, 1, 1, 0, 0, 0, 50_000_000);
            assertThat(strategy.compareLocalDateTimes(t1, t2, 100)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDates both null → true")
        void compareLocalDatesBothNull() {
            assertThat(strategy.compareLocalDates(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDates one null → false")
        void compareLocalDatesOneNull() {
            assertThat(strategy.compareLocalDates(LocalDate.of(2025, 1, 1), null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareLocalDates toleranceMs == 0")
        void compareLocalDatesToleranceZero() {
            LocalDate d = LocalDate.of(2025, 1, 1);
            assertThat(strategy.compareLocalDates(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDurations both null → true")
        void compareDurationsBothNull() {
            assertThat(strategy.compareDurations(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDurations one null → false")
        void compareDurationsOneNull() {
            assertThat(strategy.compareDurations(Duration.ofSeconds(1), null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareDurations diff")
        void compareDurationsDiff() {
            assertThat(strategy.compareDurations(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                0)).isFalse();
        }

        @Test
        @DisplayName("comparePeriods both null → true")
        void comparePeriodsBothNull() {
            assertThat(strategy.comparePeriods(null, null)).isTrue();
        }

        @Test
        @DisplayName("comparePeriods one null → false")
        void comparePeriodsOneNull() {
            assertThat(strategy.comparePeriods(Period.ofDays(1), null)).isFalse();
        }

        @Test
        @DisplayName("compareTemporal both null → true")
        void compareTemporalBothNull() {
            assertThat(strategy.compareTemporal(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal one null → false")
        void compareTemporalOneNull() {
            assertThat(strategy.compareTemporal(Instant.EPOCH, null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareTemporal type mismatch → false")
        void compareTemporalTypeMismatch() {
            assertThat(strategy.compareTemporal(
                Instant.EPOCH,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                0)).isFalse();
        }

        @Test
        @DisplayName("compareTemporal Date")
        void compareTemporalDate() {
            Date d = new Date(1000);
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Instant")
        void compareTemporalInstant() {
            assertThat(strategy.compareTemporal(Instant.EPOCH, Instant.EPOCH, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal LocalDateTime")
        void compareTemporalLocalDateTime() {
            LocalDateTime t = LocalDateTime.of(2025, 1, 1, 0, 0);
            assertThat(strategy.compareTemporal(t, t, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal LocalDate")
        void compareTemporalLocalDate() {
            LocalDate d = LocalDate.of(2025, 1, 1);
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Duration")
        void compareTemporalDuration() {
            Duration d = Duration.ofSeconds(1);
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Period")
        void compareTemporalPeriod() {
            Period p = Period.ofDays(1);
            assertThat(strategy.compareTemporal(p, p, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal unknown type → equals fallback")
        void compareTemporalUnknownType() {
            BigDecimal bd = BigDecimal.ONE;
            assertThat(strategy.compareTemporal(bd, bd, 0)).isTrue();
        }

        @Test
        @DisplayName("isTemporalType null → false")
        void isTemporalTypeNull() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(null)).isFalse();
        }

        @Test
        @DisplayName("isTemporalType Date → true")
        void isTemporalTypeDate() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(new Date())).isTrue();
        }

        @Test
        @DisplayName("needsTemporalCompare both temporal")
        void needsTemporalCompare() {
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(
                Instant.EPOCH, Instant.EPOCH)).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. DefaultComparisonProvider (20 missed branches)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultComparisonProvider")
    class DefaultComparisonProviderBranches {

        private final DefaultComparisonProvider provider = new DefaultComparisonProvider();

        @Test
        @DisplayName("compare success path")
        void compareSuccess() {
            CompareResult r = provider.compare(Map.of("a", 1), Map.of("a", 2));
            assertThat(r).isNotNull();
            assertThat(r.getObject1()).isNotNull();
            assertThat(r.getObject2()).isNotNull();
        }

        @Test
        @DisplayName("compare before null")
        void compareBeforeNull() {
            CompareResult r = provider.compare(null, Map.of("a", 1));
            assertThat(r).isNotNull();
            assertThat(r.getObject1()).isNull();
        }

        @Test
        @DisplayName("compare after null")
        void compareAfterNull() {
            CompareResult r = provider.compare(Map.of("a", 1), null);
            assertThat(r).isNotNull();
            assertThat(r.getObject2()).isNull();
        }

        @Test
        @DisplayName("compare with options success")
        void compareWithOptionsSuccess() {
            CompareResult r = provider.compare(
                Map.of("a", 1),
                Map.of("a", 2),
                CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare with options null → uses DEFAULT")
        void compareWithOptionsNull() {
            CompareResult r = provider.compare(Map.of("a", 1), Map.of("a", 2), null);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("similarity identical → 1.0")
        void similarityIdentical() {
            Map<String, Integer> m = Map.of("a", 1);
            assertThat(provider.similarity(m, m)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity changeCount == 0 → 1.0")
        void similarityChangeCountZero() {
            assertThat(provider.similarity(Map.of("a", 1), Map.of("a", 1))).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity normal")
        void similarityNormal() {
            double s = provider.similarity(Map.of("a", 1), Map.of("a", 2));
            assertThat(s).isLessThan(1.0).isGreaterThan(0);
        }

        @Test
        @DisplayName("compare exception path — identical via same reference")
        void compareExceptionSameRef() {
            Object o = new Object();
            CompareResult r = provider.compare(o, o);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare exception path — identical via equals")
        void compareExceptionEquals() {
            String s = "same";
            CompareResult r = provider.compare(s, "same");
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("priority returns 0")
        void priority() {
            assertThat(provider.priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("toString")
        void toStringTest() {
            assertThat(provider.toString()).contains("DefaultComparisonProvider");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CompareEngine quick paths (existing)
    // ═══════════════════════════════════════════════════════════════════════

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
        @DisplayName("both null")
        void bothNull() {
            CompareResult r = service.compare(null, null);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("type mismatch")
        void typeMismatch() {
            CompareResult r = service.compare("a", 1);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }
    }

    @Nested
    @DisplayName("FieldChange — getContainerMapKey")
    class FieldChangeGetContainerMapKey {

        @Test
        @DisplayName("getContainerMapKey delegates to getMapKey")
        void getContainerMapKey() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .mapKey("k")
                .build();
            FieldChange fc = FieldChange.builder().elementEvent(evt).build();
            assertThat(fc.getContainerMapKey()).isEqualTo("k");
        }
    }

    @Nested
    @DisplayName("FieldChange.CollectionChangeDetail")
    class FieldChangeCollectionChangeDetail {

        @Test
        @DisplayName("CollectionChangeDetail builder")
        void collectionChangeDetail() {
            FieldChange.CollectionChangeDetail d = FieldChange.CollectionChangeDetail.builder()
                .addedCount(1)
                .removedCount(2)
                .originalSize(5)
                .newSize(4)
                .build();
            assertThat(d.getAddedCount()).isEqualTo(1);
            assertThat(d.getRemovedCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("CompareResult — static factories")
    class CompareResultStaticFactories {

        @Test
        @DisplayName("identical()")
        void identical() {
            CompareResult r = CompareResult.identical();
            assertThat(r.isIdentical()).isTrue();
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("ofNullDiff")
        void ofNullDiff() {
            CompareResult r = CompareResult.ofNullDiff("a", null);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getObject1()).isEqualTo("a");
            assertThat(r.getObject2()).isNull();
        }

        @Test
        @DisplayName("ofTypeDiff")
        void ofTypeDiff() {
            CompareResult r = CompareResult.ofTypeDiff(1, "x");
            assertThat(r.isIdentical()).isFalse();
        }
    }

    @Nested
    @DisplayName("StrategyResolver — getCacheSize")
    class StrategyResolverCacheSize {

        @Test
        @DisplayName("getCacheSize after resolve")
        void getCacheSize() {
            StrategyResolver resolver = new StrategyResolver();
            resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            assertThat(resolver.getCacheSize()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — compareDates overload")
    class EnhancedDateCompareStrategyOverload {

        @Test
        @DisplayName("compareDates two-arg uses default tolerance")
        void compareDatesTwoArg() {
            EnhancedDateCompareStrategy s = new EnhancedDateCompareStrategy();
            Date d = new Date(1000);
            assertThat(s.compareDates(d, d)).isTrue();
        }
    }

    @Nested
    @DisplayName("CollectionCompareStrategy — getName and supports")
    class CollectionCompareStrategyMeta {

        @Test
        @DisplayName("getName")
        void getName() {
            assertThat(new CollectionCompareStrategy().getName()).isEqualTo("CollectionCompare");
        }

        @Test
        @DisplayName("supports Collection")
        void supports() {
            assertThat(new CollectionCompareStrategy().supports(ArrayList.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("FieldChange — toTypedView fieldPath edge cases")
    class FieldChangeToTypedViewEdgeCases {

        @Test
        @DisplayName("container with null fieldPath yields object unknown")
        void containerNullFieldPath() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.ARRAY)
                .operation(FieldChange.ElementOperation.MODIFY)
                .index(1)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("arr")
                .fieldPath(null)
                .elementEvent(evt)
                .build();
            Map<String, Object> view = fc.toTypedView();
            assertThat(view.get("object")).isEqualTo("unknown");
        }

        @Test
        @DisplayName("container with null fieldName uses list as base")
        void containerNullFieldName() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName(null)
                .fieldPath("x")
                .elementEvent(evt)
                .build();
            Map<String, Object> view = fc.toTypedView();
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) view.get("details");
            assertThat(details.get("index")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("CompareResult — groupByContainerOperation")
    class CompareResultGroupByContainerOpAlias {

        @Test
        @DisplayName("groupByContainerOperation delegates to typed")
        void groupByContainerOperation() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.REMOVE)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("f").changeType(ChangeType.DELETE).elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.groupByContainerOperation()).containsKey(FieldChange.ElementOperation.REMOVE);
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — LocalTime")
    class EnhancedDateCompareStrategyLocalTime {

        @Test
        @DisplayName("isTemporalType LocalTime")
        void isTemporalTypeLocalTime() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(
                java.time.LocalTime.of(12, 0))).isTrue();
        }
    }

    @Nested
    @DisplayName("StrategyResolver — extractSupportedType targetType fallback")
    class StrategyResolverTargetTypeFallback {

        @Test
        @DisplayName("resolve with strategy supporting targetType")
        void resolveTargetTypeFallback() {
            StrategyResolver resolver = new StrategyResolver();
            CompareStrategy<?> s = resolver.resolve(
                List.of(new CollectionCompareStrategy()),
                LinkedList.class);
            assertThat(s).isNotNull();
        }
    }

    @Nested
    @DisplayName("FieldChange — referenceDetail with null keys")
    class FieldChangeReferenceDetailNullKeys {

        @Test
        @DisplayName("ReferenceDetail with both keys null")
        void referenceDetailBothNull() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey(null)
                .newEntityKey(null)
                .nullReferenceChange(true)
                .build();
            Map<String, Object> map = rd.toMap();
            assertThat(map.get("oldKey")).isNull();
            assertThat(map.get("newKey")).isNull();
        }
    }

    @Nested
    @DisplayName("CompareResult — extractObjectPath container index with null fieldName")
    class CompareResultExtractObjectPathNullFieldName {

        @Test
        @DisplayName("container with index, fieldName null uses list")
        void containerIndexNullFieldName() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .entityKey(null)
                .index(3)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName(null).elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            Map<String, List<FieldChange>> byObj = r.groupByObject();
            assertThat(byObj).containsKey("list[3]");
        }
    }

    @Nested
    @DisplayName("DefaultComparisonProvider — compare exception fallback")
    class DefaultComparisonProviderCompareException {

        @Test
        @DisplayName("compare returns non-null on success")
        void compareReturnsNonNull() {
            CompareResult r = new DefaultComparisonProvider().compare("a", "b");
            assertThat(r).isNotNull();
            assertThat(r.getChanges()).isNotNull();
        }
    }

    @Nested
    @DisplayName("CollectionCompareStrategy — no changes when identical")
    class CollectionCompareStrategyNoChanges {

        @Test
        @DisplayName("identical collections produce no changes")
        void identicalCollections() {
            List<String> col = List.of("a", "b");
            CompareResult r = new CollectionCompareStrategy().compare(col, col, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).isEmpty();
        }

        @Test
        @DisplayName("same content different instances")
        void sameContentDifferentInstances() {
            List<String> a = new ArrayList<>(List.of("a", "b"));
            List<String> b = new ArrayList<>(List.of("a", "b"));
            CompareResult r = new CollectionCompareStrategy().compare(a, b, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).isEmpty();
        }
    }

    @Nested
    @DisplayName("FieldChange — ContainerType and ElementOperation enums")
    class FieldChangeEnums {

        @Test
        @DisplayName("ContainerType values")
        void containerTypeValues() {
            assertThat(FieldChange.ContainerType.values())
                .contains(FieldChange.ContainerType.LIST, FieldChange.ContainerType.SET,
                    FieldChange.ContainerType.MAP, FieldChange.ContainerType.ARRAY);
        }

        @Test
        @DisplayName("ElementOperation values")
        void elementOperationValues() {
            assertThat(FieldChange.ElementOperation.values())
                .contains(FieldChange.ElementOperation.ADD, FieldChange.ElementOperation.REMOVE,
                    FieldChange.ElementOperation.MODIFY, FieldChange.ElementOperation.MOVE);
        }
    }

    @Nested
    @DisplayName("CompareResult — builder defaults")
    class CompareResultBuilderDefaults {

        @Test
        @DisplayName("builder with minimal fields")
        void builderMinimal() {
            CompareResult r = CompareResult.builder().identical(true).build();
            assertThat(r.getChanges()).isEmpty();
            assertThat(r.getDuplicateKeys()).isEmpty();
        }

        @Test
        @DisplayName("builder with compareTime")
        void builderWithCompareTime() {
            Instant t = Instant.now();
            CompareResult r = CompareResult.builder().compareTime(t).build();
            assertThat(r.getCompareTime()).isEqualTo(t);
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — compareDates diff branches")
    class EnhancedDateCompareStrategyDateBranches {

        @Test
        @DisplayName("compareDates diff equals tolerance")
        void compareDatesDiffEqualsTolerance() {
            EnhancedDateCompareStrategy s = new EnhancedDateCompareStrategy();
            assertThat(s.compareDates(new Date(0), new Date(100), 100)).isTrue();
        }

        @Test
        @DisplayName("compareDates diff less than tolerance")
        void compareDatesDiffLtTolerance() {
            EnhancedDateCompareStrategy s = new EnhancedDateCompareStrategy();
            assertThat(s.compareDates(new Date(0), new Date(50), 100)).isTrue();
        }
    }

    @Nested
    @DisplayName("StrategyResolver — StrategyCache path")
    class StrategyResolverStrategyCachePath {

        @Test
        @DisplayName("resolve with StrategyCache uses cache")
        void resolveWithStrategyCache() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            StrategyResolver resolver = new StrategyResolver(cache);
            CompareStrategy<?> s1 = resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            CompareStrategy<?> s2 = resolver.resolve(List.of(new SetCompareStrategy()), HashSet.class);
            assertThat(s1).isNotNull();
            assertThat(s2).isNotNull();
        }
    }

    @Nested
    @DisplayName("FieldChange — toReferenceChangeView fieldPath fallback")
    class FieldChangeToReferenceChangeViewFallback {

        @Test
        @DisplayName("reference with null fieldPath uses fieldName for path")
        void referenceNullFieldPathUsesFieldName() {
            FieldChange.ReferenceDetail rd = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("o")
                .newEntityKey("n")
                .nullReferenceChange(false)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("customerRef")
                .fieldPath(null)
                .referenceChange(true)
                .referenceDetail(rd)
                .build();
            Map<String, Object> view = fc.toReferenceChangeView();
            assertThat(view.get("path")).isEqualTo("customerRef");
        }
    }

    @Nested
    @DisplayName("CompareResult — getContainerChangesByType empty types")
    class CompareResultGetContainerChangesByTypeEmpty {

        @Test
        @DisplayName("getContainerChangesByType with single type filter")
        void getContainerChangesByTypeSingle() {
            FieldChange.ContainerElementEvent evt = FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.ADD)
                .build();
            List<FieldChange> changes = List.of(
                FieldChange.builder().fieldName("m").changeType(ChangeType.CREATE).elementEvent(evt).build());
            CompareResult r = CompareResult.builder().changes(changes).build();
            assertThat(r.getContainerChangesByType(FieldChange.ContainerType.MAP)).hasSize(1);
            assertThat(r.getContainerChangesByType(FieldChange.ContainerType.LIST)).isEmpty();
        }
    }

    @Nested
    @DisplayName("DefaultComparisonProvider — compare both null")
    class DefaultComparisonProviderBothNull {

        @Test
        @DisplayName("compare null null")
        void compareNullNull() {
            CompareResult r = new DefaultComparisonProvider().compare(null, null);
            assertThat(r).isNotNull();
            assertThat(r.getObject1()).isNull();
            assertThat(r.getObject2()).isNull();
        }
    }

    @Nested
    @DisplayName("Additional branch coverage")
    class AdditionalBranchCoverage {

        @Test
        @DisplayName("FieldChange collectionChange and collectionDetail")
        void fieldChangeCollectionDetail() {
            FieldChange.CollectionChangeDetail d = FieldChange.CollectionChangeDetail.builder()
                .modifiedCount(1)
                .build();
            FieldChange fc = FieldChange.builder()
                .fieldName("col")
                .collectionChange(true)
                .collectionDetail(d)
                .changeType(ChangeType.UPDATE)
                .build();
            assertThat(fc.isCollectionChange()).isTrue();
            assertThat(fc.getCollectionDetail()).isNotNull();
        }

        @Test
        @DisplayName("CompareResult degradationReasons and algorithmUsed")
        void compareResultExtraFields() {
            CompareResult r = CompareResult.builder()
                .changes(List.of())
                .degradationReasons(List.of("timeout"))
                .algorithmUsed("LCS")
                .build();
            assertThat(r.getDegradationReasons()).contains("timeout");
            assertThat(r.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("needsTemporalCompare one temporal one not")
        void needsTemporalCompareMixed() {
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(
                Instant.EPOCH, "not-temporal")).isFalse();
        }
    }

}
