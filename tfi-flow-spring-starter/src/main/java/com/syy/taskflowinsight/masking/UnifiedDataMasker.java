package com.syy.taskflowinsight.masking;

import com.syy.taskflowinsight.config.TfiSecurityProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 统一数据脱敏器.
 *
 * <p>提供字段名敏感检测和值内容敏感检测的双重脱敏能力，确保 TFI 追踪日志中不会泄露
 * 密码、Token、邮箱、电话、信用卡等敏感信息。
 *
 * <h3>脱敏策略</h3>
 * <ol>
 *   <li><b>字段名检测</b> — 字段名包含 {@code password}, {@code token} 等关键词时，
 *       使用 STRONG 策略脱敏</li>
 *   <li><b>值内容检测</b> — 值匹配邮箱、电话或信用卡模式时，使用模式感知脱敏</li>
 *   <li><b>透传</b> — 非敏感内容原值返回</li>
 * </ol>
 *
 * <h3>可配置性</h3>
 * <p>敏感关键词列表通过 {@code tfi.security.sensitive-keywords} 自定义，
 * 默认包含 19 个常见敏感字段关键词。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 * @see TfiSecurityProperties
 */
@Component
public class UnifiedDataMasker {

    /** 默认脱敏占位符. */
    private static final String MASK_PLACEHOLDER = "***";

    /** STRONG 脱敏短值阈值. */
    private static final int STRONG_SHORT_THRESHOLD = 2;

    /** MEDIUM 脱敏短值阈值. */
    private static final int MEDIUM_SHORT_THRESHOLD = 4;

    /** MEDIUM 脱敏短值占位符. */
    private static final String MEDIUM_MASK_PLACEHOLDER = "****";

    /** WEAK 脱敏短值阈值. */
    private static final int WEAK_SHORT_THRESHOLD = 6;

    /** WEAK 脱敏可见字符数. */
    private static final int WEAK_VISIBLE_LENGTH = 3;

    /** 默认敏感关键词集合（当 TfiSecurityProperties 不可用时使用）. */
    private static final Set<String> DEFAULT_SENSITIVE_KEYWORDS = Set.of(
            "password", "token", "secret", "key", "credential",
            "apikey", "accesstoken", "refreshtoken", "privatekey",
            "oauth", "jwt", "session", "cookie", "auth",
            "pin", "cvv", "ssn", "passport", "license"
    );

    // 敏感数据正则模式（预编译，线程安全）
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");

    /** 当前生效的敏感关键词集合. */
    private final Set<String> sensitiveKeywords;

    /**
     * 使用默认敏感关键词构造脱敏器（非 Spring 环境或测试场景）.
     */
    public UnifiedDataMasker() {
        this(null);
    }

    /**
     * 使用可配置敏感关键词构造脱敏器.
     *
     * @param securityProperties 安全配置属性，{@code null} 时使用默认关键词
     */
    public UnifiedDataMasker(TfiSecurityProperties securityProperties) {
        if (securityProperties != null && securityProperties.getSensitiveKeywords() != null
                && !securityProperties.getSensitiveKeywords().isEmpty()) {
            this.sensitiveKeywords = Set.copyOf(securityProperties.getSensitiveKeywords());
        } else {
            this.sensitiveKeywords = DEFAULT_SENSITIVE_KEYWORDS;
        }
    }

