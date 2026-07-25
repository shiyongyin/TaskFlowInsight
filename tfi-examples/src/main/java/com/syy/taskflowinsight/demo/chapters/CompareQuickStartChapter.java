package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.api.ComparisonTemplate;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.Arrays;
import java.util.List;

/**
 * 第 8 章：对象比对入门 — 使用 {@code TFI.compare()} 和 {@code TFI.comparator()} 进行对象差异检测。
 *
 * <p>本章通过递进场景，帮助开发者快速掌握 TFI 对象比对功能：
 * <ol>
 *   <li>简单对象比对</li>
 *   <li>渲染 Markdown 报告</li>
 *   <li>自定义比对（忽略字段、深度限制）</li>
 *   <li>集合比对（Entity 列表策略）</li>
 *   <li>普通 List 的 ordered-index 语义</li>
 *   <li>ComparisonTemplate 模板</li>
 * </ol></p>
 *
 * @since 4.0.0
 */
public class CompareQuickStartChapter implements DemoChapter {

    @Override
    public int getChapterNumber() { return 8; }

    @Override
    public String getTitle() { return "对象比对入门"; }

    @Override
    public String getDescription() { return "使用 TFI.compare() 快速检测对象差异"; }

    @Override
    public void run() {
        DemoUI.printChapterHeader(8, getTitle(), getDescription());
        TFI.enable();

        // 8.1 简单对象比对
        DemoUI.section("8.1 简单对象比对 — TFI.compare(a, b)");
        simpleCompare();

        // 8.2 渲染 Markdown 报告
        DemoUI.section("8.2 渲染比对报告 — TFI.render(result, RenderOptions)");
        renderReport();

        // 8.3 自定义比对
        DemoUI.section("8.3 自定义比对 — TFI.comparator().ignoring().compare()");
        customCompare();

        // 8.4 集合比对
        DemoUI.section("8.4 集合比对 — Entity 列表策略");
        collectionCompare();

        // 8.5 普通 List 按索引比较
        DemoUI.section("8.5 普通 List — 按索引比较");
        orderedListComparison();

        // 8.6 模板
        DemoUI.section("8.6 比较模板 — ComparisonTemplate");
        templateComparison();

        DemoUI.printSectionSummary("对象比对入门完成", getSummaryPoints());
    }

