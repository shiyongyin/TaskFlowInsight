package com.syy.taskflowinsight.tracking.format;

import com.syy.taskflowinsight.annotation.DateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskFlowInsight统一日期时间格式化器
 * 支持ISO-8601标准、自定义格式、时区处理、格式化器缓存
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class TfiDateTimeFormatter {
    
    private static final Logger logger = LoggerFactory.getLogger(TfiDateTimeFormatter.class);
    
    // ISO-8601标准格式
    public static final String ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    public static final String DEFAULT_DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    public static final String ISO_DATE = "yyyy-MM-dd";
    
    // 默认配置
    private final String defaultPattern;
    private final ZoneId defaultZoneId;
    private final Map<String, DateTimeFormatter> formatters;
    
    /**
     * 默认构造器，使用默认配置
     */
    public TfiDateTimeFormatter() {
        this(DEFAULT_DATE_TIME, "SYSTEM");
    }
    
    /**
     * 带配置的构造器
     */
    public TfiDateTimeFormatter(String defaultPattern, String timezone) {
        this.defaultPattern = defaultPattern != null ? defaultPattern : DEFAULT_DATE_TIME;
        this.defaultZoneId = resolveZoneId(timezone);
        this.formatters = new ConcurrentHashMap<>();
        initializeFormatters();
    }
    
    /**
     * 格式化Temporal对象为字符串
     */
    public String format(Temporal temporal, Field field) {
        if (temporal == null) return null;
        
        String pattern = getFormatPattern(field);
        DateTimeFormatter formatter = getOrCreateFormatter(pattern);
        
        try {
            if (temporal instanceof ZonedDateTime) {
                return ((ZonedDateTime) temporal).format(formatter);
            } else if (temporal instanceof LocalDateTime) {
                return ((LocalDateTime) temporal).atZone(defaultZoneId).format(formatter);
            } else if (temporal instanceof Instant) {
                return ((Instant) temporal).atZone(defaultZoneId).format(formatter);
            } else if (temporal instanceof LocalDate) {
                return ((LocalDate) temporal).format(DateTimeFormatter.ofPattern(ISO_DATE));
            } else if (temporal instanceof LocalTime) {
                return ((LocalTime) temporal).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
            
            return temporal.toString();
        } catch (Exception e) {
            logger.warn("Failed to format temporal: {}", e.getMessage());
            return temporal.toString();
        }
    }
    
    /**
     * 格式化Temporal对象（无字段信息）
     */
    public String format(Temporal temporal) {
        return format(temporal, null);
    }
    
    /**
     * 格式化Duration为ISO-8601格式
     * P[n]DT[n]H[n]M[n]S
     */
    public String formatDuration(Duration duration) {
        if (duration == null) return null;
        
        StringBuilder sb = new StringBuilder("P");
        
        long days = duration.toDays();
        if (days > 0) {
            sb.append(days).append("D");
            duration = duration.minusDays(days);
        }
        
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();
        
        if (hours > 0 || minutes > 0 || seconds > 0 || millis > 0) {
            sb.append("T");
            if (hours > 0) sb.append(hours).append("H");
            if (minutes > 0) sb.append(minutes).append("M");
            if (seconds > 0 || millis > 0) {
                if (millis > 0) {
                    sb.append(String.format("%d.%03d", seconds, millis)).append("S");
                } else {
                    sb.append(seconds).append("S");
                }
            }
        }
        
        return sb.length() == 1 ? "PT0S" : sb.toString();
    }
    
    /**
     * 格式化Period为ISO-8601格式
     * P[n]Y[n]M[n]D
     */
    public String formatPeriod(Period period) {
        if (period == null) return null;
        
        StringBuilder sb = new StringBuilder("P");
        
        int years = period.getYears();
        int months = period.getMonths();
        int days = period.getDays();
        
        if (years != 0) sb.append(years).append("Y");
        if (months != 0) sb.append(months).append("M");
        if (days != 0) sb.append(days).append("D");
        
        return sb.length() == 1 ? "P0D" : sb.toString();
    }
    
    /**
     * 解析字符串为Temporal对象（支持多种格式）
     */
    public Temporal parseWithTolerance(String dateStr, Class<? extends Temporal> targetType) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        
        // 尝试多种格式解析
        List<String> patterns = Arrays.asList(
            ISO_DATE_TIME,                           // yyyy-MM-dd'T'HH:mm:ss.SSSXXX
            "yyyy-MM-dd'T'HH:mm:ss.SSS",           // ISO with milliseconds no timezone
            DEFAULT_DATE_TIME,                       // yyyy-MM-dd HH:mm:ss
            "yyyy-MM-dd'T'HH:mm:ss",               // ISO without milliseconds no timezone
            "yyyy-MM-dd'T'HH:mm:ssXXX",            // ISO with timezone no milliseconds
            ISO_DATE,                                // yyyy-MM-dd
            "yyyy/MM/dd HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss"
        );
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = getOrCreateFormatter(pattern);
                
                if (targetType == LocalDateTime.class) {
                    return LocalDateTime.parse(dateStr, formatter);
                } else if (targetType == ZonedDateTime.class) {
                    return ZonedDateTime.parse(dateStr, formatter);
                } else if (targetType == Instant.class) {
                    ZonedDateTime zdt = ZonedDateTime.parse(dateStr, formatter);
                    return zdt.toInstant();
                } else if (targetType == LocalDate.class) {
                    // 尝试日期部分
                    if (dateStr.length() <= 10) {
                        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(ISO_DATE));
                    }
                    LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
                    return ldt.toLocalDate();
                }
                
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
                logger.trace("Failed to parse with pattern {}: {}", pattern, e.getMessage());
            }
        }
        
        throw new IllegalArgumentException("无法解析日期字符串: " + dateStr);
    }
    
    /**
     * 解析Duration字符串（ISO-8601格式）
     */
    public Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) return null;
        
        try {
            return Duration.parse(durationStr);
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse duration: {}", durationStr, e);
            return null;
        }
    }
    
    /**
     * 解析Period字符串（ISO-8601格式）
     */
    public Period parsePeriod(String periodStr) {
        if (periodStr == null || periodStr.trim().isEmpty()) return null;
        
        try {
            return Period.parse(periodStr);
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse period: {}", periodStr, e);
            return null;
        }
    }
    
    /**
     * 获取字段的格式模式
     */
    private String getFormatPattern(Field field) {
        if (field != null) {
            DateFormat annotation = field.getAnnotation(DateFormat.class);
            if (annotation != null && !annotation.pattern().isEmpty()) {
                return annotation.pattern();
            }
        }
        return defaultPattern;
    }
    
    /**
     * 获取或创建格式化器（缓存）
     */
    private DateTimeFormatter getOrCreateFormatter(String pattern) {
        return formatters.computeIfAbsent(pattern, p -> {
            try {
                return DateTimeFormatter.ofPattern(p).withZone(defaultZoneId);
            } catch (Exception e) {
                logger.warn("Invalid pattern '{}', using default", p);
                return DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME).withZone(defaultZoneId);
            }
        });
    }
    
    /**
     * 解析时区配置
     */
    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isEmpty()) {
            return ZoneId.systemDefault();
        }
        
        switch (timezone.toUpperCase()) {
            case "SYSTEM":
                return ZoneId.systemDefault();
            case "UTC":
                return ZoneOffset.UTC;
            default:
                try {
                    return ZoneId.of(timezone);
                } catch (DateTimeException e) {
                    logger.warn("Invalid timezone '{}', using system default", timezone);
                    return ZoneId.systemDefault();
                }
        }
    }
    
    /**
     * 初始化常用格式化器
     */
    private void initializeFormatters() {
        // 预初始化常用格式化器，提升性能
        getOrCreateFormatter(ISO_DATE_TIME);
        getOrCreateFormatter(DEFAULT_DATE_TIME);
        getOrCreateFormatter(ISO_DATE);
    }
    
    /**
     * 获取配置的时区
     */
    public ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }
    
    /**
     * 获取默认格式模式
     */
    public String getDefaultPattern() {
        return defaultPattern;
    }
    
    /**
     * 清除格式化器缓存（用于测试或内存优化）
     */
    public void clearCache() {
        formatters.clear();
        initializeFormatters();
    }
}