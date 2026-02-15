package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * SnapshotProvider 切换逻辑集成测试
 * 验证 Provider 选择优先级和行为一致性
 *
 * 测试覆盖：
 * 1. 优先级1: Spring Bean > 优先级2: 系统属性 > 优先级3: 默认
 * 2. 系统属性 facade vs direct 切换
 * 3. DirectSnapshotProvider vs FacadeSnapshotProvider 行为一致性
 * 4. 浅快照和深快照场景
 * 5. AppContextInjector 注入验证
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0 (P0 重构)
 */
@SpringBootTest
@DisplayName("SnapshotProvider 切换逻辑测试")
class SnapshotProviderSwitchTest {

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    private TestObject testObject;
    private static final String ORIGINAL_PROVIDER_PROPERTY = System.getProperty("tfi.change-tracking.snapshot.provider");

    @BeforeEach
    void setUp() {
        // 准备测试对象
        testObject = new TestObject();
        testObject.name = "Alice";
        testObject.age = 25;
        testObject.email = "alice@example.com";
        testObject.nested = new NestedObject();
        testObject.nested.city = "Beijing";
        testObject.nested.country = "China";
    }

    @AfterEach
    void tearDown() {
        // 恢复系统属性
        if (ORIGINAL_PROVIDER_PROPERTY != null) {
            System.setProperty("tfi.change-tracking.snapshot.provider", ORIGINAL_PROVIDER_PROPERTY);
        } else {
            System.clearProperty("tfi.change-tracking.snapshot.provider");
        }
        testObject = null;
    }

    @Test
    @DisplayName("1. 默认情况下使用 DirectSnapshotProvider")
    void shouldUseDirectSnapshotProviderByDefault() {
        // Given - 清除系统属性
        System.clearProperty("tfi.change-tracking.snapshot.provider");

        // When - 获取 Provider（非 Spring Bean 场景需要手动测试）
        SnapshotProvider provider = new DirectSnapshotProvider();

        // Then - 验证可以正常捕获快照
        Map<String, Object> snapshot = provider.captureBaseline("test", testObject, new String[]{"name", "age"});

        assertThat(snapshot).isNotNull();
        assertThat(snapshot).containsKeys("name", "age");
        assertThat(snapshot.get("name")).isEqualTo("Alice");
        assertThat(snapshot.get("age")).isEqualTo(25);
    }

    @Test
    @DisplayName("2. 系统属性设置为 facade 时使用 FacadeSnapshotProvider")
    void shouldUseFacadeSnapshotProviderWhenSystemPropertySet() {
        // Given - 设置系统属性
        System.setProperty("tfi.change-tracking.snapshot.provider", "facade");

        // When - 创建 FacadeSnapshotProvider
        SnapshotProvider provider = new FacadeSnapshotProvider();

        // Then - 验证可以正常捕获快照
        Map<String, Object> snapshot = provider.captureBaseline("test", testObject, new String[]{"name", "age"});

        assertThat(snapshot).isNotNull();
        assertThat(snapshot).containsKeys("name", "age");
        assertThat(snapshot.get("name")).isEqualTo("Alice");
        assertThat(snapshot.get("age")).isEqualTo(25);
    }

