package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAndSpringCompareContractTests {

    private static final List<String> RETIRED_STATIC_FLAGS = List.of(
            "tfi.enabled",
            "tfi.api.facade.enabled",
            "tfi.api.routing.enabled");

    @Test
    void springPolicyAndRetiredFlagsCannotChangeStaticTfiRuntime() {
        ComparisonProvider staticProvider = ProviderRegistry.resolve(ComparisonProvider.class);
        RETIRED_STATIC_FLAGS.forEach(key -> System.setProperty(key, "false"));
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(TfiCompareAutoConfiguration.class))
                    .withPropertyValues("tfi.compare.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        Object value = new Object();
                        CompareResult springResult = context.getBean(CompareOperations.class)
                                .compare(value, value);
                        CompareResult staticResult = TFI.compare(value, value);

                        assertThat(springResult.getCompletion()).isEqualTo(CompareCompletion.DISABLED);
                        assertThat(staticResult.getOutcome()).isEqualTo(CompareOutcome.EQUAL);
                        assertThat(staticResult.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
                        assertThat(ProviderRegistry.resolve(ComparisonProvider.class)).isSameAs(staticProvider);
                    });
        } finally {
            RETIRED_STATIC_FLAGS.forEach(System::clearProperty);
        }
    }
}
