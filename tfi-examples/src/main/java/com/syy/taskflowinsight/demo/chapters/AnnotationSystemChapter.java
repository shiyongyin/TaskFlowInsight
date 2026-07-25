package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.Arrays;
import java.util.List;

/**
 * 第 9 章：注解系统 — TFI 类型系统注解的实战演示。
 *
 * <p>本章通过递进场景，帮助开发者掌握 TFI 注解体系的核心能力：
 * <ol>
 *   <li>{@code @Entity} + {@code @Key} — 实体标识与主键匹配</li>
 *   <li>{@code @ValueObject} — 值对象语义比对</li>
 *   <li>{@code @DiffIgnore} — 排除不关注的字段</li>
 *   <li>{@code ComparePolicy} — 显式数值容差</li>
 *   <li>注解组合 — 综合运用多个注解的最佳实践</li>
 * </ol>
 *
 * <p><b>学习目标：</b>理解 TFI 类型系统如何让比对引擎"理解"业务语义，
 * 从而在集合比对、字段过滤、数值精度等场景下给出更准确的结果。
 *
 * @since 4.0.0
 */
public class AnnotationSystemChapter implements DemoChapter {

    @Override
    public int getChapterNumber() { return 9; }

    @Override
    public String getTitle() { return "注解系统"; }

    @Override
    public String getDescription() {
        return "@Entity/@ValueObject/@Key/@DiffIgnore 与 Policy 实战演示";
    }

    @Override
    public void run() {
        DemoUI.printChapterHeader(9, getTitle(), getDescription());
        TFI.enable();

        DemoUI.section("9.1 @Entity + @Key — 实体标识与主键匹配");
        entityAndKeyDemo();

        DemoUI.section("9.2 @ValueObject — 值对象语义比对");
        valueObjectDemo();

        DemoUI.section("9.3 @DiffIgnore — 排除不关注的字段");
        diffIgnoreDemo();

        DemoUI.section("9.4 ComparePolicy — 数值容差比较");
        numericPrecisionDemo();

        DemoUI.section("9.5 注解组合 — 综合实战");
        combinedAnnotationsDemo();

        DemoUI.printSectionSummary("注解系统演示完成", getSummaryPoints());
    }

    /**
     * 场景 1：展示 {@code @Entity} + {@code @Key} 如何让集合比对按主键匹配而非按索引。
     *
     * <p>核心点：标注了 {@code @Entity} 和 {@code @Key} 的 Product 在 List 比对时，
     * 会按 {@code id} 字段匹配，而非按列表下标逐一比较。</p>
     */
    private void entityAndKeyDemo() {
        System.out.println("  Product 类标注了 @Entity, id 字段标注了 @Key");
        System.out.println("  → List 比对按 id 匹配，而非按索引");
        System.out.println();

        List<Product> before = Arrays.asList(
                new Product(1L, "iPhone 15", 7999.0, 100),
                new Product(2L, "MacBook Pro", 14999.0, 50),
                new Product(3L, "AirPods Pro", 1799.0, 200)
        );

        List<Product> after = Arrays.asList(
                new Product(2L, "MacBook Pro M4", 16999.0, 30),
                new Product(1L, "iPhone 15 Pro", 8999.0, 80),
                new Product(4L, "iPad Air", 4599.0, 150)
        );

        CompareResult result = TFI.comparator()
                .compare(before, after);

        System.out.println("  比对结果 (typeAware):");
        System.out.println(TFI.render(result, RenderOptions.markdown()));
    }

