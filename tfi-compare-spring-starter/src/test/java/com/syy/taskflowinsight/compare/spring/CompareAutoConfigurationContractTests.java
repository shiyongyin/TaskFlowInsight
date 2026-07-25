package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

class CompareAutoConfigurationContractTests {

    /** 仅保留类型和语义都能无歧义迁移的旧 key。 */
    private static final Map<String, String> EXPECTED_ALIASES = Map.ofEntries(
            Map.entry("tfi.change-tracking.enabled", "tfi.compare.enabled"),
            Map.entry("tfi.change-tracking.snapshot.max-depth", "tfi.compare.max-depth"),
            Map.entry("tfi.change-tracking.snapshot.max-elements", "tfi.compare.max-elements"),
            Map.entry("tfi.change-tracking.snapshot.time-budget-ms", "tfi.compare.deadline"),
            Map.entry("tfi.change-tracking.diff.max-changes-per-object", "tfi.compare.max-change-details"),
            Map.entry("tfi.change-tracking.value-repr-max-length", "tfi.compare.max-result-value-chars"),
            Map.entry("tfi.change-tracking.numeric.float-tolerance", "tfi.compare.numeric-absolute-tolerance"),
            Map.entry("tfi.change-tracking.numeric.relative-tolerance", "tfi.compare.numeric-relative-tolerance"),
            Map.entry("tfi.change-tracking.datetime.tolerance-ms", "tfi.compare.temporal-tolerance"));

    /** 已由前序任务逐项登记的 3.0 metadata key，避免本卡重复声明同一 breaking。 */
    private static final Set<String> PREVIOUSLY_MANIFESTED_KEYS = Set.of(
            "tfi.change-tracking.degradation.max-candidates",
            "tfi.change-tracking.export.pretty-print",
            "tfi.change-tracking.export.show-timestamp",
            "tfi.change-tracking.export.include-sensitive-info",
            "tfi.change-tracking.export.include-metadata");

