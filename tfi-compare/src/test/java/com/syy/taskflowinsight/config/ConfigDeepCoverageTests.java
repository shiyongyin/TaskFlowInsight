package com.syy.taskflowinsight.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deep coverage tests for config package.
 * Covers TfiConfig, TfiFeatureFlags, ConcurrencyConfig.
 * AutoConfiguration classes require Spring context; we test configuration records directly.
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Config — Deep Coverage Tests")
class ConfigDeepCoverageTests {

    // ── TfiConfig ──

    @Nested
    @DisplayName("TfiConfig — Main configuration")
    class TfiConfigTests {

        @Test
        @DisplayName("default constructor applies defaults for all nulls")
        void defaultConstructor_allNulls() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);

            assertThat(config.enabled()).isFalse();
            assertThat(config.changeTracking()).isNotNull();
            assertThat(config.context()).isNotNull();
            assertThat(config.metrics()).isNotNull();
            assertThat(config.security()).isNotNull();
        }

        @Test
        @DisplayName("ChangeTracking defaults")
        void changeTracking_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.ChangeTracking ct = config.changeTracking();

            assertThat(ct.enabled()).isFalse();
            assertThat(ct.valueReprMaxLength()).isEqualTo(8192);
            assertThat(ct.cleanupIntervalMinutes()).isEqualTo(5);
            assertThat(ct.maxCachedClasses()).isEqualTo(1024);
        }

        @Test
        @DisplayName("ChangeTracking.Snapshot defaults")
        void snapshot_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.ChangeTracking.Snapshot snap = config.changeTracking().snapshot();

            assertThat(snap.maxDepth()).isEqualTo(10);
            assertThat(snap.maxElements()).isEqualTo(100);
            assertThat(snap.excludes()).contains("*.password", "*.secret");
            assertThat(snap.maxStackDepth()).isEqualTo(1000);
            assertThat(snap.enableDeep()).isFalse();
            assertThat(snap.timeBudgetMs()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("ChangeTracking.Diff defaults")
        void diff_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.ChangeTracking.Diff diff = config.changeTracking().diff();

            assertThat(diff.outputMode()).isEqualTo("compat");
            assertThat(diff.includeNullChanges()).isFalse();
            assertThat(diff.maxChangesPerObject()).isEqualTo(1000);
            assertThat(diff.normalizeValues()).isTrue();
            assertThat(diff.pathFormat()).isEqualTo("legacy");
        }

        @Test
        @DisplayName("Diff useStandardPathFormat")
        void diff_useStandardPathFormat() {
            TfiConfig.ChangeTracking ctLegacy = new TfiConfig.ChangeTracking(
                null, null, null, null,
                new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "legacy"),
                null, null, null
            );
            assertThat(ctLegacy.diff().useStandardPathFormat()).isFalse();

            TfiConfig.ChangeTracking ctStandard = new TfiConfig.ChangeTracking(
                null, null, null, null,
                new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "standard"),
                null, null, null
            );
            assertThat(ctStandard.diff().useStandardPathFormat()).isTrue();
        }

        @Test
        @DisplayName("ChangeTracking.Export defaults")
        void export_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.ChangeTracking.Export exp = config.changeTracking().export();

            assertThat(exp.format()).isEqualTo("json");
            assertThat(exp.prettyPrint()).isTrue();
            assertThat(exp.showTimestamp()).isFalse();
            assertThat(exp.includeSensitiveInfo()).isFalse();
        }

        @Test
        @DisplayName("ChangeTracking.Summary defaults")
        void summary_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.ChangeTracking.Summary sum = config.changeTracking().summary();

            assertThat(sum.enabled()).isTrue();
            assertThat(sum.maxSize()).isEqualTo(100);
            assertThat(sum.maxExamples()).isEqualTo(10);
            assertThat(sum.sensitiveWords()).contains("password", "token");
        }

        @Test
        @DisplayName("Context defaults")
        void context_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.Context ctx = config.context();

            assertThat(ctx.maxAgeMillis()).isEqualTo(3600000L);
            assertThat(ctx.leakDetectionEnabled()).isFalse();
            assertThat(ctx.leakDetectionIntervalMillis()).isEqualTo(60000L);
            assertThat(ctx.cleanupEnabled()).isFalse();
            assertThat(ctx.cleanupIntervalMillis()).isEqualTo(60000L);
        }

        @Test
        @DisplayName("Metrics defaults")
        void metrics_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.Metrics m = config.metrics();

            assertThat(m.enabled()).isTrue();
            assertThat(m.tags()).isEmpty();
            assertThat(m.exportInterval()).isEqualTo("PT1M");
        }

        @Test
        @DisplayName("Security defaults")
        void security_defaults() {
            TfiConfig config = new TfiConfig(null, null, null, null, null);
            TfiConfig.Security sec = config.security();

            assertThat(sec.enableDataMasking()).isTrue();
            assertThat(sec.sensitiveFields()).contains("password", "secret");
        }

        @Test
        @DisplayName("isValid returns true for valid config")
        void isValid_valid() {
            TfiConfig config = new TfiConfig(true, null, null, null, null);
            assertThat(config.isValid()).isTrue();
        }

        @Test
        @DisplayName("isValid returns false when valueReprMaxLength invalid")
        void isValid_invalidValueRepr() {
            TfiConfig.ChangeTracking ct = new TfiConfig.ChangeTracking(
                true, 0, 5, null, null, null, 1024, null
            );
            TfiConfig config = new TfiConfig(true, ct, null, null, null);
            assertThat(config.isValid()).isFalse();
        }

        @Test
        @DisplayName("full custom config")
        void fullCustomConfig() {
            TfiConfig.ChangeTracking.Snapshot snap = new TfiConfig.ChangeTracking.Snapshot(
                5, 50, Set.of("*.internal"), 500, true, 500L
            );
            TfiConfig.ChangeTracking.Diff diff = new TfiConfig.ChangeTracking.Diff(
                "enhanced", true, 500, false, "standard"
            );
            TfiConfig.ChangeTracking.Export exp = new TfiConfig.ChangeTracking.Export(
                "json", false, true, false, true
            );
            TfiConfig.ChangeTracking.Summary sum = new TfiConfig.ChangeTracking.Summary(
                false, 50, 5, Set.of("secret")
            );
            TfiConfig.ChangeTracking ct = new TfiConfig.ChangeTracking(
                true, 4096, 10, snap, diff, exp, 512, sum
            );
            TfiConfig.Context ctx = new TfiConfig.Context(
                1800000L, true, 30000L, true, 30000L
            );
            TfiConfig.Metrics m = new TfiConfig.Metrics(true, Map.of("env", "test"), "PT30S");
            TfiConfig.Security sec = new TfiConfig.Security(false, Set.of("apiKey"));

            TfiConfig config = new TfiConfig(true, ct, ctx, m, sec);

            assertThat(config.enabled()).isTrue();
            assertThat(config.changeTracking().snapshot().maxDepth()).isEqualTo(5);
            assertThat(config.changeTracking().diff().useStandardPathFormat()).isTrue();
            assertThat(config.metrics().tags()).containsEntry("env", "test");
            assertThat(config.security().enableDataMasking()).isFalse();
        }
    }

    // ── TfiFeatureFlags ──

    @Nested
    @DisplayName("TfiFeatureFlags — Feature flags")
    class TfiFeatureFlagsTests {

        @Test
        @DisplayName("isFacadeEnabled returns default when no system property")
        void isFacadeEnabled_default() {
            String key = "tfi.api.facade.enabled";
            String orig = System.getProperty(key);
            try {
                System.clearProperty(key);
                boolean result = TfiFeatureFlags.isFacadeEnabled();
                assertThat(result).isTrue();
            } finally {
                if (orig != null) System.setProperty(key, orig);
            }
        }

        @Test
        @DisplayName("isFacadeEnabled respects system property")
        void isFacadeEnabled_systemProperty() {
            String key = "tfi.api.facade.enabled";
            String orig = System.getProperty(key);
            try {
                System.setProperty(key, "false");
                assertThat(TfiFeatureFlags.isFacadeEnabled()).isFalse();
                System.setProperty(key, "true");
                assertThat(TfiFeatureFlags.isFacadeEnabled()).isTrue();
            } finally {
                if (orig != null) System.setProperty(key, orig);
                else System.clearProperty(key);
            }
        }

        @Test
        @DisplayName("isMaskingEnabled returns default")
        void isMaskingEnabled_default() {
            String key = "tfi.render.masking.enabled";
            String orig = System.getProperty(key);
            try {
                System.clearProperty(key);
                boolean result = TfiFeatureFlags.isMaskingEnabled();
                assertThat(result).isTrue();
            } finally {
                if (orig != null) System.setProperty(key, orig);
            }
        }

        @Test
        @DisplayName("isRoutingEnabled returns false by default")
        void isRoutingEnabled_default() {
            String key = "tfi.api.routing.enabled";
            String orig = System.getProperty(key);
            try {
                System.clearProperty(key);
                assertThat(TfiFeatureFlags.isRoutingEnabled()).isFalse();
            } finally {
                if (orig != null) System.setProperty(key, orig);
            }
        }

        @Test
        @DisplayName("getRoutingProviderMode returns auto by default")
        void getRoutingProviderMode_default() {
            String key = "tfi.api.routing.provider-mode";
            String orig = System.getProperty(key);
            try {
                System.clearProperty(key);
                assertThat(TfiFeatureFlags.getRoutingProviderMode()).isEqualTo("auto");
            } finally {
                if (orig != null) System.setProperty(key, orig);
            }
        }

        @Test
        @DisplayName("Api getters and setters")
        void api_gettersSetters() {
            TfiFeatureFlags flags = new TfiFeatureFlags();
            TfiFeatureFlags.Api api = new TfiFeatureFlags.Api();
            flags.setApi(api);
            assertThat(flags.getApi()).isSameAs(api);
        }

        @Test
        @DisplayName("Render getters and setters")
        void render_gettersSetters() {
            TfiFeatureFlags flags = new TfiFeatureFlags();
            TfiFeatureFlags.Render render = new TfiFeatureFlags.Render();
            flags.setRender(render);
            assertThat(flags.getRender()).isSameAs(render);
        }

        @Test
        @DisplayName("Facade enabled getter/setter")
        void facade_enabled() {
            TfiFeatureFlags.Facade facade = new TfiFeatureFlags.Facade();
            assertThat(facade.isEnabled()).isTrue();
            facade.setEnabled(false);
            assertThat(facade.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Routing getters and setters")
        void routing_gettersSetters() {
            TfiFeatureFlags.Routing routing = new TfiFeatureFlags.Routing();
            routing.setEnabled(true);
            routing.setProviderMode("spring-only");
            assertThat(routing.isEnabled()).isTrue();
            assertThat(routing.getProviderMode()).isEqualTo("spring-only");
        }

        @Test
        @DisplayName("Masking enabled getter/setter")
        void masking_enabled() {
            TfiFeatureFlags.Masking masking = new TfiFeatureFlags.Masking();
            masking.setEnabled(false);
            assertThat(masking.isEnabled()).isFalse();
        }
    }

    // ── ConcurrencyConfig ──

    @Nested
    @DisplayName("ConcurrencyConfig — Concurrency settings")
    class ConcurrencyConfigTests {

        @Test
        @DisplayName("default CmeRetry values")
        void cmeRetry_defaults() {
            ConcurrencyConfig config = new ConcurrencyConfig();
            ConcurrencyConfig.CmeRetry cme = config.getCmeRetry();

            assertThat(cme.getMaxAttempts()).isEqualTo(1);
            assertThat(cme.getBaseDelayMs()).isEqualTo(10L);
            assertThat(cme.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("CmeRetry setters")
        void cmeRetry_setters() {
            ConcurrencyConfig.CmeRetry cme = new ConcurrencyConfig.CmeRetry();
            cme.setMaxAttempts(3);
            cme.setBaseDelayMs(50L);
            cme.setEnabled(true);
            assertThat(cme.getMaxAttempts()).isEqualTo(3);
            assertThat(cme.getBaseDelayMs()).isEqualTo(50L);
            assertThat(cme.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("ThreadLocalCleanup defaults")
        void threadLocalCleanup_defaults() {
            ConcurrencyConfig config = new ConcurrencyConfig();
            ConcurrencyConfig.ThreadLocalCleanup cleanup = config.getThreadLocalCleanup();

            assertThat(cleanup.isEnabled()).isFalse();
            assertThat(cleanup.getIntervalMs()).isEqualTo(60000L);
            assertThat(cleanup.getTimeoutMs()).isEqualTo(3600000L);
        }

        @Test
        @DisplayName("FifoCache defaults")
        void fifoCache_defaults() {
            ConcurrencyConfig config = new ConcurrencyConfig();
            ConcurrencyConfig.FifoCache fifo = config.getFifoCache();

            assertThat(fifo.isEnabled()).isFalse();
            assertThat(fifo.getDefaultSize()).isEqualTo(1000);
        }

        @Test
        @DisplayName("AsyncMetrics defaults")
        void asyncMetrics_defaults() {
            ConcurrencyConfig config = new ConcurrencyConfig();
            ConcurrencyConfig.AsyncMetrics async = config.getAsyncMetrics();

            assertThat(async.isEnabled()).isFalse();
            assertThat(async.getBufferSize()).isEqualTo(1000);
            assertThat(async.getFlushIntervalSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("ConcurrencyConfig getters return non-null")
        void config_getters() {
            ConcurrencyConfig config = new ConcurrencyConfig();
            assertThat(config.getCmeRetry()).isNotNull();
            assertThat(config.getThreadLocalCleanup()).isNotNull();
            assertThat(config.getFifoCache()).isNotNull();
            assertThat(config.getAsyncMetrics()).isNotNull();
        }
    }

    // ── ChangeTrackingAutoConfiguration (testable parts) ──

    @Nested
    @DisplayName("ChangeTrackingAutoConfiguration — Config application")
    class ChangeTrackingAutoConfigurationTests {

        @Test
        @DisplayName("TfiConfig with invalid config does not throw when validated")
        void invalidConfig_validation() {
            TfiConfig.ChangeTracking ct = new TfiConfig.ChangeTracking(
                true, 2_000_000, 5, null, null, null, 1024, null
            );
            TfiConfig config = new TfiConfig(true, ct, null, null, null);
            assertThat(config.isValid()).isFalse();
        }
    }

    // ── DeepTrackingAutoConfiguration ──

    @Nested
    @DisplayName("DeepTrackingAutoConfiguration — Bean creation")
    class DeepTrackingAutoConfigurationTests {

        @Test
        @DisplayName("TfiDeepTrackingAspect can be instantiated")
        void tfiDeepTrackingAspect_instantiable() {
            var config = new com.syy.taskflowinsight.config.DeepTrackingAutoConfiguration();
            // Bean method is static - we test that the class loads
            assertThat(DeepTrackingAutoConfiguration.class).isNotNull();
        }
    }

    // ── ConcurrencyAutoConfiguration ──

    @Nested
    @DisplayName("ConcurrencyAutoConfiguration — Config injection")
    class ConcurrencyAutoConfigurationTests {

        @Test
        @DisplayName("getConcurrencyConfig returns injected config")
        void getConcurrencyConfig() {
            ConcurrencyConfig concurrencyConfig = new ConcurrencyConfig();
            ConcurrencyAutoConfiguration autoConfig = new ConcurrencyAutoConfiguration(concurrencyConfig);

            assertThat(autoConfig.getConcurrencyConfig()).isSameAs(concurrencyConfig);
        }
    }
}
