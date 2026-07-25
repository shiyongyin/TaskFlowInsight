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
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * 固定 TYPICAL/CHANGED 对象图的 allocation 回归 workload。
 *
 * <p>三个场景分别隔离 Compare Core、单 target 完整 AOP 和八 target 完整 AOP；基础设施按生产
 * singleton 形态共享，业务对象保持线程私有。门禁只接受 GCProfiler 的 B/op，不用机器敏感的时延作硬阈值。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Threads(1)
public class CompareAllocationBenchmarks {

    /** JMH fork 用于拒绝旧本地仓库构件的 Reactor 根目录属性。 */
    static final String REPOSITORY_ROOT_PROPERTY =
            "tfi.compare.allocation.repositoryRoot";

    /** 生产形态共享的 Compare、Kernel、bridge 和 Spring proxy 图。 */
    @State(Scope.Benchmark)
    public static class Infrastructure {

        /** 当前 fork 唯一的 Kernel owner。 */
        private KernelRuntime kernelRuntime;

        /** 当前 fork 唯一的 Compare Core owner。 */
        private CompareRuntime compareRuntime;

        /** 使用生产 Advisor 的 JDK proxy。 */
        private BenchmarkService proxiedService;

        /** 构造生产组合图，并先证明 fork 没有加载旧构件。 */
        @Setup(Level.Trial)
        public void setup() throws URISyntaxException {
            Path repository = requiredRepositoryRoot();
            requireReactorCodeSource(repository, "tfi-compare-core", CompareRuntime.class);
            requireReactorCodeSource(repository, "tfi-kernel", KernelRuntime.class);
            requireReactorCodeSource(
                    repository, "tfi-kernel-compare", KernelCompareRecorder.class);
            requireReactorCodeSource(
                    repository,
                    "tfi-kernel-compare-spring-starter",
                    CompareAllocationBenchmarks.class);

            kernelRuntime = KernelRuntime.create(KernelConfig.defaults());
            compareRuntime = CompareRuntime.builder().build();
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

            BenchmarkServiceImpl target = new BenchmarkServiceImpl();
            ProxyFactory proxyFactory = new ProxyFactory(target);
            proxyFactory.setInterfaces(BenchmarkService.class);
            proxyFactory.addAdvisor(advisor);
            proxiedService = (BenchmarkService) proxyFactory.getProxy();
        }

        /** 退役当前 trial 的 Kernel owner。 */
        @TearDown(Level.Trial)
        public void teardown() {
            kernelRuntime.close();
        }
    }

    /** 每线程私有的固定输入，避免并发跑次把共享数据竞争计入组件成本。 */
    @State(Scope.Thread)
    public static class WorkloadState {

        /** compareOnly 的稳定变更前对象。 */
        private TypicalOrder compareBefore;

        /** compareOnly 的稳定变更后对象。 */
        private TypicalOrder compareAfter;

        /** 单 target AOP 路径的可变业务对象。 */
        private TypicalOrder singleTarget;

        /** 八 target AOP 路径的八个可变业务对象。 */
        private TypicalOrder[] eightTargets;

        /** 业务 action 始终返回且由 Blackhole 消费的稳定引用。 */
        private final Object result = new Object();

        /** 构造 10 个标量字段加 5 个三字段行项的固定对象图。 */
        @Setup(Level.Trial)
        public void setup() {
            compareBefore = typicalOrder(7);
            compareAfter = typicalOrder(7);
            mutate(compareAfter);
            singleTarget = typicalOrder(100);
            eightTargets = new TypicalOrder[8];
            for (int index = 0; index < eightTargets.length; index++) {
                eightTargets[index] = typicalOrder(200 + index);
            }
        }

        Object runSingleAction() {
            mutate(singleTarget);
            return result;
        }

        Object runEightAction() {
            for (TypicalOrder target : eightTargets) {
                mutate(target);
            }
            return result;
        }
    }

    /** 只测 Compare Core 的快照与比较分配，不接触 Kernel。 */
    @Benchmark
    public void compareOnly(
            Infrastructure infrastructure,
            WorkloadState workload,
            Blackhole blackhole) {
        blackhole.consume(infrastructure.compareRuntime.engine()
                .compare(workload.compareBefore, workload.compareAfter));
    }

    /** 测量单 target summary-only 的完整 AOP 分配。 */
    @Benchmark
    public void oneTargetSummaryOnly(
            Infrastructure infrastructure,
            WorkloadState workload,
            Blackhole blackhole) {
        blackhole.consume(infrastructure.proxiedService.oneTarget(
                workload.singleTarget, workload));
    }

