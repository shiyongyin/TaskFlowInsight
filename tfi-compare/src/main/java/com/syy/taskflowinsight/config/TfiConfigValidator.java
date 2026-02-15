package com.syy.taskflowinsight.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * TFI配置验证器
 * 轻量级验证器，提供配置完整性和一致性检查
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-09-18
 */
@Component
@Validated
public class TfiConfigValidator {

    /**
     * 验证TFI配置的一致性
     * 
     * @param config TFI配置对象
     * @return 验证结果消息，成功返回null
     */
    public String validate(TfiConfig config) {
        if (config == null) {
            return "TfiConfig cannot be null";
        }

        // 基础验证
        if (!config.isValid()) {
            return "TfiConfig failed basic validation checks";
        }

        // 逻辑一致性验证
        if (config.changeTracking().enabled() && !config.enabled()) {
            return "Change tracking requires tfi.enabled=true";
        }

        if (config.changeTracking().snapshot().maxDepth() > 50) {
            return "Snapshot max depth should not exceed 50 for performance reasons";
        }

        if (config.changeTracking().valueReprMaxLength() > 1_000_000) {
            return "Value representation max length is too large (>1MB)";
        }

        if (config.context().maxAgeMillis() < 1000) {
            return "Context max age should be at least 1 second";
        }

        if (config.context().leakDetectionEnabled() && 
            config.context().leakDetectionIntervalMillis() < 10000) {
            return "Leak detection interval should be at least 10 seconds";
        }

        // 性能相关验证
        if (config.changeTracking().maxCachedClasses() > 10000) {
            return "Max cached classes should not exceed 10000 for memory efficiency";
        }

        if (config.changeTracking().diff().maxChangesPerObject() > 5000) {
            return "Max changes per object should not exceed 5000";
        }

        return null; // 验证通过
    }

    /**
     * 验证并抛出异常（如果验证失败）
     * 
     * @param config TFI配置对象
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validateAndThrow(TfiConfig config) {
        String error = validate(config);
        if (error != null) {
            throw new IllegalArgumentException("TFI configuration validation failed: " + error);
        }
    }

    /**
     * 自定义配置验证注解
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = TfiConfigConstraintValidator.class)
    @Documented
    public @interface ValidTfiConfig {
        String message() default "Invalid TFI configuration";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * 配置约束验证器实现
     */
    public static class TfiConfigConstraintValidator 
            implements ConstraintValidator<ValidTfiConfig, TfiConfig> {

        private final TfiConfigValidator validator = new TfiConfigValidator();

        @Override
        public boolean isValid(TfiConfig config, ConstraintValidatorContext context) {
            if (config == null) {
                return true; // null值由@NotNull处理
            }

            String error = validator.validate(config);
            if (error != null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(error)
                       .addConstraintViolation();
                return false;
            }
            return true;
        }
    }
}