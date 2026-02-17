package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.TaskNode;

import java.math.BigDecimal;
import java.util.*;

/**
 * 第5章：高级 API 功能 - 系统控制、任务查询、自定义标签。
 *
 * <p>涵盖动态启用/禁用、任务运行时信息、TaskContext 高级用法、自定义消息标签及分布式追踪场景。
 *
 * @since 2.0.0
 */
public class AdvancedApiChapter implements DemoChapter {
    @Override
    public int getChapterNumber() { return 5; }

    @Override
    public String getTitle() { return "高级API功能"; }

    @Override
    public String getDescription() { return "系统控制、任务查询、自定义标签等高级特性"; }

    @Override
    public void run() {
        DemoUI.printChapterHeader(5, getTitle(), getDescription());
        TFI.startSession("高级API演示");

        // 5.1 系统控制API
        DemoUI.section("5.1 系统控制API - 动态启用/禁用");
        System.out.println("📊 测试系统控制功能：\n");
        System.out.println("当前系统状态: " + (TFI.isEnabled() ? "启用" : "禁用"));
        TFI.run("正常任务", () -> TFI.message("系统启用时的任务", MessageType.PROCESS));
        System.out.println("\n🔴 禁用TaskFlow Insight...");
        TFI.disable();
        System.out.println("当前系统状态: " + (TFI.isEnabled() ? "启用" : "禁用"));
        TFI.run("禁用时的任务", () -> System.out.println("  任务执行了，但不会被记录"));
        System.out.println("\n🟢 重新启用TaskFlow Insight...");
        TFI.enable();
        System.out.println("当前系统状态: " + (TFI.isEnabled() ? "启用" : "禁用"));

        // 5.2 任务查询API
        DemoUI.section("5.2 任务查询API - 获取运行时信息");
        TFI.run("父任务", () -> {
            TaskNode currentTask = TFI.getCurrentTask();
            if (currentTask != null) {
                System.out.println("\n📍 当前任务信息:");
                System.out.println("   任务名: " + currentTask.getTaskName());
                System.out.println("   任务ID: " + currentTask.getNodeId());
                System.out.println("   线程名: " + currentTask.getThreadName());
                System.out.println("   任务深度: " + currentTask.getDepth());
            }
            TFI.run("子任务1", () -> {
                TFI.message("执行子任务1", MessageType.PROCESS);
                List<TaskNode> taskStack = TFI.getTaskStack();
                System.out.println("\n📚 任务堆栈 (深度=" + taskStack.size() + "):");
                for (int i = 0; i < taskStack.size(); i++) {
                    TaskNode task = taskStack.get(i);
                    System.out.println("   " + "  ".repeat(i) + "└─ " + task.getTaskName());
                }
            });
        });

        // 5.3 TaskContext高级功能
        DemoUI.section("5.3 TaskContext高级功能");
        System.out.println("\n🔧 使用TaskContext的高级功能：\n");
        try (TaskContext ctx = TFI.start("订单处理任务")) {
            ctx.attribute("orderId", "ORD-2025")
               .attribute("userId", "USER-123")
               .attribute("amount", new BigDecimal("999.99"))
               .tag("important")
               .tag("vip-customer")
               .tag("rush-order");
            System.out.println("✅ 添加了任务属性和标签");
            System.out.println("   任务ID: " + ctx.getTaskId());
            System.out.println("   任务名: " + ctx.getTaskName());
            ctx.debug("调试信息：开始处理")
               .message("正在验证订单")
               .warn("库存即将不足")
               .error("支付网关响应慢");
            try (TaskContext subCtx = ctx.subtask("支付处理")) {
                subCtx.message("调用支付API")
                      .attribute("paymentMethod", "CREDIT_CARD");
                subCtx.success();
                System.out.println("   子任务完成: " + subCtx.getTaskName());
            }
            ctx.success();
        }

        // 5.4 自定义消息标签
        DemoUI.section("5.4 自定义消息标签");
        System.out.println("\n🏷️  使用自定义消息标签：\n");
        TFI.run("监控指标收集", () -> {
            TFI.message("CPU使用率: 45%", "MONITOR");
            TFI.message("内存使用: 2.3GB/8GB", "MONITOR");
            TFI.message("磁盘IO: 120MB/s", "MONITOR");
            TFI.message("用户登录成功", "AUDIT");
            TFI.message("权限验证通过", "SECURITY");
            TFI.message("数据库连接数: 50/100", "DATABASE");
            System.out.println("✅ 记录了各种自定义标签的消息");
        });

        // 5.5 实际应用场景
        DemoUI.section("5.5 实际应用场景演示");
        System.out.println("\n场景1: 分布式追踪");
        String traceId = UUID.randomUUID().toString();
        try (TaskContext ctx = TFI.start("API请求处理")) {
            ctx.attribute("traceId", traceId)
               .attribute("spanId", UUID.randomUUID().toString())
               .tag("api-gateway");
            TFI.message("接收到请求，TraceID: " + traceId, "TRACE");
            TFI.run("调用用户服务", () -> TFI.message("传递TraceID到用户服务: " + traceId, "TRACE"));
            TFI.run("调用订单服务", () -> TFI.message("传递TraceID到订单服务: " + traceId, "TRACE"));
            System.out.println("✅ 分布式追踪ID已传递到各服务");
        }

        System.out.println("\n场景2: 调试模式切换");
        boolean debugMode = true;
        TFI.run("业务处理", () -> {
            TFI.message("开始业务处理", MessageType.PROCESS);
            if (debugMode) {
                TFI.message("详细参数: {key1=value1, key2=value2}", "DEBUG");
                TFI.message("SQL查询: SELECT * FROM users WHERE id=123", "DEBUG");
                TFI.message("缓存命中率: 85%", "DEBUG");
            }
            TFI.message("业务处理完成", MessageType.PROCESS);
        });

        // 5.6 错误处理与任务状态
        DemoUI.section("5.6 任务状态管理");
        System.out.println("\n📊 演示任务状态管理：\n");
        try (TaskContext ctx = TFI.start("成功任务")) {
            ctx.message("执行业务逻辑");
            ctx.success();
            System.out.println("✅ 任务标记为成功");
        }
        try (TaskContext ctx = TFI.start("失败任务")) {
            ctx.message("执行业务逻辑");
            try {
                throw new RuntimeException("模拟的业务异常");
            } catch (Exception e) {
                ctx.fail(e);
                System.out.println("❌ 任务标记为失败: " + e.getMessage());
            }
        }
        TFI.start("手动停止的任务");
        TFI.message("开始执行", MessageType.PROCESS);
        TFI.stop();
        System.out.println("⏹️  任务已手动停止");

        System.out.println("\n📋 高级API使用报告:");
        TFI.exportToConsole();
        TFI.endSession();

        DemoUI.printSectionSummary("高级API功能总结", getSummaryPoints());
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "✅ 掌握了系统动态启用/禁用",
                "✅ 学会了获取任务运行时信息",
                "✅ 使用了TaskContext高级功能",
                "✅ 掌握了自定义消息标签",
                "✅ 了解了实际应用场景",
                "✅ 学会了任务状态管理"
        );
    }
}

