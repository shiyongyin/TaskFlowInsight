package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定 canonical 值事实到诊断文本和旧 JSON literal 的无回调转换。 */
class ValueSnapshotFormatterContractTests {

    @Test
    void shouldDistinguishExactSummaryAndOmittedRepresentations() {
        assertThat(ValueSnapshotFormatter.diagnosticText(ValueSnapshot.exactNull()))
                .isEqualTo("null");
        assertThat(ValueSnapshotFormatter.diagnosticText(ValueSnapshot.ofString("abcdef", 1)))
                .isEqualTo("<string:summary:6>");
        assertThat(ValueSnapshotFormatter.diagnosticText(ValueSnapshot.ofBoolean(true, 1)))
                .isEqualTo("<boolean:omitted:value_limit>");
    }

    @Test
    void shouldRenderExactScalarAndStructuredFacts() {
        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(ValueSnapshot.exactNull()))
                .isEqualTo("null");
        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(ValueSnapshot.ofInteger(42, 8)))
                .isEqualTo("42");
        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(
                ValueSnapshot.ofBigDecimal(new BigDecimal("1.20"), 16)))
                .isEqualTo("1.20");
        assertThat(ValueSnapshotFormatter.diagnosticText(
                ValueSnapshot.ofEnum(Sample.ACTIVE, 128)))
                .isEqualTo("ACTIVE");
        assertThat(ValueSnapshotFormatter.diagnosticText(
                ValueSnapshot.ofTypeMetadata(String.class, 64)))
                .isEqualTo("java.lang.String");
        assertThat(ValueSnapshotFormatter.diagnosticText(
                ValueSnapshot.ofContainer(ValueSnapshot.ContainerKind.LIST, 3, 8)))
                .isEqualTo("<list:size=3>");
    }

    @Test
    void shouldEscapeJsonControlsAndUnpairedSurrogatesWithoutChangingPairs() {
        String controls = ValueSnapshotFormatter.legacyJsonLiteral(
                ValueSnapshot.ofString("\"\\\b\f\n\r\t\u0001", 32));
        assertThat(controls).contains("\\\"", "\\\\", "\\b", "\\f", "\\n", "\\r", "\\t", "\\u0001");

        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(
                ValueSnapshot.ofString("\uD800", 8))).isEqualTo("\"\\uD800\"");
        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(
                ValueSnapshot.ofString("\uD800x", 8))).isEqualTo("\"\\uD800x\"");
        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(
                ValueSnapshot.ofString("\uDC00", 8))).isEqualTo("\"\\uDC00\"");
        assertThat(ValueSnapshotFormatter.legacyJsonLiteral(
                ValueSnapshot.ofString("\uD83D\uDE00", 8))).isEqualTo("\"\uD83D\uDE00\"");
    }

    private enum Sample {
        ACTIVE
    }
}
