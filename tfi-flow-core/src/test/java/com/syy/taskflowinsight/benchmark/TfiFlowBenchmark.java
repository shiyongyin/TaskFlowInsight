package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ContextSnapshot;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * JMH performance benchmarks for TFI Flow Core (BM-001 through BM-010).
 *
 * <p>Covers all benchmark scenarios defined in the test plan,
 * measuring throughput (ops/sec) for core TFI operations.
 *
 * <h3>Running benchmarks</h3>
 * <pre>{@code
 * # Via Maven perf profile
 * mvn test -Dtfi.perf.enabled=true \
 *     -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner
 *
 * # Run specific benchmark by regex
 * mvn test -Dtfi.perf.enabled=true \
 *     -Dexec.mainClass=com.syy.taskflowinsight.benchmark.BenchmarkRunner \
 *     -Dexec.args="bm001"
 * }</pre>
 *
 * @since 4.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Threads(1)
public class TfiFlowBenchmark {

    // ==================== State Classes ====================

    /**
     * Base state: enables TfiFlow and clears per-thread context each iteration.
     */
    @State(Scope.Thread)
    public static class BaseState {
        /** Enable TfiFlow and clear thread-local state. */
        @Setup(Level.Iteration)
        public void setup() {
            TfiFlow.enable();
            TfiFlow.clear();
        }

        /** Clean up thread-local state after iteration. */
        @TearDown(Level.Iteration)
        public void teardown() {
            TfiFlow.clear();
        }
    }

    /**
     * Disabled state: TfiFlow is disabled for measuring no-op fast-path cost.
     */
    @State(Scope.Thread)
    public static class DisabledState {
        /** Disable TfiFlow to measure no-op baseline. */
        @Setup(Level.Iteration)
        public void setup() {
            TfiFlow.disable();
            TfiFlow.clear();
        }

        /** Re-enable TfiFlow and clean up. */
        @TearDown(Level.Iteration)
        public void teardown() {
            TfiFlow.enable();
            TfiFlow.clear();
        }
    }

    /**
     * Stdout suppressor: redirects System.out to /dev/null during console
     * export benchmarks to prevent I/O noise while measuring formatting cost.
     *
     * <p>Safe in forked JVM ({@code @Fork(1)}) where JMH harness output
     * runs in the parent process.
     */
    @State(Scope.Benchmark)
    public static class StdoutSuppressor {
        private PrintStream originalOut;

        /** Redirect stdout to null output stream. */
        @Setup(Level.Trial)
        public void setup() {
            originalOut = System.out;
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        }

        /** Restore original stdout. */
        @TearDown(Level.Trial)
        public void teardown() {
            System.setOut(originalOut);
        }
    }

    // ==================== BM-001: Stage Create/Close ====================

    /**
     * BM-001: TfiFlow.stage() create + close throughput (single-threaded).
     *
     * <p>Measures the full AutoCloseable stage lifecycle: creation,
     * one message, and implicit close via try-with-resources.
     *
     * @param state base state providing enabled TfiFlow
     */
    @Benchmark
    public void bm001StageCreateClose(BaseState state) {
        try (TaskContext stage = TfiFlow.stage("bm-stage")) {
            stage.message("test");
        }
    }

    // ==================== BM-002: Stage Concurrent ====================

    /**
     * BM-002: TfiFlow.stage() concurrent throughput (4 threads).
     *
     * <p>Tests ThreadLocal isolation and aggregate throughput when
     * 4 threads independently create and close stages. Each thread
     * operates on its own ThreadLocal context.
     *
     * @param state base state (one per thread)
     */
    @Benchmark
    @Threads(4)
    public void bm002StageConcurrent(BaseState state) {
        try (TaskContext stage = TfiFlow.stage("concurrent-stage")) {
            stage.message("concurrent-msg");
        }
    }

    // ==================== BM-003: Message ====================

    /**
     * BM-003: TfiFlow.message() throughput (single-threaded).
     *
     * <p>Measures the cost of a complete message operation, including
     * session/stage lifecycle per invocation to ensure clean state.
     *
     * @param state base state providing enabled TfiFlow
     */
    @Benchmark
    public void bm003Message(BaseState state) {
        TfiFlow.startSession("msg-bench");
        try (TaskContext stage = TfiFlow.stage("msg-task")) {
            TfiFlow.message("benchmark message", MessageType.PROCESS);
        }
        TfiFlow.endSession();
        TfiFlow.clear();
    }

    // ==================== BM-004: Export JSON ====================

