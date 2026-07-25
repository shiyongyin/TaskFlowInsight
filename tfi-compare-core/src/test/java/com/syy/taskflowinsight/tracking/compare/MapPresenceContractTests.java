package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Map entry 的存在性合同，防止合法的 null value 被 {@link Map#get(Object)} 语义吞掉。
 */
class MapPresenceContractTests {

    private final CompareRuntime runtime = CompareRuntime.builder().build();
    private final CompareEngine engine = runtime.engine();

    @Test
    void presentNullRemovalIsRemoveAcrossRuntimeAndCompatibilityStrategy() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("present-null", null);
        Map<String, Object> after = new LinkedHashMap<>();

        assertContainsKind(engine.compare(before, after), ChangeKind.REMOVE);
        assertContainsKind(
                new MapCompareStrategy().compare(
                        before, after, CompareOptions.defaults(runtime.policy())),
                ChangeKind.REMOVE);
    }

    @Test
    void sameKeyNullToValueIsModifyRatherThanAdd() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("key", null);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("key", "value");

        assertContainsKind(engine.compare(before, after), ChangeKind.MODIFY);
        assertContainsKind(
                new MapCompareStrategy().compare(
                        before, after, CompareOptions.defaults(runtime.policy())),
                ChangeKind.MODIFY);
    }

    @Test
    void nullKeyRemainsAnAddressableMapEntry() {
        Map<Object, Object> before = new LinkedHashMap<>();
        before.put(null, "before");
        Map<Object, Object> after = new LinkedHashMap<>();
        after.put(null, "after");

        CompareResult result = engine.compare(before, after);

        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(ChangeKind.MODIFY);
            assertThat(change.after().orElseThrow().path().segments())
                    .singleElement()
                    .isInstanceOfSatisfying(MapKeySegment.class, segment ->
                            assertThat(segment.key()).isEqualTo(ValueSnapshot.exactNull()));
        });
    }

    @Test
    void differentKeysProduceRemoveAndAddWithoutRenameGuessing() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("customer-name", "same-value");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("customer_name", "same-value");

        CompareResult result = new MapCompareStrategy().compare(
                before, after, CompareOptions.defaults(runtime.policy()));

        assertThat(result.getChanges())
                .extracting(FieldChange::kind)
                .containsExactlyInAnyOrder(ChangeKind.REMOVE, ChangeKind.ADD)
                .doesNotContain(ChangeKind.MOVE);
    }

    @Test
    void unaddressableKeyDoesNotHideAddressableEntryChanges() {
        OpaqueKey opaqueKey = new OpaqueKey();
        Map<Object, Object> before = new LinkedHashMap<>();
        before.put(opaqueKey, "opaque-before");
        before.put("stable", "before");
        Map<Object, Object> after = new LinkedHashMap<>();
        after.put(opaqueKey, "opaque-after");
        after.put("stable", "after");

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getChanges()).anySatisfy(change ->
                assertThat(change.after().orElseThrow().path().segments())
                        .anySatisfy(segment -> assertThat(segment)
                                .isEqualTo(new MapKeySegment(
                                        ValueSnapshot.ofString("stable", "stable".length())))));
        assertThat(result.getLimitations()).anySatisfy(limitation ->
                assertThat(limitation.code()).isEqualTo(CompareLimitationCode.KEY_AMBIGUOUS));
    }

    @Test
    void duplicateCanonicalKeysDoNotOverwriteAddressableSibling() {
        Map<Object, Object> before = new IdentityHashMap<>();
        before.put(distinctString("duplicate"), "before-a");
        before.put(distinctString("duplicate"), "before-b");
        before.put("stable", "before");
        Map<Object, Object> after = new IdentityHashMap<>();
        after.put(distinctString("duplicate"), "after-a");
        after.put(distinctString("duplicate"), "after-b");
        after.put("stable", "after");

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.KEY_AMBIGUOUS);
        assertThat(result.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(ChangeKind.MODIFY);
            assertThat(change.after().orElseThrow().path().segments())
                    .containsExactly(new MapKeySegment(ValueSnapshot.ofString("stable", 6)));
        });
    }

    @Test
    void detailedCompatibilityPathDoesNotInvokeOpaqueKeyCallbacks() {
        ExplosiveOpaqueKey opaqueKey = new ExplosiveOpaqueKey();
        Map<Object, Object> before = new IdentityHashMap<>();
        before.put(opaqueKey, "opaque-before");
        before.put("stable", "before");
        Map<Object, Object> after = new IdentityHashMap<>();
        after.put(opaqueKey, "opaque-after");
        after.put("stable", "after");

        var records = new MapCompareStrategy().generateDetailedChangeRecords(
                "Order", "attributes", before, after, "session", "task");

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getOldValue()).isEqualTo(ValueSnapshot.ofString("before", 6));
            assertThat(record.getNewValue()).isEqualTo(ValueSnapshot.ofString("after", 5));
        });
    }

    private static void assertContainsKind(CompareResult result, ChangeKind expectedKind) {
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges())
                .extracting(FieldChange::kind)
                .contains(expectedKind);
    }

    private static String distinctString(String value) {
        return new String(value.toCharArray());
    }

    private static final class OpaqueKey {
    }

    private static final class ExplosiveOpaqueKey {

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("Map compatibility path must not invoke key equals");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("Map compatibility path must not invoke key hashCode");
        }

        @Override
        public String toString() {
            throw new AssertionError("Map compatibility path must not invoke key toString");
        }
    }
}
