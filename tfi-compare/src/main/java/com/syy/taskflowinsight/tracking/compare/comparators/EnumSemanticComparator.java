package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import java.lang.reflect.Field;

/**
 * 枚举语义比较器（P0骨架）。
 * P0 实现：占位（直接 String 等价）；P1 可扩展同义词映射（如 PENDING==WAITING）。
 */
public class EnumSemanticComparator implements PropertyComparator {
    @Override
    public boolean areEqual(Object left, Object right, Field field) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return String.valueOf(left).equals(String.valueOf(right));
    }
}

