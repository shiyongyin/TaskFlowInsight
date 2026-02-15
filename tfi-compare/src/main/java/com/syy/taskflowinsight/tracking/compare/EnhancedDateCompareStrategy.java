package com.syy.taskflowinsight.tracking.compare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.*;
import java.util.Date;

/**
 * 增强的日期时间比较策略
 * 支持毫秒级容差、多种时间类型、Duration/Period支持
 * 
 * 注意：默认容差为0ms（精确比较），可通过配置覆盖
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class EnhancedDateCompareStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedDateCompareStrategy.class);
    
    // 默认容差：0ms（规范要求的精确比较）
    private static final long DEFAULT_TOLERANCE_MS = 0L;
    
    /**
     * Date类型比较，支持毫秒级容差
     */
    public boolean compareDates(Date a, Date b, long toleranceMs) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        long diff = Math.abs(a.getTime() - b.getTime());
        boolean result = diff <= toleranceMs;
        
        if (logger.isTraceEnabled() && diff > 0) {
            logger.trace("Date comparison: {} vs {}, diff={}ms, tolerance={}ms, result={}", 
                a, b, diff, toleranceMs, result);
        }
        
        return result;
    }
    
    /**
     * Date类型比较，使用默认容差（0ms）
     */
    public boolean compareDates(Date a, Date b) {
        return compareDates(a, b, DEFAULT_TOLERANCE_MS);
    }
    
    /**
     * Instant比较，高精度时间戳
     */
    public boolean compareInstants(Instant a, Instant b, long toleranceMs) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        long diffMs = Math.abs(Duration.between(a, b).toMillis());
        boolean result = diffMs <= toleranceMs;
        
        if (logger.isTraceEnabled() && diffMs > 0) {
            logger.trace("Instant comparison: {} vs {}, diff={}ms, tolerance={}ms, result={}", 
                a, b, diffMs, toleranceMs, result);
        }
        
        return result;
    }
    
    /**
     * LocalDateTime比较，忽略时区
     */
    public boolean compareLocalDateTimes(LocalDateTime a, LocalDateTime b, long toleranceMs) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        if (toleranceMs == 0) {
            return a.equals(b); // 精确比较
        }
        
        long diffMs = Math.abs(Duration.between(a, b).toMillis());
        boolean result = diffMs <= toleranceMs;
        
        if (logger.isTraceEnabled() && diffMs > 0) {
            logger.trace("LocalDateTime comparison: {} vs {}, diff={}ms, tolerance={}ms, result={}", 
                a, b, diffMs, toleranceMs, result);
        }
        
        return result;
    }
    
    /**
     * LocalDate比较，仅日期部分
     */
    public boolean compareLocalDates(LocalDate a, LocalDate b, long toleranceMs) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        if (toleranceMs == 0) {
            return a.equals(b); // 精确比较
        }
        
        // 转换为同一时区的起始时间进行比较
        ZoneId zone = ZoneId.systemDefault();
        long diffMs = Math.abs(Duration.between(
            a.atStartOfDay(zone), 
            b.atStartOfDay(zone)).toMillis());
        
        return diffMs <= toleranceMs;
    }
    
    /**
     * Duration比较，支持ISO-8601格式
     */
    public boolean compareDurations(Duration a, Duration b, long toleranceMs) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        // Duration比较使用毫秒差
        long diffMs = Math.abs(a.minus(b).toMillis());
        boolean result = diffMs <= toleranceMs;
        
        if (logger.isTraceEnabled() && diffMs > 0) {
            logger.trace("Duration comparison: {} vs {}, diff={}ms, tolerance={}ms, result={}", 
                a, b, diffMs, toleranceMs, result);
        }
        
        return result;
    }
    
    /**
     * Period比较（年月日）
     */
    public boolean comparePeriods(Period a, Period b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        // Period精确比较（不支持容差）
        return a.equals(b);
    }
    
    /**
     * 通用时间比较接口
     */
    public boolean compareTemporal(Object a, Object b, long toleranceMs) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        
        // 类型不同时不相等
        if (!a.getClass().equals(b.getClass())) {
            return false;
        }
        
        // 根据类型分发
        if (a instanceof Date) {
            return compareDates((Date) a, (Date) b, toleranceMs);
        } else if (a instanceof Instant) {
            return compareInstants((Instant) a, (Instant) b, toleranceMs);
        } else if (a instanceof LocalDateTime) {
            return compareLocalDateTimes((LocalDateTime) a, (LocalDateTime) b, toleranceMs);
        } else if (a instanceof LocalDate) {
            return compareLocalDates((LocalDate) a, (LocalDate) b, toleranceMs);
        } else if (a instanceof Duration) {
            return compareDurations((Duration) a, (Duration) b, toleranceMs);
        } else if (a instanceof Period) {
            return comparePeriods((Period) a, (Period) b);
        }
        
        // 不支持的时间类型，回退到equals
        return a.equals(b);
    }
    
    /**
     * 判断对象是否为时间类型
     */
    public static boolean isTemporalType(Object value) {
        if (value == null) return false;
        
        return value instanceof Date ||
               value instanceof Instant ||
               value instanceof LocalDateTime ||
               value instanceof LocalDate ||
               value instanceof LocalTime ||
               value instanceof Duration ||
               value instanceof Period;
    }
    
    /**
     * 判断是否需要时间精度比较
     */
    public static boolean needsTemporalCompare(Object value1, Object value2) {
        return isTemporalType(value1) && isTemporalType(value2);
    }
    
    /**
     * 获取容差配置（可从字段注解或全局配置获取）
     */
    private long getToleranceMs(Field field) {
        // 未来可从字段注解或配置中获取
        // 当前返回默认值
        return DEFAULT_TOLERANCE_MS;
    }
}