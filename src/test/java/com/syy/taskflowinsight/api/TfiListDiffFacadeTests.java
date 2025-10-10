package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TfiListDiffFacade 单元测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class TfiListDiffFacadeTests {

    private ListCompareExecutor mockExecutor;
    private ChangeReportRenderer mockRenderer;
    private TfiListDiffFacade facade;

    @BeforeEach
    void setUp() {
        mockExecutor = mock(ListCompareExecutor.class);
        mockRenderer = mock(ChangeReportRenderer.class);
        facade = new TfiListDiffFacade(mockExecutor, mockRenderer);
    }

    @Test
    void testDiffWithNullLists() {
        // Given: null 列表
        CompareResult mockResult = CompareResult.builder()
                .identical(true)
                .changes(Collections.emptyList())
                .build();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用 diff
        CompareResult result = facade.diff(null, null);

        // Then: 应该将 null 转为空列表
        assertNotNull(result);
        ArgumentCaptor<List<?>> list1Captor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> list2Captor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<CompareOptions> optionsCaptor = ArgumentCaptor.forClass(CompareOptions.class);

        verify(mockExecutor).compare(list1Captor.capture(), list2Captor.capture(), optionsCaptor.capture());
        assertTrue(list1Captor.getValue().isEmpty(), "null oldList should be converted to empty list");
        assertTrue(list2Captor.getValue().isEmpty(), "null newList should be converted to empty list");
    }

    @Test
    void testDiffWithAutoStrategy() {
        // Given: 两个列表，不指定策略
        List<String> oldList = Arrays.asList("a", "b");
        List<String> newList = Arrays.asList("a", "c");
        CompareResult mockResult = CompareResult.builder()
                .identical(false)
                .build();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用基础 diff 方法
        CompareResult result = facade.diff(oldList, newList);

        // Then: 应该调用 executor.compare，且 options.strategyName 为 null（自动选择）
        assertNotNull(result);
        ArgumentCaptor<CompareOptions> optionsCaptor = ArgumentCaptor.forClass(CompareOptions.class);
        verify(mockExecutor).compare(eq(oldList), eq(newList), optionsCaptor.capture());
        assertNull(optionsCaptor.getValue().getStrategyName(), "Should use auto strategy selection");
    }

    @Test
    void testDiffWithExplicitStrategy() {
        // Given: 指定 ENTITY 策略
        List<String> oldList = Arrays.asList("a", "b");
        List<String> newList = Arrays.asList("a", "c");
        CompareResult mockResult = CompareResult.builder()
                .identical(false)
                .build();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用指定策略的 diff 方法
        CompareResult result = facade.diff(oldList, newList, "ENTITY");

        // Then: 应该传递策略名称
        assertNotNull(result);
        ArgumentCaptor<CompareOptions> optionsCaptor = ArgumentCaptor.forClass(CompareOptions.class);
        verify(mockExecutor).compare(eq(oldList), eq(newList), optionsCaptor.capture());
        assertEquals("ENTITY", optionsCaptor.getValue().getStrategyName());
    }

    @Test
    void testDiffWithNullStrategy() {
        // Given: 显式传递 null 策略
        List<String> oldList = Arrays.asList("a");
        List<String> newList = Arrays.asList("b");
        CompareResult mockResult = CompareResult.identical();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用 diff(list, list, null)
        CompareResult result = facade.diff(oldList, newList, (String) null);

        // Then: 应该使用自动策略
        assertNotNull(result);
        ArgumentCaptor<CompareOptions> optionsCaptor = ArgumentCaptor.forClass(CompareOptions.class);
        verify(mockExecutor).compare(eq(oldList), eq(newList), optionsCaptor.capture());
        assertNull(optionsCaptor.getValue().getStrategyName());
    }

    @Test
    void testDiffWithOptions() {
        // Given: 完整的 CompareOptions
        List<String> oldList = Arrays.asList("a", "b");
        List<String> newList = Arrays.asList("a", "c");
        CompareOptions options = CompareOptions.builder()
                .strategyName("SIMPLE")
                .calculateSimilarity(true)
                .enableDeepCompare(true)
                .build();
        CompareResult mockResult = CompareResult.builder()
                .identical(false)
                .similarity(0.5)
                .build();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用带完整选项的 diff 方法
        CompareResult result = facade.diff(oldList, newList, options);

        // Then: 应该原样传递选项
        assertNotNull(result);
        verify(mockExecutor).compare(eq(oldList), eq(newList), eq(options));
        assertEquals(0.5, result.getSimilarity());
    }

    @Test
    void testDiffWithNullOptions() {
        // Given: null options
        List<Integer> oldList = Arrays.asList(1, 2);
        List<Integer> newList = Arrays.asList(1, 3);
        CompareResult mockResult = CompareResult.identical();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用 diff(list, list, null)
        CompareResult result = facade.diff(oldList, newList, (CompareOptions) null);

        // Then: 应该使用默认选项（非 null）
        assertNotNull(result);
        ArgumentCaptor<CompareOptions> optionsCaptor = ArgumentCaptor.forClass(CompareOptions.class);
        verify(mockExecutor).compare(eq(oldList), eq(newList), optionsCaptor.capture());
        assertNotNull(optionsCaptor.getValue(), "null options should be converted to default options");
    }

    @Test
    void testDiffDelegatesCorrectly() {
        // Given: 真实场景数据
        List<String> oldList = Arrays.asList("apple", "banana");
        List<String> newList = Arrays.asList("apple", "cherry", "date");
        CompareResult expectedResult = CompareResult.builder()
                .object1(oldList)
                .object2(newList)
                .identical(false)
                .changes(Collections.emptyList())
                .build();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(expectedResult);

        // When: 调用 diff
        CompareResult actualResult = facade.diff(oldList, newList);

        // Then: 应该返回 executor 的结果
        assertSame(expectedResult, actualResult);
        verify(mockExecutor, times(1)).compare(any(), any(), any());
    }

    @Test
    void testDiffHandlesMixedNullParameters() {
        // Given: 一个 null 列表，一个非 null 列表
        List<String> oldList = null;
        List<String> newList = Arrays.asList("a", "b");
        CompareResult mockResult = CompareResult.identical();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        // When: 调用 diff
        CompareResult result = facade.diff(oldList, newList);

        // Then: null 列表转为空列表，非 null 保持原样
        assertNotNull(result);
        ArgumentCaptor<List<?>> list1Captor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> list2Captor = ArgumentCaptor.forClass(List.class);
        verify(mockExecutor).compare(list1Captor.capture(), list2Captor.capture(), any());
        assertTrue(list1Captor.getValue().isEmpty());
        assertSame(newList, list2Captor.getValue());
    }

    @Test
    void testMultipleStrategies() {
        // Given: 多个策略名称
        List<String> list = Arrays.asList("test");
        CompareResult mockResult = CompareResult.identical();
        when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);

        String[] strategies = {"SIMPLE", "ENTITY", "AS_SET", "LEVENSHTEIN"};

        for (String strategy : strategies) {
            // When: 使用不同策略调用 diff
            facade.diff(list, list, strategy);

            // Then: 应该传递正确的策略名称
            ArgumentCaptor<CompareOptions> captor = ArgumentCaptor.forClass(CompareOptions.class);
            verify(mockExecutor, atLeastOnce()).compare(any(), any(), captor.capture());
            assertEquals(strategy, captor.getValue().getStrategyName());
            reset(mockExecutor);
            when(mockExecutor.compare(any(), any(), any())).thenReturn(mockResult);
        }
    }
}