    /**
     * 场景 1：使用默认配置比较两个对象，展示最基本的 API 用法。
     */
    private void simpleCompare() {
        Address before = new Address("Shanghai", "SH", "100 Nanjing Road");
        Address after = new Address("Beijing", "BJ", "100 Nanjing Road");

        CompareResult result = TFI.compare(before, after);

        System.out.println("  比对结果: isDifferent=" + result.isDifferent());
        System.out.println("  变更数量: " + result.getChanges().size());
        result.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.beforeValue().orElse(null), c.afterValue().orElse(null)));
    }

    /**
     * 场景 2：将比对结果渲染为 Markdown 格式，适合日志和审计输出。
     */
    private void renderReport() {
        Product before = new Product(1L, "iPhone 15", 7999.0, 100);
        before.setSupplier(new Supplier(1L, "Apple China", "Shanghai", "SH"));

        Product after = new Product(1L, "iPhone 15 Pro", 8999.0, 80);
        after.setSupplier(new Supplier(1L, "Apple China", "Beijing", "BJ"));

        CompareResult result = TFI.compare(before, after);
        String report = TFI.render(result, RenderOptions.markdown());

        System.out.println("  === Markdown 报告 ===");
        System.out.println(report);
    }

    /**
     * 场景 3：通过 ComparatorBuilder 忽略特定字段、限制比较深度。
     */
    private void customCompare() {
        Product before = new Product(1L, "MacBook Pro", 14999.0, 50);
        before.setShippingAddress(new Address("Shanghai", "SH", "200 Huaihai Road"));

        Product after = new Product(1L, "MacBook Pro", 15999.0, 30);
        after.setShippingAddress(new Address("Beijing", "BJ", "300 Chang'an Ave"));

        CompareResult ignorePrice = TFI.comparator()
                .compare(before, after);

        System.out.println("  忽略 price 后变更数: " + ignorePrice.getChanges().size());
        ignorePrice.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.beforeValue().orElse(null), c.afterValue().orElse(null)));

        CompareResult shallow = TFI.comparator()
                .withMaxDepth(1)
                .compare(before, after);

        System.out.println("  maxDepth=1 后变更数: " + shallow.getChanges().size());
    }

    /**
     * 场景 4：使用 Entity 策略比较列表，自动按 {@code @Key} 匹配和分组。
     */
    private void collectionCompare() {
        List<Product> before = Arrays.asList(
                new Product(1L, "iPhone 15", 7999.0, 100),
                new Product(2L, "MacBook Pro", 14999.0, 50),
                new Product(3L, "AirPods Pro", 1799.0, 200)
        );

        List<Product> after = Arrays.asList(
                new Product(1L, "iPhone 15 Pro", 8999.0, 80),  // 修改
                new Product(2L, "MacBook Pro", 14999.0, 50),    // 不变
                new Product(4L, "iPad Air", 4599.0, 150)         // 新增 (id=3 删除)
        );

        CompareResult result = TFI.comparator()
                .compare(before, after);

        String report = TFI.render(result, RenderOptions.markdown());
        System.out.println("  === Entity 列表比对报告 ===");
        System.out.println(report);
    }

    /**
     * 场景 5：普通 List 始终按索引比较，避免结果随规模或运行时开关变化。
     */
    private void orderedListComparison() {
        List<String> before = Arrays.asList("A", "B", "C", "D", "E");
        List<String> after = Arrays.asList("B", "A", "C", "E", "D");

        CompareResult result = TFI.compare(before, after);

        System.out.println("  outcome=" + result.getOutcome() + ", changes=" + result.getChangeCount());
        System.out.println("\n  === ordered-index 输出示例 ===");
        System.out.println(TFI.render(result, RenderOptions.markdown()));
    }

    /**
     * 场景 6：展示 AUDIT、FAST、DEBUG 三种比较模板。
     */
    private void templateComparison() {
        Product before = new Product(1L, "iPhone 15", 7999.0, 100);
        before.setSupplier(new Supplier(1L, "Apple China", "Shanghai", "SH"));
        before.setWarehouse(new Warehouse(1001L, "CN", "Shanghai", 800));

        Product after = new Product(1L, "iPhone 15 Pro", 8999.0, 80);
        after.setSupplier(new Supplier(1L, "Apple China", "Beijing", "BJ"));
        after.setWarehouse(new Warehouse(1001L, "CN", "Shanghai", 800));

        CompareResult audit = TFI.comparator()
                .useTemplate(ComparisonTemplate.AUDIT)
                .compare(before, after);

        CompareResult fast = TFI.comparator()
                .useTemplate(ComparisonTemplate.FAST)
                .compare(before, after);

        CompareResult debugWithPatch = TFI.comparator()
                .useTemplate(ComparisonTemplate.DEBUG)
                .compare(before, after);

        System.out.println("  [AUDIT] similarity=" + audit.similarity()
                .map(score -> String.format("%.1f%%", score.value() * 100)).orElse("n/a")
                + ", changes=" + audit.getChangeCount());
        System.out.println("  [FAST ] similarity=" + fast.similarity()
                .map(score -> String.format("%.1f%%", score.value() * 100)).orElse("n/a")
                + ", changes=" + fast.getChangeCount());

        System.out.println("  [DEBUG] outcome=" + debugWithPatch.getOutcome()
                + ", completion=" + debugWithPatch.getCompletion()
                + ", changes=" + debugWithPatch.getChangeCount());
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "学会了使用 TFI.compare(a, b) 比对两个对象",
                "学会了使用 TFI.render(result, RenderOptions) 生成诊断报告",
                "掌握了 TFI.comparator() 的 ignoring / withMaxDepth 等自定义选项",
                "了解了 typeAware + ENTITY 策略进行集合比对",
                "掌握了普通 List 稳定的 ordered-index 语义",
                "了解了 ComparisonTemplate 的使用场景"
        );
    }
}
