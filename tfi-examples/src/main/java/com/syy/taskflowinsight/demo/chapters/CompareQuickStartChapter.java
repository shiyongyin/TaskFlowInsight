package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.ComparisonTemplate;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareConstants;
import com.syy.taskflowinsight.tracking.compare.PatchFormat;

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
 *   <li>高级卖点：List 移动检测（detectMoves）</li>
 *   <li>高级卖点：模板 + Patch（ComparisonTemplate / PatchFormat）</li>
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
        DemoUI.section("8.2 渲染比对报告 — TFI.render(result, style)");
        renderReport();

        // 8.3 自定义比对
        DemoUI.section("8.3 自定义比对 — TFI.comparator().ignoring().compare()");
        customCompare();

        // 8.4 集合比对
        DemoUI.section("8.4 集合比对 — Entity 列表策略");
        collectionCompare();

        // 8.5 List 移动检测
        DemoUI.section("8.5 高级卖点 — List 移动检测（detectMoves）");
        moveDetection();

        // 8.6 模板 + Patch
        DemoUI.section("8.6 高级卖点 — 模板 + Patch（ComparisonTemplate / PatchFormat）");
        templateAndPatch();

        DemoUI.printSectionSummary("对象比对入门完成", getSummaryPoints());
    }

    /**
     * 场景 1：使用默认配置比较两个对象，展示最基本的 API 用法。
     */
    private void simpleCompare() {
        Address before = new Address("Shanghai", "SH", "100 Nanjing Road");
        Address after = new Address("Beijing", "BJ", "100 Nanjing Road");

        CompareResult result = TFI.compare(before, after);

        System.out.println("  比对结果: hasChanges=" + result.hasChanges());
        System.out.println("  变更数量: " + result.getChanges().size());
        result.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.getOldValue(), c.getNewValue()));
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
        String report = TFI.render(result, "standard");

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
                .ignoring("price")
                .compare(before, after);

        System.out.println("  忽略 price 后变更数: " + ignorePrice.getChanges().size());
        ignorePrice.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.getOldValue(), c.getNewValue()));

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
                .typeAware()
                .withStrategyName("ENTITY")
                .compare(before, after);

        String report = TFI.render(result, "standard");
        System.out.println("  === Entity 列表比对报告 ===");
        System.out.println(report);
    }

    /**
     * 场景 5：展示 List 的移动检测能力（MOVE vs CREATE+DELETE）。
     *
     * <p>核心点：仅在启用 {@link com.syy.taskflowinsight.api.ComparatorBuilder#detectMoves()} 后，
     * 才会把“重排”识别为 MOVE，而不是简单的删除+新增。</p>
     */
    private void moveDetection() {
        List<String> before = Arrays.asList("A", "B", "C", "D", "E");
        List<String> after = Arrays.asList("B", "A", "C", "E", "D");

        CompareResult noMove = TFI.comparator()
                .withStrategyName(CompareConstants.STRATEGY_LCS)
                .compare(before, after);

        CompareResult withMove = TFI.comparator()
                .withStrategyName(CompareConstants.STRATEGY_LCS)
                .detectMoves()
                .compare(before, after);

        System.out.println("  [LCS] 未开启 detectMoves：algorithmUsed=" + noMove.getAlgorithmUsed()
                + ", changes=" + noMove.getChangeCount());
        System.out.println("  [LCS] 开启 detectMoves：algorithmUsed=" + withMove.getAlgorithmUsed()
                + ", MOVE=" + withMove.getChangesByType(ChangeType.MOVE).size()
                + ", CREATE=" + withMove.getChangesByType(ChangeType.CREATE).size()
                + ", DELETE=" + withMove.getChangesByType(ChangeType.DELETE).size());

        System.out.println("\n  === detectMoves 输出示例 ===");
        System.out.println(TFI.render(withMove, "standard"));
    }

    /**
     * 场景 6：展示模板（AUDIT/FAST/DEBUG）与补丁（JSON Patch/Merge Patch）。
     *
     * <p>核心点：模板用于“少配点”，Patch 用于“可执行结果”（例如前端回放、审计落库）。</p>
     */
    private void templateAndPatch() {
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
                .withPatch(PatchFormat.JSON_PATCH)
                .compare(before, after);

        System.out.println("  [AUDIT] similarity=" + audit.getSimilarityPercent()
                + "%, changes=" + audit.getChangeCount());
        System.out.println("  [FAST ] similarity=" + fast.getSimilarityPercent()
                + "%, changes=" + fast.getChangeCount());

        String patch = debugWithPatch.getPatch();
        System.out.println("  [DEBUG] patch(JSON_PATCH) length=" + (patch == null ? 0 : patch.length()));
        if (patch != null && !patch.isBlank()) {
            int max = Math.min(300, patch.length());
            System.out.println("  patch(JSON_PATCH, 截断): " + patch.substring(0, max) + (patch.length() > max ? "..." : ""));
        }
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "学会了使用 TFI.compare(a, b) 比对两个对象",
                "学会了使用 TFI.render(result, style) 生成 Markdown 报告",
                "掌握了 TFI.comparator() 的 ignoring / withMaxDepth 等自定义选项",
                "了解了 typeAware + ENTITY 策略进行集合比对",
                "掌握了 detectMoves 在 List 重排场景下的价值（MOVE vs CREATE+DELETE）",
                "了解了 ComparisonTemplate 与 PatchFormat 的使用场景"
        );
    }
}
