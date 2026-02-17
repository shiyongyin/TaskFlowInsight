package com.syy.taskflowinsight.spel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上下文感知的 SpEL 求值器.
 *
 * <p>专门处理 Map 类型的上下文对象，支持 {@code #variable} 访问模式。
 *
 * @deprecated 自 4.0.0 起废弃。请使用 {@link SafeSpELEvaluator}，它已内置 Map 上下文支持
 *     和完整的安全防御机制。此类将在 5.0.0 版本中移除。
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Deprecated(since = "4.0.0", forRemoval = true)
@Component
public class ContextAwareSpELEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(ContextAwareSpELEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 支持 Map 变量访问的 SpEL 求值.
     *
     * @param expression   SpEL 表达式
     * @param variables    变量映射
     * @param expectedType 期望的返回类型
     * @param <T>          返回类型泛型
     * @return 求值结果；异常时返回 {@code null}
     * @deprecated 使用 {@link SafeSpELEvaluator#evaluateExpression(String, Object, Class)} 替代
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    public <T> T evaluateWithVariables(String expression, Map<String, Object> variables, Class<T> expectedType) {
        if (!StringUtils.hasText(expression) || variables == null) {
            return null;
        }

        try {
            Expression expr = getCompiledExpression(expression);
            EvaluationContext context = createSecureContext(variables);
            return expr.getValue(context, expectedType);
        } catch (Exception e) {
            logger.warn("Context-aware SpEL evaluation failed: {} - {}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * 布尔条件求值.
     *
     * @param condition SpEL 条件表达式
     * @param variables 变量映射
     * @return 求值结果；异常时返回 {@code false}
     * @deprecated 使用 {@link SafeSpELEvaluator#evaluateCondition(String, Object)} 替代
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    public boolean evaluateConditionWithVariables(String condition, Map<String, Object> variables) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        Boolean result = evaluateWithVariables(condition, variables, Boolean.class);
        return result != null ? result : false;
    }

    /**
     * 字符串求值.
     *
     * @param expression SpEL 字符串表达式
     * @param variables  变量映射
     * @return 求值结果的字符串表示；异常时返回空串
     * @deprecated 使用 {@link SafeSpELEvaluator#evaluateString(String, Object)} 替代
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
    public String evaluateStringWithVariables(String expression, Map<String, Object> variables) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        Object result = evaluateWithVariables(expression, variables, Object.class);
        return result != null ? result.toString() : "";
    }

    /**
     * 创建安全的求值上下文（添加 TypeLocator / MethodResolver / ConstructorResolver 限制）.
     *
     * @param variables 变量映射
     * @return 安全限制的求值上下文
     */
    private EvaluationContext createSecureContext(Map<String, Object> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        // 安全限制：禁用类型引用、方法解析和构造器解析
        context.setTypeLocator(typeName -> {
            throw new SecurityException("Type references are not allowed");
        });
        context.setMethodResolvers(Collections.emptyList());
        context.setConstructorResolvers(Collections.emptyList());

        return context;
    }

    /**
     * 获取编译后的表达式（带缓存）.
     *
     * @param expressionString 表达式字符串
     * @return 编译后的 {@link Expression}
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
}
