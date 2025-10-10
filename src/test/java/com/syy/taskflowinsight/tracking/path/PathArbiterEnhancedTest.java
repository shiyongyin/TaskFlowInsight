package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * PathArbiter增强功能测试
 * 验证CARD-CT-ALIGN的核心裁决逻辑
 */
@DisplayName("PathArbiter增强功能测试")
class PathArbiterEnhancedTest {
    
    private Object testObject;
    
    @BeforeEach
    void setUp() {
        testObject = new TestObject("test");
    }
    
    @Test
    @DisplayName("访问类型优先级：FIELD > MAP_KEY > ARRAY_INDEX > SET_ELEMENT")
    void shouldRespectAccessTypePriority() {
        // Given: 同一对象的不同访问路径
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("obj[0]", 1, PathArbiter.AccessType.ARRAY_INDEX, testObject),
            new PathArbiter.PathCandidate("obj[\"key\"]", 1, PathArbiter.AccessType.MAP_KEY, testObject),
            new PathArbiter.PathCandidate("obj.field", 1, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("obj[id=123]", 1, PathArbiter.AccessType.SET_ELEMENT, testObject)
        );
        
        // When: 选择最具体路径
        PathArbiter.PathCandidate selected = PathArbiter.selectMostSpecific(candidates);
        
        // Then: FIELD类型应该被选中
        assertThat(selected.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
        assertThat(selected.getPath()).isEqualTo("obj.field");
    }
    
    @Test
    @DisplayName("深度优先：深度3优于深度2")
    void shouldPreferDeeperPath() {
        // Given: 不同深度的路径
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("root.child", 2, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("root.child.grandchild", 3, PathArbiter.AccessType.FIELD, testObject)
        );
        
        // When: 选择最具体路径
        PathArbiter.PathCandidate selected = PathArbiter.selectMostSpecific(candidates);
        
        // Then: 深度更大的路径应该被选中
        assertThat(selected.getDepth()).isEqualTo(3);
        assertThat(selected.getPath()).isEqualTo("root.child.grandchild");
    }
    
    @Test
    @DisplayName("字典序稳定性：相同优先级按路径字典序排序")
    void shouldUseLexicalOrderForTieBreaking() {
        // Given: 相同深度和访问类型的路径
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("root.zebra", 2, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("root.alpha", 2, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("root.beta", 2, PathArbiter.AccessType.FIELD, testObject)
        );
        
        // When: 选择最具体路径
        PathArbiter.PathCandidate selected = PathArbiter.selectMostSpecific(candidates);
        
        // Then: 字典序最小的应该被选中
        assertThat(selected.getPath()).isEqualTo("root.alpha");
    }
    
    @Test
    @DisplayName("AccessType.fromPath()正确推断访问类型")
    void shouldInferAccessTypeFromPath() {
        // Given & When & Then: 各种路径格式
        assertThat(PathArbiter.AccessType.fromPath("user.name")).isEqualTo(PathArbiter.AccessType.FIELD);
        assertThat(PathArbiter.AccessType.fromPath("map[\"key\"]")).isEqualTo(PathArbiter.AccessType.MAP_KEY);
        assertThat(PathArbiter.AccessType.fromPath("list[0]")).isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        assertThat(PathArbiter.AccessType.fromPath("set[id=123]")).isEqualTo(PathArbiter.AccessType.SET_ELEMENT);
        assertThat(PathArbiter.AccessType.fromPath("")).isEqualTo(PathArbiter.AccessType.FIELD);
        assertThat(PathArbiter.AccessType.fromPath(null)).isEqualTo(PathArbiter.AccessType.FIELD);
    }

    @Test
    @DisplayName("访问类型优先级：FIELD胜过较深的ARRAY")
    void shouldPreferAccessTypeOverDepth() {
        Object target = testObject;
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("root.name", 2, PathArbiter.AccessType.FIELD, target),
            new PathArbiter.PathCandidate("root.list[0].id", 3, PathArbiter.AccessType.ARRAY_INDEX, target)
        );

        PathArbiter.PathCandidate selected = PathArbiter.selectMostSpecific(candidates);
        // 由于默认配置accessTypeWeight=10000 > depthWeight=1000，FIELD类型会胜出
        assertThat(selected.getPath()).isEqualTo("root.name");
    }
    
    @Test
    @DisplayName("去重功能：同一对象多路径只保留最具体的")
    void shouldDeduplicateByTargetObject() {
        // Given: 指向不同对象的多个路径
        Object object1 = new TestObject("obj1");
        Object object2 = new TestObject("obj2");
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("path1", 1, PathArbiter.AccessType.ARRAY_INDEX, object1),
            new PathArbiter.PathCandidate("path2", 1, PathArbiter.AccessType.FIELD, object1),
            new PathArbiter.PathCandidate("path3", 2, PathArbiter.AccessType.MAP_KEY, object2)
        );

        // When: 执行去重
        List<PathArbiter.PathCandidate> result = PathArbiter.deduplicate(candidates);

        // Then: 不同对象的路径都应保留，同一对象只保留优先级最高的
        assertThat(result).hasSize(2);
        // 对于object1，FIELD类型优先级高于ARRAY_INDEX
        assertThat(result).anyMatch(c -> c.getPath().equals("path2") && c.getTarget() == object1);
        // object2的路径保留
        assertThat(result).anyMatch(c -> c.getPath().equals("path3") && c.getTarget() == object2);
    }
    
