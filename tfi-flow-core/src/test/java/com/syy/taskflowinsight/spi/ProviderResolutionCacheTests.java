package com.syy.taskflowinsight.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.syy.taskflowinsight.spi.fixtures.PartialFailureProvider;
import com.syy.taskflowinsight.spi.fixtures.ProviderCapacityTestTypes;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Provider selected-result cache 的单一 owner 与生命周期契约。 */
class ProviderResolutionCacheTests {

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
    void lookupAndResolveShareOneSelectedInstance() throws Exception {
        FlowProvider resolved = ProviderRegistry.resolve(FlowProvider.class);

        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(resolved);
        Map<?, ?> selected = engineMap("resolvedProviders");
        assertThat(selected).hasSize(1);
        assertThat(selected.containsValue(resolved)).isTrue();
    }

    @Test
    void mutationAfterResolutionIsRejectedWithoutChangingSelection() {
        FlowProvider resolved = ProviderRegistry.resolve(FlowProvider.class);

        assertThatIllegalStateException()
            .isThrownBy(() -> ProviderRegistry.register(FlowProvider.class, new StubFlowProvider()))
            .withMessage("Provider registry is frozen after runtime start");
        assertThat(ProviderRegistry.resolve(FlowProvider.class)).isSameAs(resolved);
    }

    @Test
    void frozenSelectionHotPathDoesNotReacquireLifecycleLock() throws Exception {
        FlowProvider resolved = ProviderRegistry.resolve(FlowProvider.class);
        Object lifecycleLock = engineField("lifecycleLock");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            synchronized (lifecycleLock) {
                Future<FlowProvider> lookup = executor.submit(
                        () -> ProviderRegistry.resolve(FlowProvider.class));
                assertThat(lookup.get(1, TimeUnit.SECONDS)).isSameAs(resolved);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void clearAllInvalidatesSelectedAndCandidateCachesAndReopensStartup() throws Exception {
        FlowProvider firstEpoch = ProviderRegistry.resolve(FlowProvider.class);
        assertThat(engineMap("resolvedProviders").containsValue(firstEpoch)).isTrue();
        assertThat(engineMap("serviceLoaderCache")).isNotEmpty();

        ProviderRegistry.clearAll();

        assertThat(engineMap("resolvedProviders")).isEmpty();
        assertThat(engineMap("serviceLoaderCache")).isEmpty();
        StubFlowProvider replacement = new StubFlowProvider();
        ProviderRegistry.register(FlowProvider.class, replacement);
        assertThat(ProviderRegistry.resolve(FlowProvider.class)).isSameAs(replacement);
    }

    @Test
    void emptyResolutionPublishesTheNoProviderSentinelOnce() throws Exception {
        assertThat(ProviderRegistry.resolve(ProviderCapacityTestTypes.EmptyA.class)).isNull();
        Map<?, ?> selected = engineMap("resolvedProviders");
        Object noProvider = engineField("NO_PROVIDER");

        assertThat(selected).hasSize(1);
        assertThat(selected.containsValue(noProvider)).isTrue();
        assertThat(ProviderRegistry.lookup(ProviderCapacityTestTypes.EmptyA.class)).isNull();
        Map<?, ?> selectedAgain = engineMap("resolvedProviders");
        assertThat(selectedAgain).hasSize(1);
        assertThat(selectedAgain.containsValue(noProvider)).isTrue();
    }

    @Test
    void failedScanPublishesOneFailureSentinelPerEpoch() throws Exception {
        PartialFailureProvider.Valid.reset();

        assertThat(ProviderRegistry.resolve(PartialFailureProvider.class)).isNull();
        assertThat(ProviderRegistry.lookup(PartialFailureProvider.class)).isNull();
        assertThat(PartialFailureProvider.Valid.constructionCount()).isEqualTo(1);

        Map<?, ?> selected = engineMap("resolvedProviders");
        Object scanFailure = engineField("SCAN_FAILURE");
        assertThat(selected).hasSize(1);
        assertThat(selected.containsValue(scanFailure)).isTrue();
        Map<?, ?> candidates = engineMap("serviceLoaderCache");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.values()).singleElement()
            .satisfies(value -> assertThat((List<?>) value).isEmpty());

        ProviderRegistry.clearAll();

        assertThat(engineMap("resolvedProviders")).isEmpty();
        assertThat(engineMap("serviceLoaderCache")).isEmpty();
        assertThat(ProviderRegistry.resolve(PartialFailureProvider.class)).isNull();
        assertThat(ProviderRegistry.lookup(PartialFailureProvider.class)).isNull();
        assertThat(PartialFailureProvider.Valid.constructionCount()).isEqualTo(2);
    }

    @Test
    void nullResolveDoesNotStartRuntime() throws Exception {
        Object resolved = ProviderRegistry.resolve(null);

        assertThat(resolved).isNull();
        assertThat(engineMap("resolvedProviders")).isEmpty();
        StubFlowProvider provider = new StubFlowProvider();
        ProviderRegistry.register(FlowProvider.class, provider);
        assertThat(ProviderRegistry.resolve(FlowProvider.class)).isSameAs(provider);
    }

    private static Map<?, ?> engineMap(String fieldName) throws Exception {
        return (Map<?, ?>) engineField(fieldName);
    }

    private static Object engineField(String fieldName) throws Exception {
        Object engine = registryEngine();
        Field field = engine.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(engine);
    }

    private static Object registryEngine() throws Exception {
        Field field = ProviderRegistry.class.getDeclaredField("engine");
        field.setAccessible(true);
        return field.get(null);
    }

    private static final class StubFlowProvider extends DefaultFlowProvider {

        @Override
        public int priority() {
            return 2_000;
        }
    }
}
