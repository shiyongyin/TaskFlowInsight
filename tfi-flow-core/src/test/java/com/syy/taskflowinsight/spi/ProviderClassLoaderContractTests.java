package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.syy.taskflowinsight.spi.fixtures.OverCapacityFlowProviders;
import com.syy.taskflowinsight.spi.fixtures.OverCapacityLookupProvider;
import com.syy.taskflowinsight.spi.fixtures.ProviderCapacityTestTypes;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Provider candidate cache 与 effective ClassLoader 的身份契约。 */
class ProviderClassLoaderContractTests {

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
    void lastNonEmptyExplicitLoaderBecomesEffectiveWithoutDiscardingPriorCandidates()
            throws Exception {
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.spi.fixtures.*"));
        try (URLClassLoader loaderA = resourceLoader("provider-loader-a");
                URLClassLoader loaderB = resourceLoader("provider-loader-b")) {
            ProviderRegistry.loadProviders(loaderA, FlowProvider.class);
            ProviderRegistry.loadProviders(loaderB, FlowProvider.class);

            Map<?, ?> candidateCache = engineMap("serviceLoaderCache");
            assertThat(candidateCache).hasSize(2);
            assertThat(candidateCache.keySet().stream().map(ProviderClassLoaderContractTests::keyLoader))
                .containsExactlyInAnyOrder(loaderA, loaderB);
            assertThat(engineMap("effectiveLoaders").get(FlowProvider.class)).isSameAs(loaderB);
            assertThat(ProviderRegistry.lookup(FlowProvider.class).startSession("test"))
                .isEqualTo("loader-b");
        }
    }

    @Test
    void equalButDistinctClassLoadersRemainIsolatedByIdentity() throws Exception {
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.spi.fixtures.*"));
        try (EqualUrlClassLoader loaderA = equalResourceLoader("provider-loader-a");
                EqualUrlClassLoader loaderB = equalResourceLoader("provider-loader-b")) {
            assertThat(loaderA).isEqualTo(loaderB).isNotSameAs(loaderB);

            ProviderRegistry.loadProviders(loaderA, FlowProvider.class);
            ProviderRegistry.loadProviders(loaderB, FlowProvider.class);

            Map<?, ?> candidateCache = engineMap("serviceLoaderCache");
            assertThat(candidateCache).hasSize(2);
            assertThat(candidateCache.keySet().stream().map(ProviderClassLoaderContractTests::keyLoader))
                .containsExactlyInAnyOrder(loaderA, loaderB);
            assertThat(engineMap("effectiveLoaders").get(FlowProvider.class)).isSameAs(loaderB);
            assertThat(ProviderRegistry.lookup(FlowProvider.class).startSession("test"))
                .isEqualTo("loader-b");
        }
    }

    @Test
    void resolutionKeysRemainDistinctForEqualButDifferentClassLoaders() throws Exception {
        try (EqualUrlClassLoader loaderA = equalResourceLoader("provider-loader-a");
                EqualUrlClassLoader loaderB = equalResourceLoader("provider-loader-b")) {
            assertThat(loaderA).isEqualTo(loaderB).isNotSameAs(loaderB);

            Object keyA = resolutionKey(7L, FlowProvider.class, loaderA);
            Object keyB = resolutionKey(7L, FlowProvider.class, loaderB);

            assertThat(keyA).isNotEqualTo(keyB);
        }
    }

    @Test
    void emptyExplicitScanDoesNotReplaceEffectiveLoaderAndResetReturnsToDefault()
            throws Exception {
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.spi.fixtures.*"));
        try (URLClassLoader loaderB = resourceLoader("provider-loader-b");
                URLClassLoader emptyLoader = new URLClassLoader(new URL[0], null)) {
            ProviderRegistry.loadProviders(loaderB, FlowProvider.class);
            ProviderRegistry.loadProviders(emptyLoader, FlowProvider.class);

            assertThat(engineMap("effectiveLoaders").get(FlowProvider.class)).isSameAs(loaderB);
            assertThat(ProviderRegistry.lookup(FlowProvider.class).startSession("test"))
                .isEqualTo("loader-b");
        }

        ProviderRegistry.clearAll();
        assertThat(engineMap("serviceLoaderCache")).isEmpty();
        assertThat(engineMap("effectiveLoaders")).isEmpty();
        ProviderRegistry.setAllowedProviders(Set.of(DefaultFlowProvider.class.getName()));

        assertThat(ProviderRegistry.lookup(FlowProvider.class))
            .isExactlyInstanceOf(DefaultFlowProvider.class);
        assertThat(engineMap("serviceLoaderCache").keySet().stream()
            .map(ProviderClassLoaderContractTests::keyLoader))
            .containsExactly(ProviderRegistry.class.getClassLoader());
    }

