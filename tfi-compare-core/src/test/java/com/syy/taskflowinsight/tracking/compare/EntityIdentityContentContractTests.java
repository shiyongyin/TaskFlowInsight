package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity identity 只负责配对，不能替代配对后的内容比较。
 *
 * <p>三种容器必须消费同一内核规则，否则 ID-only equals 很容易在某一条兼容路径重新吞掉字段变化。</p>
 */
class EntityIdentityContentContractTests {

    /** 冻结运行时确保三条断言经过生产 CompareEngine，而不是测试专用策略。 */
    private final CompareEngine engine = CompareRuntime.defaults().engine();

    @Test
    void idOnlyEqualsStillExposesContentChangesAcrossListSetAndMapValue() {
        IdOnlyEntity before = new IdOnlyEntity(7, "before");
        IdOnlyEntity after = new IdOnlyEntity(7, "after");

        assertEntityFieldChange(engine.compare(List.of(before), List.of(after)));
        assertEntityFieldChange(
                engine.compare(new LinkedHashSet<>(Set.of(before)), new LinkedHashSet<>(Set.of(after))));
        assertEntityFieldChange(engine.compare(Map.of("entity", before), Map.of("entity", after)));
    }

    @Test
    void nonScalarEqualsCannotTerminatePojoOrValueObjectComparison() {
        assertNameChange(engine.compare(
                new AlwaysEqualPojo("before"),
                new AlwaysEqualPojo("after")));
        assertNameChange(engine.compare(
                new AlwaysEqualValue("before"),
                new AlwaysEqualValue("after")));
    }

    @Test
    void mapValueComparisonDoesNotInvokeEntityEqualsHashOrDisplayCallbacks() {
        Map<String, ExplosiveEntity> before = new LinkedHashMap<>();
        before.put("entity", new ExplosiveEntity(1, "before"));
        Map<String, ExplosiveEntity> after = new LinkedHashMap<>();
        after.put("entity", new ExplosiveEntity(1, "after"));

        assertEntityFieldChange(engine.compare(before, after));
    }

    private static void assertEntityFieldChange(CompareResult result) {
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getChanges()).anySatisfy(change -> {
            assertThat(change.kind()).isEqualTo(ChangeKind.MODIFY);
            assertThat(change.after().orElseThrow().path().segments())
                    .anySatisfy(segment -> assertThat(segment)
                            .isInstanceOf(PropertySegment.class));
        });
    }

    private static void assertNameChange(CompareResult result) {
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(result.getChanges()).anySatisfy(change ->
                assertThat(change.after().orElseThrow().path().segments())
                        .contains(new PropertySegment("name")));
    }

    /** equals/hashCode 只表达业务身份，name 仍属于内容相等域。 */
    @Entity
    private static final class IdOnlyEntity {

        /** 容器候选配对使用的唯一 exact identity。 */
        @Key
        private final int id;

        /** identity 相同后仍必须比较的业务内容。 */
        private final String name;

        private IdOnlyEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdOnlyEntity entity && id == entity.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    /** 普通 POJO 的 equals 不是 Compare 的终局相等证据。 */
    private static final class AlwaysEqualPojo {

        /** 即使 equals 恒真也必须进入 descriptor 字段比较。 */
        private final String name;

        private AlwaysEqualPojo(String name) {
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

    /** ValueObject marker 固定选择字段语义，不再提供 equals 模式。 */
    @ValueObject
    private static final class AlwaysEqualValue {

        /** marker 后的真实内容事实。 */
        private final String name;

        private AlwaysEqualValue(String name) {
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

    /** 业务回调若被内核触发会直接让测试失败。 */
    @Entity
    private static final class ExplosiveEntity {

        /** 唯一允许参与配对的 scalar key。 */
        @Key
        private final int id;

        /** 配对后用于证明字段差异仍可见。 */
        private final String name;

        private ExplosiveEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("compare kernel must not invoke entity equals");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("compare kernel must not invoke entity hashCode");
        }

        @Override
        public String toString() {
            throw new AssertionError("compare kernel must not invoke entity toString");
        }
    }
}
