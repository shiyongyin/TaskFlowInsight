package com.syy.tfi.kernel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.KernelClock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 固化实例 Runtime 的实现所有权及静态门面的兼容委托，不重复测试既有业务合同全集。
 */
class KernelRuntimeOwnerContractTest {

    @AfterEach
    void resetDefaultRuntime() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void directRuntimeAndStaticFacadeProduceTheSameFactsAndCallbackSemantics() {
        List<String> directSessions = new ArrayList<>();
        KernelRuntime direct = KernelRuntime.create(config(directSessions));
        Object expectedResult = new Object();
        IllegalStateException expectedFailure = new IllegalStateException("rejected");

        try (Stage ignored = direct.begin("order.submit")) {
            direct.capture().wrap((Runnable) () -> direct.message("linked")).run();
            assertThat(direct.call("inventory.reserve", () -> expectedResult)).isSameAs(expectedResult);
            direct.message("reserved");
            assertThatThrownBy(() -> direct.stage("payment.charge", () -> {
                throw expectedFailure;
            })).isSameAs(expectedFailure);
        }

        List<String> staticSessions = new ArrayList<>();
        Tfi.configure(config(staticSessions));
        try (Stage ignored = Tfi.begin("order.submit")) {
            Tfi.capture().wrap((Runnable) () -> Tfi.message("linked")).run();
            assertThat(Tfi.call("inventory.reserve", () -> expectedResult)).isSameAs(expectedResult);
            Tfi.message("reserved");
            assertThatThrownBy(() -> Tfi.stage("payment.charge", () -> {
                throw expectedFailure;
            })).isSameAs(expectedFailure);
        }

        assertThat(directSessions).hasSize(2);
        assertThat(directSessions.getFirst())
                .contains("\"parentSessionId\":\"SESSION\"")
                .contains("\"text\":\"linked\"");
        assertThat(directSessions.getLast())
                .contains("\"parentSessionId\":null")
                .contains("\"name\":\"inventory.reserve\"")
                .contains("\"text\":\"reserved\"");
        assertThat(directSessions).containsExactlyElementsOf(staticSessions);
    }

    @Test
    void closeIsTerminalAndLateWrappersStillExecuteBusinessActionsOnce() {
        List<String> sessions = new ArrayList<>();
        KernelRuntime runtime = KernelRuntime.create(config(sessions));
        Stage root = runtime.begin("retiring");
        ContextHandle captured = runtime.capture();
        AtomicInteger actions = new AtomicInteger();

        runtime.close();
        runtime.close();
        runtime.setEnabled(true);
        root.close();
        captured.wrap((Runnable) actions::incrementAndGet).run();
        runtime.stage("late-stage", actions::incrementAndGet);

        assertThat(runtime.isEnabled()).isFalse();
        assertThat(runtime.begin("late-root").remainingEncodedBytes()).isEqualTo(-1);
        assertThat(actions).hasValue(2);
        assertThat(sessions).isEmpty();
    }

    @Test
    void invalidBootPropertyFailsWhenToJsonIsTheOnlyTfiCall() throws Exception {
        String classPath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dtfi.kernel.enabled=invalid",
                "-cp",
                classPath,
                BootFloorProbe.class.getName())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).isNotZero();
        assertThat(output).contains("tfi.kernel.enabled must be true or false");
    }

    private static KernelConfig config(List<String> completedSessions) {
        return new KernelConfig(
                true,
                List.of(session -> completedSessions.add(SessionJsonWriter.write(session))),
                name -> true,
                () -> "SESSION",
                new FixedClock(),
                64,
                12_288,
                2_048,
                32);
    }

    /** 子进程只触发 Tfi 的纯转换入口，验证类初始化仍保留 boot-floor 校验。 */
    public static final class BootFloorProbe {
        private BootFloorProbe() {
        }

        /** 子进程入口；非法属性必须在参数 null 校验前使 Tfi 初始化失败。 */
        public static void main(String[] arguments) {
            FlowSession session = null;
            Tfi.toJson(session);
        }
    }

    /** 让 direct/static 场景生成完全相同的确定性时间事实。 */
    private static final class FixedClock implements KernelClock {
        @Override
        public long wallTimeMillis() {
            return 1_000L;
        }

        @Override
        public long monotonicNanos() {
            return 1_000_000L;
        }
    }
}
