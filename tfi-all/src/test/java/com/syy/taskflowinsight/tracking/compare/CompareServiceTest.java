package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompareService测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@SpringBootTest
@DisplayName("CompareService测试")
public class CompareServiceTest {
    
    @Autowired
    private CompareService compareService;
    
    @Nested
    @DisplayName("基础比较功能测试")
    class BasicCompareTests {
        
        @Test
        @DisplayName("相同对象比较")
        void testIdenticalObjects() {
            TestObject obj = new TestObject("test", 42);
            CompareResult result = compareService.compare(obj, obj);
            
            assertThat(result.isIdentical()).isTrue();
            assertThat(result.getChanges()).isEmpty();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }
        
        @Test
        @DisplayName("null对象比较")
        void testNullComparison() {
            TestObject obj = new TestObject("test", 42);
            
            CompareResult result1 = compareService.compare(null, obj);
            assertThat(result1.isIdentical()).isFalse();
            assertThat(result1.getSimilarity()).isEqualTo(0.0);
            
            CompareResult result2 = compareService.compare(obj, null);
            assertThat(result2.isIdentical()).isFalse();
            assertThat(result2.getSimilarity()).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("不同类型对象比较")
        void testDifferentTypes() {
            TestObject obj1 = new TestObject("test", 42);
            String obj2 = "test";
            
            CompareResult result = compareService.compare(obj1, obj2);
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getSimilarity()).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("简单对象字段变更")
        void testSimpleFieldChanges() {
            TestObject obj1 = new TestObject("Alice", 25);
            TestObject obj2 = new TestObject("Bob", 30);
            
            CompareResult result = compareService.compare(obj1, obj2);
            
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).hasSize(2);
            assertThat(result.getChanges())
                .extracting(FieldChange::getFieldName)
                .containsExactlyInAnyOrder("name", "value");
        }
    }
    
    @Nested
    @DisplayName("深度比较功能测试")
    class DeepCompareTests {
        
        @Test
        @DisplayName("嵌套对象深度比较")
        void testNestedObjectComparison() {
            ComplexObject obj1 = createComplexObject(1);
            ComplexObject obj2 = createComplexObject(2);
            
            CompareOptions options = CompareOptions.deep(5);
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
        }
        
        @Test
        @DisplayName("循环引用处理")
        void testCyclicReferenceHandling() {
            CyclicObject obj1 = new CyclicObject("obj1");
            CyclicObject ref1 = new CyclicObject("ref1");
            obj1.reference = ref1;
            ref1.reference = obj1;
            
            CyclicObject obj2 = new CyclicObject("obj2");
            CyclicObject ref2 = new CyclicObject("ref2");
            obj2.reference = ref2;
            ref2.reference = obj2;
            
            CompareOptions options = CompareOptions.deep(10);
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result).isNotNull();
            assertThat(result.getCompareTimeMs()).isLessThan(1000); // 应该快速完成
        }
        
