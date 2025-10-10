package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 降级路径（缺少容器事件）测试
 */
class EntityListDiffDegradeTests {

    @Test
    void testDegradedWhenNoElementEventPresent() {
        // Given: 无 elementEvent，仅路径信息
        FieldChange c = FieldChange.builder()
            .fieldPath("entity[id=E].name")
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .oldValue("n1")
            .newValue("n2")
            .build();

        CompareResult cr = CompareResult.builder().changes(List.of(c)).build();

        // When
        EntityListDiffResult result = EntityListDiffResult.from(cr);

        // Then
        EntityChangeGroup g = result.getGroupByKey("entity[id=E]").orElseThrow();
        assertEquals(EntityOperation.MODIFY, g.getOperation());
        assertTrue(g.isDegraded(), "缺少结构化事件，应标记降级");
    }
}

