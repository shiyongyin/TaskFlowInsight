package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * API Surface Compatibility Tests
 *
 * <p>Verifies that the public API surface of the tfi-compare module
 * remains backward-compatible. This serves as a lightweight alternative
 * to japicmp when no published baseline artifact is available.</p>
 *
 * <h3>Purpose</h3>
 * <ul>
 *   <li>Detect accidental public method removal</li>
 *   <li>Verify core class accessibility</li>
 *   <li>Ensure SPI interface contracts are intact</li>
 *   <li>Guard annotation availability</li>
 * </ul>
 *
 * <h3>Maintenance</h3>
 * <p>When adding new public API methods, add corresponding assertions here.
 * When intentionally removing API, update these tests as part of the breaking change.</p>
 *
 * @author Test Expert Panel
 * @since v3.0.0
 */
@DisplayName("API Surface Compatibility Tests")
class ApiSurfaceCompatibilityTests {

    // ========== Core API Classes Exist ==========

    @Nested
    @DisplayName("Core API classes are accessible")
    class CoreClassesExist {

        @Test
        @DisplayName("TfiListDiff is public and accessible")
        void tfiListDiff() {
            assertThat(TfiListDiff.class).isPublic();
        }

        @Test
        @DisplayName("TfiListDiffFacade is public and accessible")
        void tfiListDiffFacade() {
            assertThat(TfiListDiffFacade.class).isPublic();
        }

        @Test
        @DisplayName("ComparatorBuilder is public and accessible")
        void comparatorBuilder() {
            assertThat(ComparatorBuilder.class).isPublic();
        }

        @Test
        @DisplayName("DiffBuilder is public and accessible")
        void diffBuilder() {
            assertThat(DiffBuilder.class).isPublic();
        }

        @Test
        @DisplayName("TfiContext is public and accessible")
        void tfiContext() {
            assertThat(TfiContext.class).isPublic();
        }

        @Test
        @DisplayName("TrackingOptions is public and accessible")
        void trackingOptions() {
            assertThat(TrackingOptions.class).isPublic();
        }

        @Test
        @DisplayName("TrackingStatistics is public and accessible")
        void trackingStatistics() {
            assertThat(TrackingStatistics.class).isPublic();
        }
    }

    // ========== SPI Interfaces ==========

    @Nested
    @DisplayName("SPI interfaces define expected methods")
    class SpiInterfaces {

        @Test
        @DisplayName("ComparisonProvider has compare methods")
        void comparisonProvider() {
            assertPublicMethodExists(ComparisonProvider.class, "compare", Object.class, Object.class);
            assertPublicMethodExists(ComparisonProvider.class, "compare", Object.class, Object.class, CompareOptions.class);
            assertPublicMethodExists(ComparisonProvider.class, "similarity", Object.class, Object.class);
            assertPublicMethodExists(ComparisonProvider.class, "priority");
        }

        @Test
        @DisplayName("TrackingProvider has required methods")
        void trackingProvider() {
            assertThat(TrackingProvider.class).isInterface();
            assertThat(TrackingProvider.class.getMethods()).isNotEmpty();
        }

        @Test
        @DisplayName("RenderProvider has required methods")
        void renderProvider() {
            assertThat(RenderProvider.class).isInterface();
            assertThat(RenderProvider.class.getMethods()).isNotEmpty();
        }
    }

    // ========== CompareResult API Surface ==========

    @Nested
    @DisplayName("CompareResult maintains expected API")
    class CompareResultApi {

        @Test
        @DisplayName("Factory methods exist")
        void factoryMethods() {
            assertPublicStaticMethodExists(CompareResult.class, "identical");
            assertPublicStaticMethodExists(CompareResult.class, "ofNullDiff", Object.class, Object.class);
            assertPublicStaticMethodExists(CompareResult.class, "ofTypeDiff", Object.class, Object.class);
            assertPublicStaticMethodExists(CompareResult.class, "builder");
        }

        @Test
        @DisplayName("Query methods exist (v3.1.0-P1)")
        void queryMethods() {
            assertPublicMethodExists(CompareResult.class, "getChangeCount");
            assertPublicMethodExists(CompareResult.class, "hasChanges");
            assertPublicMethodExists(CompareResult.class, "getSimilarityPercent");
            assertPublicMethodExists(CompareResult.class, "hasDuplicateKeys");
            assertPublicMethodExists(CompareResult.class, "prettyPrint");
            assertPublicMethodExists(CompareResult.class, "groupByObject");
            assertPublicMethodExists(CompareResult.class, "groupByProperty");
            assertPublicMethodExists(CompareResult.class, "groupByContainerOperation");
            assertPublicMethodExists(CompareResult.class, "groupByContainerOperationTyped");
            assertPublicMethodExists(CompareResult.class, "getReferenceChanges");
            assertPublicMethodExists(CompareResult.class, "getContainerChanges");
            assertPublicMethodExists(CompareResult.class, "getChangeCountByType");
        }
    }

    // ========== FieldChange API Surface ==========

    @Nested
    @DisplayName("FieldChange maintains expected API")
    class FieldChangeApi {

        @Test
        @DisplayName("Container event methods exist (v3.1.0-P1)")
        void containerEventMethods() {
            assertPublicMethodExists(FieldChange.class, "isContainerElementChange");
            assertPublicMethodExists(FieldChange.class, "getContainerIndex");
            assertPublicMethodExists(FieldChange.class, "getEntityKey");
            assertPublicMethodExists(FieldChange.class, "getMapKey");
            assertPublicMethodExists(FieldChange.class, "getContainerMapKey");
            assertPublicMethodExists(FieldChange.class, "getContainerOperation");
            assertPublicMethodExists(FieldChange.class, "toTypedView");
        }

