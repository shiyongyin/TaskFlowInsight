package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.core.TfiCore;
import com.syy.taskflowinsight.demo.chapters.AdvancedApiChapter;
import com.syy.taskflowinsight.demo.chapters.AdvancedFeaturesChapter;
import com.syy.taskflowinsight.demo.chapters.AnnotationSystemChapter;
import com.syy.taskflowinsight.demo.chapters.AsyncPropagationChapter;
import com.syy.taskflowinsight.demo.chapters.BestPracticesChapter;
import com.syy.taskflowinsight.demo.chapters.BusinessScenarioChapter;
import com.syy.taskflowinsight.demo.chapters.ChangeTrackingChapter;
import com.syy.taskflowinsight.demo.chapters.CompareQuickStartChapter;
import com.syy.taskflowinsight.demo.chapters.QuickStartChapter;
import com.syy.taskflowinsight.demo.chapters.SpringIntegrationChapter;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.core.DemoRegistry;
import com.syy.taskflowinsight.demo.util.DemoUI;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;

/**
 * TaskFlow Insight 完整功能演示入口。
 *
 * 说明：
 * - 本类仅负责菜单显示与章节调度；
 * - 具体演示逻辑按章节拆分在 chapters/* 中；
 * - 各章节自行管理 TFI 会话边界与报告导出。
 * - 为了便于阅读/演示，本模块使用 System.out 打印；生产环境请使用 SLF4J 日志。
 *
 * 代码路径与结构（关键位置）：
 * - 主入口：src/main/java/com/syy/taskflowinsight/demo/TaskFlowInsightDemo.java
 * - 章节接口：src/main/java/com/syy/taskflowinsight/demo/core/DemoChapter.java
 * - 注册表：src/main/java/com/syy/taskflowinsight/demo/core/DemoRegistry.java
 * - 章节实现：src/main/java/com/syy/taskflowinsight/demo/chapters/*.java
 * - 示例服务：src/main/java/com/syy/taskflowinsight/demo/service/EcommerceDemoService.java
 * - 模型定义：src/main/java/com/syy/taskflowinsight/demo/model/*.java
 * - 展示工具：src/main/java/com/syy/taskflowinsight/demo/util/*.java
 */
public class
TaskFlowInsightDemo {
    public static void main(String[] args) {
        DemoUI.printHeader();
        DemoRegistry registry = new DemoRegistry()
                .register(new QuickStartChapter())
                .register(new BusinessScenarioChapter())
                .register(new AdvancedFeaturesChapter())
                .register(new BestPracticesChapter())
                .register(new AdvancedApiChapter())
                .register(new ChangeTrackingChapter())
                .register(new AsyncPropagationChapter())
                .register(new CompareQuickStartChapter())
                .register(new AnnotationSystemChapter())
                .register(new SpringIntegrationChapter());

        try {
            // 手动初始化TfiCore以支持独立运行
            initializeTfiCore();
            TFI.enable();

            // 支持命令行直达：1..10 | all | help
            if (args != null && args.length > 0) {
                String arg = args[0].trim().toLowerCase();
                if ("help".equals(arg)) {
                    printUsage();
                    return;
                } else if ("all".equals(arg)) {
                    runAll(registry);
                    return;
                } else if (arg.matches("([1-9]|10)")) {
                    int n = Integer.parseInt(arg);
                    Optional<DemoChapter> ch = registry.find(n);
                    ch.ifPresent(DemoChapter::run);
                    return;
                }
            }

            // 交互式菜单
            try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                boolean exit = false;
                while (!exit) {
                    DemoUI.printMenu();
                    System.out.print("\n请选择演示内容 (输入数字): ");
                    String choice = scanner.nextLine().trim();
                    System.out.println();
                    switch (choice) {
                        case "1": registry.find(1).ifPresent(DemoChapter::run); break;
                        case "2": registry.find(2).ifPresent(DemoChapter::run); break;
                        case "3": registry.find(3).ifPresent(DemoChapter::run); break;
                        case "4": registry.find(4).ifPresent(DemoChapter::run); break;
                        case "5": registry.find(5).ifPresent(DemoChapter::run); break;
                        case "6": registry.find(6).ifPresent(DemoChapter::run); break;
                        case "7": registry.find(7).ifPresent(DemoChapter::run); break;
                        case "8": registry.find(8).ifPresent(DemoChapter::run); break;
                        case "9": registry.find(9).ifPresent(DemoChapter::run); break;
                        case "10": registry.find(10).ifPresent(DemoChapter::run); break;
                        case "11": runAll(registry); break;
                        case "h":
                        case "H":
                            DemoUI.printCodeMap();
                            break;
                        case "0":
                            exit = true;
                            System.out.println("感谢使用TaskFlow Insight！");
                            break;
                        default:
                            System.out.println("无效选择，请重新输入。");
                    }
                    if (!exit && !"5".equals(choice) && !"6".equals(choice)
                            && !"7".equals(choice) && !"8".equals(choice)
                            && !"9".equals(choice) && !"10".equals(choice)) {
                        DemoUI.pauseForEnter();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("演示过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            TFI.clear();
        }
    }

    private static void runAll(DemoRegistry registry) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    开始运行完整演示");
        System.out.println("=".repeat(80));
        for (DemoChapter ch : registry.allOrdered()) {
            ch.run();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    完整演示结束");
        System.out.println("=".repeat(80));
        System.out.println("\n🎉 恭喜！您已经完成了TaskFlow Insight的所有功能学习！");
    }

    private static void printUsage() {
        System.out.println("用法: TaskFlowInsightDemo [1|2|...|10|all|help]");
        System.out.println("  1:  快速入门");
        System.out.println("  2:  实际业务场景");
        System.out.println("  3:  高级特性");
        System.out.println("  4:  最佳实践");
        System.out.println("  5:  高级API功能");
        System.out.println("  6:  变更追踪功能");
        System.out.println("  7:  异步上下文传播");
        System.out.println("  8:  对象比对入门");
        System.out.println("  9:  注解系统");
        System.out.println("  10: Spring 集成");
        System.out.println("  all: 依次运行所有章节");
        System.out.println("  help: 显示帮助");
        System.out.println();
        System.out.println("提示：在交互界面按 'h' 可查看代码路径与目录结构");
    }
    
    /**
     * 手动初始化TfiCore以支持独立运行Demo
     */
    private static void initializeTfiCore() {
        try {
            TfiCore tfiCore = new TfiCore();
            
            // 使用反射注入到TFI的core字段
            Field coreField = TFI.class.getDeclaredField("core");
            coreField.setAccessible(true);
            coreField.set(null, tfiCore);
            
            System.out.println("✅ TfiCore 手动初始化成功！现在可以看到完整的树形结构输出了。");
        } catch (Exception e) {
            System.err.println("❌ TfiCore 初始化失败: " + e.getMessage());
            System.out.println("💡 提示：某些高级功能可能无法正常工作，但基本演示仍可继续。");
        }
    }
}
