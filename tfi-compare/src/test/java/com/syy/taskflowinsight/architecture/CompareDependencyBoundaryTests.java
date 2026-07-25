package com.syy.taskflowinsight.architecture;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.StrategyResolver;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompareDependencyBoundaryTests {

    /**
     * 这些类型属于已退役的自动控制面；逐个列出可防止后续以新包名或残留 class 文件悄然恢复。
     */
    private static final Set<String> RETIRED_OPERATIONAL_TYPES = Set.of(
            "com.syy.taskflowinsight.metrics.AsyncMetricsCollector",
            "com.syy.taskflowinsight.metrics.AsyncMetricsCollector$CollectorStats",
            "com.syy.taskflowinsight.metrics.MetricsLogger",
            "com.syy.taskflowinsight.metrics.MetricsSummary",
            "com.syy.taskflowinsight.metrics.MetricsSummary$MetricsSummaryBuilder",
            "com.syy.taskflowinsight.metrics.TfiMetrics",
            "com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache",
            "com.syy.taskflowinsight.tracking.cache.StrategyCache",
            "com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationConfig",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationConfig$MemoryThresholds",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationConfig$PerformanceThresholds",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationConfiguration",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationContext",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationLevel",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationLevelChangedEvent",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationManager",
            "com.syy.taskflowinsight.tracking.monitoring.DegradationPerformanceMonitor",
            "com.syy.taskflowinsight.tracking.monitoring.ResourceMonitor",
            "com.syy.taskflowinsight.tracking.monitoring.SystemMetrics",
            "com.syy.taskflowinsight.tracking.monitoring.SystemMetrics$Builder");

    private static final Set<String> FORBIDDEN_IMPORT_PREFIXES = Set.of(
            "import org.springframework",
            "import io.micrometer",
            "import jakarta.",
            "import com.github.benmanes.caffeine",
            "import com.fasterxml.jackson",
            "import org.aspectj");

    @Test
    void should_publish_operations_only_from_api_package() {
        assertThat(CompareOperations.class.getPackageName())
                .isEqualTo("com.syy.taskflowinsight.api");
        assertThat(Arrays.stream(CompareOperations.class.getDeclaredMethods()))
                .extracting(method -> method.getName() + ":" + method.getParameterCount())
                .containsExactlyInAnyOrder("compare:2", "compare:3");
    }

    @Test
    void should_publish_minimal_typed_decorator_identity_contract() {
        assertThat(CompareOperationsDecorator.class.isInterface()).isTrue();
        assertThat(CompareOperationsDecorator.class.getInterfaces())
                .containsExactly(CompareOperations.class);
        assertThat(CompareOperationsDecorator.class.getDeclaredFields()).isEmpty();
        assertThat(CompareOperationsDecorator.class.getDeclaredMethods()).singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("delegate");
                    assertThat(method.getParameterCount()).isZero();
                    assertThat(method.getReturnType()).isEqualTo(CompareOperations.class);
                    assertThat(method.isDefault()).isFalse();
                });
    }

    @Test
    void should_keep_compare_production_sources_free_of_framework_imports() throws Exception {
        Path sourceRoot = repositoryRoot().resolve("tfi-compare/src/main/java");
        List<String> forbiddenImports = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(source)) {
                    String candidate = line.strip();
                    if (FORBIDDEN_IMPORT_PREFIXES.stream().anyMatch(candidate::startsWith)) {
                        forbiddenImports.add(sourceRoot.relativize(source) + ": " + candidate);
                    }
                }
            }
        }

        assertThat(forbiddenImports)
                .as("Compare 生产源码只能依赖 Core、SLF4J、JDK 与 provided Lombok")
                .isEmpty();
    }

    @Test
    void should_keep_only_approved_compare_production_dependencies() throws Exception {
        Path pom = repositoryRoot().resolve("tfi-compare/pom.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
        Element dependencies = directChild(project, "dependencies");
        List<String> productionDependencies = new ArrayList<>();

        for (Element dependency : directChildren(dependencies, "dependency")) {
            String scope = childText(dependency, "scope");
            String effectiveScope = scope.isBlank() ? "compile" : scope;
            if (!effectiveScope.equals("test")) {
                productionDependencies.add(
                        childText(dependency, "groupId") + ":"
                                + childText(dependency, "artifactId") + ":"
                                + effectiveScope);
            }
        }

        assertThat(productionDependencies).containsExactlyInAnyOrder(
                "com.syy:tfi-flow-core:compile",
                "org.slf4j:slf4j-api:compile",
                "org.projectlombok:lombok:provided");
    }

    @Test
    void should_remove_retired_compare_operational_controls() throws Exception {
        ClassLoader loader = CompareDependencyBoundaryTests.class.getClassLoader();
        assertThat(RETIRED_OPERATIONAL_TYPES)
                .allSatisfy(type -> assertThat(loader.getResource(type.replace('.', '/') + ".class"))
                        .as("退役运维类型不得残留在 Compare artifact: %s", type)
                        .isNull());

        assertThat(Modifier.isFinal(CompareEngine.class.getModifiers())).isTrue();
        assertThat(declaredMethodNames(ConcurrentRetryUtil.class))
                .doesNotContain("executeWithRetryOrSummary", "setTfiMetrics");
        assertThat(declaredMethodNames(StrategyResolver.class))
                .doesNotContain("clearCache", "getCacheHitRate", "getCacheSize");
        assertThat(StrategyResolver.class.getConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterCount()).isZero());
        assertThat(declaredMethodNames(EntityKeyUtils.class)).doesNotContain("setReflectionMetaCache");

        assertThat(declaredMethodNames(TrackingOptions.class))
                .doesNotContain("isEnablePerformanceMonitoring");
        assertThat(declaredMethodNames(TrackingOptions.Builder.class))
                .doesNotContain("enablePerformanceMonitoring");
        assertThat(declaredMethodNames(ListCompareExecutor.class))
                .doesNotContain("getDegradationCount");

        assertThat(publicFieldNames(ConfigDefaults.class)).doesNotContain(
                "SLOW_OPERATION_MS",
                "MEMORY_THRESHOLD_SKIP_DEEP",
                "MEMORY_THRESHOLD_SIMPLE",
                "MEMORY_THRESHOLD_SUMMARY",
                "MEMORY_THRESHOLD_DISABLED",
                "CPU_USAGE_THRESHOLD",
                "SLOW_OPERATION_RATE",
                "CRITICAL_OPERATION_TIME_MS",
                "DEGRADATION_ENABLED",
                "METRICS_ENABLED",
                "METRICS_BUFFER_SIZE",
                "METRICS_FLUSH_INTERVAL_SECONDS");
        assertThat(publicFieldNames(ConfigDefaults.Keys.class)).doesNotContain(
                "SLOW_OPERATION_MS",
                "MONITORING_SLOW_OPERATION_MS",
                "METRICS_BUFFER_SIZE",
                "METRICS_FLUSH_INTERVAL_SECONDS");

        String exampleConfig = Files.readString(
                repositoryRoot().resolve("tfi-examples/src/main/resources/application.yml"));
        assertThat(exampleConfig)
                .contains("  compare:\n", "    tracking:\n")
                .doesNotContain(
                        "  change-tracking:\n",
                        "  api:\n    routing:\n",
                        "    degradation:\n",
                        "    concurrency:\n");
    }

    private static List<String> declaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(method -> method.getName()).toList();
    }

    private static List<String> publicFieldNames(Class<?> type) {
        return Arrays.stream(type.getFields()).map(field -> field.getName()).toList();
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream().findFirst().orElseThrow();
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String childText(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(Element::getTextContent)
                .map(String::strip)
                .orElse("");
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("tfi-compare/pom.xml"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return candidate;
    }
}
