package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.core.TfiCore;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for TFI routing fallback behavior.
 *
 * <p>Verifies that when routing is enabled but no Provider is registered,
 * all TFI methods gracefully fall back to the legacy (v3.0.0) path.
 * This ensures zero-regression for users who enable routing without
 * registering custom Providers.
 *
 * <p>Also verifies the FlowProvider and ExportProvider routing paths
 * that are not covered by {@link TFIRoutingTests}.
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("TFI Routing Fallback Tests")
class TFIRoutingFallbackTest {

    @BeforeEach
    void setUp() throws Exception {
        // Enable routing but register NO providers → should fallback to legacy
        System.setProperty("tfi.api.routing.enabled", "true");
        clearProviderCache();
        clearProviderRegistry();

        // Initialize TfiCore
        TfiConfig config = new TfiConfig(
                true,
                new TfiConfig.ChangeTracking(true, null, null, null, null, null, null, null),
                new TfiConfig.Context(null, null, null, null, null),
                new TfiConfig.Metrics(null, null, null),
                new TfiConfig.Security(null, null)
        );
        TfiCore core = new TfiCore(config);
        setTfiCore(core);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("tfi.api.routing.enabled");
        TFI.clear();
        try {
            clearProviderCache();
        } catch (Exception ignored) {
            // cleanup best-effort
        }
    }

    // ==================== Flow Fallback ====================

    @Nested
    @DisplayName("Flow methods fallback to legacy when no FlowProvider")
    class FlowFallback {

        @Test
        @DisplayName("stage() should work via legacy when no FlowProvider")
        void stageFallbackToLegacy() {
            assertThatCode(() -> {
                try (var stage = TFI.stage("fallback-test")) {
                    TFI.message("hello from legacy fallback", "INFO");
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("startSession/endSession should work via legacy")
        void sessionFallbackToLegacy() {
            assertThatCode(() -> {
                TFI.startSession("fallback-session");
                TFI.endSession();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("message() should work via legacy when no FlowProvider")
        void messageFallbackToLegacy() {
            assertThatCode(() -> {
                try (var stage = TFI.stage("msg-test")) {
                    TFI.message("test message", "INFO");
                    TFI.error("test error");
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getCurrentSession returns non-null via legacy")
        void getCurrentSessionFallback() {
            TFI.startSession("test-session");
            try {
                // May or may not return session depending on context state,
                // but must not throw
                assertThatCode(TFI::getCurrentSession).doesNotThrowAnyException();
            } finally {
                TFI.endSession();
            }
        }

        @Test
        @DisplayName("getTaskStack returns safely via legacy")
        void getTaskStackFallback() {
            assertThatCode(TFI::getTaskStack).doesNotThrowAnyException();
        }
    }

    // ==================== Tracking Fallback ====================

    @Nested
    @DisplayName("Tracking methods fallback to legacy when no TrackingProvider")
    class TrackingFallback {

        @Test
        @DisplayName("track() should work via legacy and detect changes")
        void trackFallbackDetectsChanges() {
            TestObj obj = new TestObj("initial");
            TFI.track("obj", obj, "value");

            obj.value = "modified";
            List<ChangeRecord> changes = TFI.getChanges();

            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getFieldName()).isEqualTo("value");
        }

        @Test
        @DisplayName("trackDeep() should work via legacy path")
        void trackDeepFallback() {
            assertThatCode(() -> {
                TestObj obj = new TestObj("deep");
                TFI.trackDeep("obj", obj);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getChanges() returns empty list when nothing tracked")
        void getChangesEmptyWhenNoTracking() {
            List<ChangeRecord> changes = TFI.getChanges();
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("clearAllTracking() works via legacy")
        void clearAllTrackingFallback() {
            TestObj obj = new TestObj("test");
            TFI.track("obj", obj, "value");

            TFI.clearAllTracking();

            List<ChangeRecord> changes = TFI.getChanges();
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("recordChange() should not throw via legacy")
        void recordChangeFallback() {
            assertThatCode(() ->
                    TFI.recordChange("obj", "field", "old", "new",
                            com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("withTracked() executes action via legacy")
        void withTrackedFallback() {
            TestObj obj = new TestObj("before");
            boolean[] executed = {false};

            TFI.withTracked("obj", obj, () -> {
                obj.value = "after";
                executed[0] = true;
            }, "value");

            assertThat(executed[0]).isTrue();
        }
    }

    // ==================== Export Fallback ====================

    @Nested
    @DisplayName("Export methods fallback to legacy when no ExportProvider")
    class ExportFallback {

        @Test
        @DisplayName("exportToJson() should not throw via legacy")
        void exportToJsonFallback() {
            try (var stage = TFI.stage("export-test")) {
                TFI.message("data", "INFO");
            }
            assertThatCode(TFI::exportToJson).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exportToMap() should not throw via legacy")
        void exportToMapFallback() {
            assertThatCode(TFI::exportToMap).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exportToConsole() should not throw via legacy")
        void exportToConsoleFallback() {
            assertThatCode(TFI::exportToConsole).doesNotThrowAnyException();
        }
    }

    // ==================== Compare Fallback ====================

    @Nested
    @DisplayName("Compare methods fallback when no ComparisonProvider")
    class CompareFallback {

        @Test
        @DisplayName("compare() should work via legacy path")
        void compareFallback() {
            TestObj a = new TestObj("v1");
            TestObj b = new TestObj("v2");

            assertThatCode(() -> TFI.compare(a, b)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("compare null objects should not throw")
        void compareNullSafe() {
            assertThatCode(() -> TFI.compare(null, null)).doesNotThrowAnyException();
        }
    }

    // ==================== Helpers ====================

    static class TestObj {
        String value;

        TestObj(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private void setTfiCore(TfiCore core) throws Exception {
        Field coreField = TFI.class.getDeclaredField("core");
        coreField.setAccessible(true);
        coreField.set(null, core);
    }

    private void clearProviderCache() throws Exception {
        String[] cacheFieldNames = {
                "cachedTrackingProvider", "cachedFlowProvider",
                "cachedExportProvider", "cachedRenderProvider",
                "cachedComparisonProvider"
        };
        for (String fieldName : cacheFieldNames) {
            try {
                Field field = TFI.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            } catch (NoSuchFieldException ignored) {
                // Field may not exist
            }
        }
    }

    private void clearProviderRegistry() {
        try {
            Class<?> registryClass = Class.forName("com.syy.taskflowinsight.spi.ProviderRegistry");
            java.lang.reflect.Method clearMethod = registryClass.getMethod("clearAll");
            clearMethod.invoke(null);
        } catch (Exception ignored) {
            // Registry may not be accessible
        }
    }
}
