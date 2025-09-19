package com.syy.taskflowinsight.spel;

import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上下文感知的SpEL求值器
 * 
 * 专门处理Map类型的上下文对象，支持#variable访问模式
 * 
 * @since 3.0.0
 */
@Component
public class ContextAwareSpELEvaluator {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    /**
     * 支持Map变量访问的SpEL求值
     */
    public <T> T evaluateWithVariables(String expression, Map<String, Object> variables, Class<T> expectedType) {
        if (!StringUtils.hasText(expression) || variables == null) {
            return null;
        }
        
        try {
            // 编译表达式
            Expression expr = getCompiledExpression(expression);
            
            // 创建支持变量访问的上下文
            // 注意：SimpleEvaluationContext不支持setVariable，需要使用StandardEvaluationContext
            org.springframework.expression.spel.support.StandardEvaluationContext context = 
                new org.springframework.expression.spel.support.StandardEvaluationContext();
            
            // 设置变量
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            
            return expr.getValue(context, expectedType);
            
        } catch (Exception e) {
            // 记录错误并返回null
            org.slf4j.LoggerFactory.getLogger(ContextAwareSpELEvaluator.class)
                .warn("Context-aware SpEL evaluation failed: {} - {}", expression, e.getMessage());
            return null;
        }
    }
    
    /**
     * 布尔条件求值
     */
    public boolean evaluateConditionWithVariables(String condition, Map<String, Object> variables) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        
        Boolean result = evaluateWithVariables(condition, variables, Boolean.class);
        return result != null ? result : false;
    }
    
    /**
     * 字符串求值
     */
    public String evaluateStringWithVariables(String expression, Map<String, Object> variables) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        
        Object result = evaluateWithVariables(expression, variables, Object.class);
        return result != null ? result.toString() : "";
    }
    
    private Expression getCompiledExpression(String expressionString) {
        return expressionCache.computeIfAbsent(expressionString, expr -> {
            try {
                return parser.parseExpression(expr);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid SpEL expression: " + expr, e);
            }
        });
    }
}