package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextMetrics} 公共快照契约测试。
 */
class ContextMetricsTests {

    private SafeContextManager manager;

    @BeforeEach
    void setUp() {
        manager = SafeContextManager.getInstance();
        manager.clearAllContextsForTesting();
        manager.apply(ContextManagerConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        manager.clearAllContextsForTesting();
        manager.apply(ContextManagerConfig.defaults());
    }

    @Test
    void exposesExactRecordComponents() {
        assertThat(Arrays.stream(ContextMetrics.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(
                        "activeContexts",
                        "createdContexts",
                        "closedContexts",
                        "detectedLeaks",
                        "asyncTasks",
                        "executorPoolSize",
                        "executorQueueSize",
                        "propagations",
                        "capturedAt");
        assertThat(Arrays.stream(ContextMetrics.class.getRecordComponents())
                .map(RecordComponent::getType))
                .containsExactly(
                        int.class,
                        long.class,
                        long.class,
                        long.class,
                        long.class,
                        int.class,
                        int.class,
                        long.class,
                        Instant.class);
    }

    @Test
    void capturesOneRegistryLifecycleAndObservationBoundary() {
        ContextMetrics before = manager.metrics();
        Instant captureStarted = Instant.now();

        ManagedThreadContext context = ManagedThreadContext.create("metrics-lifecycle");
        ContextMetrics active = manager.metrics();

        assertThat(active.activeContexts()).isEqualTo(before.activeContexts() + 1);
        assertThat(active.createdContexts()).isEqualTo(before.createdContexts() + 1);
        assertThat(active.closedContexts()).isEqualTo(before.closedContexts());
        assertThat(active.capturedAt()).isBetween(captureStarted, Instant.now());

        context.close();
        ContextMetrics closed = manager.metrics();
        assertThat(closed.activeContexts()).isEqualTo(before.activeContexts());
        assertThat(closed.createdContexts()).isEqualTo(before.createdContexts() + 1);
        assertThat(closed.closedContexts()).isEqualTo(before.closedContexts() + 1);
    }

    @Test
    void successfulPropagationIncrementsManagerMetric() {
        ContextMetrics before = manager.metrics();
        ManagedThreadContext source = ManagedThreadContext.create("metrics-propagation");
        ContextSnapshot snapshot = source.createSnapshot();

        ManagedThreadContext restored = ThreadContext.propagate(snapshot);

        assertThat(restored).isNotNull();
        assertThat(manager.metrics().propagations()).isEqualTo(before.propagations() + 1);
        ThreadContext.clear();
    }

    @Test
    void asyncSubmissionIncrementsMetricExactlyOnce() throws Exception {
        ContextMetrics before = manager.metrics();

        CompletableFuture<String> future = manager.executeAsync("metrics-async", () -> "done");

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        ContextMetrics after = manager.metrics();
        assertThat(after.asyncTasks()).isEqualTo(before.asyncTasks() + 1);
        assertThat(manager.getAsyncTaskCount()).isEqualTo(after.asyncTasks());
    }

    @Test
    void metricsSourcesUseOneSnapshotOwnerWithoutReadSideEffects() throws Exception {
        String managerSource = Files.readString(sourcePath("SafeContextManager.java"));
        String managerMethod = methodSource(managerSource, "public ContextMetrics metrics()");
        assertThat(managerMethod).containsOnlyOnce("registryState.counts()");

        String facadeSource = Files.readString(sourcePath("ThreadContext.java"));
        String statisticsMethod = methodSource(
                facadeSource, "public static ContextStatistics getStatistics()");
        assertThat(statisticsMethod)
                .containsOnlyOnce("SafeContextManager.getInstance().metrics()")
                .contains("metrics.capturedAt()")
                .doesNotContain("detectPotentialLeaks()", "getActiveContextCount()",
                        "getTotalContextsCreated()", "getTotalPropagations()");
        assertThat(facadeSource).doesNotContain("TOTAL_PROPAGATIONS");
    }

    @Test
    void doesNotExposeRemovedMapMetricsMethod() {
        assertThat(Arrays.stream(SafeContextManager.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName()))
                .doesNotContain("getMetrics");
    }

    private static Path sourcePath(String fileName) {
        Path modulePath = Path.of("src/main/java/com/syy/taskflowinsight/context", fileName);
        return Files.isRegularFile(modulePath)
                ? modulePath
                : Path.of("tfi-flow-core/src/main/java/com/syy/taskflowinsight/context", fileName);
    }

    private static String methodSource(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as("method signature %s", signature).isGreaterThanOrEqualTo(0);
        int end = source.indexOf("\n    }", start);
        assertThat(end).as("method end %s", signature).isGreaterThan(start);
        return source.substring(start, end);
    }
}
