package com.syy.taskflowinsight.demo.core;

import java.util.*;

/**
 * 章节注册表：集中管理演示章节的注册、排序与查找。
 *
 * <p>提供链式注册 {@link #register(DemoChapter)}、按编号查找 {@link #find(int)}、
 * 以及按编号有序获取全部章节 {@link #allOrdered()} 的能力。
 *
 * <p><b>线程安全说明：</b>本类非线程安全。若在多线程环境下使用，调用方需自行保证同步。
 *
 * @since 2.0.0
 */
public class DemoRegistry {
    private final Map<Integer, DemoChapter> chaptersByNumber = new LinkedHashMap<>();

    public DemoRegistry register(DemoChapter chapter) {
        Objects.requireNonNull(chapter, "chapter");
        chaptersByNumber.put(chapter.getChapterNumber(), chapter);
        return this;
    }

    public Optional<DemoChapter> find(int number) {
        return Optional.ofNullable(chaptersByNumber.get(number));
    }

    public List<DemoChapter> allOrdered() {
        List<Map.Entry<Integer, DemoChapter>> entries = new ArrayList<>(chaptersByNumber.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        List<DemoChapter> list = new ArrayList<>(entries.size());
        for (Map.Entry<Integer, DemoChapter> e : entries) {
            list.add(e.getValue());
        }
        return list;
    }
}