        @Test
        @DisplayName("深度限制测试")
        void testMaxDepthLimit() {
            DeepObject obj1 = createDeepObject(20, "v1");
            DeepObject obj2 = createDeepObject(20, "v2");
            
            CompareOptions options = CompareOptions.deep(5);
            long startTime = System.currentTimeMillis();
            CompareResult result = compareService.compare(obj1, obj2, options);
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(result).isNotNull();
            assertThat(duration).isLessThan(100); // 深度限制应该防止过度遍历
        }
    }
    
    @Nested
    @DisplayName("批量比较功能测试")
    class BatchCompareTests {
        
        @Test
        @DisplayName("批量比较基本功能")
        void testBatchComparison() {
            List<Pair<Object, Object>> pairs = IntStream.range(0, 5)
                .<Pair<Object, Object>>mapToObj(i -> Pair.of(
                    (Object) new TestObject("obj" + i, i),
                    (Object) new TestObject("obj" + i, i + 1)
                ))
                .toList();
            
            List<CompareResult> results = compareService.compareBatch(pairs);
            
            assertThat(results).hasSize(5);
            assertThat(results).allMatch(r -> !r.isIdentical());
        }
        
        @Test
        @DisplayName("并行批量比较")
        void testParallelBatchComparison() {
            List<Pair<Object, Object>> pairs = IntStream.range(0, 50)
                .<Pair<Object, Object>>mapToObj(i -> Pair.of(
                    (Object) createComplexObject(i),
                    (Object) createComplexObject(i + 1)
                ))
                .toList();
            
            CompareOptions options = CompareOptions.builder()
                .parallelThreshold(10)
                .enableDeepCompare(true)
                .build();
            
            long startTime = System.currentTimeMillis();
            List<CompareResult> results = compareService.compareBatch(pairs, options);
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(results).hasSize(50);
            System.out.printf("Parallel batch comparison took %d ms\n", duration);
        }
    }
    
    @Nested
    @DisplayName("三方比较功能测试")
    class ThreeWayCompareTests {
        
        @Test
        @DisplayName("无冲突三方合并")
        void testNoConflictMerge() {
            TestObject base = new TestObject("base", 1);
            TestObject left = new TestObject("left", 1);  // 改了name
            TestObject right = new TestObject("base", 2); // 改了value
            
            MergeResult result = compareService.compareThreeWay(base, left, right);
            
            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getLeftChanges()).hasSize(1);
            assertThat(result.getRightChanges()).hasSize(1);
        }
        
        @Test
        @DisplayName("有冲突三方合并")
        void testConflictMerge() {
            TestObject base = new TestObject("base", 1);
            TestObject left = new TestObject("left", 2);
            TestObject right = new TestObject("right", 3);
            
            MergeResult result = compareService.compareThreeWay(base, left, right);
            
            assertThat(result.hasConflicts()).isTrue();
            assertThat(result.getConflicts()).isNotEmpty();
        }
    }
    
    @Nested
    @DisplayName("相似度计算测试")
    class SimilarityTests {
        
        @Test
        @DisplayName("完全相同相似度")
        void testIdenticalSimilarity() {
            TestObject obj = new TestObject("test", 42);
            
            CompareOptions options = CompareOptions.builder()
                .calculateSimilarity(true)
                .build();
            
            CompareResult result = compareService.compare(obj, obj, options);
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }
        
        @Test
        @DisplayName("部分相同相似度")
        void testPartialSimilarity() {
            TestObject obj1 = new TestObject("same", 1);
            TestObject obj2 = new TestObject("same", 2);
            
            CompareOptions options = CompareOptions.builder()
                .calculateSimilarity(true)
                .build();
            
            CompareResult result = compareService.compare(obj1, obj2, options);
            assertThat(result.getSimilarity()).isBetween(0.0, 1.0);
            assertThat(result.getSimilarity()).isGreaterThan(0.4); // 至少有一个字段相同
        }
    }
    
    @Nested
    @DisplayName("报告生成测试")
    class ReportGenerationTests {
        
        @Test
        @DisplayName("文本报告生成")
        void testTextReport() {
            TestObject obj1 = new TestObject("Alice", 25);
            TestObject obj2 = new TestObject("Bob", 30);
            
            CompareOptions options = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result.getReport()).isNotNull();
            assertThat(result.getReport()).contains("Change Report");
            assertThat(result.getReport()).contains("name");
            assertThat(result.getReport()).contains("value");
        }
        
        @Test
        @DisplayName("Markdown报告生成")
        void testMarkdownReport() {
            TestObject obj1 = new TestObject("Alice", 25);
            TestObject obj2 = new TestObject("Bob", 30);
            
            CompareOptions options = CompareOptions.withReport(ReportFormat.MARKDOWN);
            
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result.getReport()).isNotNull();
            assertThat(result.getReport()).contains("# Change Report");
            assertThat(result.getReport()).contains("|");
            assertThat(result.getReport()).contains("Total changes");
        }
        
        @Test
        @DisplayName("JSON报告生成")
        void testJsonReport() {
            TestObject obj1 = new TestObject("Alice", 25);
            TestObject obj2 = new TestObject("Bob", 30);
            
            CompareOptions options = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.JSON)
                .build();
            
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result.getReport()).isNotNull();
            assertThat(result.getReport()).contains("\"changes\"");
            assertThat(result.getReport()).contains("\"field\"");
            assertThat(result.getReport()).contains("\"total\"");
        }
    }
    
    @Nested
    @DisplayName("补丁生成测试")
    class PatchGenerationTests {
        
        @Test
        @DisplayName("JSON Patch生成")
        void testJsonPatchGeneration() {
            TestObject obj1 = new TestObject("Alice", 25);
            TestObject obj2 = new TestObject("Bob", 30);
            
            CompareOptions options = CompareOptions.builder()
                .generatePatch(true)
                .patchFormat(PatchFormat.JSON_PATCH)
                .build();
            
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result.getPatch()).isNotNull();
            assertThat(result.getPatch()).contains("\"op\"");
            assertThat(result.getPatch()).contains("\"path\"");
            assertThat(result.getPatch()).contains("\"value\"");
        }
        
        @Test
        @DisplayName("Merge Patch生成")
        void testMergePatchGeneration() {
            TestObject obj1 = new TestObject("Alice", 25);
            TestObject obj2 = new TestObject("Bob", 30);
            
            CompareOptions options = CompareOptions.builder()
                .generatePatch(true)
                .patchFormat(PatchFormat.MERGE_PATCH)
                .build();
            
            CompareResult result = compareService.compare(obj1, obj2, options);
            
            assertThat(result.getPatch()).isNotNull();
            assertThat(result.getPatch()).contains("name");
            assertThat(result.getPatch()).contains("value");
        }
    }
    
    @Nested
    @DisplayName("自定义策略测试")
    class CustomStrategyTests {
        
        @Test
        @DisplayName("集合比较策略")
        void testCollectionCompareStrategy() {
            List<String> list1 = Arrays.asList("a", "b", "c");
            List<String> list2 = Arrays.asList("b", "c", "d");
            
            CompareOptions options = CompareOptions.builder()
                .calculateSimilarity(true)
                .generateReport(true)
                .build();
            
            CompareResult result = compareService.compare(list1, list2, options);
            
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getSimilarity()).isBetween(0.4, 0.6); // 约50%相似
        }
        
        @Test
        @DisplayName("Map比较策略")
        void testMapCompareStrategy() {
            Map<String, Integer> map1 = new HashMap<>();
            map1.put("a", 1);
            map1.put("b", 2);
            
            Map<String, Integer> map2 = new HashMap<>();
            map2.put("a", 1);
            map2.put("b", 3);
            map2.put("c", 4);
            
            CompareResult result = compareService.compare(map1, map2);
            
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).hasSize(2); // b更新，c新增
        }
        
        @Test
        @DisplayName("日期比较策略")
        void testDateCompareStrategy() {
            Date date1 = new Date();
            Date date2 = new Date(date1.getTime() + 500); // 0.5秒后
            
            CompareOptions options = CompareOptions.builder()
                .calculateSimilarity(true)
                .build();
            
            CompareResult result = compareService.compare(date1, date2, options);
            
            // DateCompareStrategy有1秒容差，所以应该相同
            assertThat(result.isIdentical()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {
        
        @Test
        @DisplayName("简单对象比较性能")
        void testSimpleObjectPerformance() {
            TestObject obj1 = new TestObject("test", 1);
            TestObject obj2 = new TestObject("test", 2);
            
            // 预热
            for (int i = 0; i < 100; i++) {
                compareService.compare(obj1, obj2);
            }
            
            // 测试
            List<Long> durations = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                long start = System.nanoTime();
                compareService.compare(obj1, obj2);
                durations.add(System.nanoTime() - start);
            }
            
            Collections.sort(durations);
            double p95 = durations.get((int) (durations.size() * 0.95)) / 1_000_000.0;
            
            System.out.printf("Simple object comparison P95: %.2f ms\n", p95);
            assertThat(p95).isLessThan(1.0); // P95 < 1ms
        }
        
        @Test
        @DisplayName("复杂对象深度比较性能")
        void testComplexObjectPerformance() {
            ComplexObject obj1 = createComplexObject(1);
            ComplexObject obj2 = createComplexObject(2);
            CompareOptions options = CompareOptions.deep(10);
            
            // 预热
            for (int i = 0; i < 10; i++) {
                compareService.compare(obj1, obj2, options);
            }
            
            // 测试
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                compareService.compare(obj1, obj2, options);
            }
            long duration = System.currentTimeMillis() - start;
            
            double avgMs = duration / 100.0;
            System.out.printf("Complex object deep comparison avg: %.2f ms\n", avgMs);
            assertThat(avgMs).isLessThan(10.0); // 平均 < 10ms
        }
    }
    
    // ========== 测试辅助类 ==========
    
    static class TestObject {
        String name;
        Integer value;
        
        TestObject(String name, Integer value) {
            this.name = name;
            this.value = value;
        }
    }
    
    static class ComplexObject {
        int id;
        String name;
        List<String> tags;
        Map<String, Object> metadata;
        ComplexObject nested;
        
        ComplexObject(int id) {
            this.id = id;
            this.name = "complex-" + id;
            this.tags = Arrays.asList("tag1", "tag2", "tag3");
            this.metadata = new HashMap<>();
            this.metadata.put("created", System.currentTimeMillis());
            this.metadata.put("version", "1.0");
        }
    }
    
    static class CyclicObject {
        String name;
        CyclicObject reference;
        
        CyclicObject(String name) {
            this.name = name;
        }
    }
    
    static class DeepObject {
        String value;
        DeepObject child;
        
        DeepObject(String value) {
            this.value = value;
        }
    }
    
    private ComplexObject createComplexObject(int id) {
        ComplexObject obj = new ComplexObject(id);
        obj.nested = new ComplexObject(id + 1000);
        return obj;
    }
    
    private DeepObject createDeepObject(int depth, String value) {
        DeepObject obj = new DeepObject(value);
        if (depth > 0) {
            obj.child = createDeepObject(depth - 1, value + "-child");
        }
        return obj;
    }
}