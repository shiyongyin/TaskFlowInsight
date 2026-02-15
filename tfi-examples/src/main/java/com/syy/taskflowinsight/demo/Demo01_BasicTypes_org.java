package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.annotation.NumericPrecision;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础类型和精度比较演示
 *
 * 展示原始类型、包装类型、BigDecimal和浮点数的变更检测及精度比较功能。
 * 适用于金融系统、科学计算、配置管理等需要精确数值比较的场景。
 *
 * 核心特性：
 * - BigDecimal精度比较（COMPARE_TO vs equals）
 * - 浮点数容差比较（可配置绝对和相对容差）
 * - 原始类型和包装类型直观检测
 * - 提供可复制的代码模板
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class Demo01_BasicTypes_org {

    // 测试枚举
    public enum Status {
        PENDING, PROCESSING, COMPLETED, CANCELLED
    }

    // 测试对象：基础类型 + 精度比较
    public static class BasicTypeTestObject {

        // 📌 原始类型
        private int intValue = 100;
        private long longValue = 1000L;
        private double doubleValue = 3.14159;
        private float floatValue = 2.718f;
        private boolean booleanValue = true;
        private char charValue = 'A';
        private byte byteValue = 127;
        private short shortValue = 32767;

        // 📦 包装类型
        private Integer integerValue = 200;
        private Long longWrapperValue = 2000L;
        private Double doubleWrapperValue = 6.28318;
        private Float floatWrapperValue = 1.414f;
        private Boolean booleanWrapperValue = false;
        private Character characterValue = 'B';
        private Byte byteWrapperValue = -128;
        private Short shortWrapperValue = -32768;

        // 📝 字符串和枚举
        private String stringValue = "Hello TaskFlowInsight";
        private Status enumValue = Status.PENDING;

        // 💰 BigDecimal精度比较（CARD-CT-007规范）
        @NumericPrecision(compareMethod = "COMPARE_TO") // 推荐：忽略scale差异
        private BigDecimal bigDecimalValue = new BigDecimal("123.456000");

        // 🔬 浮点数精度比较（科学计算级精度）
        @NumericPrecision(absoluteTolerance = 1e-12, relativeTolerance = 1e-9)
        private double scientificDouble = 3.141592653589793238; // π的高精度值

        // 🎯 Float精度比较（适合单精度场景）
        @NumericPrecision(absoluteTolerance = 1e-6, relativeTolerance = 1e-6)
        private float approximateFloat = 2.7182818f; // e的近似值

        // 模拟基础数据变更
        public void changeValues() {
            // 原始类型变更
            this.intValue = 200;
            this.doubleValue = 2.71828;
            this.booleanValue = false;

            // 包装类型变更
            this.integerValue = 300;
            this.characterValue = 'C';

            // 字符串和枚举变更
            this.stringValue = "Modified TaskFlowInsight";
            this.enumValue = Status.COMPLETED;

            // BigDecimal：scale差异但数值相同（精度比较应忽略）
            this.bigDecimalValue = new BigDecimal("123.456"); // scale 6->3，值不变

            // 浮点数：微小变化（容差内）
            this.scientificDouble = 3.141592653589794238; // 差值 ~1e-15 < 1e-12
            this.approximateFloat = 2.7182819f; // 差值 ~1e-7 < 1e-6
        }

        // Getters
        public int getIntValue() { return intValue; }
        public long getLongValue() { return longValue; }
        public double getDoubleValue() { return doubleValue; }
        public float getFloatValue() { return floatValue; }
        public boolean isBooleanValue() { return booleanValue; }
        public char getCharValue() { return charValue; }
        public byte getByteValue() { return byteValue; }
        public short getShortValue() { return shortValue; }

        public Integer getIntegerValue() { return integerValue; }
        public Long getLongWrapperValue() { return longWrapperValue; }
        public Double getDoubleWrapperValue() { return doubleWrapperValue; }
        public Float getFloatWrapperValue() { return floatWrapperValue; }
        public Boolean getBooleanWrapperValue() { return booleanWrapperValue; }
        public Character getCharacterValue() { return characterValue; }
        public Byte getByteWrapperValue() { return byteWrapperValue; }
        public Short getShortWrapperValue() { return shortWrapperValue; }

        public String getStringValue() { return stringValue; }
        public Status getEnumValue() { return enumValue; }
        public BigDecimal getBigDecimalValue() { return bigDecimalValue; }
        public double getScientificDouble() { return scientificDouble; }
        public float getApproximateFloat() { return approximateFloat; }
    }


    /**
     * 金融系统价格变化检测模板
     *
     * 使用场景：
     * - 电商价格监控：商品价格变动追踪
     * - 股票价格分析：股价波动检测
     * - 汇率监控：外汇汇率变化追踪
     * - 金融审计：交易金额准确性验证
     *
     * @param beforeData 变更前的业务对象（需包含BigDecimal价格字段）
     * @param afterData 变更后的业务对象
     * @param businessContext 业务上下文标识，用于日志和追踪
     *
     * 输出说明：
     * - 控制台输出所有检测到的数值变化
     * - 格式：字段名 | 原值 | 新值 | 变更类型
     * - 自动过滤BigDecimal的scale差异（仅关注实际数值）
     *
     * 使用示例：
     * <pre>{@code
     * Product before = new Product(new BigDecimal("99.00"));
     * Product after = new Product(new BigDecimal("99.99"));
     * trackFinancialData(before, after, "ProductPriceMonitor");
     * }</pre>
     */
    public static void trackFinancialData(Object beforeData, Object afterData, String businessContext) {
        TFI.enable();
        Map<String, Object> beforeSnapshot = createSnapshotForObject(beforeData);
        Map<String, Object> afterSnapshot = createSnapshotForObject(afterData);

        // 启用精度比较模式
        DiffDetector.setPrecisionCompareEnabled(true);
        List<ChangeRecord> changes = DiffDetector.diff(businessContext, beforeSnapshot, afterSnapshot);

        System.out.println("=== 金融数据变化检测结果 ===");
        for (ChangeRecord change : changes) {
            System.out.printf("字段：%s | 原值：%s | 新值：%s | 类型：%s%n",
                    change.getFieldName(), change.getOldValue(), change.getNewValue(), change.getChangeType());
        }
    }

    /**
     * 配置参数变更监控模板
     *
     * 使用场景：
     * - 系统配置监控：application.yml、配置中心变更
     * - 环境变量追踪：生产环境配置一致性检查
     * - 功能开关监控：特性开关状态变化
     * - 运行时参数：JVM参数、系统属性变更
     *
     * @param currentConfig 当前配置Map，key为配置项名称，value为配置值
     * @param newConfig 新配置Map
     * @return 返回检测到的配置变更列表，每个ChangeRecord包含变更详情
     *
     * 返回结果说明：
     * - ChangeRecord.getFieldName()：配置项名称
     * - ChangeRecord.getOldValue()：原始配置值
     * - ChangeRecord.getNewValue()：新配置值
     * - ChangeRecord.getChangeType()：MODIFIED/ADDED/REMOVED
     *
     * 使用示例：
     * <pre>{@code
     * Map<String, Object> current = Map.of("port", 8080, "debug", false);
     * Map<String, Object> updated = Map.of("port", 9090, "debug", true);
     * List<ChangeRecord> changes = detectConfigChanges(current, updated);
     * changes.forEach(change ->
     *     log.info("Config changed: {} from {} to {}",
     *              change.getFieldName(), change.getOldValue(), change.getNewValue()));
     * }</pre>
     */
    public static List<ChangeRecord> detectConfigChanges(Map<String, Object> currentConfig,
                                                         Map<String, Object> newConfig) {
        TFI.enable();
        // 对于配置参数，通常使用标准比较模式
        DiffDetector.setPrecisionCompareEnabled(false);
        return DiffDetector.diff("ConfigChange", currentConfig, newConfig);
    }

    /**
     * 科学计算结果验证模板
     *
     * 使用场景：
     * - 算法结果验证：机器学习模型输出比较
     * - 实验数据分析：传感器测量值差异检测
     * - 数值仿真：仿真结果与理论值对比
     * - 单元测试：浮点数计算结果断言
     *
     * @param expected 期望值（理论值或基准值）
     * @param actual 实际值（计算结果或测量值）
     * @param tolerance 容差阈值，推荐值：
     *                  - 科学计算：1e-12
     *                  - 工程应用：1e-6
     *                  - 传感器数据：根据精度调整
     * @return true表示数值发生了显著变化（超出容差），false表示在容差范围内
     *
     * 使用示例：
     * <pre>{@code
     * double piCalculated = calculatePi(); // 算法计算π值
     * double piExpected = Math.PI;
     * boolean hasSignificantError = isCalculationResultChanged(
     *     piExpected, piCalculated, 1e-12);
     * if (hasSignificantError) {
     *     log.warn("π calculation accuracy declined");
     * }
     * }</pre>
     */
    public static boolean isCalculationResultChanged(double expected, double actual, double tolerance) {
        NumericCompareStrategy strategy = new NumericCompareStrategy();
        return !strategy.compareFloats(expected, actual, tolerance, tolerance);
    }

    // 通用快照创建方法，使用反射自动提取所有字段
    private static Map<String, Object> createSnapshotForObject(Object obj) {
        Map<String, Object> snapshot = new HashMap<>();
        try {
            for (java.lang.reflect.Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                snapshot.put(field.getName(), field.get(obj));
            }
        } catch (Exception e) {
            System.err.println("创建快照失败: " + e.getMessage());
        }
        return snapshot;
    }

    /**
     * 主演示方法 - 展示基础类型和精度比较的完整功能
     *
     * 运行输出说明：
     * 1. 程序启动信息和演示标题
     * 2. 基础类型变更检测结果（按类型分类显示）
     * 3. 精度比较模式结果（过滤容差内变化）
     * 4. BigDecimal比較策略详细分析
     * 5. 浮点数精度比较详细分析
     * 6. 完成总结和建议
     */
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("演示01：基础类型和精度比较 - 完整功能演示");
        System.out.println("提示：本演示将展示所有数值类型的变更检测和精度比较功能");
        System.out.println("================================================================================");

        // 启用TFI
        TFI.enable();

        // 创建测试数据
        BasicTypeTestObject before = new BasicTypeTestObject();
        BasicTypeTestObject after = new BasicTypeTestObject();
        after.changeValues();

        // 创建快照
        java.util.Map<String, Object> beforeSnapshot = createBasicSnapshot(before);
        java.util.Map<String, Object> afterSnapshot = createBasicSnapshot(after);

        // 演示基础类型变更检测
        demoBasicTypeDetection(beforeSnapshot, afterSnapshot);

        // 演示精度比较功能
        demoPrecisionComparison(before, after, beforeSnapshot, afterSnapshot);

        // 演示实用代码模板的使用
        demoTemplateMethodsUsage(before, after);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 基础类型和精度比较演示完成");
        System.out.println();
        System.out.println("📝 输出结果说明：");
        System.out.println("   • '基础类型变更检测'：显示所有数值字段的变化");
        System.out.println("   • '精度比較功能'：过滤BigDecimal和浮点数的微小差异");
        System.out.println("   • '详细分析'：展示精度比较的具体算法和参数");
        System.out.println();
        System.out.println("💡 生产使用建议：");
        System.out.println("   • 金融系统：使用@NumericPrecision注解配置 BigDecimal 精度");
        System.out.println("   • 科学计算：设置适当的absoluteTolerance和relativeTolerance");
        System.out.println("   • 数值比较：使用trackFinancialData()模板快速集成");
        System.out.println("=".repeat(80));
    }

    /**
     * 演示基础类型变更检测（标准模式）
     *
     * 输出说明：
     * - 按类型分组显示变化：原始类型、包装类型、字符串和枚举
     * - 每个变化包含：字段名、原值、新值、变更类型
     * - 显示检测到的变更总数
     */
    private static void demoBasicTypeDetection(java.util.Map<String, Object> before,
                                               java.util.Map<String, Object> after) {
        System.out.println("\n🎯 基础类型变更检测");
        System.out.println("-".repeat(60));

        // 使用标准模式进行基础比较
        DiffDetector.setPrecisionCompareEnabled(false);
        java.util.List<ChangeRecord> changes = DiffDetector.diff("BasicTypes", before, after);

        // 按类型分类显示（保持原有风格）
        System.out.println("\n📌 原始类型变更：");
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldName();
            if (isPrimitiveField(fieldName)) {
                System.out.printf("  - %s: '%s' → '%s' [%s]%n",
                        fieldName, change.getOldValue(), change.getNewValue(), change.getChangeType());
            }
        }

        System.out.println("\n📦 包装类型变更：");
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldName();
            if (isWrapperField(fieldName)) {
                System.out.printf("  - %s: '%s' → '%s' [%s]%n",
                        fieldName, change.getOldValue(), change.getNewValue(), change.getChangeType());
            }
        }

        System.out.println("\n📝 字符串和枚举类型变更：");
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldName();
            if (fieldName.contains("string") || fieldName.contains("enum")) {
                System.out.printf("  - %s: '%s' → '%s' [%s]%n",
                        fieldName, change.getOldValue(), change.getNewValue(), change.getChangeType());
            }
        }

        System.out.printf("\n💡 标准模式检测到 %d 个变更%n", changes.size());
    }

    /**
     * 演示精度比较功能（高级模式）
     *
     * 输出说明：
     * - 显示过滤后的变更数量（BigDecimal和浮点数微小差异被忽略）
     * - BigDecimal比较：显示是否检测到scale差异变化
     * - 浮点数比较：显示是否检测到容差内的微小变化
     * - 详细精度分析：展示具体的比较算法和参数
     */
    private static void demoPrecisionComparison(BasicTypeTestObject before, BasicTypeTestObject after,
                                                java.util.Map<String, Object> beforeSnapshot,
                                                java.util.Map<String, Object> afterSnapshot) {
        System.out.println("\n🔬 精度比较功能演示");
        System.out.println("-".repeat(60));

        // 设置对象类型用于精度比较
        DiffDetector.setCurrentObjectClass(BasicTypeTestObject.class);

        // 精度比较模式
        DiffDetector.setPrecisionCompareEnabled(true);
        java.util.List<ChangeRecord> precisionChanges = DiffDetector.diff("PrecisionMode",
                beforeSnapshot, afterSnapshot);

        System.out.printf("✨ 精度模式检测到 %d 个变更（过滤了BigDecimal和浮点数的微小差异）%n",
                precisionChanges.size());

        System.out.println("\n💰 BigDecimal精度比较：");
        boolean foundBigDecimal = false;
        for (ChangeRecord change : precisionChanges) {
            if (change.getFieldName().equals("bigDecimalValue")) {
                displayChange(change);
                foundBigDecimal = true;
                break;
            }
        }

        if (!foundBigDecimal) {
            System.out.println("  ✅ 未检测到BigDecimal变更（scale差异被忽略）");
            demonstrateBigDecimalStrategy(before, after);
        }

        System.out.println("\n🎯 浮点数精度比较：");
        boolean foundScientific = false, foundFloat = false;
        for (ChangeRecord change : precisionChanges) {
            String fieldName = change.getFieldName();
            if (fieldName.equals("scientificDouble")) {
                displayChange(change);
                foundScientific = true;
            } else if (fieldName.equals("approximateFloat")) {
                displayChange(change);
                foundFloat = true;
            }
        }

        if (!foundScientific && !foundFloat) {
            System.out.println("  ✅ 未检测到浮点数变更（容差内变化被忽略）");
            demonstrateFloatPrecision(before, after);
        } else {
            if (!foundScientific) {
                System.out.println("  ✅ Scientific Double: 容差内变化被忽略");
            }
            if (!foundFloat) {
                System.out.println("  ✅ Approximate Float: 容差内变化被忽略");
            }
            // 即使有检测到的变更，也展示精度比较的详细分析
            demonstrateFloatPrecision(before, after);
        }
    }

    /**
     * 演示BigDecimal比较策略
     */
    private static void demonstrateBigDecimalStrategy(BasicTypeTestObject before, BasicTypeTestObject after) {
        BigDecimal bd1 = before.getBigDecimalValue();
        BigDecimal bd2 = after.getBigDecimalValue();

        System.out.printf("    值对比：%s vs %s%n", bd1, bd2);
        System.out.printf("    Scale：%d vs %d%n", bd1.scale(), bd2.scale());

        NumericCompareStrategy strategy = new NumericCompareStrategy();
        boolean compareToResult = strategy.compareBigDecimals(bd1, bd2,
                NumericCompareStrategy.CompareMethod.COMPARE_TO, 0);
        boolean equalsResult = bd1.equals(bd2);

        System.out.printf("    compareTo(): %s ✅%n", compareToResult ? "相等" : "不等");
        System.out.printf("    equals():    %s%n", equalsResult ? "相等" : "不等");
        System.out.println("    💡 CARD-CT-007规范：推荐使用compareTo忽略scale差异");
    }

    /**
     * 演示浮点数精度比较（集成最佳实践案例）
     */
    private static void demonstrateFloatPrecision(BasicTypeTestObject before, BasicTypeTestObject after) {
        System.out.println("\n    🔬 详细浮点数精度分析：");

        NumericCompareStrategy strategy = new NumericCompareStrategy();

        // Double精度比较（科学计算级）
        double d1 = before.getScientificDouble();
        double d2 = after.getScientificDouble();
        double doubleDiff = Math.abs(d1 - d2);

        System.out.printf("    Scientific Double: %.15f vs %.15f%n", d1, d2);
        System.out.printf("    绝对差值: %.2e%n", doubleDiff);

        boolean doubleEqual = strategy.compareFloats(d1, d2, 1e-12, 1e-9);
        System.out.printf("    精度比较: %s%n", doubleEqual ? "✅ 相等（容差内）" : "❌ 不等");
        System.out.println("    容差: ε_abs=1e-12, ε_rel=1e-9 (IEEE双精度标准)");

        // Float精度比较（单精度场景）
        float f1 = before.getApproximateFloat();
        float f2 = after.getApproximateFloat();
        float floatDiff = Math.abs(f1 - f2);

        System.out.printf("\n    Approximate Float: %.7f vs %.7f%n", f1, f2);
        System.out.printf("    绝对差值: %.2e%n", floatDiff);

        boolean floatEqual = strategy.compareFloats(f1, f2, 1e-6, 1e-6);
        System.out.printf("    精度比较: %s%n", floatEqual ? "✅ 相等（容差内）" : "❌ 不等");
        System.out.println("    容差: ε_abs=1e-6, ε_rel=1e-6 (适合单精度)");

        System.out.println("\n    📏 精度选择原则：");
        System.out.println("      • Double: 科学计算、金融精度场景 (1e-12级)");
        System.out.println("      • Float: 图形渲染、游戏开发场景 (1e-6级)");
        System.out.println("      • 根据业务需求和数据精度调整容差参数");
        System.out.println("      • CARD-CT-007默认: ε_abs=1e-12, ε_rel=1e-9");
    }

    private static void displayChange(ChangeRecord change) {
        System.out.printf("  - %s: %s → %s [%s]%n",
                change.getFieldName(),
                change.getOldValue(),
                change.getNewValue(),
                change.getChangeType());
    }

    private static boolean isPrimitiveField(String fieldName) {
        return fieldName.equals("intValue") || fieldName.equals("longValue") ||
                fieldName.equals("doubleValue") || fieldName.equals("floatValue") ||
                fieldName.equals("booleanValue") || fieldName.equals("charValue") ||
                fieldName.equals("byteValue") || fieldName.equals("shortValue");
    }

    private static boolean isWrapperField(String fieldName) {
        return fieldName.equals("integerValue") || fieldName.equals("longWrapperValue") ||
                fieldName.equals("doubleWrapperValue") || fieldName.equals("floatWrapperValue") ||
                fieldName.equals("booleanWrapperValue") || fieldName.equals("characterValue") ||
                fieldName.equals("byteWrapperValue") || fieldName.equals("shortWrapperValue");
    }

    /**
     * 创建基础类型快照
     */
    private static java.util.Map<String, Object> createBasicSnapshot(BasicTypeTestObject obj) {
        java.util.Map<String, Object> snapshot = new java.util.HashMap<>();

        // 基础类型
        snapshot.put("intValue", obj.getIntValue());
        snapshot.put("longValue", obj.getLongValue());
        snapshot.put("doubleValue", obj.getDoubleValue());
        snapshot.put("floatValue", obj.getFloatValue());
        snapshot.put("booleanValue", obj.isBooleanValue());
        snapshot.put("charValue", obj.getCharValue());
        snapshot.put("byteValue", obj.getByteValue());
        snapshot.put("shortValue", obj.getShortValue());

        // 包装类型
        snapshot.put("integerValue", obj.getIntegerValue());
        snapshot.put("longWrapperValue", obj.getLongWrapperValue());
        snapshot.put("doubleWrapperValue", obj.getDoubleWrapperValue());
        snapshot.put("floatWrapperValue", obj.getFloatWrapperValue());
        snapshot.put("booleanWrapperValue", obj.getBooleanWrapperValue());
        snapshot.put("characterValue", obj.getCharacterValue());
        snapshot.put("byteWrapperValue", obj.getByteWrapperValue());
        snapshot.put("shortWrapperValue", obj.getShortWrapperValue());

        // 字符串和枚举
        snapshot.put("stringValue", obj.getStringValue());
        snapshot.put("enumValue", obj.getEnumValue());

        // 精度比较字段
        snapshot.put("bigDecimalValue", obj.getBigDecimalValue());
        snapshot.put("scientificDouble", obj.getScientificDouble());
        snapshot.put("approximateFloat", obj.getApproximateFloat());

        return snapshot;
    }

    /**
     * 演示实用代码模板的使用
     *
     * 输出说明：
     * - 实际调用前面定义的模板方法
     * - 展示如何在实际业务中使用这些方法
     * - 验证模板方法的功能正确性
     */
    private static void demoTemplateMethodsUsage(BasicTypeTestObject before, BasicTypeTestObject after) {
        System.out.println("\n🧰 实用代码模板演示");
        System.out.println("-".repeat(60));

        System.out.println("\n💰 模板1：金融数据变化检测");
        trackFinancialData(before, after, "DemoFinancialData");

        System.out.println("\n⚙️ 模板2：配置参数变更监控");
        Map<String, Object> currentConfig = Map.of(
                "precision", before.getBigDecimalValue(),
                "threshold", before.getDoubleValue(),
                "enabled", before.isBooleanValue()
        );
        Map<String, Object> newConfig = Map.of(
                "precision", after.getBigDecimalValue(),
                "threshold", after.getDoubleValue(),
                "enabled", after.isBooleanValue()
        );
        List<ChangeRecord> configChanges = detectConfigChanges(currentConfig, newConfig);
        System.out.printf("检测到 %d 个配置变更%n", configChanges.size());
        for (ChangeRecord change : configChanges) {
            System.out.printf("  配置项：%s | %s → %s%n",
                    change.getFieldName(), change.getOldValue(), change.getNewValue());
        }

        System.out.println("\n🔬 模板3：科学计算结果验证");
        double expected = Math.PI;
        double calculated = before.getScientificDouble();
        boolean hasSignificantError = isCalculationResultChanged(expected, calculated, 1e-12);
        System.out.printf("π计算精度验证：%s (期望:%.15f, 计算:%.15f)%n",
                hasSignificantError ? "❌ 精度不足" : "✅ 精度合格", expected, calculated);

        System.out.println("\n💡 模板方法使用说明：");
        System.out.println("   • 这些方法可直接复制到你的项目中");
        System.out.println("   • 根据业务需求调整容差参数和比较策略");
        System.out.println("   • 集成日志框架替换System.out输出");
        System.out.println("   • 金融系统推荐使用BigDecimal的COMPARE_TO策略");
    }
}