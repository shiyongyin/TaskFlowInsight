package com.syy.taskflowinsight.demo.core;

import java.util.*;

/**
 * 章节注册表：集中管理章节的注册、排序与查找。
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

