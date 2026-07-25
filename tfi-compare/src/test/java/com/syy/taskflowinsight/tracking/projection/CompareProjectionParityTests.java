package com.syy.taskflowinsight.tracking.projection;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.syy.taskflowinsight.exporter.change.CanonicalChangeJsonEncoder;
import com.syy.taskflowinsight.exporter.change.CanonicalChangeMapEncoder;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompareProjectionParityTests {

    private static final String GOLDEN_ROOT = "/golden/compare-projection-v1/";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final CompareProjectionFactory factory = new CompareProjectionFactory();
    private final CanonicalChangeJsonEncoder jsonEncoder = new CanonicalChangeJsonEncoder();
    private final CanonicalChangeMapEncoder mapEncoder = new CanonicalChangeMapEncoder();

    @Test
    void should_encode_exact_same_parser_tree_from_one_projection() throws Exception {
        CompareProjection projection = projectionWithString("quote=\" line=\n lone=\uD800");

        String json = jsonEncoder.encode(projection);
        JsonNode jsonTree = JSON.readTree(json);
        JsonNode mapTree = JSON.valueToTree(mapEncoder.encode(projection));

        assertThat(jsonTree).isEqualTo(mapTree);
        assertThat(json)
                .doesNotEndWith("\n")
                .doesNotContain("  ")
                .contains("\\\"")
                .contains("\\n")
                .contains("\\uD800");
        assertThat(json).isEqualTo(readGolden("escaping.json"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_make_every_map_depth_unmodifiable() {
        Map<String, Object> tree = mapEncoder.encode(projectionWithString("safe"));
        List<Object> changes = (List<Object>) tree.get("changes");
        Map<String, Object> change = (Map<String, Object>) changes.getFirst();
        Map<String, Object> after = (Map<String, Object>) change.get("after");

        assertThatThrownBy(() -> tree.put("extra", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> changes.add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> after.put("extra", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CompareProjection projectionWithString(String value) {
        ComparePath path = ComparePath.root().append(new PropertySegment("message"));
        FieldChange change = FieldChange.canonical(
                ChangeKind.ADD,
                Optional.empty(),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString(value, 128))));
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
        return factory.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
    }

    private static String readGolden(String file) throws IOException {
        try (InputStream input = CompareProjectionParityTests.class.getResourceAsStream(GOLDEN_ROOT + file)) {
            assertThat(input).as("projection golden must exist: %s", file).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }
}
