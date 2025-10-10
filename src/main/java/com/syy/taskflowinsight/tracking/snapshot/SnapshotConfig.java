package com.syy.taskflowinsight.tracking.snapshot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 快照配置类
 * 控制深度快照的行为和限制
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Component
@ConfigurationProperties(prefix = "tfi.change-tracking.snapshot")
public class SnapshotConfig {
    
    /**
     * 是否启用深度快照
     * 默认 false，保持与历史行为一致（按需开启）
     */
    private boolean enableDeep = false;
    
    /**
     * 深度快照最大深度
     * 控制对象图遍历的最大层级
     */
    private int maxDepth = 3;
    
    /**
     * 最大栈深度（防止栈溢出）
     * 用于限制递归调用深度
     */
    private int maxStackDepth = 1000;
    
    /**
     * 包含路径模式（Include白名单）
     * 空列表表示包含所有路径
     *
     * 优先级：最高（7级决策链P1），可覆盖所有其他过滤规则：
     * - 覆盖 @DiffIgnore 字段注解
     * - 覆盖 excludePatterns/regexExcludes 路径黑名单
     * - 覆盖 @IgnoreDeclaredProperties/@IgnoreInheritedProperties 类级注解
     * - 覆盖 excludePackages 包级过滤
     * - 覆盖 defaultExclusionsEnabled 默认忽略规则
     *
     * 用途：精准控制业务关键字段，即使其他规则忽略也强制包含
     * 示例：List.of("email", "audit.password") - 即使password被排除，audit.password仍包含
     *
     * @since v3.0.0 (P2-T4)
     */
    private List<String> includePatterns = new ArrayList<>();
    
    /**
     * 排除路径模式（Glob语法路径黑名单）
     * 默认排除敏感字段
     *
     * 优先级：第3级（7级决策链P3）
     * - 低于 Include白名单（可被includePatterns覆盖）
     * - 低于 @DiffIgnore 字段注解
     * - 高于 类级注解、包级过滤、默认忽略规则
     *
     * 语法支持（Glob）：
     * - "field" - 精确匹配
     * - "*.password" - 单层通配
     * - "internal.*" - 单层子字段
     * - "debug.**" - 递归通配
     * - "items[*].id" - 数组/集合元素
     *
     * 与 regexExcludes 并列使用，结果为并集
     *
     * @since v3.0.0 (P2-T2)
     */
    private List<String> excludePatterns = Arrays.asList(
        "*.password",
        "*.secret",
        "*.token",
        "*.credential",
        "*.key"
    );
    
    /**
     * 单次快照时间预算（毫秒）
     * 防止深度遍历耗时过长
     */
    private long timeBudgetMs = 50;
    
    /**
     * 集合摘要阈值
     * 超过此大小的集合将被摘要化
     */
    private int collectionSummaryThreshold = 100;
    
    /**
     * 是否记录深度快照指标
     */
    private boolean metricsEnabled = true;

    /**
     * 浅引用字段的快照模式
     * 控制 @ShallowReference 标注字段的快照生成策略
     * 默认 VALUE_ONLY 保持向后兼容
     *
     * @since v3.0.0 (P2-2)
     */
    private ShallowReferenceMode shallowReferenceMode = ShallowReferenceMode.VALUE_ONLY;

    /**
     * 排除包列表（类级/包级过滤）
     * 支持通配符，如 "com.example.internal.**"
     * 用于批量忽略指定包下的类，减少样板代码
     *
     * @since v3.0.0 (P2-T1)
     */
    private List<String> excludePackages = new ArrayList<>();

    /**
     * 正则表达式排除列表（路径级过滤）
     * 支持完整的正则表达式语法，如 "^debug_\\d{4}$"
     * 与excludePatterns（Glob）并列，结果为并集
     *
     * @since v3.0.0 (P2-T2)
     */
    private List<String> regexExcludes = new ArrayList<>();

    /**
     * 是否启用默认排除规则
     * 控制是否自动忽略噪音字段（static/transient/synthetic/logger/serialVersionUID/$jacocoData）
     * 默认 true，开箱即用减少噪音
     * 设为 false 时，仅使用显式配置的过滤规则
     *
     * 注意：Include 白名单可覆盖默认排除（优先级更高）
     *
     * @since v3.0.0 (P2-T3)
     */
    private boolean defaultExclusionsEnabled = true;

    /**
     * 获取包含模式集合
     */
    public Set<String> getIncludePatternSet() {
        return new HashSet<>(includePatterns);
    }
    
    /**
     * 获取排除模式集合
     */
    public Set<String> getExcludePatternSet() {
        return new HashSet<>(excludePatterns);
    }

    /**
     * 获取正则排除模式集合
     *
     * @return 正则表达式排除集合
     * @since v3.0.0 (P2-T2)
     */
    public Set<String> getRegexExcludeSet() {
        return new HashSet<>(regexExcludes);
    }

    /**
     * 检查是否应该排除指定路径
     */
    public boolean shouldExclude(String path) {
        if (path == null) {
            return false;
        }
        
        // 如果有包含模式，只处理匹配的路径
        if (!includePatterns.isEmpty()) {
            boolean included = includePatterns.stream()
                .anyMatch(pattern -> matchesPattern(path, pattern));
            if (!included) {
                return true;
            }
        }
        
        // 检查排除模式
        return excludePatterns.stream()
            .anyMatch(pattern -> matchesPattern(path, pattern));
    }
    
    /**
     * 简单的通配符匹配
     */
    private boolean matchesPattern(String path, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        
        // 转换通配符为正则表达式
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^.]*")
            .replace("?", ".");
            
        return path.matches(regex);
    }
}
