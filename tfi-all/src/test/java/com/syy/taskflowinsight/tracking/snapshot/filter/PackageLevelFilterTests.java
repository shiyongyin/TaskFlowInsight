package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 包级过滤规则测试
 *
 * 测试覆盖：
 * - 精确包名匹配
 * - 通配符 ".**" 匹配
 * - 子包与兄弟包区分
 * - 多个包规则组合
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class PackageLevelFilterTests {

    // ========== 测试用例模型类 ==========

    /**
     * 当前包下的测试类
     */
    static class CurrentPackageClass {
        private String field1;
    }

    // ========== 精确匹配测试 ==========

    @Test
    void testExactPackageMatch_CurrentPackage() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 精确匹配当前包
        String exactPackage = "com.syy.taskflowinsight.tracking.snapshot.filter";
        List<String> excludePackages = Collections.singletonList(exactPackage);

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should match exact package name");
    }

    @Test
    void testExactPackageMatch_DifferentPackage_NoMatch() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 不同的包名（没有通配符）
        String differentPackage = "com.syy.taskflowinsight.tracking.snapshot";
        List<String> excludePackages = Collections.singletonList(differentPackage);

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should NOT match different package (no wildcard)");
    }

    // ========== 通配符匹配测试 ==========

    @Test
    void testWildcardMatch_SelfAndSubpackages() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 当前包 + ".**" 通配符
        String wildcardPackage = "com.syy.taskflowinsight.tracking.snapshot.filter.**";
        List<String> excludePackages = Collections.singletonList(wildcardPackage);

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should match package itself with .** wildcard");
    }

    @Test
    void testWildcardMatch_ParentPackage() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 父包 + ".**" 通配符
        String parentWildcard = "com.syy.taskflowinsight.**";
        List<String> excludePackages = Collections.singletonList(parentWildcard);

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should match child package when parent has .** wildcard");
    }

    @Test
    void testWildcardMatch_SiblingPackage_NoMatch() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 兄弟包 + ".**" 通配符
        String siblingWildcard = "com.syy.taskflowinsight.tracking.compare.**";
        List<String> excludePackages = Collections.singletonList(siblingWildcard);

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should NOT match sibling package even with .** wildcard");
    }

    // ========== 多规则组合测试 ==========

    @Test
    void testMultiplePackageRules_FirstMatches() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        List<String> excludePackages = Arrays.asList(
            "com.syy.taskflowinsight.tracking.snapshot.filter.**",  // 匹配
            "com.example.other.**"                                   // 不匹配
        );

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should match when first rule matches");
    }

    @Test
    void testMultiplePackageRules_SecondMatches() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        List<String> excludePackages = Arrays.asList(
            "com.example.other.**",                                   // 不匹配
            "com.syy.taskflowinsight.**"                             // 匹配
        );

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should match when second rule matches");
    }

    @Test
    void testMultiplePackageRules_NoneMatches() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        List<String> excludePackages = Arrays.asList(
            "com.example.other.**",
            "org.springframework.**",
            "java.util.**"
        );

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Should NOT match when no rule matches");
    }

    // ========== 边界条件测试 ==========

    @Test
    void testEmptyPattern_NoMatch() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        List<String> excludePackages = Arrays.asList("", "   ");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Empty pattern should not match");
    }

    @Test
    void testRootPackageWildcard_MatchesAll() throws NoSuchFieldException {
        Class<?> clazz = CurrentPackageClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 根包通配符（理论上匹配所有包，但要谨慎使用）
        List<String> excludePackages = Collections.singletonList("com.**");

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Root wildcard should match all subpackages under 'com'");
    }
}
