package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * toTypedView() Map 视图导出测试
 * @since v3.1.0-P1
 */
class TypedViewExportTests {

    @Test
    void typed_view_for_list_add_contains_standard_keys() {
        FieldChange change = FieldChange.builder()
            .fieldName("items")
            .fieldPath("items[2]")
            .changeType(ChangeType.CREATE)
            .newValue("NewItem")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(2)
                .entityKey("item[SKU-123]")
                .build())
            .build();

        Map<String, Object> view = change.toTypedView();

        assertNotNull(view);
        assertEquals("entry_added", view.get("kind"));
        assertEquals("items", view.get("object"));
        assertEquals("items[2]", view.get("path"));
        assertNotNull(view.get("timestamp"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("LIST", details.get("containerType"));
        assertEquals("ADD", details.get("operation"));
        assertEquals(2, details.get("index"));
        assertEquals("item[SKU-123]", details.get("entityKey"));
        assertNull(details.get("oldValue"));
        assertEquals("NewItem", details.get("newValue"));
    }

    @Test
    void typed_view_for_list_remove_contains_entry_removed_kind() {
        FieldChange change = FieldChange.builder()
            .fieldName("items")
            .fieldPath("items[1]")
            .changeType(ChangeType.DELETE)
            .oldValue("RemovedItem")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.REMOVE)
                .index(1)
                .build())
            .build();

        Map<String, Object> view = change.toTypedView();

        assertEquals("entry_removed", view.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("RemovedItem", details.get("oldValue"));
        assertNull(details.get("newValue"));
    }

    @Test
    void typed_view_for_list_modify_contains_property_path() {
        FieldChange change = FieldChange.builder()
            .fieldName("items")
            .fieldPath("items[0].price")
            .changeType(ChangeType.UPDATE)
            .oldValue(99.99)
            .newValue(89.99)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MODIFY)
                .index(0)
                .propertyPath("price")
                .build())
            .build();

        Map<String, Object> view = change.toTypedView();

        assertEquals("entry_updated", view.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("MODIFY", details.get("operation"));
        assertEquals("price", details.get("propertyPath"));
        assertEquals(99.99, details.get("oldValue"));
        assertEquals(89.99, details.get("newValue"));
    }

    @Test
    void typed_view_for_list_move_contains_old_and_new_index() {
        FieldChange change = FieldChange.builder()
            .fieldName("items")
            .fieldPath("items[1->3]")
            .changeType(ChangeType.MOVE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MOVE)
                .oldIndex(1)
                .newIndex(3)
                .build())
            .build();

        Map<String, Object> view = change.toTypedView();

        assertEquals("entry_moved", view.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("MOVE", details.get("operation"));
        assertEquals(1, details.get("oldIndex"));
        assertEquals(3, details.get("newIndex"));
        assertNull(details.get("index")); // MOVE 不使用 index
    }

    @Test
    void typed_view_for_map_add_contains_map_key() {
        FieldChange change = FieldChange.builder()
            .fieldName("orderMap")
            .fieldPath("orderMap[order-1]")
            .changeType(ChangeType.CREATE)
            .newValue("Order{id=O1}")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.ADD)
                .mapKey("order-1")
                .entityKey("order[O1]")
                .build())
            .build();

        Map<String, Object> view = change.toTypedView();

        assertEquals("entry_added", view.get("kind"));
        assertEquals("orderMap", view.get("object"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("MAP", details.get("containerType"));
        assertEquals("order-1", details.get("mapKey"));
        assertEquals("order[O1]", details.get("entityKey"));
    }

    @Test
    void typed_view_for_set_add_has_no_index() {
        FieldChange change = FieldChange.builder()
            .fieldName("users")
            .fieldPath("users[user[U1]]")
            .changeType(ChangeType.CREATE)
            .newValue("User{id=U1}")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.SET)
                .operation(FieldChange.ElementOperation.ADD)
                .entityKey("user[U1]")
                .build())
            .build();

        Map<String, Object> view = change.toTypedView();

        assertEquals("entry_added", view.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("SET", details.get("containerType"));
        assertNull(details.get("index")); // Set 无索引
        assertEquals("user[U1]", details.get("entityKey"));
    }

    @Test
    void typed_view_for_non_container_change_returns_null() {
        FieldChange scalarChange = FieldChange.builder()
            .fieldName("price")
            .fieldPath("price")
            .oldValue(99.99)
            .newValue(89.99)
            .changeType(ChangeType.UPDATE)
            .build(); // 无 elementEvent

        Map<String, Object> view = scalarChange.toTypedView();

        assertNull(view, "非容器变更应返回 null");
    }

    @Test
    void typed_view_extracts_object_name_from_path() {
        FieldChange change1 = FieldChange.builder()
            .fieldPath("items[0].price")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MODIFY)
                .build())
            .build();

        assertEquals("items", change1.toTypedView().get("object"));

        FieldChange change2 = FieldChange.builder()
            .fieldPath("orderMap[key]")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.ADD)
                .build())
            .build();

        assertEquals("orderMap", change2.toTypedView().get("object"));

        FieldChange change3 = FieldChange.builder()
            .fieldPath(null) // 边界情况
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.SET)
                .operation(FieldChange.ElementOperation.ADD)
                .build())
            .build();

        assertEquals("unknown", change3.toTypedView().get("object"));
    }

    @Test
    void typed_view_timestamp_is_iso8601_format() {
        FieldChange change = FieldChange.builder()
            .fieldPath("items[0]")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build())
            .build();

        String timestamp = (String) change.toTypedView().get("timestamp");

        assertNotNull(timestamp);
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z"),
            "时间戳应符合 ISO-8601 格式");
    }

    @Test
    void typed_view_details_omits_null_optional_fields() {
        FieldChange change = FieldChange.builder()
            .fieldPath("items[0]")
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                // entityKey, mapKey, propertyPath 均为 null
                .build())
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) change.toTypedView().get("details");

        assertTrue(details.containsKey("index"));
        assertFalse(details.containsKey("entityKey"), "null 的 entityKey 不应出现在 details 中");
        assertFalse(details.containsKey("mapKey"), "null 的 mapKey 不应出现在 details 中");
        assertFalse(details.containsKey("propertyPath"), "null 的 propertyPath 不应出现在 details 中");
    }
}
