package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * P1计划性能验证基准测试
 * <p>
 * 验证目标（来自 P1_FINAL_PLAN.md Line 1133）:
 * <ul>
 *   <li>比对延迟退化 ≤ 5%</li>
 *   <li>路径解析CPU节省 ≥ 7%</li>
 *   <li>内存占用增加 ≤ 10%</li>
 * </ul>
 * </p>
 *
 * <h3>基准分组</h3>
 * <ul>
 *   <li>Group 1: 容器事件结构化 vs 路径解析（CPU节省验证）</li>
 *   <li>Group 2: @ShallowReference vs 深度比较（O(1) vs O(n)验证）</li>
 *   <li>Group 3: 整体比对延迟退化验证</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since v3.1.0
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class P1PerformanceBenchmark {

    // ==================== 测试数据模型 ====================

    @Entity
    public static class Product {
        @Key
        private String sku;
        private String name;
        private double price;
        @ShallowReference
        private Supplier supplier; // 引用语义字段

        public Product(String sku, String name, double price, Supplier supplier) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.supplier = supplier;
        }

        // Getters
        public String getSku() { return sku; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public Supplier getSupplier() { return supplier; }
    }

    @Entity
    public static class Supplier {
        @Key
        private String supplierId;
        private String companyName;

        public Supplier(String supplierId, String companyName) {
            this.supplierId = supplierId;
            this.companyName = companyName;
        }

        // Getters
        public String getSupplierId() { return supplierId; }
        public String getCompanyName() { return companyName; }
    }

    // ==================== 基准状态 ====================

    private CompareService compareService;

    // 小规模数据集（100个实体）
    private List<Product> oldList_small;
    private List<Product> newList_small;

    // 中等规模数据集（1000个实体）
    private List<Product> oldList_medium;
    private List<Product> newList_medium;

    // 供应商池（用于引用变更）
    private List<Supplier> suppliers;

    @Setup(Level.Trial)
    public void setup() {
        compareService = CompareService.createDefault(CompareOptions.typeAware());

        // 创建供应商池（10个供应商）
        suppliers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            suppliers.add(new Supplier("SUP" + i, "Supplier " + i));
        }

        // 小规模数据集（100个实体，10%变更）
        oldList_small = generateProductList(100, 0);
        newList_small = mutateProductList(oldList_small, 10);

        // 中等规模数据集（1000个实体，10%变更）
        oldList_medium = generateProductList(1000, 1000);
        newList_medium = mutateProductList(oldList_medium, 100);
    }

    /**
     * 生成产品列表
     */
    private List<Product> generateProductList(int size, int startIndex) {
        List<Product> list = new ArrayList<>(size);
        Random rand = new Random(42); // 固定种子，保证可重复性
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

    /**
     * 变更产品列表（模拟真实业务场景）
     * - 30% 实体修改（价格变更）
     * - 30% 实体删除
     * - 30% 实体新增
     * - 10% 引用变更（供应商切换）
     */
    private List<Product> mutateProductList(List<Product> original, int changeCount) {
        List<Product> mutated = new ArrayList<>(original);
        Random rand = new Random(43);
        int modifyCount = (int) (changeCount * 0.3);
        int deleteCount = (int) (changeCount * 0.3);
        int addCount = (int) (changeCount * 0.3);
        int refChangeCount = changeCount - modifyCount - deleteCount - addCount;

        // 修改
        for (int i = 0; i < modifyCount && i < mutated.size(); i++) {
            Product old = mutated.get(i);
            mutated.set(i, new Product(
                    old.getSku(),
                    old.getName(),
                    old.getPrice() + 5.0, // 价格变更
                    old.getSupplier()
            ));
        }

        // 删除
        for (int i = 0; i < deleteCount && mutated.size() > 1; i++) {
            mutated.remove(mutated.size() - 1);
        }

        // 新增
        for (int i = 0; i < addCount; i++) {
            int newIdx = 10000 + i;
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
                    newSupplier // 供应商切换
            ));
        }

        return mutated;
    }

    // ==================== Group 1: 容器事件结构化 vs 路径解析 ====================

    /**
     * Baseline: P1迁移后的性能（使用ContainerElementEvent）
     * <p>
     * 目标: 作为性能基线，后续基准应≥ 93% of baseline（退化≤7%）
     * </p>
     */
    @Benchmark
    public void baseline_P1_with_container_events_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );
        EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList_small, newList_small);
        bh.consume(diffResult.getGroups());
    }

    @Benchmark
    public void baseline_P1_with_container_events_medium(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_medium,
                newList_medium,
                CompareOptions.typeAware()
        );
        EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList_medium, newList_medium);
        bh.consume(diffResult.getGroups());
    }

    /**
     * 路径解析CPU消耗（模拟P1迁移前的场景）
     * <p>
     * 验证: baseline应比此方法快≥7%（路径解析CPU节省目标）
     * </p>
     */
    @Benchmark
    public void legacy_path_parsing_overhead_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );
        // 模拟旧版本的路径解析开销：每个变更都需要解析路径
        result.getChanges().forEach(fc -> {
            String path = fc.getFieldPath();
            if (path != null && path.contains("[")) {
                // 模拟extractEntityKeyFromPath的开销
                int start = path.indexOf('[');
                int end = path.indexOf(']');
                if (end > start) {
                    String key = path.substring(start + 1, end);
                    bh.consume(key); // 防止JIT优化消除
                }
            }
        });
        EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList_small, newList_small);
        bh.consume(diffResult.getGroups());
    }

    // ==================== Group 2: @ShallowReference性能验证 ====================

    /**
     * @ShallowReference 检测性能（O(1)键比较）
     * <p>
     * 目标: 验证引用变更检测为O(1)复杂度
     * </p>
     */
    @Benchmark
    public void reference_change_detection_O1_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );
        // 使用Query API快速筛选引用变更
        List<FieldChange> refChanges = result.getReferenceChanges();
        bh.consume(refChanges);
    }

    @Benchmark
    public void reference_change_detection_O1_medium(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_medium,
                newList_medium,
                CompareOptions.typeAware()
        );
        List<FieldChange> refChanges = result.getReferenceChanges();
        bh.consume(refChanges);
    }

    // ==================== Group 3: Query API性能验证 ====================

    /**
     * Query Helper API性能（验证无性能退化）
     * <p>
     * 目标: 验证便捷API不引入显著性能开销
     * </p>
     */
    @Benchmark
    public void query_api_getChangesByType_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );
        bh.consume(result.getChangesByType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE));
        bh.consume(result.getChangesByType(com.syy.taskflowinsight.tracking.ChangeType.CREATE));
        bh.consume(result.getChangesByType(com.syy.taskflowinsight.tracking.ChangeType.DELETE));
    }

    @Benchmark
    public void query_api_groupByObject_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );
        bh.consume(result.groupByObject());
    }

    /**
     * Baseline: 手动filter（验证Query API无额外开销）
     */
    @Benchmark
    public void baseline_manual_filter_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );
        // 手动filter（Query API消除的样板代码）
        long updateCount = result.getChanges().stream()
                .filter(c -> c.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
                .count();
        bh.consume(updateCount);
    }

    // ==================== Group 4: 整体性能退化验证 ====================

    /**
     * P1完整功能性能（Container Events + Reference Semantic + Query API）
     * <p>
     * 目标: 比对延迟退化 ≤ 5%（相对于baseline）
     * </p>
     */
    @Benchmark
    public void p1_full_features_small(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_small,
                newList_small,
                CompareOptions.typeAware()
        );

        // 使用P1所有新特性
        EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList_small, newList_small);
        List<FieldChange> refChanges = result.getReferenceChanges();
        List<FieldChange> containerChanges = result.getContainerChanges();
        String summary = result.prettyPrint();

        bh.consume(diffResult);
        bh.consume(refChanges);
        bh.consume(containerChanges);
        bh.consume(summary);
    }

    @Benchmark
    public void p1_full_features_medium(Blackhole bh) {
        CompareResult result = compareService.compare(
                oldList_medium,
                newList_medium,
                CompareOptions.typeAware()
        );

        EntityListDiffResult diffResult = EntityListDiffResult.from(result, oldList_medium, newList_medium);
        List<FieldChange> refChanges = result.getReferenceChanges();
        List<FieldChange> containerChanges = result.getContainerChanges();
        String summary = result.prettyPrint();

        bh.consume(diffResult);
        bh.consume(refChanges);
        bh.consume(containerChanges);
        bh.consume(summary);
    }
}
