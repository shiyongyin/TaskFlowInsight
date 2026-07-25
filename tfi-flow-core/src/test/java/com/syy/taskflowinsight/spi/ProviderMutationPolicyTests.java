package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.fixtures.PartialFailureProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.Formattable;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.RandomAccess;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Provider Registry 的启动期 mutation、容量与 epoch 契约。
 *
 * <p>这些边界必须先于后续 trust/selected cache 固化，否则同一进程可能同时观察到多个配置世代。
 */
class ProviderMutationPolicyTests {

    private static final String FROZEN_MESSAGE = "Provider registry is frozen after runtime start";
    private static final List<Class<?>> TYPE_SLOTS = List.of(
        Object.class, String.class, Integer.class, Long.class, Short.class, Byte.class, Boolean.class,
        Character.class, Float.class, Double.class, Number.class, Runnable.class, AutoCloseable.class,
        Cloneable.class, Comparable.class, CharSequence.class, Appendable.class, Iterable.class,
        Iterator.class, Spliterator.class, Collection.class, List.class, Set.class, Map.class,
        Queue.class, Deque.class, SortedSet.class, NavigableSet.class, SortedMap.class,
        NavigableMap.class, RandomAccess.class, Formattable.class, Formatter.class,
        StringBuilder.class, StringBuffer.class, BigInteger.class, BigDecimal.class, UUID.class,
        Optional.class, OptionalInt.class, OptionalLong.class, OptionalDouble.class, Locale.class,
        Currency.class, TimeZone.class, Calendar.class, Date.class, Instant.class, Duration.class,
        Period.class, LocalDate.class, LocalTime.class, LocalDateTime.class, OffsetDateTime.class,
        ZonedDateTime.class, ZoneId.class, Path.class, File.class, URI.class, URL.class,
        ClassLoader.class, Thread.class, ThreadGroup.class, Process.class, ProcessBuilder.class);

