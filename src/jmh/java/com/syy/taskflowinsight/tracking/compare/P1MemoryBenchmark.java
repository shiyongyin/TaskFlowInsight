package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * P1计划内存占用验证基准测试
 * <p>
 * 验证目标（来自 P1_FINAL_PLAN.md Line 1137）:
 * <ul>
 *   <li>内存占用增加 ≤ 10%</li>
 * </ul>
 * </p>
 *
 * <h3>使用JMH GC Profiler</h3>
 * <pre>
 * 运行命令:
 * mvn clean compile exec:exec -Pbenchmark \
 *   -Dbenchmark.class=P1MemoryBenchmark \
 *   -Dbenchmark.profiler=gc
 * </pre>
 *
 * <h3>关键指标</h3>
 * <ul>
 *   <li>gc.alloc.rate.norm: 每次操作分配的内存（bytes/op）</li>
 *   <li>gc.count: GC次数</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since v3.1.0
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 10)
@Fork(1)
@State(Scope.Benchmark)
public class P1MemoryBenchmark {

    // ==================== 测试数据模型（与P1PerformanceBenchmark一致） ====================

    @Entity
    public static class Product {
        @Key private String sku;
        private String name;
        private double price;
        @ShallowReference private Supplier supplier;

        public Product(String sku, String name, double price, Supplier supplier) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.supplier = supplier;
        }

        public String getSku() { return sku; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public Supplier getSupplier() { return supplier; }
    }

    @Entity
    public static class Supplier {
        @Key private String supplierId;
        private String companyName;

        public Supplier(String supplierId, String companyName) {
            this.supplierId = supplierId;
            this.companyName = companyName;
        }

        public String getSupplierId() { return supplierId; }
        public String getCompanyName() { return companyName; }
    }

    // ==================== 基准状态 ====================

    private CompareService compareService;

    // 大规模数据集（5000个实体，用于内存压力测试）
    private List<Product> oldList_large;
    private List<Product> newList_large;

    private List<Supplier> suppliers;

    @Setup(Level.Trial)
    public void setup() {
        compareService = CompareService.createDefault(CompareOptions.typeAware());

        // 创建供应商池
        suppliers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            suppliers.add(new Supplier("SUP" + i, "Supplier " + i));
        }

        // 大规模数据集（5000个实体，20%变更）
        oldList_large = generateProductList(5000, 0);
        newList_large = mutateProductList(oldList_large, 1000);
    }

    private List<Product> generateProductList(int size, int startIndex) {
        List<Product> list = new ArrayList<>(size);
        Random rand = new Random(42);
        for (int i = 0; i < size; i++) {
            int idx = startIndex + i;
            Supplier supplier = suppliers.get(rand.nextInt(suppliers.size()));
            list.add(new Product(
                    "SKU" + idx,
                    "Product " + idx,
                    10.0 + rand.nextDouble() * 100,
                    supplier
            ));
        }
        return list;
    }

    private List<Product> mutateProductList(List<Product> original, int changeCount) {
        List<Product> mutated = new ArrayList<>(original);
        Random rand = new Random(43);
        int modifyCount = (int) (changeCount * 0.4);
        int deleteCount = (int) (changeCount * 0.3);
        int addCount = (int) (changeCount * 0.2);
        int refChangeCount = changeCount - modifyCount - deleteCount - addCount;

        // 修改
        for (int i = 0; i < modifyCount && i < mutated.size(); i++) {
            Product old = mutated.get(i);
            mutated.set(i, new Product(
                    old.getSku(),
                    old.getName(),
                    old.getPrice() + 5.0,
                    old.getSupplier()
            ));
        }

        // 删除
        for (int i = 0; i < deleteCount && mutated.size() > 1; i++) {
            mutated.remove(mutated.size() - 1);
        }

        // 新增
        for (int i = 0; i < addCount; i++) {
            int newIdx = 20000 + i;
            mutated.add(new Product(
                    "SKU" + newIdx,
                    "New Product " + newIdx,
                    50.0 + rand.nextDouble() * 50,
                    suppliers.get(rand.nextInt(suppliers.size()))
            ));
        }

        // 引用变更
        for (int i = modifyCount; i < modifyCount + refChangeCount && i < mutated.size(); i++) {
            Product old = mutated.get(i);
            Supplier newSupplier = suppliers.get((suppliers.indexOf(old.getSupplier()) + 1) % suppliers.size());
            mutated.set(i, new Product(
                    old.getSku(),
                    old.getName(),
                    old.getPrice(),
                    newSupplier
            ));
        }

        return mutated;
    }

    // ==================== 内存占用基准 ====================

    /**
     * Baseline: 基本比对内存占用（无P1新特性）
     * <p>
     * 测量CompareResult的内存占用
     * </p>
     */
    @Benchmark
    public void baseline_basic_compare_memory(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_large,
                newList_large,
                CompareOptions.typeAware()
        );
        bh.consume(result.getChanges());
    }

    /**
     * P1完整特性内存占用
     * <p>
     * 测量P1新特性（Container Events + Reference Semantic + EntityListDiffResult）的内存增量
     * 目标: 内存增量 ≤ 10%
     * </p>
     */
    @Benchmark
    public void p1_full_features_memory(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_large,
                newList_large,
                CompareOptions.typeAware()
        );

        // P1新特性：EntityListDiffResult构建
        EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList_large, newList_large);

        // P1新特性：Query API调用
        List<FieldChange> refChanges = result.getReferenceChanges();
        List<FieldChange> containerChanges = result.getContainerChanges();
        Map<String, List<FieldChange>> groupedByObject = result.groupByObject();

        // P1新特性：prettyPrint
        String summary = result.prettyPrint();

        bh.consume(result);
        bh.consume(diffResult);
        bh.consume(refChanges);
        bh.consume(containerChanges);
        bh.consume(groupedByObject);
        bh.consume(summary);
    }

    /**
     * ContainerElementEvent内存占用（详细测量）
     * <p>
     * 测量ContainerElementEvent结构化存储的内存开销
     * </p>
     */
    @Benchmark
    public void container_events_memory_overhead(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_large,
                newList_large,
                CompareOptions.typeAware()
        );

        // 提取所有ContainerElementEvent
        List<FieldChange.ContainerElementEvent> events = result.getChanges().stream()
                .filter(FieldChange::isContainerElementChange)
                .map(FieldChange::getElementEvent)
                .filter(Objects::nonNull)
                .toList();

        bh.consume(events);
    }

    /**
     * ReferenceDetail内存占用（详细测量）
     * <p>
     * 测量ReferenceDetail的内存开销
     * </p>
     */
    @Benchmark
    public void reference_detail_memory_overhead(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_large,
                newList_large,
                CompareOptions.typeAware()
        );

        // 提取所有ReferenceDetail
        List<FieldChange.ReferenceDetail> details = result.getChanges().stream()
                .filter(FieldChange::isReferenceChange)
                .map(FieldChange::getReferenceDetail)
                .filter(Objects::nonNull)
                .toList();

        bh.consume(details);
    }

    // ==================== Main方法（便于IDE直接运行） ====================

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(P1MemoryBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class) // 关键：启用GC Profiler
                .build();

        new Runner(opt).run();
    }
}
