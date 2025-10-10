package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.path.PathArbiter.PathCandidate;
import com.syy.taskflowinsight.tracking.path.PathArbiter.AccessType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathArbiter基础测试
 * 验证路径裁决和去重功能
 */
class PathArbiterTest {

    @Test
    @DisplayName("深度优先：更深的路径优先级更高")
    void testDepthPriority() {
        Object target = new Object();
        PathCandidate shallow = new PathCandidate("obj.field", 2, AccessType.FIELD, target);
        PathCandidate deep = new PathCandidate("obj.nested.field", 3, AccessType.FIELD, target);
        
        List<PathCandidate> candidates = Arrays.asList(shallow, deep);
        PathCandidate result = PathArbiter.selectMostSpecific(candidates);
        
        assertEquals("obj.nested.field", result.getPath());
        assertEquals(3, result.getDepth());
    }

    @Test
    @DisplayName("访问类型权重：字段优于Map键优于数组索引")
    void testAccessTypeWeight() {
        Object target = new Object();
        PathCandidate arrayAccess = new PathCandidate("obj[0]", 2, AccessType.ARRAY_INDEX, target);
        PathCandidate mapAccess = new PathCandidate("obj[\"key\"]", 2, AccessType.MAP_KEY, target);
        PathCandidate fieldAccess = new PathCandidate("obj.field", 2, AccessType.FIELD, target);
        
        List<PathCandidate> candidates = Arrays.asList(arrayAccess, mapAccess, fieldAccess);
        PathCandidate result = PathArbiter.selectMostSpecific(candidates);
        
        assertEquals("obj.field", result.getPath());
        assertEquals(AccessType.FIELD, result.getAccessType());
    }

    @Test
    @DisplayName("字典序排序：相同深度和类型时按字典序")
    void testLexicalOrder() {
        Object target = new Object();
        PathCandidate pathZ = new PathCandidate("obj.z", 2, AccessType.FIELD, target);
        PathCandidate pathA = new PathCandidate("obj.a", 2, AccessType.FIELD, target);
        
        List<PathCandidate> candidates = Arrays.asList(pathZ, pathA);
        PathCandidate result = PathArbiter.selectMostSpecific(candidates);
        
        // 字典序较小的优先（a < z），与ChangeRecordComparator保持一致
        assertEquals("obj.a", result.getPath());
    }

