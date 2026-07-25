package com.syy.taskflowinsight.model;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务树共享 gate 与 snapshot capture 的 JMH 相对性能基准。
 *
 * <p>mutation 基准让每个线程写入独立 TaskNode，但所有节点共享同一公平 gate，避免节点 monitor
 * 争用掩盖 gate 成本。mixed group 则让七个 mutation 线程与一个 capture 线程共享同一 Session，
 * 用于同环境前后版本比较，不定义跨硬件吞吐阈值。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class TaskTreeMutationBenchmark {

    private static final String ATTRIBUTE_KEY = "gate-benchmark";

    /** 一个 trial 内由全部 mutation worker 共享的 gate。 */
    @State(Scope.Benchmark)
    public static class SharedGateState {
        private TaskTreeMutationGate gate;

        /** 为当前 trial 创建独立 gate，避免跨 benchmark 污染。 */
        @Setup(Level.Trial)
        public void setup() {
            gate = new TaskTreeMutationGate();
        }
    }

    /** 每个 worker 独享节点，节点之间只共享 gate。 */
    @State(Scope.Thread)
    public static class MutationState {
        private TaskNode task;

        /**
         * 把线程本地节点绑定到 benchmark-scoped gate，并预热固定 attribute slot。
         *
         * @param sharedGate 当前 trial 的共享 gate
         */
        @Setup(Level.Trial)
        public void setup(SharedGateState sharedGate) {
            task = new TaskNode(
                    "gate-mutation-" + Thread.currentThread().threadId(), sharedGate.gate);
            task.addAttribute(ATTRIBUTE_KEY, Boolean.FALSE);
        }

        private TaskNode mutate() {
            return task.addAttribute(ATTRIBUTE_KEY, Boolean.TRUE);
        }
    }

    /** 一个线程反复进入共享 gate 的 mutation 路径。 */
    @Benchmark
    @Threads(1)
    public TaskNode singleThreadMutation(MutationState state) {
        return state.mutate();
    }

    /** 八线程写不同节点但竞争同一公平 gate 的 mutation 路径。 */
    @Benchmark
    @Threads(8)
    public TaskNode eightThreadSharedGateMutation(MutationState state) {
        return state.mutate();
    }

    /** mixed group 共享的 Session 与节点分配器。 */
    @State(Scope.Group)
    public static class MixedCaptureState {
        private final AtomicInteger nodeSequence = new AtomicInteger();
        private Session session;
        private TaskNode root;

        /** 为每个 group 创建独立 Session。 */
        @Setup(Level.Trial)
        public void setup() {
            session = Session.create("mixed-capture-mutation");
            root = session.getRootTask();
        }

        private TaskNode createMutationNode() {
            TaskNode task = root.createChild(
                    "mutation-worker-" + nodeSequence.getAndIncrement());
            task.addAttribute(ATTRIBUTE_KEY, Boolean.FALSE);
            return task;
        }

        private SessionExportSnapshot capture() {
            return SessionExportSnapshot.capture(session);
        }
    }

    /** mixed group 中每个 mutation worker 独享的节点。 */
    @State(Scope.Thread)
    public static class MixedMutationState {
        private TaskNode task;

        /**
         * 从 group-scoped Session 创建线程本地节点。
         *
         * @param captureState 当前 mixed group 的 Session state
         */
        @Setup(Level.Trial)
        public void setup(MixedCaptureState captureState) {
            task = captureState.createMutationNode();
        }

        private TaskNode mutate() {
            return task.addAttribute(ATTRIBUTE_KEY, Boolean.TRUE);
        }
    }

    /** mixed group 的七个 mutation workers。 */
    @Benchmark
    @Group("mixedCaptureMutation")
    @GroupThreads(7)
    public TaskNode mixedMutation(MixedMutationState state) {
        return state.mutate();
    }

    /** mixed group 的单个 snapshot capture worker。 */
    @Benchmark
    @Group("mixedCaptureMutation")
    @GroupThreads(1)
    public SessionExportSnapshot mixedCapture(MixedCaptureState state) {
        return state.capture();
    }
}
