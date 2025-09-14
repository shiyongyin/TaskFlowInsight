package com.syy.taskflowinsight.demo.core;

import java.util.List;

/**
 * 章节演示的统一接口。
 * 每个章节实现该接口，提供标题、描述、编号、执行入口以及学习要点。
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

