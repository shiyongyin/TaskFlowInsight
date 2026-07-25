package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deep coverage tests for tracking/query package.
 * Maximizes coverage for ListChangeProjector, MapSetEntryProjector, and ChangeAdapters.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Query — Deep Coverage Tests")
class QueryDeepCoverageTests {

    // ──────────────────────────────────────────────────────────────
    //  ListChangeProjector
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ListChangeProjector")
    class ListChangeProjectorTests {

        @Test
        @DisplayName("project — null result returns empty")
        void project_nullResult_returnsEmpty() {
            List<Map<String, Object>> events = ListChangeProjector.project(
                null, List.of(1, 2), List.of(1, 2, 3), CompareOptions.builder().build(), "items");
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("project — SIMPLE algorithm")
        void project_simpleAlgorithm() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, List.of("a", "b"), List.of("a", "x"), CompareOptions.builder().build(), "list");
            assertThat(events).isNotEmpty();
            assertThat(events.get(0)).containsKeys("kind", "object", "path", "timestamp", "details");
        }

        @Test
        @DisplayName("project — replacement at index")
        void project_replacementAtIndex() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, List.of("a", "b"), List.of("a", "c"), CompareOptions.builder().build(), "set");
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("project — insertion at ordered index")
        void project_insertionAtOrderedIndex() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, List.of("a", "b"), List.of("a", "x", "b"), CompareOptions.builder().build(), "list");
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("project — exact index update")
        void project_exactIndexUpdate() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, List.of("a", "b"), List.of("a", "x"), CompareOptions.builder().build(), "list");
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("project — ENTITY algorithm")
        void project_entityAlgorithm() {
            CompareResult result = CompareResult.identical();
            List<TestEntity> left = List.of(new TestEntity(1, "A"));
            List<TestEntity> right = List.of(new TestEntity(1, "B"));
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, left, right, CompareOptions.builder().build(), "entities");
            assertThat(events).isNotNull();
        }

        @Test
        @DisplayName("project — default plan handles append")
        void project_defaultPlanHandlesAppend() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, List.of(1), List.of(1, 2), CompareOptions.builder().build(), "x");
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("project — default plan handles replacement")
        void project_defaultPlanHandlesReplacement() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, List.of("a"), List.of("b"), CompareOptions.builder().build(), "x");
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("project — reordering remains index updates")
        void project_reorderingRemainsIndexUpdates() {
            CompareResult result = CompareResult.identical();
            List<String> left = List.of("a", "b", "c");
            List<String> right = List.of("b", "a", "c");
            CompareOptions opts = CompareOptions.builder().build();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, left, right, opts, "list");
            assertThat(events).isNotEmpty()
                    .noneMatch(event -> "entry_moved".equals(event.get("kind")));
        }

        @Test
        @DisplayName("project — null left/right handled")
        void project_nullLeftRight_handled() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = ListChangeProjector.project(
                result, null, List.of(1), CompareOptions.builder().build(), "x");
            assertThat(events).isNotNull();
        }

        @Entity
        static class TestEntity {
            @Key
            private final int id;
            @SuppressWarnings("unused")
            private String name;

            TestEntity(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                TestEntity that = (TestEntity) o;
                return id == that.id;
            }

            @Override
            public int hashCode() {
                return Integer.hashCode(id);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  MapSetEntryProjector
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MapSetEntryProjector")
    class MapSetEntryProjectorTests {

        @Test
        @DisplayName("projectMap — entry_added")
        void projectMap_entryAdded() {
            Map<String, Object> left = Map.of("a", 1);
            Map<String, Object> right = Map.of("a", 1, "b", 2);
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "map");
            assertThat(events).anyMatch(e -> "entry_added".equals(e.get("kind")));
        }

        @Test
        @DisplayName("projectMap — entry_removed")
        void projectMap_entryRemoved() {
            Map<String, Object> left = Map.of("a", 1, "b", 2);
            Map<String, Object> right = Map.of("a", 1);
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "map");
            assertThat(events).anyMatch(e -> "entry_removed".equals(e.get("kind")));
        }

        @Test
        @DisplayName("projectMap — entry_updated")
        void projectMap_entryUpdated() {
            Map<String, Object> left = Map.of("a", 1);
            Map<String, Object> right = Map.of("a", 2);
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "map");
            assertThat(events).anyMatch(e -> "entry_updated".equals(e.get("kind")));
        }

        @Test
        @DisplayName("projectMap — null maps handled")
        void projectMap_nullMaps_handled() {
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, null, Map.of("k", "v"), "map");
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("projectSet — entry_added")
        void projectSet_entryAdded() {
            Set<String> left = Set.of("a");
            Set<String> right = Set.of("a", "b");
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, "set");
            assertThat(events).anyMatch(e -> "entry_added".equals(e.get("kind")));
        }

        @Test
        @DisplayName("projectSet — entry_removed")
        void projectSet_entryRemoved() {
            Set<String> left = Set.of("a", "b");
            Set<String> right = Set.of("a");
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, "set");
            assertThat(events).anyMatch(e -> "entry_removed".equals(e.get("kind")));
        }

        @Test
        @DisplayName("projectSet — null containerPath")
        void projectSet_nullContainerPath() {
            Set<String> left = Set.of("a");
            Set<String> right = Set.of("a", "b");
            CompareResult result = CompareResult.identical();
            List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, null);
            assertThat(events).isNotEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ChangeAdapters
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ChangeAdapters — Deep")
    class ChangeAdaptersDeepTests {

        @Test
        @DisplayName("toTypedView — CREATE maps to entry_added")
        void toTypedView_create_mapsToEntryAdded() {
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.ADD, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("order.items[0]")), null, "item1"));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView("Order", changes);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).get("kind")).isEqualTo("entry_added");
        }

        @Test
        @DisplayName("toTypedView — DELETE maps to entry_removed")
        void toTypedView_delete_mapsToEntryRemoved() {
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.REMOVE, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("items[0]")), "item1", null));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView("Order", changes);
            assertThat(events.get(0).get("kind")).isEqualTo("entry_removed");
        }

        @Test
        @DisplayName("toTypedView — UPDATE maps to entry_updated")
        void toTypedView_update_mapsToEntryUpdated() {
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.MODIFY, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("status")), "NEW", "PAID"));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView("Order", changes);
            assertThat(events.get(0).get("kind")).isEqualTo("entry_updated");
        }

        @Test
        @DisplayName("toTypedView — MOVE maps to entry_moved")
        void toTypedView_move_mapsToEntryMoved() {
            List<FieldChange> changes = List.of(
                FieldChange.moved(
                        com.syy.taskflowinsight.tracking.path.ComparePath.root()
                                .append(new com.syy.taskflowinsight.tracking.path.IndexSegment(1)),
                        "a",
                        com.syy.taskflowinsight.tracking.path.ComparePath.root()
                                .append(new com.syy.taskflowinsight.tracking.path.IndexSegment(2)),
                        "a"));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView("List", changes);
            assertThat(events.get(0).get("kind")).isEqualTo("entry_moved");
        }

        @Test
        @DisplayName("toTypedView — valueType in details")
        void toTypedView_valueType_inDetails() {
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.MODIFY, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("amount")), 10, 20));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView("Order", changes);
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) events.get(0).get("details");
            assertThat(details).containsKey("valueType");
        }

        @Test
        @DisplayName("toTypedView — null objectName uses Unknown")
        void toTypedView_nullObjectName() {
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.MODIFY, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("x")), null, null));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView(null, changes);
            assertThat(events.get(0).get("object")).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("toTypedJson — returns JSON array string")
        void toTypedJson_returnsJsonArray() {
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.MODIFY, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("status")), "A", "B"));
            String json = ChangeAdapters.toTypedJson("Order", changes);
            assertThat(json).startsWith("[");
            assertThat(json).endsWith("]");
            assertThat(json).contains("status");
        }

        @Test
        @DisplayName("toTypedJson — empty list returns []")
        void toTypedJson_empty_returnsEmptyArray() {
            String json = ChangeAdapters.toTypedJson("Order", Collections.emptyList());
            assertThat(json).isEqualTo("[]");
        }

        @Test
        @DisplayName("registerCustomizer and applyCustomizers")
        void registerCustomizer_shouldWork() {
            List<Map<String, Object>> captured = new java.util.ArrayList<>();
            ChangeAdapters.Customizer customizer = (events, source) -> captured.addAll(events);
            ChangeAdapters.registerCustomizer(customizer);
            List<FieldChange> changes = List.of(
                FieldChange.at(com.syy.taskflowinsight.tracking.compare.ChangeKind.MODIFY, com.syy.taskflowinsight.tracking.path.ComparePath.root().append(new com.syy.taskflowinsight.tracking.path.PropertySegment("x")), null, null));
            List<Map<String, Object>> events = ChangeAdapters.toTypedView("Obj", changes);
            ChangeAdapters.applyCustomizers(events, CompareResult.identical());
            assertThat(captured).isNotEmpty();
        }
    }
}
