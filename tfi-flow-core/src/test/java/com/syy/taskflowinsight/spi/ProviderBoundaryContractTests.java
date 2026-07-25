package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.exampleevil.AdjacentPackageFlowProvider;
import com.syy.taskflowinsight.spi.fixtures.LoaderAFlowProvider;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Provider trust 边界契约测试。 */
class ProviderBoundaryContractTests {

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
    void adjacentPackageDoesNotMatchAllowedPackage() {
        ProviderRegistry.setAllowedProviders(Set.of("com.example.*"));

        ProviderRegistry.register(FlowProvider.class, new AdjacentPackageFlowProvider());

        assertThat(ProviderRegistry.getAllRegistered()).doesNotContainKey(FlowProvider.class);
    }

    @Test
    void exactClassNameAllowsManualProvider() {
        AdjacentPackageFlowProvider provider = new AdjacentPackageFlowProvider();
        ProviderRegistry.setAllowedProviders(Set.of(provider.getClass().getName()));

        ProviderRegistry.register(FlowProvider.class, provider);

        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);
    }

    @Test
    void packageRuleAllowsProviderInSubpackage() {
        LoaderAFlowProvider provider = new LoaderAFlowProvider();
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.*"));

        ProviderRegistry.register(FlowProvider.class, provider);

        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);
    }

    @Test
    void explicitDisabledWhitelistIgnoresSystemProperty() {
        System.setProperty("tfi.spi.allowedProviders", "com.other.*");
        ProviderRegistry.setAllowedProviders(null);
        LoaderAFlowProvider provider = new LoaderAFlowProvider();

        ProviderRegistry.register(FlowProvider.class, provider);

        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);
    }

    @Test
    void freshEngineUsesSystemPropertyWhenWhitelistIsUnset() {
        System.setProperty("tfi.spi.allowedProviders", "com.other.*");
        ProviderRegistryEngine freshEngine = new ProviderRegistryEngine();

        freshEngine.register(FlowProvider.class, new LoaderAFlowProvider());

        assertThat(freshEngine.registeredProviders()).isEmpty();
    }

    @Test
    void externalServiceLoaderUsesTheSameWhitelistBoundary() throws Exception {
        ProviderRegistry.setAllowedProviders(Set.of("com.syy.taskflowinsight.spi.fixtures.*"));
        try (URLClassLoader loaderA = resourceLoader("provider-loader-a")) {
            ProviderRegistry.loadProviders(loaderA, FlowProvider.class);
            assertThat(ProviderRegistry.lookup(FlowProvider.class).startSession("test"))
                .isEqualTo("loader-a");
        }

        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(Set.of("com.example.*"));
        try (URLClassLoader blockedLoader = resourceLoader("provider-loader-blocked")) {
            ProviderRegistry.loadProviders(blockedLoader, FlowProvider.class);

            assertThat(ProviderRegistry.lookup(FlowProvider.class)).isNull();
            assertThat(cachedProviderObjects())
                .noneMatch(AdjacentPackageFlowProvider.class::isInstance);
        }
    }

    private static URLClassLoader resourceLoader(String directory) throws Exception {
        Path moduleRoot = moduleRoot();
        URL resourceRoot = moduleRoot.resolve("target/test-classes").resolve(directory)
            .toUri().toURL();
        return new URLClassLoader(new URL[] {resourceRoot},
            ProviderBoundaryContractTests.class.getClassLoader());
    }

    private static Path moduleRoot() {
        Path basedir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        return Files.isDirectory(basedir.resolve("target/test-classes"))
            ? basedir
            : basedir.resolve("tfi-flow-core");
    }

    private static List<Object> cachedProviderObjects() throws Exception {
        Object engine = registryEngine();
        var cacheField = engine.getClass().getDeclaredField("serviceLoaderCache");
        cacheField.setAccessible(true);
        List<Object> providers = new ArrayList<>();
        for (Object value : ((Map<?, ?>) cacheField.get(engine)).values()) {
            for (Object candidate : (List<?>) value) {
                providers.add(candidateProvider(candidate));
            }
        }
        return providers;
    }

    private static Object candidateProvider(Object candidate) throws Exception {
        try {
            Method provider = candidate.getClass().getDeclaredMethod("provider");
            provider.setAccessible(true);
            return provider.invoke(candidate);
        } catch (NoSuchMethodException ignored) {
            return candidate;
        }
    }

    private static Object registryEngine() throws Exception {
        var engineField = ProviderRegistry.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        return engineField.get(null);
    }
}
