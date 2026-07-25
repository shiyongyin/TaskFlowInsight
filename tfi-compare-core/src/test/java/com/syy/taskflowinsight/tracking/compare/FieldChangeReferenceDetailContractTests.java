package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证引用身份变化的三种过渡及其稳定 JSON 转义。 */
class FieldChangeReferenceDetailContractTests {

    @Test
    void shouldDescribeAssociationEstablished() {
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
                .oldEntityKey(null)
                .newEntityKey("Customer#1")
                .nullReferenceChange(true)
                .build();

        assertThat(detail.toMap()).containsEntry("transitionType", "ASSOCIATION_ESTABLISHED");
        assertThat(detail.toJson())
                .contains("\"oldKey\":null", "\"isNullTransition\":true", "ASSOCIATION_ESTABLISHED");
    }

    @Test
    void shouldDescribeAssociationRemovedAndReferenceSwitched() {
        FieldChange.ReferenceDetail removed = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("Customer#1")
                .newEntityKey(null)
                .nullReferenceChange(true)
                .build();
        FieldChange.ReferenceDetail switched = FieldChange.ReferenceDetail.builder()
                .oldEntityKey("Customer#1")
                .newEntityKey("Customer#2")
                .nullReferenceChange(false)
                .build();

        assertThat(removed.toMap()).containsEntry("transitionType", "ASSOCIATION_REMOVED");
        assertThat(removed.toJson()).contains("ASSOCIATION_REMOVED");
        assertThat(switched.toMap()).containsEntry("transitionType", "REFERENCE_SWITCHED");
        assertThat(switched.toJson()).contains("REFERENCE_SWITCHED");
    }

    @Test
    void shouldEscapeControlsAndSurrogatesInEntityKeys() {
        String key = "\"\\\b\f\n\r\t\u0001\uD800x\uDC00\uD83D\uDE00";
        String json = FieldChange.ReferenceDetail.builder()
                .oldEntityKey(key)
                .newEntityKey("next")
                .nullReferenceChange(false)
                .build()
                .toJson();

        assertThat(json).contains(
                "\\\"", "\\\\", "\\b", "\\f", "\\n", "\\r", "\\t", "\\u0001",
                "\\uD800", "\\uDC00", "\uD83D\uDE00");
    }
}
