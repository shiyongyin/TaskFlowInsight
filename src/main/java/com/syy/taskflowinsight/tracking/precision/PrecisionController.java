package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.annotation.DateFormat;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精度控制器
 * 统一管理数值和日期时间的精度设置，包含完整的降级策略和缓存机制
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class PrecisionController {
    
    private static final Logger logger = LoggerFactory.getLogger(PrecisionController.class);
    
    // 字段精度缓存（LRU策略）
    private final Map<Field, PrecisionSettings> fieldPrecisionCache;
    
    // 默认配置（可通过构造器注入）
    private final double defaultAbsoluteTolerance;
    private final double defaultRelativeTolerance;
    private final NumericCompareStrategy.CompareMethod defaultCompareMethod;
    private final long defaultDateToleranceMs;
    
    /**
     * 精度设置内部类
     */
    public static class PrecisionSettings {
        private final double absoluteTolerance;
        private final double relativeTolerance;
        private final NumericCompareStrategy.CompareMethod compareMethod;
        private final int scale;
        private final RoundingMode roundingMode;
        private final long dateToleranceMs;
        private final String datePattern;
        private final String timezone;
        
        private PrecisionSettings(Builder builder) {
            this.absoluteTolerance = builder.absoluteTolerance;
            this.relativeTolerance = builder.relativeTolerance;
            this.compareMethod = builder.compareMethod;
            this.scale = builder.scale;
            this.roundingMode = builder.roundingMode;
            this.dateToleranceMs = builder.dateToleranceMs;
            this.datePattern = builder.datePattern;
            this.timezone = builder.timezone;
        }
        
        // Getters
        public double getAbsoluteTolerance() { return absoluteTolerance; }
        public double getRelativeTolerance() { return relativeTolerance; }
        public NumericCompareStrategy.CompareMethod getCompareMethod() { return compareMethod; }
        public int getScale() { return scale; }
        public RoundingMode getRoundingMode() { return roundingMode; }
        public long getDateToleranceMs() { return dateToleranceMs; }
        public String getDatePattern() { return datePattern; }
        public String getTimezone() { return timezone; }
        
        /**
         * 系统默认值（最终兜底）
         */
        public static PrecisionSettings systemDefaults() {
            return new Builder()
                .absoluteTolerance(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE)
                .relativeTolerance(ConfigDefaults.NUMERIC_RELATIVE_TOLERANCE)
                .compareMethod(NumericCompareStrategy.CompareMethod.COMPARE_TO)
                .scale(-1)
                .roundingMode(RoundingMode.HALF_UP)
                .dateToleranceMs(0L)
                .datePattern("yyyy-MM-dd HH:mm:ss")
                .timezone("SYSTEM")
                .build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private double absoluteTolerance = ConfigDefaults.NUMERIC_FLOAT_TOLERANCE;
            private double relativeTolerance = ConfigDefaults.NUMERIC_RELATIVE_TOLERANCE;
            private NumericCompareStrategy.CompareMethod compareMethod = NumericCompareStrategy.CompareMethod.COMPARE_TO;
            private int scale = -1;
            private RoundingMode roundingMode = RoundingMode.HALF_UP;
            private long dateToleranceMs = 0L;
            private String datePattern = "yyyy-MM-dd HH:mm:ss";
            private String timezone = "SYSTEM";
            
            public Builder absoluteTolerance(double value) {
                this.absoluteTolerance = value;
                return this;
            }
            
            public Builder relativeTolerance(double value) {
                this.relativeTolerance = value;
                return this;
            }
            
            public Builder compareMethod(NumericCompareStrategy.CompareMethod method) {
                this.compareMethod = method;
                return this;
            }
            
            public Builder scale(int value) {
                this.scale = value;
                return this;
            }
            
            public Builder roundingMode(RoundingMode mode) {
                this.roundingMode = mode;
                return this;
            }
            
            public Builder dateToleranceMs(long value) {
                this.dateToleranceMs = value;
                return this;
            }
            
            public Builder datePattern(String pattern) {
                this.datePattern = pattern;
                return this;
            }
            
            public Builder timezone(String tz) {
                this.timezone = tz;
                return this;
            }
            
            public PrecisionSettings build() {
                return new PrecisionSettings(this);
            }
        }
    }
    
    /**
     * 默认构造器，使用系统默认值
     */
    public PrecisionController() {
        this(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE, 
             ConfigDefaults.NUMERIC_RELATIVE_TOLERANCE,
             NumericCompareStrategy.CompareMethod.COMPARE_TO,
             0L);
    }
    
    /**
     * 带配置的构造器
     */
    public PrecisionController(double absoluteTolerance, 
                              double relativeTolerance,
                              NumericCompareStrategy.CompareMethod compareMethod,
                              long dateToleranceMs) {
        this.defaultAbsoluteTolerance = absoluteTolerance;
        this.defaultRelativeTolerance = relativeTolerance;
        this.defaultCompareMethod = compareMethod;
        this.defaultDateToleranceMs = dateToleranceMs;
        
        // 创建LRU缓存（最多缓存1024个字段）
        this.fieldPrecisionCache = Collections.synchronizedMap(
            new LinkedHashMap<Field, PrecisionSettings>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Field, PrecisionSettings> eldest) {
                    boolean shouldRemove = size() > ConfigDefaults.MAX_CACHED_CLASSES;
                    if (shouldRemove) {
                        logger.debug("Evicting precision cache entry for field: {}", 
                            eldest.getKey().getName());
                    }
                    return shouldRemove;
                }
            }
        );
    }
    
    /**
     * 获取字段的精度设置（三级优先级）
     * 1. 字段注解（最高优先级）
     * 2. 配置文件
     * 3. 系统默认值
     */
    public PrecisionSettings getFieldPrecision(Field field) {
        if (field == null) {
            return getDefaultSettings();
        }
        
        // 从缓存获取
        return fieldPrecisionCache.computeIfAbsent(field, this::computeFieldPrecision);
    }
    
    /**
     * 计算字段的精度设置
     */
    private PrecisionSettings computeFieldPrecision(Field field) {
        PrecisionSettings.Builder builder = PrecisionSettings.builder();
        
        // 先设置默认值
        builder.absoluteTolerance(defaultAbsoluteTolerance)
               .relativeTolerance(defaultRelativeTolerance)
               .compareMethod(defaultCompareMethod)
               .dateToleranceMs(defaultDateToleranceMs);
        
        // 检查数值精度注解
        NumericPrecision numericAnnotation = field.getAnnotation(NumericPrecision.class);
        if (numericAnnotation != null) {
            builder.absoluteTolerance(numericAnnotation.absoluteTolerance())
                   .relativeTolerance(numericAnnotation.relativeTolerance())
                   .scale(numericAnnotation.scale());
            
            try {
                builder.compareMethod(NumericCompareStrategy.CompareMethod.valueOf(
                    numericAnnotation.compareMethod()));
            } catch (Exception e) {
                logger.warn("Invalid compare method: {}, using default", 
                    numericAnnotation.compareMethod());
            }
            
            try {
                builder.roundingMode(RoundingMode.valueOf(numericAnnotation.roundingMode()));
            } catch (Exception e) {
                logger.warn("Invalid rounding mode: {}, using default", 
                    numericAnnotation.roundingMode());
            }
        }
        
        // 检查日期格式注解
        DateFormat dateAnnotation = field.getAnnotation(DateFormat.class);
        if (dateAnnotation != null) {
            builder.dateToleranceMs(dateAnnotation.toleranceMs())
                   .datePattern(dateAnnotation.pattern())
                   .timezone(dateAnnotation.timezone());
        }
        
        return builder.build();
    }
    
    /**
     * 获取默认设置
     */
    private PrecisionSettings getDefaultSettings() {
        return PrecisionSettings.builder()
            .absoluteTolerance(defaultAbsoluteTolerance)
            .relativeTolerance(defaultRelativeTolerance)
            .compareMethod(defaultCompareMethod)
            .dateToleranceMs(defaultDateToleranceMs)
            .build();
    }
    
    /**
     * 为字段解析容差值（根据值大小自适应）
     */
    public double resolveToleranceForField(Field field, Object value) {
        PrecisionSettings settings = getFieldPrecision(field);
        
        if (value instanceof Number) {
            double absValue = Math.abs(((Number) value).doubleValue());
            
            // 接近0时使用绝对容差
            if (absValue < 1e-6) {
                return settings.getAbsoluteTolerance();
            } else {
                // 大值使用相对容差
                return absValue * settings.getRelativeTolerance();
            }
        }
        
        return settings.getAbsoluteTolerance();
    }
    
    /**
     * 应用舍入模式到BigDecimal
     */
    public BigDecimal applyRoundingMode(BigDecimal value, Field field) {
        if (value == null) return null;
        
        PrecisionSettings settings = getFieldPrecision(field);
        
        if (settings.getScale() >= 0) {
            return value.setScale(settings.getScale(), settings.getRoundingMode());
        }
        
        return value; // 不改变scale
    }
    
    /**
     * 获取字段的日期容差（毫秒）
     */
    public long getDateToleranceMs(Field field) {
        PrecisionSettings settings = getFieldPrecision(field);
        return settings.getDateToleranceMs();
    }
    
    /**
     * 带降级策略的精度设置获取
     */
    public PrecisionSettings getWithFallback(Field field) {
        try {
            // 1. 尝试正常获取
            return getFieldPrecision(field);
            
        } catch (Exception e) {
            logger.warn("Failed to compute precision for field: {}, using fallback", 
                    field != null ? field.getName() : "null", e);
            
            try {
                // 2. 降级到配置默认值
                return getDefaultSettings();
                
            } catch (Exception configError) {
                logger.error("Config fallback failed, using system defaults", configError);
                
                // 3. 最终降级到系统默认值
                return PrecisionSettings.systemDefaults();
            }
        }
    }
    
    /**
     * 验证精度设置合理性
     */
    public ValidationResult validatePrecisionSettings(PrecisionSettings settings) {
        ValidationResult.Builder result = ValidationResult.builder();
        
        // 验证绝对容差
        if (settings.getAbsoluteTolerance() < 0) {
            result.addError("Absolute tolerance cannot be negative");
        } else if (settings.getAbsoluteTolerance() > 1.0) {
            result.addWarning("Absolute tolerance > 1.0 may be too large");
        } else if (settings.getAbsoluteTolerance() < 1e-15) {
            result.addWarning("Absolute tolerance < 1e-15 may cause precision issues");
        }
        
        // 验证相对容差
        if (settings.getRelativeTolerance() < 0) {
            result.addError("Relative tolerance cannot be negative");
        } else if (settings.getRelativeTolerance() > 0.1) {
            result.addWarning("Relative tolerance > 10% may be too large");
        }
        
        // 验证日期容差
        if (settings.getDateToleranceMs() < 0) {
            result.addError("Date tolerance cannot be negative");
        } else if (settings.getDateToleranceMs() > 86400000L) { // 1天
            result.addWarning("Date tolerance > 1 day may be too large");
        }
        
        return result.build();
    }
    
    /**
     * 清除字段精度缓存
     */
    public void clearCache() {
        fieldPrecisionCache.clear();
        logger.info("Precision cache cleared");
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", fieldPrecisionCache.size());
        stats.put("maxCacheSize", ConfigDefaults.MAX_CACHED_CLASSES);
        stats.put("cacheHitRate", "N/A"); // 可以扩展实现命中率统计
        return stats;
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        private ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private final List<String> errors = new ArrayList<>();
            private final List<String> warnings = new ArrayList<>();
            
            public Builder addError(String error) {
                errors.add(error);
                return this;
            }
            
            public Builder addWarning(String warning) {
                warnings.add(warning);
                return this;
            }
            
            public ValidationResult build() {
                return new ValidationResult(errors.isEmpty(), errors, warnings);
            }
        }
    }
}