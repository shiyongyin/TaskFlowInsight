package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompareResultTypedQueryAPITests {

    @Test
    void group_by_container_operation_typed_should_work() {
        FieldChange listAdd = FieldChange.builder()
            .fieldName("items")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.CREATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .build())
            .build();

        FieldChange mapModify = FieldChange.builder()
            .fieldName("config.key")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.MODIFY)
                .mapKey("key")
                .build())
            .build();

        CompareResult result = CompareResult.builder()
            .changes(List.of(listAdd, mapModify))
            .identical(false)
            .build();

        Map<FieldChange.ElementOperation, List<FieldChange>> byOp = result.groupByContainerOperationTyped();
        assertEquals(2, byOp.size());
        assertEquals(1, byOp.get(FieldChange.ElementOperation.ADD).size());
        assertEquals(1, byOp.get(FieldChange.ElementOperation.MODIFY).size());
    }

    @Test
    void get_container_changes_by_type_should_filter_correctly() {
        FieldChange listAdd = FieldChange.builder()
            .fieldName("items")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.CREATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(1)
                .build())
            .build();

        FieldChange mapModify = FieldChange.builder()
            .fieldName("config.key")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.MODIFY)
                .mapKey("key")
                .build())
            .build();

        CompareResult result = CompareResult.builder()
            .changes(List.of(listAdd, mapModify))
            .identical(false)
            .build();

        List<FieldChange> listOnly = result.getContainerChangesByType(FieldChange.ContainerType.LIST);
        assertEquals(1, listOnly.size());
        assertEquals(FieldChange.ContainerType.LIST, listOnly.get(0).getElementEvent().getContainerType());

        List<FieldChange> all = result.getContainerChangesByType();
        assertEquals(2, all.size());
    }
}