    /** 无可靠等价语义或已迁移到其他模块的旧 key，本卡按主版本升级直接移除。 */
    private static final Set<String> REMOVED_LEGACY_KEYS = Set.of(
            "tfi.enabled",
            "tfi.context.max-age-millis",
            "tfi.context.leak-detection-enabled",
            "tfi.context.leak-detection-interval-millis",
            "tfi.context.cleanup-enabled",
            "tfi.context.cleanup-interval-millis",
            "tfi.metrics.enabled",
            "tfi.security.enable-data-masking",
            "tfi.annotation.enabled",
            "tfi.change-tracking.cleanup-interval-minutes",
            "tfi.change-tracking.max-cached-classes",
            "tfi.change-tracking.monitoring.slow-operation-ms",
            "tfi.config.enable-env",
            "tfi.config.resolver.enabled",
            "tfi.change-tracking.snapshot.excludes",
            "tfi.change-tracking.snapshot.max-stack-depth",
            "tfi.change-tracking.snapshot.enable-deep",
            "tfi.change-tracking.diff.include-null-changes",
            "tfi.change-tracking.diff.normalize-values",
            "tfi.change-tracking.summary.enabled",
            "tfi.change-tracking.summary.max-size",
            "tfi.change-tracking.summary.max-examples",
            "tfi.change-tracking.summary.sensitive-words",
            "tfi.metrics.tags",
            "tfi.change-tracking.max-tracked-objects",
            "tfi.change-tracking.datetime.default-format",
            "tfi.change-tracking.datetime.timezone",
            "tfi.change-tracking.degradation.enabled",
            "tfi.diff.pathFormat");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TfiCompareAutoConfiguration.class));

    @Test
    void defaultCompositionExportsOneContextLocalRuntimeGraph() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TfiCompareProperties.class);
            assertThat(context).hasSingleBean(ComparePolicy.class);
            assertThat(context).hasSingleBean(CompareRuntime.class);
            assertThat(context).hasSingleBean(CompareEngine.class);
            assertThat(context).hasSingleBean(CompareOperations.class);
            assertThat(context).hasSingleBean(MaskingPolicy.class);

            CompareRuntime runtime = context.getBean(CompareRuntime.class);
            assertThat(context.getBean(ComparePolicy.class)).isSameAs(runtime.policy());
            assertThat(context.getBean(CompareEngine.class)).isSameAs(runtime.engine());
            assertThat(context.getBean(CompareOperations.class)).isSameAs(runtime.engine());
        });
    }

    @Test
    void prerequisiteUsesPackagePrivateConstructionBoundary() {
        Constructor<?>[] constructors =
                TfiCompareTrackingPrerequisiteAutoConfiguration.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        Constructor<?> constructor = constructors[0];
        assertThat(constructor.getParameterCount()).isZero();
        assertThat(Modifier.isPublic(constructor.getModifiers())).isFalse();
        assertThat(Modifier.isProtected(constructor.getModifiers())).isFalse();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isFalse();

        Method[] guards = Arrays.stream(
                        TfiCompareTrackingPrerequisiteAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("tfiCompareTrackingPrerequisiteGuard"))
                .toArray(Method[]::new);
        assertThat(guards).hasSize(1);
        Method guard = guards[0];
        assertThat(Modifier.isPublic(guard.getModifiers())).isTrue();
        assertThat(guard.getParameterCount()).isZero();
        assertThat(guard.getReturnType()).isSameAs(SmartInitializingSingleton.class);
        assertThat(guard.getAnnotation(Bean.class)).isNotNull();
    }

    @Test
    void generatedMetadataPublishesOnlyCanonicalCompareProperties() throws Exception {
        try (InputStream input = TfiCompareProperties.class.getResourceAsStream(
                "/META-INF/spring-configuration-metadata.json")) {
            assertThat(input).as("starter必须发布configuration processor生成的完整metadata").isNotNull();
            JSONObject metadata = new JSONObject(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
            JSONArray properties = metadata.getJSONArray("properties");
            Set<String> names = new HashSet<>();
            for (int index = 0; index < properties.length(); index++) {
                names.add(properties.getJSONObject(index).getString("name"));
            }

            assertThat(names).hasSize(28).allMatch(name -> name.startsWith("tfi.compare."));
            assertThat(names).contains(
                    "tfi.compare.enabled",
                    "tfi.compare.max-depth",
                    "tfi.compare.masking.additional-rules",
                    "tfi.compare.tracking.enabled");
        }
    }

    @Test
    void legacyMetadataKeysAreExhaustivelyPartitionedIntoAliasesAndDirectRemovals() throws Exception {
        Set<String> baselineKeys = baselineMetadataKeys();
        Set<String> accountedKeys = new HashSet<>(EXPECTED_ALIASES.keySet());
        accountedKeys.addAll(PREVIOUSLY_MANIFESTED_KEYS);
        accountedKeys.addAll(REMOVED_LEGACY_KEYS);

        assertThat(baselineKeys).hasSize(43);
        assertThat(TfiComparePropertyAliases.ALIASES)
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_ALIASES);
        assertThat(REMOVED_LEGACY_KEYS)
                .doesNotContainAnyElementsOf(EXPECTED_ALIASES.keySet())
                .doesNotContainAnyElementsOf(PREVIOUSLY_MANIFESTED_KEYS);
        assertThat(accountedKeys).containsExactlyInAnyOrderElementsOf(baselineKeys);
    }

    private static Set<String> baselineMetadataKeys() throws Exception {
        Path baseline = repositoryRoot().resolve(
                ".mvn/api-baseline/repository/com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar");
        try (JarFile jar = new JarFile(baseline.toFile());
             InputStream input = jar.getInputStream(
                     jar.getJarEntry("META-INF/additional-spring-configuration-metadata.json"))) {
            JSONArray properties = new JSONObject(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getJSONArray("properties");
            Set<String> names = new HashSet<>();
            for (int index = 0; index < properties.length(); index++) {
                names.add(properties.getJSONObject(index).getString("name"));
            }
            return Set.copyOf(names);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("tfi-compare"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
