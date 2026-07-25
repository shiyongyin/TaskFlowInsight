package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** keyed List 的 identity、位置与内容必须作为三类独立事实发布。 */
class KeyedListMoveContractTests {

    /** 生产入口用于覆盖唯一 request-local kernel。 */
    private final CompareEngine engine = CompareRuntime.defaults().engine();

    @Test
    void uniqueKeysPublishMovesAndKeepFieldModificationOnStableEntityPath() {
        List<Item> before = List.of(new Item(1, "before"), new Item(2, "same"));
        List<Item> after = List.of(new Item(2, "same"), new Item(1, "after"));

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).filteredOn(change -> change.kind() == ChangeKind.MOVE)
                .hasSize(2)
                .allSatisfy(change -> {
                    assertThat(change.before().orElseThrow().path().segments())
                            .anySatisfy(segment -> assertThat(segment)
                                    .isInstanceOf(EntityKeySegment.class));
                    assertThat(change.after().orElseThrow().path().segments())
                            .anySatisfy(segment -> assertThat(segment)
                                    .isInstanceOf(EntityKeySegment.class));
                    assertThat(change.before().orElseThrow().path().segments().getLast())
                            .isInstanceOf(IndexSegment.class);
                    assertThat(change.after().orElseThrow().path().segments().getLast())
                            .isInstanceOf(IndexSegment.class);
                });
        assertThat(result.getChanges())
                .filteredOn(change -> change.kind() == ChangeKind.MODIFY)
                .anySatisfy(change ->
                        assertThat(change.after().orElseThrow().path().segments())
                                .anySatisfy(segment -> assertThat(segment)
                                        .isInstanceOf(EntityKeySegment.class))
                                .contains(new PropertySegment("name")));
    }

    @Test
    void duplicateKeyPublishesW2201WithoutHidingUniqueSiblingChange() {
        List<Item> before = List.of(
                new Item(1, "duplicate-a"),
                new Item(1, "duplicate-b"),
                new Item(2, "before"));
        List<Item> after = List.of(
                new Item(1, "duplicate-c"),
                new Item(1, "duplicate-d"),
                new Item(2, "after"));

        CompareResult result = engine.compare(before, after);

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.KEY_AMBIGUOUS);
        assertThat(result.getChanges()).anySatisfy(change ->
                assertThat(change.after().orElseThrow().path().segments())
                        .contains(new PropertySegment("name")));
    }

    @Test
    void unresolvedEntityKeyPublishesW2201InsteadOfFallingBackToIndexPairing() {
        CompareResult result = engine.compare(
                List.of(new UnresolvedItem(new Object(), "before")),
                List.of(new UnresolvedItem(new Object(), "after")));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
        assertThat(result.getLimitations())
                .extracting(CompareLimitation::code)
                .contains(CompareLimitationCode.KEY_AMBIGUOUS);
        assertThat(result.getChanges()).isEmpty();
    }

    @Test
    void ignoredKeyConflictIsTypedProblemAndCannotPairByThatValue() {
        CompareResult result = engine.compare(
                List.of(new ConflictingKeyItem(1, "before")),
                List.of(new ConflictingKeyItem(1, "after")));

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.FAILED);
        assertThat(result.getProblems())
                .extracting(CompareProblem::code)
                .contains(CompareProblemCode.TYPE_DESCRIPTOR_CONFLICT);
        assertThat(result.getChanges()).isEmpty();
    }

    /** 唯一 key 支持跨位置配对，name 独立表达内容。 */
    @Entity
    private static final class Item {

        /** List 中的稳定候选 identity。 */
        @Key
        private final int id;

        /** MOVE 之外仍需比较的业务字段。 */
        private final String name;

        private Item(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** 非 scalar key 不允许通过展示、哈希或物理索引兜底。 */
    @Entity
    private static final class UnresolvedItem {

        /** Object 不属于 exact scalar key 闭集。 */
        @Key
        private final Object id;

        /** 不得在错误的索引配对下发布该字段变化。 */
        private final String name;

        private UnresolvedItem(Object id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** 被排除的Key不能暗中成为跨侧identity，否则ignore与配对相等域会互相矛盾。 */
    @Entity
    private static final class ConflictingKeyItem {

        /** 同时声明身份与忽略没有合法优先级，descriptor必须整体拒绝该identity。 */
        @Key
        @DiffIgnore
        private final int id;

        /** 若错误地按id配对，该字段会产生误导性的确定修改。 */
        private final String name;

        private ConflictingKeyItem(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
