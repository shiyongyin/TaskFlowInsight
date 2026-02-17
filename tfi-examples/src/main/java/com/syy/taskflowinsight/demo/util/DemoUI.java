package com.syy.taskflowinsight.demo.util;

import java.util.List;

/**
 * 控制台展示工具：统一演示头、菜单、章节分隔与摘要等输出格式。
 *
 * <p>提供 {@link #printHeader()}、{@link #printMenu()}、{@link #printChapterHeader(int, String, String)}
 * 等方法，用于 TFI 演示程序的控制台交互式展示。
 *
 * <p><b>注意：</b>演示模块为教学示例，刻意使用 {@code System.out} 打印到控制台，
 * 以便阅读与录屏展示；生产代码请使用 SLF4J 日志接口进行记录。
 *
 * @since 2.0.0
 */
public final class DemoUI {
    private DemoUI() {}

    public static void printHeader() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    TaskFlow Insight 完整功能演示");
        System.out.println("=".repeat(80));
        System.out.println("版本: v2.0.0 | 作者: TaskFlow Insight Team");
        System.out.println("本演示通过电商系统场景，帮助您快速掌握TaskFlow Insight的使用方法");
        System.out.println("=".repeat(80));
    }

    public static void printMenu() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("                    演示菜单");
        System.out.println("=".repeat(60));
        System.out.println("1. 快速入门 - 5分钟了解核心功能");
        System.out.println("2. 实际业务场景 - 电商系统完整流程");
        System.out.println("3. 高级特性 - 并发、异常处理、性能优化");
        System.out.println("4. 最佳实践 - API选择指南和使用建议");
        System.out.println("5. 高级API功能 - 系统控制、任务查询、自定义标签");
        System.out.println("6. 变更追踪功能 - 对象字段变更的自动追踪与记录");
        System.out.println("7. 异步上下文传播 - 异步场景下自动传播TFI上下文");
        System.out.println("8. 对象比对入门 - 使用TFI.compare()检测对象差异");
        System.out.println("9. 运行完整演示 (自动运行所有章节)");
        System.out.println("0. 退出");
        System.out.println("h. 查看代码路径与目录结构");
        System.out.println("=".repeat(60));
    }

    public static void printChapterHeader(int chapter, String title, String desc) {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("第" + chapter + "章：" + title);
        if (desc != null && !desc.isBlank()) {
            System.out.println(desc);
        }
        System.out.println("-".repeat(70));
    }

    public static void section(String title) {
        System.out.println("\n▶ " + title);
    }

    public static void printSectionSummary(String title, List<String> points) {
        System.out.println("\n" + "✔" + " "+ title);
        if (points != null && !points.isEmpty()) {
            for (String p : points) {
                System.out.println("  - " + p);
            }
        }
    }

    public static void pauseForEnter() {
        try {
            System.out.println("\n按Enter键继续...");
            int c;
            while ((c = System.in.read()) != -1) {
                if (c == '\n') break;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 打印演示代码路径与目录结构，方便开发者快速定位。
     */
    public static void printCodeMap() {
        System.out.println("\n代码路径与目录结构");
        System.out.println("-".repeat(70));
        System.out.println("根路径: src/main/java/com/syy/taskflowinsight/demo");
        System.out.println();
        System.out.println("TaskFlowInsightDemo.java              # 主入口（菜单与调度）");
        System.out.println("core/DemoChapter.java                 # 章节接口");
        System.out.println("core/DemoRegistry.java                # 章节注册与查找");
        System.out.println("chapters/QuickStartChapter.java       # 第1章：快速入门");
        System.out.println("chapters/BusinessScenarioChapter.java # 第2章：实际业务场景");
        System.out.println("chapters/AdvancedFeaturesChapter.java # 第3章：高级特性");
        System.out.println("chapters/BestPracticesChapter.java    # 第4章：最佳实践");
        System.out.println("chapters/AdvancedApiChapter.java      # 第5章：高级API功能");
        System.out.println("chapters/ChangeTrackingChapter.java   # 第6章：变更追踪功能");
        System.out.println("chapters/AsyncPropagationChapter.java # 第7章：异步上下文传播");
        System.out.println("chapters/CompareQuickStartChapter.java# 第8章：对象比对入门");
        System.out.println("service/EcommerceDemoService.java     # 电商示例业务逻辑");
        System.out.println("model/Order.java                      # 订单模型");
        System.out.println("model/UserOrderResult.java            # 并发下单结果");
        System.out.println("util/DemoUI.java                      # 控制台输出工具");
        System.out.println("util/DemoUtils.java                   # 通用工具（sleep等）");
        System.out.println("-".repeat(70));
    }
}
