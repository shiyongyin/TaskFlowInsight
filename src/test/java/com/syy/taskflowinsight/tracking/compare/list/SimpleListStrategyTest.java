package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleListStrategy单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
class SimpleListStrategyTest {
    
    private final SimpleListStrategy strategy = new SimpleListStrategy();
    private final CompareOptions options = CompareOptions.DEFAULT;
    
    @Test
    void testIdenticalLists() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "b", "c");
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertTrue(result.isIdentical());
        assertEquals(0, result.getChanges().size());
    }
    
    @Test
    void testBasicAddDeleteUpdate() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "modified", "c", "d");
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(2, result.getChanges().size());
        
        // 验证UPDATE操作
        FieldChange updateChange = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.UPDATE)
            .findFirst()
            .orElse(null);
        assertNotNull(updateChange);
        assertEquals("[1]", updateChange.getFieldName());
        assertEquals("b", updateChange.getOldValue());
        assertEquals("modified", updateChange.getNewValue());
        
        // 验证CREATE操作
        FieldChange createChange = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.CREATE)
            .findFirst()
            .orElse(null);
        assertNotNull(createChange);
        assertEquals("[3]", createChange.getFieldName());
        assertEquals("d", createChange.getNewValue());
    }
    
    @Test
    void testDeleteOperation() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d");
        List<String> list2 = Arrays.asList("a", "b");
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(2, result.getChanges().size());
        
        // 验证DELETE操作
        long deleteCount = result.getChanges().stream()
            .filter(c -> c.getChangeType() == ChangeType.DELETE)
            .count();
        assertEquals(2, deleteCount);
    }
    
    @Test
    void testEmptyLists() {
        List<String> list1 = Collections.emptyList();
        List<String> list2 = Collections.emptyList();
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        assertTrue(result.isIdentical());
        assertEquals(0, result.getChanges().size());
    }
    
    @Test
    void testNullLists() {
        CompareResult result = strategy.compare(null, null, options);
        assertTrue(result.isIdentical());
        
        List<String> list = Arrays.asList("a", "b");
        CompareResult result2 = strategy.compare(null, list, options);
        assertFalse(result2.isIdentical());
        
        CompareResult result3 = strategy.compare(list, null, options);
        assertFalse(result3.isIdentical());
    }
    
    @Test
    void testDoesNotSupportMoveDetection() {
        assertFalse(strategy.supportsMoveDetection());
    }
    
    @Test
    void testStrategyMetadata() {
        assertEquals("SIMPLE", strategy.getStrategyName());
        assertEquals(Integer.MAX_VALUE, strategy.getMaxRecommendedSize());
    }
    
    @Test
    void testNoMoveTypeGenerated() {
        // 即使元素位置交换，SIMPLE策略也不应输出MOVE类型
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("b", "a", "c"); // a和b交换位置
        
        CompareResult result = strategy.compare(list1, list2, options);
        
        // 应该输出UPDATE而非MOVE
        assertTrue(result.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
        assertTrue(result.getChanges().stream()
            .anyMatch(c -> c.getChangeType() == ChangeType.UPDATE));
    }
}