package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.model.RecordType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 Stage 公共合同、Noop 透明性和会话边界，不断言 KNL-02 的编码细节。
 */
class KernelStageContractTest {

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void manualStageOperationsFreezeAReadOnlyErrorFlow() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, KernelConfig.defaults(), 64, 32);

        try (Stage root = Tfi.begin("manual-flow")) {
            assertThat(root.attr("requestId", "R1")).isSameAs(root);
            try (Stage child = Tfi.stage("manual-stage")) {
                child.attr("attempt", 1);
                child.message("message");
                child.change("state", null, "DONE");
                child.error("plain-error");
                child.error("typed-error", new IllegalStateException("broken"));
                child.error("null-falls-back", null);
                assertThat(child.record(RecordType.MESSAGE, "CUSTOM", null, Map.of("value", 1))).isTrue();
                assertThat(child.remainingEncodedBytes()).isPositive();
            }
        }

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.status()).isEqualTo(FlowStatus.ERROR);
            assertThat(session.attrs()).containsEntry("requestId", "R1");
            assertThatThrownBy(() -> session.attrs().put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(session.root().children()).singleElement().satisfies(child -> {
                assertThat(child.attrs()).containsEntry("attempt", 1);
                assertThat(child.records()).hasSize(6);
                assertThat(child.records().get(1).data()).containsEntry("path", "state");
                assertThatThrownBy(() -> child.records().clear())
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> child.records().get(0).data().put("x", "y"))
                        .isInstanceOf(UnsupportedOperationException.class);
            });
        });
    }

    @Test
    void noopAndEmptyHandleKeepAllBusinessOperationsTransparent() throws Exception {
        Stage noop = Tfi.stage("without-session");
        assertThat(noop.attr("key", "value")).isSameAs(noop);
        noop.message("message");
        noop.change("path", "before", "after");
        noop.error("error");
        noop.error("error", new IllegalStateException());
        assertThat(noop.record(RecordType.MESSAGE, "CODE", null, Map.of())).isFalse();
        assertThat(noop.remainingEncodedBytes()).isEqualTo(-1);
        noop.close();

        AtomicInteger calls = new AtomicInteger();
        ContextHandle empty = Tfi.capture();
        Runnable runnable = calls::incrementAndGet;
        empty.wrap(runnable).run();
        Object expected = new Object();
        Callable<Object> callable = () -> expected;
        assertThat(empty.wrap(callable).call()).isSameAs(expected);
        assertThat(calls).hasValue(1);
        assertThat(Tfi.currentToJson()).isEmpty();
        assertThat(Tfi.currentToConsole()).isEmpty();
        Tfi.clear();

        Tfi.setEnabled(false);
        assertThat(Tfi.isEnabled()).isFalse();
        assertThat(Tfi.begin("disabled").remainingEncodedBytes()).isEqualTo(-1);
        assertThatThrownBy(() -> Tfi.stage("null", (Runnable) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Tfi.call("null", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Tfi.configure(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void disablingAnActiveSessionIsPermanentButPublishesItsAcceptedPrefix() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, KernelConfig.defaults(), 64, 32);
        Stage root = Tfi.begin("disable-mid-session");
        root.attr("accepted", true);

        Tfi.setEnabled(false);
        assertThat(Tfi.isEnabled()).isFalse();
        assertThat(root.remainingEncodedBytes()).isZero();
        Tfi.setEnabled(true);
        assertThat(Tfi.isEnabled()).isFalse();
        Tfi.message("rejected");
        root.close();

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.attrs()).containsEntry("accepted", true);
            assertThat(session.root().records()).isEmpty();
            assertThat(session.incompleteReasons()).containsExactly("DISABLED_MID_SESSION");
        });
    }

    @Test
    void attributeAndStackLimitsRejectAtomicallyAndReportStableReasons() {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, KernelConfig.defaults(), 1_024, 0);
        Stage root = Tfi.begin("bounded-flow");
        root.attr("rejected", "value");
        for (int index = 0; index < 63; index++) {
            assertThat(Tfi.stage("level-" + index).remainingEncodedBytes()).isPositive();
        }
        assertThat(Tfi.stage("too-deep").remainingEncodedBytes()).isEqualTo(-1);
        root.close();

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.attrs()).isEmpty();
            assertThat(session.incompleteReasons())
                    .containsExactly("STACK_DEPTH_LIMIT", "ATTR_LIMIT", "NON_LIFO_CLOSE");
        });
    }

    private static void configure(
            List<FlowSession> sessions, KernelConfig base, int maxStages, int maxAttrs) {
        Tfi.configure(new KernelConfig(
                true, List.of(sessions::add), base.sampler(), base.idGenerator(), base.clock(),
                maxStages, base.maxSessionEncodedBytes(), base.maxRecordEncodedBytes(), maxAttrs));
    }
}
