package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.ContainerEvents;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Map 容器相关的实体列表分组/键解析测试（去嵌套）
 */
class EntityListDiffMapTests {

    @Test
    void testMapWithEntityValueUsesEntityKey() {
        // Given: Map<String, Entity> 场景，值为实体，事件提供 entityKey
        FieldChange addChange = FieldChange.builder()
            .fieldPath("map[key-1]") // 路径存在，用于兼容
            .changeType(ChangeType.CREATE)
            .newValue(Map.of("id", "A"))
            .elementEvent(ContainerEvents.mapEvent(FieldChange.ElementOperation.ADD, "key-1", "entity[id=A]", null))
            .build();

        CompareResult cr = CompareResult.builder().changes(List.of(addChange)).build();

        // When
        EntityListDiffResult result = EntityListDiffResult.from(cr);

        // Then: 应按实体键分组
        assertTrue(result.getGroupByKey("entity[id=A]").isPresent());
        EntityChangeGroup g = result.getGroupByKey("entity[id=A]").orElseThrow();
        assertEquals(EntityOperation.ADD, g.getOperation());
        assertFalse(g.isDegraded(), "事件中有实体键，不降级");
    }

    @Test
    void testMapWithPlainValueFallsBackToMapKeyParsing() {
        // Given: Map<String, String> 场景，值非实体，entityKey=null，应回退为 map[key] 分组
        FieldChange modChange = FieldChange.builder()
            .fieldPath("map[user-42].value")
            .changeType(ChangeType.UPDATE)
            .oldValue("v1")
            .newValue("v2")
            .elementEvent(ContainerEvents.mapEvent(FieldChange.ElementOperation.MODIFY, "user-42", null, "value"))
            .build();

        CompareResult cr = CompareResult.builder().changes(List.of(modChange)).build();

        // When
        EntityListDiffResult result = EntityListDiffResult.from(cr);

        // Then: 退化为 map[...] 键分组（路径解析）
        assertTrue(result.getGroupByKey("map[user-42]").isPresent());
        EntityChangeGroup g = result.getGroupByKey("map[user-42]").orElseThrow();
        assertEquals(EntityOperation.MODIFY, g.getOperation());
        assertTrue(g.isDegraded(), "缺少实体键，依赖路径解析应标记降级");
    }

    @Test
    void testMapNullKeyHandledGracefully() {
        // Given: Map 的 key 可能为 null（极端场景），确保不会抛异常
        FieldChange delChange = FieldChange.builder()
            .fieldPath("map[null]")
            .changeType(ChangeType.DELETE)
            .oldValue("v")
            .elementEvent(ContainerEvents.mapEvent(FieldChange.ElementOperation.REMOVE, null, null, null))
            .build();

        CompareResult cr = CompareResult.builder().changes(List.of(delChange)).build();

        // When
        EntityListDiffResult result = EntityListDiffResult.from(cr);

        // Then: 应能生成一个分组（键来源于路径），且降级标记
        assertTrue(result.getGroups().size() >= 1);
        assertTrue(result.getGroups().stream().anyMatch(g -> g.isDegraded()));
    }
}

