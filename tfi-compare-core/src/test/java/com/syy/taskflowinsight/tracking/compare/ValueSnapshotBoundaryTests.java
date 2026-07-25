package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ValueSnapshot负责把结果中的值限制为有界事实，防止比较结果重新持有业务对象图。
 */
class ValueSnapshotBoundaryTests {

    private enum SampleState {
        READY
    }

    @Test
    void exactNullIsAPresentValueWithoutTextFacts() {
        ValueSnapshot snapshot = ValueSnapshot.exactNull();

        assertThat(snapshot.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
        assertThat(snapshot.typeCode()).isEqualTo("null");
        assertThat(snapshot.canonicalTextFacts()).isEmpty();
    }

    @Test
    void stringAtLimitIsExactAndOverLimitKeepsOnlyLengthSummary() {
        ValueSnapshot atLimit = ValueSnapshot.ofString("ab", 2);
        ValueSnapshot overLimit = ValueSnapshot.ofString("abc", 2);

        assertThat(atLimit.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
        assertThat(atLimit.canonicalTextFacts()).containsExactly("ab");
        assertThat(overLimit.representation()).isEqualTo(ValueSnapshot.Representation.SUMMARY);
        assertThat(overLimit.typeCode()).isEqualTo("string");
        assertThat(overLimit.canonicalTextFacts()).containsExactly("3");
    }

    @Test
    void stringIsOmittedWhenEvenItsLengthSummaryExceedsTheValueBudget() {
        ValueSnapshot snapshot = ValueSnapshot.ofString("secret", 0);

        assertThat(snapshot.representation()).isEqualTo(ValueSnapshot.Representation.OMITTED);
        assertThat(snapshot.typeCode()).isEqualTo("string");
        assertThat(snapshot.canonicalTextFacts()).isEmpty();
        assertThat(snapshot.omissionReason())
                .contains(ValueSnapshot.OmissionReason.VALUE_LIMIT);
    }

    @Test
    void booleanUsesTypedCanonicalTokenAndHonorsTheValueBudget() {
        ValueSnapshot exact = ValueSnapshot.ofBoolean(false, 5);
        ValueSnapshot omitted = ValueSnapshot.ofBoolean(false, 4);

        assertThat(exact.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
        assertThat(exact.typeCode()).isEqualTo("boolean");
        assertThat(exact.canonicalTextFacts()).containsExactly("false");
        assertThat(omitted.representation()).isEqualTo(ValueSnapshot.Representation.OMITTED);
        assertThat(omitted.canonicalTextFacts()).isEmpty();
        assertThat(omitted.omissionReason()).contains(ValueSnapshot.OmissionReason.VALUE_LIMIT);
    }

    @Test
    void characterPreservesOneUtf16CodeUnitIncludingUnpairedSurrogate() {
        ValueSnapshot exact = ValueSnapshot.ofCharacter('\uD800', 8);
        ValueSnapshot omitted = ValueSnapshot.ofCharacter('\uD800', 7);

        assertThat(exact.typeCode()).isEqualTo("character");
        assertThat(exact.canonicalTextFacts()).containsExactly("u16:D800");
        assertThat(omitted.representation()).isEqualTo(ValueSnapshot.Representation.OMITTED);
        assertThat(omitted.canonicalTextFacts()).isEmpty();
    }

    @Test
    void bigDecimalKeepsUnscaledValueAndScaleWithoutRawPrefixTruncation() {
        ValueSnapshot exact = ValueSnapshot.ofBigDecimal(new BigDecimal("1.00"), 5);
        ValueSnapshot summary = ValueSnapshot.ofBigDecimal(new BigDecimal("123456789.00"), 5);

        assertThat(exact.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
        assertThat(exact.typeCode()).isEqualTo("big-decimal");
        assertThat(exact.canonicalTextFacts()).containsExactly("100", "2");
        assertThat(summary.representation()).isEqualTo(ValueSnapshot.Representation.SUMMARY);
        assertThat(summary.canonicalTextFacts()).containsExactly("11", "2");
    }

    @Test
    void integerFactsKeepRuntimeTypeAndRejectCustomNumberCallbacks() {
        ValueSnapshot byteValue = ValueSnapshot.ofInteger((byte) 7, 1);
        ValueSnapshot longValue = ValueSnapshot.ofInteger(7L, 1);
        ValueSnapshot bigInteger = ValueSnapshot.ofInteger(BigInteger.valueOf(7), 1);
        Number customNumber = new Number() {
            @Override
            public int intValue() {
                return 7;
            }

            @Override
            public long longValue() {
                return 7;
            }

            @Override
            public float floatValue() {
                return 7;
            }

            @Override
            public double doubleValue() {
                return 7;
            }

            @Override
            public String toString() {
                throw new AssertionError("custom callback must not run");
            }
        };

        assertThat(byteValue.typeCode()).isEqualTo("byte");
        assertThat(longValue.typeCode()).isEqualTo("long");
        assertThat(bigInteger.typeCode()).isEqualTo("big-integer");
        assertThatThrownBy(() -> ValueSnapshot.ofInteger(customNumber, 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported integer type");
    }

    @Test
    void floatingFactsUseRoundTripHexAndStableSpecialTokens() {
        ValueSnapshot positiveZero = ValueSnapshot.ofFloating(0.0d, 16);
        ValueSnapshot negativeZero = ValueSnapshot.ofFloating(-0.0d, 16);
        ValueSnapshot nan = ValueSnapshot.ofFloating(Float.NaN, 16);
        ValueSnapshot positiveInfinity = ValueSnapshot.ofFloating(Double.POSITIVE_INFINITY, 16);

        assertThat(positiveZero.typeCode()).isEqualTo("double");
        assertThat(positiveZero.canonicalTextFacts()).containsExactly("0x0.0p0");
        assertThat(negativeZero.canonicalTextFacts()).containsExactly("-0x0.0p0");
        assertThat(nan.typeCode()).isEqualTo("float");
        assertThat(nan.canonicalTextFacts()).containsExactly("nan");
        assertThat(positiveInfinity.canonicalTextFacts()).containsExactly("+infinity");
    }

    @Test
    void floatingFactsPreserveJdkHexWireAcrossIeee754BitPatterns() {
        for (double value : new double[] {
                0.0d, -0.0d, Double.MIN_VALUE, -Double.MIN_VALUE,
                Double.MIN_NORMAL, -Double.MIN_NORMAL, Double.MAX_VALUE, -Double.MAX_VALUE,
                1.0d, -1.0d, Math.PI, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertDoubleWire(value);
        }
        for (float value : new float[] {
                0.0f, -0.0f, Float.MIN_VALUE, -Float.MIN_VALUE,
                Float.MIN_NORMAL, -Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE,
                1.0f, -1.0f, (float) Math.PI, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFloatWire(value);
        }

        SplittableRandom random = new SplittableRandom(0x5446_4920_4650_574CL);
        for (int sample = 0; sample < 10_000; sample++) {
            assertDoubleWire(Double.longBitsToDouble(random.nextLong()));
            assertFloatWire(Float.intBitsToFloat(random.nextInt()));
        }
    }

    private static void assertDoubleWire(double value) {
        String expected = Double.isNaN(value)
                ? "nan"
                : value == Double.POSITIVE_INFINITY
                        ? "+infinity"
                        : value == Double.NEGATIVE_INFINITY ? "-infinity" : Double.toHexString(value);
        String actual = ValueSnapshot.ofFloating(value, 64).canonicalTextFacts().getFirst();
        assertEquals(expected, actual, () -> "double bits=" + Long.toHexString(Double.doubleToRawLongBits(value)));
    }

    private static void assertFloatWire(float value) {
        String expected = Float.isNaN(value)
                ? "nan"
                : value == Float.POSITIVE_INFINITY
                        ? "+infinity"
                        : value == Float.NEGATIVE_INFINITY ? "-infinity" : Float.toHexString(value);
        String actual = ValueSnapshot.ofFloating(value, 64).canonicalTextFacts().getFirst();
        assertEquals(expected, actual, () -> "float bits=" + Integer.toHexString(Float.floatToRawIntBits(value)));
    }

    @Test
    void enumKeepsDeclaringBinaryTypeAndDropsItWhenOmitted() {
        String binaryType = SampleState.class.getName();
        int exactCost = binaryType.length() + 1 + SampleState.READY.name().length();

        ValueSnapshot exact = ValueSnapshot.ofEnum(SampleState.READY, exactCost);
        ValueSnapshot omitted = ValueSnapshot.ofEnum(SampleState.READY, exactCost - 1);

        assertThat(exact.typeCode()).isEqualTo("enum");
        assertThat(exact.canonicalTextFacts()).containsExactly(binaryType, "READY");
        assertThat(omitted.representation()).isEqualTo(ValueSnapshot.Representation.OMITTED);
        assertThat(omitted.canonicalTextFacts()).isEmpty();
    }

    @Test
    void temporalFactsKeepUtcAndTypeNativeIsoWithoutSystemTimezone() {
        Instant instant = Instant.parse("2026-07-12T12:34:56.789Z");
        LocalDateTime local = LocalDateTime.parse("2026-07-12T20:34:56.789");

        ValueSnapshot dateFact = ValueSnapshot.ofTemporal(Date.from(instant), 32);
        ValueSnapshot instantFact = ValueSnapshot.ofTemporal(instant, 32);
        ValueSnapshot localFact = ValueSnapshot.ofTemporal(local, 32);

        assertThat(dateFact.typeCode()).isEqualTo("date");
        assertThat(dateFact.canonicalTextFacts()).containsExactly("2026-07-12T12:34:56.789Z");
        assertThat(instantFact.typeCode()).isEqualTo("instant");
        assertThat(instantFact.canonicalTextFacts()).containsExactly("2026-07-12T12:34:56.789Z");
        assertThat(localFact.typeCode()).isEqualTo("local-date-time");
        assertThat(localFact.canonicalTextFacts()).containsExactly("2026-07-12T20:34:56.789");
    }

    @Test
    void typeMetadataKeepsOnlyStableKindAndBoundedBinaryName() {
        String binaryName = ValueSnapshotBoundaryTests.class.getName();
        int exactCost = "class".length() + 1 + binaryName.length();

        ValueSnapshot exact = ValueSnapshot.ofTypeMetadata(ValueSnapshotBoundaryTests.class, exactCost);
        ValueSnapshot omitted = ValueSnapshot.ofTypeMetadata(ValueSnapshotBoundaryTests.class, exactCost - 1);

        assertThat(exact.typeCode()).isEqualTo("type-metadata");
        assertThat(exact.canonicalTextFacts()).containsExactly("class", binaryName);
        assertThat(omitted.representation()).isEqualTo(ValueSnapshot.Representation.OMITTED);
        assertThat(omitted.canonicalTextFacts()).isEmpty();
    }

    @Test
    void millionElementContainerKeepsOnlyKindAndExactSize() {
        ValueSnapshot snapshot = ValueSnapshot.ofContainer(
                ValueSnapshot.ContainerKind.LIST,
                1_000_000,
                7);

        assertThat(snapshot.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
        assertThat(snapshot.typeCode()).isEqualTo("list");
        assertThat(snapshot.canonicalTextFacts()).containsExactly("1000000");
        assertThatThrownBy(() -> ValueSnapshot.ofContainer(
                ValueSnapshot.ContainerKind.LIST,
                -1,
                7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringReportsShapeWithoutLeakingExactFacts() {
        ValueSnapshot snapshot = ValueSnapshot.ofString("top-secret", 10);

        assertThat(snapshot.toString())
                .contains("typeCode=string", "representation=EXACT", "factCount=1")
                .doesNotContain("top-secret");
    }

    @Test
    void independentlyCapturedFactsUseValueIdentity() {
        ValueSnapshot first = ValueSnapshot.ofString("key", 3);
        ValueSnapshot same = ValueSnapshot.ofString("key", 3);
        ValueSnapshot different = ValueSnapshot.ofString("other", 5);

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(different);
    }
}