    @BeforeEach
    void setUp() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.spi.allowedProviders");
    }

    @AfterEach
    void tearDown() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.spi.allowedProviders");
    }

    @Test
    void firstLookupFreezesEveryPublicMutationEntry() {
        FlowProvider selected = new TestFlowProvider(10);
        ProviderRegistry.register(FlowProvider.class, selected);
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(selected);
        long generation = ProviderRegistry.getGeneration();

        assertAll(
            () -> assertFrozen(() -> ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(20))),
            () -> assertFrozen(() -> ProviderRegistry.unregister(FlowProvider.class, selected)),
            () -> assertFrozen(() -> ProviderRegistry.setAllowedProviders(List.of("com.example.*"))),
            () -> assertFrozen(() -> ProviderRegistry.loadProviders(
                ProviderRegistry.class.getClassLoader(), FlowProvider.class)));

        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(selected);
        ProviderRegistry.register(null, selected);
        ProviderRegistry.register(FlowProvider.class, null);
        assertThat(ProviderRegistry.unregister(null, selected)).isFalse();
        assertThat(ProviderRegistry.unregister(FlowProvider.class, null)).isFalse();
        ProviderRegistry.loadProviders(null, FlowProvider.class);
        Object nullLookup = ProviderRegistry.lookup(null);
        assertThat(nullLookup).isNull();
    }

    @Test
    void clearAllReopensStartupMutationAndAdvancesEpochOnce() throws Exception {
        ProviderRegistry.setAllowedProviders(List.of(TestFlowProvider.class.getName()));
        FlowProvider first = new TestFlowProvider(10);
        ProviderRegistry.register(FlowProvider.class, first);
        ProviderRegistry.lookup(FlowProvider.class);
        long generation = ProviderRegistry.getGeneration();
        long epoch = registryEpoch();

        ProviderRegistry.clearAll();

        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation + 1);
        assertThat(registryEpoch()).isEqualTo(epoch + 1);
        assertThat(ProviderRegistry.getAllRegistered()).isEmpty();
        FlowProvider replacement = new TestFlowProvider(20);
        ProviderRegistry.register(FlowProvider.class, replacement);
        ProviderRegistry.register(FlowProvider.class, new DisallowedFlowProvider());
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(replacement);
        assertThat(ProviderRegistry.getAllRegistered().get(FlowProvider.class)).containsExactly(replacement);
    }

    @Test
    void sixtyFifthRegisteredTypeIsRejectedAtomically() {
        for (int dimension = 1; dimension <= 64; dimension++) {
            registerTypeSlot(dimension);
        }
        long generation = ProviderRegistry.getGeneration();

        assertThatIllegalStateException()
            .isThrownBy(() -> registerTypeSlot(65))
            .withMessage("Provider registry supports at most 64 provider types per epoch");

        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
        assertThat(ProviderRegistry.getAllRegistered()).hasSize(64);
        registerTypeSlot(1);
        assertThat(ProviderRegistry.getAllRegistered()).hasSize(64);

        ProviderRegistry.clearAll();
        registerTypeSlot(65);
        assertThat(ProviderRegistry.getAllRegistered()).hasSize(1);
    }

    @Test
    void sixtyFifthRegistrationForTypeIsRejectedAtomically() {
        for (int index = 0; index < 64; index++) {
            ProviderRegistry.register(Object.class, new Object());
        }
        long generation = ProviderRegistry.getGeneration();

        assertThatIllegalStateException()
            .isThrownBy(() -> ProviderRegistry.register(Object.class, new Object()))
            .withMessage("Provider type supports at most 64 registered instances");

        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
        assertThat(ProviderRegistry.getAllRegistered().get(Object.class)).hasSize(64);
    }

    @Test
    void sixtyFifthLookupTypeIsRejectedWithoutStartingRuntime() {
        for (int dimension = 1; dimension <= 64; dimension++) {
            registerTypeSlot(dimension);
        }
        long generation = ProviderRegistry.getGeneration();

        assertThatIllegalStateException()
            .isThrownBy(() -> lookupRaw(typeSlot(65)))
            .withMessage("Provider registry supports at most 64 provider types per epoch");

        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
        registerTypeSlot(1);
    }

    @Test
    void successfulEmptyLookupConsumesTypeSlot() {
        for (int dimension = 1; dimension <= 63; dimension++) {
            registerTypeSlot(dimension);
        }

        assertThat(lookupRaw(typeSlot(64))).isNull();
        assertThatIllegalStateException()
            .isThrownBy(() -> lookupRaw(typeSlot(65)))
            .withMessage("Provider registry supports at most 64 provider types per epoch");
    }

    @Test
    void successfulLookupDoesNotAdvanceConfigurationGeneration() {
        long generation = ProviderRegistry.getGeneration();

        assertThat(ProviderRegistry.lookup(Runnable.class)).isNull();

        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
    }

    @Test
    void priorityCallbackRunsOutsideLifecycleLock() throws Exception {
        Object lifecycleLock = lifecycleLock();
        AtomicBoolean callbackInvoked = new AtomicBoolean();
        AtomicBoolean lockHeld = new AtomicBoolean();
        TestFlowProvider provider = new TestFlowProvider(10) {
            @Override
            public int priority() {
                callbackInvoked.set(true);
                lockHeld.set(Thread.holdsLock(lifecycleLock));
                return super.priority();
            }
        };

        ProviderRegistry.register(FlowProvider.class, provider);

        assertAll(
            () -> assertThat(callbackInvoked.get()).isTrue(),
            () -> assertThat(lockHeld.get()).isFalse());
    }

    @Test
    void equalsCallbackRunsOutsideLifecycleLock() throws Exception {
        Object lifecycleLock = lifecycleLock();
        TestFlowProvider registered = new TestFlowProvider(10);
        ProviderRegistry.register(FlowProvider.class, registered);
        AtomicBoolean callbackInvoked = new AtomicBoolean();
        AtomicBoolean lockHeld = new AtomicBoolean();
        TestFlowProvider matching = new TestFlowProvider(10) {
            @Override
            public boolean equals(Object other) {
                callbackInvoked.set(true);
                lockHeld.set(Thread.holdsLock(lifecycleLock));
                return other == registered;
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        assertThat(ProviderRegistry.unregister(FlowProvider.class, matching)).isTrue();
        assertAll(
            () -> assertThat(callbackInvoked.get()).isTrue(),
            () -> assertThat(lockHeld.get()).isFalse());
    }

    @Test
    void failedLookupScanConsumesTypeSlot() {
        for (int slot = 1; slot <= 63; slot++) {
            registerTypeSlot(slot);
        }
        Class<?> inaccessibleArrayType = Array.newInstance(Object.class, 0).getClass();

        assertThat(lookupRaw(inaccessibleArrayType)).isNull();
        assertThatIllegalStateException()
            .isThrownBy(() -> lookupRaw(typeSlot(64)))
            .withMessage("Provider registry supports at most 64 provider types per epoch");
    }

    @Test
    void failedLookupScanDoesNotExposeDiscoveredPrefix() throws Exception {
        long generation = ProviderRegistry.getGeneration();
        int knownTypes = knownProviderTypeCount();

        assertThat(ProviderRegistry.lookup(PartialFailureProvider.class)).isNull();

        assertAll(
            () -> assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation),
            () -> assertThat(knownProviderTypeCount()).isEqualTo(knownTypes + 1));
    }

    @Test
    void emptyExplicitScansDoNotPreflightUncommittedTypeCapacity() throws Exception {
        for (int slot = 1; slot <= 63; slot++) {
            registerTypeSlot(slot);
        }
        long generation = ProviderRegistry.getGeneration();
        int knownTypes = knownProviderTypeCount();
        ClassLoader emptyLoader = new ClassLoader(null) {
        };

        assertThatNoException().isThrownBy(() -> ProviderRegistry.loadProviders(
            emptyLoader, java.util.function.Supplier.class, java.util.function.Consumer.class));

        assertAll(
            () -> assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation),
            () -> assertThat(knownProviderTypeCount()).isEqualTo(knownTypes));
    }

    @Test
    void whitelistChangeDuringExplicitScanCannotPublishStaleCandidates() throws Exception {
        BlockingServiceResourceClassLoader classLoader = new BlockingServiceResourceClassLoader(
            ProviderRegistry.class.getClassLoader(), FlowProvider.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> scan = executor.submit(() ->
            ProviderRegistry.loadProviders(classLoader, FlowProvider.class));
        try {
            assertThat(classLoader.scanStarted.await(5, TimeUnit.SECONDS)).isTrue();
            ProviderRegistry.setAllowedProviders(List.of("com.example.*"));
            classLoader.releaseScan.countDown();
            scan.get(5, TimeUnit.SECONDS);

            assertThat(ProviderRegistry.lookup(FlowProvider.class)).isNull();
        } finally {
            classLoader.releaseScan.countDown();
            scan.cancel(true);
            executor.shutdownNow();
        }
    }

    @Test
    void clearAllDuringExplicitScanCannotPublishOldEpochCandidates() throws Exception {
        BlockingServiceResourceClassLoader classLoader = new BlockingServiceResourceClassLoader(
            ProviderRegistry.class.getClassLoader(), FlowProvider.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> scan = executor.submit(() ->
            ProviderRegistry.loadProviders(classLoader, FlowProvider.class));
        try {
            assertThat(classLoader.scanStarted.await(5, TimeUnit.SECONDS)).isTrue();
            ProviderRegistry.clearAll();
            ProviderRegistry.setAllowedProviders(List.of("com.example.*"));
            classLoader.releaseScan.countDown();
            scan.get(5, TimeUnit.SECONDS);

            assertThat(ProviderRegistry.lookup(FlowProvider.class)).isNull();
        } finally {
            classLoader.releaseScan.countDown();
            scan.cancel(true);
            executor.shutdownNow();
        }
    }

    private static void assertFrozen(Runnable mutation) {
        assertThatIllegalStateException().isThrownBy(mutation::run).withMessage(FROZEN_MESSAGE);
    }

    private static void registerTypeSlot(int slot) {
        registerRaw(typeSlot(slot), new Object());
    }

    private static Class<?> typeSlot(int slot) {
        return TYPE_SLOTS.get(slot - 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRaw(Class<?> providerType, Object provider) {
        ProviderRegistry.register((Class) providerType, provider);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object lookupRaw(Class<?> providerType) {
        return ProviderRegistry.lookup((Class) providerType);
    }

    private static long registryEpoch() throws Exception {
        Object engine = engine();
        Field epochField = engine.getClass().getDeclaredField("registryEpoch");
        epochField.setAccessible(true);
        return epochField.getLong(engine);
    }

    private static Object lifecycleLock() throws Exception {
        Object engine = engine();
        Field lockField = engine.getClass().getDeclaredField("lifecycleLock");
        lockField.setAccessible(true);
        return lockField.get(engine);
    }

    private static int knownProviderTypeCount() throws Exception {
        Object engine = engine();
        Field knownTypesField = engine.getClass().getDeclaredField("knownProviderTypes");
        knownTypesField.setAccessible(true);
        return ((Set<?>) knownTypesField.get(engine)).size();
    }

    private static Object engine() throws Exception {
        Field engineField = ProviderRegistry.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        return engineField.get(null);
    }

    private static final class BlockingServiceResourceClassLoader extends ClassLoader {
        private final String blockedResource;
        private final CountDownLatch scanStarted = new CountDownLatch(1);
        private final CountDownLatch releaseScan = new CountDownLatch(1);

        private BlockingServiceResourceClassLoader(ClassLoader parent, Class<?> providerType) {
            super(parent);
            this.blockedResource = "META-INF/services/" + providerType.getName();
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (blockedResource.equals(name)) {
                scanStarted.countDown();
                try {
                    if (!releaseScan.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release Provider scan");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to release Provider scan", interrupted);
                }
            }
            Enumeration<URL> resources = super.getResources(name);
            return resources == null ? Collections.emptyEnumeration() : resources;
        }
    }

    private static class TestFlowProvider extends StubFlowProvider {
        TestFlowProvider(int priority) {
            super(priority);
        }
    }

    private static final class DisallowedFlowProvider extends StubFlowProvider {
        DisallowedFlowProvider() {
            super(100);
        }
    }

    private static class StubFlowProvider implements FlowProvider {
        private final int priority;

        StubFlowProvider(int priority) {
            this.priority = priority;
        }

        @Override public String startSession(String sessionName) { return null; }
        @Override public void endSession() { }
        @Override public TaskNode startTask(String taskName) { return null; }
        @Override public void endTask() { }
        @Override public Session currentSession() { return null; }
        @Override public TaskNode currentTask() { return null; }
        @Override public void message(String content, String label) { }
        @Override public int priority() { return priority; }
    }
}
