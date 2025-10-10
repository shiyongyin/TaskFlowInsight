package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FieldChange.typed view generation and container event edge cases.
 */
class FieldChangeTypedViewTests {

    private Clock originalClock;

    @BeforeEach
    void setupClock() {
        originalClock = FieldChange.getClock();
    }

    @AfterEach
    void restoreClock() {
        FieldChange.setClock(originalClock);
    }

    @Test
    void mixedContainerPaths_shouldExportNestedPathCorrectly() {
        // Map value property change under list element (simulated)
        FieldChange mapModify = FieldChange.builder()
            .fieldName("items[2].map[A].order.price")
            .fieldPath("items[2].map[A].order.price")
            .oldValue(10)
            .newValue(20)
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .elementEvent(
                ContainerEvents.mapEvent(
                    FieldChange.ElementOperation.MODIFY,
                    "A",
                    "order[O9]",
                    "price"
                )
            )
            .build();

        Map<String, Object> view = mapModify.toTypedView();
        assertThat(view).isNotNull();
        assertThat(view.get("kind")).isEqualTo("entry_updated");
        assertThat(view.get("object")).isEqualTo("items");
        assertThat(view.get("path")).isEqualTo("items[2].map[A].order.price");
        @SuppressWarnings("unchecked") Map<String,Object> details = (Map<String, Object>) view.get("details");
        assertThat(details.get("operation")).isEqualTo("MODIFY");
        assertThat(details.get("mapKey")).isEqualTo("A");
        assertThat(details.get("propertyPath")).isEqualTo("price");
        assertThat(details.get("oldValue")).isEqualTo(10);
        assertThat(details.get("newValue")).isEqualTo(20);
    }

    @Test
    void listMove_edgeJump_shouldIncludeIndices() {
        FieldChange move = FieldChange.builder()
            .fieldName("items[9]")
            .fieldPath("items[9]")
            .oldValue("X")
            .newValue("X")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.MOVE)
            .elementEvent(ContainerEvents.listMove(0, 9, "item[SKU-1]"))
            .build();

        Map<String, Object> view = move.toTypedView();
        assertThat(view).isNotNull();
        assertThat(view.get("kind")).isEqualTo("entry_moved");
        assertThat(view.get("object")).isEqualTo("items");
        @SuppressWarnings("unchecked") Map<String,Object> details = (Map<String, Object>) view.get("details");
        assertThat(details.get("oldIndex")).isEqualTo(0);
        assertThat(details.get("newIndex")).isEqualTo(9);
    }

    @Test
    void duplicateKeyWithMove_shouldNotThrow_andExportView() {
        FieldChange.ContainerElementEvent event = FieldChange.ContainerElementEvent.builder()
            .containerType(FieldChange.ContainerType.LIST)
            .operation(FieldChange.ElementOperation.MOVE)
            .oldIndex(3)
            .newIndex(1)
            .entityKey("entity[1001]")
            .duplicateKey(true)
            .build();

        FieldChange change = FieldChange.builder()
            .fieldName("entity[1001]")
            .fieldPath("entity[1001]")
            .oldValue("E")
            .newValue("E")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.MOVE)
            .elementEvent(event)
            .build();

        Map<String, Object> view = change.toTypedView();
        assertThat(view).isNotNull();
        assertThat(view.get("kind")).isEqualTo("entry_moved");
        // duplicateKey is an internal hint; not exported but must not break
        @SuppressWarnings("unchecked") Map<String,Object> details = (Map<String, Object>) view.get("details");
        assertThat(details.get("oldIndex")).isEqualTo(3);
        assertThat(details.get("newIndex")).isEqualTo(1);
    }

    @Test
    void propertyPath_blank_shouldBeToleratedAndOmitted() {
        FieldChange change = FieldChange.builder()
            .fieldName("items[0].map[A]")
            .fieldPath("items[0].map[A]")
            .oldValue(1)
            .newValue(2)
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .elementEvent(ContainerEvents.mapEvent(FieldChange.ElementOperation.MODIFY, "A", null, "  "))
            .build();

        Map<String, Object> view = change.toTypedView();
        @SuppressWarnings("unchecked") Map<String,Object> details = (Map<String, Object>) view.get("details");
        assertThat(details.containsKey("propertyPath")).isFalse();
    }

    @Test
    void typedView_shouldUseInjectedClockForTimestamp() {
        Instant fixed = Instant.parse("2025-10-08T12:00:00Z");
        FieldChange.setClock(Clock.fixed(fixed, ZoneOffset.UTC));

        FieldChange change = FieldChange.builder()
            .fieldName("items[1]")
            .fieldPath("items[1]")
            .oldValue(null)
            .newValue("X")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.CREATE)
            .elementEvent(ContainerEvents.listAdd(1, "item[SKU-2]"))
            .build();

        Map<String, Object> view = change.toTypedView();
        assertThat(view.get("timestamp")).isEqualTo("2025-10-08T12:00:00Z");
    }
}

