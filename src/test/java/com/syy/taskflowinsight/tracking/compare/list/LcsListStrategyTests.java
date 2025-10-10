package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LcsListStrategyTests {

    @Test
    void smallList_usesLcs_andProducesExpectedChanges() {
        // Given
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(2, 3, 4);
        LcsListStrategy s = new LcsListStrategy();
        CompareOptions opts = CompareOptions.builder().detectMoves(true).build();

        // When
        CompareResult r = s.compare(a, b, opts);

        // Then
        assertNotNull(r);
        assertEquals("LCS", r.getAlgorithmUsed());
        assertFalse(r.isIdentical());
        assertTrue(r.getChangeCount() >= 2);
        // 应包含对 index=0 的删除、index=2 的新增（顺序可能不同，断言集合包含关系）
        String joined = r.getChanges().stream().map(c -> c.getFieldName() + ":" + c.getChangeType()).reduce("", (x,y)->x+","+y);
        assertTrue(joined.contains("[0]:DELETE"));
        assertTrue(joined.contains("[2]:CREATE"));
    }

    @Test
    void largeList_degrades_notComputingLcs() {
        // Given size > 300
        List<Integer> a = java.util.stream.IntStream.range(0, 310).boxed().toList();
        List<Integer> b = java.util.stream.IntStream.range(1, 311).boxed().toList();
        LcsListStrategy s = new LcsListStrategy();

        // When
        CompareResult r = s.compare(a, b, CompareOptions.DEFAULT);

        // Then
        assertNotNull(r);
        assertNotNull(r.getAlgorithmUsed());
        assertTrue(r.getAlgorithmUsed().startsWith("LCS"));
        assertTrue(r.getAlgorithmUsed().contains("DEGRADED"));
    }
}