        @Test
        @DisplayName("Reference change methods exist (v3.1.0-P1)")
        void referenceChangeMethods() {
            assertPublicMethodExists(FieldChange.class, "isReferenceChange");
            assertPublicMethodExists(FieldChange.class, "getReferenceDetail");
            assertPublicMethodExists(FieldChange.class, "getOldEntityKey");
            assertPublicMethodExists(FieldChange.class, "getNewEntityKey");
            assertPublicMethodExists(FieldChange.class, "toReferenceChangeView");
        }

        @Test
        @DisplayName("Value description methods exist")
        void valueDescMethods() {
            assertPublicMethodExists(FieldChange.class, "getValueDescription");
            assertPublicMethodExists(FieldChange.class, "isNullChange");
        }

        @Test
        @DisplayName("Enums ContainerType and ElementOperation exist")
        void enums() {
            assertThat(FieldChange.ContainerType.values()).hasSize(4);
            assertThat(FieldChange.ElementOperation.values()).hasSize(4);
        }
    }

    // ========== ComparatorBuilder API Surface ==========

    @Nested
    @DisplayName("ComparatorBuilder maintains expected fluent API")
    class ComparatorBuilderApi {

        @Test
        @DisplayName("Fluent builder methods exist")
        void fluentMethods() {
            assertPublicMethodExists(ComparatorBuilder.class, "ignoring", String[].class);
            assertPublicMethodExists(ComparatorBuilder.class, "exclude", String[].class);
            assertPublicMethodExists(ComparatorBuilder.class, "withMaxDepth", int.class);
            assertPublicMethodExists(ComparatorBuilder.class, "withSimilarity");
            assertPublicMethodExists(ComparatorBuilder.class, "withReport");
            assertPublicMethodExists(ComparatorBuilder.class, "detectMoves");
            assertPublicMethodExists(ComparatorBuilder.class, "typeAware");
            assertPublicMethodExists(ComparatorBuilder.class, "includeNulls");
            assertPublicMethodExists(ComparatorBuilder.class, "compare", Object.class, Object.class);
        }
    }

    // ========== Annotations Exist ==========

    @Nested
    @DisplayName("Annotations are accessible")
    class AnnotationsExist {

        @Test
        @DisplayName("Core annotations exist")
        void coreAnnotations() throws ClassNotFoundException {
            Class.forName("com.syy.taskflowinsight.annotation.Entity");
            Class.forName("com.syy.taskflowinsight.annotation.ValueObject");
            Class.forName("com.syy.taskflowinsight.annotation.Key");
            Class.forName("com.syy.taskflowinsight.annotation.DiffIgnore");
            Class.forName("com.syy.taskflowinsight.annotation.DiffInclude");
            Class.forName("com.syy.taskflowinsight.annotation.NumericPrecision");
            Class.forName("com.syy.taskflowinsight.annotation.DateFormat");
            Class.forName("com.syy.taskflowinsight.annotation.ShallowReference");
        }
    }

    // ========== Model Classes ==========

    @Nested
    @DisplayName("Model classes are accessible")
    class ModelClasses {

        @Test
        @DisplayName("ChangeRecord has factory method and builder")
        void changeRecord() {
            assertPublicStaticMethodExists(ChangeRecord.class, "of",
                String.class, String.class, Object.class, Object.class,
                com.syy.taskflowinsight.tracking.ChangeType.class);
            assertPublicStaticMethodExists(ChangeRecord.class, "builder");
        }

        @Test
        @DisplayName("CompareOptions has DEFAULT and builder")
        void compareOptions() {
            assertThat(CompareOptions.DEFAULT).isNotNull();
            assertPublicStaticMethodExists(CompareOptions.class, "builder");
        }
    }

    // ========== Public Method Count Stability ==========

    @Nested
    @DisplayName("Public method count stability")
    class MethodCountStability {

        @Test
        @DisplayName("CompareResult has >= 20 public methods (growth only)")
        void compareResult_minMethods() {
            long publicMethods = countPublicDeclaredMethods(CompareResult.class);
            assertThat(publicMethods)
                .as("CompareResult public method count should only grow")
                .isGreaterThanOrEqualTo(20);
        }

        @Test
        @DisplayName("FieldChange has >= 15 public methods (growth only)")
        void fieldChange_minMethods() {
            long publicMethods = countPublicDeclaredMethods(FieldChange.class);
            assertThat(publicMethods)
                .as("FieldChange public method count should only grow")
                .isGreaterThanOrEqualTo(15);
        }
    }

    // ========== Helpers ==========

    private void assertPublicMethodExists(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getMethod(name, params);
            assertThat(Modifier.isPublic(m.getModifiers()))
                .as("Method %s.%s should be public", clazz.getSimpleName(), name)
                .isTrue();
        } catch (NoSuchMethodException e) {
            fail("Expected public method %s.%s(%s) not found",
                clazz.getSimpleName(), name,
                Arrays.stream(params).map(Class::getSimpleName).collect(Collectors.joining(", ")));
        }
    }

    private void assertPublicStaticMethodExists(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getMethod(name, params);
            assertThat(Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                .as("Method %s.%s should be public static", clazz.getSimpleName(), name)
                .isTrue();
        } catch (NoSuchMethodException e) {
            fail("Expected public static method %s.%s(%s) not found",
                clazz.getSimpleName(), name,
                Arrays.stream(params).map(Class::getSimpleName).collect(Collectors.joining(", ")));
        }
    }

    private long countPublicDeclaredMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .count();
    }
}
