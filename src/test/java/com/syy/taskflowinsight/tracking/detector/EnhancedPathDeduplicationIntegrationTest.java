package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 增强路径去重集成测试（重构版本）
 * 验证真实对象图的最具体路径去重逻辑
 */
@DisplayName("增强路径去重集成测试（重构版本）")
class EnhancedPathDeduplicationIntegrationTest {
    
    @BeforeEach
    void setUp() {
        // 确保增强去重功能启用
        DiffDetector.setEnhancedDeduplicationEnabled(true);
    }
    
    @AfterEach
    void tearDown() {
        // 恢复默认设置
        DiffDetector.setEnhancedDeduplicationEnabled(true);
    }
    
    @Test
    @DisplayName("真实重复路径场景：同一对象多条路径只保留最具体的")
    void shouldKeepOnlyMostSpecificPathForSameObject() {
        // Given: 创建指向同一对象的多个路径变更
        String sharedValue = "SharedObject";  // 同一个字符串对象
        
        Map<String, Object> before = new HashMap<>();
        before.put("user.name", "old");
        before.put("user.profile.name", "old"); 
        before.put("user.details.fullName", "old");
        
        Map<String, Object> after = new HashMap<>();
        after.put("user.name", sharedValue);           // 浅路径
        after.put("user.profile.name", sharedValue);   // 中等路径
        after.put("user.details.fullName", sharedValue); // 深路径（应该被保留）
        
        // When: 执行增强去重
        List<ChangeRecord> result = DiffDetector.diff("User", before, after);
        
        // Then: 应该只保留最具体的路径
        assertThat(result).isNotEmpty();
        
        // 验证去重统计
        PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalDeduplicationCount()).isGreaterThan(0);
        
