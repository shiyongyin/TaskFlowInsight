package com.syy.tfi.kernel.benchmark;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import com.syy.tfi.kernel.model.RecordType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * 内核身份热路径；输入已构造，避免把业务字符串拼装成本算进门面。
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Threads(1)
public class KernelBenchmarks {
    private static final String MESSAGE_32 = "0123456789abcdefghijklmnopqrstuv";
    private static final String CHANGE_BEFORE = "PENDING";
    private static final String CHANGE_AFTER = "COMPLETED";

    /** disabled 路径在每个 iteration 前清理全局状态。 */
    @State(Scope.Thread)
    public static class DisabledState {

        /** 关闭记录并移除当前线程上下文。 */
        @Setup(Level.Iteration)
        public void setup() {
            Tfi.clear();
            Tfi.configure(KernelConfig.defaults());
            Tfi.setEnabled(false);
        }

        /** 恢复全局开关，避免 fork 内其他 benchmark 受污染。 */
        @TearDown(Level.Iteration)
        public void teardown() {
            Tfi.setEnabled(true);
            Tfi.clear();
        }
    }

    /** direct Runtime 的 disabled 路径，业务输入与静态组完全一致。 */
    @State(Scope.Thread)
    public static class DirectDisabledState {
        /** 当前 iteration 独享且已关闭记录的 Runtime。 */
        private KernelRuntime runtime;

        /** 创建新 Runtime 并关闭记录，初始化成本不计入被测操作。 */
        @Setup(Level.Iteration)
        public void setup() {
            runtime = KernelRuntime.create(KernelConfig.defaults());
            runtime.setEnabled(false);
        }

        /** 终结本 iteration 的实例状态。 */
        @TearDown(Level.Iteration)
        public void teardown() {
            runtime.close();
        }
    }

    /** 为每次采样创建独立根 Session，setup/teardown 成本不计入被测操作。 */
    @State(Scope.Thread)
    public static class ActiveState {
        private Stage root;

        /** 每个 trial 只创建一次默认 SPI，避免 setup 自身产生无关 SecureRandom 成本。 */
        @Setup(Level.Trial)
        public void configure() {
            Tfi.clear();
            Tfi.setEnabled(true);
            Tfi.configure(KernelConfig.defaults());
        }

        /** 创建无 Sink 的活动根 Session。 */
        @Setup(Level.Invocation)
        public void setup() {
            root = Tfi.begin("benchmark-root");
        }

        /** 关闭根 Session 并清除 ThreadLocal。 */
        @TearDown(Level.Invocation)
        public void teardown() {
            root.close();
            Tfi.clear();
        }
    }

    /** direct Runtime 的活动路径，Session 和业务输入与静态组完全一致。 */
    @State(Scope.Thread)
    public static class DirectActiveState {
        /** 当前 trial 独享且配置冻结的 Runtime。 */
        private KernelRuntime runtime;
        /** 当前 invocation 的根 Stage。 */
        private Stage root;

        /** trial 内只创建一次默认 SPI，避免把 Runtime 创建成本从热路径扣除后另行估算。 */
        @Setup(Level.Trial)
        public void configure() {
            runtime = KernelRuntime.create(KernelConfig.defaults());
        }

        /** 创建无 Sink 的活动根 Session。 */
        @Setup(Level.Invocation)
        public void setup() {
            root = runtime.begin("benchmark-root");
        }

        /** 关闭根 Session。 */
        @TearDown(Level.Invocation)
        public void teardown() {
            root.close();
            runtime.clear();
        }

        /** trial 结束时不可逆退役 Runtime。 */
        @TearDown(Level.Trial)
        public void closeRuntime() {
            runtime.close();
        }
    }

    /** 预构造不同长度的消息，单独观察编码成本随输入大小的变化。 */
    @State(Scope.Thread)
    public static class MessageSizeState {
        @Param({"32", "256", "1024"})
        private int characters;
        private String message;

        /** 字符串构造不计入 message 热路径。 */
        @Setup(Level.Trial)
        public void setup() {
            message = "x".repeat(characters);
        }
    }

    /** 预构造不同规模的 structured data，避免把业务 Map 组装计入内核成本。 */
    @State(Scope.Thread)
    public static class StructuredDataSizeState {
        @Param({"1", "8", "32"})
        private int entries;
        private Map<String, Object> data;

        /** 使用稳定插入顺序构造输入；内核仍需执行自己的 UTF-8 排序与深复制。 */
        @Setup(Level.Trial)
        public void setup() {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = entries - 1; index >= 0; index--) {
                values.put("field-" + index, index);
            }
            data = values;
        }
    }

    /** 测量 disabled 下预构造消息的门面短路。 */
    @Benchmark
    public void disabledMessage(DisabledState state) {
        Tfi.message(MESSAGE_32);
    }

    /** 测量 direct Runtime disabled 下相同预构造消息的短路。 */
    @Benchmark
    public void directDisabledMessage(DirectDisabledState state) {
        state.runtime.message(MESSAGE_32);
    }

    /** 测量活动 Session 内创建并关闭空 Stage。 */
    @Benchmark
    public void stageClose(ActiveState state) {
        try (Stage ignored = Tfi.stage("benchmark-stage")) {
            // 只测作用域生命周期。
        }
    }

    /** 测量 direct Runtime 内创建并关闭相同空 Stage。 */
    @Benchmark
    public void directStageClose(DirectActiveState state) {
        try (Stage ignored = state.runtime.stage("benchmark-stage")) {
            // 只测作用域生命周期。
        }
    }

    /** 测量活动 Session 内接纳一条 32 字符预构造消息。 */
    @Benchmark
    public void message32(ActiveState state) {
        Tfi.message(MESSAGE_32);
    }

    /** 测量 direct Runtime 接纳相同 32 字符消息。 */
    @Benchmark
    public void directMessage32(DirectActiveState state) {
        state.runtime.message(MESSAGE_32);
    }

    /** 测量消息成本随预构造输入长度的变化，结果只作分档事实，不外推固定阈值。 */
    @Benchmark
    public void messageBySize(ActiveState active, MessageSizeState input) {
        Tfi.message(input.message);
    }

    /** 测量 direct Runtime 对相同分档消息输入的接纳成本。 */
    @Benchmark
    public void directMessageBySize(DirectActiveState active, MessageSizeState input) {
        active.runtime.message(input.message);
    }

    /** 为两侧短 scalar 的显式 change 建立独立基线。 */
    @Benchmark
    public void changeScalar(ActiveState state) {
        Tfi.change("order.status", CHANGE_BEFORE, CHANGE_AFTER);
    }

    /** 测量 direct Runtime 对相同 scalar change 的接纳成本。 */
    @Benchmark
    public void directChangeScalar(DirectActiveState state) {
        state.runtime.change("order.status", CHANGE_BEFORE, CHANGE_AFTER);
    }

    /** 测量 structured data 排序、深复制和编码记账随字段数量的变化。 */
    @Benchmark
    public void structuredDataBySize(ActiveState active, StructuredDataSizeState input) {
        active.root.record(RecordType.MESSAGE, "BENCHMARK_DATA", null, input.data);
    }

    /** 测量 direct Runtime 对相同 structured data 的排序、深复制和预算记账。 */
    @Benchmark
    public void directStructuredDataBySize(DirectActiveState active, StructuredDataSizeState input) {
        active.root.record(RecordType.MESSAGE, "BENCHMARK_DATA", null, input.data);
    }
}
