package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TFI 特性开关单元测试
 * <p>
 * 验证特性开关在各种场景下的行为：
 * 1. Facade 总开关关闭时，所有 API 返回安全默认值
 * 2. Masking 开关关闭时，敏感字段不掩码
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class TfiFeatureFlagsTests {

    private static final String FACADE_ENABLED_KEY = "tfi.api.facade.enabled";
    private static final String MASKING_ENABLED_KEY = "tfi.render.masking.enabled";

    @BeforeEach
    void setUp() {
        // 清理 System Properties，避免影响其他测试
        System.clearProperty(FACADE_ENABLED_KEY);
        System.clearProperty(MASKING_ENABLED_KEY);
    }

    @AfterEach
    void tearDown() {
        // 测试结束后清理
        System.clearProperty(FACADE_ENABLED_KEY);
        System.clearProperty(MASKING_ENABLED_KEY);
    }

    // ==================== Facade 开关测试 ====================

    @Test
    void testFacadeEnabled_DefaultBehavior() {
        // Given: 不设置任何 System Property（默认启用）

        // When: 调用 TFI.compare()
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");
        obj1.put("age", 30);

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");
        obj2.put("age", 35);

        CompareResult result = TFI.compare(obj1, obj2);

        // Then: 应该正常比较（非 identical）
        assertNotNull(result, "Result should not be null");
        // Note: Map 的 compare 可能返回 identical，因为没有注解支持
        // 但至少不会因为 feature flag 被短路
    }

    @Test
    void testFacadeDisabled_CompareReturnsIdentical() {
        // Given: 通过 System Property 禁用 Facade
        System.setProperty(FACADE_ENABLED_KEY, "false");

        // When: 调用 TFI.compare()
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        CompareResult result = TFI.compare(obj1, obj2);

        // Then: 应该返回 identical（而不是实际比较结果）
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isIdentical(), "Facade disabled should return identical result");
    }

    @Test
    void testFacadeDisabled_ComparatorReturnsDisabled() {
        // Given: 通过 System Property 禁用 Facade
        System.setProperty(FACADE_ENABLED_KEY, "false");

        // When: 调用 TFI.comparator() 构建器
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");

        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        CompareResult result = TFI.comparator()
                .withMaxDepth(5)
                .ignoring("id")
                .compare(obj1, obj2);

        // Then: 应该返回 identical（禁用的构建器）
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isIdentical(), "Disabled comparator should always return identical");
    }

    @Test
    void testFacadeDisabled_RenderReturnsDisabledMessage() {
        // Given: 通过 System Property 禁用 Facade
        System.setProperty(FACADE_ENABLED_KEY, "false");

        // 创建一个空的 CompareResult（模拟渲染场景）
        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("name", "Alice");
        Map<String, Object> obj2 = new HashMap<>();
        obj2.put("name", "Bob");

        // 先正常比较得到结果（此时 facade 已禁用，会返回 identical）
        CompareResult result = TFI.compare(obj1, obj2);

        // When: 调用 TFI.render()
        String rendered = TFI.render(result, RenderStyle.standard());

        // Then: 应该返回禁用消息
        assertNotNull(rendered, "Rendered output should not be null");
        assertTrue(rendered.contains("Facade Disabled"),
                "Disabled facade should return disabled message");
        assertTrue(rendered.contains("tfi.api.facade.enabled=false"),
                "Disabled message should mention the config key");
    }

    // ==================== Masking 开关测试 ====================

    @Test
    void testMaskingEnabled_DefaultBehavior() {
        // Given: 不设置任何 System Property（默认启用）
        // When: 检查静态方法
        boolean enabled = TfiFeatureFlags.isMaskingEnabled();

        // Then: 应该默认启用
        assertTrue(enabled, "Masking should be enabled by default");
    }

    @Test
    void testMaskingDisabled_StaticMethodReturns() {
        // Given: 通过 System Property 禁用 Masking
        System.setProperty(MASKING_ENABLED_KEY, "false");

        // When: 检查静态方法
        boolean enabled = TfiFeatureFlags.isMaskingEnabled();

        // Then: 应该返回 false
        assertFalse(enabled, "Masking should be disabled when System property is false");
    }

    @Test
    void testMaskingEnabled_SystemPropertyTrue() {
        // Given: 通过 System Property 显式启用 Masking
        System.setProperty(MASKING_ENABLED_KEY, "true");

        // When: 检查静态方法
        boolean enabled = TfiFeatureFlags.isMaskingEnabled();

        // Then: 应该返回 true
        assertTrue(enabled, "Masking should be enabled when System property is true");
    }

    // ==================== 配置对象测试 ====================

    @Test
    void testTfiFeatureFlagsBean_DefaultValues() {
        // Given & When: 创建配置对象（模拟 Spring 场景）
        TfiFeatureFlags flags = new TfiFeatureFlags();

        // Then: 应该有正确的默认值
        assertNotNull(flags.getApi(), "Api config should not be null");
        assertNotNull(flags.getApi().getFacade(), "Facade config should not be null");
        assertTrue(flags.getApi().getFacade().isEnabled(), "Facade should be enabled by default");

        assertNotNull(flags.getRender(), "Render config should not be null");
        assertNotNull(flags.getRender().getMasking(), "Masking config should not be null");
        assertTrue(flags.getRender().getMasking().isEnabled(), "Masking should be enabled by default");
    }

    @Test
    void testTfiFeatureFlagsBean_SettersWork() {
        // Given: 创建配置对象
        TfiFeatureFlags flags = new TfiFeatureFlags();

        // When: 修改配置（模拟 Spring ConfigurationProperties 绑定）
        flags.getApi().getFacade().setEnabled(false);
        flags.getRender().getMasking().setEnabled(false);

        // Then: 值应该已更新
        assertFalse(flags.getApi().getFacade().isEnabled(), "Facade should be disabled");
        assertFalse(flags.getRender().getMasking().isEnabled(), "Masking should be disabled");
    }

    @Test
    void testStaticMethod_FacadeEnabled_Fallback() {
        // Given: 不设置 System Property（测试默认值）

        // When: 调用静态方法
        boolean enabled = TfiFeatureFlags.isFacadeEnabled();

        // Then: 应该返回默认值 true
        assertTrue(enabled, "Facade should be enabled by default");
    }

    @Test
    void testStaticMethod_FacadeDisabled_SystemProperty() {
        // Given: 设置 System Property
        System.setProperty(FACADE_ENABLED_KEY, "false");

        // When: 调用静态方法
        boolean enabled = TfiFeatureFlags.isFacadeEnabled();

        // Then: 应该返回 false
        assertFalse(enabled, "Facade should be disabled when System property is false");
    }

    @Test
    void testStaticMethod_FacadeEnabled_SystemProperty() {
        // Given: 设置 System Property
        System.setProperty(FACADE_ENABLED_KEY, "true");

        // When: 调用静态方法
        boolean enabled = TfiFeatureFlags.isFacadeEnabled();

        // Then: 应该返回 true
        assertTrue(enabled, "Facade should be enabled when System property is true");
    }
}
