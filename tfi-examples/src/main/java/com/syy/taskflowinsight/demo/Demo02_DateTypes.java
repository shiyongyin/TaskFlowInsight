package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.render.RenderOptions;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.time.*;
import java.util.Date;
import java.sql.Timestamp;

/**
 * 演示02：日期时间类型快速上手
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
 *     .withMaxDepth(10)
 *     .compare(before, after);
 * System.out.println(TFI.render(r, RenderOptions.markdown()));
 * }</pre>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>审计与风控（精确到毫秒）</li>
 *   <li>订单/支付/工作流等业务事件时间核对（100–1000ms 容差）</li>
 *   <li>跨系统时间同步/定时任务延迟监控</li>
 * </ul>
 *
 * <p><b>使用效果：</b>
 * 清晰展示"字段名、旧值、新值"，容差内抖动可按需忽略，
 * 复杂类型（Duration/Period）也能直观对比。
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2.0.0
 */
public class Demo02_DateTypes {

    /**
     * 测试对象：完整的日期时间类型 + 精度比较
     */
    public static class DateTimeTestObject {
        // 📅 传统日期时间类型
        private Date date = new Date();
        private Timestamp timestampValue = new Timestamp(System.currentTimeMillis());

        // 🕐 Java 8 时间API（核心类型）
        private LocalDateTime localDateTime = LocalDateTime.now();
        private LocalDate localDate = LocalDate.now();
        private LocalTime localTime = LocalTime.now();
        private ZonedDateTime zonedDateTime = ZonedDateTime.now();
        private Instant instant = Instant.now();

        // ⏰ Duration和Period（ISO-8601格式）
        private Duration duration = Duration.ofHours(2).plusMinutes(30).plusSeconds(45);
        private Period period = Period.of(1, 6, 15); // 1年6月15天

        // 时间值统一使用canonical表示
        private Date customDate = new Date();

        private LocalTime customTime = LocalTime.now();

        private ZonedDateTime customDateTime = ZonedDateTime.now();

        // 默认精确比较
        private Instant preciseInstant = Instant.now();

        // 容差需通过ComparePolicy显式配置
        private Date toleranceDate = new Date();

        // 💼 SQL时间戳（业务系统常用）
        private Timestamp sqlTimestamp = new Timestamp(System.currentTimeMillis());

        // 业务事件时间
        private LocalDateTime businessEventTime = LocalDateTime.now();

        // 模拟精确的时间变更（毫秒级精度测试）
        public void changeDateTime() {
            long baseTime = date.getTime();

            // 传统日期类型变更
            date = new Date(baseTime + 86400000L + 50); // +1天+50ms
            timestampValue = new Timestamp(baseTime + 86400000L + 75); // +1天+75ms

            // Java 8 时间API精确变更
            localDateTime = localDateTime.plusDays(1).plusNanos(100_000); // +1天+0.1ms
            localTime = localTime.plusHours(1).plusNanos(500_000); // +1小时+0.5ms
            zonedDateTime = zonedDateTime.plusDays(1).plus(Duration.ofMillis(10)); // +1天+10ms
            instant = instant.plus(Duration.ofDays(1)).plusMillis(25); // +1天+25ms

            // Duration和Period变更（显著差异）
            duration = Duration.ofHours(5).plusMinutes(45).plusSeconds(30);
            period = Period.of(2, 3, 10);

            // 自定义格式日期精确变更
            customDate = new Date(baseTime + 2 * 86400000L); // +2天
            customTime = customTime.plusHours(2).plusMinutes(30);
            customDateTime = customDateTime.plusDays(1).plusNanos(50_000_000); // +1天+50ms

            // 精度比较测试：毫秒级差异演示
            preciseInstant = preciseInstant.plusMillis(1); // 1ms变化（应被0ms容差检测）
            toleranceDate = new Date(toleranceDate.getTime() + 800); // 800ms（<1000ms容差）

            // 新增字段变更
            sqlTimestamp = new Timestamp(baseTime + 120); // +120ms
            businessEventTime = businessEventTime.plusNanos(80_000_000); // +80ms (<100ms容差)
        }

        // Getters
        public Date getDate() { return date; }
        public Timestamp getTimestampValue() { return timestampValue; }
        public LocalDateTime getLocalDateTime() { return localDateTime; }
        public LocalDate getLocalDate() { return localDate; }
        public LocalTime getLocalTime() { return localTime; }
        public ZonedDateTime getZonedDateTime() { return zonedDateTime; }
        public Instant getInstant() { return instant; }
        public Duration getDuration() { return duration; }
        public Period getPeriod() { return period; }
        public Date getCustomDate() { return customDate; }
        public LocalTime getCustomTime() { return customTime; }
        public ZonedDateTime getCustomDateTime() { return customDateTime; }
        public Instant getPreciseInstant() { return preciseInstant; }
        public Date getToleranceDate() { return toleranceDate; }
        public Timestamp getSqlTimestamp() { return sqlTimestamp; }
        public LocalDateTime getBusinessEventTime() { return businessEventTime; }
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
        DateTimeTestObject before = new DateTimeTestObject();
        // 等待1ms确保时间差异
        try { Thread.sleep(2); } catch (InterruptedException e) { }
        DateTimeTestObject after = new DateTimeTestObject();
        after.changeDateTime();

