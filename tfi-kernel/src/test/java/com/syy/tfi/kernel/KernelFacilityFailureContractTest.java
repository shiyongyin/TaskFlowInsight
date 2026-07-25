package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 Clock 与异常详情提取失败不会改变业务 callback 的控制流。
 */
class KernelFacilityFailureContractTest {

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void ordinaryClockFailuresAtRootStageAndCloseDegradeWithoutEscaping() {
        List<FlowSession> sessions = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        configure(sessions, new FailingClock(1, 0));
        try (Stage ignored = Tfi.begin("root-clock-failure")) {
            Tfi.stage("business", callbacks::incrementAndGet);
        }
        assertThat(callbacks).hasValue(1);
        assertThat(sessions).isEmpty();

        configure(sessions, new FailingClock(2, 0));
        try (Stage root = Tfi.begin("stage-clock-failure")) {
            Tfi.stage("business", callbacks::incrementAndGet);
            root.message("accepted-after-stage-failure");
        }
        assertThat(callbacks).hasValue(2);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().children()).isEmpty();
            assertThat(session.root().records()).hasSize(1);
            assertThat(session.incompleteReasons()).containsExactly("RECORDING_FAILURE");
        });

        sessions.clear();
        configure(sessions, new FailingClock(0, 2));
        Throwable closeFailure = catchThrowable(() -> {
            try (Stage ignored = Tfi.begin("close-clock-failure")) {
                Tfi.message("accepted");
            }
        });
        assertThat(closeFailure).isNull();
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.durMs()).isZero();
            assertThat(session.incompleteReasons()).containsExactly("RECORDING_FAILURE");
        });
    }

    @Test
    void callbackFailureIdentityAndErrorStatusSurviveBrokenThrowableMessages() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, new FailingClock(0, 0));
        RuntimeException expected = new BrokenMessageException(false);

        Throwable actual = catchThrowable(() -> {
            try (Stage ignored = Tfi.begin("broken-message")) {
                Tfi.call("callback", () -> {
                    throw expected;
                });
            }
        });

        assertThat(actual).isSameAs(expected);
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(FlowStatus.ERROR);
            assertThat(session.incompleteReasons()).containsExactly("RECORDING_FAILURE");
            assertThat(session.root().children()).singleElement().satisfies(child -> {
                assertThat(child.status()).isEqualTo(FlowStatus.ERROR);
                assertThat(child.records()).isEmpty();
            });
        });
    }

    @Test
    void fatalFailureWhileCapturingCallbackDetailsBecomesSuppressed() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, new FailingClock(0, 0));
        BrokenMessageException expected = new BrokenMessageException(true);

        Throwable actual = catchThrowable(() -> {
            try (Stage ignored = Tfi.begin("fatal-message")) {
                Tfi.stage("callback", () -> {
                    throw expected;
                });
            }
        });

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getSuppressed()).singleElement().isInstanceOf(TestFatalError.class);
        assertThat(sessions).singleElement().satisfies(session ->
                assertThat(session.status()).isEqualTo(FlowStatus.ERROR));
    }

    private static void configure(List<FlowSession> sessions, KernelClock clock) {
        KernelConfig base = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true, List.of(sessions::add), base.sampler(), () -> "session-id", clock,
                base.maxStages(), base.maxSessionEncodedBytes(), base.maxRecordEncodedBytes(), base.maxAttrs()));
    }

    private static final class FailingClock implements KernelClock {
        private final int failingWallCall;
        private final int failingMonotonicCall;
        private int wallCalls;
        private int monotonicCalls;

        private FailingClock(int failingWallCall, int failingMonotonicCall) {
            this.failingWallCall = failingWallCall;
            this.failingMonotonicCall = failingMonotonicCall;
        }

        @Override
        public long wallTimeMillis() {
            if (++wallCalls == failingWallCall) {
                throw new IllegalStateException("wall clock failure");
            }
            return 1_000L;
        }

        @Override
        public long monotonicNanos() {
            if (++monotonicCalls == failingMonotonicCall) {
                throw new IllegalStateException("monotonic clock failure");
            }
            return 0L;
        }
    }

    private static final class BrokenMessageException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final boolean fatal;

        private BrokenMessageException(boolean fatal) {
            this.fatal = fatal;
        }

        @Override
        public String getMessage() {
            if (fatal) {
                throw new TestFatalError();
            }
            throw new IllegalStateException("message unavailable");
        }
    }

    private static final class TestFatalError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
