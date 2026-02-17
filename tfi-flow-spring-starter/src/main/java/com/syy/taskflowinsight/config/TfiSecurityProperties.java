package com.syy.taskflowinsight.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TFI 安全策略配置属性.
 *
 * <p>用于在 Spring 环境下配置 SpEL 安全求值和数据脱敏的安全策略参数。
 * 所有属性均提供合理默认值，用户可通过 {@code tfi.security.*} 进行自定义覆盖。
 *
 * <p>对应配置前缀：{@code tfi.security}
 *
 * <h3>SpEL 安全限制</h3>
 * <ul>
 *   <li>{@code tfi.security.spel-max-length} — 表达式最大长度</li>
 *   <li>{@code tfi.security.spel-max-nesting} — 括号最大嵌套深度</li>
 *   <li>{@code tfi.security.spel-blocked-patterns} — 表达式黑名单关键词</li>
 *   <li>{@code tfi.security.spel-cache-max-size} — 表达式缓存容量上限</li>
 * </ul>
 *
 * <h3>数据脱敏</h3>
 * <ul>
 *   <li>{@code tfi.security.sensitive-keywords} — 敏感字段关键词列表</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@Validated
@ConfigurationProperties(prefix = "tfi.security")
public class TfiSecurityProperties {

    /** SpEL 表达式最大长度，超过此长度的表达式将被拒绝. */
    @Min(value = 1, message = "spelMaxLength must be positive")
    private int spelMaxLength = 1000;

    /** SpEL 表达式括号最大嵌套深度. */
    @Min(value = 1, message = "spelMaxNesting must be positive")
    private int spelMaxNesting = 10;

    /** SpEL 表达式缓存最大条目数，防止无限增长导致 OOM. */
    @Min(value = 1, message = "spelCacheMaxSize must be positive")
    private int spelCacheMaxSize = 500;

    /** SpEL 表达式黑名单关键词，匹配到任意关键词的表达式将被拒绝. */
    private Set<String> spelBlockedPatterns = new LinkedHashSet<>(Set.of(
            "class", "getClass", "forName", "newInstance",
            "runtime", "exec", "process", "thread",
            "system", "security", "file", "io",
            "reflect", "unsafe", "classloader"
    ));

    /** 敏感字段关键词列表，字段名包含任意关键词时自动脱敏. */
    private Set<String> sensitiveKeywords = new LinkedHashSet<>(Set.of(
            "password", "token", "secret", "key", "credential",
            "apikey", "accesstoken", "refreshtoken", "privatekey",
            "oauth", "jwt", "session", "cookie", "auth",
            "pin", "cvv", "ssn", "passport", "license"
    ));

    /**
     * 获取 SpEL 表达式最大长度.
     *
     * @return 最大长度，默认 1000
     */
    public int getSpelMaxLength() {
        return spelMaxLength;
    }

    /**
     * 设置 SpEL 表达式最大长度.
     *
     * @param spelMaxLength 最大长度，必须为正整数
     */
    public void setSpelMaxLength(int spelMaxLength) {
        this.spelMaxLength = spelMaxLength;
    }

    /**
     * 获取 SpEL 表达式括号最大嵌套深度.
     *
     * @return 最大嵌套深度，默认 10
     */
    public int getSpelMaxNesting() {
        return spelMaxNesting;
    }

    /**
     * 设置 SpEL 表达式括号最大嵌套深度.
     *
     * @param spelMaxNesting 最大嵌套深度，必须为正整数
     */
    public void setSpelMaxNesting(int spelMaxNesting) {
        this.spelMaxNesting = spelMaxNesting;
    }

    /**
     * 获取 SpEL 表达式缓存最大条目数.
     *
     * @return 缓存上限，默认 500
     */
    public int getSpelCacheMaxSize() {
        return spelCacheMaxSize;
    }

    /**
     * 设置 SpEL 表达式缓存最大条目数.
     *
     * @param spelCacheMaxSize 缓存上限，必须为正整数
     */
    public void setSpelCacheMaxSize(int spelCacheMaxSize) {
        this.spelCacheMaxSize = spelCacheMaxSize;
    }

    /**
     * 获取 SpEL 表达式黑名单关键词集合.
     *
     * @return 黑名单关键词集合（不可变副本语义，但实际为可变 Set 以支持 Spring 绑定）
     */
    public Set<String> getSpelBlockedPatterns() {
        return spelBlockedPatterns;
    }

    /**
     * 设置 SpEL 表达式黑名单关键词集合.
     *
     * @param spelBlockedPatterns 黑名单关键词集合
     */
    public void setSpelBlockedPatterns(Set<String> spelBlockedPatterns) {
        this.spelBlockedPatterns = spelBlockedPatterns;
    }

    /**
     * 获取敏感字段关键词列表.
     *
     * @return 敏感关键词集合
     */
    public Set<String> getSensitiveKeywords() {
        return sensitiveKeywords;
    }

    /**
     * 设置敏感字段关键词列表.
     *
     * @param sensitiveKeywords 敏感关键词集合
     */
    public void setSensitiveKeywords(Set<String> sensitiveKeywords) {
        this.sensitiveKeywords = sensitiveKeywords;
    }
}
