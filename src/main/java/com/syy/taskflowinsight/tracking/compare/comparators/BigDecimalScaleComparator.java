package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparisonException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BigDecimal 精度比较器（按 scale 精度 + 可选容差）。
 * 示例：new BigDecimalScaleComparator(2) → 比较到小数点后 2 位。
 */
public class BigDecimalScaleComparator implements PropertyComparator {
    private final int scale;
    private final double tolerance;

    public BigDecimalScaleComparator(int scale) {
        this(scale, 0.0);
    }

    public BigDecimalScaleComparator(int scale, double tolerance) {
        if (scale < 0) throw new IllegalArgumentException("scale must be >= 0");
        if (tolerance < 0) throw new IllegalArgumentException("tolerance must be >= 0");
        this.scale = scale;
        this.tolerance = tolerance;
    }

    @Override
    public boolean areEqual(Object left, Object right, Field field) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        try {
            BigDecimal a = toScaled(left);
            BigDecimal b = toScaled(right);
            if (tolerance > 0) {
                return a.subtract(b).abs().doubleValue() <= tolerance;
            }
            return a.compareTo(b) == 0;
        } catch (Exception e) {
            throw new PropertyComparisonException("BigDecimalScale compare failed", e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return Number.class.isAssignableFrom(type) || CharSequence.class.isAssignableFrom(type);
    }

    @Override
    public String getName() {
        return "BigDecimalScale[" + scale + ",tol=" + tolerance + "]";
    }

    private BigDecimal toScaled(Object v) {
        BigDecimal bd = (v instanceof BigDecimal) ? (BigDecimal) v : new BigDecimal(String.valueOf(v));
        return bd.setScale(scale, RoundingMode.HALF_UP);
    }
}

