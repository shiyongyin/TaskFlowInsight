package com.syy.taskflowinsight.aspect;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolver;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "tfi.runPerfTests", matches = "true")
class TfiAnnotationAspectPerformanceTests {

    private static final int WORKLOAD = 200_000;
    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURE_ITERATIONS = 200;
    private static final int SAMPLE_COUNT = 5;
    private static volatile long BLACKHOLE;

    @BeforeAll
    static void enableTfiFallbackCore() {
        TFI.enable();
    }

    @Test
    @DisplayName("AOP aspect overhead stays below 2 percent compared to direct invocation")
    void aspectOverheadShouldStayBelowTwoPercent() {
        DummyService directService = new DummyService();
        DummyService proxiedService = createProxiedService();

        warmUp(directService, proxiedService);

        long baselineNanos = averageDurationNanos(directService);
        long proxiedNanos = averageDurationNanos(proxiedService);

        assertThat(baselineNanos).isGreaterThan(0);
        assertThat(proxiedNanos).isGreaterThan(0);

        double overheadPercent = ((double) (proxiedNanos - baselineNanos) * 100.0) / baselineNanos;

        System.out.printf(
            "TFI AOP benchmark -> baseline: %dns, proxied: %dns, overhead: %.3f%%%n",
            baselineNanos, proxiedNanos, overheadPercent
        );

        assertThat(overheadPercent)
            .withFailMessage("Expected aspect overhead <2%% but was %.3f%% (baseline=%dns, proxied=%dns)",
                overheadPercent, baselineNanos, proxiedNanos)
            .isLessThan(2.0);
    }

    private static DummyService createProxiedService() {
        DummyService target = new DummyService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new TfiAnnotationAspect(
            new SafeSpELEvaluator(),
            new UnifiedDataMasker()
        ));
        return factory.getProxy();
    }

    private static void warmUp(DummyService directService, DummyService proxiedService) {
        measureOnce(directService, WARMUP_ITERATIONS);
        measureOnce(proxiedService, WARMUP_ITERATIONS);
        TFI.clear();
    }

    private static long averageDurationNanos(DummyService service) {
        long total = 0;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            TFI.clear();
            total += measureOnce(service, MEASURE_ITERATIONS);
        }
        TFI.clear();
        return total / SAMPLE_COUNT;
    }

    private static long measureOnce(DummyService service, int iterations) {
        long sink = 0;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += service.performWork(WORKLOAD);
        }
        long duration = System.nanoTime() - start;
        BLACKHOLE = sink;
        return duration;
    }

    static class DummyService {

        DummyService() {
        }

        @TfiTask(value = "perf-test", samplingRate = 0.0, logArgs = false, logResult = false, logException = false)
        long performWork(int workload) {
            long acc = 0;
            for (int i = 0; i < workload; i++) {
                acc += (i & 1) == 0 ? i : -i;
                acc ^= (acc << 1) ^ (acc >> 3);
            }
            return acc;
        }
    }

    private static class NoopConfigurationResolver implements ConfigurationResolver {
        @Override
        public <T> T resolve(String key, Class<T> type, T defaultValue) {
            return defaultValue;
        }

        @Override
        public <T> Optional<T> resolve(String key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public ConfigPriority getEffectivePriority(String key) {
            return ConfigPriority.DEFAULT_VALUE;
        }

        @Override
        public Map<ConfigPriority, ConfigSource> getConfigSources(String key) {
            return Map.of();
        }

        @Override
        public void setRuntimeConfig(String key, Object value) {
            // no-op
        }

        @Override
        public void clearRuntimeConfig(String key) {
            // no-op
        }

        @Override
        public boolean isEnvVariablesEnabled() {
            return false;
        }

        @Override
        public void refresh() {
            // no-op
        }
    }
}