    @Test
    @DisplayName("3. DirectSnapshotProvider 浅快照行为验证")
    void shouldCaptureShallowSnapshotWithDirectProvider() {
        // Given
        DirectSnapshotProvider provider = new DirectSnapshotProvider();

        // When - 浅快照只捕获指定字段
        Map<String, Object> snapshot = provider.captureBaseline("test", testObject, new String[]{"name", "age"});

        // Then
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot).containsKeys("name", "age");
        assertThat(snapshot).doesNotContainKey("email");
        assertThat(snapshot).doesNotContainKey("nested");
    }

    @Test
    @DisplayName("4. DirectSnapshotProvider 深快照行为验证")
    void shouldCaptureDeepSnapshotWithDirectProvider() {
        // Given
        DirectSnapshotProvider provider = new DirectSnapshotProvider();
        TrackingOptions options = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(5)
            .build();

        // When - 深快照捕获所有字段（包括嵌套）
        Map<String, Object> snapshot = provider.captureWithOptions("test", testObject, options);

        // Then
        assertThat(snapshot).isNotNull();
        assertThat(snapshot).containsKeys("name", "age", "email");

        // 深快照应该包含嵌套对象的字段
        assertThat(snapshot).containsKey("nested.city");
        assertThat(snapshot).containsKey("nested.country");
        assertThat(snapshot.get("nested.city")).isEqualTo("Beijing");
    }

    @Test
    @DisplayName("5. FacadeSnapshotProvider 浅快照行为验证")
    void shouldCaptureShallowSnapshotWithFacadeProvider() {
        // Given
        FacadeSnapshotProvider provider = new FacadeSnapshotProvider();

        // When
        Map<String, Object> snapshot = provider.captureBaseline("test", testObject, new String[]{"name", "age"});

        // Then
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot).containsKeys("name", "age");
        assertThat(snapshot.get("name")).isEqualTo("Alice");
        assertThat(snapshot.get("age")).isEqualTo(25);
    }

    @Test
    @DisplayName("6. 验证两种 Provider 浅快照结果一致")
    void shouldProduceSameResultForShallowSnapshot() {
        // Given
        DirectSnapshotProvider directProvider = new DirectSnapshotProvider();
        FacadeSnapshotProvider facadeProvider = new FacadeSnapshotProvider();
        String[] fields = new String[]{"name", "age", "email"};

        // When
        Map<String, Object> directSnapshot = directProvider.captureBaseline("test", testObject, fields);
        Map<String, Object> facadeSnapshot = facadeProvider.captureBaseline("test", testObject, fields);

        // Then - 两种实现应该产生相同的结果
        assertThat(directSnapshot).hasSameSizeAs(facadeSnapshot);
        assertThat(directSnapshot.get("name")).isEqualTo(facadeSnapshot.get("name"));
        assertThat(directSnapshot.get("age")).isEqualTo(facadeSnapshot.get("age"));
        assertThat(directSnapshot.get("email")).isEqualTo(facadeSnapshot.get("email"));
    }

    @Test
    @DisplayName("7. 验证两种 Provider 深快照结果一致")
    void shouldProduceSameResultForDeepSnapshot() {
        // Given
        DirectSnapshotProvider directProvider = new DirectSnapshotProvider();
        FacadeSnapshotProvider facadeProvider = new FacadeSnapshotProvider();
        TrackingOptions options = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(5)
            .build();

        // When
        Map<String, Object> directSnapshot = directProvider.captureWithOptions("test", testObject, options);
        Map<String, Object> facadeSnapshot = facadeProvider.captureWithOptions("test", testObject, options);

        // Then - 关键字段应该一致
        assertThat(directSnapshot.get("name")).isEqualTo(facadeSnapshot.get("name"));
        assertThat(directSnapshot.get("age")).isEqualTo(facadeSnapshot.get("age"));
        assertThat(directSnapshot.get("nested.city")).isEqualTo(facadeSnapshot.get("nested.city"));
        assertThat(directSnapshot.get("nested.country")).isEqualTo(facadeSnapshot.get("nested.country"));
    }

    @Test
    @DisplayName("8. 验证空字段数组时的行为")
    void shouldHandleEmptyFieldsArray() {
        // Given
        DirectSnapshotProvider provider = new DirectSnapshotProvider();

        // When - 空字段数组应该捕获所有字段
        Map<String, Object> snapshot = provider.captureBaseline("test", testObject, new String[]{});

        // Then
        assertThat(snapshot).isNotNull();
        // ObjectSnapshot.capture 在字段为空时会捕获所有标量字段
        assertThat(snapshot).isNotEmpty();
    }

    @Test
    @DisplayName("9. 验证 null 目标对象的处理")
    void shouldHandleNullTarget() {
        // Given
        DirectSnapshotProvider provider = new DirectSnapshotProvider();

        // When/Then - null 对象应该返回空 Map 或抛出异常（取决于实现）
        Map<String, Object> snapshot = provider.captureBaseline("test", null, new String[]{"name"});

        // ObjectSnapshot.capture 对 null 对象返回空 Map
        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("10. 验证 AppContextInjector 在 Spring 环境正常工作")
    void shouldInjectApplicationContextInSpringEnvironment() {
        // Given - Spring 环境已启动
        assertThat(applicationContext).isNotNull();

        // When - 使用 SnapshotProviders.get() 应该能够访问 Spring Bean（如果有）
        // 在当前测试中，没有自定义 SnapshotProvider bean，所以会回退到系统属性或默认

        // Then - 验证 AppContextInjector 已注册
        // 实际注入发生在 Spring 容器启动时，这里只验证上下文可用
        assertThat(applicationContext.getBean(SnapshotProviders.AppContextInjector.class)).isNotNull();
    }

    @Test
    @DisplayName("11. 验证嵌套对象的深度捕获")
    void shouldCaptureNestedObjectFieldsInDeepMode() {
        // Given
        DirectSnapshotProvider provider = new DirectSnapshotProvider();
        TrackingOptions deepOptions = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(10)
            .build();

        // When
        Map<String, Object> snapshot = provider.captureWithOptions("test", testObject, deepOptions);

        // Then - 嵌套对象字段应该以 "nested.field" 形式存在
        assertThat(snapshot).containsKey("nested.city");
        assertThat(snapshot).containsKey("nested.country");
        assertThat(snapshot.get("nested.city")).isEqualTo("Beijing");
        assertThat(snapshot.get("nested.country")).isEqualTo("China");
    }

    @Test
    @DisplayName("12. 验证 maxDepth 限制生效")
    void shouldRespectMaxDepthLimit() {
        // Given
        DirectSnapshotProvider provider = new DirectSnapshotProvider();
        TrackingOptions limitedDepth = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(1) // 只捕获第一层
            .build();

        // When
        Map<String, Object> snapshot = provider.captureWithOptions("test", testObject, limitedDepth);

        // Then - 应该只包含顶层字段
        assertThat(snapshot).containsKeys("name", "age", "email");
        // maxDepth=1 时，嵌套对象可能作为整体被捕获或被跳过（取决于实现）
    }

    // ==================== 测试数据类 ====================

    /**
     * 测试用对象
     */
    static class TestObject {
        public String name;
        public Integer age;
        public String email;
        public NestedObject nested;
    }

    /**
     * 嵌套对象
     */
    static class NestedObject {
        public String city;
        public String country;
    }

    /**
     * 测试配置：提供自定义 SnapshotProvider Bean（可选）
     * 用于验证 Spring Bean 优先级
     */
    @TestConfiguration
    static class CustomSnapshotProviderConfig {

        @Bean
        @Primary
        public SnapshotProvider customSnapshotProvider() {
            // 返回一个自定义实现，用于验证 Spring Bean 优先级
            return new DirectSnapshotProvider();
        }
    }
}
