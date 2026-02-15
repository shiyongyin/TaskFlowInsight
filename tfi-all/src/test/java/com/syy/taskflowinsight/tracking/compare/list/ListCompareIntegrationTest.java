package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * List比较路由集成测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@SpringBootTest
class ListCompareIntegrationTest {
    
    @Autowired
    private CompareService compareService;
    
    @Test
    void testListRoutingToSimpleStrategy() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "modified", "c");
        
        // 不指定策略，应该默认使用SIMPLE
        CompareOptions options = CompareOptions.DEFAULT;
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(1, result.getChanges().size());
        assertEquals(ChangeType.UPDATE, result.getChanges().get(0).getChangeType());
        assertEquals("[1]", result.getChanges().get(0).getFieldName());
    }
    
    @Test
    void testListRoutingToSpecifiedSimpleStrategy() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "modified", "c");
        
        // 显式指定SIMPLE策略
        CompareOptions options = CompareOptions.builder()
            .strategyName("SIMPLE")
            .build();
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(1, result.getChanges().size());
        assertEquals(ChangeType.UPDATE, result.getChanges().get(0).getChangeType());
    }
    
    @Test
    void testListRoutingToAsSetStrategy() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("c", "a", "b", "d"); // 顺序不同，新增d
        
        // 指定AS_SET策略
        CompareOptions options = CompareOptions.builder()
            .strategyName("AS_SET")
            .build();
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(1, result.getChanges().size()); // 只有新增d
        assertEquals(ChangeType.CREATE, result.getChanges().get(0).getChangeType());
        assertEquals("d", result.getChanges().get(0).getNewValue());
    }
    
    @Test
    void testAsSetStrategyIgnoresOrder() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("c", "a", "b"); // 不同顺序，相同元素
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("AS_SET")
            .build();
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertTrue(result.isIdentical());
        assertEquals(0, result.getChanges().size());
    }
    
    @Test
    void testAsSetStrategyOnlyCreateDelete() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "c", "d"); // 删除b，新增d
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("AS_SET")
            .build();
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertFalse(result.isIdentical());
        assertEquals(2, result.getChanges().size());
        
        // 验证只有CREATE/DELETE，没有UPDATE
        assertTrue(result.getChangesByType(ChangeType.DELETE).stream()
            .anyMatch(c -> "b".equals(c.getOldValue())));
        assertTrue(result.getChangesByType(ChangeType.CREATE).stream()
            .anyMatch(c -> "d".equals(c.getNewValue())));
        assertTrue(result.getChangesByType(ChangeType.UPDATE).isEmpty());
    }
    
    @Test
    void testNoMoveTypeGenerated() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("b", "a", "c"); // a和b交换位置
        
        // 测试SIMPLE策略
        CompareOptions simpleOptions = CompareOptions.builder()
            .strategyName("SIMPLE")
            .build();
        CompareResult simpleResult = compareService.compare(list1, list2, simpleOptions);
        assertTrue(simpleResult.getChangesByType(ChangeType.MOVE).isEmpty());
        
        // 测试AS_SET策略
        CompareOptions asSetOptions = CompareOptions.builder()
            .strategyName("AS_SET")
            .build();
        CompareResult asSetResult = compareService.compare(list1, list2, asSetOptions);
        assertTrue(asSetResult.getChangesByType(ChangeType.MOVE).isEmpty());
    }
}
