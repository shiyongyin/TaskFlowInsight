package com.syy.taskflowinsight.actuator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 锁定Ops只发布真实运行状态，不把Compare内部cache伪装成健康组件。 */
class CompareKernelEndpointContractTests {

    @Test
    void secureEndpointHasNoPathMatcherCacheDependency() {
        assertThat(Arrays.stream(SecureTfiEndpoint.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .map(Class::getSimpleName))
                .doesNotContain("PathMatcherCacheInterface");
    }

    @Test
    void runtimeBenchmarkAndDashboardTypesAreAbsent() {
        String performancePackage = SecureTfiEndpoint.class.getPackageName()
                .replace(".actuator", ".performance");
        List<String> removedTypes = List.of(
                performancePackage + ".BenchmarkRunner",
                performancePackage + ".BenchmarkEndpoint",
                performancePackage + ".BenchmarkReport",
                performancePackage + ".BenchmarkResult",
                performancePackage + ".dashboard.PerformanceDashboard");

        for (String removedType : removedTypes) {
            assertThatThrownBy(() -> Class.forName(removedType))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }
}
