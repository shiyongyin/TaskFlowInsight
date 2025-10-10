package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TfiListDiff API 集成测试
 * <p>
 * 验证 TfiListDiffFacade 与 ListCompareExecutor 的端到端集成
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
@SpringBootTest
class TfiListDiffIntegrationTest {

    @Autowired
    private TfiListDiffFacade facade;

    @Autowired
    private ListCompareExecutor executor;

    @Entity
    static class TestUser {
        @Key
        private Long id;
        private String name;

        public TestUser(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Test
    void testFacadeBeanRegistration() {
        // Given & When: Spring 容器启动
        // Then: TfiListDiffFacade 应该被注册为 Bean
        assertNotNull(facade, "TfiListDiffFacade should be auto-wired");
        assertNotNull(executor, "ListCompareExecutor should be auto-wired");
    }

    @Test
    void testEndToEndSimpleListComparison() {
        // Given: 两个简单字符串列表
        List<String> oldList = Arrays.asList("apple", "banana", "cherry");
        List<String> newList = Arrays.asList("apple", "banana", "date");

        // When: 通过 facade 比较
        CompareResult result = facade.diff(oldList, newList);

        // Then: 应该检测到差异
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertEquals(oldList, result.getObject1());
        assertEquals(newList, result.getObject2());
    }

    @Test
    void testEndToEndEntityListAutoRouting() {
        // Given: 包含 @Entity 注解的对象列表
        List<TestUser> oldList = Arrays.asList(
                new TestUser(1L, "Alice"),
                new TestUser(2L, "Bob")
        );
        List<TestUser> newList = Arrays.asList(
                new TestUser(1L, "Alice"),
                new TestUser(2L, "Bobby"),  // 名称变更
                new TestUser(3L, "Charlie")  // 新增
        );

        // When: 使用自动策略（应该路由到 ENTITY 策略）
        CompareResult result = facade.diff(oldList, newList);

        // Then: 应该检测到变更
        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertTrue(result.hasChanges(), "Should detect changes in entity list");
    }

    @Test
    void testEndToEndWithExplicitStrategy() {
        // Given: 数字列表
        List<Integer> oldList = Arrays.asList(1, 2, 3, 4, 5);
        List<Integer> newList = Arrays.asList(1, 2, 4, 5, 6);

        // When: 显式指定 SIMPLE 策略
        CompareResult result = facade.diff(oldList, newList, "SIMPLE");

        // Then: 应该使用 SIMPLE 策略进行比较
        assertNotNull(result);
        assertFalse(result.isIdentical());
    }

    @Test
    void testEndToEndWithCompareOptions() {
        // Given: 列表和选项
        List<String> oldList = Arrays.asList("a", "b", "c");
        List<String> newList = Arrays.asList("a", "b", "d");
        CompareOptions options = CompareOptions.builder()
                .strategyName("SIMPLE")
                .calculateSimilarity(true)
                .build();

        // When: 使用完整配置
        CompareResult result = facade.diff(oldList, newList, options);

        // Then: 应该按配置执行
        assertNotNull(result);
        assertNotNull(result.getSimilarity(), "Similarity should be calculated");
        assertTrue(result.getSimilarity() > 0 && result.getSimilarity() < 1,
                "Similarity should reflect partial match");
    }

    @Test
    void testNullListsHandling() {
        // Given: null 列表
        // When: 通过 facade 比较
        CompareResult result = facade.diff(null, null);

        // Then: 应该视为相同
        assertNotNull(result);
        assertTrue(result.isIdentical(), "Two null lists should be identical");
    }

    @Test
    void testEmptyListsComparison() {
        // Given: 空列表
        List<String> emptyList = Collections.emptyList();

        // When: 比较两个空列表
        CompareResult result = facade.diff(emptyList, emptyList);

        // Then: 应该识别为相同
        assertNotNull(result);
        assertTrue(result.isIdentical());
        assertEquals(0, result.getChangeCount());
    }

    @Test
    void testMixedNullAndEmptyLists() {
        // Given: 一个 null，一个空列表
        List<String> emptyList = Collections.emptyList();

        // When: 比较 null 和空列表
        CompareResult result = facade.diff(null, emptyList);

        // Then: 应该视为相同（null 转为空列表）
        assertNotNull(result);
        assertTrue(result.isIdentical());
    }

    @Test
    void testStaticProxyIntegration() {
        // Given: 使用静态代理入口
        List<String> oldList = Arrays.asList("static", "test");
        List<String> newList = Arrays.asList("static", "integration");

        // When: 通过静态方法调用
        CompareResult result = TfiListDiff.diff(oldList, newList);

        // Then: 应该正常工作（委托给 facade）
        assertNotNull(result);
        assertFalse(result.isIdentical());
    }

    @Test
    void testPerformanceWithLargeLists() {
        // Given: 较大的列表（1000个元素）
        List<Integer> oldList = generateList(0, 1000);
        List<Integer> newList = generateList(1, 1001);  // 偏移1个元素

        // When: 进行比较
        long startTime = System.currentTimeMillis();
        CompareResult result = facade.diff(oldList, newList);
        long duration = System.currentTimeMillis() - startTime;

        // Then: 应该在合理时间内完成（< 5秒）
        assertNotNull(result);
        assertTrue(duration < 5000, "Large list comparison should complete in < 5s, actual: " + duration + "ms");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Given: 多个线程同时访问
        List<String> list1 = Arrays.asList("thread", "test");
        List<String> list2 = Arrays.asList("thread", "concurrent");

        // When: 并发调用
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                CompareResult result = facade.diff(list1, list2);
                assertNotNull(result);
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join(1000);
        }

        // Then: 所有调用都应该成功（无异常）
        for (Thread thread : threads) {
            assertFalse(thread.isAlive(), "All threads should complete");
        }
    }

    /**
     * 生成整数列表
     */
    private List<Integer> generateList(int start, int end) {
        Integer[] array = new Integer[end - start];
        for (int i = 0; i < array.length; i++) {
            array[i] = start + i;
        }
        return Arrays.asList(array);
    }
}