    /**
     * BM-004: TfiFlow.exportToJson() throughput (single-threaded).
     *
     * <p>Full cycle: create session, add content, export to JSON string,
     * and clean up. Measures serialization overhead.
     *
     * @param state base state providing enabled TfiFlow
     * @return JSON string (consumed by JMH to prevent dead-code elimination)
     */
    @Benchmark
    public String bm004ExportJson(BaseState state) {
        TfiFlow.startSession("json-bench");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }
        String json = TfiFlow.exportToJson();
        TfiFlow.endSession();
        TfiFlow.clear();
        return json;
    }

    // ==================== BM-005: Export Console ====================

    /**
     * BM-005: TfiFlow.exportToConsole() throughput (single-threaded).
     *
     * <p>Stdout is redirected to null via {@link StdoutSuppressor} to
     * isolate formatting cost from I/O overhead. Measures tree-building
     * and emoji rendering logic.
     *
     * @param state base state providing enabled TfiFlow
     * @param suppressor stdout suppressor (active during this benchmark)
     * @return export result (consumed by JMH)
     */
    @Benchmark
    public boolean bm005ExportConsole(BaseState state,
                                      StdoutSuppressor suppressor) {
        TfiFlow.startSession("console-bench");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }
        boolean result = TfiFlow.exportToConsole();
        TfiFlow.endSession();
        TfiFlow.clear();
        return result;
    }

    // ==================== BM-006: Context Create/Close ====================

    /**
     * BM-006: ManagedThreadContext create/close lifecycle cost.
     *
     * <p>Measures the overhead of creating a new thread-local context,
     * registering with SafeContextManager, and closing/unregistering.
     *
     * @param state base state
     */
    @Benchmark
    public void bm006ContextCreateClose(BaseState state) {
        try (ManagedThreadContext ctx =
                 ManagedThreadContext.create("bm-ctx")) {
            // context create + close lifecycle only
        }
    }

    // ==================== BM-007: Snapshot Create/Restore ====================

    /**
     * BM-007: ContextSnapshot create + restore cycle cost.
     *
     * <p>Measures the overhead of snapshotting a context and restoring
     * it on the same thread. Covers async context propagation cost.
     *
     * @param state base state
     */
    @Benchmark
    public void bm007SnapshotCreateRestore(BaseState state) {
        ManagedThreadContext ctx = ManagedThreadContext.create("bm-snap");
        ContextSnapshot snapshot = ctx.createSnapshot();
        ctx.close();
        ManagedThreadContext restored = snapshot.restore();
        restored.close();
    }

    // ==================== BM-008: Registry Lookup ====================

    /**
     * BM-008: ProviderRegistry.lookup() throughput.
     *
     * <p>Measures ConcurrentHashMap-based provider resolution cost.
     * The DefaultFlowProvider is pre-registered by TfiFlow.enable()
     * in {@link BaseState#setup()}.
     *
     * @param state base state (ensures provider is registered)
     * @return found provider (consumed by JMH)
     */
    @Benchmark
    public FlowProvider bm008RegistryLookup(BaseState state) {
        return ProviderRegistry.lookup(FlowProvider.class);
    }

    // ==================== BM-009: 10-Level Nested Stages ====================

    /**
     * BM-009: 10-level nested stage creation throughput.
     *
     * <p>Tests deep task tree construction, stack push/pop overhead,
     * and hierarchical parent-child wiring. Each invocation creates
     * a full 10-level tree and tears it down.
     *
     * @param state base state
     */
    @Benchmark
    @SuppressWarnings("checkstyle:MethodLength")
    public void bm009NestedStages(BaseState state) {
        TfiFlow.startSession("nested-bench");
        try (TaskContext s1 = TfiFlow.stage("L1")) {
            try (TaskContext s2 = TfiFlow.stage("L2")) {
                try (TaskContext s3 = TfiFlow.stage("L3")) {
                    try (TaskContext s4 = TfiFlow.stage("L4")) {
                        try (TaskContext s5 = TfiFlow.stage("L5")) {
                            try (TaskContext s6 = TfiFlow.stage("L6")) {
                                try (TaskContext s7 = TfiFlow.stage("L7")) {
                                    try (TaskContext s8 =
                                             TfiFlow.stage("L8")) {
                                        try (TaskContext s9 =
                                                 TfiFlow.stage("L9")) {
                                            try (TaskContext s10 =
                                                     TfiFlow.stage("L10")) {
                                                s10.message("leaf");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        TfiFlow.endSession();
        TfiFlow.clear();
    }

    // ==================== BM-010: Disabled No-Op Baseline ====================

    /**
     * BM-010: Disabled-state stage overhead (no-op baseline).
     *
     * <p>TfiFlow is disabled; stage() returns null or a no-op context.
     * Establishes the minimum cost baseline for the disabled fast-path,
     * allowing comparison against enabled benchmarks.
     *
     * @param state disabled state (TfiFlow.disable() called in setup)
     */
    @Benchmark
    public void bm010DisabledNoOp(DisabledState state) {
        try (TaskContext stage = TfiFlow.stage("disabled-stage")) {
            if (stage != null) {
                stage.message("noop");
            }
        }
    }
}
