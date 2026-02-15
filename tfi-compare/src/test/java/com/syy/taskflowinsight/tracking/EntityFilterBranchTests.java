package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.ContainerEvents;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import com.syy.taskflowinsight.tracking.snapshot.filter.ClassLevelFilterEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.DefaultExclusionEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.FilterDecision;
import com.syy.taskflowinsight.tracking.snapshot.filter.FilterReason;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathLevelFilterEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathMatcher;
import com.syy.taskflowinsight.tracking.snapshot.filter.UnifiedFilterEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            CompareResult orig = CompareResult.builder().identical(true).build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .originalResult(orig)
                    .build();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("getSimilarity — originalResult null → null")
        void getSimilarity_whenOriginalNull_returnsNull() {
            EntityListDiffResult result = EntityListDiffResult.builder().build();
            assertThat(result.getSimilarity()).isNull();
        }

        @Test
        @DisplayName("getSimilarity — originalResult has similarity → value")
        void getSimilarity_whenOriginalHasValue_returnsValue() {
            CompareResult orig = CompareResult.builder().similarity(0.9).build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .originalResult(orig)
                    .build();
            assertThat(result.getSimilarity()).isEqualTo(0.9);
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
                    .changes(List.of(FieldChange.builder().fieldName("x").changeType(ChangeType.CREATE).build()))
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
            CompareResult cr = CompareResult.builder().changes(null).build();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("from — result changes empty → empty")
        void from_whenChangesEmpty_returnsEmpty() {
            CompareResult cr = CompareResult.builder().changes(Collections.emptyList()).build();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("from — path-based field change → groups by path")
        void from_withPathBasedChange_groupsByPath() {
            FieldChange fc = FieldChange.builder()
                    .fieldName("status")
                    .fieldPath("entity[1001].status")
                    .oldValue("A")
                    .newValue("B")
                    .changeType(ChangeType.UPDATE)
                    .build();
            CompareResult cr = CompareResult.builder()
                    .changes(List.of(fc))
                    .identical(false)
                    .build();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isTrue();
            assertThat(result.getGroups()).isNotEmpty();
        }

        @Test
        @DisplayName("from — container event with entityKey → uses entityKey")
        void from_withContainerEventEntityKey_usesEntityKey() {
            FieldChange fc = FieldChange.builder()
                    .fieldPath("items")
                    .elementEvent(ContainerEvents.listAdd(0, "order[O123]"))
                    .changeType(ChangeType.CREATE)
                    .build();
            CompareResult cr = CompareResult.builder()
                    .changes(List.of(fc))
                    .identical(false)
                    .build();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.getGroups()).anyMatch(g -> "order[O123]".equals(g.getEntityKey()));
        }

        @Test
        @DisplayName("from — container event with index only → entity[index]")
        void from_withContainerEventIndexOnly_usesEntityIndex() {
            FieldChange fc = FieldChange.builder()
                    .fieldPath("items")
                    .elementEvent(FieldChange.ContainerElementEvent.builder()
                            .operation(FieldChange.ElementOperation.ADD)
                            .index(5)
                            .entityKey(null)
                            .build())
                    .changeType(ChangeType.CREATE)
                    .build();
            CompareResult cr = CompareResult.builder()
                    .changes(List.of(fc))
                    .identical(false)
                    .build();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.getGroups()).anyMatch(g -> "entity[5]".equals(g.getEntityKey()));
        }

        @Test
        @DisplayName("from — collectionChange fieldName fallback")
        void from_withCollectionChangeFieldName_fallback() {
            FieldChange fc = FieldChange.builder()
                    .fieldName("collection")
                    .fieldPath(null)
                    .collectionChange(true)
                    .changeType(ChangeType.UPDATE)
                    .build();
            CompareResult cr = CompareResult.builder()
                    .changes(List.of(fc))
                    .identical(false)
                    .build();
            EntityListDiffResult result = EntityListDiffResult.from(cr);
            assertThat(result.hasChanges()).isTrue();
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
            FieldChange fc = FieldChange.builder()
                    .fieldPath("entity[1].name")
                    .fieldName("name")
                    .changeType(ChangeType.UPDATE)
                    .build();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("e[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(fc))
                    .build();
            assertThat(group.getFieldChanges("name")).hasSize(1);
        }

        @Test
        @DisplayName("getFieldChanges — fieldPath null, fieldName used")
        void getFieldChanges_fieldPathNull_usesFieldName() {
            FieldChange fc = FieldChange.builder()
                    .fieldPath(null)
                    .fieldName("name")
                    .changeType(ChangeType.UPDATE)
                    .build();
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
            FieldChange fc = FieldChange.builder()
                    .fieldPath("name")
                    .fieldName("name")
                    .changeType(ChangeType.UPDATE)
                    .build();
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
            FieldChange fc = FieldChange.builder()
                    .fieldPath("entity[1].other")
                    .changeType(ChangeType.UPDATE)
                    .build();
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

    // ═══════════════════════════════════════════════════════════════════════
    // FILTER PACKAGE: tracking/snapshot/filter
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PathLevelFilterEngine — Branch Coverage")
    class PathLevelFilterEngineBranchTests {

        @Test
        @DisplayName("matchesIncludePatterns — path null → false")
        void matchesInclude_pathNull_returnsFalse() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns(null, Set.of("*.x"))).isFalse();
        }

        @Test
        @DisplayName("matchesIncludePatterns — includePatterns null → false")
        void matchesInclude_patternsNull_returnsFalse() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns("a.b", null)).isFalse();
        }

        @Test
        @DisplayName("matchesIncludePatterns — includePatterns empty → false")
        void matchesInclude_patternsEmpty_returnsFalse() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns("a.b", Collections.emptySet())).isFalse();
        }

        @Test
        @DisplayName("matchesIncludePatterns — pattern null in set → skips")
        void matchesInclude_patternNullInSet_skips() {
            Set<String> patterns = new HashSet<>(Arrays.asList(null, "*.b"));
            assertThat(PathLevelFilterEngine.matchesIncludePatterns("a.b", patterns)).isTrue();
        }

        @Test
        @DisplayName("matchesIncludePatterns — match → true")
        void matchesInclude_match_returnsTrue() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns("order.items", Set.of("order.*"))).isTrue();
        }

        @Test
        @DisplayName("matchesIncludePatterns — no match → false")
        void matchesInclude_noMatch_returnsFalse() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns("x.y", Set.of("order.*"))).isFalse();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — path null → false")
        void shouldIgnore_pathNull_returnsFalse() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath(null, Set.of("*.x"), null)).isFalse();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — excludeGlob null → no glob match")
        void shouldIgnore_globNull_skipsGlob() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("a.b", null, Set.of("^a\\.b$"))).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — excludeGlob empty → no glob match")
        void shouldIgnore_globEmpty_skipsGlob() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("a.b", Collections.emptySet(), Set.of("^a\\.b$"))).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — glob pattern null → skips")
        void shouldIgnore_globPatternNull_skips() {
            Set<String> globs = new HashSet<>(Arrays.asList(null, "*.b"));
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("a.b", globs, null)).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — glob match → true")
        void shouldIgnore_globMatch_returnsTrue() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("order.internal", Set.of("order.internal*"), null)).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — excludeRegex null → no regex match")
        void shouldIgnore_regexNull_skipsRegex() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("a.b", Set.of("*.b"), null)).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — regex pattern null → skips")
        void shouldIgnore_regexPatternNull_skips() {
            Set<String> regexes = new HashSet<>(Arrays.asList(null, ".*"));
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("a.b", null, regexes)).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — regex match → true")
        void shouldIgnore_regexMatch_returnsTrue() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("debug_001", null, Set.of("^debug_\\d+$"))).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath — no match → false")
        void shouldIgnore_noMatch_returnsFalse() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("order.status", Set.of("*.internal"), Set.of("debug.*"))).isFalse();
        }
    }

    @Nested
    @DisplayName("UnifiedFilterEngine — Branch Coverage")
    class UnifiedFilterEngineBranchTests {

        @Test
        @DisplayName("Include whitelist match → include")
        void includeMatch_returnsInclude() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "order.items",
                    Set.of("order.*"), null, null, null, false);
            assertThat(d.shouldInclude()).isTrue();
            assertThat(d.getReason()).isEqualTo(FilterReason.INCLUDE_PATTERNS);
        }

        @Test
        @DisplayName("Exclude glob match → exclude")
        void excludeGlobMatch_returnsExclude() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "order.internal",
                    null, Set.of("order.internal*"), null, null, false);
            assertThat(d.shouldExclude()).isTrue();
            assertThat(d.getReason()).isEqualTo(FilterReason.EXCLUDE_PATTERNS);
        }

        @Test
        @DisplayName("Exclude regex match → exclude")
        void excludeRegexMatch_returnsExclude() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "debug_data",
                    null, null, Set.of("^debug_.*"), null, false);
            assertThat(d.shouldExclude()).isTrue();
            assertThat(d.getReason()).isEqualTo(FilterReason.REGEX_EXCLUDES);
        }

        @Test
        @DisplayName("Class-level exclude → exclude")
        void classLevelExclude_returnsExclude() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "order.status",
                    null, null, null, List.of("java.lang"), false);
            assertThat(d.shouldExclude()).isTrue();
            assertThat(d.getReason()).isEqualTo(FilterReason.EXCLUDE_PACKAGES);
        }

        @Test
        @DisplayName("Default exclusion → exclude")
        void defaultExclusion_returnsExclude() throws Exception {
            Field field = DefaultExclusionEngine.class.getDeclaredField("logger");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    DefaultExclusionEngine.class, field, "logger",
                    null, null, null, null, true);
            assertThat(d.shouldExclude()).isTrue();
            assertThat(d.getReason()).isEqualTo(FilterReason.DEFAULT_EXCLUSIONS);
        }

        @Test
        @DisplayName("Default retain → include")
        void defaultRetain_returnsInclude() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "order.status",
                    null, null, null, null, false);
            assertThat(d.shouldInclude()).isTrue();
            assertThat(d.getReason()).isEqualTo(FilterReason.DEFAULT_RETAIN);
        }

        @Test
        @DisplayName("excludeGlobPatterns null → skips glob loop")
        void excludeGlobNull_skipsGlob() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "order.status",
                    null, null, Set.of("^nomatch$"), null, false);
            assertThat(d.shouldInclude()).isTrue();
        }

        @Test
        @DisplayName("excludeRegexPatterns null → skips regex loop")
        void excludeRegexNull_skipsRegex() throws Exception {
            Field field = String.class.getDeclaredField("value");
            FilterDecision d = UnifiedFilterEngine.shouldIgnore(
                    String.class, field, "order.status",
                    null, Set.of("nomatch"), null, null, false);
            assertThat(d.shouldInclude()).isTrue();
        }
    }

    @Nested
    @DisplayName("ClassLevelFilterEngine — Branch Coverage")
    class ClassLevelFilterEngineBranchTests {

        @Test
        @DisplayName("ownerClass null → false")
        void ownerClassNull_returnsFalse() throws Exception {
            Field field = String.class.getDeclaredField("value");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(null, field, null)).isFalse();
        }

        @Test
        @DisplayName("field null → false")
        void fieldNull_returnsFalse() {
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(String.class, null, null)).isFalse();
        }

        @Test
        @DisplayName("excludePackages null → no package match")
        void excludePackagesNull_returnsFalse() throws Exception {
            Field field = String.class.getDeclaredField("value");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(String.class, field, null)).isFalse();
        }

        @Test
        @DisplayName("excludePackages empty → no package match")
        void excludePackagesEmpty_returnsFalse() throws Exception {
            Field field = String.class.getDeclaredField("value");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(String.class, field, List.of())).isFalse();
        }

        @Test
        @DisplayName("package match exact → true")
        void packageMatchExact_returnsTrue() throws Exception {
            Field field = String.class.getDeclaredField("value");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(String.class, field, List.of("java.lang"))).isTrue();
        }

        @Test
        @DisplayName("package match .** prefix → true")
        void packageMatchWildcard_returnsTrue() throws Exception {
            Field field = String.class.getDeclaredField("value");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(String.class, field, List.of("java.**"))).isTrue();
        }

        @Test
        @DisplayName("excludePackages contains null → skips null pattern")
        void excludePackagesContainsNull_skipsNull() throws Exception {
            Field field = String.class.getDeclaredField("value");
            List<String> packages = Arrays.asList(null, "other.pkg");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(String.class, field, packages)).isFalse();
        }

        @IgnoreDeclaredProperties
        static class IgnoreAllDeclared {
            public String x;
        }

        @IgnoreDeclaredProperties("ignored")
        static class IgnoreSpecified {
            public String kept;
            public String ignored;
        }

        @IgnoreInheritedProperties
        static class IgnoreInherited extends IgnoreSpecified {
            public String childField;
        }

        @Test
        @DisplayName("IgnoreDeclaredProperties empty array → ignore all declared")
        void ignoreDeclaredEmpty_ignoresAll() throws Exception {
            Field field = IgnoreAllDeclared.class.getDeclaredField("x");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(IgnoreAllDeclared.class, field, null)).isTrue();
        }

        @Test
        @DisplayName("IgnoreDeclaredProperties specified field → ignore that field")
        void ignoreDeclaredSpecified_ignoresField() throws Exception {
            Field field = IgnoreSpecified.class.getDeclaredField("ignored");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(IgnoreSpecified.class, field, null)).isTrue();
        }

        @Test
        @DisplayName("IgnoreDeclaredProperties — non-specified field not ignored")
        void ignoreDeclaredSpecified_keepsOther() throws Exception {
            Field field = IgnoreSpecified.class.getDeclaredField("kept");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(IgnoreSpecified.class, field, null)).isFalse();
        }

        @Test
        @DisplayName("IgnoreInheritedProperties — inherited field ignored")
        void ignoreInherited_ignoresInherited() throws Exception {
            Field field = IgnoreSpecified.class.getDeclaredField("kept");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(IgnoreInherited.class, field, null)).isTrue();
        }

        @Test
        @DisplayName("IgnoreInheritedProperties — declared field not ignored")
        void ignoreInherited_keepsDeclared() throws Exception {
            Field field = IgnoreInherited.class.getDeclaredField("childField");
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(IgnoreInherited.class, field, null)).isFalse();
        }

        @Test
        @DisplayName("package exact match with empty string")
        void packageExactMatchEmpty() throws Exception {
            @SuppressWarnings("unused")
            class LocalInTracking { public int x; }
            Field field = LocalInTracking.class.getDeclaredField("x");
            String pkg = LocalInTracking.class.getPackage() != null ? LocalInTracking.class.getPackage().getName() : "";
            assertThat(ClassLevelFilterEngine.shouldIgnoreByClass(LocalInTracking.class, field, List.of(pkg))).isTrue();
        }
    }

    @Nested
    @DisplayName("DefaultExclusionEngine — Branch Coverage")
    class DefaultExclusionEngineBranchTests {

        @Test
        @DisplayName("enabled false → false")
        void enabledFalse_returnsFalse() throws Exception {
            Field field = String.class.getDeclaredField("value");
            assertThat(DefaultExclusionEngine.isDefaultExcluded(field, false)).isFalse();
        }

        @Test
        @DisplayName("field null → false")
        void fieldNull_returnsFalse() {
            assertThat(DefaultExclusionEngine.isDefaultExcluded(null, true)).isFalse();
        }

        @Test
        @DisplayName("static modifier → true")
        void staticModifier_returnsTrue() throws Exception {
            Field field = FilterReason.class.getDeclaredField("INCLUDE_PATTERNS");
            assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isTrue();
        }

        @Test
        @DisplayName("transient modifier → true")
        void transientModifier_returnsTrue() throws Exception {
            @SuppressWarnings("unused")
            class WithTransient { transient int x; }
            Field field = WithTransient.class.getDeclaredField("x");
            assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isTrue();
        }

        @Test
        @DisplayName("synthetic field → true")
        void syntheticField_returnsTrue() throws Exception {
            class Outer {
                class Inner { }
            }
            Field[] fields = Outer.Inner.class.getDeclaredFields();
            for (Field f : fields) {
                if (f.isSynthetic()) {
                    assertThat(DefaultExclusionEngine.isDefaultExcluded(f, true)).isTrue();
                    return;
                }
            }
        }

        @Test
        @DisplayName("serialVersionUID → true")
        void serialVersionUID_returnsTrue() throws Exception {
            Field field = String.class.getDeclaredField("serialVersionUID");
            assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isTrue();
        }

        @Test
        @DisplayName("$jacocoData → true")
        void jacocoData_returnsTrue() throws Exception {
            try {
                Field field = DefaultExclusionEngine.class.getDeclaredField("$jacocoData");
                assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isTrue();
            } catch (NoSuchFieldException e) {
                @SuppressWarnings("unused")
                class WithJacoco { int $jacocoData; }
                Field field = WithJacoco.class.getDeclaredField("$jacocoData");
                assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isTrue();
            }
        }

        @Test
        @DisplayName("logger field name+type match → true")
        void loggerField_returnsTrue() throws Exception {
            Field field = DefaultExclusionEngine.class.getDeclaredField("logger");
            assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isTrue();
        }

        @Test
        @DisplayName("normal field → false")
        void normalField_returnsFalse() throws Exception {
            @SuppressWarnings("unused")
            class Normal { private String name; }
            Field field = Normal.class.getDeclaredField("name");
            assertThat(DefaultExclusionEngine.isDefaultExcluded(field, true)).isFalse();
        }

        @Test
        @DisplayName("getSupportedExclusionTypes → returns array")
        void getSupportedExclusionTypes_returnsArray() {
            String[] types = DefaultExclusionEngine.getSupportedExclusionTypes();
            assertThat(types).isNotEmpty();
            assertThat(types).anyMatch(s -> s.contains("static"));
        }
    }

    @Nested
    @DisplayName("PathMatcher — Branch Coverage")
    class PathMatcherBranchTests {

        @Test
        @DisplayName("matchGlob — path null → false")
        void matchGlob_pathNull_returnsFalse() {
            assertThat(PathMatcher.matchGlob(null, "*.x")).isFalse();
        }

        @Test
        @DisplayName("matchGlob — glob null → false")
        void matchGlob_globNull_returnsFalse() {
            assertThat(PathMatcher.matchGlob("a.b", null)).isFalse();
        }

        @Test
        @DisplayName("matchRegex — path null → false")
        void matchRegex_pathNull_returnsFalse() {
            assertThat(PathMatcher.matchRegex(null, ".*")).isFalse();
        }

        @Test
        @DisplayName("matchRegex — regex null → false")
        void matchRegex_regexNull_returnsFalse() {
            assertThat(PathMatcher.matchRegex("a", null)).isFalse();
        }

        @Test
        @DisplayName("matchRegex — invalid regex → false")
        void matchRegex_invalidRegex_returnsFalse() {
            assertThat(PathMatcher.matchRegex("test", "[invalid")).isFalse();
        }

        @Test
        @DisplayName("matchRegex — valid match → true")
        void matchRegex_validMatch_returnsTrue() {
            assertThat(PathMatcher.matchRegex("user.name", "user\\..*")).isTrue();
        }

        @Test
        @DisplayName("matchGlob — ** at end")
        void matchGlob_doubleStarAtEnd() {
            assertThat(PathMatcher.matchGlob("order.items.name", "order.**")).isTrue();
        }

        @Test
        @DisplayName("matchGlob — [*] array index")
        void matchGlob_arrayIndex() {
            assertThat(PathMatcher.matchGlob("items[0].id", "items[*].id")).isTrue();
        }

        @Test
        @DisplayName("matchGlob — ? single char")
        void matchGlob_singleChar() {
            assertThat(PathMatcher.matchGlob("a", "?")).isTrue();
        }

        @Test
        @DisplayName("getCacheSize — returns sum")
        void getCacheSize_returnsSum() {
            PathMatcher.clearCache();
            PathMatcher.matchGlob("x", "y");
            assertThat(PathMatcher.getCacheSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("clearCache — clears")
        void clearCache_clears() {
            PathMatcher.matchGlob("a", "b");
            PathMatcher.clearCache();
            assertThat(PathMatcher.getCacheSize()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("FilterDecision — Branch Coverage")
    class FilterDecisionBranchTests {

        @Test
        @DisplayName("include — shouldExclude false, shouldInclude true")
        void include_shouldExcludeFalse() {
            FilterDecision d = FilterDecision.include("r");
            assertThat(d.shouldExclude()).isFalse();
            assertThat(d.shouldInclude()).isTrue();
        }

        @Test
        @DisplayName("exclude — shouldExclude true, shouldInclude false")
        void exclude_shouldExcludeTrue() {
            FilterDecision d = FilterDecision.exclude("r");
            assertThat(d.shouldExclude()).isTrue();
            assertThat(d.shouldInclude()).isFalse();
        }
    }

    @Nested
    @DisplayName("FilterReason — Constants")
    class FilterReasonBranchTests {

        @Test
        @DisplayName("All reason constants defined")
        void allConstantsDefined() {
            assertThat(FilterReason.INCLUDE_PATTERNS).isEqualTo("includePatterns");
            assertThat(FilterReason.EXCLUDE_PATTERNS).isEqualTo("excludePatterns");
            assertThat(FilterReason.REGEX_EXCLUDES).isEqualTo("regexExcludes");
            assertThat(FilterReason.EXCLUDE_PACKAGES).isEqualTo("class/package");
            assertThat(FilterReason.DEFAULT_EXCLUSIONS).isEqualTo("defaultExclusions");
            assertThat(FilterReason.DEFAULT_RETAIN).isEqualTo("default");
        }
    }
}
