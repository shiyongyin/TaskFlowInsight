package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.InputViolation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证默认SPI只委托共享runtime，且不吞掉调用方可修复的typed输入错误。 */
class DefaultComparisonProviderContractTests {

    @Test
    void spiExposesOnlyPolicyAwareCompareContract() throws NoSuchMethodException {
        assertThat(Arrays.stream(ComparisonProvider.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName))
                .containsOnly("compare")
                .hasSize(2);
        assertThat(ComparisonProvider.class.getDeclaredMethod(
                "compare", Object.class, Object.class, CompareOptions.class))
                .satisfies(method -> assertThat(Modifier.isAbstract(method.getModifiers())).isFalse());

        ComparisonProvider legacyShape = (before, after) -> CompareResult.identical();
        assertThatThrownBy(() -> legacyShape.compare("before", "after", CompareOptions.builder().build()))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.INVALID_INPUT_SHAPE));
    }

    @Test
    void builtInProvidersShareOneHolderRuntimeInsteadOfOwningSeparateGraphs() {
        assertThat(DefaultComparisonProvider.class.getDeclaredFields())
                .filteredOn(field -> field.getType() == CompareRuntime.class)
                .isEmpty();
        assertThat(DefaultTrackingProvider.class.getDeclaredFields())
                .filteredOn(field -> field.getType() == CompareRuntime.class)
                .isEmpty();
        assertThat(DefaultCompareRuntimeHolder.class.getDeclaredFields())
                .filteredOn(field -> field.getType() == CompareRuntime.class)
                .singleElement()
                .satisfies(field -> {
                    assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
                    assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
                });
    }

    @Test
    void explicitNullOptionsArePropagatedAsTypedInputFailure() {
        DefaultComparisonProvider provider = new DefaultComparisonProvider();

        assertThatThrownBy(() -> provider.compare(new Object(), new Object(), null))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.NULL_OPTIONS));
    }

    @Test
    void identitySimilarityIsPublishedOnlyWhenTheRequestEnablesIt() {
        DefaultComparisonProvider provider = new DefaultComparisonProvider();
        Object same = new Object();

        assertThat(provider.compare(same, same).similarity()).isEmpty();
        assertThat(provider.compare(
                same,
                same,
                CompareOptions.builder().computeSimilarity(true).build()).similarity())
                .isPresent();
    }
}
