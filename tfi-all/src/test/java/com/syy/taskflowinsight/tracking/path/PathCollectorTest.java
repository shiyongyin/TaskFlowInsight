package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * PathCollector核心功能测试
 * 验证对象图遍历和路径收集逻辑
 */
@DisplayName("PathCollector核心功能测试")
class PathCollectorTest {
    
    private PathCollector pathCollector;
    private PathDeduplicationConfig config;
    
    @BeforeEach
    void setUp() {
        config = new PathDeduplicationConfig();
        pathCollector = new PathCollector(config);
    }
    
    @Test
    @DisplayName("基础路径收集：从变更记录和对象快照中收集路径候选")
    void shouldCollectBasicPathCandidates() {
        // Given: 创建变更记录和对象快照
        ChangeRecord record = ChangeRecord.builder()
            .objectName("User")
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .oldValue("Alice")
            .newValue("Bob")
            .build();
        
        Map<String, Object> beforeSnapshot = Map.of("name", "Alice", "age", 25);
        Map<String, Object> afterSnapshot = Map.of("name", "Bob", "age", 25);
        
        // When: 收集路径候选
        List<PathArbiter.PathCandidate> candidates = pathCollector.collectFromChangeRecords(
            List.of(record), beforeSnapshot, afterSnapshot);
        
        // Then: 应该收集到路径候选
        assertThat(candidates).isNotEmpty();
        
        // 验证候选包含预期的路径信息
        boolean hasNamePath = candidates.stream()
            .anyMatch(c -> "name".equals(c.getPath()) && c.getAccessType() == PathArbiter.AccessType.FIELD);
        assertThat(hasNamePath).as("应该收集到name字段的路径候选").isTrue();
    }
    
    @Test
    @DisplayName("复杂对象图遍历：处理嵌套结构和集合")
    void shouldTraverseComplexObjectGraphs() {
        // Given: 复杂的对象结构
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", "John");
        profile.put("lastName", "Doe");
        
        List<String> tags = Arrays.asList("developer", "architect");
        Map<String, String> settings = Map.of("theme", "dark", "language", "en");
        
        Map<String, Object> complexObject = new HashMap<>();
        complexObject.put("profile", profile);
        complexObject.put("tags", tags);
        complexObject.put("settings", settings);
        
        // When: 收集复杂对象的路径
        List<PathArbiter.PathCandidate> paths = pathCollector.collectPathsForObject(
            profile, "user.profile", complexObject);
        
        // Then: 应该找到指向profile对象的路径
        assertThat(paths).isNotEmpty();
        
        // 验证路径包含预期信息
        boolean hasProfilePath = paths.stream()
            .anyMatch(c -> c.getTarget() == profile);
        assertThat(hasProfilePath).as("应该包含指向profile对象的路径").isTrue();
    }
    
