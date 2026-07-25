package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证容器事件便捷入口不会遗漏类型、操作和定位字段。 */
class ContainerEventsContractTests {

    @Test
    void shouldBuildListEventsAndGuardMissingMoveIndexes() {
        assertThat(ContainerEvents.listAdd(1, "A"))
                .extracting(FieldChange.ContainerElementEvent::getContainerType,
                        FieldChange.ContainerElementEvent::getOperation,
                        FieldChange.ContainerElementEvent::getIndex)
                .containsExactly(FieldChange.ContainerType.LIST, FieldChange.ElementOperation.ADD, 1);
        assertThat(ContainerEvents.listAdd(1, "A", true).isDuplicateKey()).isTrue();
        assertThat(ContainerEvents.listRemove(2, "B").getOperation())
                .isEqualTo(FieldChange.ElementOperation.REMOVE);
        assertThat(ContainerEvents.listRemove(2, "B", true).isDuplicateKey()).isTrue();
        assertThat(ContainerEvents.listModify(3, "C", "name").getPropertyPath()).isEqualTo("name");

        assertThat(ContainerEvents.listMove(null, 2, "A").getOldIndex()).isNull();
        assertThat(ContainerEvents.listMove(1, null, "A").getNewIndex()).isNull();
        assertThat(ContainerEvents.listMove(1, 2, "A"))
                .extracting(FieldChange.ContainerElementEvent::getOldIndex,
                        FieldChange.ContainerElementEvent::getNewIndex)
                .containsExactly(1, 2);
    }

    @Test
    void shouldBuildMapEventsAcrossPropertyPathBranches() {
        assertThat(ContainerEvents.mapEvent(
                FieldChange.ElementOperation.MODIFY, "key", "entity", null).getPropertyPath()).isNull();
        assertThat(ContainerEvents.mapEvent(
                FieldChange.ElementOperation.MODIFY, "key", "entity", "  ").getPropertyPath()).isBlank();
        assertThat(ContainerEvents.mapEvent(
                FieldChange.ElementOperation.MODIFY, "key", "entity", "name").getPropertyPath())
                .isEqualTo("name");

        FieldChange.ContainerElementEvent added = ContainerEvents.mapEvent(
                FieldChange.ElementOperation.ADD, "key", "entity", null);
        assertThat(added.getContainerType()).isEqualTo(FieldChange.ContainerType.MAP);
        assertThat(added.getMapKey()).isEqualTo("key");
    }

    @Test
    void shouldBuildSetAndArrayEvents() {
        assertThat(ContainerEvents.setEvent(
                FieldChange.ElementOperation.MODIFY, "entity", "name", true))
                .satisfies(event -> {
                    assertThat(event.getContainerType()).isEqualTo(FieldChange.ContainerType.SET);
                    assertThat(event.isDuplicateKey()).isTrue();
                });
        assertThat(ContainerEvents.arrayAdd(1).getOperation()).isEqualTo(FieldChange.ElementOperation.ADD);
        assertThat(ContainerEvents.arrayModify(2).getOperation()).isEqualTo(FieldChange.ElementOperation.MODIFY);
        assertThat(ContainerEvents.arrayRemove(3).getOperation()).isEqualTo(FieldChange.ElementOperation.REMOVE);
    }
}
