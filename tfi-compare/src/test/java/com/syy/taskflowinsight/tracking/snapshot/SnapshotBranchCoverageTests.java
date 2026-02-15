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
 * Branch coverage tests for tracking/snapshot package.
 * Targets ObjectSnapshotDeep ALL Java types, SnapshotConfig ALL properties,
 * collection strategies, primitive arrays, format edge cases.
 *
 * @since 3.0.0
 */
@DisplayName("Snapshot Branch Coverage — 快照分支覆盖测试")
class SnapshotBranchCoverageTests {

    private SnapshotConfig config;

    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        config.setMaxDepth(5);
        config.setCollectionSummaryThreshold(10);
        config.setTimeBudgetMs(5000);
    }

    @AfterEach
    void tearDown() {
        ObjectSnapshot.clearCaches();
        ObjectSnapshotDeep.resetMetrics();
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — All primitive types")
    class PrimitiveTypes {

        @Test
        @DisplayName("byte primitive")
        void captureDeep_byte() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            ByteBean bean = new ByteBean();
            bean.b = (byte) 42;
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("b");
        }

        @Test
        @DisplayName("short primitive")
        void captureDeep_short() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            ShortBean bean = new ShortBean();
            bean.s = (short) 100;
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("s");
        }

        @Test
        @DisplayName("char primitive")
        void captureDeep_char() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            CharBean bean = new CharBean();
            bean.c = 'X';
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("c");
        }

        @Test
        @DisplayName("float primitive")
        void captureDeep_float() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            FloatBean bean = new FloatBean();
            bean.f = 1.5f;
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("f");
        }

        @Test
        @DisplayName("double primitive")
        void captureDeep_double() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            DoubleBean bean = new DoubleBean();
            bean.d = 3.14;
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("d");
        }

        @Test
        @DisplayName("long primitive")
        void captureDeep_long() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            LongBean bean = new LongBean();
            bean.l = 999L;
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("l");
        }

        @Test
        @DisplayName("boolean primitive")
        void captureDeep_boolean() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            BooleanBean bean = new BooleanBean();
            bean.b = true;
            Map<String, Object> result = deep.captureDeep(bean, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("b");
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — Primitive arrays")
    class PrimitiveArrays {

        @Test
        @DisplayName("byte array")
        void captureDeep_byteArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            byte[] arr = new byte[]{1, 2, 3};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("short array")
        void captureDeep_shortArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            short[] arr = new short[]{1, 2};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("char array")
        void captureDeep_charArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            char[] arr = new char[]{'a', 'b'};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("float array")
        void captureDeep_floatArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            float[] arr = new float[]{1.0f, 2.0f};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("double array")
        void captureDeep_doubleArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            double[] arr = new double[]{1.0, 2.0};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("long array")
        void captureDeep_longArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            long[] arr = new long[]{1L, 2L};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("boolean array")
        void captureDeep_booleanArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            boolean[] arr = new boolean[]{true, false};
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("empty primitive array")
        void captureDeep_emptyPrimitiveArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = new int[0];
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("large primitive array (>10 elements)")
        void captureDeep_largePrimitiveArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = new int[15];
            for (int i = 0; i < 15; i++) arr[i] = i;
            Map<String, Object> result = deep.captureDeep(arr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — Collection strategies")
    class CollectionStrategies {

        @Test
        @DisplayName("IGNORE strategy")
        void captureDeep_ignoreStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.IGNORE);
            List<Object> list = List.of(1, 2, 3);
            Map<String, Object> result = deep.captureDeep(list, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
            assertThat(result.values()).anyMatch(v -> v.toString().contains("List"));
        }

        @Test
        @DisplayName("SUMMARY strategy with large collection")
        void captureDeep_summaryStrategy_large() {
            config.setCollectionSummaryThreshold(5);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 20; i++) list.add(i);
            Map<String, Object> result = deep.captureDeep(list, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("ELEMENT strategy")
        void captureDeep_elementStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT);
            List<Object> list = List.of("a", "b");
            Map<String, Object> result = deep.captureDeep(list, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).containsKey("[0]").containsKey("[1]");
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — Map and array edge cases")
    class MapAndArrayEdges {

        @Test
        @DisplayName("large Map uses summary")
        void captureDeep_largeMap() {
            config.setCollectionSummaryThreshold(5);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < 20; i++) map.put("k" + i, i);
            Map<String, Object> result = deep.captureDeep(map, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("large object array truncated")
        void captureDeep_largeObjectArray() {
            config.setCollectionSummaryThreshold(5);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Object[] arr = new Object[150];
            for (int i = 0; i < 150; i++) arr[i] = "item" + i;
            Map<String, Object> result = deep.captureDeep(arr, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Map with special key characters")
        void captureDeep_mapSpecialKeys() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, String> map = new HashMap<>();
            map.put("key.with.dots", "v1");
            map.put("key\"quote", "v2");
            Map<String, Object> result = deep.captureDeep(map, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — Long string truncation")
    class LongStringTruncation {

        @Test
        @DisplayName("string > 1000 chars truncated")
        void captureDeep_longString() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            String longStr = "x".repeat(1500);
            Map<String, Object> result = deep.captureDeep(longStr, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(result).isNotEmpty();
            Object val = result.get("");
            assertThat(val).isInstanceOf(String.class);
            assertThat((String) val).contains("truncated");
        }
    }

    @Nested
    @DisplayName("SnapshotConfig — All properties")
    class SnapshotConfigAllProperties {

        @Test
        @DisplayName("enableDeep getter setter")
        void enableDeep() {
            SnapshotConfig c = new SnapshotConfig();
            c.setEnableDeep(true);
            assertThat(c.isEnableDeep()).isTrue();
        }

        @Test
        @DisplayName("maxDepth")
        void maxDepth() {
            SnapshotConfig c = new SnapshotConfig();
            c.setMaxDepth(8);
            assertThat(c.getMaxDepth()).isEqualTo(8);
        }

        @Test
        @DisplayName("maxStackDepth")
        void maxStackDepth() {
            SnapshotConfig c = new SnapshotConfig();
            c.setMaxStackDepth(500);
            assertThat(c.getMaxStackDepth()).isEqualTo(500);
        }

        @Test
        @DisplayName("timeBudgetMs")
        void timeBudgetMs() {
            SnapshotConfig c = new SnapshotConfig();
            c.setTimeBudgetMs(200);
            assertThat(c.getTimeBudgetMs()).isEqualTo(200);
        }

        @Test
        @DisplayName("collectionSummaryThreshold")
        void collectionSummaryThreshold() {
            SnapshotConfig c = new SnapshotConfig();
            c.setCollectionSummaryThreshold(50);
            assertThat(c.getCollectionSummaryThreshold()).isEqualTo(50);
        }

        @Test
        @DisplayName("metricsEnabled")
        void metricsEnabled() {
            SnapshotConfig c = new SnapshotConfig();
            c.setMetricsEnabled(false);
            assertThat(c.isMetricsEnabled()).isFalse();
        }

        @Test
        @DisplayName("shallowReferenceMode COMPOSITE_STRING")
        void shallowReferenceMode_compositeString() {
            SnapshotConfig c = new SnapshotConfig();
            c.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
            assertThat(c.getShallowReferenceMode()).isEqualTo(ShallowReferenceMode.COMPOSITE_STRING);
        }

        @Test
        @DisplayName("shallowReferenceMode COMPOSITE_MAP")
        void shallowReferenceMode_compositeMap() {
            SnapshotConfig c = new SnapshotConfig();
            c.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
            assertThat(c.getShallowReferenceMode()).isEqualTo(ShallowReferenceMode.COMPOSITE_MAP);
        }

        @Test
        @DisplayName("excludePackages")
        void excludePackages() {
            SnapshotConfig c = new SnapshotConfig();
            c.setExcludePackages(List.of("com.internal"));
            assertThat(c.getExcludePackages()).contains("com.internal");
        }

        @Test
        @DisplayName("regexExcludes")
        void regexExcludes() {
            SnapshotConfig c = new SnapshotConfig();
            c.setRegexExcludes(List.of("^debug.*"));
            assertThat(c.getRegexExcludeSet()).contains("^debug.*");
        }

        @Test
        @DisplayName("defaultExclusionsEnabled")
        void defaultExclusionsEnabled() {
            SnapshotConfig c = new SnapshotConfig();
            c.setDefaultExclusionsEnabled(false);
            assertThat(c.isDefaultExclusionsEnabled()).isFalse();
        }

        @Test
        @DisplayName("shouldExclude with wildcard pattern")
        void shouldExclude_wildcard() {
            SnapshotConfig c = new SnapshotConfig();
            c.setIncludePatterns(Collections.emptyList());
            c.setExcludePatterns(List.of("*"));
            assertThat(c.shouldExclude("any.path")).isTrue();
        }

        @Test
        @DisplayName("shouldExclude with *.field pattern")
        void shouldExclude_starField() {
            SnapshotConfig c = new SnapshotConfig();
            c.setIncludePatterns(Collections.emptyList());
            c.setExcludePatterns(List.of("*.password"));
            assertThat(c.shouldExclude("user.password")).isTrue();
        }
    }

    @Nested
    @DisplayName("ShallowReferenceMode")
    class ShallowReferenceModeTests {

        @Test
        @DisplayName("fromString null returns VALUE_ONLY")
        void fromString_null() {
            assertThat(ShallowReferenceMode.fromString(null)).isEqualTo(ShallowReferenceMode.VALUE_ONLY);
        }

        @Test
        @DisplayName("fromString empty returns VALUE_ONLY")
        void fromString_empty() {
            assertThat(ShallowReferenceMode.fromString("")).isEqualTo(ShallowReferenceMode.VALUE_ONLY);
        }

        @Test
        @DisplayName("fromString invalid returns VALUE_ONLY")
        void fromString_invalid() {
            assertThat(ShallowReferenceMode.fromString("INVALID")).isEqualTo(ShallowReferenceMode.VALUE_ONLY);
        }

        @Test
        @DisplayName("fromString composite_string")
        void fromString_compositeString() {
            assertThat(ShallowReferenceMode.fromString("composite_string")).isEqualTo(ShallowReferenceMode.COMPOSITE_STRING);
        }

        @Test
        @DisplayName("requiresKeyExtraction VALUE_ONLY false")
        void requiresKeyExtraction_valueOnly() {
            assertThat(ShallowReferenceMode.VALUE_ONLY.requiresKeyExtraction()).isFalse();
        }

        @Test
        @DisplayName("requiresKeyExtraction COMPOSITE_STRING true")
        void requiresKeyExtraction_compositeString() {
            assertThat(ShallowReferenceMode.COMPOSITE_STRING.requiresKeyExtraction()).isTrue();
        }

        @Test
        @DisplayName("getDescription")
        void getDescription() {
            assertThat(ShallowReferenceMode.VALUE_ONLY.getDescription()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — captureDeep with TrackingOptions null")
    class TrackingOptionsNull {

        @Test
        @DisplayName("captureDeep options null uses defaults")
        void captureDeep_optionsNull() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> result = deep.captureDeep("x", (TrackingOptions) null);
            assertThat(result).isNotEmpty();
        }
    }

    // Test beans
    static class ByteBean { byte b; }
    static class ShortBean { short s; }
    static class CharBean { char c; }
    static class FloatBean { float f; }
    static class DoubleBean { double d; }
    static class LongBean { long l; }
    static class BooleanBean { boolean b; }
}
