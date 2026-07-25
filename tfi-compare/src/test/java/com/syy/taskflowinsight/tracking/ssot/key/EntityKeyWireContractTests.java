package com.syy.taskflowinsight.tracking.ssot.key;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 地址键wire合同，锁定identity与普通value equality之间的边界。
 */
class EntityKeyWireContractTests {

    @Test
    void bigDecimalScaleRemainsPartOfAddressIdentity() {
        EntityKeyWire oneDecimalPlace = wire(new BigDecimal("1.0"));
        EntityKeyWire twoDecimalPlaces = wire(new BigDecimal("1.00"));

        assertThat(oneDecimalPlace).isNotEqualTo(twoDecimalPlaces);
        assertThat(oneDecimalPlace.compareTo(twoDecimalPlaces)).isNotZero();
    }

    @Test
    void floatingIdentityUsesCanonicalJvmBits() {
        EntityKeyWire positiveZero = wire(0.0d);
        EntityKeyWire negativeZero = wire(-0.0d);
        EntityKeyWire firstNan = wire(Double.NaN);
        EntityKeyWire secondNan = wire(Double.longBitsToDouble(0x7ff8_0000_0000_0001L));

        assertThat(positiveZero).isNotEqualTo(negativeZero);
        assertThat(firstNan).isEqualTo(secondNan);
    }

    @Test
    void enumDeclaringTypeIsPartOfAddressIdentity() {
        assertThat(wire(FirstStatus.READY)).isNotEqualTo(wire(SecondStatus.READY));
    }

    @Test
    void stringIdentityPreservesOriginalUnicodeAndRejectsMalformedUtf16() {
        EntityKeyWire composed = wire("\u00E9");
        EntityKeyWire decomposed = wire("e\u0301");

        assertThat(composed).isNotEqualTo(decomposed);
        assertThat(KeyComponent.tryCapture("\uD800", 512)).isEmpty();
    }

    @Test
    void unsupportedKeyDoesNotInvokeBusinessCallbacks() {
        assertThat(KeyComponent.tryCapture(new CallbackProbe(), 512)).isEmpty();
    }

    @Test
    void entityWireHonorsExactByteBoundaryWithoutTruncation() {
        KeyComponent component = KeyComponent.tryCapture("key", 512).orElseThrow();
        EntityKeyWire baseline = EntityKeyWire.tryCreate(
                "example.Order", List.of(component), 8, 512).orElseThrow();

        assertThat(EntityKeyWire.tryCreate(
                "example.Order", List.of(component), 8, baseline.encodedLength())).contains(baseline);
        assertThat(EntityKeyWire.tryCreate(
                "example.Order", List.of(component), 8, baseline.encodedLength() - 1)).isEmpty();
    }

    @Test
    void entityWireRejectsComponentOverflowWithoutDroppingTail() {
        List<KeyComponent> components = List.of(
                KeyComponent.tryCapture("tenant", 512).orElseThrow(),
                KeyComponent.tryCapture(42L, 512).orElseThrow());

        assertThat(EntityKeyWire.tryCreate("example.Order", components, 2, 512)).isPresent();
        assertThat(EntityKeyWire.tryCreate("example.Order", components, 1, 512)).isEmpty();
    }

    private static EntityKeyWire wire(Object value) {
        KeyComponent component = KeyComponent.tryCapture(value, 512).orElseThrow();
        return EntityKeyWire.tryCreate(
                "example.Order",
                List.of(component),
                8,
                512).orElseThrow();
    }

    /** 第一种声明类型，用于证明同名常量不能跨类型合并。 */
    private enum FirstStatus {
        /** 第一种声明类型下的就绪状态。 */
        READY
    }

    /** 第二种声明类型，用于形成同名但不同identity的对照。 */
    private enum SecondStatus {
        /** 第二种声明类型下的就绪状态。 */
        READY
    }

    /** 任一业务回调被触发都会让合同立即失败。 */
    private static final class CallbackProbe {

        @Override
        public String toString() {
            throw new AssertionError("key identity must not call toString");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("key identity must not call hashCode");
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("key identity must not call equals");
        }
    }
}
