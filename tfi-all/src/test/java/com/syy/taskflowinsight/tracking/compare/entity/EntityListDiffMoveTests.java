package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.ContainerEvents;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MOVE 语义与索引填充场景测试（去嵌套）
 */
class EntityListDiffMoveTests {

    @Test
    void testMoveEventSetsMovedFlagAndIndices() {
        // Given: 同一实体从索引1 → 3 的移动（使用结构化容器事件）
        FieldChange moveChange = FieldChange.builder()
            .fieldPath("entity[id=A]") // 路径存在但不用于索引
            .changeType(ChangeType.MOVE)
            .oldValue("A")
            .newValue("A")
            .elementEvent(ContainerEvents.listMove(1, 3, "entity[id=A]"))
            .build();

        CompareResult cr = CompareResult.builder()
            .changes(List.of(moveChange))
            .build();

        // When
        EntityListDiffResult result = EntityListDiffResult.from(cr);

        // Then
        EntityChangeGroup gA = result.getGroupByKey("entity[id=A]").orElseThrow();
        assertEquals(EntityOperation.MODIFY, gA.getOperation(), "MOVE 被折叠为实体层级的 MODIFY");
        assertTrue(gA.isMoved(), "应标记 moved=true");
        assertEquals(Integer.valueOf(1), gA.getOldIndex());
        assertEquals(Integer.valueOf(3), gA.getNewIndex());
        assertFalse(gA.isDegraded(), "使用事件索引，不应降级");
    }
}

