package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for tracking/snapshot package.
 * Targets ObjectSnapshot, ObjectSnapshotDeep, ObjectSnapshotDeepOptimized,
 * SnapshotFacade, SnapshotConfig, DirectSnapshotProvider, FacadeSnapshotProvider.
 *
 * @since 3.0.0
 */
@DisplayName("Snapshot Max Coverage — 快照最大覆盖测试")
class SnapshotMaxCoverageTests {

    @AfterEach
    void tearDown() {
        ObjectSnapshot.clearCaches();
        ObjectSnapshotDeep.resetMetrics();
        ObjectSnapshotDeepOptimized.resetMetrics();
        ObjectSnapshotDeepOptimized.clearCaches();
    }

    // ── ObjectSnapshot ──

    @Nested
    @DisplayName("ObjectSnapshot")
    class ObjectSnapshotTests {

        @Test
        @DisplayName("capture null target")
        void capture_nullTarget() {
            assertThat(ObjectSnapshot.capture("x", null)).isEmpty();
        }

        @Test
        @DisplayName("capture with primitive types")
        void capture_primitives() {
            PrimitiveBean bean = new PrimitiveBean();
            Map<String, Object> snap = ObjectSnapshot.capture("p", bean);
            assertThat(snap).containsKey("intVal").containsKey("longVal").containsKey("booleanVal");
        }

