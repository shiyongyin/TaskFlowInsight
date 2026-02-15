package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Deep coverage tests for tracking/snapshot package.
 * Maximizes coverage for ObjectSnapshot, ObjectSnapshotDeep, ObjectSnapshotDeepOptimized,
 * SnapshotFacade, SnapshotFacadeOptimized, DirectSnapshotProvider, FacadeSnapshotProvider,
 * SnapshotProviders, SnapshotConfig, ShallowSnapshotStrategy, DeepSnapshotStrategy, ShallowReferenceMode.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Snapshot Deep Coverage — 快照深度覆盖测试")
class SnapshotDeepCoverageTests {

    // ── ObjectSnapshot static methods ──

    @Nested
    @DisplayName("ObjectSnapshot")
    class ObjectSnapshotTests {

        @AfterEach
        void tearDown() {
            ObjectSnapshot.clearCaches();
        }

        @Test
        @DisplayName("capture null target returns empty map")
        void capture_nullTarget_returnsEmpty() {
            assertThat(ObjectSnapshot.capture("x", null)).isEmpty();
        }

        @Test
        @DisplayName("capture with null fields uses all fields")
        void capture_nullFields_capturesAll() {
            TestUser user = new TestUser("A", 1, "a@b.com");
            Map<String, Object> snap = ObjectSnapshot.capture("u", user, (String[]) null);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with empty fields uses all fields")
        void capture_emptyFields_capturesAll() {
            TestUser user = new TestUser("A", 1, "a@b.com");
            Map<String, Object> snap = ObjectSnapshot.capture("u", user);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with non-existent field skips")
        void capture_nonExistentField_skips() {
            TestUser user = new TestUser("A", 1, "a@b.com");
            Map<String, Object> snap = ObjectSnapshot.capture("u", user, "nonexistent");
            assertThat(snap).isEmpty();
        }

        @Test
        @DisplayName("repr null returns formatted")
        void repr_null() {
            assertThat(ObjectSnapshot.repr(null)).isNotNull();
        }

        @Test
        @DisplayName("repr with maxLength truncates long string")
        void repr_maxLength_truncates() {
            String longStr = "x".repeat(20000);
            String result = ObjectSnapshot.repr(longStr, 100);
            assertThat(result).contains("truncated");
        }

        @Test
        @DisplayName("repr Date uses timestamp")
        void repr_date() {
            Date d = new Date(1000L);
            assertThat(ObjectSnapshot.repr(d, 100)).isEqualTo("1000");
        }

        @Test
        @DisplayName("setMaxValueLength and getMaxValueLength")
        void maxValueLength_config() {
            int orig = ObjectSnapshot.getMaxValueLength();
            ObjectSnapshot.setMaxValueLength(5000);
            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(5000);
            ObjectSnapshot.setMaxValueLength(orig);
        }

        @Test
        @DisplayName("setMaxValueLength ignores non-positive")
        void setMaxValueLength_ignoresNonPositive() {
            int orig = ObjectSnapshot.getMaxValueLength();
            ObjectSnapshot.setMaxValueLength(0);
            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(orig);
            ObjectSnapshot.setMaxValueLength(-1);
            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(orig);
        }

        @Test
        @DisplayName("clearCaches does not throw")
        void clearCaches() {
            assertThatCode(ObjectSnapshot::clearCaches).doesNotThrowAnyException();
        }
    }

    // ── ObjectSnapshotDeep ──

    @Nested
    @DisplayName("ObjectSnapshotDeep")
    class ObjectSnapshotDeepTests {

        private SnapshotConfig config;

        @BeforeEach
        void setUp() {
            config = new SnapshotConfig();
        }

        @Test
        @DisplayName("captureDeep null returns empty")
        void captureDeep_null() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            assertThat(deep.captureDeep(null, 5, Collections.emptySet(), Collections.emptySet())).isEmpty();
        }

        @Test
        @DisplayName("captureDeep simple object")
        void captureDeep_simpleObject() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(
                Map.of("a", 1, "b", "x"),
                3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep nested object")
        void captureDeep_nestedObject() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            TestUserWithAddress user = new TestUserWithAddress("A", new TestAddress("St", "City", "Z"));
            Map<String, Object> snap = deep.captureDeep(user, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep with includeFields")
        void captureDeep_includeFields() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            TestUser user = new TestUser("A", 1, "a@b");
            Set<String> include = Set.of("name");
            Map<String, Object> snap = deep.captureDeep(user, 3, include, Collections.emptySet());
            assertThat(snap).containsKey("name");
        }

        @Test
        @DisplayName("captureDeep with excludePatterns")
        void captureDeep_excludePatterns() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("name", "x", "password", "secret");
            Set<String> exclude = Set.of("*.password");
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), exclude);
            assertThat(snap).doesNotContainKey("password");
        }

