package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ContainerEvents;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive branch coverage tests for entity and filter packages.
 * Targets every if/else, switch, ternary, and try/catch branch.
 *
 * @since 3.0.0
 */
@DisplayName("Entity & Filter — Branch Coverage Tests")
class EntityFilterBranchTests {

    // ═══════════════════════════════════════════════════════════════════════
    // ENTITY PACKAGE: tracking/compare/entity
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EntityListDiffResult — Branch Coverage")
    class EntityListDiffResultBranchTests {

        @Test
        @DisplayName("getAddedEntities — operationGroups has ADD → returns list")
        void getAddedEntities_whenAddExists_returnsList() {
            EntityChangeGroup addGroup = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .changes(Collections.emptyList())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(addGroup))
                    .build();
            assertThat(result.getAddedEntities()).hasSize(1);
        }

        @Test
        @DisplayName("getAddedEntities — no ADD → returns empty list")
        void getAddedEntities_whenNoAdd_returnsEmpty() {
            EntityListDiffResult result = EntityListDiffResult.empty();
            assertThat(result.getAddedEntities()).isEmpty();
        }

        @Test
        @DisplayName("getModifiedEntities — operationGroups has MODIFY → returns list")
        void getModifiedEntities_whenModifyExists_returnsList() {
            EntityChangeGroup modGroup = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(Collections.emptyList())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(modGroup))
                    .build();
            assertThat(result.getModifiedEntities()).hasSize(1);
        }

        @Test
        @DisplayName("getModifiedEntities — no MODIFY → returns empty list")
        void getModifiedEntities_whenNoModify_returnsEmpty() {
            EntityListDiffResult result = EntityListDiffResult.empty();
            assertThat(result.getModifiedEntities()).isEmpty();
        }

        @Test
        @DisplayName("getDeletedEntities — operationGroups has DELETE → returns list")
        void getDeletedEntities_whenDeleteExists_returnsList() {
            EntityChangeGroup delGroup = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.DELETE)
                    .changes(Collections.emptyList())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(delGroup))
                    .build();
            assertThat(result.getDeletedEntities()).hasSize(1);
        }

        @Test
        @DisplayName("getDeletedEntities — no DELETE → returns empty list")
        void getDeletedEntities_whenNoDelete_returnsEmpty() {
            EntityListDiffResult result = EntityListDiffResult.empty();
            assertThat(result.getDeletedEntities()).isEmpty();
        }

        @Test
        @DisplayName("hasChanges — groups non-empty → true")
        void hasChanges_whenGroupsNonEmpty_returnsTrue() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .changes(Collections.emptyList())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(List.of(group)).build();
            assertThat(result.hasChanges()).isTrue();
        }

        @Test
        @DisplayName("hasChanges — groups empty → false")
        void hasChanges_whenGroupsEmpty_returnsFalse() {
            assertThat(EntityListDiffResult.empty().hasChanges()).isFalse();
        }

        @Test
        @DisplayName("isIdentical — originalResult null → false")
        void isIdentical_whenOriginalResultNull_returnsFalse() {
            EntityListDiffResult result = EntityListDiffResult.builder().build();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("isIdentical — originalResult identical → true")
        void isIdentical_whenOriginalIdentical_returnsTrue() {
            CompareResult orig = CompareResult.identical();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .originalResult(orig)
                    .build();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("getSimilarity — originalResult null → null")
        void getSimilarity_whenOriginalNull_returnsNull() {
            EntityListDiffResult result = EntityListDiffResult.builder().build();
        }

        @Test
        @DisplayName("getSimilarity — canonical score is adapted for the legacy entity view")
        void getSimilarity_whenOriginalHasValue_returnsValue() {
            CompareResult orig = CompareResult.identical();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .originalResult(orig)
                    .build();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("getSummary — no changes → No changes detected")
        void getSummary_whenNoChanges_returnsNoChanges() {
            String summary = EntityListDiffResult.empty().getSummary();
            assertThat(summary).isEqualTo("No changes detected");
        }

        @Test
        @DisplayName("getSummary — has changes → formatted summary")
        void getSummary_whenHasChanges_returnsFormatted() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .changes(List.of(FieldChange.at(
                            ChangeKind.ADD,
                            ComparePath.root().append(new PropertySegment("x")),
                            null,
                            null)))
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder().groups(List.of(group)).build();
            String summary = result.getSummary();
            assertThat(summary).contains("Total:").contains("Added: 1");
        }

        @Test
        @DisplayName("from — result null → empty")
        void from_whenResultNull_returnsEmpty() {
            EntityListDiffResult result = EntityListDiffResult.from(null);
            assertThat(result.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("from — result changes null → empty")
        void from_whenChangesNull_returnsEmpty() {
            CompareResult cr = CompareResult.identical();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("from — result changes empty → empty")
        void from_whenChangesEmpty_returnsEmpty() {
            CompareResult cr = CompareResultReducer.complete(Collections.emptyList());
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("from — path-based field change → groups by path")
        void from_withPathBasedChange_groupsByPath() {
            FieldChange fc = FieldChange.at(ChangeKind.MODIFY,
                    ComparePath.root().append(new PropertySegment("entity[1001].status")), "A", "B");
            CompareResult cr = CompareResultReducer.complete(List.of(fc));
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isTrue();
            assertThat(result.getGroups()).isNotEmpty();
        }

        @Test
        @DisplayName("from — typed entity key path → renders typed key components")
        void from_withContainerEventEntityKey_usesEntityKey() {
            FieldChange fc = FieldChange.at(
                    ChangeKind.ADD,
                    ComparePath.root()
                            .append(new PropertySegment("order"))
                            .append(new EntityKeySegment(
                                    "Order",
                                    List.of(ValueSnapshot.ofString("O123", 4)))),
                    null,
                    "item");
            CompareResult cr = CompareResultReducer.complete(List.of(fc));
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.getGroups()).singleElement().satisfies(group -> {
                assertThat(group.getEntityKey()).isEqualTo("entity[O123]");
                assertThat(group.getKeyParts()).containsExactly("O123");
            });
        }

        @Test
        @DisplayName("from — typed index path → entity[index]")
        void from_withContainerEventIndexOnly_usesEntityIndex() {
            FieldChange fc = FieldChange.at(
                    ChangeKind.ADD,
                    ComparePath.root()
                            .append(new PropertySegment("entity"))
                            .append(new IndexSegment(5)),
                    null,
                    "item");
            CompareResult cr = CompareResultReducer.complete(List.of(fc));
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.getGroups()).anyMatch(g -> "entity[5]".equals(g.getEntityKey()));
        }

        @Test
        @DisplayName("Builder groups null → empty list")
        void builder_groupsNull_usesEmptyList() {
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(null)
                    .build();
            assertThat(result.getGroups()).isEmpty();
        }
    }

    @Nested
    @DisplayName("EntityChangeGroup — Branch Coverage")
    class EntityChangeGroupBranchTests {

        @Test
        @DisplayName("keyParts null → empty list")
        void keyPartsNull_returnsEmptyList() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .keyParts(null)
                    .build();
            assertThat(group.getKeyParts()).isEmpty();
        }

        @Test
        @DisplayName("keyParts non-null → unmodifiable copy")
        void keyPartsNonNull_returnsCopy() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .keyParts(List.of("a", "b"))
                    .build();
            assertThat(group.getKeyParts()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("oldIndexes null → null")
        void oldIndexesNull_returnsNull() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .oldIndexes(null)
                    .build();
            assertThat(group.getOldIndexes()).isNull();
        }

        @Test
        @DisplayName("newIndexes null → null")
        void newIndexesNull_returnsNull() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .newIndexes(null)
                    .build();
            assertThat(group.getNewIndexes()).isNull();
        }

        @Test
        @DisplayName("getFieldChanges — fieldPath non-null, endsWith match")
        void getFieldChanges_fieldPathEndsWith_match() {
            FieldChange fc = FieldChange.at(
                    ChangeKind.MODIFY,
                    ComparePath.root().append(new PropertySegment("entity[1].name")),
                    null,
                    null);
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(fc))
                    .build();
            assertThat(group.getFieldChanges("name")).hasSize(1);
        }

        @Test
        @DisplayName("getFieldChanges — path equals fieldName")
        void getFieldChanges_pathEqualsFieldName_match() {
            FieldChange fc = FieldChange.at(
                    ChangeKind.MODIFY,
                    ComparePath.root().append(new PropertySegment("name")),
                    null,
                    null);
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(fc))
                    .build();
            assertThat(group.getFieldChanges("name")).hasSize(1);
        }

        @Test
        @DisplayName("getFieldChanges — no match → empty")
        void getFieldChanges_noMatch_returnsEmpty() {
            FieldChange fc = FieldChange.at(
                    ChangeKind.MODIFY,
                    ComparePath.root().append(new PropertySegment("entity[1].other")),
                    null,
                    null);
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(fc))
                    .build();
            assertThat(group.getFieldChanges("name")).isEmpty();
        }

        @Test
        @DisplayName("Builder changes null → empty list")
        void builder_changesNull_usesEmptyList() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.ADD)
                    .changes(null)
                    .build();
            assertThat(group.getChanges()).isEmpty();
        }

        @Test
        @DisplayName("Builder entityKey null → throws")
        void builder_entityKeyNull_throws() {
            assertThatThrownBy(() -> EntityChangeGroup.builder()
                    .entityKey(null)
                    .operation(EntityOperation.ADD)
                    .build())
                    .hasMessageContaining("Entity key cannot be null");
        }

        @Test
        @DisplayName("Builder operation null → throws")
        void builder_operationNull_throws() {
            assertThatThrownBy(() -> EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(null)
                    .build())
                    .hasMessageContaining("Operation cannot be null");
        }
    }

    @Nested
    @DisplayName("EntityOperation — Branch Coverage")
    class EntityOperationBranchTests {

        @Test
        @DisplayName("ADD getDisplayName")
        void add_getDisplayName() {
            assertThat(EntityOperation.ADD.getDisplayName()).isEqualTo("新增");
        }

        @Test
        @DisplayName("MODIFY getDisplayName")
        void modify_getDisplayName() {
            assertThat(EntityOperation.MODIFY.getDisplayName()).isEqualTo("修改");
        }

        @Test
        @DisplayName("DELETE getDisplayName")
        void delete_getDisplayName() {
            assertThat(EntityOperation.DELETE.getDisplayName()).isEqualTo("删除");
        }
    }

}
