package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.RecordType;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证启动期 fail-fast 与动态输入 fail-soft 的分界。
 */
class KernelBoundaryContractTest {

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kernelConfigRejectsNullsAndEveryBudgetBoundaryViolation() {
        KernelConfig base = KernelConfig.defaults();
        assertThatThrownBy(() -> config(base, null, 1, 1, 1, 0)).isInstanceOf(NullPointerException.class);
        List<FlowSink> nullSink = Arrays.asList((FlowSink) null);
        assertThatThrownBy(() -> config(base, nullSink, 1, 1, 1, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KernelConfig(true, List.of(), null, base.idGenerator(), base.clock(), 1, 1, 1, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KernelConfig(true, List.of(), base.sampler(), null, base.clock(), 1, 1, 1, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KernelConfig(true, List.of(), base.sampler(), base.idGenerator(), null, 1, 1, 1, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config(base, List.of(), 0, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1_025, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 1_048_577, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 65_536, 65_537, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 2, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 2, 1, 257)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(base, List.of(), 1, 1, 2, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rateSamplerUsesABoundedWindowAndDoesNotResetOnClockRollback() {
        MutableWallClock clock = new MutableWallClock(1_000L);
        Sampler sampler = Sampler.rateLimited(1, clock);
        assertThat(sampler.shouldRecord("first")).isTrue();
        assertThat(sampler.shouldRecord("second")).isFalse();
        clock.wallTime = 2_000L;
        assertThat(sampler.shouldRecord("next-window")).isTrue();
        clock.wallTime = 1_000L;
        assertThat(sampler.shouldRecord("rollback")).isFalse();
        assertThatThrownBy(() -> Sampler.rateLimited(0, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sampler.rateLimited(1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void invalidDynamicValuesAreNoopAndDoNotMutateTheAcceptedSession() {
        List<FlowSession> sessions = new ArrayList<>();
        configureSink(sessions, KernelConfig.defaults());
        assertThat(Tfi.begin(null).remainingEncodedBytes()).isEqualTo(-1);
        assertThat(Tfi.begin("\uD800").remainingEncodedBytes()).isEqualTo(-1);

        Stage root = Tfi.begin("valid");
        root.attr(" ", "value");
        root.message("");
        root.change("\t", "before", "after");
        root.error(null);
        root.error(null, null);
        assertThat(root.record(null, "CODE", null, Map.of())).isFalse();
        assertThat(root.record(RecordType.MESSAGE, " ", null, Map.of())).isFalse();
        assertThat(root.record(RecordType.MESSAGE, "CODE", null, null)).isFalse();
        assertThat(root.record(RecordType.MESSAGE, "CODE", "\uDC00", Map.of())).isFalse();
        root.close();
        root.attr("closed", true).message("closed");

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.attrs()).isEmpty();
            assertThat(session.root().records()).isEmpty();
        });
    }

    @Test
    void invalidIdsDegradeWhileFatalSamplerAndIdFailuresPropagateUnchanged() {
        KernelConfig base = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(true, List.of(), base.sampler(), () -> null, base.clock(), 1, 2, 1, 0));
        assertThat(Tfi.begin("null-id").remainingEncodedBytes()).isEqualTo(-1);
        Tfi.configure(new KernelConfig(true, List.of(), base.sampler(), () -> " ", base.clock(), 1, 2, 1, 0));
        assertThat(Tfi.begin("blank-id").remainingEncodedBytes()).isEqualTo(-1);

        TestFatalError samplerFailure = new TestFatalError();
        Tfi.configure(new KernelConfig(true, List.of(), name -> {
            throw samplerFailure;
        }, base.idGenerator(), base.clock(), 1, 2, 1, 0));
        assertThatThrownBy(() -> Tfi.begin("fatal-sampler")).isSameAs(samplerFailure);
        TestFatalError idFailure = new TestFatalError();
        Tfi.configure(new KernelConfig(true, List.of(), base.sampler(), () -> {
            throw idFailure;
        }, base.clock(), 1, 2, 1, 0));
        assertThatThrownBy(() -> Tfi.begin("fatal-id")).isSameAs(idFailure);
    }

    private static KernelConfig config(
            KernelConfig base, List<FlowSink> sinks, int stages, int sessionBytes, int recordBytes, int attrs) {
        return new KernelConfig(true, sinks, base.sampler(), base.idGenerator(), base.clock(),
                stages, sessionBytes, recordBytes, attrs);
    }

    private static void configureSink(List<FlowSession> sessions, KernelConfig base) {
        Tfi.configure(new KernelConfig(true, List.of(sessions::add), base.sampler(), base.idGenerator(), base.clock(),
                base.maxStages(), base.maxSessionEncodedBytes(), base.maxRecordEncodedBytes(), base.maxAttrs()));
    }

    private static final class MutableWallClock implements KernelClock {
        private long wallTime;

        private MutableWallClock(long wallTime) {
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
