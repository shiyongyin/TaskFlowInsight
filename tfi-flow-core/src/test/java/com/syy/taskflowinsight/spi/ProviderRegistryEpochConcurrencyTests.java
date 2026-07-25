package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.syy.taskflowinsight.spi.fixtures.BlockingFlowProvider;
import com.syy.taskflowinsight.spi.fixtures.ProviderCapacityTestTypes;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Provider scan publication 的 epoch 与 bounded-retry 并发契约。 */
class ProviderRegistryEpochConcurrencyTests {

    @BeforeEach
    void setUp() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.spi.allowedProviders");
        BlockingFlowProvider.reset();
    }

    @AfterEach
    void tearDown() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.spi.allowedProviders");
        BlockingFlowProvider.reset();
    }

    @Test
    void clearAllDuringExplicitScanCannotPublishOldEpochProvider() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (URLClassLoader loader = resourceLoader("provider-loader-blocking")) {
            Future<?> load = executor.submit(() ->
                ProviderRegistry.loadProviders(loader, FlowProvider.class));
            try {
                int firstId = BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
                long blockedEpoch = registryEpoch();

                Future<?> reset = executor.submit(ProviderRegistry::clearAll);
                reset.get(1, TimeUnit.SECONDS);
                assertThat(registryEpoch()).isEqualTo(blockedEpoch + 1);

                BlockingFlowProvider.allowNextConstruction();
                int secondId = BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
                assertThat(secondId).isGreaterThan(firstId);
                BlockingFlowProvider.allowNextConstruction();
                load.get(5, TimeUnit.SECONDS);

                assertThat(ProviderRegistry.lookup(FlowProvider.class))
                    .isInstanceOfSatisfying(BlockingFlowProvider.class, provider -> {
                        assertThat(provider).isNotSameAs(BlockingFlowProvider.firstInstance());
                        assertThat(provider.instanceId()).isEqualTo(secondId);
                    });
                assertThat(cacheEpochs("serviceLoaderCache")).containsOnly(registryEpoch());
                assertThat(cachedProviders()).doesNotContain(BlockingFlowProvider.firstInstance());
                assertThat(cacheEpochs("resolvedProviders")).containsOnly(registryEpoch());
                assertThat(selectedProviders()).doesNotContain(BlockingFlowProvider.firstInstance());
            } finally {
                releaseConstructionPermits();
                load.cancel(true);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void repeatedClearAllExhaustsThePublicationBudgetWithoutPublishingStaleCandidates()
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (URLClassLoader loader = resourceLoader("provider-loader-blocking")) {
            Future<?> load = executor.submit(() ->
                ProviderRegistry.loadProviders(loader, FlowProvider.class));
            try {
                List<Integer> attempts = new ArrayList<>();
                for (int attempt = 1; attempt <= 3; attempt++) {
                    attempts.add(BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5)));
                    Future<?> reset = executor.submit(ProviderRegistry::clearAll);
                    reset.get(1, TimeUnit.SECONDS);
                    BlockingFlowProvider.allowNextConstruction();
                }

                ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> load.get(5, TimeUnit.SECONDS));
                assertThat(failure.getCause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Provider registry changed during provider scan after 3 attempts");
                assertThat(attempts).containsExactly(1, 2, 3);
                assertThatIllegalStateException()
                    .isThrownBy(() -> BlockingFlowProvider.awaitConstructionStarted(
                        Duration.ofMillis(200)))
                    .withMessage("Timed out waiting for Provider construction");
                assertThat(cachedProviders()).doesNotContain(BlockingFlowProvider.firstInstance());
                assertThat(engineMap("resolvedProviders")).isEmpty();
            } finally {
                releaseConstructionPermits();
                load.cancel(true);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void resolveSharesOneThreeAttemptBudgetWithCandidateScanning() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ProviderCapacityTestTypes.RaceA> lookup = executor.submit(() ->
            ProviderRegistry.lookup(ProviderCapacityTestTypes.RaceA.class));
        try {
            List<Integer> attempts = new ArrayList<>();
            for (int attempt = 1; attempt <= 3; attempt++) {
                attempts.add(BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5)));
                Future<?> reset = executor.submit(ProviderRegistry::clearAll);
                reset.get(1, TimeUnit.SECONDS);
                BlockingFlowProvider.allowNextConstruction();
            }

            assertThatIllegalStateException()
                .isThrownBy(() -> BlockingFlowProvider.awaitConstructionStarted(
                    Duration.ofMillis(200)))
                .withMessage("Timed out waiting for Provider construction");
            ExecutionException failure = assertThrows(ExecutionException.class,
                () -> lookup.get(5, TimeUnit.SECONDS));
            assertThat(failure.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                    "Provider registry changed during provider resolution after 3 attempts");
            assertThat(attempts).containsExactly(1, 2, 3);
            assertThat(cachedProviders()).isEmpty();
            assertThat(engineMap("resolvedProviders")).isEmpty();
        } finally {
            releaseConstructionPermits();
            lookup.cancel(true);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentNewLookupTypesCompeteForLastSlotAtomically() throws Exception {
        reserveTypeSlots(63);
        long generationBefore = ProviderRegistry.getGeneration();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ProviderCapacityTestTypes.RaceA> raceA = executor.submit(() ->
            ProviderRegistry.lookup(ProviderCapacityTestTypes.RaceA.class));
        Future<ProviderCapacityTestTypes.RaceB> raceB = executor.submit(() ->
            ProviderRegistry.lookup(ProviderCapacityTestTypes.RaceB.class));
        try {
            BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
            BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
            BlockingFlowProvider.allowNextConstruction();
            BlockingFlowProvider.allowNextConstruction();

            LookupOutcome outcomeA = outcome(raceA);
            LookupOutcome outcomeB = outcome(raceB);
            assertThat(List.of(outcomeA, outcomeB).stream()
                .filter(result -> result.provider() instanceof BlockingFlowProvider))
                .hasSize(1);
            assertThat(List.of(outcomeA, outcomeB).stream()
                .map(LookupOutcome::failure)
                .filter(IllegalStateException.class::isInstance)
                .map(Throwable::getMessage))
                .containsExactly("Provider registry supports at most 64 provider types per epoch");

            Class<?> committedType = outcomeA.provider() != null
                ? ProviderCapacityTestTypes.RaceA.class
                : ProviderCapacityTestTypes.RaceB.class;
            Class<?> rejectedType = outcomeA.provider() != null
                ? ProviderCapacityTestTypes.RaceB.class
                : ProviderCapacityTestTypes.RaceA.class;
            assertThat(knownProviderTypeCount()).isEqualTo(64);
            assertThat(cacheProviderTypes()).contains(committedType).doesNotContain(rejectedType);
            assertThat(resolutionProviderTypes()).contains(committedType).doesNotContain(rejectedType);
            assertThat(selectedProviders()).singleElement()
                .isInstanceOf(BlockingFlowProvider.class);
            assertThat(engineMap("effectiveLoaders").containsKey(
                ProviderCapacityTestTypes.RaceA.class)).isFalse();
            assertThat(engineMap("effectiveLoaders").containsKey(
                ProviderCapacityTestTypes.RaceB.class)).isFalse();
            assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
        } finally {
            releaseConstructionPermits();
            raceA.cancel(true);
            raceB.cancel(true);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentLookupsForSameNewTypeReuseCommittedScan() throws Exception {
        reserveTypeSlots(63);
        long generationBefore = ProviderRegistry.getGeneration();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ProviderCapacityTestTypes.RaceA> first = executor.submit(() ->
            ProviderRegistry.lookup(ProviderCapacityTestTypes.RaceA.class));
        Future<ProviderCapacityTestTypes.RaceA> second = executor.submit(() ->
            ProviderRegistry.lookup(ProviderCapacityTestTypes.RaceA.class));
        try {
            BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
            BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
            BlockingFlowProvider.allowNextConstruction();
            BlockingFlowProvider.allowNextConstruction();

            ProviderCapacityTestTypes.RaceA firstResult = first.get(5, TimeUnit.SECONDS);
            ProviderCapacityTestTypes.RaceA secondResult = second.get(5, TimeUnit.SECONDS);
            assertThat(firstResult).isSameAs(secondResult).isInstanceOf(BlockingFlowProvider.class);
            assertThat(knownProviderTypeCount()).isEqualTo(64);
            assertThat(cacheProviderTypes().stream()
                .filter(ProviderCapacityTestTypes.RaceA.class::equals))
                .hasSize(1);
            assertThat(cachedProviders()).containsExactly(firstResult);
            assertThat(engineMap("resolvedProviders")).hasSize(1);
            assertThat(selectedProviders()).containsExactly(firstResult);
            assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
        } finally {
            releaseConstructionPermits();
            first.cancel(true);
            second.cancel(true);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void multiTypeExplicitLoadPublishesAllOrNothingWhenRuntimeFreezes() throws Exception {
        List<Class<?>> requestedTypes = List.of(
            FlowProvider.class,
            ExportProvider.class,
            ProviderCapacityTestTypes.EmptyA.class,
            ProviderCapacityTestTypes.EmptyB.class,
            Runnable.class);
        long generationBefore = ProviderRegistry.getGeneration();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (URLClassLoader loader = resourceLoader("provider-loader-blocking")) {
            Future<?> load = executor.submit(() -> ProviderRegistry.loadProviders(loader,
                requestedTypes.toArray(Class<?>[]::new)));
            try {
                BlockingFlowProvider.awaitConstructionStarted(Duration.ofSeconds(5));
                assertThat(ProviderRegistry.lookup(java.util.function.Supplier.class)).isNull();
                BlockingFlowProvider.allowNextConstruction();

                ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> load.get(5, TimeUnit.SECONDS));
                assertThat(failure.getCause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Provider registry is frozen after runtime start");
                assertThat(knownProviderTypes()).doesNotContainAnyElementsOf(requestedTypes);
                assertThat(cacheProviderTypes()).doesNotContainAnyElementsOf(requestedTypes);
                assertThat(requestedTypes.stream()
                    .noneMatch(engineMap("effectiveLoaders")::containsKey)).isTrue();
                assertThat(resolutionProviderTypes()).doesNotContainAnyElementsOf(requestedTypes);
                assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
            } finally {
                releaseConstructionPermits();
                load.cancel(true);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static URLClassLoader resourceLoader(String directory) throws Exception {
        URL root = moduleRoot().resolve("target/test-classes").resolve(directory).toUri().toURL();
        return new URLClassLoader(new URL[] {root},
            ProviderRegistryEpochConcurrencyTests.class.getClassLoader());
    }

    private static Path moduleRoot() {
        Path basedir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        return Files.isDirectory(basedir.resolve("target/test-classes"))
            ? basedir
            : basedir.resolve("tfi-flow-core");
    }

    private static long registryEpoch() throws Exception {
        Object engine = registryEngine();
        Field field = engine.getClass().getDeclaredField("registryEpoch");
        field.setAccessible(true);
        return field.getLong(engine);
    }

    private static List<Long> cacheEpochs(String fieldName) throws Exception {
        List<Long> epochs = new ArrayList<>();
        for (Object key : engineMap(fieldName).keySet()) {
            epochs.add((Long) accessor(key, "epoch"));
        }
        return epochs;
    }

    private static List<Object> cachedProviders() throws Exception {
        List<Object> providers = new ArrayList<>();
        for (Object value : engineMap("serviceLoaderCache").values()) {
            for (Object candidate : (List<?>) value) {
                providers.add(accessor(candidate, "provider"));
            }
        }
        return providers;
    }

    private static List<Class<?>> cacheProviderTypes() throws Exception {
        List<Class<?>> providerTypes = new ArrayList<>();
        for (Object key : engineMap("serviceLoaderCache").keySet()) {
            providerTypes.add((Class<?>) accessor(key, "providerType"));
        }
        return providerTypes;
    }

    private static List<Class<?>> resolutionProviderTypes() throws Exception {
        List<Class<?>> providerTypes = new ArrayList<>();
        for (Object key : engineMap("resolvedProviders").keySet()) {
            providerTypes.add((Class<?>) accessor(key, "providerType"));
        }
        return providerTypes;
    }

    private static List<Object> selectedProviders() throws Exception {
        return new ArrayList<>(engineMap("resolvedProviders").values());
    }

    private static Object accessor(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Map<?, ?> engineMap(String fieldName) throws Exception {
        Object engine = registryEngine();
        Field field = engine.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(engine);
    }

    private static int knownProviderTypeCount() throws Exception {
        return knownProviderTypes().size();
    }

    private static Set<Class<?>> knownProviderTypes() throws Exception {
        Object engine = registryEngine();
        Field field = engine.getClass().getDeclaredField("knownProviderTypes");
        field.setAccessible(true);
        var providerTypes = new java.util.HashSet<Class<?>>();
        for (Object providerType : (Set<?>) field.get(engine)) {
            providerTypes.add((Class<?>) providerType);
        }
        return Set.copyOf(providerTypes);
    }

    private static Object registryEngine() throws Exception {
        Field field = ProviderRegistry.class.getDeclaredField("engine");
        field.setAccessible(true);
        return field.get(null);
    }

    private static void releaseConstructionPermits() {
        for (int permit = 0; permit < 8; permit++) {
            BlockingFlowProvider.allowNextConstruction();
        }
    }

    private static void reserveTypeSlots(int count) {
        for (int dimensions = 1; dimensions <= count; dimensions++) {
            registerRaw(Array.newInstance(Object.class, new int[dimensions]).getClass(), new Object());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRaw(Class<?> providerType, Object provider) {
        ProviderRegistry.register((Class) providerType, provider);
    }

    private static LookupOutcome outcome(Future<?> future) throws Exception {
        try {
            return new LookupOutcome(future.get(5, TimeUnit.SECONDS), null);
        } catch (ExecutionException failure) {
            return new LookupOutcome(null, failure.getCause());
        }
    }

    private record LookupOutcome(Object provider, Throwable failure) {
    }
}
