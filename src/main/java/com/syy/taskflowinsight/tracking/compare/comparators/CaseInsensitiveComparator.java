package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import java.lang.reflect.Field;

/**
 * 忽略大小写的字符串等价比较器。
 * 非字符串类型将通过 String.valueOf 进行比较。
 */
public class CaseInsensitiveComparator implements PropertyComparator {
    @Override
    public boolean areEqual(Object left, Object right, Field field) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return String.valueOf(left).equalsIgnoreCase(String.valueOf(right));
    }
}