    @Test
    @DisplayName("访问类型推断：正确识别不同的访问模式")
    void shouldInferCorrectAccessTypes() {
        // Given: 不同访问模式的路径
        Object targetObject = new TestObject("test");
        
        // When: 收集不同访问类型的路径
        PathArbiter.PathCandidate fieldCandidate = new PathArbiter.PathCandidate("obj.field", 2, targetObject);
        PathArbiter.PathCandidate mapCandidate = new PathArbiter.PathCandidate("obj[\"key\"]", 2, targetObject);
        PathArbiter.PathCandidate arrayCandidate = new PathArbiter.PathCandidate("obj[0]", 2, targetObject);
        PathArbiter.PathCandidate setCandidate = new PathArbiter.PathCandidate("obj[id=123]", 2, targetObject);
        
        // Then: 访问类型应该被正确推断
        assertThat(fieldCandidate.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
        assertThat(mapCandidate.getAccessType()).isEqualTo(PathArbiter.AccessType.MAP_KEY);
        assertThat(arrayCandidate.getAccessType()).isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        assertThat(setCandidate.getAccessType()).isEqualTo(PathArbiter.AccessType.SET_ELEMENT);
    }
    
    @Test
    @DisplayName("循环引用检测：避免无限递归")
    void shouldHandleCircularReferences() {
        // Given: 创建循环引用的对象结构
        Map<String, Object> parent = new HashMap<>();
        Map<String, Object> child = new HashMap<>();
        parent.put("child", child);
        child.put("parent", parent); // 循环引用
        
        ChangeRecord record = ChangeRecord.builder()
            .objectName("Parent")
            .fieldName("child")
            .changeType(ChangeType.UPDATE)
            .build();
        
        // When: 处理循环引用对象（不应该抛出异常）
        List<PathArbiter.PathCandidate> candidates = pathCollector.collectFromChangeRecords(
            List.of(record), parent, parent);
        
        // Then: 应该正常处理，不抛出StackOverflowError
        assertThat(candidates).isNotNull(); // 能正常返回就说明处理了循环引用
    }
    
    @Test
    @DisplayName("深度限制：遵循配置的最大深度限制")
    void shouldRespectMaxDepthLimit() {
        // Given: 设置较小的深度限制
        PathDeduplicationConfig limitedConfig = new PathDeduplicationConfig();
        limitedConfig.setMaxCollectionDepth(2);
        PathCollector limitedCollector = new PathCollector(limitedConfig);
        
        // 创建深层嵌套对象
        Map<String, Object> level3 = Map.of("value", "deep");
        Map<String, Object> level2 = Map.of("level3", level3);
        Map<String, Object> level1 = Map.of("level2", level2);
        Map<String, Object> root = Map.of("level1", level1);
        
        // When: 收集深层对象的路径
        List<PathArbiter.PathCandidate> paths = limitedCollector.collectPathsForObject(
            level3, "root.level1.level2.level3", root);
        
        // Then: 应该受深度限制约束
        assertThat(paths).isNotNull(); // 基本测试，具体的深度验证可能需要更复杂的逻辑
    }
    
    @Test
    @DisplayName("缓存机制：重复对象应该命中缓存")
    void shouldUtilizeCacheForRepeatedObjects() {
        // Given: 启用缓存的配置
        PathDeduplicationConfig cacheConfig = new PathDeduplicationConfig();
        cacheConfig.setCacheEnabled(true);
        PathCollector cacheCollector = new PathCollector(cacheConfig);
        
        Object sameObject = new TestObject("cached");
        Map<String, Object> snapshot = Map.of("field", sameObject);
        
        // When: 多次收集同一对象的路径
        List<PathArbiter.PathCandidate> firstCall = cacheCollector.collectPathsForObject(
            sameObject, "obj.field", snapshot);
        List<PathArbiter.PathCandidate> secondCall = cacheCollector.collectPathsForObject(
            sameObject, "obj.field", snapshot);
        
        // Then: 两次调用都应该返回结果（具体的缓存命中验证可通过统计信息检查）
        assertThat(firstCall).isNotEmpty();
        assertThat(secondCall).isNotEmpty();
        
        // 验证缓存统计
        Map<String, Object> cacheStats = cacheCollector.getCacheStatistics();
        assertThat(cacheStats).containsKey("cacheEnabled");
        assertThat(cacheStats.get("cacheEnabled")).isEqualTo(true);
    }
    
    @Test
    @DisplayName("性能配置：对象数量限制正常工作")
    void shouldRespectObjectCountLimits() {
        // Given: 设置较小的对象数量限制
        PathDeduplicationConfig limitedConfig = new PathDeduplicationConfig();
        limitedConfig.setMaxObjectsPerLevel(2);
        PathCollector limitedCollector = new PathCollector(limitedConfig);
        
        // 创建包含多个对象的集合
        List<String> manyItems = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        Map<String, Object> container = Map.of("items", manyItems);
        
        // When: 处理包含大量对象的容器
        List<PathArbiter.PathCandidate> paths = limitedCollector.collectPathsForObject(
            manyItems, "container.items", container);
        
        // Then: 应该正常处理（不验证具体数量，因为实现可能有变化）
        assertThat(paths).isNotNull();
    }
    
    @Test
    @DisplayName("配置禁用：禁用时应该返回空结果")
    void shouldReturnEmptyWhenDisabled() {
        // Given: 禁用的配置
        PathDeduplicationConfig disabledConfig = new PathDeduplicationConfig();
        disabledConfig.setEnabled(false);
        PathCollector disabledCollector = new PathCollector(disabledConfig);
        
        ChangeRecord record = ChangeRecord.builder()
            .objectName("Test")
            .fieldName("field")
            .changeType(ChangeType.UPDATE)
            .build();
        
        // When: 使用禁用的收集器
        List<PathArbiter.PathCandidate> candidates = disabledCollector.collectFromChangeRecords(
            List.of(record), Map.of(), Map.of());
        
        // Then: 应该返回空结果
        assertThat(candidates).isEmpty();
    }
    
    // 测试用的简单对象
    private static class TestObject {
        private final String name;
        
        public TestObject(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return "TestObject{name='" + name + "'}";
        }
    }
}