    /**
     * 脱敏处理主入口.
     *
     * <p>按优先级依次检查字段名敏感性和值内容敏感性，匹配后自动脱敏。
     *
     * @param fieldName 字段名（用于关键词匹配）
     * @param value     字段值
     * @return 脱敏后的字符串；{@code value} 为 {@code null} 时返回 {@code null}
     */
    public String maskValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value);

        if (isSensitiveField(fieldName)) {
            return maskSensitiveValue(stringValue, MaskingPolicy.STRONG);
        }

        if (containsSensitiveContent(stringValue)) {
            return maskSensitiveContent(stringValue);
        }

        return stringValue;
    }

    /**
     * 强制脱敏（无论字段名是否敏感）.
     *
     * @param fieldName 字段名
     * @param value     字段值
     * @param forceMask 是否强制脱敏
     * @return 脱敏后的字符串
     */
    public String maskValue(String fieldName, Object value, boolean forceMask) {
        if (forceMask) {
            return maskSensitiveValue(String.valueOf(value), MaskingPolicy.STRONG);
        }
        return maskValue(fieldName, value);
    }

    /**
     * 检查字段名是否匹配敏感关键词.
     *
     * @param fieldName 字段名
     * @return {@code true} 表示该字段名包含敏感关键词
     */
    private boolean isSensitiveField(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }

        String lowerField = fieldName.toLowerCase();
        for (String keyword : sensitiveKeywords) {
            if (lowerField.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查值内容是否匹配敏感数据模式（邮箱、电话、信用卡）.
     *
     * @param value 待检查的字符串值
     * @return {@code true} 表示值中包含敏感内容
     */
    private boolean containsSensitiveContent(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(value).find()
                || PHONE_PATTERN.matcher(value).find()
                || CREDIT_CARD_PATTERN.matcher(value).find();
    }

    /**
     * 对值中的敏感内容进行模式感知脱敏.
     *
     * @param value 包含敏感内容的字符串
     * @return 脱敏后的字符串
     */
    private String maskSensitiveContent(String value) {
        String result = value;
        result = EMAIL_PATTERN.matcher(result).replaceAll(m -> maskEmail(m.group()));
        result = PHONE_PATTERN.matcher(result).replaceAll(m -> maskPhone(m.group()));
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll(m -> maskCreditCard(m.group()));
        return result;
    }

    /**
     * 按脱敏策略处理敏感值.
     *
     * @param value  待脱敏的值
     * @param policy 脱敏策略
     * @return 脱敏后的字符串
     */
    private String maskSensitiveValue(String value, MaskingPolicy policy) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return switch (policy) {
            case STRONG -> maskStrong(value);
            case MEDIUM -> maskMedium(value);
            case WEAK -> maskWeak(value);
        };
    }

    /**
     * 强脱敏：仅保留首尾字符.
     *
     * @param value 原始值
     * @return 脱敏后的值，短值返回 {@code ***}
     */
    private String maskStrong(String value) {
        if (value.length() <= STRONG_SHORT_THRESHOLD) {
            return MASK_PLACEHOLDER;
        }
        return value.charAt(0) + MASK_PLACEHOLDER + value.charAt(value.length() - 1);
    }

    /**
     * 中等脱敏：保留前后各 1/4 的字符.
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    private String maskMedium(String value) {
        if (value.length() <= MEDIUM_SHORT_THRESHOLD) {
            return MEDIUM_MASK_PLACEHOLDER;
        }
        int visibleLength = Math.min(2, value.length() / 4);
        return value.substring(0, visibleLength)
                + "*".repeat(value.length() - visibleLength * 2)
                + value.substring(value.length() - visibleLength);
    }

    /**
     * 弱脱敏：保留前后各 3 个字符.
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    private String maskWeak(String value) {
        if (value.length() <= WEAK_SHORT_THRESHOLD) {
            return value.charAt(0) + MASK_PLACEHOLDER + value.charAt(value.length() - 1);
        }
        return value.substring(0, WEAK_VISIBLE_LENGTH)
                + MASK_PLACEHOLDER
                + value.substring(value.length() - WEAK_VISIBLE_LENGTH);
    }

    /**
     * 邮箱脱敏：保留首字符和域名.
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱，例如 {@code t***@example.com}
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return MASK_PLACEHOLDER + "@" + MASK_PLACEHOLDER;
        }
        return email.charAt(0) + MASK_PLACEHOLDER + email.substring(atIndex);
    }

    /**
     * 电话脱敏：保留前 3 位和后 4 位.
     *
     * @param phone 电话号码
     * @return 脱敏后的电话，例如 {@code 123-***-7890}
     */
    private String maskPhone(String phone) {
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() < 7) {
            return MASK_PLACEHOLDER + "-" + MASK_PLACEHOLDER + "-****";
        }
        return digits.substring(0, 3) + "-" + MASK_PLACEHOLDER + "-" + digits.substring(digits.length() - 4);
    }

    /**
     * 信用卡脱敏：仅显示后 4 位.
     *
     * @param card 信用卡号
     * @return 脱敏后的卡号，例如 {@code ****-****-****-3456}
     */
    private String maskCreditCard(String card) {
        String digits = card.replaceAll("[^\\d]", "");
        if (digits.length() < 4) {
            return "****-****-****-****";
        }
        return "****-****-****-" + digits.substring(digits.length() - 4);
    }

    /**
     * 脱敏策略枚举.
     *
     * @since 3.0.0
     */
    public enum MaskingPolicy {
        /** 强脱敏：仅保留首尾字符，适用于密码、Token 等高敏感字段. */
        STRONG,
        /** 中等脱敏：保留前后各 1/4 字符，适用于一般敏感字段. */
        MEDIUM,
        /** 弱脱敏：保留前后各 3 字符，适用于低敏感字段. */
        WEAK
    }
}