    /** 测量八 target summary-only 的完整 AOP 分配。 */
    @Benchmark
    public void eightTargetsSummaryOnly(
            Infrastructure infrastructure,
            WorkloadState workload,
            Blackhole blackhole) {
        TypicalOrder[] targets = workload.eightTargets;
        blackhole.consume(infrastructure.proxiedService.eightTargets(
                targets[0], targets[1], targets[2], targets[3],
                targets[4], targets[5], targets[6], targets[7], workload));
    }

    /** 固定 annotation surface；业务 action 与被跟踪 target 明确分离。 */
    interface BenchmarkService {

        @TfiTracked(operation = "allocation.one")
        Object oneTarget(
                @TfiTrackTarget("aggregate") Object target,
                WorkloadState workload);

        @TfiTracked(operation = "allocation.eight")
        Object eightTargets(
                @TfiTrackTarget("target1") Object first,
                @TfiTrackTarget("target2") Object second,
                @TfiTrackTarget("target3") Object third,
                @TfiTrackTarget("target4") Object fourth,
                @TfiTrackTarget("target5") Object fifth,
                @TfiTrackTarget("target6") Object sixth,
                @TfiTrackTarget("target7") Object seventh,
                @TfiTrackTarget("target8") Object eighth,
                WorkloadState workload);
    }

    /** action 只修改固定三处叶子字段，保证每次调用都走 CHANGED 路径。 */
    static final class BenchmarkServiceImpl implements BenchmarkService {

        @Override
        public Object oneTarget(Object target, WorkloadState workload) {
            return workload.runSingleAction();
        }

        @Override
        public Object eightTargets(
                Object first,
                Object second,
                Object third,
                Object fourth,
                Object fifth,
                Object sixth,
                Object seventh,
                Object eighth,
                WorkloadState workload) {
            return workload.runEightAction();
        }
    }

    /** TYPICAL 行项，三个字段中包含浮点 canonical 热路径。 */
    public static final class TypicalItem {

        /** 业务库存标识。 */
        public String sku;

        /** 当前订购数量，单位为件。 */
        public int quantity;

        /** 当前单价，单位由订单币种定义。 */
        public double price;

        TypicalItem(String sku, int quantity, double price) {
            this.sku = sku;
            this.quantity = quantity;
            this.price = price;
        }
    }

    /** 10 个标量字段和 5 个行项组成的固定 TYPICAL 对象。 */
    public static final class TypicalOrder {

        /** 订单稳定标识。 */
        public String id;

        /** 当前订单状态。 */
        public String status;

        /** 客户稳定标识。 */
        public String customer;

        /** 下单渠道。 */
        public String channel;

        /** 金额字段使用的币种。 */
        public String currency;

        /** 固定长度的业务备注。 */
        public String note;

        /** 是否按优先订单处理。 */
        public boolean priority;

        /** 创建时间，Unix epoch 毫秒。 */
        public long createdAtMs;

        /** 行项数量，单位为项。 */
        public int itemCount;

        /** 订单总金额，单位由 currency 定义。 */
        public double total;

        /** 保持索引语义的五个订单行项。 */
        public List<TypicalItem> items;
    }

    private static TypicalOrder typicalOrder(int seed) {
        TypicalOrder order = new TypicalOrder();
        order.id = "ORD-2026-" + seed;
        order.status = "PENDING";
        order.customer = "customer-" + seed;
        order.channel = "web";
        order.currency = "CNY";
        order.note = "benchmark order fixed note text";
        order.priority = (seed & 1) == 0;
        order.createdAtMs = 1_760_000_000_000L + seed;
        order.itemCount = 5;
        order.total = 100.0 + seed;
        order.items = new ArrayList<>(order.itemCount);
        for (int index = 0; index < order.itemCount; index++) {
            order.items.add(new TypicalItem(
                    "SKU-" + seed + "-" + index,
                    index + 1,
                    10.0 + index));
        }
        return order;
    }

    private static void mutate(TypicalOrder order) {
        order.status = "PROCESSING".equals(order.status) ? "PENDING" : "PROCESSING";
        order.total += 1.0;
        order.items.getFirst().quantity++;
    }

    private static Path requiredRepositoryRoot() {
        String configured = System.getProperty(REPOSITORY_ROOT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "allocation gate requires " + REPOSITORY_ROOT_PROPERTY);
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static void requireReactorCodeSource(
            Path repository,
            String module,
            Class<?> type) throws URISyntaxException {
        if (type.getProtectionDomain().getCodeSource() == null) {
            throw new IllegalStateException("missing CodeSource for " + type.getName());
        }
        Path actual = Path.of(type.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).toAbsolutePath().normalize();
        Path expectedTarget = repository.resolve(module).resolve("target").normalize();
        if (!actual.startsWith(expectedTarget)) {
            throw new IllegalStateException(
                    type.getName() + " must load from " + expectedTarget + " but was " + actual);
        }
    }
}
