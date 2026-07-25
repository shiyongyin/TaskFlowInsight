package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.context.ContextMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定Ops只发布当前进程能够证明的事实，防止已删除的Compare全局历史以空值接口重新出现。
 */
class CompareTrackingEndpointContractTests {

    @Test
    void opsProductionHasNoLegacyTrackingStoreDependency() throws IOException {
        try (Stream<Path> sources = Files.walk(sourceRoot())) {
            String productionSource = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(CompareTrackingEndpointContractTests::readSource)
                    .reduce("", String::concat);

            assertThat(productionSource)
                    .doesNotContain("SessionAwareChangeTracker")
                    .doesNotContain("ChangeTracker")
                    .doesNotContain("\"sessionCount\"")
                    .doesNotContain("\"totalChanges\"");
        }
    }

    @Test
    void actuatorEndpointsExposeNoWriteOrDeleteOperation() {
        assertThat(Stream.of(TfiEndpoint.class, SecureTfiEndpoint.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(CompareTrackingEndpointContractTests::isMutationOperation))
                .isEmpty();
    }

    @Test
    void advancedEndpointKeepsOnlyOverviewAndStatsRoutes() {
        Set<String> routes = Arrays.stream(TfiAdvancedEndpoint.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> mapping.value().length == 0
                        ? Stream.of("")
                        : Arrays.stream(mapping.value()))
                .collect(Collectors.toUnmodifiableSet());

        assertThat(routes).containsExactlyInAnyOrder("", "/stats");
    }

    @Test
    void statsContainOnlyCanonicalContextFacts() {
        ContextMetrics metrics = new ContextMetrics(
                1, 2L, 3L, 4L, 5L, 6, 7, 8L,
                Instant.parse("2026-07-14T00:00:00Z"));

        Map<String, Object> stats = new TfiStatsAggregator().aggregateStats(metrics);

        assertThat(stats).containsOnlyKeys(
                "activeContexts", "createdContexts", "closedContexts", "detectedLeaks",
                "asyncTasks", "executorPoolSize", "executorQueueSize", "propagations", "capturedAt");
    }

    @Test
    void publishedMetadataHasNoRetiredHistoryOrAccessLogControls() throws IOException {
        String metadata = Files.readString(moduleRoot().resolve(
                "src/main/resources/META-INF/additional-spring-configuration-metadata.json"));

        assertThat(metadata).doesNotContain(
                "tfi.actuator.access-log.",
                "tfi.health.max-sessions-warning",
                "tfi.limits.max-tracked-objects",
                "tfi.limits.max-changes",
                "tfi.limits.max-value-length");
    }

    @Test
    void retiredSessionHelpersAndUnusedEndpointDependenciesAreAbsent() throws IOException {
        Path moduleRoot = moduleRoot();
        assertThat(moduleRoot.resolve(
                "src/main/java/com/syy/taskflowinsight/actuator/support/SessionIdMasker.java"))
                .doesNotExist();
        assertThat(moduleRoot.resolve(
                "src/main/java/com/syy/taskflowinsight/actuator/support/EndpointAccessLog.java"))
                .doesNotExist();

        String endpoint = Files.readString(moduleRoot.resolve(
                "src/main/java/com/syy/taskflowinsight/actuator/SecureTfiEndpoint.java"));
        assertThat(endpoint).doesNotContain(
                "MeterRegistry",
                "EndpointAccessLog",
                "accessLogs",
                "recordAccess(",
                "getRecentAccessCount(");
    }

    private static boolean isMutationOperation(Method method) {
        return method.isAnnotationPresent(WriteOperation.class)
                || method.isAnnotationPresent(DeleteOperation.class);
    }

    private static Path sourceRoot() {
        return moduleRoot().resolve("src/main/java");
    }

    private static Path moduleRoot() {
        return Files.isDirectory(Path.of("src/main/java"))
                ? Path.of("")
                : Path.of("tfi-ops-spring");
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取Ops生产源码: " + path, exception);
        }
    }
}
