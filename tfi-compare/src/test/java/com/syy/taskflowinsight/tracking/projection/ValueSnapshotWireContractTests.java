package com.syy.taskflowinsight.tracking.projection;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ValueSnapshotWireContractTests {

    private final CompareProjectionFactory factory = new CompareProjectionFactory();
    private final CanonicalChangeMapEncoder encoder = new CanonicalChangeMapEncoder();

    @ParameterizedTest(name = "{0}")
    @MethodSource("wireCases")
    void should_encode_each_value_snapshot_as_tagged_v1_wire(
            String scenario,
            ValueSnapshot snapshot,
            Map<String, Object> expected) {
        assertThat(projectedValue(snapshot))
                .as(scenario)
                .isEqualTo(expected);
    }

    private static Stream<Arguments> wireCases() {
        String enumType = SampleState.class.getName();
        return Stream.of(
                Arguments.of("null", ValueSnapshot.exactNull(),
                        Map.of("representation", "EXACT", "type", "null")),
                Arguments.of("boolean", ValueSnapshot.ofBoolean(false, 8),
                        Map.of("representation", "EXACT", "type", "boolean", "value", false)),
                Arguments.of("string", ValueSnapshot.ofString("text", 8),
                        Map.of("representation", "EXACT", "type", "string", "value", "text")),
                Arguments.of("character", ValueSnapshot.ofCharacter('\uD800', 16),
                        Map.of("representation", "EXACT", "type", "character", "value", "u16:D800")),
                Arguments.of("long", ValueSnapshot.ofInteger(Long.MAX_VALUE, 32),
                        Map.of("representation", "EXACT", "type", "long",
                                "value", Long.toString(Long.MAX_VALUE))),
                Arguments.of("big-integer", ValueSnapshot.ofInteger(new BigInteger("12345678901234567890"), 32),
                        Map.of("representation", "EXACT", "type", "big-integer",
                                "value", "12345678901234567890")),
                Arguments.of("big-decimal", ValueSnapshot.ofBigDecimal(new BigDecimal("1.00"), 16),
                        Map.of("representation", "EXACT", "type", "big-decimal",
                                "value", Map.of("unscaled", "100", "scale", 2))),
                Arguments.of("float-special", ValueSnapshot.ofFloating(Float.NaN, 16),
                        Map.of("representation", "EXACT", "type", "float", "value", "nan")),
                Arguments.of("double-signed-zero", ValueSnapshot.ofFloating(-0.0d, 16),
                        Map.of("representation", "EXACT", "type", "double", "value", "-0x0.0p0")),
                Arguments.of("enum", ValueSnapshot.ofEnum(SampleState.READY, 256),
                        Map.of("representation", "EXACT", "type", "enum",
                                "value", Map.of("declaringType", enumType, "constant", "READY"))),
                Arguments.of("temporal", ValueSnapshot.ofTemporal(Instant.EPOCH, 32),
                        Map.of("representation", "EXACT", "type", "instant", "value", "1970-01-01T00:00:00Z")),
                Arguments.of("type-metadata", ValueSnapshot.ofTypeMetadata(String.class, 256),
                        Map.of("representation", "EXACT", "type", "type-metadata",
                                "value", Map.of("kind", "class", "binaryType", "java.lang.String"))),
                Arguments.of("container", ValueSnapshot.ofContainer(ValueSnapshot.ContainerKind.LIST, 3, 8),
                        Map.of("representation", "EXACT", "type", "list",
                                "value", Map.of("size", "3"))),
                Arguments.of("summary", ValueSnapshot.ofString("abc", 1),
                        Map.of("representation", "SUMMARY", "type", "string",
                                "summary", Map.of("length", "3"))),
                Arguments.of("omitted", ValueSnapshot.ofBoolean(false, 0),
                        Map.of("representation", "OMITTED", "type", "boolean", "reason", "VALUE_LIMIT")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> projectedValue(ValueSnapshot snapshot) {
        ComparePath path = ComparePath.root().append(new PropertySegment("wireValue"));
        FieldChange change = FieldChange.canonical(
                ChangeKind.ADD,
                Optional.empty(),
                Optional.of(new ChangeSide(path, snapshot)));
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
        Map<String, Object> tree = encoder.encode(factory.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults()));
        Map<String, Object> projectedChange = (Map<String, Object>) ((List<?>) tree.get("changes")).getFirst();
        Map<String, Object> after = (Map<String, Object>) projectedChange.get("after");
        return (Map<String, Object>) after.get("value");
    }

    private enum SampleState {
        READY
    }
}
