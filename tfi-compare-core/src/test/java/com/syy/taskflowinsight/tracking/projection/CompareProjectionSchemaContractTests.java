package com.syy.taskflowinsight.tracking.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.syy.taskflowinsight.exporter.change.CanonicalChangeMapEncoder;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompareProjectionSchemaContractTests {

    private static final String GOLDEN_ROOT = "/golden/compare-projection-v1/";
    private static final JsonMapper JSON = new JsonMapper();

    private final CompareProjectionFactory factory = new CompareProjectionFactory();
    private final CanonicalChangeMapEncoder mapEncoder = new CanonicalChangeMapEncoder();

    @Test
    void should_freeze_schema_v1_field_order_when_projection_has_similarity() throws IOException {
        CompareProjection projection = factory.create(
                CompareResult.identical(),
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());

        Map<String, Object> tree = mapEncoder.encode(projection);

        assertThat(tree.keySet()).containsExactly(
                "schemaId",
                "schemaVersion",
                "outcome",
                "completion",
                "problems",
                "limitations",
                "diagnostics",
                "changes",
                "similarity");
        assertThat(tree)
                .containsEntry("schemaId", "tfi.compare.change")
                .containsEntry("schemaVersion", 1)
                .containsEntry("outcome", "EQUAL")
                .containsEntry("completion", "COMPLETE");
        Map<String, Object> diagnostics = mapValue(tree, "diagnostics");
        assertThat(diagnostics.keySet()).containsExactly(
                "durationNanos",
                "rootAlgorithmId",
                "appliedAlgorithmIds",
                "effectivePolicyFingerprint",
                "comparedNodes",
                "consumedElements",
                "retainedResultChars",
                "omittedPaths",
                "omittedChanges",
                "omittedProblems",
                "omittedLimitations");
        assertThat(diagnostics)
                .containsEntry("durationNanos", "0")
                .containsEntry("rootAlgorithmId", "tfi:identity:v1")
                .containsEntry("appliedAlgorithmIds", List.of("tfi:identity:v1"))
                .containsEntry("effectivePolicyFingerprint", null)
                .containsEntry("omittedPaths", "0")
                .containsEntry("omittedChanges", "0")
                .containsEntry("omittedProblems", "0")
                .containsEntry("omittedLimitations", "0");
        assertThat(tree.get("similarity")).isEqualTo(Map.of(
                "algorithmId", "tfi:identity:v1",
                "value", 1.0d));
        JsonNode actual = JSON.valueToTree(tree);
        assertThat(actual).isEqualTo(readGolden("schema.json"));
    }

    @Test
    void should_keep_big_decimal_scale_in_tagged_value_wire() throws IOException {
        ComparePath path = ComparePath.root().append(new PropertySegment("amount"));
        FieldChange change = FieldChange.canonical(
                ChangeKind.MODIFY,
                Optional.of(new ChangeSide(path, ValueSnapshot.ofBigDecimal(new BigDecimal("1.00"), 16))),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofBigDecimal(new BigDecimal("2.0"), 16))));
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());

        Map<String, Object> tree = mapEncoder.encode(factory.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults()));

        Map<String, Object> projectedChange = firstMap(tree, "changes");
        assertThat(projectedChange.keySet()).containsExactly("kind", "before", "after");
        assertBigDecimalWire(sideValue(projectedChange, "before"), "100", 2);
        assertBigDecimalWire(sideValue(projectedChange, "after"), "20", 1);
        JsonNode actual = JSON.valueToTree(tree);
        assertThat(actual).isEqualTo(readGolden("value-wire.json"));
    }

    @Test
    void should_project_closed_issues_and_all_omitted_counters() {
        ComparePath issuePath = ComparePath.root().append(new PropertySegment("profile"));
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                7,
                Optional.empty(),
                List.of(),
                Optional.empty(),
                2,
                3,
                4,
                5,
                6,
                7,
                8);
        CompareResult result = CompareResult.canonical(
                CompareOutcome.INDETERMINATE,
                CompareCompletion.PARTIAL,
                List.of(),
                List.of(new CompareProblem(
                        CompareProblemCode.SNAPSHOT_FAILED,
                        CompareStage.SNAPSHOT,
                        Optional.of(issuePath))),
                List.of(new CompareLimitation(
                        CompareLimitationCode.DEPTH_LIMIT_REACHED,
                        CompareStage.DIFF,
                        Optional.empty())),
                diagnostics,
                Optional.empty());

        Map<String, Object> tree = mapEncoder.encode(factory.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults()));

        assertThat(firstMap(tree, "problems")).isEqualTo(Map.of(
                "code", "CMP_E_2001",
                "stage", "SNAPSHOT",
                "path", List.of(Map.of("kind", "PROPERTY", "name", "profile"))));
        assertThat(firstMap(tree, "limitations")).isEqualTo(Map.of(
                "code", "CMP_W_2102",
                "stage", "DIFF"));
        assertThat(mapValue(tree, "diagnostics"))
                .containsEntry("omittedPaths", "5")
                .containsEntry("omittedChanges", "6")
                .containsEntry("omittedProblems", "7")
                .containsEntry("omittedLimitations", "8");
        assertThat(tree).doesNotContainKey("similarity");
    }

    @Test
    void should_bound_optional_metadata_without_mutating_result_truth() {
        ProjectionMetadata metadata = new ProjectionMetadata(
                Optional.of("four"),
                Optional.of("ok"),
                Optional.empty());

        Map<String, Object> tree = mapEncoder.encode(factory.create(
                CompareResult.identical(),
                metadata,
                MaskingPolicy.explicitlyIncludeSensitiveValues(),
                new ProjectionOptions(3)));

        assertThat(tree.keySet()).containsExactly(
                "schemaId",
                "schemaVersion",
                "outcome",
                "completion",
                "problems",
                "limitations",
                "diagnostics",
                "changes",
                "metadata",
                "similarity");
        Map<String, Object> projectedMetadata = mapValue(tree, "metadata");
        assertThat(projectedMetadata.keySet()).containsExactly("sessionId", "taskId");
        assertThat(mapValue(projectedMetadata, "sessionId")).isEqualTo(Map.of(
                "representation", "OMITTED",
                "type", "string",
                "reason", "VALUE_LIMIT"));
        assertThat(mapValue(projectedMetadata, "taskId")).isEqualTo(Map.of(
                "representation", "EXACT",
                "type", "string",
                "value", "ok"));
        assertThat(metadata.toString()).doesNotContain("four", "ok");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstMap(Map<String, Object> tree, String field) {
        return (Map<String, Object>) ((List<?>) tree.get(field)).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sideValue(Map<String, Object> change, String side) {
        return (Map<String, Object>) ((Map<String, Object>) change.get(side)).get("value");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> tree, String field) {
        return (Map<String, Object>) tree.get(field);
    }

    private static void assertBigDecimalWire(
            Map<String, Object> snapshot,
            String expectedUnscaled,
            int expectedScale) {
        assertThat(snapshot.keySet()).containsExactly("representation", "type", "value");
        assertThat(snapshot)
                .containsEntry("representation", "EXACT")
                .containsEntry("type", "big-decimal");
        Map<String, Object> value = mapValue(snapshot, "value");
        assertThat(value.keySet()).containsExactly("unscaled", "scale");
        assertThat(value)
                .containsEntry("unscaled", expectedUnscaled)
                .containsEntry("scale", expectedScale);
    }

    private static JsonNode readGolden(String file) throws IOException {
        try (InputStream input = CompareProjectionSchemaContractTests.class.getResourceAsStream(GOLDEN_ROOT + file)) {
            assertThat(input).as("projection golden must exist: %s", file).isNotNull();
            return JSON.readTree(input);
        }
    }
}
