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
 * Levenshtein策略集成测试
 * 验证通过CompareService的MOVE检测功能
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@SpringBootTest
class LevenshteinIntegrationTest {
    
    @Autowired
    private CompareService compareService;
    
    @Test
    void testLevenshteinStrategyRouting() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "modified", "c");
        
        // 指定LEVENSHTEIN策略
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(false)
            .build();
        
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertEquals(1, result.getChanges().size());
        
        // 应该是UPDATE操作，不是MOVE
        assertEquals(ChangeType.UPDATE, result.getChanges().get(0).getChangeType());
        assertTrue(result.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testMoveDetectionThroughCompareService() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d");
        List<String> list2 = Arrays.asList("a", "c", "b", "d"); // b和c交换位置
        
        // 启用MOVE检测
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .detectMoves(true)
            .build();
        
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertNotNull(result);
        assertFalse(result.isIdentical());
        
        // 应该检测到MOVE操作
        assertTrue(result.getChanges().stream()
            .anyMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testMoveDetectionDefaultDisabled() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("b", "a", "c"); // a和b交换
        
        // 使用LEVENSHTEIN但不显式设置detectMoves（应该默认false）
        CompareOptions options = CompareOptions.builder()
            .strategyName("LEVENSHTEIN")
            .build();
        
        CompareResult result = compareService.compare(list1, list2, options);
        
        assertNotNull(result);
        
        // 验证detectMoves默认为false，不应该有MOVE操作
        assertTrue(result.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testCompareAllThreeStrategies() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("c", "a", "b"); // 循环移位
        
        // SIMPLE策略
        CompareResult simpleResult = compareService.compare(list1, list2, 
            CompareOptions.builder().strategyName("SIMPLE").build());
        assertTrue(simpleResult.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
        
        // AS_SET策略  
        CompareResult asSetResult = compareService.compare(list1, list2,
            CompareOptions.builder().strategyName("AS_SET").build());
        assertTrue(asSetResult.isIdentical()); // 相同元素，忽略顺序
        
        // LEVENSHTEIN策略（未启用MOVE检测）
        CompareResult levenshteinResult = compareService.compare(list1, list2,
            CompareOptions.builder().strategyName("LEVENSHTEIN").detectMoves(false).build());
        assertTrue(levenshteinResult.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
        
        // LEVENSHTEIN策略（启用MOVE检测）
        CompareResult levenshteinMoveResult = compareService.compare(list1, list2,
            CompareOptions.builder().strategyName("LEVENSHTEIN").detectMoves(true).build());
        assertTrue(levenshteinMoveResult.getChanges().stream()
            .anyMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testMoveOnlyInLevenshteinWithDetectMoves() {
        List<String> list1 = Arrays.asList("x", "y", "z");
        List<String> list2 = Arrays.asList("y", "x", "z"); // x和y交换
        
        // 测试所有策略，只有LEVENSHTEIN+detectMoves=true应该输出MOVE
        String[] strategies = {"SIMPLE", "AS_SET", "LEVENSHTEIN"};
        
        for (String strategy : strategies) {
            CompareResult result = compareService.compare(list1, list2,
                CompareOptions.builder()
                    .strategyName(strategy)
                    .detectMoves(strategy.equals("LEVENSHTEIN")) // 只有LEVENSHTEIN启用MOVE检测
                    .build());
            
            if ("LEVENSHTEIN".equals(strategy)) {
                // LEVENSHTEIN+detectMoves=true应该有MOVE
                assertTrue(result.getChanges().stream()
                    .anyMatch(c -> c.getChangeType() == ChangeType.MOVE),
                    "LEVENSHTEIN strategy with detectMoves=true should detect MOVE operations");
            } else {
                // 其他策略不应该有MOVE
                assertTrue(result.getChanges().stream()
                    .noneMatch(c -> c.getChangeType() == ChangeType.MOVE),
                    strategy + " strategy should not output MOVE operations");
            }
        }
    }
}