    @Test
    @DisplayName("稳定ID生成：相同路径产生相同ID")
    void testStableIdGeneration() {
        Object target1 = new Object();
        Object target2 = new Object();
        
        PathCandidate candidate1 = new PathCandidate("data.items[\"key\"]", 3, AccessType.MAP_KEY, target1);
        PathCandidate candidate2 = new PathCandidate("data.items[\"key\"]", 3, AccessType.MAP_KEY, target2);
        
        assertEquals(candidate1.getStableId(), candidate2.getStableId(), 
            "相同路径必须产生相同稳定ID");
        
        // 验证1000次重复生成的一致性
        Set<String> generatedIds = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            PathCandidate candidate = new PathCandidate("test.path", 2, AccessType.FIELD, new Object());
            generatedIds.add(candidate.getStableId());
        }
        assertEquals(1, generatedIds.size(), "1000次重复生成必须产生相同ID");
    }

    @Test
    @DisplayName("路径去重：同一对象多路径只保留最具体的")
    void testPathDeduplication() {
        Object sharedTarget = new Object();
        
        PathCandidate path1 = new PathCandidate("obj", 1, AccessType.FIELD, sharedTarget);
        PathCandidate path2 = new PathCandidate("obj.field", 2, AccessType.FIELD, sharedTarget);
        PathCandidate path3 = new PathCandidate("obj[\"key\"]", 2, AccessType.MAP_KEY, sharedTarget);
        
        List<PathCandidate> allPaths = Arrays.asList(path1, path2, path3);
        List<PathCandidate> deduplicated = PathArbiter.deduplicate(allPaths);
        
        assertEquals(1, deduplicated.size(), "同一对象的多路径应去重为1条");
        assertEquals("obj.field", deduplicated.get(0).getPath(), "应保留最具体的字段访问路径");
    }

    @Test
    @DisplayName("多对象去重：不同对象保持独立")
    void testMultiObjectDeduplication() {
        Object target1 = new Object();
        Object target2 = new Object();
        
        PathCandidate path1 = new PathCandidate("obj1.field", 2, AccessType.FIELD, target1);
        PathCandidate path2 = new PathCandidate("obj2.field", 2, AccessType.FIELD, target2);
        
        List<PathCandidate> allPaths = Arrays.asList(path1, path2);
        List<PathCandidate> deduplicated = PathArbiter.deduplicate(allPaths);
        
        assertEquals(2, deduplicated.size(), "不同对象的路径应保持独立");
    }

    @Test
    @DisplayName("稳定性验证：1000次裁决结果一致")
    void testArbitrationStability() {
        Object target = new Object();
        List<PathCandidate> candidates = Arrays.asList(
            new PathCandidate("obj.field1", 2, AccessType.FIELD, target),
            new PathCandidate("obj.field2", 2, AccessType.FIELD, target),
            new PathCandidate("obj[0]", 2, AccessType.ARRAY_INDEX, target)
        );
        
        boolean isStable = PathArbiter.verifyStability(candidates, 1000);
        assertTrue(isStable, "1000次裁决结果应该一致");
    }

    @Test
    @DisplayName("边界情况：空列表和单元素列表")
    void testBoundaryCases() {
        // 空列表应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            PathArbiter.selectMostSpecific(Arrays.asList());
        });
        
        // 单元素列表直接返回
        PathCandidate single = new PathCandidate("single", 1, AccessType.FIELD, new Object());
        PathCandidate result = PathArbiter.selectMostSpecific(Arrays.asList(single));
        assertEquals(single, result);
        
        // null列表去重应返回空列表
        List<PathCandidate> emptyResult = PathArbiter.deduplicate(null);
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    @DisplayName("目标对象ID生成：不同对象产生不同ID")
    void testTargetIdGeneration() {
        Object target1 = new Object();
        Object target2 = new Object();
        
        PathCandidate candidate1 = new PathCandidate("path", 1, AccessType.FIELD, target1);
        PathCandidate candidate2 = new PathCandidate("path", 1, AccessType.FIELD, target2);
        
        assertNotEquals(candidate1.getTargetId(), candidate2.getTargetId(), 
            "不同对象应产生不同的目标ID");
        
        // null目标应产生特定ID
        PathCandidate nullTarget = new PathCandidate("path", 1, AccessType.FIELD, null);
        assertEquals("null-target", nullTarget.getTargetId());
    }

    @Test
    @DisplayName("扩展接口：高级裁决方法预留")
    void testAdvancedArbitrationInterface() {
        Object target = new Object();
        List<PathCandidate> candidates = Arrays.asList(
            new PathCandidate("obj.field", 2, AccessType.FIELD, target)
        );
        
        // 调用扩展接口，当前应返回与基础方法相同结果
        PathCandidate basic = PathArbiter.selectMostSpecific(candidates);
        PathCandidate advanced = PathArbiter.selectMostSpecificAdvanced(candidates, "future-strategy");
        
        assertEquals(basic.getPath(), advanced.getPath(), 
            "基础实现和高级接口应返回相同结果");
    }

    @Test
    @DisplayName("toString方法：提供有用的调试信息")
    void testToStringFormat() {
        PathCandidate candidate = new PathCandidate("test.path", 2, AccessType.FIELD, new Object());
        String toString = candidate.toString();
        
        assertTrue(toString.contains("test.path"), "toString应包含路径");
        assertTrue(toString.contains("depth=2"), "toString应包含深度");
        assertTrue(toString.contains("FIELD"), "toString应包含访问类型");
        assertTrue(toString.contains("ID"), "toString应包含稳定ID");
    }
}