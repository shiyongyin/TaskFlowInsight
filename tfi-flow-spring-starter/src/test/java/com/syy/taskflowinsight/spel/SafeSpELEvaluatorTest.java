package com.syy.taskflowinsight.spel;

import com.syy.taskflowinsight.config.TfiSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SafeSpELEvaluator} 单元测试.
 *
 * <p>覆盖安全校验（L1-L4）、表达式求值、缓存管理和可配置化场景。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("SafeSpELEvaluator 安全 SpEL 求值器测试")
class SafeSpELEvaluatorTest {

    private SafeSpELEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SafeSpELEvaluator();
    }

    // ── evaluateExpression ──

    @Nested
    @DisplayName("evaluateExpression - 通用求值")
    class EvaluateExpressionTests {

        @Test
        @DisplayName("空表达式返回 null")
        void nullExpression_returnsNull() {
            assertThat(evaluator.evaluateExpression(null, new Object(), String.class)).isNull();
            assertThat(evaluator.evaluateExpression("", new Object(), String.class)).isNull();
            assertThat(evaluator.evaluateExpression("  ", new Object(), String.class)).isNull();
        }

        @Test
        @DisplayName("Map 根对象支持变量访问")
        void mapRootObject_supportsVariableAccess() {
            Map<String, Object> context = Map.of("name", "TFI", "version", 3);
            assertThat(evaluator.evaluateExpression("#name", context, String.class)).isEqualTo("TFI");
            assertThat(evaluator.evaluateExpression("#version", context, Integer.class)).isEqualTo(3);
        }

        @Test
        @DisplayName("语法错误的表达式返回 null 不抛异常")
        void invalidSyntax_returnsNull() {
            assertThat(evaluator.evaluateExpression("{{invalid}}", Map.of(), String.class)).isNull();
        }

        @Test
        @DisplayName("类型不匹配返回 null")
        void typeMismatch_returnsNull() {
            Map<String, Object> context = Map.of("name", "text");
            assertThat(evaluator.evaluateExpression("#name", context, Integer.class)).isNull();
        }
    }

    // ── evaluateCondition ──

    @Nested
    @DisplayName("evaluateCondition - 布尔条件求值")
    class EvaluateConditionTests {

        @Test
        @DisplayName("空条件默认返回 true")
        void emptyCondition_returnsTrue() {
            assertThat(evaluator.evaluateCondition(null, null)).isTrue();
            assertThat(evaluator.evaluateCondition("", null)).isTrue();
        }

        @Test
        @DisplayName("true/false 字面量条件")
        void literalConditions() {
            assertThat(evaluator.evaluateCondition("true", Map.of())).isTrue();
            assertThat(evaluator.evaluateCondition("false", Map.of())).isFalse();
        }

        @Test
        @DisplayName("Map 变量条件判断")
        void variableCondition() {
            Map<String, Object> ctx = Map.of("methodName", "saveOrder");
            assertThat(evaluator.evaluateCondition(
                    "#methodName == 'saveOrder'", ctx)).isTrue();
            assertThat(evaluator.evaluateCondition(
                    "#methodName == 'other'", ctx)).isFalse();
        }

        @Test
        @DisplayName("异常条件返回 false")
        void invalidCondition_returnsFalse() {
            assertThat(evaluator.evaluateCondition("{{bad}}", Map.of())).isFalse();
        }
    }

    // ── evaluateString ──

    @Nested
    @DisplayName("evaluateString - 字符串求值")
    class EvaluateStringTests {

        @Test
        @DisplayName("空表达式返回空串")
        void emptyExpression_returnsEmpty() {
            assertThat(evaluator.evaluateString(null, null)).isEmpty();
            assertThat(evaluator.evaluateString("", null)).isEmpty();
        }

        @Test
        @DisplayName("字面量字符串")
        void literalString() {
            assertThat(evaluator.evaluateString("'hello'", Map.of())).isEqualTo("hello");
        }

        @Test
        @DisplayName("变量拼接")
        void variableConcatenation() {
            Map<String, Object> ctx = Map.of("methodName", "save", "className", "OrderSvc");
            assertThat(evaluator.evaluateString(
                    "#methodName + '-' + #className", ctx)).isEqualTo("save-OrderSvc");
        }
    }

    // ── POJO 根对象 ──

    @Nested
    @DisplayName("POJO 根对象 (SimpleEvaluationContext)")
    class PojoRootTests {

        @Test
        @DisplayName("POJO 字段可通过属性名访问")
        void pojoField_accessibleByName() {
            TestBean bean = new TestBean("TFI", 42);
            assertThat(evaluator.evaluateExpression("name", bean, String.class)).isEqualTo("TFI");
            assertThat(evaluator.evaluateExpression("value", bean, Integer.class)).isEqualTo(42);
        }

        @Test
        @DisplayName("POJO 上下文中方法调用可用")
        void pojoMethod_callable() {
            TestBean bean = new TestBean("hello", 5);
            assertThat(evaluator.evaluateExpression("name.length()", bean, Integer.class)).isEqualTo(5);
        }

        @Test
        @DisplayName("POJO 上下文中不存在的属性返回 null")
        void pojoMissingProperty_returnsNull() {
            TestBean bean = new TestBean("test", 1);
            assertThat(evaluator.evaluateExpression("nonExistent", bean, String.class)).isNull();
        }
    }

    /** 用于 POJO 根对象测试的简单 Bean. */
    public static class TestBean {
        private final String name;
        private final int value;

        public TestBean(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public int getValue() { return value; }
    }

    // ── 安全校验（L1-L3）──

    @Nested
    @DisplayName("安全校验 - L1/L2/L3 防御层")
    class SecurityValidationTests {

        @Test
        @DisplayName("L1: 超长表达式被拒绝")
        void tooLongExpression_rejected() {
            String longExpr = "'a'".repeat(400);
            assertThat(evaluator.evaluateExpression(longExpr, Map.of(), String.class)).isNull();
        }

        @Test
        @DisplayName("L2: 黑名单关键词 - class")
        void blockedPattern_class() {
            assertThat(evaluator.evaluateExpression("T(java.lang.Runtime).class", Map.of(), Object.class))
                    .isNull();
        }

        @Test
        @DisplayName("L2: 黑名单关键词 - runtime")
        void blockedPattern_runtime() {
            assertThat(evaluator.evaluateCondition("runtime != null", Map.of())).isFalse();
        }

        @Test
        @DisplayName("L2: 黑名单关键词 - exec")
        void blockedPattern_exec() {
            assertThat(evaluator.evaluateCondition("exec('cmd')", Map.of())).isFalse();
        }

        @Test
        @DisplayName("L2: 黑名单关键词 - reflect")
        void blockedPattern_reflect() {
            assertThat(evaluator.evaluateCondition("reflect.method()", Map.of())).isFalse();
        }

        @Test
        @DisplayName("L2: 黑名单关键词 - unsafe")
        void blockedPattern_unsafe() {
            assertThat(evaluator.evaluateCondition("unsafe.access()", Map.of())).isFalse();
        }

        @Test
        @DisplayName("L3: 嵌套过深被拒绝")
        void deepNesting_rejected() {
            String deepExpr = "(".repeat(11) + "1" + ")".repeat(11);
            assertThat(evaluator.evaluateExpression(deepExpr, Map.of(), Integer.class)).isNull();
        }

        @Test
        @DisplayName("L3: 10 层嵌套正常通过")
        void maxNesting_passes() {
            String expr = "(".repeat(10) + "1" + ")".repeat(10);
            assertThat(evaluator.evaluateExpression(expr, Map.of(), Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("L4: Map 上下文禁止类型引用")
        void mapContext_typeReferenceBlocked() {
            // "class" 关键词会先被 L2 黑名单拦截
            assertThat(evaluator.evaluateExpression(
                    "T(String).valueOf(1)", Map.of(), String.class)).isNull();
        }

        @Test
        @DisplayName("L4: Map 上下文允许安全的方法调用（如 String.length()）")
        void mapContext_safeMethodAllowed() {
            Map<String, Object> ctx = Map.of("val", "hello");
            // 基础方法调用（如 String.length()）不涉及安全风险，应正常工作
            assertThat(evaluator.evaluateExpression("#val.length()", ctx, Integer.class)).isEqualTo(5);
        }

        @Test
        @DisplayName("L4: Map 上下文禁止构造器调用（TypeLocator 已禁用）")
        void mapContext_constructorBlockedByTypeLocator() {
            // SpEL 的 new ClassName() 依赖 TypeLocator 解析类名，
            // 禁用 TypeLocator 后构造器调用应失败
            Map<String, Object> ctx = Map.of();
            assertThat(evaluator.evaluateExpression("new String('test')", ctx, String.class)).isNull();
        }

        @Test
        @DisplayName("L2 + L4: getClass 反射链被黑名单拦截")
        void mapContext_getClassBlockedByBlacklist() {
            Map<String, Object> ctx = Map.of("val", "hello");
            // "getClass" 在默认黑名单中，L2 层直接拦截
            assertThat(evaluator.evaluateExpression("#val.getClass()", ctx, Object.class)).isNull();
        }
    }

    // ── 缓存管理 ──

    @Nested
    @DisplayName("缓存管理")
    class CacheTests {

        @Test
        @DisplayName("缓存统计正确反映状态")
        void cacheStats_reflectsState() {
            assertThat(evaluator.getCacheStats().getCachedExpressions()).isZero();

            evaluator.evaluateExpression("'test'", Map.of(), String.class);
            assertThat(evaluator.getCacheStats().getCachedExpressions()).isEqualTo(1);

            evaluator.evaluateExpression("'test'", Map.of(), String.class);
            assertThat(evaluator.getCacheStats().getCachedExpressions()).isEqualTo(1);

            evaluator.evaluateExpression("'other'", Map.of(), String.class);
            assertThat(evaluator.getCacheStats().getCachedExpressions()).isEqualTo(2);
        }

        @Test
        @DisplayName("clearCache 清空缓存")
        void clearCache_emptiesCache() {
            evaluator.evaluateExpression("'a'", Map.of(), String.class);
            evaluator.evaluateExpression("'b'", Map.of(), String.class);
            assertThat(evaluator.getCacheStats().getCachedExpressions()).isEqualTo(2);

            evaluator.clearCache();
            assertThat(evaluator.getCacheStats().getCachedExpressions()).isZero();
        }

        @Test
        @DisplayName("blockedPatterns 统计正确")
        void blockedPatternsCount() {
            assertThat(evaluator.getCacheStats().getBlockedPatterns()).isGreaterThan(0);
        }
    }

    // ── 可配置化 ──

    @Nested
    @DisplayName("可配置化（TfiSecurityProperties）")
    class ConfigurableTests {

        @Test
        @DisplayName("自定义最大长度")
        void customMaxLength() {
            TfiSecurityProperties props = new TfiSecurityProperties();
            props.setSpelMaxLength(10);
            SafeSpELEvaluator custom = new SafeSpELEvaluator(props);

            assertThat(custom.evaluateExpression("'short'", Map.of(), String.class)).isEqualTo("short");
            assertThat(custom.evaluateExpression("'this is a long expression'", Map.of(), String.class)).isNull();
        }

        @Test
        @DisplayName("自定义黑名单")
        void customBlockedPatterns() {
            TfiSecurityProperties props = new TfiSecurityProperties();
            props.setSpelBlockedPatterns(Set.of("forbidden"));
            SafeSpELEvaluator custom = new SafeSpELEvaluator(props);

            assertThat(custom.evaluateCondition("forbidden == true", Map.of())).isFalse();
            // "class" 不再被阻断（自定义后覆盖了默认列表）
            assertThat(custom.evaluateExpression("'class'", Map.of(), String.class)).isEqualTo("class");
        }

        @Test
        @DisplayName("自定义缓存大小生效")
        void customCacheSize() {
            TfiSecurityProperties props = new TfiSecurityProperties();
            props.setSpelCacheMaxSize(2);
            SafeSpELEvaluator custom = new SafeSpELEvaluator(props);

            custom.evaluateExpression("'a'", Map.of(), String.class);
            custom.evaluateExpression("'b'", Map.of(), String.class);
            custom.evaluateExpression("'c'", Map.of(), String.class);
            // LRU 缓存最多保留 2 个
            assertThat(custom.getCacheStats().getCachedExpressions()).isLessThanOrEqualTo(2);
        }
    }
}
