package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * 实体比较系统测试。
 * 覆盖 EntityListDiffResult、EntityChangeGroup、EntityOperation 的业务逻辑。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Entity Diff — 实体比较测试")
class EntityDiffTests {

    // ── EntityOperation ──

    @Nested
    @DisplayName("EntityOperation — 操作类型")
    class EntityOperationTests {

        @Test
        @DisplayName("所有操作类型有 displayName")
        void allOperations_shouldHaveDisplayName() {
            for (EntityOperation op : EntityOperation.values()) {
                assertThat(op.getDisplayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("包含 ADD、MODIFY、DELETE")
        void shouldContainAllOperations() {
            assertThat(EntityOperation.values()).hasSize(3);
            assertThat(EntityOperation.valueOf("ADD")).isNotNull();
            assertThat(EntityOperation.valueOf("MODIFY")).isNotNull();
            assertThat(EntityOperation.valueOf("DELETE")).isNotNull();
        }
    }

    // ── EntityChangeGroup ──

    @Nested
    @DisplayName("EntityChangeGroup — 变更分组")
    class EntityChangeGroupTests {

        @Test
        @DisplayName("Builder 构建完整分组")
        void builder_shouldCreateGroup() {
            FieldChange change = FieldChange.builder()
                    .fieldName("name")
                    .fieldPath("entity.name")
                    .oldValue("old")
                    .newValue("new")
                    .changeType(ChangeType.UPDATE)
                    .build();

            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1001]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(change))
                    .build();

            assertThat(group.getEntityKey()).isEqualTo("entity[1001]");
            assertThat(group.getOperation()).isEqualTo(EntityOperation.MODIFY);
            assertThat(group.hasChanges()).isTrue();
            assertThat(group.getChangeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getFieldChanges 按字段名查询")
        void getFieldChanges_shouldFilterByFieldName() {
            FieldChange nameChange = FieldChange.builder()
                    .fieldName("name")
                    .fieldPath("entity.name")
                    .oldValue("old")
                    .newValue("new")
                    .changeType(ChangeType.UPDATE)
                    .build();
            FieldChange ageChange = FieldChange.builder()
                    .fieldName("age")
                    .fieldPath("entity.age")
                    .oldValue(20)
                    .newValue(30)
                    .changeType(ChangeType.UPDATE)
                    .build();

            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("user[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(nameChange, ageChange))
                    .build();

            List<FieldChange> nameChanges = group.getFieldChanges("name");
            assertThat(nameChanges).hasSize(1);
            assertThat(nameChanges.get(0).getFieldName()).isEqualTo("name");
        }

        @Test
        @DisplayName("空 changes → hasChanges=false")
        void emptyChanges_shouldNotHaveChanges() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.ADD)
                    .changes(Collections.emptyList())
                    .build();
            assertThat(group.hasChanges()).isFalse();
            assertThat(group.getChangeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("isMoved 和 isDegraded 标志")
        void moveAndDegradedFlags_shouldWork() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.MODIFY)
                    .changes(Collections.emptyList())
                    .moved(true)
                    .degraded(false)
                    .build();
            assertThat(group.isMoved()).isTrue();
            assertThat(group.isDegraded()).isFalse();
        }
    }

    // ── EntityListDiffResult ──

    @Nested
    @DisplayName("EntityListDiffResult — 列表比较结果")
    class EntityListDiffResultTests {

        @Test
        @DisplayName("empty() → 空结果")
        void empty_shouldReturnEmptyResult() {
            EntityListDiffResult result = EntityListDiffResult.empty();
            assertThat(result.hasChanges()).isFalse();
            assertThat(result.getGroups()).isEmpty();
            assertThat(result.getAddedEntities()).isEmpty();
            assertThat(result.getModifiedEntities()).isEmpty();
            assertThat(result.getDeletedEntities()).isEmpty();
        }

        @Test
        @DisplayName("Builder 构建包含多种操作的结果")
        void builder_shouldCreateMixedResult() {
            EntityChangeGroup addGroup = EntityChangeGroup.builder()
                    .entityKey("entity[new]")
                    .operation(EntityOperation.ADD)
                    .changes(List.of(
                            FieldChange.builder().fieldName("name").fieldPath("entity.name")
                                    .changeType(ChangeType.CREATE).newValue("New Item").build()
                    ))
                    .build();

            EntityChangeGroup modifyGroup = EntityChangeGroup.builder()
                    .entityKey("entity[1001]")
                    .operation(EntityOperation.MODIFY)
                    .changes(List.of(
                            FieldChange.builder().fieldName("status").fieldPath("entity.status")
                                    .changeType(ChangeType.UPDATE).oldValue("ACTIVE").newValue("INACTIVE").build()
                    ))
                    .build();

            EntityChangeGroup deleteGroup = EntityChangeGroup.builder()
                    .entityKey("entity[999]")
                    .operation(EntityOperation.DELETE)
                    .changes(Collections.emptyList())
                    .build();

            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(addGroup, modifyGroup, deleteGroup))
                    .build();

            assertThat(result.hasChanges()).isTrue();
            assertThat(result.getAddedEntities()).hasSize(1);
            assertThat(result.getModifiedEntities()).hasSize(1);
            assertThat(result.getDeletedEntities()).hasSize(1);
        }

        @Test
        @DisplayName("getGroupByKey → 按键查询")
        void getGroupByKey_shouldFindByKey() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("order[42]")
                    .operation(EntityOperation.MODIFY)
                    .changes(Collections.emptyList())
                    .build();

            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(group))
                    .build();

            Optional<EntityChangeGroup> found = result.getGroupByKey("order[42]");
            assertThat(found).isPresent();
            assertThat(found.get().getEntityKey()).isEqualTo("order[42]");
        }

        @Test
        @DisplayName("getGroupByKey 不存在 → empty")
        void getGroupByKey_notFound_shouldReturnEmpty() {
            EntityListDiffResult result = EntityListDiffResult.empty();
            Optional<EntityChangeGroup> found = result.getGroupByKey("nonexistent");
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("getStatistics → 有效统计")
        void getStatistics_shouldReturnValidStats() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.ADD)
                    .changes(List.of(FieldChange.builder()
                            .fieldName("f").fieldPath("f")
                            .changeType(ChangeType.CREATE).build()))
                    .build();

            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(group))
                    .build();

            var stats = result.getStatistics();
            assertThat(stats).isNotNull();
        }

        @Test
        @DisplayName("getSummary → 可读摘要")
        void getSummary_shouldReturnReadableSummary() {
            EntityListDiffResult result = EntityListDiffResult.empty();
            String summary = result.getSummary();
            assertThat(summary).isNotBlank();
        }

        @Test
        @DisplayName("from CompareResult → 转换成功")
        void fromCompareResult_shouldConvert() {
            FieldChange change = FieldChange.builder()
                    .fieldName("status")
                    .fieldPath("entity.status")
                    .oldValue("A")
                    .newValue("B")
                    .changeType(ChangeType.UPDATE)
                    .build();

            CompareResult compareResult = CompareResult.builder()
                    .changes(List.of(change))
                    .identical(false)
                    .build();

            EntityListDiffResult result = EntityListDiffResult.from(compareResult);
            assertThat(result).isNotNull();
        }
    }
}
