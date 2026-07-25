package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Compare artifact 对 Core 优先级契约的模块边界测试。
 *
 * <p>三类下游 SPI 由 compare 模块负责验证，避免 Core 为测试方便反向依赖 Compare 类型。
 */
class ProviderPriorityContractTests {

    @Test
    void compareArtifactProvidersUseOneCorePriorityContract() {
        assertThat(PrioritizedProvider.class).isAssignableFrom(ComparisonProvider.class);
        assertThat(PrioritizedProvider.class).isAssignableFrom(TrackingProvider.class);
        assertThat(PrioritizedProvider.class).isAssignableFrom(RenderProvider.class);

        assertThat(declaredMethodNames(ComparisonProvider.class)).doesNotContain("priority");
        assertThat(declaredMethodNames(TrackingProvider.class)).doesNotContain("priority");
        assertThat(declaredMethodNames(RenderProvider.class)).doesNotContain("priority");
    }

    private static List<String> declaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }
}
