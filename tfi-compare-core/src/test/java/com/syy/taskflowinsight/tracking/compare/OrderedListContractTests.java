package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 普通 List 的 ordered-index 合同；位置是业务事实，不能由编辑距离或集合路由改写。
 */
class OrderedListContractTests {

    private final CompareRuntime runtime = CompareRuntime.builder().build();
    private final CompareOptions options = CompareOptions.defaults(runtime.policy());

    @Test
    void ordinaryReorderingProducesIndexModificationsWithoutMove() {
        List<String> before = List.of("first", "second");
        List<String> after = List.of("second", "first");

        assertIndexModifications(runtime.engine().compare(before, after));
        assertIndexModifications(new SimpleListStrategy().compare(before, after, options));
    }

    @Test
    void nullAndDuplicateValuesRetainTheirIndexes() {
        List<String> before = Arrays.asList(null, "duplicate", "duplicate");
        List<String> after = Arrays.asList("value", "duplicate", null);

        CompareResult result = runtime.engine().compare(before, after);

        assertThat(result.getChanges()).hasSize(2);
        assertThat(result.getChanges())
                .extracting(change -> change.after().or(() -> change.before())
                        .orElseThrow().path().segments().getLast())
                .containsExactly(new IndexSegment(0), new IndexSegment(2));
    }

    @Test
    void nestedContainersKeepListAndMapAddressSegments() {
        List<Map<String, List<String>>> before = List.of(
                Map.of("items", List.of("same", "before")));
        List<Map<String, List<String>>> after = List.of(
                Map.of("items", List.of("same", "after")));

        CompareResult result = runtime.engine().compare(before, after);

        assertThat(result.getChanges()).singleElement().satisfies(change ->
                assertThat(change.after().orElseThrow().path().segments())
                        .containsExactly(
                                new IndexSegment(0),
                                new MapKeySegment(ValueSnapshot.ofString("items", 5)),
                                new IndexSegment(1)));
    }

    @Test
    void mixedListDoesNotRouteByFirstEntitySample() {
        ProbeEntityStrategy entityStrategy = new ProbeEntityStrategy();
        ListCompareExecutor executor = new ListCompareExecutor(
                List.of(new SimpleListStrategy(), entityStrategy));
        SampleEntity entity = new SampleEntity("id");

        CompareResult result = executor.compare(
                List.of(entity, "before"),
                List.of(entity, "after"),
                options);

        assertThat(entityStrategy.invoked).isFalse();
        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.DIFFERENT);
    }

    private static void assertIndexModifications(CompareResult result) {
        assertThat(result.getChanges()).hasSize(2);
        assertThat(result.getChanges()).allSatisfy(change ->
                assertThat(change.kind()).isEqualTo(ChangeKind.MODIFY));
        assertThat(result.getChanges())
                .extracting(change -> change.after().orElseThrow().path().segments().getLast())
                .containsExactly(new IndexSegment(0), new IndexSegment(1));
    }

    @Entity
    private static final class SampleEntity {

        /** 仅用于触发已解析 Entity 候选分支的稳定测试身份。 */
        @Key
        private final String id;

        private SampleEntity(String id) {
            this.id = id;
        }
    }

    private static final class ProbeEntityStrategy implements ListCompareStrategy {

        /** 记录混合列表是否被错误路由到 Entity 策略。 */
        private boolean invoked;

        @Override
        public CompareResult compare(List<?> list1, List<?> list2, CompareOptions compareOptions) {
            invoked = true;
            return CompareResult.identical();
        }

        @Override
        public boolean supportsMoveDetection() {
            return true;
        }

        @Override
        public String getStrategyName() {
            return "ENTITY";
        }

        @Override
        public int getMaxRecommendedSize() {
            return Integer.MAX_VALUE;
        }
    }
}
