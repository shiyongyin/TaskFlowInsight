package com.syy.taskflowinsight.tracking.compare;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReferenceDetail Map 视图导出测试
 */
class ReferenceDetailMapViewTests {

    @Test
    void reference_detail_to_map_should_contain_standard_keys() {
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
            .oldEntityKey("Customer[C1]")
            .newEntityKey("Customer[C2]")
            .nullReferenceChange(false)
            .build();

        Map<String, Object> map = detail.toMap();

        assertTrue(map.containsKey("oldKey"));
        assertTrue(map.containsKey("newKey"));
        assertTrue(map.containsKey("isNullTransition"));
        assertTrue(map.containsKey("transitionType"));

        assertEquals("Customer[C1]", map.get("oldKey"));
        assertEquals("Customer[C2]", map.get("newKey"));
        assertEquals(false, map.get("isNullTransition"));
        assertEquals("REFERENCE_SWITCHED", map.get("transitionType"));
    }

    @Test
    void null_to_entity_transition_type_should_be_association_established() {
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
            .oldEntityKey(null)
            .newEntityKey("Customer[C1]")
            .nullReferenceChange(true)
            .build();

        Map<String, Object> map = detail.toMap();
        assertEquals("ASSOCIATION_ESTABLISHED", map.get("transitionType"));
    }

    @Test
    void entity_to_null_transition_type_should_be_association_removed() {
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
            .oldEntityKey("Customer[C1]")
            .newEntityKey(null)
            .nullReferenceChange(true)
            .build();

        Map<String, Object> map = detail.toMap();
        assertEquals("ASSOCIATION_REMOVED", map.get("transitionType"));
    }

    @Test
    void field_change_to_reference_view_for_valid_reference_change() {
        FieldChange change = FieldChange.builder()
            .fieldName("customer")
            .fieldPath("Order.customer")
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey("Customer[C1]")
                .newEntityKey("Customer[C2]")
                .nullReferenceChange(false)
                .build())
            .build();

        Map<String, Object> view = change.toReferenceChangeView();

        assertNotNull(view);
        assertEquals("reference_change", view.get("kind"));
        assertEquals("Order", view.get("object"));
        assertEquals("Order.customer", view.get("path"));
        assertTrue(view.containsKey("timestamp"));
        assertTrue(view.containsKey("details"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) view.get("details");
        assertEquals("Customer[C1]", details.get("oldKey"));
        assertEquals("Customer[C2]", details.get("newKey"));
    }

    @Test
    void non_reference_change_should_return_null_view() {
        FieldChange scalarChange = FieldChange.builder()
            .fieldName("price")
            .oldValue(99.99)
            .newValue(89.99)
            .referenceChange(false)
            .build();

        assertNull(scalarChange.toReferenceChangeView());
    }

    @Test
    void reference_detail_to_json_produces_valid_json() throws Exception {
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
            .oldEntityKey("Customer[C1]")
            .newEntityKey("Customer[C2]")
            .nullReferenceChange(false)
            .build();

        String json = detail.toJson();

        // 验证是有效的 JSON
        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> parsed = mapper.readValue(json, Map.class);

        assertNotNull(parsed);
        assertEquals("Customer[C1]", parsed.get("oldKey"));
        assertEquals("Customer[C2]", parsed.get("newKey"));
        assertEquals(false, parsed.get("isNullTransition"));
        assertEquals("REFERENCE_SWITCHED", parsed.get("transitionType"));
    }
}
