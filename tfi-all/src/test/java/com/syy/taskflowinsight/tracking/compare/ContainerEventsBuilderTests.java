package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ContainerEvents 工具类的各构造方法填充字段正确。
 */
class ContainerEventsBuilderTests {

    @Test
    void list_builders_fill_expected_fields() {
        FieldChange.ContainerElementEvent add = ContainerEvents.listAdd(2, "entity[E1]");
        assertEquals(FieldChange.ContainerType.LIST, add.getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, add.getOperation());
        assertEquals(2, add.getIndex());
        assertEquals("entity[E1]", add.getEntityKey());

        FieldChange.ContainerElementEvent removeDup = ContainerEvents.listRemove(3, "entity[E2]", true);
        assertEquals(FieldChange.ElementOperation.REMOVE, removeDup.getOperation());
        assertEquals(3, removeDup.getIndex());
        assertTrue(removeDup.isDuplicateKey());

        FieldChange.ContainerElementEvent modify = ContainerEvents.listModify(1, "entity[E3]", "price");
        assertEquals(FieldChange.ElementOperation.MODIFY, modify.getOperation());
        assertEquals(1, modify.getIndex());
        assertEquals("price", modify.getPropertyPath());

        FieldChange.ContainerElementEvent move = ContainerEvents.listMove(4, 7, "entity[E4]");
        assertEquals(FieldChange.ElementOperation.MOVE, move.getOperation());
        assertNull(move.getIndex());
        assertEquals(4, move.getOldIndex());
        assertEquals(7, move.getNewIndex());
    }

    @Test
    void set_and_map_and_array_builders_fill_expected_fields() {
        FieldChange.ContainerElementEvent setEv = ContainerEvents.setEvent(FieldChange.ElementOperation.ADD, "entity[E1]", null, false);
        assertEquals(FieldChange.ContainerType.SET, setEv.getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, setEv.getOperation());
        assertEquals("entity[E1]", setEv.getEntityKey());

        FieldChange.ContainerElementEvent mapEv = ContainerEvents.mapEvent(FieldChange.ElementOperation.MODIFY, "K1", "entity[M1]", "amount");
        assertEquals(FieldChange.ContainerType.MAP, mapEv.getContainerType());
        assertEquals("K1", mapEv.getMapKey());
        assertEquals("amount", mapEv.getPropertyPath());

        FieldChange.ContainerElementEvent arrAdd = ContainerEvents.arrayAdd(5);
        assertEquals(FieldChange.ContainerType.ARRAY, arrAdd.getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, arrAdd.getOperation());
        assertEquals(5, arrAdd.getIndex());
    }
}

