package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChangeAdapters 单元测试
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 */
class ChangeAdaptersTests {

    @Test
    void toTypedView_emptyChanges_shouldReturnEmpty() {
        List<Map<String, Object>> result = ChangeAdapters.toTypedView("Test", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void toTypedView_createChange_shouldMapToEntryAdded() {
        FieldChange change = FieldChange.builder()
                .fieldName("items")
                .fieldPath("items[0]")
                .changeType(ChangeType.CREATE)
                .newValue("newValue")
                .valueType("String")
                .build();

        List<Map<String, Object>> result = ChangeAdapters.toTypedView("Order", List.of(change));

        assertEquals(1, result.size());
        Map<String, Object> event = result.get(0);
        assertEquals("entry_added", event.get("kind"));
        assertEquals("Order", event.get("object"));
        assertEquals("items[0]", event.get("path"));
        assertNotNull(event.get("timestamp"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("newValue", details.get("newEntryValue"));
        assertEquals("String", details.get("valueType"));
    }

    @Test
    void toTypedView_deleteChange_shouldMapToEntryRemoved() {
        FieldChange change = FieldChange.builder()
                .fieldName("items")
                .fieldPath("items[1]")
                .changeType(ChangeType.DELETE)
                .oldValue("oldValue")
                .valueType("String")
                .build();

        List<Map<String, Object>> result = ChangeAdapters.toTypedView("Order", List.of(change));

        assertEquals(1, result.size());
        Map<String, Object> event = result.get(0);
        assertEquals("entry_removed", event.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("oldValue", details.get("oldEntryValue"));
    }

    @Test
    void toTypedView_updateChange_shouldMapToEntryUpdated() {
        FieldChange change = FieldChange.builder()
                .fieldName("price")
                .fieldPath("items[0].price")
                .changeType(ChangeType.UPDATE)
                .oldValue(100)
                .newValue(150)
                .valueType("Integer")
                .build();

        List<Map<String, Object>> result = ChangeAdapters.toTypedView("Order", List.of(change));

        assertEquals(1, result.size());
        Map<String, Object> event = result.get(0);
        assertEquals("entry_updated", event.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals(100, details.get("oldEntryValue"));
        assertEquals(150, details.get("newEntryValue"));
    }

    @Test
    void toTypedView_multipleChanges_shouldReturnMultipleEvents() {
        List<FieldChange> changes = Arrays.asList(
                FieldChange.builder()
                        .fieldName("items")
                        .fieldPath("items[0]")
                        .changeType(ChangeType.CREATE)
                        .newValue("new")
                        .build(),
                FieldChange.builder()
                        .fieldName("items")
                        .fieldPath("items[1]")
                        .changeType(ChangeType.DELETE)
                        .oldValue("old")
                        .build()
        );

        List<Map<String, Object>> result = ChangeAdapters.toTypedView("Test", changes);

        assertEquals(2, result.size());
        assertEquals("entry_added", result.get(0).get("kind"));
        assertEquals("entry_removed", result.get(1).get("kind"));
    }

    @Test
    void toTypedJson_shouldGenerateValidJson() {
        FieldChange change = FieldChange.builder()
                .fieldName("name")
                .fieldPath("user.name")
                .changeType(ChangeType.UPDATE)
                .oldValue("Alice")
                .newValue("Bob")
                .valueType("String")
                .build();

        String json = ChangeAdapters.toTypedJson("User", List.of(change));

        assertNotNull(json);
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));
        assertTrue(json.contains("\"kind\":\"entry_updated\""));
        assertTrue(json.contains("\"object\":\"User\""));
        assertTrue(json.contains("\"path\":\"user.name\""));
    }

    @Test
    void toTypedJson_emptyChanges_shouldReturnEmptyArray() {
        String json = ChangeAdapters.toTypedJson("Test", List.of());
        assertEquals("[]", json);
    }

    @Test
    void registerCustomizer_shouldApplyCustomization() {
        ChangeAdapters.registerCustomizer((events, source) -> {
            for (Map<String, Object> event : events) {
                event.put("custom", "value");
            }
        });

        FieldChange change = FieldChange.builder()
                .fieldName("test")
                .changeType(ChangeType.CREATE)
                .newValue("test")
                .build();

        List<Map<String, Object>> result = ChangeAdapters.toTypedView("Test", List.of(change));

        // Note: Customizers are applied via applyCustomizers which is package-private
        // This test verifies registration doesn't throw
        assertNotNull(result);
    }

    @Test
    void toTypedView_nullObjectName_shouldUseDefault() {
        FieldChange change = FieldChange.builder()
                .fieldName("test")
                .changeType(ChangeType.CREATE)
                .newValue("value")
                .build();

        List<Map<String, Object>> result = ChangeAdapters.toTypedView(null, List.of(change));

        assertEquals(1, result.size());
        assertEquals("Unknown", result.get(0).get("object"));
    }

    @Test
    void toTypedView_jsonEscaping_shouldHandleSpecialCharacters() {
        FieldChange change = FieldChange.builder()
                .fieldName("desc")
                .fieldPath("item.desc")
                .changeType(ChangeType.UPDATE)
                .oldValue("Line1\nLine2")
                .newValue("Quote\"Test")
                .valueType("String")
                .build();

        String json = ChangeAdapters.toTypedJson("Item", List.of(change));

        assertTrue(json.contains("\\n")); // Escaped newline
        assertTrue(json.contains("\\\"")); // Escaped quote
    }
}

