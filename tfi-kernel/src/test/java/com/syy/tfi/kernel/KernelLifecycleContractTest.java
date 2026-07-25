package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 从静态门面验证业务透明性，避免测试绑定内部上下文或节点实现。
 */
class KernelLifecycleContractTest {

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kvLc01DisabledCallReturnsTheExactBusinessResultOnce() {
        List<FlowSession> sessions = new ArrayList<>();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
        Tfi.setEnabled(false);

        Object expected = new Object();
        AtomicInteger calls = new AtomicInteger();
        Object actual = Tfi.call("disabled-call", () -> {
            calls.incrementAndGet();
            return expected;
        });

        assertThat(actual).isSameAs(expected);
        assertThat(calls).hasValue(1);
        assertThat(sessions).isEmpty();
    }

    @Test
    void kvLc02SamplerRejectionStillExecutesBusinessStageOnce() {
        List<FlowSession> sessions = new ArrayList<>();
        AtomicInteger samples = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                name -> {
                    samples.incrementAndGet();
                    return false;
                },
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));

        try (Stage ignored = Tfi.begin("sampled-out")) {
            Tfi.stage("business-stage", calls::incrementAndGet);
        }

        assertThat(samples).hasValue(1);
        assertThat(calls).hasValue(1);
        assertThat(sessions).isEmpty();
    }

    @Test
    void kvLc03CallbackFailureKeepsIdentityAndMarksTheFlowError() {
        List<FlowSession> sessions = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
        RuntimeException expected = new IllegalStateException("business failure");

        try (Stage ignored = Tfi.begin("root")) {
            Throwable actual = catchThrowable(() -> Tfi.call("failing-call", () -> {
                calls.incrementAndGet();
                throw expected;
            }));
            assertThat(actual).isSameAs(expected);
        }

        assertThat(calls).hasValue(1);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(FlowStatus.ERROR);
            assertThat(session.root().status()).isEqualTo(FlowStatus.ERROR);
            assertThat(session.root().children()).singleElement().satisfies(child -> {
                assertThat(child.status()).isEqualTo(FlowStatus.ERROR);
                assertThat(child.records()).singleElement().satisfies(record ->
                        assertThat(record.code()).isEqualTo("CALLBACK_ERROR"));
            });
        });
    }

    @Test
    void kvLc04OrdinarySinkFailureDoesNotEscapeOrBlockLaterSinks() {
        List<FlowSession> delivered = new ArrayList<>();
        AtomicInteger firstSinkCalls = new AtomicInteger();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(
                        session -> {
                            firstSinkCalls.incrementAndGet();
                            throw new IllegalStateException("sink unavailable");
                        },
                        delivered::add),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));

        Throwable escaped = catchThrowable(() -> {
            try (Stage ignored = Tfi.begin("sink-isolation")) {
                Tfi.message("business completed");
            }
        });

        assertThat(escaped).isNull();
        assertThat(firstSinkCalls).hasValue(1);
        assertThat(delivered).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(FlowStatus.OK);
            assertThat(session.root().records()).hasSize(1);
        });
    }
}
