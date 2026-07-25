package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证列表门面只委托宿主传入的CompareOperations，不构造fallback执行图。
 */
class TfiListDiffFacadeTests {

    private CompareOperations operations;
    private TfiListDiffFacade facade;

    @BeforeEach
    void setUp() {
        operations = mock(CompareOperations.class);
        ChangeReportRenderer renderer = mock(ChangeReportRenderer.class);
        facade = new TfiListDiffFacade(
                operations,
                MaskingPolicy.safeDefaults(),
                renderer);
    }

    @Test
    void convertsNullListsBeforeDefaultComparison() {
        CompareResult expected = CompareResult.identical();
        when(operations.compare(any(), any())).thenReturn(expected);

        CompareResult result = facade.diff(null, null);

        ArgumentCaptor<List<?>> oldList = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> newList = ArgumentCaptor.forClass(List.class);
        verify(operations).compare(oldList.capture(), newList.capture());
        assertTrue(oldList.getValue().isEmpty());
        assertTrue(newList.getValue().isEmpty());
        assertSame(expected, result);
    }

    @Test
    void delegatesDefaultComparisonWithoutCreatingOptions() {
        List<String> oldList = Arrays.asList("a", "b");
        List<String> newList = Arrays.asList("a", "c");
        CompareResult expected = CompareResult.identical();
        when(operations.compare(oldList, newList)).thenReturn(expected);

        CompareResult result = facade.diff(oldList, newList);

        assertSame(expected, result);
        verify(operations).compare(oldList, newList);
    }

    @Test
    void passesExplicitOptionsUnchanged() {
        List<String> oldList = Arrays.asList("a", "b");
        List<String> newList = Arrays.asList("a", "c");
        CompareOptions options = CompareOptions.builder().computeSimilarity(true).build();
        CompareResult expected = CompareResult.identical();
        when(operations.compare(oldList, newList, options)).thenReturn(expected);

        CompareResult result = facade.diff(oldList, newList, options);

        assertSame(expected, result);
        verify(operations).compare(eq(oldList), eq(newList), eq(options));
    }

    @Test
    void nullOptionsUseRuntimeDefaults() {
        List<Integer> oldList = Arrays.asList(1, 2);
        List<Integer> newList = Arrays.asList(1, 3);
        CompareResult expected = CompareResult.identical();
        when(operations.compare(oldList, newList)).thenReturn(expected);

        CompareResult result = facade.diff(oldList, newList, null);

        assertNotNull(result);
        verify(operations).compare(oldList, newList);
    }
}
