package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.lang.reflect.Field;

/**
 * 数值精度比较策略
 * 支持浮点容差（绝对/相对）和BigDecimal标准化比较
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class NumericCompareStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(NumericCompareStrategy.class);
    
    // 默认容差常量（规范要求）
    private static final double DEFAULT_ABSOLUTE_TOLERANCE = 1e-12;
    private static final double DEFAULT_RELATIVE_TOLERANCE = 1e-9;
    private static final double NEAR_ZERO_THRESHOLD = 1e-6; // 切换容差的阈值
    
    // 可选的监控指标（轻量设计，不强制依赖）
    private PrecisionMetrics metrics;
    
    // BigDecimal比较方法枚举
    public enum CompareMethod {
        COMPARE_TO,      // 忽略scale差异（默认）
        EQUALS,          // 严格比较scale
        WITH_TOLERANCE   // 基于容差比较
    }
    
    /**
     * 设置监控指标（可选）
     */
    public void setMetrics(PrecisionMetrics metrics) {
        this.metrics = metrics;
    }
    
    /**
     * 浮点数容差比较
     * 优先使用相对容差，当数值接近0时使用绝对容差
     */
    public boolean compareFloats(double a, double b, Field field) {
        return compareFloats(a, b, DEFAULT_ABSOLUTE_TOLERANCE, DEFAULT_RELATIVE_TOLERANCE);
    }
    
    /**
     * 浮点数容差比较（可指定容差）
     */
    public boolean compareFloats(double a, double b, double absTolerance, double relTolerance) {
        long startTime = System.nanoTime();
        try {
        // 检查特殊值
        if (Double.isNaN(a) && Double.isNaN(b)) {
            return true; // NaN与NaN相等
        }
        if (Double.isInfinite(a) && Double.isInfinite(b)) {
            return Double.compare(a, b) == 0; // 正无穷与正无穷相等，负无穷与负无穷相等
        }
        if (Double.isNaN(a) || Double.isNaN(b) || 
            Double.isInfinite(a) || Double.isInfinite(b)) {
            return false; // NaN或无穷与普通数值不等
        }
        
        double diff = Math.abs(a - b);
        
            // 绝对容差检查（处理接近0的情况）
            if (diff <= absTolerance) {
                logger.trace("Float comparison with absolute tolerance: {} vs {}, diff={}, tolerance={}", 
                    a, b, diff, absTolerance);
                if (metrics != null) {
                    metrics.recordToleranceHit("absolute", diff);
                }
                return true;
            }
            
            // 相对容差检查（处理大数值）
            double maxValue = Math.max(Math.abs(a), Math.abs(b));
            if (maxValue > NEAR_ZERO_THRESHOLD) {
                double relativeDiff = diff / maxValue;
                if (relativeDiff <= relTolerance) {
                    logger.trace("Float comparison with relative tolerance: {} vs {}, relativeDiff={}, tolerance={}", 
                        a, b, relativeDiff, relTolerance);
                    if (metrics != null) {
                        metrics.recordToleranceHit("relative", relativeDiff);
                    }
                    return true;
                }
            }
            
            return false;
        } finally {
            if (metrics != null) {
                metrics.recordNumericComparison("float");
                metrics.recordCalculationTime(System.nanoTime() - startTime);
            }
        }
    }
    
    /**
     * BigDecimal比较策略
     * 默认使用compareTo忽略scale差异
     */
    public boolean compareBigDecimals(BigDecimal a, BigDecimal b, Field field) {
        return compareBigDecimals(a, b, CompareMethod.COMPARE_TO, DEFAULT_ABSOLUTE_TOLERANCE);
    }
    
    /**
     * BigDecimal比较策略（可指定方法）
     */
    public boolean compareBigDecimals(BigDecimal a, BigDecimal b, CompareMethod method, double tolerance) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        long startTime = System.nanoTime();
        try {
            switch (method) {
                case COMPARE_TO:
                    // 忽略scale差异：0.0 == 0.00，但如果有容差则应用容差比较
                    if (tolerance > 0) {
                        BigDecimal diff = a.subtract(b).abs();
                        BigDecimal toleranceBD = BigDecimal.valueOf(tolerance);
                        boolean toleranceResult = diff.compareTo(toleranceBD) <= 0;
                        logger.trace("BigDecimal compareTo with tolerance: {} vs {}, diff={}, tolerance={}, result={}", 
                            a, b, diff, tolerance, toleranceResult);
                        if (metrics != null) {
                            metrics.recordBigDecimalComparison("compareTo-tolerance");
                        }
                        return toleranceResult;
                    } else {
                        boolean compareToResult = a.compareTo(b) == 0;
                        logger.trace("BigDecimal compareTo: {} vs {} = {}", a, b, compareToResult);
                        if (metrics != null) {
                            metrics.recordBigDecimalComparison("compareTo");
                        }
                        return compareToResult;
                    }
                    
                case EQUALS:
                    // 严格比较：0.0 != 0.00
                    boolean equalsResult = a.equals(b);
                    logger.trace("BigDecimal equals: {} vs {} = {}", a, b, equalsResult);
                    if (metrics != null) {
                        metrics.recordBigDecimalComparison("equals");
                    }
                    return equalsResult;
                    
                case WITH_TOLERANCE:
                    // 基于容差比较
                    BigDecimal diff = a.subtract(b).abs();
                    BigDecimal toleranceBD = BigDecimal.valueOf(tolerance);
                    boolean toleranceResult = diff.compareTo(toleranceBD) <= 0;
                    logger.trace("BigDecimal tolerance: {} vs {}, diff={}, tolerance={}, result={}", 
                        a, b, diff, tolerance, toleranceResult);
                    if (metrics != null) {
                        metrics.recordBigDecimalComparison("tolerance");
                    }
                    return toleranceResult;
                    
                default:
                    if (metrics != null) {
                        metrics.recordBigDecimalComparison("default");
                    }
                    return a.compareTo(b) == 0;
            }
        } finally {
            if (metrics != null) {
                metrics.recordNumericComparison("bigdecimal");
                metrics.recordCalculationTime(System.nanoTime() - startTime);
            }
        }
    }
    
    /**
     * 通用数值比较接口
     */
    public boolean compareNumbers(Number a, Number b, Field field) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        // BigDecimal特殊处理
        if (a instanceof BigDecimal && b instanceof BigDecimal) {
            return compareBigDecimals((BigDecimal) a, (BigDecimal) b, field);
        }
        
        // 其他数值类型转换为double进行浮点比较
        return compareFloats(a.doubleValue(), b.doubleValue(), field);
    }
    
    /**
     * 判断对象是否为数值类型
     */
    public static boolean isNumericType(Object value) {
        if (value == null) return false;
        return value instanceof Number;
    }
    
    /**
     * 判断是否需要精度比较
     */
    public static boolean needsPrecisionCompare(Object value1, Object value2) {
        return isNumericType(value1) && isNumericType(value2);
    }
}