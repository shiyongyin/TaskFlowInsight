package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for tracking.snapshot package to improve coverage
 */
class TrackingSnapshotComprehensiveTest {

    private SnapshotConfig config;
    private DeepSnapshotStrategy deepStrategy;

    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        deepStrategy = new DeepSnapshotStrategy(config);
    }

    @Test
    @DisplayName("SnapshotConfig默认配置")
    void snapshotConfig_defaultValues() {
        SnapshotConfig defaultConfig = new SnapshotConfig();
        
        assertThat(defaultConfig.isEnableDeep()).isFalse();
        assertThat(defaultConfig.getMaxDepth()).isEqualTo(3);
        assertThat(defaultConfig.getMaxStackDepth()).isEqualTo(1000);
        assertThat(defaultConfig.getTimeBudgetMs()).isEqualTo(50);
        assertThat(defaultConfig.getCollectionSummaryThreshold()).isEqualTo(100);
        assertThat(defaultConfig.isMetricsEnabled()).isTrue();
        
        assertThat(defaultConfig.getIncludePatterns()).isEmpty();
        assertThat(defaultConfig.getExcludePatterns()).containsExactly(
            "*.password", "*.secret", "*.token", "*.credential", "*.key"
        );
    }

    @Test
    @DisplayName("SnapshotConfig模式集合转换")
    void snapshotConfig_patternSets() {
        config.setIncludePatterns(Arrays.asList("user.*", "profile.*"));
        config.setExcludePatterns(Arrays.asList("*.secret", "*.password"));
        
        Set<String> includeSet = config.getIncludePatternSet();
        Set<String> excludeSet = config.getExcludePatternSet();
        
        assertThat(includeSet).containsExactlyInAnyOrder("user.*", "profile.*");
        assertThat(excludeSet).containsExactlyInAnyOrder("*.secret", "*.password");
        
        // 验证返回的是新集合（防止外部修改）
        includeSet.add("test");
        assertThat(config.getIncludePatterns()).hasSize(2);
    }

    @Test
    @DisplayName("SnapshotConfig路径排除逻辑 - null值")
    void snapshotConfig_shouldExclude_nullPath() {
        assertThat(config.shouldExclude(null)).isFalse();
    }

    @Test
    @DisplayName("SnapshotConfig路径排除逻辑 - 默认排除模式")
    void snapshotConfig_shouldExclude_defaultPatterns() {
        // 匹配默认排除模式
        assertThat(config.shouldExclude("user.password")).isTrue();
        assertThat(config.shouldExclude("api.secret")).isTrue();
        assertThat(config.shouldExclude("auth.token")).isTrue();
        assertThat(config.shouldExclude("db.credential")).isTrue();
        assertThat(config.shouldExclude("encryption.key")).isTrue();
        
        // 不匹配排除模式
        assertThat(config.shouldExclude("user.name")).isFalse();
        assertThat(config.shouldExclude("user.email")).isFalse();
    }

    @Test
    @DisplayName("SnapshotConfig路径排除逻辑 - 包含模式")
    void snapshotConfig_shouldExclude_includePatterns() {
        config.setIncludePatterns(Arrays.asList("user.*", "profile.*"));
        
        // 匹配包含模式
        assertThat(config.shouldExclude("user.name")).isFalse();
        assertThat(config.shouldExclude("profile.avatar")).isFalse();
        
        // 不匹配包含模式，应被排除
        assertThat(config.shouldExclude("system.config")).isTrue();
        assertThat(config.shouldExclude("admin.settings")).isTrue();
    }

    @Test
    @DisplayName("SnapshotConfig路径排除逻辑 - 复合规则")
    void snapshotConfig_shouldExclude_complexRules() {
        config.setIncludePatterns(Arrays.asList("user.*"));
        config.setExcludePatterns(Arrays.asList("*.password", "*.secret"));
        
        // 匹配包含但不匹配排除
        assertThat(config.shouldExclude("user.name")).isFalse();
        assertThat(config.shouldExclude("user.email")).isFalse();
        
        // 匹配包含但也匹配排除
        assertThat(config.shouldExclude("user.password")).isTrue();
        assertThat(config.shouldExclude("user.secret")).isTrue();
        
        // 不匹配包含
        assertThat(config.shouldExclude("admin.name")).isTrue();
    }

    @Test
    @DisplayName("SnapshotConfig通配符匹配 - 基本匹配")
    void snapshotConfig_patternMatching_basic() {
        config.setExcludePatterns(Arrays.asList("*"));
        
        // 星号匹配所有
        assertThat(config.shouldExclude("anything")).isTrue();
        assertThat(config.shouldExclude("user.name")).isTrue();
    }

    @Test
    @DisplayName("SnapshotConfig通配符匹配 - 复杂模式")
    void snapshotConfig_patternMatching_complex() {
        config.setExcludePatterns(Arrays.asList(
            "user.?.temp",      // 单字符通配符
            "*.config*",        // 星号匹配（不跨越点）
            "system.*"          // 简单星号匹配
        ));
        
        // 单字符通配符匹配
        assertThat(config.shouldExclude("user.a.temp")).isTrue();
        assertThat(config.shouldExclude("user.1.temp")).isTrue();
        assertThat(config.shouldExclude("user.ab.temp")).isFalse(); // 两个字符不匹配
        
        // 星号匹配（[^.]*不跨越点分隔符）
        assertThat(config.shouldExclude("app.config")).isTrue();
        assertThat(config.shouldExclude("test.config.debug")).isFalse(); // 有点在config后，不匹配
        assertThat(config.shouldExclude("appconfig")).isFalse(); // 没有点分隔，不匹配
        assertThat(config.shouldExclude("my.configdata")).isTrue(); // 匹配*.config*模式
        
        // 系统模式匹配
        assertThat(config.shouldExclude("system.settings")).isTrue();
        assertThat(config.shouldExclude("system.cache")).isTrue();
        assertThat(config.shouldExclude("user.settings")).isFalse();
    }

    @Test
    @DisplayName("DeepSnapshotStrategy基本功能")
    void deepSnapshotStrategy_basicFunctionality() {
        assertThat(deepStrategy.getName()).isEqualTo("deep");
        assertThat(deepStrategy.supportsAsync()).isTrue();
    }

    @Test
    @DisplayName("DeepSnapshotStrategy null对象处理")
    void deepSnapshotStrategy_nullObject() {
        Map<String, Object> result = deepStrategy.capture("test", null, config);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("DeepSnapshotStrategy正常对象捕获")
    void deepSnapshotStrategy_normalCapture() {
        TestObject obj = new TestObject("testName", 42);
        
        Map<String, Object> result = deepStrategy.capture("testObj", obj, config);
        
        // 应该有结果（具体内容依赖ObjectSnapshotDeep实现）
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("DeepSnapshotStrategy字段过滤")
    void deepSnapshotStrategy_fieldFiltering() {
        TestObject obj = new TestObject("testName", 42);
        
        Map<String, Object> result = deepStrategy.capture("testObj", obj, config, "name", "value");
        
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("DeepSnapshotStrategy异步捕获")
    void deepSnapshotStrategy_asyncCapture() throws Exception {
        TestObject obj = new TestObject("testName", 42);
        
        CompletableFuture<Map<String, Object>> future = deepStrategy.captureAsync("testObj", obj, config);
        
        Map<String, Object> result = future.get();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("DeepSnapshotStrategy配置验证 - 有效配置")
    void deepSnapshotStrategy_validateConfig_valid() {
        SnapshotConfig validConfig = new SnapshotConfig();
        validConfig.setMaxDepth(5);
        validConfig.setMaxStackDepth(500);
        validConfig.setTimeBudgetMs(100);
        validConfig.setCollectionSummaryThreshold(50);
        
        assertThatNoException().isThrownBy(() -> deepStrategy.validateConfig(validConfig));
    }

    @Test
    @DisplayName("DeepSnapshotStrategy配置验证 - maxDepth边界")
    void deepSnapshotStrategy_validateConfig_maxDepthBounds() {
        SnapshotConfig invalidConfig = new SnapshotConfig();
        
        // maxDepth < 0
        invalidConfig.setMaxDepth(-1);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth must be between 0 and 10");
        
        // maxDepth > 10
        invalidConfig.setMaxDepth(11);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth must be between 0 and 10");
    }

    @Test
    @DisplayName("DeepSnapshotStrategy配置验证 - maxStackDepth边界")
    void deepSnapshotStrategy_validateConfig_maxStackDepthBounds() {
        SnapshotConfig invalidConfig = new SnapshotConfig();
        
        // maxStackDepth < 100
        invalidConfig.setMaxStackDepth(50);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxStackDepth must be between 100 and 10000");
        
        // maxStackDepth > 10000
        invalidConfig.setMaxStackDepth(15000);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxStackDepth must be between 100 and 10000");
    }

    @Test
    @DisplayName("DeepSnapshotStrategy配置验证 - timeBudgetMs边界")
    void deepSnapshotStrategy_validateConfig_timeBudgetMsBounds() {
        SnapshotConfig invalidConfig = new SnapshotConfig();
        
        // timeBudgetMs < 0
        invalidConfig.setTimeBudgetMs(-1);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeBudgetMs must be between 0 and 5000");
        
        // timeBudgetMs > 5000
        invalidConfig.setTimeBudgetMs(6000);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeBudgetMs must be between 0 and 5000");
    }

    @Test
    @DisplayName("DeepSnapshotStrategy配置验证 - collectionSummaryThreshold边界")
    void deepSnapshotStrategy_validateConfig_collectionSummaryThresholdBounds() {
        SnapshotConfig invalidConfig = new SnapshotConfig();
        
        // collectionSummaryThreshold < 10
        invalidConfig.setCollectionSummaryThreshold(5);
        assertThatThrownBy(() -> deepStrategy.validateConfig(invalidConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("collectionSummaryThreshold must be >= 10");
    }

    @Test
    @DisplayName("SnapshotStrategy接口默认方法")
    void snapshotStrategy_defaultMethods() {
        SnapshotStrategy testStrategy = new TestSnapshotStrategy();
        
        // 默认不支持异步
        assertThat(testStrategy.supportsAsync()).isFalse();
        
        // 默认验证方法不抛异常
        assertThatNoException().isThrownBy(() -> testStrategy.validateConfig(config));
    }

    @Test
    @DisplayName("SnapshotConfig所有字段的getter/setter")
    void snapshotConfig_allGettersSetters() {
        SnapshotConfig testConfig = new SnapshotConfig();
        
        // 测试所有字段的设置和获取
        testConfig.setEnableDeep(true);
        assertThat(testConfig.isEnableDeep()).isTrue();
        
        testConfig.setMaxDepth(5);
        assertThat(testConfig.getMaxDepth()).isEqualTo(5);
        
        testConfig.setMaxStackDepth(2000);
        assertThat(testConfig.getMaxStackDepth()).isEqualTo(2000);
        
        testConfig.setTimeBudgetMs(200);
        assertThat(testConfig.getTimeBudgetMs()).isEqualTo(200);
        
        testConfig.setCollectionSummaryThreshold(200);
        assertThat(testConfig.getCollectionSummaryThreshold()).isEqualTo(200);
        
        testConfig.setMetricsEnabled(false);
        assertThat(testConfig.isMetricsEnabled()).isFalse();
        
        List<String> newIncludes = Arrays.asList("new.pattern");
        testConfig.setIncludePatterns(newIncludes);
        assertThat(testConfig.getIncludePatterns()).isEqualTo(newIncludes);
        
        List<String> newExcludes = Arrays.asList("new.exclude");
        testConfig.setExcludePatterns(newExcludes);
        assertThat(testConfig.getExcludePatterns()).isEqualTo(newExcludes);
    }

    // 测试用辅助类
    private static class TestObject {
        private String name;
        private int value;
        
        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public int getValue() { return value; }
    }
    
    // 测试用SnapshotStrategy实现
    private static class TestSnapshotStrategy implements SnapshotStrategy {
        @Override
        public Map<String, Object> capture(String objectName, Object target, SnapshotConfig config, String... fields) {
            return Collections.emptyMap();
        }
        
        @Override
        public String getName() {
            return "test";
        }
    }
}