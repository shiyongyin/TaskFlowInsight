package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.model.UserOrderResult;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;
import com.syy.taskflowinsight.enums.MessageType;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 第3章：高级特性 - 并发、异常处理、性能优化
 */
public class AdvancedFeaturesChapter implements DemoChapter {
    @Override
    public int getChapterNumber() { return 3; }

    @Override
    public String getTitle() { return "高级特性"; }

    @Override
    public String getDescription() { return "并发处理、异常处理、性能优化"; }

    @Override
    public void run() {
        DemoUI.printChapterHeader(3, getTitle(), getDescription());

        // 3.1 并发处理
        DemoUI.section("3.1 并发处理 - 多线程任务追踪");
        System.out.println("🚀 模拟黑色星期五：10个用户同时下单\n");

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(10);
        List<Future<UserOrderResult>> futures = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            final int userId = i;
            Future<UserOrderResult> future = executor.submit(() -> {
                UserOrderResult userResult = new UserOrderResult(userId);
                try {
                    TFI.startSession("用户" + userId + "-下单");
                    long startTime = System.currentTimeMillis();

                    Boolean result = TFI.call("用户" + userId + "下单", () -> {
                        TFI.message("用户" + userId + "开始下单", MessageType.PROCESS);
                        int processTime = 100 + new Random().nextInt(200);
                        DemoUtils.sleep(processTime);
                        boolean success = new Random().nextDouble() > 0.3; // 70% 成功率
                        if (success) {
                            String orderId = "ORD-" + System.currentTimeMillis() + "-U" + userId;
                            TFI.message("订单创建成功: " + orderId, MessageType.CHANGE);
                            userResult.setOrderId(orderId);
                            userResult.setStatus("SUCCESS");
                        } else {
                            TFI.error("下单失败：库存不足");
                            userResult.setStatus("FAILED");
                            userResult.setFailReason("库存不足");
                        }
                        return success;
                    });

                    userResult.setProcessTime(System.currentTimeMillis() - startTime);
                    userResult.setSuccess(Boolean.TRUE.equals(result));
                    // 可选：保存报告
                    // userResult.setReport(TFI.exportToJson());
                    TFI.endSession();
                    return userResult;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
            System.out.println("📋 用户下单详细结果:");
            System.out.println("─".repeat(70));
            System.out.printf("%-8s %-10s %-30s %-10s %s\n", "用户ID", "状态", "订单号", "耗时(ms)", "失败原因");
            System.out.println("─".repeat(70));

            int successCount = 0;
            long totalTime = 0;
            for (Future<UserOrderResult> f : futures) {
                UserOrderResult r = f.get();
                System.out.printf("用户%-4d %-10s %-30s %-10d %s\n",
                        r.getUserId(),
                        r.isSuccess() ? "✅成功" : "❌失败",
                        r.getOrderId() != null ? r.getOrderId() : "-",
                        r.getProcessTime(),
                        r.getFailReason() != null ? r.getFailReason() : "-"
                );
                if (r.isSuccess()) successCount++;
                totalTime += r.getProcessTime();
            }
            System.out.println("─".repeat(70));
            System.out.println("\n📊 并发处理汇总:");
            System.out.println("   总用户数: 10");
            System.out.println("   成功订单: " + successCount + " (" + (successCount * 10) + "%)");
            System.out.println("   失败订单: " + (10 - successCount) + " (" + ((10 - successCount) * 10) + "%)");
            System.out.println("   平均处理时间: " + (totalTime / 10) + "ms");
            System.out.println("   总处理时间: " + totalTime + "ms");
        } catch (Exception e) {
            System.err.println("并发处理异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        // 3.2 异常处理
        DemoUI.section("3.2 异常处理 - 优雅的错误处理");
        TFI.startSession("异常处理演示");

        System.out.println("🔧 演示三种异常处理场景：\n");
        System.out.println("场景1：订单处理异常及自动恢复");
        System.out.println("-".repeat(40));
        String orderId = "ORD-EX-" + System.currentTimeMillis();
        Boolean orderResult = TFI.call("处理订单-" + orderId, () -> {
            TFI.message("开始处理订单: " + orderId, MessageType.PROCESS);
            try {
                TFI.run("验证订单", () -> TFI.message("订单格式验证通过", MessageType.PROCESS));
                TFI.run("支付处理", () -> {
                    TFI.message("调用支付网关", MessageType.PROCESS);
                    if (new Random().nextDouble() > 0.5) {
                        throw new RuntimeException("支付网关超时");
                    }
                    TFI.message("支付成功", MessageType.CHANGE);
                });
                System.out.println("✅ 订单处理成功！");
                return true;
            } catch (Exception e) {
                System.out.println("❌ 捕获异常: " + e.getMessage());
                TFI.error("订单处理失败", e);
                System.out.println("🔄 执行自动恢复流程...");
                Boolean recovered = TFI.call("异常恢复", () -> {
                    TFI.message("开始执行补救措施", MessageType.PROCESS);
                    TFI.run("回滚库存", () -> { TFI.message("恢复商品库存", MessageType.CHANGE); DemoUtils.sleep(30); });
                    TFI.run("通知用户", () -> { TFI.message("发送失败通知给用户", MessageType.PROCESS); DemoUtils.sleep(20); });
                    TFI.run("记录异常日志", () -> TFI.message("异常已记录到系统日志", MessageType.METRIC));
                    TFI.message("补救措施执行完成", MessageType.CHANGE);
                    return true;
                });
                if (Boolean.TRUE.equals(recovered)) {
                    System.out.println("✅ 异常恢复成功！订单已安全回滚");
                } else {
                    System.out.println("⚠️  恢复失败，需要人工介入");
                }
                return false;
            }
        });
        System.out.println("订单最终状态: " + (Boolean.TRUE.equals(orderResult) ? "成功" : "失败（已恢复）"));

        System.out.println("\n场景2：批量任务异常容错处理");
        System.out.println("-".repeat(40));
        List<String> tasks = Arrays.asList("任务A", "任务B", "任务C", "任务D", "任务E");
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        TFI.run("批量任务处理", () -> {
            for (String taskName : tasks) {
                try {
                    Boolean result = TFI.call("处理-" + taskName, () -> {
                        TFI.message("开始处理: " + taskName, MessageType.PROCESS);
                        if (new Random().nextDouble() > 0.6) {
                            throw new RuntimeException(taskName + " 处理异常");
                        }
                        TFI.message(taskName + " 处理成功", MessageType.CHANGE);
                        return true;
                    });
                    if (Boolean.TRUE.equals(result)) {
                        successCount.incrementAndGet();
                        System.out.println("  ✅ " + taskName + " - 成功");
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  ❌ " + taskName + " - 失败 (已记录)");
                    TFI.error(taskName + " 处理失败: " + e.getMessage());
                }
            }
            TFI.message("批量处理完成 - 成功: " + successCount.get() + ", 失败: " + failCount.get(), MessageType.METRIC);
        });
        System.out.println("\n📊 批量处理统计:");
        System.out.println("   总任务数: " + tasks.size());
        System.out.println("   成功: " + successCount.get());
        System.out.println("   失败: " + failCount.get());
        System.out.println("   成功率: " + String.format("%.1f%%", successCount.get() * 100.0 / tasks.size()));
        System.out.println("\n📋 异常处理任务追踪报告:");
        TFI.exportToConsole();

        // 3.3 性能对比
        DemoUI.section("3.3 性能对比 - 传统API vs 现代API");
        int iterations = 1000;
        long traditionalTime = measurePerformance("传统API测试", () -> {
            for (int i = 0; i < iterations; i++) {
                try (TaskContext ignored = TFI.start("task-" + i)) {
                    // 空任务，仅测试API开销
                }
            }
        });
        long modernTime = measurePerformance("现代API测试", () -> {
            for (int i = 0; i < iterations; i++) {
                TFI.run("task-" + i, () -> { /* 空任务 */ });
            }
        });
        System.out.println("\n📊 性能测试结果 (" + iterations + "次迭代):");
        System.out.println("   传统API: " + traditionalTime + "ms");
        System.out.println("   现代API: " + modernTime + "ms");
        if (modernTime < traditionalTime) {
            double improvement = ((double) (traditionalTime - modernTime) / traditionalTime) * 100;
            System.out.println("   ✅ 现代API性能提升: " + String.format("%.1f%%", improvement));
        }
        TFI.endSession();

        DemoUI.printSectionSummary("高级特性演示完成", getSummaryPoints());
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "✅ 掌握了多线程环境下的任务追踪",
                "✅ 学会了优雅的异常处理方式",
                "✅ 了解了不同API的性能差异",
                "✅ 理解了任务追踪的线程隔离机制"
        );
    }

    private long measurePerformance(String name, Runnable task) {
        System.out.println("\n⏱️  测量" + name + "性能...");
        long start = System.currentTimeMillis();
        task.run();
        long end = System.currentTimeMillis();
        return end - start;
    }
}

