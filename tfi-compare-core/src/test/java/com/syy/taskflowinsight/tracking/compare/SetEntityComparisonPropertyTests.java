package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Set 按成员逐项解释，结果不得依赖首项类型或迭代顺序。 */
class SetEntityComparisonPropertyTests {

    /** 同一个冻结引擎重复执行，用于排除请求状态和插入顺序污染。 */
    private final CompareEngine engine = CompareRuntime.defaults().engine();

    @Test
    void mixedNullScalarAndEntityFactsAreIndependentOfInsertionOrder() {
        CompareResult first = engine.compare(
                mixedSet(null, "stable", new SetEntity(1, "before")),
                mixedSet(new SetEntity(1, "after"), "stable", null));
        CompareResult second = engine.compare(
                mixedSet(new SetEntity(1, "before"), null, "stable"),
                mixedSet("stable", null, new SetEntity(1, "after")));

        assertCanonicalFactsEqual(first, second);
        assertThat(first.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(first.getChanges()).anySatisfy(change ->
                assertThat(change.after().orElseThrow().path().segments())
                        .anySatisfy(segment -> assertThat(segment)
                                .isInstanceOf(EntityKeySegment.class)));
    }

    @Test
    void unmarkedComplexMembersUseFullSnapshotsEvenWhenEqualsReturnsTrue() {
        Set<AlwaysEqualMember> before = identitySet(new AlwaysEqualMember("before"));
        Set<AlwaysEqualMember> after = identitySet(new AlwaysEqualMember("after"));

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).isNotEmpty();
    }

    @Test
    void duplicateCanonicalEntityIdentityPublishesW2201AndKeepsUniqueSibling() {
        Set<SetEntity> before = identitySet(
                new SetEntity(1, "duplicate-a"),
                new SetEntity(1, "duplicate-b"),
                new SetEntity(2, "before"));
        Set<SetEntity> after = identitySet(
                new SetEntity(1, "duplicate-c"),
                new SetEntity(1, "duplicate-d"),
                new SetEntity(2, "after"));

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.KEY_AMBIGUOUS);
        assertThat(result.getChanges()).anySatisfy(change ->
                assertThat(change.after().orElseThrow().path().segments())
                        .anySatisfy(segment -> assertThat(segment)
                                .isInstanceOf(EntityKeySegment.class)));
    }

    @Test
    void complexMemberGroupingIsIndependentOfIterationOrder() {
        Set<PlainMember> beforeFirst = orderedSet(
                new PlainMember(1, "stable"),
                new PlainMember(2, "before"));
        Set<PlainMember> afterFirst = orderedSet(
                new PlainMember(2, "after"),
                new PlainMember(1, "stable"));
        Set<PlainMember> beforeSecond = orderedSet(
                new PlainMember(2, "before"),
                new PlainMember(1, "stable"));
        Set<PlainMember> afterSecond = orderedSet(
                new PlainMember(1, "stable"),
                new PlainMember(2, "after"));

        CompareResult first = engine.compare(beforeFirst, afterFirst);
        CompareResult second = engine.compare(beforeSecond, afterSecond);

        assertCanonicalFactsEqual(first, second);
        assertThat(first.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(first.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
    }

    @Test
    void duplicateCompleteComplexSnapshotsRemainExplicitlyAmbiguous() {
        Set<PlainMember> before = identitySet(
                new PlainMember(1, "same"),
                new PlainMember(1, "same"));
        Set<PlainMember> after = identitySet(
                new PlainMember(1, "same"),
                new PlainMember(1, "same"));

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.KEY_AMBIGUOUS);
    }

    private static LinkedHashSet<Object> mixedSet(Object first, Object second, Object third) {
        LinkedHashSet<Object> values = new LinkedHashSet<>();
        values.add(first);
        values.add(second);
        values.add(third);
        return values;
    }

    @SafeVarargs
    private static <T> Set<T> orderedSet(T... values) {
        Set<T> result = new LinkedHashSet<>();
        Collections.addAll(result, values);
        return result;
    }

    @SafeVarargs
    private static <T> Set<T> identitySet(T... values) {
        Set<T> result = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(result, values);
        return result;
    }

    private static void assertCanonicalFactsEqual(CompareResult left, CompareResult right) {
        assertThat(left.getChanges()).containsExactlyElementsOf(right.getChanges());
        assertThat(left.getProblems()).containsExactlyElementsOf(right.getProblems());
        assertThat(left.getLimitations()).containsExactlyElementsOf(right.getLimitations());
    }

    /** Set Entity 使用 id 配对，name 仍属于内容。 */
    @Entity
    private static final class SetEntity {

        /** Set 中的 exact candidate identity。 */
        @Key
        private final int id;

        /** 配对后必须继续比较的字段。 */
        private final String name;

        private SetEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SetEntity entity && id == entity.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    /** 完整字段快照而非业务 equals 决定未标注复杂成员内容。 */
    private static final class AlwaysEqualMember {

        /** 唯一可观察内容，变化必须形成 Set 容器差异。 */
        private final String name;

        private AlwaysEqualMember(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return true;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    /** 多成员完整快照使用字段事实排序，不能依赖Set迭代位置。 */
    private static final class PlainMember {

        /** 用于区分两个复杂成员的稳定内容字段。 */
        private final int id;

        /** 用于制造单个成员内容变化。 */
        private final String name;

        private PlainMember(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
