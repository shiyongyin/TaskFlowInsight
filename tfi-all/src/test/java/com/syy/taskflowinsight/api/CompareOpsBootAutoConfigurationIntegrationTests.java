package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.ops.compare.CompareHealthIndicator;
import com.syy.taskflowinsight.ops.compare.ObservedCompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * All-in-one 制品的 Compare/Ops 真实 Boot discovery 合同。
 *
 * <p>测试只通过 {@link EnableAutoConfiguration} 触发注册，用于捕获手工注入 Registry
 * 或直接指定 auto-configuration 无法复现的解析顺序。</p>
 */
class CompareOpsBootAutoConfigurationIntegrationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class);

    @Test
    void should_observe_canonical_result_after_real_boot_discovery() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MeterRegistry.class);
            assertThat(context).hasSingleBean(CompareHealthIndicator.class);
            assertThat(context).hasSingleBean(CompareEngine.class);
            assertThat(context).hasSingleBean(ObservedCompareOperations.class);
            assertThat(context.getBeansOfType(CompareOperations.class)).hasSize(2);

            CompareEngine engine = context.getBean(CompareEngine.class);
            ObservedCompareOperations observed = context.getBean(ObservedCompareOperations.class);
            CompareOperations selected = context.getBean(CompareOperations.class);
            assertThat(selected).isSameAs(observed).isInstanceOf(CompareOperationsDecorator.class);
            assertThat(((CompareOperationsDecorator) selected).delegate()).isSameAs(engine);

            CompareRuntime runtime = context.getBean(CompareRuntime.class);
            CompareOptions options = CompareOptions.builder(runtime.policy())
                    .maxChangeDetails(1)
                    .build();
            CompareResult result = selected.compare(
                    new Sample("before-left", "before-right"),
                    new Sample("after-left", "after-right"),
                    options);

            assertThat(result.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
            assertThat(result.getLimitations())
                    .extracting(CompareLimitation::code)
                    .contains(CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED);
            assertThat(result.getDiagnostics().omittedChanges()).isPositive();
            assertMeters(context.getBean(MeterRegistry.class), result);
            assertThat(engine).isNotSameAs(observed);
        });
    }

    private static void assertMeters(MeterRegistry registry, CompareResult result) {
        assertThat(registry.get("tfi.compare.request").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("tfi.compare.duration").timer().count()).isEqualTo(1L);
        assertTagKeys(registry.find("tfi.compare.request").meters(),
                "rootAlgorithmId", "outcome", "completion");
        assertTagKeys(registry.find("tfi.compare.duration").meters(),
                "rootAlgorithmId", "outcome", "completion");

        Collection<Meter> issueMeters = registry.find("tfi.compare.issue").meters();
        assertThat(issueMeters).hasSize(result.getProblems().size() + result.getLimitations().size());
        assertTagKeys(issueMeters,
                "rootAlgorithmId", "outcome", "completion", "kind", "code", "stage");

        Meter omitted = registry.find("tfi.compare.omitted")
                .tag("kind", "change")
                .meter();
        assertThat(omitted).isNotNull();
        assertThat(registry.get("tfi.compare.omitted")
                .tag("kind", "change")
                .counter().count()).isEqualTo(result.getDiagnostics().omittedChanges());
        assertTagKeys(registry.find("tfi.compare.omitted").meters(),
                "rootAlgorithmId", "outcome", "completion", "kind");
    }

    private static void assertTagKeys(Collection<Meter> meters, String... expectedKeys) {
        assertThat(meters).isNotEmpty().allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(Tag::getKey)
                        .containsExactlyInAnyOrder(expectedKeys));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }

    /**
     * 两个字段同时变化，用于稳定触发详情上限与 omitted 诊断。
     *
     * @param left 左侧比较字段
     * @param right 右侧比较字段
     */
    private record Sample(String left, String right) {
    }
}
