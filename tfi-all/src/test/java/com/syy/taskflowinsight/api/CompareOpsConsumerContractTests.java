package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration;
import com.syy.taskflowinsight.ops.compare.CompareObservationAutoConfiguration;
import com.syy.taskflowinsight.ops.compare.ObservedCompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 all-in-one 组合根只观测 Spring direct Operations 调用。 */
class CompareOpsConsumerContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TfiCompareAutoConfiguration.class,
                    CompareObservationAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void should_observe_facade_but_not_static_or_direct_engine_calls() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CompareOperations.class))
                    .isInstanceOf(ObservedCompareOperations.class);

            TfiListDiffFacade facade = context.getBean(TfiListDiffFacade.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            facade.diff(List.of("same"), List.of("same"));

            assertThat(registry.get("tfi.compare.request").counter().count()).isEqualTo(1.0);

            Object value = new Object();
            TFI.compare(value, value);
            context.getBean(CompareEngine.class).compare(value, value);

            assertThat(registry.get("tfi.compare.request").counter().count())
                    .as("static 与 direct Engine 不属于 Spring Operations 观测范围")
                    .isEqualTo(1.0);
        });
    }
}
