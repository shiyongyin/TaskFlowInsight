package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;
import com.syy.taskflowinsight.enums.MessageType;

import java.math.BigDecimal;
import java.util.*;

/**
 * 第4章：最佳实践 - API 选择指南和使用建议。
 *
 * <p>涵盖 TFI.run()/call()/传统 API 的选用场景、消息类型使用、嵌套深度控制及导出格式选择。
 *
 * @since 2.0.0
 */
public class BestPracticesChapter implements DemoChapter {
    @Override
    public int getChapterNumber() { return 4; }

    @Override
    public String getTitle() { return "最佳实践"; }

    @Override
    public String getDescription() { return "API选择指南和使用建议"; }

    @Override
    public void run() {
        DemoUI.printChapterHeader(4, getTitle(), getDescription());
        TFI.startSession("最佳实践演示");

        // 4.1 API选择指南
        DemoUI.section("4.1 API选择指南");
        System.out.println("\n📚 何时使用不同的API:\n");
        System.out.println("场景1: 简单的无返回值任务 → 使用 TFI.run()");
        TFI.run("发送邮件", () -> {
            TFI.message("发送邮件给用户", MessageType.PROCESS);
            DemoUtils.sleep(50);
        });
        System.out.println("   ✅ 代码简洁，自动资源管理\n");

        System.out.println("场景2: 需要返回值的任务 → 使用 TFI.call()");
        BigDecimal price = TFI.call("查询商品价格", () -> {
            TFI.message("查询数据库", MessageType.PROCESS);
            DemoUtils.sleep(50);
            return new BigDecimal("99.99");
        });
        System.out.println("   ✅ 返回值: ¥" + price + "\n");

        System.out.println("场景3: 需要任务上下文操作 → 使用传统API");
        try (TaskContext ctx = TFI.start("复杂任务")) {
            ctx.attribute("userId", "12345")
               .attribute("orderId", "ORD-001")
               .tag("important")
               .tag("vip-user");
            TFI.message("执行复杂业务逻辑", MessageType.PROCESS);
            System.out.println("   ✅ 任务ID: " + ctx.getTaskId());
            System.out.println("   ✅ 添加了属性和标签\n");
        }

        // 4.2 消息类型使用指南
        DemoUI.section("4.2 消息类型使用指南");
        TFI.run("消息类型示例", () -> {
            TFI.message("开始处理订单", MessageType.PROCESS);
            TFI.message("验证用户身份", MessageType.PROCESS);
            TFI.message("订单金额: ¥1999.00", MessageType.METRIC);
            TFI.message("处理时间: 235ms", MessageType.METRIC);
            TFI.message("订单状态: 待支付 → 已支付", MessageType.CHANGE);
            TFI.message("库存变更: 100 → 95", MessageType.CHANGE);
            TFI.error("支付接口响应超时");
            TFI.error("库存预警：商品即将售罄");
        });
        System.out.println("\n📝 消息类型使用建议:");
        System.out.println("   • PROCESS: 记录业务执行步骤");
        System.out.println("   • METRIC:  记录关键业务指标");
        System.out.println("   • CHANGE:  记录数据变更");
        System.out.println("   • ALERT:   记录异常和警告");

        // 4.3 嵌套深度控制
        DemoUI.section("4.3 任务嵌套最佳实践");
        TFI.run("合理的嵌套示例", () -> {
            TFI.message("第1层：主业务流程", MessageType.PROCESS);
            TFI.run("第2层：子流程", () -> {
                TFI.message("第2层：执行子流程", MessageType.PROCESS);
                TFI.run("第3层：具体操作", () -> {
                    TFI.message("第3层：执行具体操作", MessageType.PROCESS);
                });
            });
        });
        System.out.println("\n💡 嵌套建议:");
        System.out.println("   • 控制在3-4层以内");
        System.out.println("   • 每层代表不同的抽象级别");
        System.out.println("   • 避免过深的嵌套影响可读性");

        // 4.4 导出格式选择
        DemoUI.section("4.4 导出格式选择");
        System.out.println("\n📤 不同导出格式的使用场景:\n");
        System.out.println("1. 控制台输出 (开发调试):");
        TFI.exportToConsole();
        System.out.println("\n2. JSON格式 (系统集成):");
        String json = TFI.exportToJson();
        if (json != null) {
            System.out.println(json.substring(0, Math.min(200, json.length())) + "...");
        }
        System.out.println("\n3. Map格式 (程序处理):");
        Map<String, Object> map = TFI.exportToMap();
        System.out.println("   Session ID: " + map.get("sessionId"));
        System.out.println("   Status: " + map.get("status"));

        TFI.endSession();
        DemoUI.printSectionSummary("最佳实践总结", getSummaryPoints());
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "✅ 简单任务用 TFI.run()",
                "✅ 需要返回值用 TFI.call()",
                "✅ 需要上下文操作用传统API",
                "✅ 合理使用消息类型",
                "✅ 控制嵌套深度",
                "✅ 选择合适的导出格式"
        );
    }
}

