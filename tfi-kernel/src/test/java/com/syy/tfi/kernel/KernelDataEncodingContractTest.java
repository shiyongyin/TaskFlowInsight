package com.syy.tfi.kernel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.RecordType;
import com.syy.tfi.kernel.spi.KernelClock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * KNL-02 批次 A 的固化与 JSON literal oracle。
 */
class KernelDataEncodingContractTest {

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kvU801And02JsonLiteralBytesMatchUtf8AndEscapingRules() {
        Map<String, Integer> vectors = new LinkedHashMap<>();
        vectors.put("", 2);
        vectors.put("A", 3);
        vectors.put("\n", 4);
        vectors.put("\"", 4);
        vectors.put("\\", 4);
        vectors.put("中", 5);
        vectors.put("\u0001", 8);

        vectors.forEach((text, expectedBytes) -> {
            String json = currentJsonWithText(text);
            String literal = extract(json, "\"text\":", ",\"data\":");
            assertThat(literal.getBytes(UTF_8)).hasSize(expectedBytes);
            if ("\u0001".equals(text)) {
                assertThat(literal).isEqualTo("\"\\u0001\"");
            }
        });
    }

    @Test
    void kvU803UnpairedSurrogateRejectsTheWholeRecord() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, 12_288, 2_048, 32);
        try (Stage root = Tfi.begin("invalid-surrogate")) {
            root.message("\uD800");
        }

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().records()).isEmpty();
            assertThat(session.incompleteReasons()).containsExactly("STRUCTURED_DATA_INVALID");
        });
    }

    @Test
    void kvBd01AndDt01NumbersKeepScaleAndUnsupportedValuesNeverCallToString() {
        AtomicInteger toStringCalls = new AtomicInteger();
        Object unsupported = new Object() {
            @Override
            public String toString() {
                toStringCalls.incrementAndGet();
                throw new IllegalStateException("must not be called");
            }
        };
        configure(new ArrayList<>(), 12_288, 2_048, 32);
        String json;
        try (Stage root = Tfi.begin("scalar-freeze")) {
            root.record(RecordType.MESSAGE, "DECIMAL", null, Map.of("value", new BigDecimal("1.00")));
            root.change("unsupported", unsupported, null);
            json = Tfi.currentToJson();
        }

        assertThat(toStringCalls).hasValue(0);
        assertThat(json).contains("\"data\":{\"value\":1.00}");
        assertThat(json).contains("\"representation\":\"UNSUPPORTED\"");
        assertThat(json).contains("\"type\":\"" + unsupported.getClass().getName() + "\"");
    }

    @Test
    void kvDt02And03StructuredMapsSortByUtf8AndAttrReplacementIsAtomic() {
        Map<String, Object> unordered = new HashMap<>();
        unordered.put("中", 3);
        unordered.put("a", 2);
        unordered.put("A", 1);
        unordered.put("\uE000", 4);
        unordered.put("\uD800\uDC00", 5);
        configure(new ArrayList<>(), 12_288, 2_048, 32);
        String replacement;
        int replacementRemaining;
        try (Stage root = Tfi.begin("deterministic-data")) {
            root.attr("version", "v1");
            root.attr("version", "v2");
            root.record(RecordType.MESSAGE, "SORTED", null, unordered);
            replacement = Tfi.currentToJson();
            replacementRemaining = root.remainingEncodedBytes();
        }

        configure(new ArrayList<>(), 12_288, 2_048, 32);
        String direct;
        int directRemaining;
        try (Stage root = Tfi.begin("deterministic-data")) {
            root.attr("version", "v2");
            root.record(RecordType.MESSAGE, "SORTED", null, unordered);
            direct = Tfi.currentToJson();
            directRemaining = root.remainingEncodedBytes();
        }

        assertThat(replacement).isEqualTo(direct);
        assertThat(replacementRemaining).isEqualTo(directRemaining);
        assertThat(replacement.indexOf("\"A\":1")).isLessThan(replacement.indexOf("\"a\":2"));
        assertThat(replacement.indexOf("\"a\":2")).isLessThan(replacement.indexOf("\"中\":3"));
        assertThat(replacement.indexOf("\"\uE000\":4"))
                .isLessThan(replacement.indexOf("\"\uD800\uDC00\":5"));
        assertThat(replacement.split("\"version\"", -1)).hasSize(2);
    }

    @Test
    void integralScalarByteAccountingPreservesSignedDecimalBoundaries() {
        configure(new ArrayList<>(), 12_288, 2_048, 32);
        String json;
        try (Stage root = Tfi.begin("integral-boundaries")) {
            root.record(RecordType.MESSAGE, "INTEGER", null, Map.of(
                    "longMin", Long.MIN_VALUE,
                    "longMax", Long.MAX_VALUE,
                    "zero", 0,
                    "negative", -1));
            json = Tfi.currentToJson();
        }

        assertThat(json)
                .contains("\"longMin\":-9223372036854775808")
                .contains("\"longMax\":9223372036854775807")
                .contains("\"zero\":0")
                .contains("\"negative\":-1");
    }

    @Test
    void structuredDataIsDetachedAndDeeplyUnmodifiableAtAcceptance() {
        List<FlowSession> sessions = new ArrayList<>();
        List<Object> businessList = new ArrayList<>(List.of("before"));
        Map<String, Object> businessMap = new HashMap<>();
        businessMap.put("items", businessList);
        configure(sessions, 12_288, 2_048, 32);
        try (Stage root = Tfi.begin("deep-copy")) {
            root.record(RecordType.MESSAGE, "DATA", null, businessMap);
            businessList.add("after");
            businessMap.put("late", true);
        }

        Map<String, Object> data = sessions.get(0).root().records().get(0).data();
        assertThat(data).containsOnlyKeys("items");
        assertThat((Object) data.get("items")).isEqualTo(List.of("before"));
        assertThatThrownBy(() -> ((List<Object>) data.get("items")).add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static String currentJsonWithText(String text) {
        configure(new ArrayList<>(), 12_288, 2_048, 32);
        try (Stage root = Tfi.begin("literal")) {
            root.record(RecordType.MESSAGE, "TEXT", text, Map.of());
            return Tfi.currentToJson();
        }
    }

    private static String extract(String value, String prefix, String suffix) {
        int start = value.indexOf(prefix) + prefix.length();
        return value.substring(start, value.indexOf(suffix, start));
    }

    private static void configure(List<FlowSession> sessions, int sessionBytes, int recordBytes, int attrs) {
        Tfi.configure(new KernelConfig(
                true, List.of(sessions::add), name -> true, () -> "S",
                new FixedClock(), 64, sessionBytes, recordBytes, attrs));
    }

    private static final class FixedClock implements KernelClock {
        @Override public long wallTimeMillis() { return 0L; }
        @Override public long monotonicNanos() { return 0L; }
    }
}
