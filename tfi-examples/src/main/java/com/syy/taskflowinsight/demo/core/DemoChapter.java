package com.syy.taskflowinsight.demo.core;

import java.util.List;

/**
 * 章节演示的统一接口。
 *
 * <p>每个章节实现该接口，提供标题、描述、编号、执行入口以及学习要点。
 * 用于 TFI 演示程序的菜单驱动式章节调度。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class QuickStartChapter implements DemoChapter {
 *     @Override
 *     public int getChapterNumber() { return 1; }
 *
 *     @Override
 *     public String getTitle() { return "快速入门"; }
 *
 *     @Override
 *     public String getDescription() { return "5分钟了解核心功能"; }
 *
 *     @Override
 *     public void run() {
 *         TFI.startSession("演示");
 *         TFI.run("示例任务", () -> { ... });
 *         TFI.endSession();
 *     }
 *
 *     @Override
 *     public List<String> getSummaryPoints() {
 *         return List.of("要点1", "要点2");
 *     }
 * }
 * }</pre>
 *
 * @since 2.0.0
 */
public interface DemoChapter {
    /** 章节编号（用于排序与菜单显示） */
    int getChapterNumber();

    /** 章节标题（菜单显示） */
    String getTitle();

    /** 章节描述（简述本章学习目标） */
    String getDescription();

    /** 执行本章演示入口。章节内部自行管理 TFI 会话边界。 */
    void run();

    /** 本章关键学习要点摘要 */
    List<String> getSummaryPoints();
}

