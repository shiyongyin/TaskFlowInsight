package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 锁定静态TFI门面对typed tracking生命周期的无状态适配合同。
 *
 * <p>这些测试刻意让provider看不到action，用于反证facade不会缓存provider、回退到legacy
 * action wrapper，或在executor之外形成第二个业务时序owner。</p>
 */
class TfiTrackingFacadeContractTests {

    @BeforeEach
    void setUp() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.api.routing.enabled");
    }

    @AfterEach
    void tearDown() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.api.routing.enabled");
    }

    @Test
    void hiddenRoutingFlagCannotCreateSecondTrackingPath() {
        RecordingProvider provider = new RecordingProvider(null);
        ProviderRegistry.register(TrackingProvider.class, provider);
        System.setProperty("tfi.api.routing.enabled", "false");
        AtomicInteger actionCalls = new AtomicInteger();

        TFI.withTracked("order", new Object(), actionCalls::incrementAndGet);

        assertThat(actionCalls).hasValue(1);
        assertThat(provider.beginCalls).hasValue(1);
        assertThat(provider.captureCalls).hasValue(1);
        assertThat(provider.closeCalls).hasValue(1);
    }

    @Test
    void legacyFieldsCannotCreatePerCallPolicy() {
        RecordingProvider provider = new RecordingProvider(null);
        ProviderRegistry.register(TrackingProvider.class, provider);

        TFI.withTracked("order", new Object(), () -> { }, "status", "amount");

        assertThat(provider.receivedOptions).isNotNull();
        assertThat(provider.receivedOptions.getPolicy().includePathPatterns()).isEmpty();
    }

    @Test
    void invalidInputFailsBeforeProviderAndAction() {
        RecordingProvider provider = new RecordingProvider(null);
        ProviderRegistry.register(TrackingProvider.class, provider);
        AtomicInteger actionCalls = new AtomicInteger();

        assertThatThrownBy(() -> TFI.withTracked(" ", new Object(), actionCalls::incrementAndGet))
                .isInstanceOf(CompareInputException.class);

        assertThat(actionCalls).hasValue(0);
        assertThat(provider.beginCalls).hasValue(0);
    }

    @Test
    void businessFailureKeepsIdentityAndIsNotCapturedOrRetried() {
        RecordingProvider provider = new RecordingProvider(null);
        ProviderRegistry.register(TrackingProvider.class, provider);
        AtomicInteger actionCalls = new AtomicInteger();
        IllegalStateException businessFailure = new IllegalStateException("business-failure");

        assertThatThrownBy(() -> TFI.withTracked("order", new Object(), () -> {
            actionCalls.incrementAndGet();
            throw businessFailure;
        })).isSameAs(businessFailure);

        assertThat(actionCalls).hasValue(1);
        assertThat(provider.beginCalls).hasValue(1);
        assertThat(provider.captureCalls).hasValue(0);
        assertThat(provider.closeCalls).hasValue(1);
    }

    @Test
    void ordinaryBeginFailureStillRunsActionExactlyOnce() {
        RecordingProvider provider = new RecordingProvider(new IllegalStateException("begin-failure"));
        ProviderRegistry.register(TrackingProvider.class, provider);
        AtomicInteger actionCalls = new AtomicInteger();

        TFI.withTracked("order", new Object(), actionCalls::incrementAndGet);

        assertThat(actionCalls).hasValue(1);
        assertThat(provider.beginCalls).hasValue(1);
        assertThat(provider.captureCalls).hasValue(0);
        assertThat(provider.closeCalls).hasValue(0);
    }

    @Test
    void missingProviderDoesNotRunLegacyFallbackAction() {
        ProviderRegistry.setAllowedProviders(List.of("missing.provider.Implementation"));
        AtomicInteger actionCalls = new AtomicInteger();

        assertThatThrownBy(() -> TFI.withTracked("order", new Object(), actionCalls::incrementAndGet))
                .isInstanceOf(NullPointerException.class);

        assertThat(actionCalls).hasValue(0);
    }

    @Test
    void facadeResolvesProviderAgainAfterRegistryEpochChanges() {
        RecordingProvider first = new RecordingProvider(null);
        ProviderRegistry.register(TrackingProvider.class, first);
        TFI.withTracked("first", new Object(), () -> { });

        ProviderRegistry.clearAll();
        RecordingProvider second = new RecordingProvider(null);
        ProviderRegistry.register(TrackingProvider.class, second);
        TFI.withTracked("second", new Object(), () -> { });

        assertThat(first.beginCalls).hasValue(1);
        assertThat(second.beginCalls).hasValue(1);
    }

    /** provider只暴露资源事件，避免测试替身成为action时序owner。 */
    private static final class RecordingProvider implements TrackingProvider {

        /** begin阶段的可选普通故障。 */
        private final RuntimeException beginFailure;
        /** 实际进入begin的次数。 */
        private final AtomicInteger beginCalls = new AtomicInteger();
        /** 实际消费batch的次数。 */
        private final AtomicInteger captureCalls = new AtomicInteger();
        /** 实际释放batch的次数。 */
        private final AtomicInteger closeCalls = new AtomicInteger();
        /** 用于反证facade没有临时构造第二份runtime policy。 */
        private CompareOptions receivedOptions;

        private RecordingProvider(RuntimeException beginFailure) {
            this.beginFailure = beginFailure;
        }

        @Override
        public TrackingBatchScope begin(
                List<TrackingExecutor.Target> targets,
                CompareOptions options) {
            beginCalls.incrementAndGet();
            receivedOptions = options;
            if (beginFailure != null) {
                throw beginFailure;
            }
            return new TrackingBatchScope() {
                @Override
                public List<TrackingExecutor.Item> capture() {
                    captureCalls.incrementAndGet();
                    return targets.stream()
                            .map(target -> new TrackingExecutor.Item(target.name(), CompareResult.identical()))
                            .toList();
                }

                @Override
                public void close() {
                    closeCalls.incrementAndGet();
                }
            };
        }

        @Override
        public int priority() {
            return Integer.MAX_VALUE;
        }
    }
}