        // 一行式比较和渲染
        CompareResult result = TFI.compare(before, after);
        String report = TFI.render(result, RenderOptions.markdown());

        System.out.println(report);

        System.out.println("\n💡 使用说明：");
        System.out.println("  • TFI.compare(before, after) - 一行式对比");
        System.out.println("  • TFI.render(result, RenderOptions.markdown()) - Markdown渲染");
        System.out.println("  • 自动检测日期时间变更，包括容差处理");
    }

    /**
     * 演示进阶链式 API
     *
     * <p>使用 ComparatorBuilder 进行细粒度配置：</p>
     * <pre>{@code
     * CompareResult r = TFI.comparator()
     *     .withMaxDepth(10)    // 深度比较
     *     .compare(before, after);
     * }</pre>
     */
    public static void demonstrateAdvancedAPI() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🔧 进阶链式用法");
        System.out.println("=".repeat(80));

        DateTimeTestObject before = new DateTimeTestObject();
        try { Thread.sleep(2); } catch (InterruptedException e) { }
        DateTimeTestObject after = new DateTimeTestObject();
        after.changeDateTime();

        // 场景1：带相似度计算
        System.out.println("\n▶ 场景1：计算相似度");
        CompareResult result1 = TFI.comparator()
            .withSimilarity()
            .compare(before, after);
        System.out.println(TFI.render(result1, RenderOptions.markdown()));
        result1.similarity().ifPresent(score ->
                System.out.printf("  相似度: %.2f%%%n", score.value() * 100));

        // 场景2：深度比较 + 详细报告
        System.out.println("\n▶ 场景2：深度比较 + 详细报告");
        CompareResult result2 = TFI.comparator()
            .withMaxDepth(10)
            .compare(before, after);
        System.out.println(TFI.render(result2, RenderOptions.markdown()));

        // 场景3：忽略容差字段
        System.out.println("\n▶ 场景3：忽略特定容差字段");
        CompareResult result3 = TFI.comparator()
            .compare(before, after);
        System.out.println(TFI.render(result3, RenderOptions.markdown()));

        System.out.println("\n💡 链式 API 说明：");
        System.out.println("  • withSimilarity() - 启用相似度计算");
        System.out.println("  • withMaxDepth(n) - 限制递归深度");
        System.out.println("  • RenderOptions.markdown()/console() - 选择闭集诊断布局");
        System.out.println("  • ignoring(...) - 忽略指定字段");
        System.out.println("  • ComparePolicy 显式控制时间容差");
    }

    /**
     * 审计日志时间变化检测模板（使用 TFI Facade）
     *
     * <p>使用场景：
     * <ul>
     *   <li>用户操作审计：登录时间、操作时间的精确记录</li>
     *   <li>系统事件追踪：服务启停时间、配置变更时间</li>
     *   <li>安全审计：攻击时间、异常访问时间点分析</li>
     *   <li>金融交易：交易时间戳的精确性验证</li>
     * </ul>
     */
    public static void trackAuditTimeChanges(Object beforeAuditLog, Object afterAuditLog, String auditContext) {
        System.out.println("\n=== " + auditContext + " ===");
        CompareResult result = TFI.compare(beforeAuditLog, afterAuditLog);
        System.out.println(TFI.render(result, RenderOptions.markdown()));
    }

    /**
     * 主演示方法
     */
    public static void main(String[] args) {
        System.out.println("演示02：日期时间类型快速上手");
        System.out.println("适用场景：审计风控、订单支付、跨系统时间同步");
        System.out.println();

        // 先演示一行式最小 API
        demonstrateSimplifiedAPI();

        // 再演示进阶链式 API
        demonstrateAdvancedAPI();

        // 演示模板方法
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🧰 实用模板演示");
        System.out.println("=".repeat(80));

        DateTimeTestObject before = new DateTimeTestObject();
        try { Thread.sleep(2); } catch (InterruptedException e) { }
        DateTimeTestObject after = new DateTimeTestObject();
        after.changeDateTime();

        trackAuditTimeChanges(before, after, "审计日志时间变化检测");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("✅ 日期时间演示完成");
        System.out.println("效果：容差内抖动自动忽略，ISO-8601格式输出，模板可直接落地");
        System.out.println("=".repeat(80));
    }
}
