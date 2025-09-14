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
     * 默认false，使用浅快照以保证兼容性
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
     * 包含路径模式
     * 空列表表示包含所有路径
     */
    private List<String> includePatterns = new ArrayList<>();
    
    /**
     * 排除路径模式
     * 默认排除敏感字段
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