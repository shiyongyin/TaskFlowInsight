package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import java.lang.reflect.Field;

/**
 * URL 归一化比较器（P0骨架）。
 * P0 实现：占位（直接 String 等价）；P1 可增强为协议/主机大小写、默认端口、尾斜杠、参数排序等归一化。
 */
public class UrlCanonicalComparator implements PropertyComparator {
    @Override
    public boolean areEqual(Object left, Object right, Field field) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        // TODO P1: 归一化处理
        return String.valueOf(left).equals(String.valueOf(right));
    }
}

