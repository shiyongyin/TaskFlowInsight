package com.syy.taskflowinsight.tracking.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CollectionSummary单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("CollectionSummary单元测试")
public class CollectionSummaryTest {
    
    private CollectionSummary summary;
    
    @BeforeEach
    void setUp() {
        summary = new CollectionSummary();
        summary.setEnabled(true);
        summary.setMaxSize(100);
        summary.setMaxExamples(10);
    }
    
    @Test
    @DisplayName("小集合不触发摘要")
    void testSmallCollectionNoSummary() {
        List<String> smallList = Arrays.asList("a", "b", "c");
        
        assertThat(summary.shouldSummarize(smallList)).isFalse();
        
        SummaryInfo info = summary.summarize(smallList);
        assertThat(info.getSize()).isEqualTo(3);
        assertThat(info.getExamples()).hasSize(3);
    }
    
    @Test
    @DisplayName("大集合触发摘要")
    void testLargeCollectionSummary() {
        List<String> largeList = IntStream.range(0, 1000)
            .mapToObj(i -> "item-" + i)
            .collect(Collectors.toList());
        
        assertThat(summary.shouldSummarize(largeList)).isTrue();
        
        SummaryInfo info = summary.summarize(largeList);
        assertThat(info.getSize()).isEqualTo(1000);
        assertThat(info.getType()).isEqualTo("ArrayList");
        assertThat(info.getExamples()).hasSizeLessThanOrEqualTo(10);
        assertThat(info.isTruncated()).isTrue();
    }
    
    @Test
    @DisplayName("Set集合摘要")
    void testSetSummary() {
        Set<Integer> set = IntStream.range(0, 200)
            .boxed()
            .collect(Collectors.toSet());
        
        SummaryInfo info = summary.summarize(set);
        assertThat(info.getSize()).isEqualTo(200);
        assertThat(info.getFeatures()).contains("unique");
        assertThat(info.getExamples()).hasSizeLessThanOrEqualTo(10);
    }
    
    @Test
    @DisplayName("Map摘要")
    void testMapSummary() {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 150; i++) {
            map.put("key" + i, i);
        }
        
        SummaryInfo info = summary.summarize(map);
        assertThat(info.getSize()).isEqualTo(150);
        assertThat(info.getType()).isEqualTo("Map");
        assertThat(info.getMapExamples()).hasSizeLessThanOrEqualTo(10);
        assertThat(info.getKeyTypeDistribution()).containsKey(String.class);
        assertThat(info.getValueTypeDistribution()).containsKey(Integer.class);
    }
    
    @Test
    @DisplayName("数组摘要")
    void testArraySummary() {
        int[] array = IntStream.range(0, 500).toArray();
        
        SummaryInfo info = summary.summarize(array);
        assertThat(info.getSize()).isEqualTo(500);
        assertThat(info.getType()).isEqualTo("int[]");
        assertThat(info.getExamples()).hasSizeLessThanOrEqualTo(10);
        assertThat(info.getStatistics()).isNotNull();
        assertThat(info.getStatistics().getMin()).isEqualTo(0.0);
        assertThat(info.getStatistics().getMax()).isEqualTo(199.0); // 只处理前200个
    }
    
    @Test
    @DisplayName("敏感信息过滤")
    void testSensitiveDataFiltering() {
        Map<String, String> sensitiveMap = new HashMap<>();
        sensitiveMap.put("username", "alice");
        sensitiveMap.put("password", "secret123");
        sensitiveMap.put("email", "alice@example.com");
        sensitiveMap.put("token", "jwt-token-123");
        
        summary.setSensitiveWords(Arrays.asList("password", "token", "secret"));
        
        SummaryInfo info = summary.summarize(sensitiveMap);
        
        // 验证敏感信息被过滤
        assertThat(info.getMapExamples()).allSatisfy(entry -> {
            assertThat(entry.getKey()).doesNotContain("password", "token");
            if (entry.getKey().equals("password") || entry.getKey().equals("token")) {
                assertThat(entry.getValue()).isEqualTo("***MASKED***");
            }
        });
    }
    
    @Test
    @DisplayName("同质集合特征识别")
    void testHomogeneousCollection() {
        List<String> stringList = IntStream.range(0, 150)
            .mapToObj(i -> "string" + i)
            .collect(Collectors.toList());
        
        SummaryInfo info = summary.summarize(stringList);
        assertThat(info.getFeatures()).contains("homogeneous");
        assertThat(info.getTypeDistribution()).hasSize(1);
        assertThat(info.getTypeDistribution()).containsKey(String.class);
    }
    
    @Test
    @DisplayName("混合类型集合")
    void testMixedTypeCollection() {
        List<Object> mixedList = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            if (i % 3 == 0) {
                mixedList.add("string" + i);
            } else if (i % 3 == 1) {
                mixedList.add(i);
            } else {
                mixedList.add(i % 2 == 0);
            }
        }
        
        SummaryInfo info = summary.summarize(mixedList);
        assertThat(info.getTypeDistribution()).hasSize(3);
        assertThat(info.getTypeDistribution()).containsKeys(String.class, Integer.class, Boolean.class);
        assertThat(info.getFeatures()).doesNotContain("homogeneous");
    }
    
    @Test
    @DisplayName("数值集合统计")
    void testNumericStatistics() {
        List<Integer> numbers = IntStream.range(1, 201)  // 创建大于maxSize的集合以触发摘要
            .boxed()
            .collect(Collectors.toList());
        
        SummaryInfo info = summary.summarize(numbers);
        assertThat(info.getStatistics()).isNotNull();
        assertThat(info.getStatistics().getMin()).isEqualTo(1.0);
        assertThat(info.getStatistics().getMax()).isEqualTo(200.0);
        assertThat(info.getStatistics().getMean()).isEqualTo(100.5);
        assertThat(info.getStatistics().getMedian()).isEqualTo(100.5);
    }
    
    @Test
    @DisplayName("空集合处理")
    void testEmptyCollection() {
        List<String> emptyList = Collections.emptyList();
        
        SummaryInfo info = summary.summarize(emptyList);
        assertThat(info.getSize()).isEqualTo(0);
        assertThat(info.getExamples()).isEmpty();
    }
    
    @Test
    @DisplayName("null值处理")
    void testNullHandling() {
        assertThat(summary.shouldSummarize(null)).isFalse();
        
        SummaryInfo info = summary.summarize(null);
        assertThat(info.getType()).isEqualTo("empty");
        assertThat(info.getSize()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("摘要紧凑字符串表示")
    void testCompactStringRepresentation() {
        List<String> list = Arrays.asList("a", "b", "c", "d", "e");
        
        SummaryInfo info = summary.summarize(list);
        String compact = info.toCompactString();
        
        assertThat(compact).contains("[ArrayList size=5");
        assertThat(compact).contains("examples=[a, b, c]");
    }
    
    @Test
    @DisplayName("摘要转Map格式")
    void testSummaryToMap() {
        Set<Integer> set = IntStream.range(0, 150)
            .boxed()
            .collect(Collectors.toSet());
        
        SummaryInfo info = summary.summarize(set);
        Map<String, Object> map = info.toMap();
        
        assertThat(map).containsKeys("type", "size", "examples", "features", "timestamp");
        assertThat(map.get("size")).isEqualTo(150);
        assertThat((Set<String>) map.get("features")).contains("unique");
    }
}