        @Test
        @DisplayName("capture with String int BigDecimal Date")
        void capture_stringIntBigDecimalDate() {
            MixedBean bean = new MixedBean("a", 1, BigDecimal.TEN, new Date());
            Map<String, Object> snap = ObjectSnapshot.capture("m", bean);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with Collection and Map")
        void capture_collectionMap() {
            CollectionBean bean = new CollectionBean();
            bean.list = List.of(1, 2);
            bean.map = Map.of("k", "v");
            Map<String, Object> snap = ObjectSnapshot.capture("c", bean);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with enum")
        void capture_enum() {
            EnumBean bean = new EnumBean();
            bean.status = TestStatus.ACTIVE;
            Map<String, Object> snap = ObjectSnapshot.capture("e", bean);
            assertThat(snap).containsKey("status");
        }

        @Test
        @DisplayName("capture with null fields param")
        void capture_nullFields() {
            MixedBean bean = new MixedBean("x", 0, null, null);
            Map<String, Object> snap = ObjectSnapshot.capture("m", bean, (String[]) null);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with empty fields")
        void capture_emptyFields() {
            MixedBean bean = new MixedBean("x", 0, null, null);
            Map<String, Object> snap = ObjectSnapshot.capture("m", bean);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("capture with non-existent field")
        void capture_nonExistentField() {
            MixedBean bean = new MixedBean("x", 0, null, null);
            Map<String, Object> snap = ObjectSnapshot.capture("m", bean, "nonexistent");
            assertThat(snap).isEmpty();
        }

        @Test
        @DisplayName("repr null")
        void repr_null() {
            assertThat(ObjectSnapshot.repr(null)).isNotNull();
        }

        @Test
        @DisplayName("repr with maxLength truncates")
        void repr_maxLength_truncates() {
            String longStr = "x".repeat(20000);
            String result = ObjectSnapshot.repr(longStr, 100);
            assertThat(result).contains("truncated");
        }

        @Test
        @DisplayName("repr Date")
        void repr_date() {
            String r = ObjectSnapshot.repr(new Date(), 500);
            assertThat(r).isNotNull().doesNotContain("truncated");
        }

        @Test
        @DisplayName("setMaxValueLength getMaxValueLength")
        void maxValueLength() {
            int orig = ObjectSnapshot.getMaxValueLength();
            ObjectSnapshot.setMaxValueLength(5000);
            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(5000);
            ObjectSnapshot.setMaxValueLength(orig);
        }

        @Test
        @DisplayName("setMaxValueLength invalid ignored")
        void setMaxValueLength_invalid() {
            int orig = ObjectSnapshot.getMaxValueLength();
            ObjectSnapshot.setMaxValueLength(0);
            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(orig);
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
            config.setMaxDepth(5);
            config.setCollectionSummaryThreshold(10);
        }

        @Test
        @DisplayName("captureDeep null root")
        void captureDeep_nullRoot() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            assertThat(deep.captureDeep(null, 5, Collections.emptySet(), Collections.emptySet())).isEmpty();
        }

        @Test
        @DisplayName("captureDeep primitives")
        void captureDeep_primitives() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> result = deep.captureDeep(42, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsEntry("", 42);
        }

        @Test
        @DisplayName("captureDeep String")
        void captureDeep_string() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> result = deep.captureDeep("hello", 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsEntry("", "hello");
        }

        @Test
        @DisplayName("captureDeep enum")
        void captureDeep_enum() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> result = deep.captureDeep(TestStatus.ACTIVE, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep Date")
        void captureDeep_date() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Date d = new Date();
            Map<String, Object> result = deep.captureDeep(d, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep BigDecimal")
        void captureDeep_bigDecimal() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> result = deep.captureDeep(BigDecimal.ONE, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep List")
        void captureDeep_list() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            List<Object> list = List.of("a", 1, true);
            Map<String, Object> result = deep.captureDeep(list, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep Map")
        void captureDeep_map() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> map = Map.of("k1", "v1", "k2", 2);
            Map<String, Object> result = deep.captureDeep(map, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep array")
        void captureDeep_array() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Object[] arr = new Object[]{"a", "b"};
            Map<String, Object> result = deep.captureDeep(arr, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep primitive array")
        void captureDeep_primitiveArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = new int[]{1, 2, 3};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep nested object")
        void captureDeep_nestedObject() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            NestedBean bean = new NestedBean();
            bean.name = "outer";
            bean.inner = new NestedBean();
            bean.inner.name = "inner";
            Map<String, Object> result = deep.captureDeep(bean, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep circular reference")
        void captureDeep_circularRef() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            CircularBean bean = new CircularBean();
            bean.name = "a";
            bean.self = bean;
            Map<String, Object> result = deep.captureDeep(bean, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsValue("<circular-reference>");
        }

        @Test
        @DisplayName("captureDeep depth limit")
        void captureDeep_depthLimit() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            NestedBean bean = new NestedBean();
            bean.name = "a";
            NestedBean curr = bean;
            for (int i = 0; i < 5; i++) {
                curr.inner = new NestedBean();
                curr.inner.name = "level" + i;
                curr = curr.inner;
            }
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsValue("<depth-limit>");
        }

        @Test
        @DisplayName("captureDeep with TrackingOptions")
        void captureDeep_withOptions() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            TrackingOptions options = TrackingOptions.deep();
            Map<String, Object> result = deep.captureDeep(new MixedBean("x", 1, null, null), options);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("getMetrics")
        void getMetrics() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.captureDeep("x", 1, Collections.emptySet(), Collections.emptySet());
            Map<String, Long> metrics = ObjectSnapshotDeep.getMetrics();
            assertThat(metrics).containsKey("depth.limit.reached").containsKey("cycle.detected");
        }

        @Test
        @DisplayName("setCollectionStrategy")
        void setCollectionStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.IGNORE);
            Map<String, Object> result = deep.captureDeep(List.of(1, 2, 3), 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }
    }

    // ── ObjectSnapshotDeepOptimized ──

    @Nested
    @DisplayName("ObjectSnapshotDeepOptimized")
    class ObjectSnapshotDeepOptimizedTests {

        private SnapshotConfig config;

        @BeforeEach
        void setUp() {
            config = new SnapshotConfig();
            config.setMaxDepth(5);
            config.setCollectionSummaryThreshold(10);
        }

        @Test
        @DisplayName("captureDeep null")
        void captureDeep_null() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            assertThat(opt.captureDeep(null, 5, Collections.emptySet(), Collections.emptySet())).isEmpty();
        }

        @Test
        @DisplayName("captureDeep simple types")
        void captureDeep_simpleTypes() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> r1 = opt.captureDeep("s", 2, Collections.emptySet(), Collections.emptySet());
            Map<String, Object> r2 = opt.captureDeep(123, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(r1).isNotEmpty();
            assertThat(r2).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep List")
        void captureDeep_list() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> result = opt.captureDeep(List.of(1, 2), 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep Map")
        void captureDeep_map() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> result = opt.captureDeep(Map.of("a", 1), 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep array")
        void captureDeep_array() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> result = opt.captureDeep(new Object[]{"x"}, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep primitive array")
        void captureDeep_primitiveArray() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> result = opt.captureDeep(new int[]{1, 2}, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep object with fields")
        void captureDeep_object() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            Map<String, Object> result = opt.captureDeep(new NestedBean() {{ name = "x"; }}, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep circular")
        void captureDeep_circular() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            CircularBean bean = new CircularBean();
            bean.name = "a";
            bean.self = bean;
            Map<String, Object> result = opt.captureDeep(bean, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsValue("<circular-reference>");
        }

        @Test
        @DisplayName("captureDeep with includeFields")
        void captureDeep_includeFields() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            NestedBean bean = new NestedBean();
            bean.name = "n";
            bean.inner = new NestedBean();
            bean.inner.name = "inner";
            Map<String, Object> result = opt.captureDeep(bean, 5, Set.of("name"), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureDeep with excludePatterns")
        void captureDeep_excludePatterns() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            NestedBean bean = new NestedBean();
            bean.name = "n";
            Map<String, Object> result = opt.captureDeep(bean, 5, Collections.emptySet(), Set.of("*.secret"));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("getMetrics")
        void getMetrics() {
            ObjectSnapshotDeepOptimized opt = new ObjectSnapshotDeepOptimized(config);
            opt.captureDeep("x", 1, Collections.emptySet(), Collections.emptySet());
            Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
            assertThat(metrics).containsKey("field.cache.size");
        }
    }

    // ── SnapshotConfig ──

    @Nested
    @DisplayName("SnapshotConfig")
    class SnapshotConfigTests {

        @Test
        @DisplayName("all properties")
        void allProperties() {
            SnapshotConfig c = new SnapshotConfig();
            c.setEnableDeep(true);
            c.setMaxDepth(5);
            c.setMaxStackDepth(500);
            c.setTimeBudgetMs(100);
            c.setCollectionSummaryThreshold(50);
            c.setMetricsEnabled(false);
            c.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
            c.setExcludePackages(List.of("com.internal"));
            c.setRegexExcludes(List.of("^debug.*"));
            c.setDefaultExclusionsEnabled(false);
            c.setIncludePatterns(List.of("*.name"));
            c.setExcludePatterns(List.of("*.password"));

            assertThat(c.isEnableDeep()).isTrue();
            assertThat(c.getMaxDepth()).isEqualTo(5);
            assertThat(c.getIncludePatternSet()).contains("*.name");
            assertThat(c.getExcludePatternSet()).contains("*.password");
            assertThat(c.getRegexExcludeSet()).contains("^debug.*");
        }

        @Test
        @DisplayName("shouldExclude with include patterns")
        void shouldExclude_includePatterns() {
            SnapshotConfig c = new SnapshotConfig();
            c.setIncludePatterns(List.of("user.name"));
            assertThat(c.shouldExclude("user.email")).isTrue();
            assertThat(c.shouldExclude("user.name")).isFalse();
        }

        @Test
        @DisplayName("shouldExclude with exclude patterns")
        void shouldExclude_excludePatterns() {
            SnapshotConfig c = new SnapshotConfig();
            c.setIncludePatterns(Collections.emptyList());
            c.setExcludePatterns(List.of("*.secret"));
            assertThat(c.shouldExclude("user.secret")).isTrue();
        }

        @Test
        @DisplayName("shouldExclude null")
        void shouldExclude_null() {
            SnapshotConfig c = new SnapshotConfig();
            assertThat(c.shouldExclude(null)).isFalse();
        }
    }

    // ── SnapshotFacade ──

    @Nested
    @DisplayName("SnapshotFacade")
    class SnapshotFacadeTests {

        @Test
        @DisplayName("capture null target")
        void capture_nullTarget() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            assertThat(facade.capture("x", null)).isEmpty();
        }

        @Test
        @DisplayName("capture shallow default")
        void capture_shallowDefault() {
            SnapshotConfig config = new SnapshotConfig();
            config.setEnableDeep(false);
            SnapshotFacade facade = new SnapshotFacade(config);
            Map<String, Object> result = facade.capture("u", new MixedBean("a", 1, null, null));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("capture deep when enabled")
        void capture_deepWhenEnabled() {
            SnapshotConfig config = new SnapshotConfig();
            config.setEnableDeep(true);
            SnapshotFacade facade = new SnapshotFacade(config);
            Map<String, Object> result = facade.capture("u", new NestedBean() {{ name = "x"; }});
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("capture with TrackingOptions DEEP")
        void capture_withOptionsDeep() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            TrackingOptions options = TrackingOptions.deep();
            Map<String, Object> result = facade.capture("u", new NestedBean() {{ name = "x"; }}, options);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("capture with TrackingOptions null")
        void capture_withOptionsNull() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            Map<String, Object> result = facade.capture("u", new MixedBean("a", 1, null, null), (TrackingOptions) null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("getConfig setEnableDeep")
        void getConfig_setEnableDeep() {
            SnapshotConfig config = new SnapshotConfig();
            SnapshotFacade facade = new SnapshotFacade(config);
            assertThat(facade.getConfig()).isSameAs(config);
            facade.setEnableDeep(true);
            assertThat(config.isEnableDeep()).isTrue();
        }
    }

    // ── DirectSnapshotProvider ──

    @Nested
    @DisplayName("DirectSnapshotProvider")
    class DirectSnapshotProviderTests {

        @Test
        @DisplayName("captureBaseline")
        void captureBaseline() {
            DirectSnapshotProvider provider = new DirectSnapshotProvider();
            Map<String, Object> result = provider.captureBaseline("u", new MixedBean("a", 1, null, null), null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureWithOptions DEEP")
        void captureWithOptions_deep() {
            DirectSnapshotProvider provider = new DirectSnapshotProvider();
            TrackingOptions options = TrackingOptions.deep();
            Map<String, Object> result = provider.captureWithOptions("u", new NestedBean() {{ name = "x"; }}, options);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureWithOptions SHALLOW")
        void captureWithOptions_shallow() {
            DirectSnapshotProvider provider = new DirectSnapshotProvider();
            TrackingOptions options = TrackingOptions.shallow();
            Map<String, Object> result = provider.captureWithOptions("u", new MixedBean("a", 1, null, null), options);
            assertThat(result).isNotEmpty();
        }
    }

    // ── FacadeSnapshotProvider ──

    @Nested
    @DisplayName("FacadeSnapshotProvider")
    class FacadeSnapshotProviderTests {

        @Test
        @DisplayName("captureBaseline")
        void captureBaseline() {
            FacadeSnapshotProvider provider = new FacadeSnapshotProvider();
            Map<String, Object> result = provider.captureBaseline("u", new MixedBean("a", 1, null, null), new String[0]);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("captureWithOptions")
        void captureWithOptions() {
            FacadeSnapshotProvider provider = new FacadeSnapshotProvider();
            Map<String, Object> result = provider.captureWithOptions("u", new MixedBean("a", 1, null, null), TrackingOptions.deep());
            assertThat(result).isNotEmpty();
        }
    }

    // ── Test beans ──

    static class PrimitiveBean {
        int intVal = 1;
        long longVal = 2L;
        boolean booleanVal = true;
    }

    static class MixedBean {
        String str;
        int num;
        BigDecimal decimal;
        Date date;

        MixedBean(String str, int num, BigDecimal decimal, Date date) {
            this.str = str;
            this.num = num;
            this.decimal = decimal;
            this.date = date;
        }
    }

    static class CollectionBean {
        List<Integer> list;
        Map<String, String> map;
    }

    enum TestStatus { ACTIVE, INACTIVE }

    static class EnumBean {
        TestStatus status;
    }

    static class NestedBean {
        String name;
        NestedBean inner;
    }

    static class CircularBean {
        String name;
        CircularBean self;
    }
}
