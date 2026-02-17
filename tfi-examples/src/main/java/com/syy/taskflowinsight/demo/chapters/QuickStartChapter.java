package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.util.DemoUI;
import com.syy.taskflowinsight.demo.util.DemoUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 第1章：快速入门 - 5分钟了解核心功能。
 *
 * <p>涵盖 TFI.run()、TFI.call()、任务嵌套、带返回值任务及任务报告导出等核心用法。
 *
 * @since 2.0.0
 */
public class QuickStartChapter implements DemoChapter {
    @Override
    public int getChapterNumber() { return 1; }

    @Override
    public String getTitle() { return "快速入门"; }

    @Override
    public String getDescription() { return "5分钟了解TaskFlow Insight核心功能"; }

    @Override
    public void run() {
        DemoUI.printChapterHeader(1, getTitle(), getDescription());

        // 1.1 最简单的使用方式
        DemoUI.section("1.1 Hello World - 最简单的任务追踪");

        TFI.startSession("快速入门演示");
        TFI.run("我的第一个任务", () -> {
            System.out.println("  执行任务中...");
            TFI.message("任务执行中", MessageType.PROCESS);
            DemoUtils.sleep(100);
            TFI.message("任务完成", MessageType.PROCESS);
        });

        System.out.println("\n✅ 恭喜！您已经完成了第一个任务追踪！");
        System.out.println("📝 任务自动记录了执行时间和消息");

        // 1.2 任务嵌套
        DemoUI.section("1.2 任务嵌套 - 展示任务层级关系");
        TFI.run("父任务", () -> {
            TFI.message("开始处理父任务", MessageType.PROCESS);
            TFI.run("子任务1", () -> {
                TFI.message("处理子任务1", MessageType.PROCESS);
                DemoUtils.sleep(50);
            });
            TFI.run("子任务2", () -> {
                TFI.message("处理子任务2", MessageType.PROCESS);
                DemoUtils.sleep(50);
            });
            TFI.message("父任务完成", MessageType.PROCESS);
        });
        System.out.println("✅ 任务会自动形成树形结构，展示执行层级");

        // 1.3 带返回值的任务
        DemoUI.section("1.3 带返回值的任务 - 使用call()方法");
        Integer result = TFI.call("计算任务", () -> {
            TFI.message("执行计算: 1 + 2 + 3", MessageType.PROCESS);
            DemoUtils.sleep(50);
            int sum = 1 + 2 + 3;
            TFI.message("计算结果: " + sum, MessageType.METRIC);
            return sum;
        });
        System.out.println("✅ 任务返回结果: " + result);

        // 1.4 查看任务报告
        DemoUI.section("1.4 查看任务报告");
        System.out.println("\n📊 任务执行报告:");
        System.out.print(TFI.exportToConsole(true));
        TFI.endSession();

        DemoUI.printSectionSummary("快速入门完成", getSummaryPoints());
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "✅ 学会了使用 TFI.run() 执行无返回值任务",
                "✅ 学会了使用 TFI.call() 执行有返回值任务",
                "✅ 了解了任务嵌套和自动计时功能",
                "✅ 学会了查看任务执行报告"
        );
    }
}

