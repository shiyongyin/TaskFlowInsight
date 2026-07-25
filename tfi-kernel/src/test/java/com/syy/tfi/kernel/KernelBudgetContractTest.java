package com.syy.tfi.kernel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * KNL-02 批次 A 的 Record 与 Session 编码预算边界。
 */
class KernelBudgetContractTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kvBg01SeventySixByteRecordIsRejectedOnlyBelowItsExactBoundary() {
        FlowSession below = sessionWithMessage(75);
        FlowSession exact = sessionWithMessage(76);
        FlowSession above = sessionWithMessage(77);

        assertThat(below.root().records()).isEmpty();
        assertThat(below.incompleteReasons()).containsExactly("RECORD_BYTES_LIMIT");
        assertThat(exact.root().records()).hasSize(1);
        assertThat(above.root().records()).hasSize(1);
    }

    @Test
    void kvBg02FirstSessionByteRejectionMakesAllLaterAppendsNoop() throws Exception {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, 700, 400);
        String rejected;
        String afterLaterAppend;
        try (Stage root = Tfi.begin("session-budget")) {
            root.message("x".repeat(300));
            rejected = Tfi.currentToJson();
            root.message("small-later-message");
            root.attr("later", "value");
            Tfi.stage("later-stage").close();
            afterLaterAppend = Tfi.currentToJson();
        }

        assertThat(afterLaterAppend).isEqualTo(rejected);
        assertThat(rejected.getBytes(UTF_8).length).isLessThanOrEqualTo(700);
        assertThat(JSON.readTree(rejected).isObject()).isTrue();
        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.root().records()).isEmpty();
            assertThat(session.attrs()).isEmpty();
            assertThat(session.root().children()).isEmpty();
            assertThat(session.incompleteReasons()).containsExactly("SESSION_BYTES_LIMIT");
        });
    }

    private static FlowSession sessionWithMessage(int recordBytes) {
        List<FlowSession> sessions = new ArrayList<>();
        configure(sessions, 2_000, recordBytes);
        try (Stage root = Tfi.begin("record-budget")) {
            root.message("12345");
        }
        return sessions.get(0);
    }

    private static void configure(List<FlowSession> sessions, int sessionBytes, int recordBytes) {
        Tfi.configure(new KernelConfig(
                true, List.of(sessions::add), name -> true, () -> "S", new FixedClock(),
                64, sessionBytes, recordBytes, 32));
    }

    private static final class FixedClock implements KernelClock {
        @Override public long wallTimeMillis() { return 0L; }
        @Override public long monotonicNanos() { return 0L; }
    }
}
