package com.syy.taskflowinsight.masking;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 统一数据脱敏器
 * 
 * 与TFI Endpoint保持一致的脱敏策略，确保敏感信息不会泄露
 * 
 * @since 3.0.0
 */
@Component
public class UnifiedDataMasker {
    
    // 敏感字段关键词（与现有ChangeTrackingPropertiesV2保持一致）
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "token", "secret", "key", "credential",
        "apikey", "accesstoken", "refreshtoken", "privatekey",
        "oauth", "jwt", "session", "cookie", "auth",
        "pin", "cvv", "ssn", "passport", "license"
    );
    
    // 敏感数据模式
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = 
        Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");
    
    /**
     * 脱敏处理主入口
     */
    public String maskValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        
        String stringValue = String.valueOf(value);
        
        // 1. 检查字段名是否敏感
        if (isSensitiveField(fieldName)) {
            return maskSensitiveValue(stringValue, MaskingPolicy.STRONG);
        }
        
        // 2. 检查值内容是否包含敏感信息
        if (containsSensitiveContent(stringValue)) {
            return maskSensitiveContent(stringValue);
        }
        
        return stringValue;
    }
    
    /**
     * 强制脱敏（用于强制脱敏场景）
     */
    public String maskValue(String fieldName, Object value, boolean forceMask) {
        if (forceMask) {
            return maskSensitiveValue(String.valueOf(value), MaskingPolicy.STRONG);
        }
        return maskValue(fieldName, value);
    }
    
    /**
     * 检查字段名是否敏感
     */
    private boolean isSensitiveField(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        
        String lowerField = fieldName.toLowerCase();
        
        // 检查内置敏感关键词
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerField.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查值内容是否包含敏感信息
     */
    private boolean containsSensitiveContent(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        
        // 检查邮箱模式
        if (EMAIL_PATTERN.matcher(value).find()) {
            return true;
        }
        
        // 检查电话模式
        if (PHONE_PATTERN.matcher(value).find()) {
            return true;
        }
        
        // 检查信用卡模式
        if (CREDIT_CARD_PATTERN.matcher(value).find()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 脱敏敏感内容
     */
    private String maskSensitiveContent(String value) {
        String result = value;
        
        // 脱敏邮箱
        result = EMAIL_PATTERN.matcher(result).replaceAll(matchResult -> maskEmail(matchResult.group()));
        
        // 脱敏电话
        result = PHONE_PATTERN.matcher(result).replaceAll(matchResult -> maskPhone(matchResult.group()));
        
        // 脱敏信用卡
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(matchResult -> maskCreditCard(matchResult.group()));
        
        return result;
    }
    
    /**
     * 脱敏敏感值
     */
    private String maskSensitiveValue(String value, MaskingPolicy policy) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        
        switch (policy) {
            case STRONG:
                return maskStrong(value);
            case MEDIUM:
                return maskMedium(value);
            case WEAK:
                return maskWeak(value);
            default:
                return "***";
        }
    }
    
    private String maskStrong(String value) {
        if (value.length() <= 2) {
            return "***";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }
    
    private String maskMedium(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        int visibleLength = Math.min(2, value.length() / 4);
        return value.substring(0, visibleLength) + 
               "*".repeat(value.length() - visibleLength * 2) + 
               value.substring(value.length() - visibleLength);
    }
    
    private String maskWeak(String value) {
        if (value.length() <= 6) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 3) + 
               "***" + 
               value.substring(value.length() - 3);
    }
    
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***@***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
    
    private String maskPhone(String phone) {
        // 保留前3位和后4位
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() < 7) {
            return "***-***-****";
        }
        return digits.substring(0, 3) + "-***-" + digits.substring(digits.length() - 4);
    }
    
    private String maskCreditCard(String card) {
        // 只显示后4位
        String digits = card.replaceAll("[^\\d]", "");
        if (digits.length() < 4) {
            return "****-****-****-****";
        }
        return "****-****-****-" + digits.substring(digits.length() - 4);
    }
    
    /**
     * 脱敏策略枚举
     */
    public enum MaskingPolicy {
        STRONG,   // 强脱敏：只保留首尾字符
        MEDIUM,   // 中等脱敏：保留部分字符
        WEAK      // 弱脱敏：保留较多字符
    }
}