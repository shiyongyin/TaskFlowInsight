package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 降级机制测试
 * 验证大列表自动降级功能
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@SpringBootTest
class DegradationTest {
    
    @Autowired
    private CompareService compareService;
    
    @Autowired
    private ListCompareExecutor listCompareExecutor;
    
    @Test
    void testSize500Boundary() {
        // 测试边界值：500元素不应降级
        List<String> list1_500 = generateList(500);
        List<String> list2_500 = generateList(500);
        
        long initialCount = listCompareExecutor.getDegradationCount();
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();
        
        CompareResult result = compareService.compare(list1_500, list2_500, options);
        
        assertNotNull(result);
        // 500元素不应该触发降级
        assertEquals(initialCount, listCompareExecutor.getDegradationCount());
    }
    
    @Test
    void testSize501AutoDegradation() {
        // 测试501元素触发降级
        List<String> list1_501 = generateList(501);
        List<String> list2_501 = generateList(501);
        
        long initialCount = listCompareExecutor.getDegradationCount();
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();
        
        CompareResult result = compareService.compare(list1_501, list2_501, options);
        
        assertNotNull(result);
        // 501元素应该触发降级
        assertEquals(initialCount + 1, listCompareExecutor.getDegradationCount());
        
        // 降级后不应该有MOVE操作（因为降级到SIMPLE）
        assertTrue(result.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testSize1000ForceDegradation() {
        // 测试1000元素强制降级到SIMPLE
        List<String> list1_1000 = generateList(1000);
        List<String> list2_1000 = generateList(1000);
        
        long initialCount = listCompareExecutor.getDegradationCount();
        
        // 即使请求AS_SET，也应该强制降级到SIMPLE
        CompareOptions options = CompareOptions.builder()
            .strategyName("AS_SET")
            .build();
        
        CompareResult result = compareService.compare(list1_1000, list2_1000, options);
        
        assertNotNull(result);
        // 1000元素应该触发强制降级
        assertEquals(initialCount + 1, listCompareExecutor.getDegradationCount());
    }
    
    @Test
    void testBusinessHintDegradationToAsSet() {
        // 测试业务hint降级：600元素，指定AS_SET
        List<String> list1_600 = generateList(600);
        List<String> list2_600 = generateListWithDifference(600, 590); // 有差异的列表
        
        long initialCount = listCompareExecutor.getDegradationCount();
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("AS_SET") // 业务指定AS_SET
            .build();
        
        CompareResult result = compareService.compare(list1_600, list2_600, options);
        
        assertNotNull(result);
        // 应该触发降级
        assertEquals(initialCount + 1, listCompareExecutor.getDegradationCount());
        
        // 应该使用AS_SET策略（只有CREATE/DELETE，无MOVE/UPDATE）
        if (!result.isIdentical()) {
            assertTrue(result.getChanges().stream()
                .allMatch(c -> c.getChangeType() == ChangeType.CREATE || c.getChangeType() == ChangeType.DELETE));
            assertTrue(result.getChanges().stream()
                .noneMatch(c -> c.getChangeType() == ChangeType.MOVE || c.getChangeType() == ChangeType.UPDATE));
        }
    }
    
    @Test
    void testDefaultDegradationToSimple() {
        // 测试默认降级：700元素，指定LEVENSHTEIN，应该降级到SIMPLE
        List<String> list1_700 = generateList(700);
        List<String> list2_700 = generateListWithModification(700);
        
        long initialCount = listCompareExecutor.getDegradationCount();
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();
        
        CompareResult result = compareService.compare(list1_700, list2_700, options);
        
        assertNotNull(result);
        // 应该触发降级
        assertEquals(initialCount + 1, listCompareExecutor.getDegradationCount());
        
        // 降级到SIMPLE，不应该有MOVE操作
        assertTrue(result.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testDegradationCounterIncrement() {
        // 测试降级计数器正确递增
        long initialCount = listCompareExecutor.getDegradationCount();
        
        // 触发多次降级
        List<String> largeList1 = generateList(600);
        List<String> largeList2 = generateList(600);
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();
        
        // 第一次比较
        compareService.compare(largeList1, largeList2, options);
        assertEquals(initialCount + 1, listCompareExecutor.getDegradationCount());
        
        // 第二次比较
        compareService.compare(largeList1, largeList2, options);
        assertEquals(initialCount + 2, listCompareExecutor.getDegradationCount());
    }
    
    @Test
    void testNormalSizeNoLegradation() {
        // 测试正常大小不触发降级
        List<String> normalList1 = generateList(100);
        List<String> normalList2 = generateList(100);
        
        long initialCount = listCompareExecutor.getDegradationCount();
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();
        
        CompareResult result = compareService.compare(normalList1, normalList2, options);
        
        assertNotNull(result);
        // 不应该触发降级
        assertEquals(initialCount, listCompareExecutor.getDegradationCount());
    }
    
    @Test
    void testPerformanceWithDegradation() {
        // 测试降级后的性能
        List<String> list1 = generateList(800);
        List<String> list2 = generateList(800);
        
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();
        
        long startTime = System.nanoTime();
        CompareResult result = compareService.compare(list1, list2, options);
        long duration = System.nanoTime() - startTime;
        
        assertNotNull(result);
        
        // 降级后的性能应该很快（<10ms）
        long durationMs = duration / 1_000_000;
        assertTrue(durationMs < 100, "Degraded comparison should be fast, actual: " + durationMs + "ms");
    }
    
    // 辅助方法
    private List<String> generateList(int size) {
        return IntStream.range(0, size)
            .mapToObj(i -> "item" + i)
            .collect(Collectors.toList());
    }
    
    private List<String> generateListWithDifference(int size, int commonSize) {
        List<String> list = IntStream.range(0, commonSize)
            .mapToObj(i -> "item" + i)
            .collect(Collectors.toList());
        
        // 添加不同的元素
        for (int i = commonSize; i < size; i++) {
            list.add("different" + i);
        }
        
        return list;
    }
    
    private List<String> generateListWithModification(int size) {
        List<String> list = IntStream.range(0, size)
            .mapToObj(i -> i == 0 ? "modified" : "item" + i)
            .collect(Collectors.toList());
        
        return list;
    }
}