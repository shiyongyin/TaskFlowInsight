package com.syy.taskflowinsight.spel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * Enhanced tests targeting low-coverage classes in spel package
 * Focus: ContextAwareSpELEvaluator (10%), SafeSpELEvaluator.CacheStats (0%)
 */
class SpelPackageEnhancedTest {

    private ContextAwareSpELEvaluator contextEvaluator;
    private SafeSpELEvaluator safeEvaluator;

    @BeforeEach
    void setUp() {
        contextEvaluator = new ContextAwareSpELEvaluator();
        safeEvaluator = new SafeSpELEvaluator();
    }

    // ========== ContextAwareSpELEvaluator Coverage (提升10%→90%+) ==========

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 基本变量访问")
    void contextAwareEvaluator_basicVariableAccess() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "test");
        variables.put("age", 25);
        variables.put("active", true);
        
        // 测试String类型
        String result1 = contextEvaluator.evaluateWithVariables("#name", variables, String.class);
        assertThat(result1).isEqualTo("test");
        
        // 测试Integer类型
        Integer result2 = contextEvaluator.evaluateWithVariables("#age", variables, Integer.class);
        assertThat(result2).isEqualTo(25);
        
        // 测试Boolean类型
        Boolean result3 = contextEvaluator.evaluateWithVariables("#active", variables, Boolean.class);
        assertThat(result3).isTrue();
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 复杂表达式")
    void contextAwareEvaluator_complexExpressions() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("a", 10);
        variables.put("b", 5);
        variables.put("name", "John");
        
        // 数学运算
        Integer mathResult = contextEvaluator.evaluateWithVariables("#a + #b * 2", variables, Integer.class);
        assertThat(mathResult).isEqualTo(20);
        
        // 字符串操作
        String stringResult = contextEvaluator.evaluateWithVariables("#name + ' Doe'", variables, String.class);
        assertThat(stringResult).isEqualTo("John Doe");
        
        // 比较操作
        Boolean compareResult = contextEvaluator.evaluateWithVariables("#a > #b", variables, Boolean.class);
        assertThat(compareResult).isTrue();
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - null和空值处理")
    void contextAwareEvaluator_nullAndEmptyHandling() {
        // null expression
        String result1 = contextEvaluator.evaluateWithVariables(null, Map.of("key", "value"), String.class);
        assertThat(result1).isNull();
        
        // 空expression
        String result2 = contextEvaluator.evaluateWithVariables("", Map.of("key", "value"), String.class);
        assertThat(result2).isNull();
        
        // 空白expression
        String result3 = contextEvaluator.evaluateWithVariables("   ", Map.of("key", "value"), String.class);
        assertThat(result3).isNull();
        
        // null variables
        String result4 = contextEvaluator.evaluateWithVariables("#key", null, String.class);
        assertThat(result4).isNull();
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 异常处理")
    void contextAwareEvaluator_exceptionHandling() {
        Map<String, Object> variables = Map.of("test", "value");
        
        // 无效表达式语法
        String result1 = contextEvaluator.evaluateWithVariables("#invalid(", variables, String.class);
        assertThat(result1).isNull();
        
        // 访问不存在的变量
        String result2 = contextEvaluator.evaluateWithVariables("#nonexistent", variables, String.class);
        assertThat(result2).isNull();
        
        // 类型转换异常
        Integer result3 = contextEvaluator.evaluateWithVariables("#test", variables, Integer.class);
        assertThat(result3).isNull();
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 条件求值")
    void contextAwareEvaluator_conditionEvaluation() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("score", 85);
        variables.put("pass", true);
        
        // 正常条件
        boolean result1 = contextEvaluator.evaluateConditionWithVariables("#score >= 80", variables);
        assertThat(result1).isTrue();
        
        boolean result2 = contextEvaluator.evaluateConditionWithVariables("#score < 60", variables);
        assertThat(result2).isFalse();
        
        // 布尔变量
        boolean result3 = contextEvaluator.evaluateConditionWithVariables("#pass", variables);
        assertThat(result3).isTrue();
        
        // 空条件（默认true）
        boolean result4 = contextEvaluator.evaluateConditionWithVariables("", variables);
        assertThat(result4).isTrue();
        
        boolean result5 = contextEvaluator.evaluateConditionWithVariables(null, variables);
        assertThat(result5).isTrue();
        
        // 异常条件（默认false）
        boolean result6 = contextEvaluator.evaluateConditionWithVariables("#invalid(", variables);
        assertThat(result6).isFalse();
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 字符串求值")
    void contextAwareEvaluator_stringEvaluation() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Alice");
        variables.put("count", 42);
        
        // 正常字符串
        String result1 = contextEvaluator.evaluateStringWithVariables("#name", variables);
        assertThat(result1).isEqualTo("Alice");
        
        // 数字转字符串
        String result2 = contextEvaluator.evaluateStringWithVariables("#count", variables);
        assertThat(result2).isEqualTo("42");
        
        // 空表达式（返回空字符串）
        String result3 = contextEvaluator.evaluateStringWithVariables("", variables);
        assertThat(result3).isEqualTo("");
        
        String result4 = contextEvaluator.evaluateStringWithVariables(null, variables);
        assertThat(result4).isEqualTo("");
        
        // 异常表达式（返回空字符串）
        String result5 = contextEvaluator.evaluateStringWithVariables("#invalid(", variables);
        assertThat(result5).isEqualTo("");
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 表达式缓存")
    void contextAwareEvaluator_expressionCaching() {
        Map<String, Object> variables = Map.of("value", "test");
        
        // 多次使用相同表达式，验证缓存工作
        String result1 = contextEvaluator.evaluateWithVariables("#value", variables, String.class);
        String result2 = contextEvaluator.evaluateWithVariables("#value", variables, String.class);
        String result3 = contextEvaluator.evaluateWithVariables("#value", variables, String.class);
        
        assertThat(result1).isEqualTo("test");
        assertThat(result2).isEqualTo("test");
        assertThat(result3).isEqualTo("test");
        
        // 验证缓存中有表达式
        ConcurrentHashMap<String, ?> cache = (ConcurrentHashMap<String, ?>) 
            ReflectionTestUtils.getField(contextEvaluator, "expressionCache");
        assertThat(cache).containsKey("#value");
    }

    @Test
    @DisplayName("ContextAwareSpELEvaluator - 表达式解析异常")
    void contextAwareEvaluator_parseException() {
        // 通过反射直接测试getCompiledExpression方法的异常处理
        assertThatThrownBy(() -> {
            java.lang.reflect.Method method = ContextAwareSpELEvaluator.class
                .getDeclaredMethod("getCompiledExpression", String.class);
            method.setAccessible(true);
            method.invoke(contextEvaluator, "#invalid expression with ( unmatched parenthesis");
        }).isInstanceOf(java.lang.reflect.InvocationTargetException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .satisfies(ex -> {
              Throwable cause = ex.getCause();
              assertThat(cause.getMessage()).startsWith("Invalid SpEL expression: #invalid expression with ( unmatched parenthesis");
          });
    }

    // ========== SafeSpELEvaluator.CacheStats Coverage (提升0%→100%) ==========

    @Test
    @DisplayName("SafeSpELEvaluator.CacheStats - 所有方法覆盖")
    void safeSpelEvaluator_cacheStatsAllMethods() {
        // 获取CacheStats实例
        SafeSpELEvaluator.CacheStats stats = safeEvaluator.getCacheStats();
        assertThat(stats).isNotNull();
        
        // 测试所有getter方法
        assertThat(stats.getCachedExpressions()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getAllowedTypes()).isGreaterThan(0);
        assertThat(stats.getBlockedPatterns()).isGreaterThan(0);
    }

    @Test
    @DisplayName("SafeSpELEvaluator.CacheStats - 缓存统计验证")
    void safeSpelEvaluator_cacheStatsVerification() {
        // 执行一些表达式来增加缓存统计
        Object testObj = new Object();
        safeEvaluator.evaluateExpression("'hello'", testObj, String.class);
        safeEvaluator.evaluateExpression("1 + 2", testObj, Integer.class);
        safeEvaluator.evaluateExpression("true", testObj, Boolean.class);
        
        // 重复执行相同表达式来增加缓存
        safeEvaluator.evaluateExpression("'hello'", testObj, String.class);
        safeEvaluator.evaluateExpression("1 + 2", testObj, Integer.class);
        
        SafeSpELEvaluator.CacheStats stats = safeEvaluator.getCacheStats();
        
        // 验证统计数据合理性
        assertThat(stats.getCachedExpressions()).isGreaterThan(0);
        assertThat(stats.getAllowedTypes()).isGreaterThan(0);
        assertThat(stats.getBlockedPatterns()).isGreaterThan(0);
    }

    // ========== Additional SafeSpELEvaluator Coverage ==========

    @Test
    @DisplayName("SafeSpELEvaluator - 额外边界测试")
    void safeSpelEvaluator_additionalBoundaryTests() {
        Object testObj = new Object();
        
        // 测试复杂嵌套表达式
        String complexExpr = "'prefix' + ('middle' + 'suffix')";
        String result1 = safeEvaluator.evaluateExpression(complexExpr, testObj, String.class);
        assertThat(result1).isEqualTo("prefixmiddlesuffix");
        
        // 测试数学运算
        Integer mathResult = safeEvaluator.evaluateExpression("(10 + 5) * 2", testObj, Integer.class);
        assertThat(mathResult).isEqualTo(30);
        
        // 测试布尔逻辑
        Boolean boolResult = safeEvaluator.evaluateExpression("true && false", testObj, Boolean.class);
        assertThat(boolResult).isFalse();
    }

    @Test
    @DisplayName("SafeSpELEvaluator - 条件求值边界")
    void safeSpelEvaluator_conditionEvaluationBoundary() {
        Object testObj = new Object();
        
        // 复杂条件
        boolean result1 = safeEvaluator.evaluateCondition("10 > 5 && 'test'.length() == 4", testObj);
        assertThat(result1).isTrue();
        
        // 字符串比较
        boolean result2 = safeEvaluator.evaluateCondition("'abc' == 'abc'", testObj);
        assertThat(result2).isTrue();
        
        // 数值比较
        boolean result3 = safeEvaluator.evaluateCondition("100.5 >= 100", testObj);
        assertThat(result3).isTrue();
    }

    @Test
    @DisplayName("集成测试 - 两个评估器协同工作")
    void integrationTest_bothEvaluatorsWorking() {
        // 测试SafeSpELEvaluator的安全表达式
        Object testObj = new Object();
        String safeResult = safeEvaluator.evaluateExpression("'Safe: ' + (2 * 3)", testObj, String.class);
        assertThat(safeResult).isEqualTo("Safe: 6");
        
        // 测试ContextAwareSpELEvaluator的变量访问
        Map<String, Object> vars = Map.of("multiplier", 3, "base", 2);
        Integer contextResult = contextEvaluator.evaluateWithVariables("#base * #multiplier", vars, Integer.class);
        assertThat(contextResult).isEqualTo(6);
        
        // 验证两者结果一致性
        assertThat(safeResult).contains("6");
        assertThat(contextResult).isEqualTo(6);
    }
}