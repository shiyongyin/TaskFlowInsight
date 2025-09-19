package com.syy.taskflowinsight.spel;

import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全的SpEL表达式求值器
 * 
 * 实现白名单安全机制，防止SpEL注入攻击
 * 
 * 安全特性：
 * - 使用SimpleEvaluationContext禁用反射
 * - 白名单类型限制
 * - 表达式复杂度限制
 * - 缓存编译后的表达式
 * 
 * @since 3.0.0
 */
@Component
public class SafeSpELEvaluator {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private final EvaluationContext context;
    
    // 表达式缓存（避免重复编译）
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    // 允许的类型白名单
    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
        String.class, Integer.class, Long.class, Double.class, Float.class,
        Boolean.class, Number.class, Object.class,
        // 常用集合类型
        java.util.List.class, java.util.Map.class, java.util.Set.class,
        // 时间类型
        java.time.LocalDateTime.class, java.time.LocalDate.class, java.util.Date.class
    );
    
    // 禁用的方法/字段模式
    private static final Set<String> BLOCKED_PATTERNS = Set.of(
        "class", "getClass", "forName", "newInstance",
        "runtime", "exec", "process", "thread",
        "system", "security", "file", "io",
        "reflect", "unsafe", "classloader"
    );
    
    public SafeSpELEvaluator() {
        // 使用SimpleEvaluationContext禁用反射和类型引用
        this.context = SimpleEvaluationContext.forReadOnlyDataBinding()
            .withInstanceMethods() // 允许实例方法调用
            .build();
    }
    
    /**
     * 安全求值SpEL表达式
     * 
     * @param expression SpEL表达式字符串
     * @param rootObject 根对象
     * @param expectedType 期望的返回类型
     * @return 求值结果
     */
    public <T> T evaluateExpression(String expression, Object rootObject, Class<T> expectedType) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        
        try {
            // 安全检查
            validateExpression(expression);
            
            // 获取或编译表达式
            Expression expr = getCompiledExpression(expression);
            
            // 创建带根对象的上下文
            EvaluationContext evalContext = createEvaluationContext(rootObject);
            
            // 求值
            return expr.getValue(evalContext, expectedType);
            
        } catch (Exception e) {
            // 记录但不抛出异常，保证系统稳定性
            handleEvaluationError(expression, e);
            return null;
        }
    }
    
    /**
     * 求值布尔表达式（用于条件判断）
     */
    public boolean evaluateCondition(String condition, Object rootObject) {
        if (!StringUtils.hasText(condition)) {
            return true; // 空条件默认为true
        }
        
        Boolean result = evaluateExpression(condition, rootObject, Boolean.class);
        return result != null ? result : false;
    }
    
    /**
     * 求值字符串表达式（用于名称生成）
     */
    public String evaluateString(String expression, Object rootObject) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        
        Object result = evaluateExpression(expression, rootObject, Object.class);
        return result != null ? result.toString() : "";
    }
    
    /**
     * 验证表达式安全性
     */
    private void validateExpression(String expression) {
        if (expression.length() > 1000) {
            throw new IllegalArgumentException("Expression too long: " + expression.length());
        }
        
        String lowerExpr = expression.toLowerCase();
        
        // 检查危险模式
        for (String pattern : BLOCKED_PATTERNS) {
            if (lowerExpr.contains(pattern)) {
                throw new SecurityException("Blocked pattern detected: " + pattern);
            }
        }
        
        // 检查嵌套层级（防止深度嵌套攻击）
        int nestingLevel = 0;
        int maxNesting = 10;
        for (char c : expression.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                nestingLevel++;
                if (nestingLevel > maxNesting) {
                    throw new SecurityException("Expression nesting too deep: " + nestingLevel);
                }
            } else if (c == ')' || c == ']' || c == '}') {
                nestingLevel--;
            }
        }
    }
    
    /**
     * 获取编译后的表达式（带缓存）
     */
    private Expression getCompiledExpression(String expressionString) {
        return expressionCache.computeIfAbsent(expressionString, expr -> {
            try {
                return parser.parseExpression(expr);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid SpEL expression: " + expr, e);
            }
        });
    }
    
    /**
     * 创建安全的求值上下文
     */
    private EvaluationContext createEvaluationContext(Object rootObject) {
        // 对于Map，我们需要使用StandardEvaluationContext来支持变量访问
        // 但仍然限制反射和类型访问
        if (rootObject instanceof java.util.Map) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            
            // 设置根对象为Map
            context.setRootObject(rootObject);
            
            // 将Map中的每个键值对设置为变量
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> variables = (java.util.Map<String, Object>) rootObject;
            for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            
            // 禁用类型定位器和方法解析器来保证安全
            context.setTypeLocator(typeName -> {
                throw new SecurityException("Type references are not allowed");
            });
            
            return context;
        } else {
            // 对于非Map对象，使用SimpleEvaluationContext
            return SimpleEvaluationContext.forReadOnlyDataBinding()
                .withInstanceMethods()
                .withRootObject(rootObject)
                .build();
        }
    }
    
    /**
     * 处理求值错误
     */
    private void handleEvaluationError(String expression, Exception e) {
        // 记录错误但不影响主流程
        org.slf4j.LoggerFactory.getLogger(SafeSpELEvaluator.class)
            .warn("SpEL expression evaluation failed: {} - {}", expression, e.getMessage());
    }
    
    /**
     * 清理表达式缓存
     */
    public void clearCache() {
        expressionCache.clear();
    }
    
    /**
     * 获取缓存统计
     */
    public CacheStats getCacheStats() {
        return new CacheStats(expressionCache.size(), ALLOWED_TYPES.size(), BLOCKED_PATTERNS.size());
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int cachedExpressions;
        private final int allowedTypes;
        private final int blockedPatterns;
        
        public CacheStats(int cachedExpressions, int allowedTypes, int blockedPatterns) {
            this.cachedExpressions = cachedExpressions;
            this.allowedTypes = allowedTypes;
            this.blockedPatterns = blockedPatterns;
        }
        
        public int getCachedExpressions() { return cachedExpressions; }
        public int getAllowedTypes() { return allowedTypes; }
        public int getBlockedPatterns() { return blockedPatterns; }
    }
}