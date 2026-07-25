package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tracking旧action/query SPI的结构防回归合同。
 *
 * <p>provider一旦重新获得action或全局查询方法，就可能绕过final executor并恢复重试或隐式状态。</p>
 */
class TrackingLegacyApiRemovalContractTests {

    @Test
    void providerExposesOnlyTypedBatchBegin() {
        assertThat(Arrays.stream(TrackingProvider.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName() + Arrays.toString(method.getParameterTypes())))
                .containsExactly("begin[interface java.util.List, class "
                        + "com.syy.taskflowinsight.tracking.compare.CompareOptions]");
        assertThat(Arrays.stream(DefaultTrackingProvider.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("begin", "priority", "toString");
        assertThat(Modifier.isFinal(TrackingExecutor.class.getModifiers())).isTrue();
    }
}
