package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定3.x TrackingOptions只能单向收紧canonical CompareOptions的兼容边界。
 *
 * <p>反射断言用于防止后续又把depth、budget或性能开关作为第二套实例状态加回来。</p>
 */
class TrackingOptionsCompatContractTests {

    @Test
    void shouldKeepOnlyOneCanonicalOptionsField() {
        Field[] instanceFields = Arrays.stream(TrackingOptions.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toArray(Field[]::new);

        assertThat(instanceFields).singleElement().satisfies(field ->
                assertThat(field.getType()).isEqualTo(CompareOptions.class));
    }

    @Test
    void shouldMapDepthAndCollectionTokensOneWay() {
        ComparePolicy defaults = ComparePolicy.defaults();

        CompareOptions shallow = TrackingOptions.shallow().toCompareOptions();
        CompareOptions deep = TrackingOptions.deep().toCompareOptions();
        CompareOptions summary = TrackingOptions.builder()
                .collectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY)
                .build()
                .toCompareOptions();
        CompareOptions ignore = TrackingOptions.builder()
                .collectionStrategy(TrackingOptions.CollectionStrategy.IGNORE)
                .build()
                .toCompareOptions();

        assertThat(shallow.maxDepth()).isZero();
        assertThat(shallow.includeCollectionContents()).isFalse();
        assertThat(deep.maxDepth()).isEqualTo(defaults.maxDepth());
        assertThat(deep.includeCollectionContents()).isTrue();
        assertThat(summary.includeCollectionContents()).isTrue();
        assertThat(ignore.includeCollectionContents()).isFalse();

        assertThat(CompareRuntime.defaults().engine().compare(
                List.of("same", "before"), List.of("same", "after"), summary).getOutcome())
                .isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(CompareRuntime.defaults().engine().compare(
                List.of("before"), List.of("after"), ignore).getOutcome())
                .isEqualTo(CompareOutcome.EQUAL);
        assertThat(CompareRuntime.defaults().engine().compare(
                List.of("same"), List.of("same", "extra"), ignore).getOutcome())
                .isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(CompareRuntime.defaults().engine().compare(
                List.of("same"), Set.of("same"), ignore).getOutcome())
                .isEqualTo(CompareOutcome.DIFFERENT);
        assertThat(CompareRuntime.defaults().engine().compare(
                null, List.of("same"), ignore).getOutcome())
                .isEqualTo(CompareOutcome.DIFFERENT);
    }

    @Test
    void shouldClampLegacyResourceRequestsToCanonicalPolicy() {
        ComparePolicy defaults = ComparePolicy.defaults();

        CompareOptions mapped = TrackingOptions.builder()
                .maxDepth(Integer.MAX_VALUE)
                .timeBudgetMs(Long.MAX_VALUE)
                .build()
                .toCompareOptions();

        assertThat(mapped.maxDepth()).isEqualTo(defaults.maxDepth());
        assertThat(mapped.deadline()).isEqualTo(defaults.deadline());
    }

    @Test
    void shouldCompileLegacyFieldSelectorsWithoutRetainingRawSets() {
        CompareOptions mapped = TrackingOptions.builder()
                .includeFields("customer")
                .excludeFields("secret")
                .build()
                .toCompareOptions();

        assertThat(mapped.getPolicy().includePathPatterns()).hasSize(1);
        assertThat(mapped.getPolicy().excludePathPatterns()).hasSize(1);
        assertThat(TrackingOptions.builder().includeFields("customer").build().getIncludeFields())
                .isEmpty();
    }

    @Test
    void shouldNotRestoreRemovedSemanticOverrideTypes() {
        assertThat(Arrays.stream(TrackingOptions.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain("getForcedObjectType", "getForcedStrategy", "isTypeAwareEnabled");
        assertThat(Arrays.stream(TrackingOptions.Builder.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain("enableTypeAware", "forceObjectType", "forceStrategy");
    }
}
