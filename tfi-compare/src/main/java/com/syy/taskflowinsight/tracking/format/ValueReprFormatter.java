package com.syy.taskflowinsight.tracking.format;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;

/**
 * 统一值表示格式化器
 * 负责将对象值格式化为符合规范的字符串表示
 * 
 * 功能特性：
 * - 字符串值双引号包围和JSON风格转义
 * - 三级截断规则：<100全显，≥100首50+...+尾50，≥1000标注(truncated)
 * - 数值类型特殊处理（BigDecimal、浮点数等）
 * - 日期时间ISO标准格式
 * - 集合类型简化显示
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class ValueReprFormatter {
    
    // 截断阈值常量
    private static final int SHORT_THRESHOLD = 100;    // <100字符：全显示
    private static final int LONG_THRESHOLD = 1000;    // ≥1000字符：标注truncated
    private static final String TRUNCATE_MARKER = "...";
    private static final String TRUNCATE_SUFFIX = " (truncated)";
    
    // 统一日期时间格式化器（默认yyyy-MM-dd HH:mm:ss UTC）
    private static final TfiDateTimeFormatter dateFormatter = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "UTC");
    
    /**
     * 统一值格式化入口
     * 先格式化再截断，确保"先转义后截断"顺序
     * 
     * @param value 原始值对象
     * @return 格式化和截断后的字符串表示
     */
    public static String format(Object value) {
        if (value == null) {
            return "null";
        }
        
        String rawRepr = formatRaw(value);
        return applyTruncation(rawRepr);
    }
    
    /**
     * 原始值格式化（不含截断处理）
     * 
     * @param value 输入值
     * @return 格式化后的字符串（未截断）
     */
    private static String formatRaw(Object value) {
        if (value instanceof String) {
            return "\"" + escapeAndQuote((String) value) + "\"";
        }
        
        if (value instanceof Number) {
            return formatNumber((Number) value);
        }
        
        if (value instanceof Date) {
            // 统一使用TfiDateTimeFormatter处理Date类型
            java.time.Instant instant = ((Date) value).toInstant();
            String formatted = dateFormatter.format(instant);
            return "\"" + formatted + "\"";
        }
        
        if (value instanceof Temporal) {
            // 统一使用TfiDateTimeFormatter处理所有Temporal类型
            String formatted = dateFormatter.format((Temporal) value);
            return "\"" + formatted + "\"";
        }
        
        if (value instanceof Collection) {
            return formatCollection((Collection<?>) value);
        }
        
        if (value instanceof Boolean) {
            return value.toString();
        }
        
        // 默认toString()处理
        return String.valueOf(value);
    }
    
    /**
     * 字符串转义和引号处理（JSON风格）
     * 
     * @param str 输入字符串
     * @return 转义后的字符串（不含外层双引号）
     */
    private static String escapeAndQuote(String str) {
        return str.replace("\\", "\\\\")    // 反斜杠必须最先处理
                 .replace("\"", "\\\"")     // 双引号
                 .replace("\n", "\\n")      // 换行符
                 .replace("\t", "\\t")      // 制表符
                 .replace("\r", "\\r");     // 回车符
    }
    
    /**
     * 数值格式化处理
     * 
     * @param number 数值对象
     * @return 格式化的数值字符串
     */
    private static String formatNumber(Number number) {
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).toPlainString();
        }
        
        if (number instanceof Float || number instanceof Double) {
            double d = number.doubleValue();
            if (Double.isNaN(d)) return "NaN";
            if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
            
            // 格式化浮点数，去除尾随零
            String str = String.format("%.10f", d);
            return str.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        
        return number.toString();
    }
    
    /**
     * 集合格式化处理（简化显示）
     * 
     * @param collection 集合对象
     * @return 格式化的集合字符串
     */
    private static String formatCollection(Collection<?> collection) {
        if (collection.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Object item : collection) {
            if (count > 0) sb.append(", ");
            if (count >= 5) { // 最多显示5个元素
                sb.append("...(").append(collection.size() - 5).append(" more)");
                break;
            }
            sb.append(format(item));
            count++;
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 应用三级截断规则（先转义后截断）
     * 
     * @param input 已转义的字符串
     * @return 应用截断规则后的字符串
     */
    private static String applyTruncation(String input) {
        if (input.length() < SHORT_THRESHOLD) {
            return input; // <100字符：全显示
        }
        
        if (input.length() < LONG_THRESHOLD) {
            // 101-999字符：首50+...+尾50
            String prefix = input.substring(0, 50);
            String suffix = input.substring(input.length() - 50);
            return prefix + TRUNCATE_MARKER + suffix;
        }
        
        // ≥1000字符：首50+...+尾50+(truncated)
        String prefix = input.substring(0, 50);
        String suffix = input.substring(input.length() - 50);
        return prefix + TRUNCATE_MARKER + suffix + TRUNCATE_SUFFIX;
    }
    
    /**
     * 获取截断阈值信息（用于测试和监控）
     */
    public static class TruncationInfo {
        public static final int SHORT_THRESHOLD = ValueReprFormatter.SHORT_THRESHOLD;
        public static final int LONG_THRESHOLD = ValueReprFormatter.LONG_THRESHOLD;
        public static final String TRUNCATE_MARKER = ValueReprFormatter.TRUNCATE_MARKER;
        public static final String TRUNCATE_SUFFIX = ValueReprFormatter.TRUNCATE_SUFFIX;
    }
}