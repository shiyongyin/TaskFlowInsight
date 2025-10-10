package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Include 白名单覆盖测试
 *
 * 测试覆盖（10用例）：
 * - Include 白名单挽回各类黑名单场景
 * - 验证 Include 作为最高优先级规则的有效性
 * - 典型业务场景：显式保留被默认忽略或黑名单过滤的重要字段
 *
 * 业务价值：
 * - 安全字段白名单：password 默认忽略，但审计需求要求追踪变更
 * - Logger 字段白名单：某些场景需要追踪 logger 配置变化
 * - 内部字段白名单：internal.* 路径被黑名单过滤，但某些内部字段需要比对
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class IncludeOverrideTests {

    // ========== 测试模型类 ==========

    static class BusinessClass {
        private String orderId;
        private String password;  // Typically in excludePatterns
        private String internalToken;  // Matches *.internal*
        private static final Logger logger = LoggerFactory.getLogger(BusinessClass.class);
        private transient String cacheData;
    }

    // Reuse PriorityResolver from FilterPriorityTests
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

            if (PathLevelFilterEngine.matchesIncludePatterns(fieldPath, includePatterns)) {
                return FilterDecision.include(FilterReason.INCLUDE_PATTERNS);
            }

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

            if (ClassLevelFilterEngine.shouldIgnoreByClass(ownerClass, field, excludePackages)) {
                return FilterDecision.exclude(FilterReason.EXCLUDE_PACKAGES);
            }

            if (DefaultExclusionEngine.isDefaultExcluded(field, defaultExclusionsEnabled)) {
                return FilterDecision.exclude(FilterReason.DEFAULT_EXCLUSIONS);
            }

            return FilterDecision.include(FilterReason.DEFAULT_RETAIN);
        }
    }

    // ========== Include 覆盖路径黑名单 ==========

    @Test
    void testInclude_OverridesPasswordBlacklist() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("password");
        String path = "user.password";

        // Scenario: Audit requirement needs to track password changes
        Set<String> includes = Set.of("user.password");
        Set<String> excludes = Set.of("*.password");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, excludes, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Include should override password blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testInclude_OverridesInternalPathPattern() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("internalToken");
        String path = "order.internalToken";

        // Scenario: Need to track specific internal field despite *.internal* blacklist
        Set<String> includes = Set.of("order.internalToken");
        Set<String> excludes = Set.of("*.internal*");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, excludes, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Include should override internal path pattern");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testInclude_OverridesRegexBlacklist() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("orderId");
        String path = "temp_123";

        // Scenario: temp_* pattern matches debug/temp paths, but need to include specific one
        Set<String> includes = Set.of("temp_123");
        Set<String> regexExcludes = Set.of("^temp_.*$");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, null, regexExcludes, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Include should override regex blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== Include 覆盖默认排除 ==========

    @Test
    void testInclude_OverridesStaticLoggerExclusion() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("logger");
        String path = "config.logger";

        // Scenario: Track logger configuration changes for diagnostics
        Set<String> includes = Set.of("config.logger");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, null, null, Collections.emptyList(), true
        );

        assertTrue(decision.shouldInclude(), "Include should override static logger exclusion");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    @Test
    void testInclude_OverridesTransientExclusion() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("cacheData");
        String path = "session.cacheData";

        // Scenario: Track cache state for debugging
        Set<String> includes = Set.of("session.cacheData");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, null, null, Collections.emptyList(), true
        );

        assertTrue(decision.shouldInclude(), "Include should override transient exclusion");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== Include 覆盖包级黑名单 ==========

    @Test
    void testInclude_OverridesPackageBlacklist() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("orderId");
        String path = "order.orderId";

        // Scenario: Package is blacklisted, but specific field needs tracking
        Set<String> includes = Set.of("order.orderId");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, null, null, packageExcludes, false
        );

        assertTrue(decision.shouldInclude(), "Include should override package blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== Include 通配符挽回场景 ==========

    @Test
    void testInclude_WildcardOverridesMultipleBlacklists() throws NoSuchFieldException {
        Field passwordField = BusinessClass.class.getDeclaredField("password");
        Field tokenField = BusinessClass.class.getDeclaredField("internalToken");

        // Scenario: Audit mode - include all user fields despite blacklists
        Set<String> includes = Set.of("user.**");
        Set<String> excludes = Set.of("*.password", "*.internal*");

        FilterDecision passwordDecision = PriorityResolver.shouldIgnore(
            "user.password", BusinessClass.class, passwordField,
            includes, excludes, null, Collections.emptyList(), false
        );

        FilterDecision tokenDecision = PriorityResolver.shouldIgnore(
            "user.internalToken", BusinessClass.class, tokenField,
            includes, excludes, null, Collections.emptyList(), false
        );

        assertTrue(passwordDecision.shouldInclude(), "Include wildcard should override password blacklist");
        assertTrue(tokenDecision.shouldInclude(), "Include wildcard should override internal blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, passwordDecision.getReason());
        assertEquals(FilterReason.INCLUDE_PATTERNS, tokenDecision.getReason());
    }

    // ========== Include 精确挽回 vs 模糊黑名单 ==========

    @Test
    void testInclude_ExactMatchOverridesFuzzyBlacklist() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("orderId");
        String path = "order.internal.orderId";

        // Scenario: Broad blacklist (*.internal.*), precise whitelist (exact path)
        Set<String> includes = Set.of("order.internal.orderId");
        Set<String> excludes = Set.of("*.internal.*");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, excludes, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldInclude(), "Exact include should override fuzzy blacklist");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== 复杂组合挽回场景 ==========

    @Test
    void testInclude_OverridesAllExclusionRules() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("logger");
        String path = "internal.security.logger";

        // Scenario: Field matches ALL exclusion rules
        Set<String> includes = Set.of("internal.**");
        Set<String> pathExcludes = Set.of("*.internal.*");
        Set<String> regexExcludes = Set.of("^internal\\..*");
        List<String> packageExcludes = List.of("com.syy.taskflowinsight.**");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, pathExcludes, regexExcludes, packageExcludes, true
        );

        assertTrue(decision.shouldInclude(), "Include should override all exclusion rules");
        assertEquals(FilterReason.INCLUDE_PATTERNS, decision.getReason());
    }

    // ========== 负面测试：Include 不匹配时黑名单生效 ==========

    @Test
    void testInclude_DoesNotMatchThenBlacklistApplies() throws NoSuchFieldException {
        Field field = BusinessClass.class.getDeclaredField("password");
        String path = "user.password";

        // Scenario: Include pattern does not match, blacklist should apply
        Set<String> includes = Set.of("admin.**");  // Different prefix
        Set<String> excludes = Set.of("*.password");

        FilterDecision decision = PriorityResolver.shouldIgnore(
            path, BusinessClass.class, field,
            includes, excludes, null, Collections.emptyList(), false
        );

        assertTrue(decision.shouldExclude(), "Blacklist should apply when include doesn't match");
        assertEquals(FilterReason.EXCLUDE_PATTERNS, decision.getReason());
    }
}
