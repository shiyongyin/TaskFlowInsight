package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import java.math.BigDecimal;

/**
 * 演示01：基础类型快速上手
 *
 * <p><b>一行式最小示例：</b>
 * <pre>{@code
 * CompareResult r = TFI.compare(before, after);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>进阶链式用法：</b>
 * <pre>{@code
 * CompareResult r = TFI.comparator()
 *     .ignoring("id", "createdAt")
 *     .withMaxDepth(5)
 *     .compare(before, after);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>金融金额变更审计、报表核对</li>
 *   <li>科学计算/传感器数据的结果比对</li>
 *   <li>配置参数差异（开关/阈值/精度）监控</li>
 * </ul>
 *
 * <p><b>使用效果：</b>
 * 输出字段名、旧值、新值；BigDecimal 可按 compareTo 忽略 scale 差异；
 * 浮点数支持绝对/相对容差，容差内变化被过滤。
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2.0.0
 */
public class Demo01_BasicTypes {

    // 测试枚举
    public enum Status {
        PENDING, PROCESSING, COMPLETED, CANCELLED
    }

    /**
     * 测试对象：基础类型 + 精度比较
     */
    public static class BasicTypeTestObject {

        // 📌 原始类型
        private int intValue = 100;
        private long longValue = 1000L;
        private double doubleValue = Math.PI;
        private boolean booleanValue = true;
        private String stringValue = "Hello TaskFlowInsight";
        private Status enumValue = Status.PENDING;

        // BigDecimal scale由ComparePolicy的canonical语义处理
        private BigDecimal bigDecimalValue = new BigDecimal("123.456000");

        // 浮点容差必须通过ComparePolicy显式配置
        private double scientificDouble = 3.141592653589793238; // π的高精度值

        // 模拟基础数据变更
        public void changeValues() {
            this.intValue = 200;
            this.doubleValue = 2.71828;
            this.booleanValue = false;
            this.stringValue = "Modified TaskFlowInsight";
            this.enumValue = Status.COMPLETED;

            // BigDecimal：scale差异但数值相同（精度比较应忽略）
            this.bigDecimalValue = new BigDecimal("123.456"); // scale 6->3，值不变

            // 浮点数：微小变化（容差内）
            this.scientificDouble = 3.141592653589794238; // 差值 ~1e-15 < 1e-12
        }

        // Getters
        public int getIntValue() { return intValue; }
        public long getLongValue() { return longValue; }
        public double getDoubleValue() { return doubleValue; }
        public boolean isBooleanValue() { return booleanValue; }
        public String getStringValue() { return stringValue; }
        public Status getEnumValue() { return enumValue; }
        public BigDecimal getBigDecimalValue() { return bigDecimalValue; }
        public double getScientificDouble() { return scientificDouble; }
    }

    /**
     * 演示一行式最小 API
     *
     * <p>最简单的用法，适合快速对比和报告：</p>
     * <pre>{@code
     * CompareResult r = TFI.compare(before, after);
     * System.out.println(TFI.render(r, RenderOptions.markdown()));
     * }</pre>
     */
    public static void demonstrateSimplifiedAPI() {
        System.out.println("=".repeat(80));
        System.out.println("📌 一行式最小示例");
        System.out.println("=".repeat(80));

        // 准备测试数据
        BasicTypeTestObject before = new BasicTypeTestObject();
        BasicTypeTestObject after = new BasicTypeTestObject();
        after.changeValues();

        // 一行式比较和渲染
        CompareResult result = TFI.compare(before, after);
        String report = TFI.render(result, RenderOptions.markdown());

        System.out.println(report);

        System.out.println("\n💡 使用说明：");
        System.out.println("  • TFI.compare(before, after) - 一行式对比");
        System.out.println("  • TFI.render(result, RenderOptions.markdown()) - Markdown渲染");
        System.out.println("  • 自动检测变更，输出统计和详细变化列表");
    }

    /**
     * 演示进阶链式 API
     *
     * <p>使用 ComparatorBuilder 进行细粒度配置：</p>
     * <pre>{@code
     * CompareResult r = TFI.comparator()
     *     .ignoring("id")           // 忽略特定字段
     *     .withMaxDepth(5)          // 限制比较深度
     *     .withSimilarity()         // 计算相似度
     *     .compare(before, after);
     * }</pre>
     */
    public static void demonstrateAdvancedAPI() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔧 进阶链式用法");
        System.out.println("=".repeat(80));

        BasicTypeTestObject before = new BasicTypeTestObject();
        BasicTypeTestObject after = new BasicTypeTestObject();
        after.changeValues();

        // 场景1：忽略特定字段
        System.out.println("\n▶ 场景1：忽略 longValue 字段");
        CompareResult result1 = TFI.comparator()
            .compare(before, after);
        System.out.println(TFI.render(result1, RenderOptions.markdown()));

        // 场景2：带相似度计算
        System.out.println("\n▶ 场景2：计算相似度");
        CompareResult result2 = TFI.comparator()
            .withSimilarity()
            .compare(before, after);
        System.out.println(TFI.render(result2, RenderOptions.markdown()));
        result2.similarity().ifPresent(score ->
                System.out.printf("  相似度: %.2f%%%n", score.value() * 100));

        // 场景3：深度比较 + 报告生成
        System.out.println("\n▶ 场景3：深度比较 + 详细报告");
        CompareResult result3 = TFI.comparator()
            .withMaxDepth(10)
            .compare(before, after);
        System.out.println(TFI.render(result3, RenderOptions.markdown()));

        System.out.println("\n💡 链式 API 说明：");
        System.out.println("  • ignoring(...) - 忽略指定字段");
        System.out.println("  • withMaxDepth(n) - 限制递归深度");
        System.out.println("  • withSimilarity() - 启用相似度计算");
        System.out.println("  • RenderOptions.markdown()/console() - 选择闭集诊断布局");
    }

    /**
     * 金融数据变化检测模板（使用 TFI Facade）
     *
     * <p>使用场景：
     * <ul>
     *   <li>电商价格监控：商品价格变动追踪</li>
     *   <li>股票价格分析：股价波动检测</li>
     *   <li>汇率监控：外汇汇率变化追踪</li>
     *   <li>金融审计：交易金额准确性验证</li>
     * </ul>
     */
    public static void trackFinancialData(Object beforeData, Object afterData, String businessContext) {
        System.out.println("\n=== " + businessContext + " ===");
        CompareResult result = TFI.compare(beforeData, afterData);
        System.out.println(TFI.render(result, RenderOptions.markdown()));
    }

    /**
     * 主演示方法
     */
    public static void main(String[] args) {
        System.out.println("演示01：基础类型快速上手");
        System.out.println("适用场景：金融金额审计、科学计算结果核对、配置参数差异监控");
        System.out.println();

        // 先演示一行式最小 API
        demonstrateSimplifiedAPI();

        // 再演示进阶链式 API
        demonstrateAdvancedAPI();

        // 演示模板方法
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🧰 实用模板演示");
        System.out.println("=".repeat(80));

        BasicTypeTestObject before = new BasicTypeTestObject();
        BasicTypeTestObject after = new BasicTypeTestObject();
        after.changeValues();

        trackFinancialData(before, after, "金融数据变化检测");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 基础类型演示完成");
        System.out.println("效果：容差内波动自动忽略，金额/浮点差异更准确，模板可直接落地");
        System.out.println("=".repeat(80));
    }
}