        @Test
        @DisplayName("captureDeep with TrackingOptions null")
        void captureDeep_optionsNull() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("x", 1), null);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep with TrackingOptions DEEP")
        void captureDeep_optionsDeep() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            TrackingOptions opts = TrackingOptions.builder()
                .depth(TrackingOptions.TrackingDepth.DEEP)
                .maxDepth(5)
                .build();
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1), opts);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("setCollectionStrategy")
        void setCollectionStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.IGNORE);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT);
            deep.setCollectionStrategy(null);
        }

        @Test
        @DisplayName("setTypeAwareEnabled")
        void setTypeAwareEnabled() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            Map<String, Object> snap = deep.captureDeep(new TestUser("A", 1, "a@b"), 3,
                Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("getMetrics returns map")
        void getMetrics() {
            Map<String, Long> m = ObjectSnapshotDeep.getMetrics();
            assertThat(m).containsKeys("depth.limit.reached", "cycle.detected", "path.excluded");
        }

        @Test
        @DisplayName("resetMetrics")
        void resetMetrics() {
            ObjectSnapshotDeep.resetMetrics();
        }
    }

    // ── ObjectSnapshotDeepOptimized ──

    @Nested
    @DisplayName("ObjectSnapshotDeepOptimized")
    class ObjectSnapshotDeepOptimizedTests {

        @Test
        @DisplayName("captureDeep null")
        void captureDeep_null() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            assertThat(opt.captureDeep(null, 5, Collections.emptySet(), Collections.emptySet())).isEmpty();
        }

        @Test
        @DisplayName("captureDeep simple")
        void captureDeep_simple() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> snap = opt.captureDeep(Map.of("a", 1), 3,
                Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep nested object")
        void captureDeep_nested() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            TestUserWithAddress user = new TestUserWithAddress("A", new TestAddress("S", "C", "Z"));
            Map<String, Object> snap = opt.captureDeep(user, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("getMetrics and resetMetrics")
        void metrics() {
            Map<String, Long> m = ObjectSnapshotDeepOptimized.getMetrics();
            assertThat(m).isNotEmpty();
            ObjectSnapshotDeepOptimized.resetMetrics();
        }

        @Test
        @DisplayName("clearCaches")
        void clearCaches() {
            assertThatCode(ObjectSnapshotDeepOptimized::clearCaches).doesNotThrowAnyException();
        }
    }

    // ── SnapshotFacade ──

    @Nested
    @DisplayName("SnapshotFacade")
    class SnapshotFacadeTests {

        @Test
        @DisplayName("capture null returns empty")
        void capture_null() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            assertThat(facade.capture("x", null)).isEmpty();
        }

        @Test
        @DisplayName("capture shallow when deep disabled")
        void capture_shallow() {
            SnapshotConfig config = new SnapshotConfig();
            config.setEnableDeep(false);
            SnapshotFacade facade = new SnapshotFacade(config);
            Map<String, Object> snap = facade.capture("u", new TestUser("A", 1, "a@b"));
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture deep when enabled")
        void capture_deep() {
            SnapshotConfig config = new SnapshotConfig();
            config.setEnableDeep(true);
            SnapshotFacade facade = new SnapshotFacade(config);
            Map<String, Object> snap = facade.capture("u", new TestUser("A", 1, "a@b"));
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with options null")
        void capture_optionsNull() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            assertThat(facade.capture("u", new TestUser("A", 1, "a@b"), (TrackingOptions) null)).isNotEmpty();
        }

        @Test
        @DisplayName("capture with options DEEP")
        void capture_optionsDeep() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            TrackingOptions opts = TrackingOptions.builder()
                .depth(TrackingOptions.TrackingDepth.DEEP)
                .maxDepth(5)
                .build();
            Map<String, Object> snap = facade.capture("u", new TestUser("A", 1, "a@b"), opts);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("getConfig and setEnableDeep")
        void configAndEnable() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            assertThat(facade.getConfig()).isSameAs(config);
            facade.setEnableDeep(true);
        }
    }

    // ── SnapshotFacadeOptimized ──

    @Nested
    @DisplayName("SnapshotFacadeOptimized")
    class SnapshotFacadeOptimizedTests {

        @Test
        @DisplayName("capture null")
        void capture_null() {
            SnapshotConfig config = new SnapshotConfig();
            ShallowSnapshotStrategy shallow = new ShallowSnapshotStrategy();
            DeepSnapshotStrategy deep = new DeepSnapshotStrategy(config);
            SnapshotFacadeOptimized facade = new SnapshotFacadeOptimized(config, shallow, deep);
            assertThat(facade.capture("x", null)).isEmpty();
        }

        @Test
        @DisplayName("capture shallow")
        void capture_shallow() {
            SnapshotConfig config = new SnapshotConfig();
            config.setEnableDeep(false);
            ShallowSnapshotStrategy shallow = new ShallowSnapshotStrategy();
            DeepSnapshotStrategy deep = new DeepSnapshotStrategy(config);
            SnapshotFacadeOptimized facade = new SnapshotFacadeOptimized(config, shallow, deep);
            facade.init();
            Map<String, Object> snap = facade.capture("u", new TestUser("A", 1, "a@b"));
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("captureAsync")
        void captureAsync() throws Exception {
            SnapshotConfig config = new SnapshotConfig();
            ShallowSnapshotStrategy shallow = new ShallowSnapshotStrategy();
            DeepSnapshotStrategy deep = new DeepSnapshotStrategy(config);
            SnapshotFacadeOptimized facade = new SnapshotFacadeOptimized(config, shallow, deep);
            facade.init();
            Map<String, Object> snap = facade.captureAsync("u", new TestUser("A", 1, "a@b")).get();
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("setStrategy and setEnableDeep")
        void setStrategy() {
            SnapshotConfig config = new SnapshotConfig();
            ShallowSnapshotStrategy shallow = new ShallowSnapshotStrategy();
            DeepSnapshotStrategy deep = new DeepSnapshotStrategy(config);
            SnapshotFacadeOptimized facade = new SnapshotFacadeOptimized(config, shallow, deep);
            facade.init();
            facade.setStrategy("shallow");
            facade.setStrategy("nonexistent");
            facade.setEnableDeep(true);
        }

        @Test
        @DisplayName("getCurrentStrategyName getAvailableStrategies getConfig getMetrics")
        void getters() {
            SnapshotConfig config = new SnapshotConfig();
            ShallowSnapshotStrategy shallow = new ShallowSnapshotStrategy();
            DeepSnapshotStrategy deep = new DeepSnapshotStrategy(config);
            SnapshotFacadeOptimized facade = new SnapshotFacadeOptimized(config, shallow, deep);
            facade.init();
            assertThat(facade.getCurrentStrategyName()).isNotNull();
            assertThat(facade.getAvailableStrategies()).isNotEmpty();
            assertThat(facade.getConfig()).isNotNull();
            assertThat(facade.getMetrics()).isNotEmpty();
        }
    }

    // ── DirectSnapshotProvider / FacadeSnapshotProvider ──

    @Nested
    @DisplayName("SnapshotProviders")
    class SnapshotProviderTests {

        @Test
        @DisplayName("DirectSnapshotProvider captureBaseline")
        void directCaptureBaseline() {
            DirectSnapshotProvider p = new DirectSnapshotProvider();
            Map<String, Object> snap = p.captureBaseline("u", new TestUser("A", 1, "a@b"), null);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("DirectSnapshotProvider captureWithOptions DEEP")
        void directCaptureWithOptionsDeep() {
            DirectSnapshotProvider p = new DirectSnapshotProvider();
            TrackingOptions opts = TrackingOptions.builder()
                .depth(TrackingOptions.TrackingDepth.DEEP)
                .maxDepth(5)
                .build();
            Map<String, Object> snap = p.captureWithOptions("u", new TestUser("A", 1, "a@b"), opts);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("DirectSnapshotProvider captureWithOptions SHALLOW")
        void directCaptureWithOptionsShallow() {
            DirectSnapshotProvider p = new DirectSnapshotProvider();
            TrackingOptions opts = TrackingOptions.builder()
                .depth(TrackingOptions.TrackingDepth.SHALLOW)
                .build();
            Map<String, Object> snap = p.captureWithOptions("u", new TestUser("A", 1, "a@b"), opts);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("FacadeSnapshotProvider captureBaseline")
        void facadeCaptureBaseline() {
            FacadeSnapshotProvider p = new FacadeSnapshotProvider();
            Map<String, Object> snap = p.captureBaseline("u", new TestUser("A", 1, "a@b"), new String[0]);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("FacadeSnapshotProvider captureWithOptions")
        void facadeCaptureWithOptions() {
            FacadeSnapshotProvider p = new FacadeSnapshotProvider();
            TrackingOptions opts = TrackingOptions.builder()
                .depth(TrackingOptions.TrackingDepth.DEEP)
                .build();
            Map<String, Object> snap = p.captureWithOptions("u", new TestUser("A", 1, "a@b"), opts);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("SnapshotProviders.get returns provider")
        void snapshotProvidersGet() {
            SnapshotProvider p = SnapshotProviders.get();
            assertThat(p).isNotNull();
        }
    }

    // ── SnapshotConfig ──

    @Nested
    @DisplayName("SnapshotConfig")
    class SnapshotConfigTests {

        @Test
        @DisplayName("all properties getters/setters")
        void configProperties() {
            SnapshotConfig c = new SnapshotConfig();
            c.setEnableDeep(true);
            c.setMaxDepth(5);
            c.setMaxStackDepth(2000);
            c.setTimeBudgetMs(100);
            c.setCollectionSummaryThreshold(50);
            c.setMetricsEnabled(false);
            c.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
            c.setExcludePackages(List.of("com.internal"));
            c.setRegexExcludes(List.of("^debug.*"));
            c.setDefaultExclusionsEnabled(false);
            c.setIncludePatterns(List.of("email"));
            c.setExcludePatterns(List.of("*.secret"));

            assertThat(c.isEnableDeep()).isTrue();
            assertThat(c.getMaxDepth()).isEqualTo(5);
            assertThat(c.getMaxStackDepth()).isEqualTo(2000);
            assertThat(c.getTimeBudgetMs()).isEqualTo(100);
            assertThat(c.getCollectionSummaryThreshold()).isEqualTo(50);
            assertThat(c.isMetricsEnabled()).isFalse();
            assertThat(c.getShallowReferenceMode()).isEqualTo(ShallowReferenceMode.COMPOSITE_STRING);
            assertThat(c.getIncludePatternSet()).contains("email");
            assertThat(c.getExcludePatternSet()).contains("*.secret");
            assertThat(c.getRegexExcludeSet()).contains("^debug.*");
        }

        @Test
        @DisplayName("shouldExclude with include patterns")
        void shouldExclude_includePatterns() {
            SnapshotConfig c = new SnapshotConfig();
            c.setIncludePatterns(List.of("email"));
            assertThat(c.shouldExclude("password")).isTrue();
            assertThat(c.shouldExclude("email")).isFalse();
        }

        @Test
        @DisplayName("shouldExclude null path")
        void shouldExclude_nullPath() {
            SnapshotConfig c = new SnapshotConfig();
            assertThat(c.shouldExclude(null)).isFalse();
        }
    }

    // ── ShallowSnapshotStrategy / DeepSnapshotStrategy ──

    @Nested
    @DisplayName("SnapshotStrategies")
    class SnapshotStrategyTests {

        @Test
        @DisplayName("ShallowSnapshotStrategy null target")
        void shallow_null() {
            ShallowSnapshotStrategy s = new ShallowSnapshotStrategy();
            assertThat(s.capture("x", null, new SnapshotConfig())).isEmpty();
        }

        @Test
        @DisplayName("DeepSnapshotStrategy validateConfig invalid maxDepth")
        void deep_validateConfig_invalidMaxDepth() {
            DeepSnapshotStrategy s = new DeepSnapshotStrategy(new SnapshotConfig());
            SnapshotConfig bad = new SnapshotConfig();
            bad.setMaxDepth(-1);
            assertThatThrownBy(() -> s.validateConfig(bad))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("DeepSnapshotStrategy validateConfig invalid maxStackDepth")
        void deep_validateConfig_invalidMaxStackDepth() {
            DeepSnapshotStrategy s = new DeepSnapshotStrategy(new SnapshotConfig());
            SnapshotConfig bad = new SnapshotConfig();
            bad.setMaxStackDepth(50);
            assertThatThrownBy(() -> s.validateConfig(bad))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("DeepSnapshotStrategy supportsAsync")
        void deep_supportsAsync() {
            DeepSnapshotStrategy s = new DeepSnapshotStrategy(new SnapshotConfig());
            assertThat(s.supportsAsync()).isTrue();
        }
    }

    // ── ShallowReferenceMode ──

    @Nested
    @DisplayName("ShallowReferenceMode")
    class ShallowReferenceModeTests {

        @Test
        @DisplayName("fromString null/empty returns VALUE_ONLY")
        void fromString_nullEmpty() {
            assertThat(ShallowReferenceMode.fromString(null)).isEqualTo(ShallowReferenceMode.VALUE_ONLY);
            assertThat(ShallowReferenceMode.fromString("")).isEqualTo(ShallowReferenceMode.VALUE_ONLY);
        }

        @Test
        @DisplayName("requiresKeyExtraction")
        void requiresKeyExtraction() {
            assertThat(ShallowReferenceMode.VALUE_ONLY.requiresKeyExtraction()).isFalse();
            assertThat(ShallowReferenceMode.COMPOSITE_STRING.requiresKeyExtraction()).isTrue();
            assertThat(ShallowReferenceMode.COMPOSITE_MAP.requiresKeyExtraction()).isTrue();
        }
    }

    // ── Test data ──

    static class TestUser {
        String name;
        int age;
        String email;

        TestUser(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
    }

    static class TestAddress {
        String street;
        String city;
        String zip;

        TestAddress(String street, String city, String zip) {
            this.street = street;
            this.city = city;
            this.zip = zip;
        }
    }

    static class TestUserWithAddress {
        String name;
        TestAddress address;

        TestUserWithAddress(String name, TestAddress address) {
            this.name = name;
            this.address = address;
        }
    }
}
