package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LevenshteinListStrategy单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
class LevenshteinListStrategyTest {
    
    private final LevenshteinListStrategy strategy = new LevenshteinListStrategy();
    
    @Test
    void testIdenticalLists() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "b", "c");
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(false)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertTrue(result.isIdentical());
        assertEquals(0, result.getChanges().size());
    }
    
    @Test
    void testBasicEditOperationsWithoutMoves() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "modified", "c", "d");
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(false)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(2, result.getChanges().size());
        
        // 验证UPDATE操作
        assertTrue(result.getChangesByType(ChangeType.UPDATE).stream()
            .anyMatch(c -> "b".equals(c.getOldValue()) && 
                          "modified".equals(c.getNewValue())));
        
        // 验证CREATE操作
        assertTrue(result.getChangesByType(ChangeType.CREATE).stream()
            .anyMatch(c -> "d".equals(c.getNewValue())));
        
        // 不应该有MOVE操作
        assertTrue(result.getChangesByType(ChangeType.MOVE).isEmpty());
    }
    
    @Test
    void testMoveDetectionEnabled() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d");
        List<String> list2 = Arrays.asList("a", "c", "b", "d"); // b和c交换位置
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertTrue(result.getChanges().size() > 0);
        
        // 应该检测出移动操作
        assertFalse(result.getChangesByType(ChangeType.MOVE).isEmpty());
        
        // 移动的元素应该是b或c
        assertTrue(result.getChangesByType(ChangeType.MOVE).stream()
            .anyMatch(c -> "b".equals(c.getOldValue()) || "c".equals(c.getOldValue())));
    }
    
    @Test
    void testMoveDetectionDisabled() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d");
        List<String> list2 = Arrays.asList("a", "c", "b", "d"); // b和c交换位置
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(false)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        // 不应该检测出移动操作
        assertTrue(result.getChangesByType(ChangeType.MOVE).isEmpty());
        
        // 应该有变更（因为位置不同），但不是MOVE类型
        assertFalse(result.isIdentical());
        
        // 变更应该是UPDATE或DELETE+CREATE的组合
        assertTrue(result.getChangesByType(ChangeType.MOVE).isEmpty());
    }
    
    @Test
    void testComplexMoveDetection() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d", "e");
        List<String> list2 = Arrays.asList("e", "a", "d", "b", "c"); // 复杂的重排
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        
        // 应该检测到多个移动操作
        long moveCount = result.getChangesByType(ChangeType.MOVE).size();
        assertTrue(moveCount > 0);
    }
    
    @Test
    void testMixedOperationsWithMoves() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d");
        List<String> list2 = Arrays.asList("c", "a", "new", "d"); // c移动到前面，b删除，新增new
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        
        // 应该包含不同类型的操作
        boolean hasMove = !result.getChangesByType(ChangeType.MOVE).isEmpty();
        boolean hasDelete = !result.getChangesByType(ChangeType.DELETE).isEmpty();
        boolean hasCreate = !result.getChangesByType(ChangeType.CREATE).isEmpty();
        
        assertTrue(hasMove || hasDelete || hasCreate); // 至少应该有一种操作
    }
    
    @Test
    void testDuplicateElementsHandling() {
        List<String> list1 = Arrays.asList("a", "b", "a", "c");
        List<String> list2 = Arrays.asList("a", "c", "a", "b");
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        // 对于重复元素，移动检测应该能够合理处理
        assertNotNull(result);
        assertFalse(result.isIdentical());
    }
    
    @Test
    void testEmptyAndNullLists() {
        // 空列表
        List<String> empty1 = Collections.emptyList();
        List<String> empty2 = Collections.emptyList();
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        CompareResult result1 = strategy.compare(empty1, empty2, options);
        assertTrue(result1.isIdentical());
        
        // null列表
        CompareResult result2 = strategy.compare(null, null, options);
        assertTrue(result2.isIdentical());
        
        // 一个为null
        List<String> list = Arrays.asList("a", "b");
        CompareResult result3 = strategy.compare(null, list, options);
        assertFalse(result3.isIdentical());
    }
    
    @Test
    void testLargeListDirectCall() {
        // 直接调用strategy（绕过Executor），大列表应该能正常处理
        // 注意：实际使用中大列表会被Executor拦截降级
        List<String> largeList1 = generateList(600);
        List<String> largeList2 = generateList(600);
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        CompareResult result = strategy.compare(largeList1, largeList2, options);
        
        // 直接调用strategy应该能正常返回结果
        assertNotNull(result);
        assertTrue(result.isIdentical()); // 内容相同
    }
    
    @Test
    void testStrategyMetadata() {
        assertTrue(strategy.supportsMoveDetection());
        assertEquals("LEVENSHTEIN", strategy.getStrategyName());
        assertEquals(500, strategy.getMaxRecommendedSize());
    }
    
    @Test
    void testPerformanceOnMediumList() {
        // 测试中等大小列表的性能
        List<String> list1 = generateList(100);
        List<String> list2 = generateShuffledList(100);
        
        CompareOptions options = CompareOptions.builder()
            .detectMoves(true)
            .build();
        
        long startTime = System.nanoTime();
        CompareResult result = strategy.compare(list1, list2, options);
        long duration = System.nanoTime() - startTime;
        
        // 100元素应该在合理时间内完成
        assertTrue(duration < 100_000_000); // <100ms
        assertNotNull(result);
    }
    
    // 辅助方法
    private List<String> generateList(int size) {
        return IntStream.range(0, size)
            .mapToObj(i -> "item" + i)
            .collect(Collectors.toList());
    }
    
    private List<String> generateShuffledList(int size) {
        List<String> list = generateList(size);
        Collections.shuffle(list);
        return list;
    }
}