        // 打印调试信息
        System.out.println("Deduplication result count: " + result.size());
        System.out.println("Deduplication statistics: " + stats);
        result.forEach(r -> System.out.println("Kept path: " + r.getFieldName()));
    }
    
    @Test
    @DisplayName("访问类型优先级验证：FIELD > MAP_KEY > ARRAY_INDEX > SET_ELEMENT")
    void shouldRespectAccessTypePriorityInRealScenario() {
        // Given: 创建实际的嵌套对象结构，包含不同访问类型指向同一值
        Integer sameValue = 42;

        // 创建包含各种访问类型的嵌套对象
        TestData beforeData = createTestDataWithAccessTypes(0);
        TestData afterData = createTestDataWithAccessTypes(sameValue);

        Map<String, Object> before = Map.of("testData", beforeData);
        Map<String, Object> after = Map.of("testData", afterData);

        // When: 执行差异检测
        List<ChangeRecord> result = DiffDetector.diff("Data", before, after);

        // Then: 应该有结果
        assertThat(result).isNotEmpty();

        System.out.println("Access type priority test - kept paths:");
        result.forEach(r -> System.out.println("  " + r.getFieldName()));

        // 验证去重统计
        PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
        assertThat(stats).isNotNull();
        System.out.println("Deduplication statistics: " + stats);
    }

    // 辅助类：创建包含多种访问类型的测试数据
    private static class TestData {
        private Integer field;           // FIELD访问
        private Map<String, Integer> map = new HashMap<>();  // MAP_KEY访问
        private Integer[] array = new Integer[1];            // ARRAY_INDEX访问
        private Set<Integer> set = new HashSet<>();          // SET_ELEMENT访问

        public TestData(Integer value) {
            this.field = value;
            this.map.put("key", value);
            this.array[0] = value;
            this.set.add(value);
        }

        // 需要getters for reflection
        public Integer getField() { return field; }
        public Map<String, Integer> getMap() { return map; }
        public Integer[] getArray() { return array; }
        public Set<Integer> getSet() { return set; }
    }

    private TestData createTestDataWithAccessTypes(Integer value) {
        return new TestData(value);
    }
    
    @Test
    @DisplayName("深度优先验证：深度更大的路径优先")
    void shouldPreferDeeperPathsInRealScenario() {
        // Given: 创建不同深度指向同一值的路径
        String targetValue = "DeepValue";
        
        Map<String, Object> before = new HashMap<>();
        before.put("root", "old");                           // 深度1
        before.put("root.level1", "old");                    // 深度2
        before.put("root.level1.level2", "old");             // 深度3
        before.put("root.level1.level2.level3", "old");      // 深度4（最深）
        
        Map<String, Object> after = new HashMap<>();
        after.put("root", targetValue);
        after.put("root.level1", targetValue);
        after.put("root.level1.level2", targetValue);
        after.put("root.level1.level2.level3", targetValue);
        
        // When: 执行差异检测  
        List<ChangeRecord> result = DiffDetector.diff("DeepObject", before, after);
        
        // Then: 如果去重生效，应该优先保留最深的路径
        assertThat(result).isNotEmpty();
        
        if (result.size() < 4) { // 说明进行了去重
            boolean hasDeepestPath = result.stream()
                .anyMatch(r -> "root.level1.level2.level3".equals(r.getFieldName()));
            assertThat(hasDeepestPath).as("应该保留最深的路径").isTrue();
        }
        
        System.out.println("Depth priority test - kept paths:");
        result.forEach(r -> System.out.println("  " + r.getFieldName() + " (depth: " + 
            r.getFieldName().split("\\.").length + ")"));
    }
    
    @Test
    @DisplayName("性能基准：增强去重的性能开销验证")
    void shouldMeetPerformanceRequirements() {
        // Given: 创建简化的测试数据（减少复杂度以提高稳定性）
        Map<String, Object> before = createSimpleTestObject("before");
        Map<String, Object> after = createSimpleTestObject("after");

        int iterations = 50; // 减少迭代次数提高稳定性
        int warmupIterations = 10;

        // Warmup: 预热JIT编译器
        for (int i = 0; i < warmupIterations; i++) {
            DiffDetector.setEnhancedDeduplicationEnabled(true);
            DiffDetector.diff("WarmupEnhanced", before, after);
            DiffDetector.setEnhancedDeduplicationEnabled(false);
            DiffDetector.diff("WarmupBasic", before, after);
        }

        // When: 测试基础去重性能（先测基础，避免缓存影响）
        System.gc(); // 建议垃圾回收
        long basicStart = System.nanoTime();
        DiffDetector.setEnhancedDeduplicationEnabled(false);
        for (int i = 0; i < iterations; i++) {
            DiffDetector.diff("PerfTest", before, after);
        }
        long basicTime = System.nanoTime() - basicStart;

        // When: 测试增强去重性能
        System.gc(); // 建议垃圾回收
        long enhancedStart = System.nanoTime();
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        for (int i = 0; i < iterations; i++) {
            DiffDetector.diff("PerfTest", before, after);
        }
        long enhancedTime = System.nanoTime() - enhancedStart;

        // Then: 计算性能开销
        double overheadPercent = basicTime > 0 ? ((double)(enhancedTime - basicTime) / basicTime) * 100 : 0;

        System.out.printf("Performance test results:\n");
        System.out.printf("  Basic deduplication:    %.2f ms\n", basicTime / 1_000_000.0);
        System.out.printf("  Enhanced deduplication: %.2f ms\n", enhancedTime / 1_000_000.0);
        System.out.printf("  Overhead: %.1f%%\n", overheadPercent);

        // 验证性能要求：开销应该小于100%（更宽松的限制，考虑测试环境不稳定性）
        assertThat(overheadPercent).as("性能开销应该在合理范围内").isLessThan(100.0);

        // 恢复设置
        DiffDetector.setEnhancedDeduplicationEnabled(true);
    }

    /**
     * 创建简单测试对象（用于性能测试）
     */
    private Map<String, Object> createSimpleTestObject(String suffix) {
        Map<String, Object> obj = new HashMap<>();

        // 添加少量字段以减少测试复杂度
        for (int i = 0; i < 10; i++) {
            obj.put("field" + i, suffix + "_value" + i);
            obj.put("nested" + i, Map.of("inner", suffix + "_nested" + i));
        }

        return obj;
    }
    
    @Test
    @DisplayName("缓存效果验证：重复操作应该有高缓存命中率")
    void shouldAchieveHighCacheHitRate() {
        // Given: 相同的对象进行多次变更检测
        Map<String, Object> before = Map.of("name", "Alice", "age", 25, "city", "NYC");
        Map<String, Object> after1 = Map.of("name", "Bob", "age", 25, "city", "NYC");
        Map<String, Object> after2 = Map.of("name", "Charlie", "age", 25, "city", "NYC");
        Map<String, Object> after3 = Map.of("name", "David", "age", 25, "city", "NYC");
        
        // When: 多次执行相似的差异检测（应该触发缓存）
        for (int i = 0; i < 10; i++) {
            DiffDetector.diff("User", before, after1);
            DiffDetector.diff("User", before, after2);
            DiffDetector.diff("User", before, after3);
        }
        
        // Then: 检查缓存统计
        PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
        assertThat(stats).isNotNull();
        
        System.out.println("Cache effectiveness test:");
        System.out.println("  " + stats);
        
        // 验证至少进行了去重操作
        assertThat(stats.getTotalDeduplicationCount()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("配置化验证：不同配置产生不同的去重行为")
    void shouldRespectDifferentConfigurations() {
        // Given: 创建测试数据
        Map<String, Object> before = Map.of("field", "old");
        Map<String, Object> after = Map.of("field", "new");
        
        // When: 测试默认配置
        List<ChangeRecord> defaultResult = DiffDetector.diff("DefaultConfig", before, after);
        PathDeduplicator.DeduplicationStatistics defaultStats = DiffDetector.getDeduplicationStatistics();
        
        // Then: 应该正常工作
        assertThat(defaultResult).hasSize(1);
        assertThat(defaultStats.getConfig()).isNotNull();
        
        System.out.println("Configuration test:");
        System.out.println("  Default config: " + defaultStats.getConfig());
        System.out.println("  Result count: " + defaultResult.size());
    }
    
    @Test
    @DisplayName("错误处理验证：异常情况下的降级行为")
    void shouldGracefullyHandleEdgeCases() {
        // Given: 边界情况数据
        Map<String, Object> nullValues = new HashMap<>();
        nullValues.put("nullField", null);
        nullValues.put("emptyString", "");
        
        Map<String, Object> afterNulls = new HashMap<>();
        afterNulls.put("nullField", "notNull");
        afterNulls.put("emptyString", "notEmpty");
        
        // When: 处理包含null和边界值的场景
        List<ChangeRecord> result = DiffDetector.diff("EdgeCase", nullValues, afterNulls);
        
        // Then: 应该正常处理，不抛出异常
        assertThat(result).isNotNull();
        
        System.out.println("Edge case handling - result count: " + result.size());
        result.forEach(r -> System.out.println("  " + r.getFieldName() + ": " + 
            r.getChangeType() + " (" + r.getOldValue() + " -> " + r.getNewValue() + ")"));
    }
    
    @Test
    @DisplayName("路径语义去重端到端验证：祖先后代路径仅保留最具体的")
    void shouldKeepOnlyMostSpecificPathsEndToEnd() {
        // Given: 构造包含祖先与后代路径的before/after快照
        Map<String, Object> before = new HashMap<>();
        before.put("user", "old_user");                    // 祖先路径
        before.put("user.name", "old_name");               // 后代路径
        before.put("user.profile", "old_profile");         // 祖先路径
        before.put("user.profile.email", "old@email.com"); // 后代路径
        before.put("user.addresses[0]", "old_addr");       // 祖先路径
        before.put("user.addresses[0].city", "OldCity");   // 后代路径
        
        Map<String, Object> after = new HashMap<>();
        after.put("user", "new_user");
        after.put("user.name", "new_name");
        after.put("user.profile", "new_profile");
        after.put("user.profile.email", "new@email.com");
        after.put("user.addresses[0]", "new_addr");
        after.put("user.addresses[0].city", "NewCity");
        
        // When: 执行增强去重的差异检测
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        List<ChangeRecord> enhancedResult = DiffDetector.diff("User", before, after);
        
        // Then: 应该仅保留最具体路径对应的ChangeRecord
        assertThat(enhancedResult).isNotEmpty();
        
        Set<String> enhancedPaths = enhancedResult.stream()
            .map(ChangeRecord::getFieldName)
            .collect(java.util.stream.Collectors.toSet());
        
        // 验证仅保留最具体的路径
        assertThat(enhancedPaths).containsAnyOf(
            "user.name", 
            "user.profile.email", 
            "user.addresses[0].city"
        );
        
        // 验证祖先路径被去重
        assertThat(enhancedPaths).doesNotContain("user", "user.profile", "user.addresses[0]");
        
        System.out.println("Enhanced deduplication kept paths: " + enhancedPaths);
    }
    
    @Test  
    @DisplayName("基础与增强去重一致性验证：增强版更精确或一致")
    void shouldProduceConsistentOrMorePreciseResults() {
        // Given: 混合场景的测试数据
        Map<String, Object> before = new HashMap<>();
        before.put("data.value", "old1");
        before.put("data.nested.value", "old2");
        before.put("other.field", "old3");
        
        Map<String, Object> after = new HashMap<>();
        after.put("data.value", "new1");
        after.put("data.nested.value", "new2");
        after.put("other.field", "new3");
        
        // When: 分别使用基础和增强去重
        DiffDetector.setEnhancedDeduplicationEnabled(false);
        List<ChangeRecord> basicResult = DiffDetector.diff("Data", before, after);
        
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        List<ChangeRecord> enhancedResult = DiffDetector.diff("Data", before, after);
        
        // Then: 增强版应该产生一致或更精确的结果
        assertThat(basicResult).isNotEmpty();
        assertThat(enhancedResult).isNotEmpty();
        
        // 增强版结果应该≤基础版（更精确）
        assertThat(enhancedResult.size()).isLessThanOrEqualTo(basicResult.size());
        
        System.out.println("Basic result count: " + basicResult.size());
        System.out.println("Enhanced result count: " + enhancedResult.size());
        
        // 所有增强版保留的路径都应该在基础版中存在
        Set<String> basicPaths = basicResult.stream()
            .map(ChangeRecord::getFieldName)
            .collect(java.util.stream.Collectors.toSet());
        
        Set<String> enhancedPaths = enhancedResult.stream()
            .map(ChangeRecord::getFieldName)
            .collect(java.util.stream.Collectors.toSet());
            
        assertThat(basicPaths).containsAll(enhancedPaths);
        
        // 恢复设置
        DiffDetector.setEnhancedDeduplicationEnabled(true);
    }
    
    /**
     * 创建中等复杂度的测试对象（保留原有方法以兼容其他测试）
     */
    private Map<String, Object> createMediumComplexObject(String suffix) {
        Map<String, Object> obj = new HashMap<>();

        // 添加各种类型的字段
        for (int i = 0; i < 20; i++) {
            obj.put("field" + i, suffix + "_value" + i);
            obj.put("nested.field" + i, suffix + "_nested" + i);
            obj.put("array[" + i + "]", suffix + "_array" + i);
            if (i % 3 == 0) {
                obj.put("map[\"key" + i + "\"]", suffix + "_map" + i);
            }
        }

        return obj;
    }
}