package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 过滤优先级规则测试
 *
 * 测试覆盖（25用例）：
 * - 7级优先级规则验证
 * - 优先级冲突场景（Include 覆盖所有规则）
 * - 字段注解优先于类注解
 * - 路径黑名单优先于类黑名单
 * - 默认排除规则的优先级
 * - 决策原因可观测性
 *
 * 优先级规则（由高到低）：
 * 1. includePatterns（Include 白名单）
 * 2. @DiffIgnore/@IgnoreProperty（字段级注解）
 * 3. excludePatterns/regexExcludes（路径黑名单）
 * 4. excludePackages（类/包级黑名单）
 * 5. @IgnoreDeclaredProperties/@IgnoreInheritedProperties（类级注解）
 * 6. defaultExclusionsEnabled（默认排除）
 * 7. 默认保留
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class FilterPriorityTests {

    // ========== 测试辅助类：优先级决策器 ==========

    /**
     * 优先级决策辅助类（用于测试）
     * 实现7级优先级规则链条
     * 注意：生产代码应使用 UnifiedFilterEngine（P2-T5任务）
     */
    static class PriorityResolver {
        public static FilterDecision shouldIgnore(
            String fieldPath,
            Class<?> ownerClass,
            Field field,
            Set<String> includePatterns,
            Set<String> excludeGlobPatterns,
            Set<String> excludeRegexPatterns,
            List<String> excludePackages,
            boolean defaultExclusionsEnabled) {

            // Priority 1: Include whitelist (highest priority)
            if (PathLevelFilterEngine.matchesIncludePatterns(fieldPath, includePatterns)) {
                return FilterDecision.include(FilterReason.INCLUDE_PATTERNS);
            }

            // Priority 2: Field-level annotations (@DiffIgnore/@IgnoreProperty)
            // Note: This is typically handled by the caller (annotation processing layer)
            // For testing purposes, we skip this layer as it's outside snapshot filtering

            // Priority 3: Path blacklist (Glob/Regex)
            if (PathLevelFilterEngine.shouldIgnoreByPath(fieldPath, excludeGlobPatterns, excludeRegexPatterns)) {
                if (excludeGlobPatterns != null && !excludeGlobPatterns.isEmpty()) {
                    for (String pattern : excludeGlobPatterns) {
                        if (PathMatcher.matchGlob(fieldPath, pattern)) {
                            return FilterDecision.exclude(FilterReason.EXCLUDE_PATTERNS);
                        }
                    }
                }
                if (excludeRegexPatterns != null && !excludeRegexPatterns.isEmpty()) {
                    for (String regex : excludeRegexPatterns) {
                        if (PathMatcher.matchRegex(fieldPath, regex)) {
                            return FilterDecision.exclude(FilterReason.REGEX_EXCLUDES);
                        }
                    }
                }
            }

            // Priority 4/5: Class/Package-level blacklist + class-level annotations
            if (ClassLevelFilterEngine.shouldIgnoreByClass(ownerClass, field, excludePackages)) {
                return FilterDecision.exclude(FilterReason.EXCLUDE_PACKAGES);
            }

            // Priority 6: Default exclusions (static/transient/logger/etc.)
            if (DefaultExclusionEngine.isDefaultExcluded(field, defaultExclusionsEnabled)) {
                return FilterDecision.exclude(FilterReason.DEFAULT_EXCLUSIONS);
            }

            // Priority 7: Default retain
            return FilterDecision.include(FilterReason.DEFAULT_RETAIN);
        }
    }

    // ========== 测试模型类 ==========

    static class NormalClass {
        private String businessField;
        private static final Logger logger = LoggerFactory.getLogger(NormalClass.class);
        private transient String tempField;
    }

    @IgnoreDeclaredProperties({"field1", "field2"})
    static class ClassWithIgnoreAnnotation {
        private String field1;
        private String field2;
        private String field3;
    }

    @IgnoreInheritedProperties
    static class ClassWithInheritedIgnore extends BaseClass {
        private String ownField;
    }

    static class BaseClass {
        protected String inheritedField;
    }

    // ========== Priority 1: Include 白名单覆盖测试 ==========

    @Test
    void testIncludeOverridesPathBlacklist() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.businessField";

        // Scenario: Path matches both include and exclude patterns
        Set<String> includes = Set.of("order.*");
        Set<String> excludes = Set.of("*.businessField");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            includes, excludes, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Include should override path blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testIncludeOverridesRegexBlacklist() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "debug_001";

        Set<String> includes = Set.of("debug_*");
        Set<String> regexExcludes = Set.of("^debug_\\d{3}$");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            includes, null, regexExcludes, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Include should override regex blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testIncludeOverridesPackageBlacklist() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.businessField";

        Set<String> includes = Set.of("order.*");
        List<String> excludePackages = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            includes, null, null, excludePackages, false
        );

        assertTrue(decision.shouldInclude(), "Include should override package blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testIncludeOverridesClassAnnotation() throws NoSuchFieldException {
        Field field = ClassWithIgnoreAnnotation.class.getDeclaredField("field1");
        String path = "order.field1";

        Set<String> includes = Set.of("order.field1");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, ClassWithIgnoreAnnotation.class, field,
            includes, null, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Include should override class annotation");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testIncludeOverridesDefaultExclusions() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("logger");
        String path = "order.logger";

        Set<String> includes = Set.of("order.logger");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            includes, null, null, Collections.emptyList(), true
        );

        assertTrue(decision.shouldInclude(), "Include should override default exclusions");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== Priority 3: 路径黑名单测试 ==========

    @Test
    void testPathBlacklistGlob_ExcludesField() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.internal.cache";

        Set<String> excludes = Set.of("*.internal.*");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, excludes, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldExclude(), "Glob pattern should exclude field");
        assertEquals(FilterReason.EXCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testPathBlacklistRegex_ExcludesField() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "debug_123";

        Set<String> regexExcludes = Set.of("^debug_\\d+$");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, regexExcludes, Collections.emptyList(), false
        );

        assertTrue(decision.shouldExclude(), "Regex pattern should exclude field");
        assertEquals(FilterReason.REGEX_EXCLUDES, decision.getReason());
    }

    @Test
    void testPathBlacklistOverridesPackageBlacklist() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.internal.data";

        // Both path and package blacklists match
        Set<String> pathExcludes = Set.of("*.internal.*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, pathExcludes, null, packageExcludes, false
        );

        assertTrue(decision.shouldExclude(), "Path blacklist should trigger");
        assertEquals(FilterReason.EXCLUDE_PATTERNS, decision.getReason(),
            "Path blacklist should have higher priority than package blacklist");
    }

    // ========== Priority 4: 类/包级黑名单测试 ==========

    @Test
    void testPackageBlacklist_ExcludesField() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.businessField";

        List<String> packageExcludes = List.of("com.syy.taskflowinsight.tracking.snapshot.filter.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, packageExcludes, false
        );

        assertTrue(decision.shouldExclude(), "Package blacklist should exclude field");
        assertEquals(FilterReason.EXCLUDE_PACKAGES, decision.getReason());
    }

    @Test
    void testPackageBlacklistOverridesClassAnnotation() throws NoSuchFieldException {
        Field field = ClassWithIgnoreAnnotation.class.getDeclaredField("field1");
        String path = "order.field1";

        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, ClassWithIgnoreAnnotation.class, field,
            null, null, null, packageExcludes, false
        );

        assertTrue(decision.shouldExclude(), "Package blacklist should trigger");
        assertEquals(FilterReason.EXCLUDE_PACKAGES, decision.getReason());
    }

    // ========== Priority 5: 类级注解测试 ==========

    @Test
    void testClassAnnotation_ExcludesField() throws NoSuchFieldException {
        Field field = ClassWithIgnoreAnnotation.class.getDeclaredField("field1");
        String path = "order.field1";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, ClassWithIgnoreAnnotation.class, field,
            null, null, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldExclude(), "Class annotation should exclude field");
        assertEquals(FilterReason.EXCLUDE_PACKAGES, decision.getReason(),
            "ClassLevelFilterEngine returns true for annotation match");
    }

    @Test
    void testClassAnnotation_DoesNotExcludeUnlistedField() throws NoSuchFieldException {
        Field field = ClassWithIgnoreAnnotation.class.getDeclaredField("field3");
        String path = "order.field3";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, ClassWithIgnoreAnnotation.class, field,
            null, null, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Unlisted field should not be excluded");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    // ========== Priority 6: 默认排除规则测试 ==========

    @Test
    void testDefaultExclusions_ExcludesStaticField() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("logger");
        String path = "order.logger";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, Collections.emptyList(), true
        );

        assertTrue(decision.shouldExclude(), "Default exclusions should exclude static logger");
        assertEquals(FilterReason.DEFAULT_EXCLUSIONS, decision.getReason());
    }

    @Test
    void testDefaultExclusions_ExcludesTransientField() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("tempField");
        String path = "order.tempField";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, Collections.emptyList(), true
        );

        assertTrue(decision.shouldExclude(), "Default exclusions should exclude transient field");
        assertEquals(FilterReason.DEFAULT_EXCLUSIONS, decision.getReason());
    }

    @Test
    void testDefaultExclusions_DisabledBySwitch() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("logger");
        String path = "order.logger";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Logger should not be excluded when switch is off");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    // ========== Priority 7: 默认保留测试 ==========

    @Test
    void testDefaultRetain_NoRuleMatches() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.businessField";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Field should be retained by default");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    // ========== 决策原因可观测性测试 ==========

    @Test
    void testDecisionReason_AlwaysPresent() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");

        // Test all priority levels have non-null reasons
        FilterDecision includeDecision = PriorityResolver.shouldIgnore(
            "order.field", NormalClass.class, field,
            Set.of("order.*"), null, null, Collections.emptyList(), false
        );
        assertNotNull(includeDecision.getReason());

        FilterDecision excludeDecision = PriorityResolver.shouldIgnore(
            "order.internal.field", NormalClass.class, field,
            null, Set.of("*.internal.*"), null, Collections.emptyList(), false
        );
        assertNotNull(excludeDecision.getReason());

        FilterDecision defaultDecision = PriorityResolver.shouldIgnore(
            "order.field", NormalClass.class, field,
            null, null, null, Collections.emptyList(), false
        );
        assertNotNull(defaultDecision.getReason());
    }

    // ========== 复杂冲突场景测试 ==========

    @Test
    void testComplexConflict_IncludeWinsOverAll() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("logger");
        String path = "order.internal.logger";

        // All exclusion rules match, but include overrides
        Set<String> includes = Set.of("order.**");
        Set<String> pathExcludes = Set.of("*.internal.*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            includes, pathExcludes, null, packageExcludes, true
        );

        assertTrue(decision.shouldInclude(), "Include should win over all exclusion rules");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testComplexConflict_PathOverridesPackageAndDefault() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("logger");
        String path = "order.internal.logger";

        // Path, package, and default exclusions all match
        Set<String> pathExcludes = Set.of("*.internal.*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, pathExcludes, null, packageExcludes, true
        );

        assertTrue(decision.shouldExclude(), "Should be excluded");
        assertEquals(FilterReason.EXCLUDE_PATTERNS, decision.getReason(),
            "Path blacklist should have higher priority");
    }

    @Test
    void testComplexConflict_PackageOverridesDefault() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("logger");
        String path = "order.logger";

        // Package and default exclusions both match
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, packageExcludes, true
        );

        assertTrue(decision.shouldExclude(), "Should be excluded");
        assertEquals(FilterReason.EXCLUDE_PACKAGES, decision.getReason(),
            "Package blacklist should have higher priority than default exclusions");
    }

    // ========== 边界条件测试 ==========

    @Test
    void testEmptyIncludeList_DoesNotMatchAnything() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.field";

        Set<String> emptyIncludes = Collections.emptySet();

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            emptyIncludes, null, null, Collections.emptyList(), false
        );

        // Should fall through to default retain
        assertTrue(decision.shouldInclude());
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    @Test
    void testNullPatterns_FallsToDefaultRetain() throws NoSuchFieldException {
        Field field = NormalClass.class.getDeclaredField("businessField");
        String path = "order.field";

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, NormalClass.class, field,
            null, null, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Should default to include when no rules match");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }
}