    /**
     * 场景 2：展示 {@code @ValueObject} 语义 — 没有身份标识，按全字段比较。
     *
     * <p>核心点：{@code Address} 标注了 {@code @ValueObject}，比对时不需要主键，
     * 任何字段变化都算"不同的值对象"。</p>
     */
    private void valueObjectDemo() {
        System.out.println("  Address 类标注了 @ValueObject");
        System.out.println("  → 按全部字段比较，任何字段变化即视为不同");
        System.out.println();

        Address before = new Address("Shanghai", "SH", "100 Nanjing Road");
        Address after = new Address("Shanghai", "SH", "200 Huaihai Road");

        CompareResult result = TFI.compare(before, after);

        System.out.println("  isDifferent=" + result.isDifferent());
        result.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.beforeValue().orElse(null), c.afterValue().orElse(null)));
    }

    /**
     * 场景 3：展示 {@code @DiffIgnore} 如何排除不关注的字段。
     *
     * <p>核心点：内部类 {@code PricedItem} 的 {@code internalCode} 字段标注了
     * {@code @DiffIgnore}，比对时该字段被自动跳过。</p>
     */
    private void diffIgnoreDemo() {
        System.out.println("  PricedItem.internalCode 标注了 @DiffIgnore");
        System.out.println("  → 比对时自动跳过该字段");
        System.out.println();

        PricedItem before = new PricedItem("SKU-001", "Widget", 29.99, "INTERNAL-A");
        PricedItem after = new PricedItem("SKU-001", "Widget Pro", 39.99, "INTERNAL-B");

        CompareResult result = TFI.compare(before, after);

        System.out.println("  变更数量: " + result.getChanges().size() + " (internalCode 被忽略)");
        result.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.beforeValue().orElse(null), c.afterValue().orElse(null)));
    }

    /**
     * 场景 4：展示 Policy 如何显式控制数值比较精度。
     *
     * <p>核心点：容差属于单次比较的 Policy/Options，不再隐藏在模型字段上。</p>
     */
    private void numericPrecisionDemo() {
        System.out.println("  PreciseProduct.price 使用 ComparePolicy 显式容差");
        System.out.println();

        PreciseProduct before = new PreciseProduct("A", 29.991);
        PreciseProduct after = new PreciseProduct("A", 29.999);

        CompareResult result = TFI.compare(before, after);

        System.out.println("  29.991 vs 29.999 (scale=2): isDifferent=" + result.isDifferent());
        System.out.println("  变更数: " + result.getChanges().size());

        PreciseProduct after2 = new PreciseProduct("A", 30.50);
        CompareResult result2 = TFI.compare(before, after2);

        System.out.println("  29.991 vs 30.50 (scale=2): isDifferent=" + result2.isDifferent());
        result2.getChanges().forEach(c ->
                System.out.printf("    - %s: \"%s\" -> \"%s\"%n",
                        c.getFieldName(), c.beforeValue().orElse(null), c.afterValue().orElse(null)));
    }

    /**
     * 场景 5：综合运用多个注解，展示真实业务场景。
     *
     * <p>核心点：同时使用 {@code @Entity}、{@code @Key}、{@code @DiffIgnore}
     * 演示一个完整的业务对象比对流程。</p>
     */
    private void combinedAnnotationsDemo() {
        System.out.println("  综合运用: @Entity + @Key + @DiffIgnore + Product 模型");
        System.out.println();

        Product before = new Product(1L, "iPhone 15", 7999.0, 100);
        Product after = new Product(1L, "iPhone 15 Pro", 8999.0, 80);

        CompareResult result = TFI.comparator()
                .compare(before, after);

        String report = TFI.render(result, RenderOptions.markdown());
        System.out.println("  === 综合比对报告 ===");
        System.out.println(report);
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "学会了 @Entity + @Key 让集合按主键匹配而非按索引",
                "理解了 @ValueObject 的全字段比较语义",
                "掌握了 @DiffIgnore 排除不关注字段的用法",
                "了解了 ComparePolicy 控制数值比较精度",
                "综合运用多注解处理真实业务对象比对"
        );
    }

    // ────────────────────────── 内部演示模型 ──────────────────────────

    /**
     * 演示 {@code @DiffIgnore} 的内部模型。
     */
    static class PricedItem {
        private String sku;
        private String name;
        private double price;
        @DiffIgnore
        private String internalCode;

        PricedItem(String sku, String name, double price, String internalCode) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.internalCode = internalCode;
        }

        public String getSku() { return sku; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getInternalCode() { return internalCode; }
    }

    /**
     * 演示数值字段模型。
     */
    static class PreciseProduct {
        private String name;
        private double price;

        PreciseProduct(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public String getName() { return name; }
        public double getPrice() { return price; }
    }
}