    @Test
    void ninthLoaderForOneTypeIsRejectedWithoutScanning() throws Exception {
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.spi.fixtures.*"));
        var loaders = new ArrayList<URLClassLoader>();
        try {
            for (int index = 0; index < 8; index++) {
                URLClassLoader loader = resourceLoader("provider-loader-a");
                loaders.add(loader);
                ProviderRegistry.loadProviders(loader, FlowProvider.class);
            }
            ClassLoader effectiveBefore = (ClassLoader) engineMap("effectiveLoaders")
                .get(FlowProvider.class);
            long generationBefore = ProviderRegistry.getGeneration();
            CountingUrlClassLoader ninth = new CountingUrlClassLoader(
                new URL[] {resourceUrl("provider-loader-a")},
                ProviderClassLoaderContractTests.class.getClassLoader());
            loaders.add(ninth);

            org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> ProviderRegistry.loadProviders(ninth, FlowProvider.class))
                .withMessage("Provider type supports at most 8 cached ClassLoader identities");
            assertThat(ninth.resourceLookups.get()).isZero();
            assertThat(engineMap("serviceLoaderCache")).hasSize(8);
            assertThat(engineMap("effectiveLoaders").get(FlowProvider.class))
                .isSameAs(effectiveBefore);
            assertThat(engineMap("resolvedProviders")).isEmpty();
            assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
        } finally {
            for (URLClassLoader loader : loaders) {
                loader.close();
            }
        }
    }

    @Test
    void sixtyFifthServiceDeclarationFailsWithoutPublication() throws Exception {
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.spi.fixtures.*"));
        OverCapacityFlowProviders.reset();
        long generationBefore = ProviderRegistry.getGeneration();
        int knownTypesBefore = knownProviderTypeCount();
        int cacheEntriesBefore = engineMap("serviceLoaderCache").size();
        int effectiveLoadersBefore = engineMap("effectiveLoaders").size();

        try (URLClassLoader overCapacity = isolatedResourceLoader(
                "provider-loader-over-capacity", FlowProvider.class)) {
            assertThatIllegalStateException()
                .isThrownBy(() -> ProviderRegistry.loadProviders(overCapacity, FlowProvider.class))
                .withMessage("Provider scan supports at most 64 declarations per type and ClassLoader");
        }

        assertThat(OverCapacityFlowProviders.constructionCount()).isZero();
        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
        assertThat(knownProviderTypeCount()).isEqualTo(knownTypesBefore);
        assertThat(engineMap("serviceLoaderCache")).hasSize(cacheEntriesBefore);
        assertThat(engineMap("effectiveLoaders")).hasSize(effectiveLoadersBefore);
        assertThat(engineMap("resolvedProviders")).isEmpty();

        try (URLClassLoader loaderA = resourceLoader("provider-loader-a")) {
            ProviderRegistry.loadProviders(loaderA, FlowProvider.class);
            assertThat(ProviderRegistry.lookup(FlowProvider.class).startSession("test"))
                .isEqualTo("loader-a");
        }
    }

    @Test
    void normalLookupDeclarationCapacityFailureLeavesNoReservationOrCache()
            throws Exception {
        for (int slot = 1; slot <= 63; slot++) {
            registerRaw(typeSlot(slot), new Object());
        }
        OverCapacityFlowProviders.reset();
        long generationBefore = ProviderRegistry.getGeneration();
        int knownTypesBefore = knownProviderTypeCount();

        assertThatIllegalStateException()
            .isThrownBy(() -> ProviderRegistry.lookup(OverCapacityLookupProvider.class))
            .withMessage("Provider scan supports at most 64 declarations per type and ClassLoader");

        assertThat(OverCapacityFlowProviders.constructionCount()).isZero();
        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
        assertThat(knownProviderTypeCount()).isEqualTo(knownTypesBefore);
        assertThat(engineMap("serviceLoaderCache")).isEmpty();
        assertThat(engineMap("effectiveLoaders")).isEmpty();
        assertThat(engineMap("resolvedProviders")).isEmpty();

        assertThat(ProviderRegistry.lookup(ProviderCapacityTestTypes.EmptyA.class)).isNull();
        assertThat(knownProviderTypeCount()).isEqualTo(64);
        assertThat(engineMap("resolvedProviders")).hasSize(1);
    }

    @Test
    void registeredWinnerBypassesOverCapacityServiceLoaderScan() throws Exception {
        OverCapacityLookupProvider registered = new OverCapacityFlowProviders.Provider01();
        OverCapacityFlowProviders.reset();
        ProviderRegistry.register(OverCapacityLookupProvider.class, registered);
        long generationBefore = ProviderRegistry.getGeneration();

        assertThat(ProviderRegistry.lookup(OverCapacityLookupProvider.class)).isSameAs(registered);
        assertThat(OverCapacityFlowProviders.constructionCount()).isZero();
        assertThat(ProviderRegistry.getGeneration()).isEqualTo(generationBefore);
        assertThat(engineMap("serviceLoaderCache")).isEmpty();
        Map<?, ?> selected = engineMap("resolvedProviders");
        assertThat(selected).hasSize(1);
        assertThat(selected.containsValue(registered)).isTrue();
    }

    private static URLClassLoader resourceLoader(String directory) throws Exception {
        return new URLClassLoader(new URL[] {resourceUrl(directory)},
            ProviderClassLoaderContractTests.class.getClassLoader());
    }

    private static EqualUrlClassLoader equalResourceLoader(String directory) throws Exception {
        return new EqualUrlClassLoader(new URL[] {resourceUrl(directory)},
            ProviderClassLoaderContractTests.class.getClassLoader());
    }

    private static URLClassLoader isolatedResourceLoader(String directory, Class<?> providerType)
            throws Exception {
        return new IsolatedServiceUrlClassLoader(new URL[] {resourceUrl(directory)},
            ProviderClassLoaderContractTests.class.getClassLoader(), providerType);
    }

    private static URL resourceUrl(String directory) throws Exception {
        return moduleRoot().resolve("target/test-classes").resolve(directory).toUri().toURL();
    }

    private static Path moduleRoot() {
        Path basedir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        return Files.isDirectory(basedir.resolve("target/test-classes"))
            ? basedir
            : basedir.resolve("tfi-flow-core");
    }

    private static Map<?, ?> engineMap(String fieldName) throws Exception {
        Object engine = registryEngine();
        Field field = engine.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(engine);
    }

    private static int knownProviderTypeCount() throws Exception {
        Object engine = registryEngine();
        Field field = engine.getClass().getDeclaredField("knownProviderTypes");
        field.setAccessible(true);
        return ((Set<?>) field.get(engine)).size();
    }

    private static Class<?> typeSlot(int dimensions) {
        return Array.newInstance(Object.class, new int[dimensions]).getClass();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRaw(Class<?> providerType, Object provider) {
        ProviderRegistry.register((Class) providerType, provider);
    }

    private static ClassLoader keyLoader(Object key) {
        try {
            Field identityField = key.getClass().getDeclaredField("loaderIdentity");
            identityField.setAccessible(true);
            Object identity = identityField.get(key);
            Field valueField = identity.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            return (ClassLoader) valueField.get(identity);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect LoaderKey", failure);
        }
    }

    private static Object resolutionKey(long epoch, Class<?> providerType, ClassLoader loader)
            throws Exception {
        Class<?> identityType = Class.forName(
            "com.syy.taskflowinsight.spi.ProviderRegistryEngine$LoaderIdentity");
        Constructor<?> identityConstructor = identityType.getDeclaredConstructor(ClassLoader.class);
        identityConstructor.setAccessible(true);
        Object identity = identityConstructor.newInstance(loader);
        Class<?> keyType = Class.forName(
            "com.syy.taskflowinsight.spi.ProviderRegistryEngine$ResolutionKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructor(
            long.class, Class.class, identityType);
        keyConstructor.setAccessible(true);
        return keyConstructor.newInstance(epoch, providerType, identity);
    }

    private static Object registryEngine() throws Exception {
        Field engineField = ProviderRegistry.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        return engineField.get(null);
    }

    private static final class EqualUrlClassLoader extends URLClassLoader {

        private EqualUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualUrlClassLoader;
        }

        @Override
        public int hashCode() {
            return 37;
        }
    }

    private static final class CountingUrlClassLoader extends URLClassLoader {
        private final AtomicInteger resourceLookups = new AtomicInteger();

        private CountingUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + FlowProvider.class.getName()).equals(name)) {
                resourceLookups.incrementAndGet();
            }
            return super.getResources(name);
        }
    }

    private static final class IsolatedServiceUrlClassLoader extends URLClassLoader {
        private final String isolatedResource;

        private IsolatedServiceUrlClassLoader(URL[] urls, ClassLoader parent, Class<?> providerType) {
            super(urls, parent);
            isolatedResource = "META-INF/services/" + providerType.getName();
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return isolatedResource.equals(name) ? findResources(name) : super.getResources(name);
        }
    }
}