    @Test
    @DisplayName("稳定性验证：1000次运行结果一致")
    void shouldBeStableAcrossMultipleRuns() {
        // Given: 复杂的候选列表
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("root.field1", 2, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("root[\"key1\"]", 2, PathArbiter.AccessType.MAP_KEY, testObject),
            new PathArbiter.PathCandidate("root[0]", 2, PathArbiter.AccessType.ARRAY_INDEX, testObject)
        );
        
        // When & Then: 多次运行应该得到相同结果
        boolean stable = PathArbiter.verifyStability(candidates, 1000);
        assertThat(stable).isTrue();
        
        // 验证选择结果确实是FIELD类型（优先级最高）
        PathArbiter.PathCandidate selected = PathArbiter.selectMostSpecific(candidates);
        assertThat(selected.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
    }
    
    @Test
    @DisplayName("祖先路径去重：仅保留最具体的后代路径")
    void shouldKeepOnlyMostSpecificDescendantPaths() {
        // Given: 包含祖先与后代路径的候选列表
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("user", 1, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("user.name", 2, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("user.addresses[0]", 2, PathArbiter.AccessType.ARRAY_INDEX, testObject),
            new PathArbiter.PathCandidate("user.addresses[0].city", 3, PathArbiter.AccessType.FIELD, testObject)
        );
        
        // When: 执行全局最具体路径去重
        List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(candidates);
        
        // Then: 应该仅保留最具体的后代路径
        assertThat(result).hasSize(2);
        
        // 验证保留的路径
        Set<String> keptPaths = result.stream()
            .map(PathArbiter.PathCandidate::getPath)
            .collect(java.util.stream.Collectors.toSet());
        
        assertThat(keptPaths).containsExactlyInAnyOrder(
            "user.name",
            "user.addresses[0].city"
        );
        
        // 验证祖先路径被移除
        assertThat(keptPaths).doesNotContain("user", "user.addresses[0]");
    }
    
    @Test
    @DisplayName("混合访问类型优先级：相同深度时按访问权重选择")
    void shouldRespectAccessTypeWeightWhenDepthIsSame() {
        // Given: 相同深度但不同访问类型的路径（非祖先关系）
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("data.value", 2, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("other[\"key\"]", 2, PathArbiter.AccessType.MAP_KEY, testObject),
            new PathArbiter.PathCandidate("list[0]", 2, PathArbiter.AccessType.ARRAY_INDEX, testObject),
            new PathArbiter.PathCandidate("set[id=elem]", 2, PathArbiter.AccessType.SET_ELEMENT, testObject)
        );
        
        // When: 执行去重（相同深度，不同访问类型，非祖先关系）
        List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(candidates);
        
        // Then: 应该保留所有路径（因为它们不是祖先-后代关系）
        assertThat(result).hasSize(4);
        
        // 验证排序优先级：FIELD应该在前面
        List<String> resultPaths = result.stream()
            .map(PathArbiter.PathCandidate::getPath)
            .toList();
        
        assertThat(resultPaths.get(0)).isEqualTo("data.value"); // FIELD类型优先级最高
    }
    
    @Test 
    @DisplayName("PathCandidate构造器：自动推断访问类型")
    void shouldInferAccessTypeInConstructor() {
        // Given & When: 使用便捷构造器
        PathArbiter.PathCandidate fieldCandidate = new PathArbiter.PathCandidate("user.name", 2, testObject);
        PathArbiter.PathCandidate mapCandidate = new PathArbiter.PathCandidate("user[\"key\"]", 2, testObject);
        
        // Then: 访问类型应该被正确推断
        assertThat(fieldCandidate.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
        assertThat(mapCandidate.getAccessType()).isEqualTo(PathArbiter.AccessType.MAP_KEY);
    }
    
    // 测试对象类
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

    @Test
    @DisplayName("全局最具体路径去重：仅保留后代路径")
    void shouldKeepOnlyMostSpecificDescendants() {
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("user", 1, PathArbiter.AccessType.FIELD, null),
            new PathArbiter.PathCandidate("user.name", 2, PathArbiter.AccessType.FIELD, null),
            new PathArbiter.PathCandidate("user.addresses[0]", 2, PathArbiter.AccessType.ARRAY_INDEX, null),
            new PathArbiter.PathCandidate("user.addresses[0].city", 3, PathArbiter.AccessType.FIELD, null)
        );
        
        List<PathArbiter.PathCandidate> deduped = PathArbiter.deduplicateMostSpecific(candidates);
        List<String> keptPaths = deduped.stream().map(PathArbiter.PathCandidate::getPath).toList();
        
        assertThat(keptPaths).containsExactlyInAnyOrder("user.name", "user.addresses[0].city");
        assertThat(keptPaths).doesNotContain("user", "user.addresses[0]");
    }
}
