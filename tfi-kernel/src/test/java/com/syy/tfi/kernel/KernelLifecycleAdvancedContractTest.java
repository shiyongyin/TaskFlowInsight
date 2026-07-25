package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.KernelClock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证同线程嵌套、配置快照和异常矩阵，不依赖内核私有状态。
 */
class KernelLifecycleAdvancedContractTest {

    @AfterEach
    void restoreDefaults() {
        Thread.interrupted();
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kvLc05NestedBeginAndNonLifoClosePublishOneCompleteSession() {
        List<FlowSession> sessions = new ArrayList<>();
        configureSinks(List.of(sessions::add));

        Stage root = Tfi.begin("root");
        Stage nested = Tfi.begin("nested");
        Stage descendant = Tfi.stage("descendant");
        root.close();
        descendant.close();
        nested.close();
        root.close();

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(FlowStatus.OK);
            assertThat(session.incompleteReasons()).containsExactly("NON_LIFO_CLOSE");
            assertThat(session.root().children()).singleElement().satisfies(child -> {
                assertThat(child.name()).isEqualTo("nested");
                assertThat(child.status()).isEqualTo(FlowStatus.OK);
                assertThat(child.records()).singleElement().satisfies(record ->
                        assertThat(record.code()).isEqualTo("KERNEL_NESTED_BEGIN"));
                assertThat(child.children()).singleElement().satisfies(grandchild -> {
                    assertThat(grandchild.name()).isEqualTo("descendant");
                    assertThat(grandchild.status()).isEqualTo(FlowStatus.OK);
                });
            });
        });
    }

    @Test
    void kvLc10ConfigureDuringSessionOnlyAffectsTheNextSession() {
        List<FlowSession> oldSessions = new ArrayList<>();
        List<FlowSession> newSessions = new ArrayList<>();
        KernelClock oldClock = new FixedClock(100L);
        KernelClock newClock = new FixedClock(200L);
        Tfi.configure(new KernelConfig(
                true, List.of(oldSessions::add), name -> true, () -> "old-id", oldClock,
                1, 1_000, 100, 4));

        Stage oldRoot = Tfi.begin("old-config");
        Tfi.configure(new KernelConfig(
                true, List.of(newSessions::add), name -> true, () -> "new-id", newClock,
                2, 2_000, 100, 8));
        Stage rejectedByOldLimit = Tfi.stage("old-limit-still-applies");
        Tfi.message("uses-old-clock");
        oldRoot.close();

        Stage newRoot = Tfi.begin("new-config");
        Stage acceptedByNewLimit = Tfi.stage("new-limit-applies");
        acceptedByNewLimit.close();
        newRoot.close();

        assertThat(rejectedByOldLimit.remainingEncodedBytes()).isEqualTo(-1);
        assertThat(oldSessions).singleElement().satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo("old-id");
            assertThat(session.startMs()).isEqualTo(100L);
            assertThat(session.incompleteReasons()).containsExactly("STAGE_LIMIT");
            assertThat(session.root().records()).singleElement().satisfies(record ->
                    assertThat(record.atMs()).isEqualTo(100L));
        });
        assertThat(newSessions).singleElement().satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo("new-id");
            assertThat(session.startMs()).isEqualTo(200L);
            assertThat(session.root().children()).hasSize(1);
            assertThat(session.incompleteReasons()).isEmpty();
        });
    }

    @Test
    void kvLc11CallbackFailureRemainsPrimaryWhenRootCloseFailsFatally() {
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicInteger sinkCalls = new AtomicInteger();
        RuntimeException businessFailure = new IllegalArgumentException("business failure");
        TestFatalError closeFailure = new TestFatalError();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(session -> {
                    sinkCalls.incrementAndGet();
                    throw closeFailure;
                }),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));

        Throwable actual = catchThrowable(() -> {
            try (Stage ignored = Tfi.begin("fatal-close")) {
                Tfi.call("business", () -> {
                    callbackCalls.incrementAndGet();
                    throw businessFailure;
                });
            }
        });

        assertThat(actual).isSameAs(businessFailure);
        assertThat(actual.getSuppressed()).containsExactly(closeFailure);
        assertThat(callbackCalls).hasValue(1);
        assertThat(sinkCalls).hasValue(1);
    }

    @Test
    void kvLc12OrdinarySamplerAndIdFailuresDoNotSkipCallbacks() {
        List<FlowSession> sessions = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                name -> {
                    throw new IllegalStateException("sampler failure");
                },
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));

        try (Stage ignored = Tfi.begin("sampler-failure")) {
            Tfi.stage("business-one", callbacks::incrementAndGet);
        }

        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                () -> {
                    throw new IllegalStateException("id failure");
                },
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));

        try (Stage ignored = Tfi.begin("id-failure")) {
            Tfi.stage("business-two", callbacks::incrementAndGet);
        }

        assertThat(callbacks).hasValue(2);
        assertThat(sessions).isEmpty();
    }

    @Test
    void kvLc13InvalidFunctionalNamesDoNotChangeBusinessResultsOrFailures() {
        List<FlowSession> sessions = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        configureSinks(List.of(sessions::add));
        Object expectedResult = new Object();
        RuntimeException expectedFailure = new IllegalArgumentException("business failure");

        try (Stage ignored = Tfi.begin("valid-root")) {
            Object actualResult = Tfi.call(" ", () -> {
                callbacks.incrementAndGet();
                return expectedResult;
            });
            Throwable actualFailure = catchThrowable(() -> Tfi.stage("\t", () -> {
                callbacks.incrementAndGet();
                throw expectedFailure;
            }));

            assertThat(actualResult).isSameAs(expectedResult);
            assertThat(actualFailure).isSameAs(expectedFailure);
        }

        assertThat(callbacks).hasValue(2);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(FlowStatus.OK);
            assertThat(session.root().records()).isEmpty();
            assertThat(session.root().children()).isEmpty();
        });
    }

    @Test
    void kvLc14WrappedCallableKeepsCheckedIdentityAndRestoresInterrupt() {
        List<FlowSession> sessions = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                () -> "session-" + ids.incrementAndGet(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
        IOException ioFailure = new IOException("checked failure");
        InterruptedException interruptedFailure = new InterruptedException("cancelled");

        try (Stage ignored = Tfi.begin("parent")) {
            ContextHandle handle = Tfi.capture();
            Callable<Object> ioAction = () -> {
                throw ioFailure;
            };
            Throwable actualIo = catchThrowable(handle.wrap(ioAction)::call);
            assertThat(actualIo).isSameAs(ioFailure);

            Thread.interrupted();
            Callable<Object> interruptedAction = () -> {
                throw interruptedFailure;
            };
            Throwable actualInterrupted = catchThrowable(handle.wrap(interruptedAction)::call);
            assertThat(actualInterrupted).isSameAs(interruptedFailure);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Thread.interrupted();
        }

        assertThat(sessions).hasSize(3);
        assertThat(sessions.subList(0, 2)).allSatisfy(child -> {
            assertThat(child.parentSessionId()).isEqualTo("session-1");
            assertThat(child.status()).isEqualTo(FlowStatus.ERROR);
            assertThat(child.root().records()).singleElement().satisfies(record ->
                    assertThat(record.code()).isEqualTo("CALLBACK_ERROR"));
        });
        assertThat(sessions.get(0).sessionId()).isEqualTo("session-2");
        assertThat(sessions.get(1).sessionId()).isEqualTo("session-3");
        assertThat(sessions.get(2).sessionId()).isEqualTo("session-1");
        assertThat(sessions.get(2).status()).isEqualTo(FlowStatus.OK);
    }

    private static void configureSinks(List<FlowSink> sinks) {
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                sinks,
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
    }

    private static final class FixedClock implements KernelClock {
        private final long wallTime;

        private FixedClock(long wallTime) {
            this.wallTime = wallTime;
        }

        @Override
        public long wallTimeMillis() {
            return wallTime;
        }

        @Override
        public long monotonicNanos() {
            return 0L;
        }
    }

    private static final class TestFatalError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
