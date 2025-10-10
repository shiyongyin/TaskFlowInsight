package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ContainerEvents 工具方法输出的容器事件字段正确。
 */
class ContainerEventsFactoryTests {

    @Test
    void listFactories_shouldFillAllFields() {
        var add = ContainerEvents.listAdd(2, "entity[1]");
        assertThat(add.getContainerType()).isEqualTo(FieldChange.ContainerType.LIST);
        assertThat(add.getOperation()).isEqualTo(FieldChange.ElementOperation.ADD);
        assertThat(add.getIndex()).isEqualTo(2);
        assertThat(add.getEntityKey()).isEqualTo("entity[1]");
        assertThat(add.isDuplicateKey()).isFalse();

        var remove = ContainerEvents.listRemove(3, "entity[2]");
        assertThat(remove.getContainerType()).isEqualTo(FieldChange.ContainerType.LIST);
        assertThat(remove.getOperation()).isEqualTo(FieldChange.ElementOperation.REMOVE);
        assertThat(remove.getIndex()).isEqualTo(3);
        assertThat(remove.getEntityKey()).isEqualTo("entity[2]");

        var modify = ContainerEvents.listModify(1, "entity[9]", "price");
        assertThat(modify.getOperation()).isEqualTo(FieldChange.ElementOperation.MODIFY);
        assertThat(modify.getIndex()).isEqualTo(1);
        assertThat(modify.getPropertyPath()).isEqualTo("price");

        var move = ContainerEvents.listMove(0, 5, "entity[8]");
        assertThat(move.getOperation()).isEqualTo(FieldChange.ElementOperation.MOVE);
        assertThat(move.getOldIndex()).isEqualTo(0);
        assertThat(move.getNewIndex()).isEqualTo(5);
        assertThat(move.getEntityKey()).isEqualTo("entity[8]");
    }

    @Test
    void setFactory_shouldSupportDuplicateKey() {
        var e = ContainerEvents.setEvent(FieldChange.ElementOperation.ADD, "entity[1]", null, true);
        assertThat(e.getContainerType()).isEqualTo(FieldChange.ContainerType.SET);
        assertThat(e.getOperation()).isEqualTo(FieldChange.ElementOperation.ADD);
        assertThat(e.getEntityKey()).isEqualTo("entity[1]");
        assertThat(e.isDuplicateKey()).isTrue();
    }

    @Test
    void mapFactory_shouldFillKeyAndPropertyPath() {
        var m = ContainerEvents.mapEvent(FieldChange.ElementOperation.MODIFY, "A", "entity[7]", "price");
        assertThat(m.getContainerType()).isEqualTo(FieldChange.ContainerType.MAP);
        assertThat(m.getOperation()).isEqualTo(FieldChange.ElementOperation.MODIFY);
        assertThat(m.getMapKey()).isEqualTo("A");
        assertThat(m.getEntityKey()).isEqualTo("entity[7]");
        assertThat(m.getPropertyPath()).isEqualTo("price");
    }
}

