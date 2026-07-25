package com.syy.tfi.kernel.compare.spring.benchmark;

import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.compare.spring.TfiKernelCompareAopAutoConfiguration;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;

/**
 * 固定 AOP 三场景基线；三组共用同一八对象 workload、action 和返回引用。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Threads(1)
public class AopBenchmarks {

    /** trial 内独享全部 Runtime、代理和业务输入，初始化成本不计入测量。 */
    @State(Scope.Thread)
    public static class BenchmarkState {

        /** 不经过 Advisor 的业务实现。 */
        private BenchmarkServiceImpl directService;
        /** 使用生产 Advisor 的 JDK proxy。 */
        private BenchmarkService proxiedService;
        /** 三组共用的固定八对象业务输入。 */
        private Workload workload;
        /** trial 内独享的 Kernel owner，结束时 terminal close。 */
        private KernelRuntime kernelRuntime;

        /** 构造 summary-only owner 图和生产 Advisor；trial 外不共享任何可变状态。 */
        @Setup(Level.Trial)
        public void setup() {
            kernelRuntime = KernelRuntime.create(KernelConfig.defaults());
            CompareRuntime compareRuntime = CompareRuntime.builder().build();
            TrackingExecutor trackingExecutor =
                    new TrackingExecutor(compareRuntime.engine()::beginTracking);
            KernelCompareRecorder recorder = new KernelCompareRecorder(
                    compareRuntime.engine(),
                    new CompareProjectionFactory(),
                    MaskingPolicy.safeDefaults(),
                    KernelCompareRecordPolicy.defaults());
            Advisor advisor = new TfiKernelCompareAopAutoConfiguration()
                    .tfiKernelCompareAdvisor(
                            kernelRuntime, compareRuntime, trackingExecutor, recorder);

            directService = new BenchmarkServiceImpl();
            ProxyFactory proxyFactory = new ProxyFactory(directService);
            proxyFactory.setInterfaces(BenchmarkService.class);
            proxyFactory.addAdvisor(advisor);
            proxiedService = (BenchmarkService) proxyFactory.getProxy();
            workload = new Workload();
        }

        /** 退役 trial 的 Kernel owner，避免 fork 内状态泄漏。 */
        @TearDown(Level.Trial)
        public void teardown() {
            kernelRuntime.close();
        }
    }

    /** 无 Advisor 的直接业务调用基线。 */
    @Benchmark
    public void directInvocation(BenchmarkState state, Blackhole blackhole) {
        blackhole.consume(state.directService.direct(state.workload));
    }

    /** 一个 aggregate target 的 summary-only AOP 路径。 */
    @Benchmark
    public void oneTargetSummaryOnly(BenchmarkState state, Blackhole blackhole) {
        blackhole.consume(state.proxiedService.oneTarget(state.workload));
    }

    /** 八个 leaf target 的 summary-only AOP 路径。 */
    @Benchmark
    public void eightTargetsSummaryOnly(BenchmarkState state, Blackhole blackhole) {
        int[][] targets = state.workload.targets;
        blackhole.consume(state.proxiedService.eightTargets(
                targets[0], targets[1], targets[2], targets[3],
                targets[4], targets[5], targets[6], targets[7],
                state.workload));
    }

    /** JDK proxy 的固定 annotation surface；业务实现始终委托同一个 action。 */
    interface BenchmarkService {

        @TfiTracked(operation = "benchmark.one")
        Object oneTarget(@TfiTrackTarget("aggregate") Workload workload);

        @TfiTracked(operation = "benchmark.eight")
        Object eightTargets(
                @TfiTrackTarget("target1") int[] first,
                @TfiTrackTarget("target2") int[] second,
                @TfiTrackTarget("target3") int[] third,
                @TfiTrackTarget("target4") int[] fourth,
                @TfiTrackTarget("target5") int[] fifth,
                @TfiTrackTarget("target6") int[] sixth,
                @TfiTrackTarget("target7") int[] seventh,
                @TfiTrackTarget("target8") int[] eighth,
                Workload workload);
    }

    /** 三个入口都调用 {@link Workload#runAction()}，不为 AOP 场景削减业务成本。 */
    static final class BenchmarkServiceImpl implements BenchmarkService {

        Object direct(Workload workload) {
            return workload.runAction();
        }

        @Override
        public Object oneTarget(Workload workload) {
            return workload.runAction();
        }

        @Override
        public Object eightTargets(
                int[] first,
                int[] second,
                int[] third,
                int[] fourth,
                int[] fifth,
                int[] sixth,
                int[] seventh,
                int[] eighth,
                Workload workload) {
            return workload.runAction();
        }
    }

    /** 八个同规模 leaf 对象和一个稳定返回引用组成的固定 workload。 */
    static final class Workload {

        /** 三组均修改的八个单整数对象。 */
        private final int[][] targets = {
            {0}, {0}, {0}, {0}, {0}, {0}, {0}, {0}
        };
        /** 三组均返回且由 Blackhole 消费的同一对象引用。 */
        private final Object result = new Object();

        Object runAction() {
            for (int[] target : targets) {
                target[0]++;
            }
            return result;
        }
    }
}
