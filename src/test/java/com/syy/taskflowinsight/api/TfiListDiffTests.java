package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TfiListDiff 静态代理测试
 * <p>
 * 注意：此测试需要 Spring 容器启动，因为静态代理依赖 ApplicationContext
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
@SpringBootTest
class TfiListDiffTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void testStaticDiffWithBasicLists() {
        // Given: 两个简单列表
        List<String> oldList = Arrays.asList("a", "b", "c");
        List<String> newList = Arrays.asList("a", "b", "d");

        // When: 使用静态方法调用
        CompareResult result = TfiListDiff.diff(oldList, newList);

        // Then: 应该返回有效结果
        assertNotNull(result);
        assertNotNull(result.getObject1());
        assertNotNull(result.getObject2());
    }

    @Test
    void testStaticDiffWithStrategy() {
        // Given: 指定 SIMPLE 策略
        List<Integer> oldList = Arrays.asList(1, 2, 3);
        List<Integer> newList = Arrays.asList(1, 2, 4);

        // When: 使用静态方法指定策略
        CompareResult result = TfiListDiff.diff(oldList, newList, "SIMPLE");

        // Then: 应该返回有效结果
        assertNotNull(result);
    }

    @Test
    void testStaticDiffWithOptions() {
        // Given: 完整配置选项
        List<String> oldList = Arrays.asList("apple", "banana");
        List<String> newList = Arrays.asList("apple", "cherry");
        CompareOptions options = CompareOptions.builder()
                .strategyName("SIMPLE")
                .calculateSimilarity(true)
                .build();

        // When: 使用静态方法传递选项
        CompareResult result = TfiListDiff.diff(oldList, newList, options);

        // Then: 应该返回有效结果并计算相似度
        assertNotNull(result);
        assertNotNull(result.getSimilarity(), "Similarity should be calculated when requested");
    }

    @Test
    void testStaticDiffWithNullLists() {
        // Given: null 列表
        List<String> oldList = null;
        List<String> newList = null;

        // When: 使用静态方法
        CompareResult result = TfiListDiff.diff(oldList, newList);

        // Then: 应该处理 null 列表（转为空列表）
        assertNotNull(result);
        assertTrue(result.isIdentical(), "Two null lists should be considered identical");
    }

    @Test
    void testStaticDiffWithEmptyLists() {
        // Given: 空列表
        List<String> emptyList = Collections.emptyList();

        // When: 比较空列表
        CompareResult result = TfiListDiff.diff(emptyList, emptyList);

        // Then: 应该识别为相同
        assertNotNull(result);
        assertTrue(result.isIdentical(), "Two empty lists should be identical");
    }

    @Test
    void testStaticProxyDelegateToFacade() {
        // Given: 确认 Spring 容器包含 TfiListDiffFacade Bean
        assertTrue(context.containsBean("tfiListDiffFacade"),
                "TfiListDiffFacade should be registered as Spring Bean");
        TfiListDiffFacade facade = context.getBean(TfiListDiffFacade.class);
        assertNotNull(facade);

        // When & Then: 静态代理应该能正常工作
        List<String> list = Arrays.asList("test");
        CompareResult result = TfiListDiff.diff(list, list);
        assertNotNull(result);
    }

    @Test
    void testStaticDiffWithDifferentTypes() {
        // Given: 不同类型的列表元素
        List<Object> oldList = Arrays.asList(1, "two", 3.0);
        List<Object> newList = Arrays.asList(1, "TWO", 3.0);

        // When: 比较包含不同类型的列表
        CompareResult result = TfiListDiff.diff(oldList, newList);

        // Then: 应该检测到差异
        assertNotNull(result);
        assertFalse(result.isIdentical());
    }

    @Test
    void testMultipleStaticCalls() {
        // Given: 多次调用场景
        List<String> list1 = Arrays.asList("a", "b");
        List<String> list2 = Arrays.asList("c", "d");
        List<String> list3 = Arrays.asList("e", "f");

        // When: 连续调用多次
        CompareResult result1 = TfiListDiff.diff(list1, list2);
        CompareResult result2 = TfiListDiff.diff(list2, list3);
        CompareResult result3 = TfiListDiff.diff(list1, list3);

        // Then: 所有调用都应该成功
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
        assertNotSame(result1, result2, "Each call should return a new result");
    }
}