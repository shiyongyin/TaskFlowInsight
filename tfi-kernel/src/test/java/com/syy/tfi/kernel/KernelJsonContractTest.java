package com.syy.tfi.kernel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 固定机器 JSON golden，并把活动态 renderer 与终态发布语义分开验证。
 */
class KernelJsonContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kvJson01FinalSessionExactlyMatchesThe384ByteGolden() throws Exception {
        AtomicReference<String> exported = new AtomicReference<>();
        AtomicInteger sinkCalls = new AtomicInteger();
        FlowSink sink = session -> {
            sinkCalls.incrementAndGet();
            exported.set(Tfi.toJson(session));
        };
        Tfi.configure(new KernelConfig(
                true, List.of(sink), name -> true, () -> "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                new SequenceClock(new long[] {1_000L, 1_001L}, new long[] {0L, 2_000_000L}),
                64, 12_288, 2_048, 32));

        try (Stage root = Tfi.begin("demo")) {
            root.attr("requestId", "R1");
            root.message("ok");
        }

        String expected = "{\"schema\":\"tfi-flow/1\","
                + "\"sessionId\":\"01ARZ3NDEKTSV4RRFFQ69G5FAV\",\"parentSessionId\":null,"
                + "\"name\":\"demo\",\"status\":\"OK\",\"startMs\":1000,\"durMs\":2,"
                + "\"truncated\":false,\"incompleteReasons\":[],\"attrs\":{\"requestId\":\"R1\"},"
                + "\"root\":{\"name\":\"demo\",\"status\":\"OK\",\"startMs\":1000,\"durMs\":2,"
                + "\"attrs\":{},\"records\":[{\"type\":\"MESSAGE\",\"code\":\"MANUAL_MESSAGE\","
                + "\"text\":\"ok\",\"data\":{},\"atMs\":1001}],\"children\":[]}}";
        assertThat(exported.get()).isEqualTo(expected);
        assertThat(sinkCalls).hasValue(1);
        assertThat(exported.get().getBytes(UTF_8)).hasSize(384);
        assertThat(JSON.readTree(exported.get()).path("schema").asText()).isEqualTo("tfi-flow/1");
    }

    @Test
    void kvJson02SinkCanRenderFinalCanonicalJsonWithoutAmbientContext() throws Exception {
        List<String> exported = new ArrayList<>();
        List<String> ambient = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger();
        FlowSink sink = session -> {
            ambient.add(Tfi.currentToJson());
            exported.add(Tfi.toJson(session));
        };
        Tfi.configure(new KernelConfig(
                true, List.of(sink), name -> true, () -> "FINAL-" + ids.incrementAndGet(),
                new ConstantClock(), 64, 12_288, 2_048, 32));

        try (Stage root = Tfi.begin("final-ok")) {
            root.message("accepted");
        }
        try (Stage root = Tfi.begin("final-error")) {
            root.error("rejected");
        }

        assertThat(ambient).containsExactly("", "");
        assertThat(exported).hasSize(2);
        var ok = JSON.readTree(exported.get(0));
        var error = JSON.readTree(exported.get(1));
        assertThat(ok.path("schema").asText()).isEqualTo("tfi-flow/1");
        assertThat(ok.path("sessionId").asText()).isEqualTo("FINAL-1");
        assertThat(ok.path("status").asText()).isEqualTo("OK");
        assertThat(ok.path("root").path("records")).hasSize(1);
        assertThat(error.path("sessionId").asText()).isEqualTo("FINAL-2");
        assertThat(error.path("status").asText()).isEqualTo("ERROR");
        assertThatNullPointerException().isThrownBy(() -> Tfi.toJson(null));
    }

    @Test
    void currentRenderersReadRunningStateWithoutClosingOrPublishing() throws Exception {
        AtomicInteger sinkCalls = new AtomicInteger();
        Tfi.configure(new KernelConfig(
                true, List.of(session -> sinkCalls.incrementAndGet()), name -> true, () -> "S",
                new ConstantClock(), 64, 12_288, 2_048, 32));

        Stage root = Tfi.begin("current-flow");
        Tfi.message("visible-now");
        String firstJson = Tfi.currentToJson();
        String secondJson = Tfi.currentToJson();
        String console = Tfi.currentToConsole();

        assertThat(firstJson).isEqualTo(secondJson);
        assertThat(JSON.readTree(firstJson).path("status").asText()).isEqualTo("RUNNING");
        assertThat(console).contains("current-flow", "RUNNING", "visible-now");
        assertThat(sinkCalls).hasValue(0);
        root.close();
        assertThat(sinkCalls).hasValue(1);
        assertThat(Tfi.currentToJson()).isEmpty();
        assertThat(Tfi.currentToConsole()).isEmpty();
    }

    @Test
    void currentConsoleUsesUnicodeTreeConnectorsAndIcons() {
        Tfi.configure(new KernelConfig(
                true, List.of(), name -> true, () -> "S",
                new ConstantClock(), 64, 12_288, 2_048, 32));

        try (Stage root = Tfi.begin("current-flow")) {
            root.message("root-message");
            try (Stage first = Tfi.stage("first")) {
                first.message("first-message");
            }
            try (Stage second = Tfi.stage("second")) {
                second.message("second-message");
            }

            String console = Tfi.currentToConsole();

            assertThat(console)
                    .startsWith("\uD83D\uDCCB current-flow [RUNNING] 0ms\n")
                    .contains("\u251C\u2500\u2500 \uD83D\uDCAC MESSAGE/MANUAL_MESSAGE: root-message\n")
                    .contains("\u251C\u2500\u2500 \uD83D\uDD27 first [OK] 0ms\n")
                    .contains("\u2502   \u2514\u2500\u2500 \uD83D\uDCAC MESSAGE/MANUAL_MESSAGE: first-message\n")
                    .contains("\u2514\u2500\u2500 \uD83D\uDD27 second [OK] 0ms\n")
                    .contains("    \u2514\u2500\u2500 \uD83D\uDCAC MESSAGE/MANUAL_MESSAGE: second-message\n");
        }
    }

    private static final class SequenceClock implements KernelClock {
        private final long[] wallTimes;
        private final long[] monotonicTimes;
        private int wallIndex;
        private int monotonicIndex;

        private SequenceClock(long[] wallTimes, long[] monotonicTimes) {
            this.wallTimes = wallTimes;
            this.monotonicTimes = monotonicTimes;
        }

        @Override
        public long wallTimeMillis() {
            return wallTimes[wallIndex++];
        }

        @Override
        public long monotonicNanos() {
            return monotonicTimes[monotonicIndex++];
        }
    }

    private static final class ConstantClock implements KernelClock {
        @Override public long wallTimeMillis() { return 1L; }
        @Override public long monotonicNanos() { return 1L; }
    }
}
