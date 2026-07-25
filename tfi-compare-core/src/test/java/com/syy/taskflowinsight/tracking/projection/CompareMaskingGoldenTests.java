package com.syy.taskflowinsight.tracking.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.path.SetMemberSegment;
import com.syy.taskflowinsight.tracking.projection.internal.SensitiveValueDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompareMaskingGoldenTests {

    private static final String GOLDEN_ROOT = "/golden/compare-projection-v1/";
    private static final String REDACTED = "[REDACTED]";
    private static final JsonMapper JSON = new JsonMapper();

    private final CompareProjectionFactory factory = new CompareProjectionFactory();
    private final CanonicalChangeMapEncoder encoder = new CanonicalChangeMapEncoder();

    @Test
    void should_apply_safe_field_luhn_and_ssn_floor_without_masking_invalid_luhn() {
        List<FieldChange> changes = List.of(
                added(property("password"), ValueSnapshot.ofString("ordinary", 64)),
                added(property("note"), ValueSnapshot.ofString("card 4111 1111 1111 1111 tail", 64)),
                added(property("note"), ValueSnapshot.ofString("ssn 123-45-6789 tail", 64)),
                added(property("note"), ValueSnapshot.ofString("4111 1111 1111 1112", 64)));

        List<Map<String, Object>> projected = projectedChanges(changes, ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(), ProjectionOptions.defaults());

        assertThat(afterValue(projected.get(0))).isEqualTo(maskedWire());
        assertThat(afterValue(projected.get(1))).isEqualTo(maskedWire());
        assertThat(afterValue(projected.get(2))).containsEntry("value", "4111 1111 1111 1112");
        assertThat(afterValue(projected.get(3))).isEqualTo(maskedWire());
    }

    @Test
    void should_mask_dynamic_keys_and_keep_colliding_changes_distinct() {
        ComparePath first = ComparePath.root().append(new MapKeySegment(
                ValueSnapshot.ofString("4111 1111 1111 1111", 64)));
        ComparePath second = ComparePath.root().append(new MapKeySegment(
                ValueSnapshot.ofString("5555-5555-5555-4444", 64)));

        List<Map<String, Object>> changes = projectedChanges(
                List.of(
                        added(first, ValueSnapshot.ofString("first", 16)),
                        added(second, ValueSnapshot.ofString("second", 16))),
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());

        assertThat(pathKey(changes.get(0))).isEqualTo(maskedWire());
        assertThat(pathKey(changes.get(1))).isEqualTo(maskedWire());
        assertThat(changes)
                .extracting(change -> change.get("maskedOccurrence"))
                .containsExactly(0, 1);
        assertThat(changes).hasSize(2);
    }

    @Test
    void should_mask_fixed_metadata_and_allow_only_explicit_call_opt_in() {
        ProjectionMetadata metadata = new ProjectionMetadata(
                Optional.of("session-visible"),
                Optional.of("task-visible"),
                Optional.of("pay 4111111111111111"));

        Map<String, Object> safe = projectionTree(
                List.of(added(property("note"), ValueSnapshot.ofString("plain", 16))),
                metadata,
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
        Map<String, Object> included = projectionTree(
                List.of(added(property("note"), ValueSnapshot.ofString("plain", 16))),
                metadata,
                MaskingPolicy.explicitlyIncludeSensitiveValues(),
                ProjectionOptions.defaults());

        Map<String, Object> safeMetadata = map(safe, "metadata");
        assertThat(safeMetadata.values()).allMatch(maskedWire()::equals);
        assertThat(map(included, "metadata").values())
                .extracting(value -> mapValue(value).get("value"))
                .containsExactly("session-visible", "task-visible", "pay 4111111111111111");
    }

    @Test
    void should_match_masking_golden_without_raw_value() throws IOException {
        Map<String, Object> tree = projectionTree(
                List.of(added(property("password"), ValueSnapshot.ofString("must-not-leak", 32))),
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());

        JsonNode actual = JSON.valueToTree(tree);
        assertThat(actual).isEqualTo(readGolden("masking.json"));
        assertThat(tree.toString()).doesNotContain("must-not-leak");
    }

    @Test
    void should_reject_raw_dynamic_pattern_and_accept_whole_segment_wildcard() {
        assertThatThrownBy(() -> MaskingPolicy.safeDefaultsWithAdditionalRules(List.of("MAP_KEY:raw-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid typed path pattern");

        MaskingPolicy policy = MaskingPolicy.safeDefaultsWithAdditionalRules(List.of("MAP_KEY:*"));
        ComparePath path = ComparePath.root().append(new MapKeySegment(ValueSnapshot.ofString("ordinary", 16)));
        Map<String, Object> change = projectedChanges(
                List.of(added(path, ValueSnapshot.ofString("value", 16))),
                ProjectionMetadata.empty(),
                policy,
                ProjectionOptions.defaults()).getFirst();

        assertThat(pathKey(change)).isEqualTo(maskedWire());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sensitiveValueCases")
    void should_detect_only_complete_bounded_sensitive_candidates(
            String scenario,
            ValueSnapshot snapshot,
            boolean expected) {
        assertThat(SensitiveValueDetector.isSensitive(snapshot))
                .as(scenario)
                .isEqualTo(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_mask_set_member_and_entity_key_components_like_map_keys() {
        ComparePath setPath = ComparePath.root().append(new SetMemberSegment(
                ValueSnapshot.ofString("123-45-6789", 32)));
        ComparePath entityPath = ComparePath.root().append(new EntityKeySegment(
                "example.Account",
                List.of(ValueSnapshot.ofString("4111111111111111", 32))));

        List<Map<String, Object>> changes = projectedChanges(
                List.of(
                        added(setPath, ValueSnapshot.ofString("set", 16)),
                        added(entityPath, ValueSnapshot.ofString("entity", 16))),
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());

        Map<String, Object> setSegment = firstPathSegment(changes.get(0));
        Map<String, Object> entitySegment = firstPathSegment(changes.get(1));
        assertThat(mapValue(setSegment.get("member"))).isEqualTo(maskedWire());
        List<Map<String, Object>> components = (List<Map<String, Object>>) entitySegment.get("components");
        assertThat(components).containsExactly(maskedWire());
    }

    private static Stream<Arguments> sensitiveValueCases() {
        return Stream.of(
                Arguments.of("13-digit Luhn", ValueSnapshot.ofString("4222222222222", 32), true),
                Arguments.of("16-digit Luhn", ValueSnapshot.ofString("4111-1111-1111-1111", 32), true),
                Arguments.of("19-digit Luhn", ValueSnapshot.ofString("4000000000000000006", 32), true),
                Arguments.of("12 digits", ValueSnapshot.ofString("411111111111", 32), false),
                Arguments.of("20 digits", ValueSnapshot.ofString("41111111111111111111", 32), false),
                Arguments.of("invalid Luhn", ValueSnapshot.ofString("4111111111111112", 32), false),
                Arguments.of("SSN", ValueSnapshot.ofString("123-45-6789", 32), true),
                Arguments.of("SSN without boundary", ValueSnapshot.ofString("x123-45-6789y", 32), false),
                Arguments.of("numeric scalar", ValueSnapshot.ofInteger(4111111111111111L, 32), true),
                Arguments.of("summary has no raw prefix", ValueSnapshot.ofString("4111111111111111", 1), false));
    }

    private List<Map<String, Object>> projectedChanges(
            List<FieldChange> changes,
            ProjectionMetadata metadata,
            MaskingPolicy policy,
            ProjectionOptions options) {
        return changes(projectionTree(changes, metadata, policy, options));
    }

    private Map<String, Object> projectionTree(
            List<FieldChange> changes,
            ProjectionMetadata metadata,
            MaskingPolicy policy,
            ProjectionOptions options) {
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                changes,
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
        return encoder.encode(factory.create(result, metadata, policy, options));
    }

    private static FieldChange added(ComparePath path, ValueSnapshot value) {
        return FieldChange.canonical(
                ChangeKind.ADD,
                Optional.empty(),
                Optional.of(new ChangeSide(path, value)));
    }

    private static ComparePath property(String name) {
        return ComparePath.root().append(new PropertySegment(name));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> changes(Map<String, Object> tree) {
        return (List<Map<String, Object>>) tree.get("changes");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> afterValue(Map<String, Object> change) {
        return (Map<String, Object>) ((Map<String, Object>) change.get("after")).get("value");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pathKey(Map<String, Object> change) {
        return (Map<String, Object>) firstPathSegment(change).get("key");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstPathSegment(Map<String, Object> change) {
        Map<String, Object> after = (Map<String, Object>) change.get("after");
        List<Map<String, Object>> path = (List<Map<String, Object>>) after.get("path");
        return path.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> owner, String field) {
        return (Map<String, Object>) owner.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> maskedWire() {
        return Map.of(
                "representation", "EXACT",
                "type", "masked",
                "value", REDACTED);
    }

    private static JsonNode readGolden(String file) throws IOException {
        try (InputStream input = CompareMaskingGoldenTests.class.getResourceAsStream(GOLDEN_ROOT + file)) {
            assertThat(input).as("projection golden must exist: %s", file).isNotNull();
            return JSON.readTree(input);
        }
    }
}
