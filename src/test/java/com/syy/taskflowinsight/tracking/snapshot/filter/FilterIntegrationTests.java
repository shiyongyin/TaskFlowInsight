package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一过滤引擎集成测试
 *
 * 测试覆盖（15用例）：
 * - 三引擎组合决策（类级/路径级/默认忽略）
 * - 优先级规则验证（7级规则链）
 * - 与P0/P1特性共存验证
 * - 决策原因可观测性
 * - 边界条件与回归测试
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class FilterIntegrationTests {

    // ========== 测试模型类 ==========

    static class OrderClass {
        private String orderId;
        private String password;
        private String internalToken;
        private static final Logger logger = LoggerFactory.getLogger(OrderClass.class);
        private transient String tempData;
    }

    @IgnoreDeclaredProperties({"field1", "field2"})
    static class ClassWithAnnotation {
        private String field1;
        private String field2;
        private String field3;
    }

    // ========== 三引擎组合测试 ==========

    @Test
    void testThreeEnginesIntegration_AllEnginesTriggered() throws NoSuchFieldException {
        Field loggerField = OrderClass.class.getDeclaredField("logger");
        String path = "order.internal.logger";

        // Scenario: All three engines should exclude
        // - PathLevel: *.internal.*
        // - ClassLevel: com.syy.taskflowinsight.** (matches test package)
        // - DefaultExclusion: static logger

        Set<String> pathExcludes = Set.of("*.internal.*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            loggerField,
            path,
            null,  // No include patterns
            pathExcludes,
            null,  // No regex excludes
            packageExcludes,
            true   // Default exclusions enabled
        );

        assertTrue(decision.shouldExclude(), "Field should be excluded by path blacklist (highest priority)");
        assertEquals(FilterReason.EXCLUDE_PATTERNS, decision.getReason(),
            "Path blacklist should have highest priority among exclusions");
    }

    @Test
    void testThreeEnginesIntegration_IncludeOverridesAll() throws NoSuchFieldException {
        Field loggerField = OrderClass.class.getDeclaredField("logger");
        String path = "order.internal.logger";

        // Scenario: Include whitelist overrides all exclusion engines
        Set<String> includes = Set.of("order.**");
        Set<String> pathExcludes = Set.of("*.internal.*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            loggerField,
            path,
            includes,
            pathExcludes,
            null,
            packageExcludes,
            true
        );

        assertTrue(decision.shouldInclude(), "Include whitelist should override all exclusions");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== 优先级规则验证 ==========

    @Test
    void testPriorityChain_PathOverridesClass() throws NoSuchFieldException {
        Field field = OrderClass.class.getDeclaredField("orderId");
        String path = "order.internal.orderId";

        // Path blacklist (priority 3) should override class blacklist (priority 4/5)
        Set<String> pathExcludes = Set.of("*.internal.*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            path,
            null,
            pathExcludes,
            null,
            packageExcludes,
            false  // Default exclusions disabled
        );

        assertTrue(decision.shouldExclude());
        assertEquals(FilterReason.EXCLUDE_PATTERNS, decision.getReason(),
            "Path blacklist should have higher priority than class blacklist");
    }

    @Test
    void testPriorityChain_ClassOverridesDefault() throws NoSuchFieldException {
        Field loggerField = OrderClass.class.getDeclaredField("logger");
        String path = "order.logger";

        // Class blacklist (priority 4/5) should override default exclusions (priority 6)
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            loggerField,
            path,
            null,
            null,
            null,
            packageExcludes,
            true  // Default exclusions enabled
        );

        assertTrue(decision.shouldExclude());
        assertEquals(FilterReason.EXCLUDE_PACKAGES, decision.getReason(),
            "Class blacklist should have higher priority than default exclusions");
    }

    @Test
    void testPriorityChain_DefaultExclusionsWhenNoOtherRules() throws NoSuchFieldException {
        Field transientField = OrderClass.class.getDeclaredField("tempData");
        String path = "order.tempData";

        // No path/class blacklists, only default exclusions
        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            transientField,
            path,
            null,
            null,
            null,
            Collections.emptyList(),
            true
        );

        assertTrue(decision.shouldExclude());
        assertEquals(FilterReason.DEFAULT_EXCLUSIONS, decision.getReason());
    }

    // ========== Glob与Regex组合测试 ==========

    @Test
    void testGlobAndRegexCombination() throws NoSuchFieldException {
        Field field = OrderClass.class.getDeclaredField("password");

        // Test Glob exclusion
        FilterDecision globDecision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "user.password",
            null,
            Set.of("*.password"),  // Glob pattern
            null,
            Collections.emptyList(),
            false
        );

        assertTrue(globDecision.shouldExclude());
        assertEquals(FilterReason.EXCLUDE_PATTERNS, globDecision.getReason());

        // Test Regex exclusion
        FilterDecision regexDecision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "debug_123",
            null,
            null,
            Set.of("^debug_\\d+$"),  // Regex pattern
            Collections.emptyList(),
            false
        );

        assertTrue(regexDecision.shouldExclude());
        assertEquals(FilterReason.REGEX_EXCLUDES, regexDecision.getReason());
    }

    // ========== 类级注解测试 ==========

    @Test
    void testClassAnnotation_IntegrationWithEngine() throws NoSuchFieldException {
        Field field1 = ClassWithAnnotation.class.getDeclaredField("field1");
        Field field3 = ClassWithAnnotation.class.getDeclaredField("field3");

        // field1 should be excluded by class annotation
        FilterDecision decision1 = UnifiedFilterEngine.shouldIgnore(
            ClassWithAnnotation.class,
            field1,
            "obj.field1",
            null,
            null,
            null,
            Collections.emptyList(),
            false
        );

        assertTrue(decision1.shouldExclude(), "field1 should be excluded by @IgnoreDeclaredProperties");

        // field3 should not be excluded (not in annotation list)
        FilterDecision decision3 = UnifiedFilterEngine.shouldIgnore(
            ClassWithAnnotation.class,
            field3,
            "obj.field3",
            null,
            null,
            null,
            Collections.emptyList(),
            false
        );

        assertTrue(decision3.shouldInclude(), "field3 should not be excluded");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision3.getReason());
    }

    // ========== 与P0/P1共存验证 ==========

    @Test
    void testBackwardCompatibility_NoConfiguredRules() throws NoSuchFieldException {
        Field normalField = OrderClass.class.getDeclaredField("orderId");

        // No rules configured, should default to retain
        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            normalField,
            "order.orderId",
            null,
            null,
            null,
            Collections.emptyList(),
            false  // Default exclusions disabled
        );

        assertTrue(decision.shouldInclude(), "Normal field should be retained when no rules configured");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    @Test
    void testBackwardCompatibility_DefaultExclusionsDisabled() throws NoSuchFieldException {
        Field loggerField = OrderClass.class.getDeclaredField("logger");

        // Default exclusions disabled, logger should be retained
        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            loggerField,
            "order.logger",
            null,
            null,
            null,
            Collections.emptyList(),
            false  // Disabled
        );

        assertTrue(decision.shouldInclude(), "Logger should be retained when default exclusions disabled");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    // ========== 决策原因可观测性测试 ==========

    @Test
    void testDecisionReason_AlwaysPresent() throws NoSuchFieldException {
        Field field = OrderClass.class.getDeclaredField("orderId");

        // Test all decision types have reasons
        FilterDecision includeDecision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "order.orderId",
            Set.of("order.*"),
            null,
            null,
            Collections.emptyList(),
            false
        );
        assertNotNull(includeDecision.getReason(), "Include decision should have reason");

        FilterDecision excludeDecision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "order.internal.orderId",
            null,
            Set.of("*.internal.*"),
            null,
            Collections.emptyList(),
            false
        );
        assertNotNull(excludeDecision.getReason(), "Exclude decision should have reason");

        FilterDecision defaultDecision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "order.orderId",
            null,
            null,
            null,
            Collections.emptyList(),
            false
        );
        assertNotNull(defaultDecision.getReason(), "Default decision should have reason");
    }

    // ========== 边界条件测试 ==========

    @Test
    void testEmptyPatterns_DefaultsToRetain() throws NoSuchFieldException {
        Field field = OrderClass.class.getDeclaredField("orderId");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "order.orderId",
            Collections.emptySet(),    // Empty include
            Collections.emptySet(),    // Empty exclude
            Collections.emptySet(),    // Empty regex
            Collections.emptyList(),   // Empty package
            false
        );

        assertTrue(decision.shouldInclude(), "Should default to retain with empty patterns");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    @Test
    void testNullPatterns_DefaultsToRetain() throws NoSuchFieldException {
        Field field = OrderClass.class.getDeclaredField("orderId");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            field,
            "order.orderId",
            null,  // Null patterns
            null,
            null,
            null,
            false
        );

        assertTrue(decision.shouldInclude(), "Should default to retain with null patterns");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    // ========== 复杂场景回归测试 ==========

    @Test
    void testComplexScenario_MultipleRulesInteraction() throws NoSuchFieldException {
        Field passwordField = OrderClass.class.getDeclaredField("password");
        String path = "user.internal.password";

        // Multiple blacklists, but include overrides all
        Set<String> includes = Set.of("user.internal.**");
        Set<String> globExcludes = Set.of("*.internal.*", "*.password");
        Set<String> regexExcludes = Set.of("^.*password$");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            passwordField,
            path,
            includes,
            globExcludes,
            regexExcludes,
            packageExcludes,
            true
        );

        assertTrue(decision.shouldInclude(), "Include should win over all exclusions");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testComplexScenario_CascadingExclusions() throws NoSuchFieldException {
        Field loggerField = OrderClass.class.getDeclaredField("logger");
        String path = "order.logger";

        // Test priority: path > class > default
        // Remove path blacklist to test class blacklist
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            OrderClass.class,
            loggerField,
            path,
            null,
            null,  // No path blacklist
            null,
            packageExcludes,
            true
        );

        assertTrue(decision.shouldExclude());
        assertEquals(FilterReason.EXCLUDE_PACKAGES, decision.getReason(),
            "Class blacklist should trigger when path blacklist absent");
    }
}
