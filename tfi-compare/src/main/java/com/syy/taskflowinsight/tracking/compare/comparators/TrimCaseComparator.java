package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import java.lang.reflect.Field;

/**
 * Trim + 空白折叠 + 大小写不敏感比较器。
 */
public class TrimCaseComparator implements PropertyComparator {
    @Override
    public boolean areEqual(Object left, Object right, Field field) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        String l = normalize(String.valueOf(left));
        String r = normalize(String.valueOf(right));
        return l.equals(r);
    }

    private String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}

