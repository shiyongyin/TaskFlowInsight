package com.syy.taskflowinsight.exporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.exporter.json.JsonExporterTestAccess;
import com.syy.taskflowinsight.exporter.map.MapExporterTestAccess;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Map 与 JSON 从同一预构建快照发布 canonical V2 的最终契约。 */
class ExportV2ContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void should_publish_map_and_json_from_the_same_prebuilt_snapshot() throws Exception {
        Session session = Session.create("parity-root");
        TaskNode root = session.getRootTask();
        CallbackTrap trap = new CallbackTrap();
        root.addDebug("debug-message");
        root.addAttribute("nullable", null);
        root.addAttribute("character", 'x');
        root.addAttribute("bigInteger", new BigInteger("12345678901234567890"));
        root.addAttribute("bigDecimal", new BigDecimal("1234.50"));
        root.addAttribute("notANumber", Double.NaN);
        root.addAttribute("unsupported", trap);
        root.addTag("parity");
        root.createChild("first");
        root.createChild("second");

        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        Map<String, Object> canonical = snapshot.toCanonicalV2();
        root.addAttribute("afterCapture", "must-not-leak");
        root.createChild("after-capture");

        AtomicInteger captures = new AtomicInteger();
        Function<Session, SessionExportSnapshot> capturer = ignored -> {
            captures.incrementAndGet();
            return snapshot;
        };

        Map<String, Object> actualMap = MapExporterTestAccess.export(session, capturer);
        String actualJson = JsonExporterTestAccess.export(session, capturer);
        JsonNode expectedJsonTree = MAPPER.readTree(MAPPER.writeValueAsString(canonical));
        JsonNode actualJsonTree = MAPPER.readTree(actualJson);

        assertThat(captures).as("one existing seam invocation per exporter").hasValue(2);
        assertThat(actualMap).isEqualTo(canonical);
        assertThat(actualJsonTree).isEqualTo(expectedJsonTree);
        assertThat(actualMap.toString()).doesNotContain("afterCapture", "after-capture");
        assertThat(actualJson).doesNotContain("afterCapture", "after-capture");

        Map<String, Object> attributes = map(map(actualMap.get("rootTask")).get("attributes"));
        assertThat(attributes).containsEntry("nullable", null);
        assertThat(attributes.get("character")).isEqualTo('x').isExactlyInstanceOf(Character.class);
        assertThat(attributes.get("bigInteger")).isExactlyInstanceOf(BigInteger.class);
        assertThat(attributes.get("bigDecimal")).isEqualTo(new BigDecimal("1234.50"));
        assertThat(map(attributes.get("notANumber")))
                .containsEntry("kind", "nonFiniteNumber")
                .containsEntry("numberType", "DOUBLE")
                .containsEntry("value", "NaN");
        assertThat(map(attributes.get("unsupported")))
                .containsEntry("kind", "unsupported")
                .containsEntry("className", CallbackTrap.class.getName());
        assertThat(map(actualMap.get("rootTask")).get("tags")).isEqualTo(List.of("parity"));
        assertThat(actualJsonTree.path("rootTask").path("attributes").path("character").textValue())
                .isEqualTo("x");
        assertThat(actualJsonTree.path("rootTask").path("messages").get(0).path("severity").textValue())
                .isEqualTo("DEBUG");
        assertThat(actualJsonTree.path("rootTask").path("children").findValuesAsText("sequence"))
                .containsExactly("0", "1");
        assertThat(trap.callbackCount).isZero();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static final class CallbackTrap {

        private int callbackCount;

        @Override
        public String toString() {
            callbackCount++;
            throw new AssertionError("parity must not invoke user callbacks");
        }

        @Override
        public int hashCode() {
            callbackCount++;
            throw new AssertionError("parity must not invoke user callbacks");
        }

        @Override
        public boolean equals(Object other) {
            callbackCount++;
            throw new AssertionError("parity must not invoke user callbacks");
        }
    }
}
