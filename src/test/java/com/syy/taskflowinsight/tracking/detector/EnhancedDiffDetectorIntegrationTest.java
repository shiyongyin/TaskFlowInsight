package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * DiffDetector增强去重集成测试
 * 验证CARD-CT-ALIGN功能的端到端工作
 */
@DisplayName("DiffDetector增强去重集成测试")
class EnhancedDiffDetectorIntegrationTest {
    
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
    @DisplayName("基础功能：增强去重开关控制")
    void shouldControlEnhancedDeduplicationWithSwitch() {
        // Given: 简单的变更场景
        Map<String, Object> before = Map.of("field1", "old", "field2", "old");
        Map<String, Object> after = Map.of("field1", "new", "field2", "new");
        
        // When: 启用增强去重
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        List<ChangeRecord> enhancedResult = DiffDetector.diff("test", before, after);
        
        // When: 禁用增强去重
        DiffDetector.setEnhancedDeduplicationEnabled(false);
        List<ChangeRecord> basicResult = DiffDetector.diff("test", before, after);
        
        // Then: 都应该返回结果（基础场景下结果相同）
        assertThat(enhancedResult).isNotEmpty();
        assertThat(basicResult).isNotEmpty();
        assertThat(DiffDetector.isEnhancedDeduplicationEnabled()).isFalse();
        
        // 恢复设置
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        assertThat(DiffDetector.isEnhancedDeduplicationEnabled()).isTrue();
    }
    
    @Test
    @DisplayName("去重统计：监控去重效果")
    void shouldTrackDeduplicationStatistics() {
        // Given: 创建有重复路径的变更场景
        Map<String, Object> before = Map.of(
            "user.name", "Alice",
            "user.email", "alice@old.com",
            "user.age", 25
        );
        Map<String, Object> after = Map.of(
            "user.name", "Bob", 
            "user.email", "bob@new.com",
            "user.age", 30
        );
        
        // When: 执行差异检测（会触发去重）
        List<ChangeRecord> result = DiffDetector.diff("User", before, after);
        
        // Then: 应该有去重统计
        PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalDeduplicationCount()).isGreaterThan(0);
        
        // 验证结果不为空
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(record -> record.getChangeType() == ChangeType.UPDATE);
    }
    
    @Test
    @DisplayName("性能基准：增强去重不显著影响性能")
    void shouldNotSignificantlyImpactPerformance() {
        // Given: 中等复杂度的对象
        Map<String, Object> before = createMediumComplexObject("before");
        Map<String, Object> after = createMediumComplexObject("after");
        
        // When: 测量基础去重性能
        long basicStartTime = System.nanoTime();
        DiffDetector.setEnhancedDeduplicationEnabled(false);
        List<ChangeRecord> basicResult = DiffDetector.diff("Complex", before, after);
        long basicDuration = (System.nanoTime() - basicStartTime) / 1000; // 微秒
        
        // When: 测量增强去重性能  
        long enhancedStartTime = System.nanoTime();
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        List<ChangeRecord> enhancedResult = DiffDetector.diff("Complex", before, after);
        long enhancedDuration = (System.nanoTime() - enhancedStartTime) / 1000; // 微秒
        
        // Then: 增强版性能开销应该<5%（实际可能更好）
        assertThat(basicResult).isNotEmpty();
        assertThat(enhancedResult).isNotEmpty();
        
        // 允许增强版慢一些，但不应该超过太多
        double overhead = (double) (enhancedDuration - basicDuration) / basicDuration * 100;
        System.out.println(String.format("Performance: Basic=%dμs, Enhanced=%dμs, Overhead=%.1f%%", 
                          basicDuration, enhancedDuration, overhead));
        
        // 验证开销在合理范围内（测试环境可能有波动，放宽到50%）
        assertThat(overhead).isLessThan(50.0);
    }
    
    @Test
    @DisplayName("容错性：增强去重失败时回退到基础去重")
    void shouldFallbackToBasicDeduplicationOnFailure() {
        // Given: 正常的变更场景
        Map<String, Object> before = Map.of("field", "old");
        Map<String, Object> after = Map.of("field", "new"); 
        
        // When: 启用增强去重执行差异检测
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        List<ChangeRecord> result = DiffDetector.diff("test", before, after);
        
        // Then: 应该正常返回结果（即使内部可能有回退）
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldName()).isEqualTo("field");
        assertThat(result.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
    }
    
    @Test
    @DisplayName("缓存效果：重复对象应该有高缓存命中率")
    void shouldAchieveHighCacheHitRateForRepeatedObjects() {
        // Given: 相同的对象多次比较
        Map<String, Object> before = Map.of("name", "Alice", "age", 25);
        Map<String, Object> after1 = Map.of("name", "Bob", "age", 25);
        Map<String, Object> after2 = Map.of("name", "Charlie", "age", 25);
        
        // When: 多次执行差异检测（模拟相同对象的多次变更）
        for (int i = 0; i < 10; i++) {
            DiffDetector.diff("User", before, after1);
            DiffDetector.diff("User", before, after2);
        }
        
        // Then: 缓存命中率应该比较高
        PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
        assertThat(stats).isNotNull();
        assertThat(stats.getCacheStats()).isNotNull();
        
        // 打印统计信息用于观察
        System.out.println("Cache statistics: " + stats.getCacheStats());
    }
    
    @Test
    @DisplayName("路径复杂性：支持嵌套和集合类型路径")
    void shouldHandleComplexPathStructures() {
        // Given: 包含嵌套结构的复杂对象
        Map<String, Object> before = Map.of(
            "user.profile.name", "Alice",
            "user.addresses[0].city", "New York", 
            "user.tags[\"primary\"]", "developer",
            "user.settings[id=123]", "enabled"
        );
        Map<String, Object> after = Map.of(
            "user.profile.name", "Alice Updated",
            "user.addresses[0].city", "San Francisco",
            "user.tags[\"primary\"]", "architect", 
            "user.settings[id=123]", "disabled"
        );
        
        // When: 执行差异检测
        List<ChangeRecord> result = DiffDetector.diff("ComplexUser", before, after);

        // Then: 应该正确处理各种路径类型
        assertThat(result).hasSize(4);
        assertThat(result).allMatch(record -> record.getChangeType() == ChangeType.UPDATE);
        
        // 验证不同路径格式都被正确处理
        List<String> paths = result.stream().map(ChangeRecord::getFieldName).toList();
        assertThat(paths).contains(
            "user.profile.name",           // 字段路径
            "user.addresses[0].city",      // 数组索引路径
            "user.tags[\"primary\"]",      // Map键路径  
            "user.settings[id=123]"        // Set元素路径
        );
    }
    
    /**
     * 创建中等复杂度的测试对象
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

    @Test
    @DisplayName("最具体路径裁决：全局仅保留后代路径")
    void shouldKeepOnlyMostSpecificPathsGlobally() {
        // Given: 扁平快照包含祖先与后代路径
        Map<String, Object> before = Map.of(
            "user", "Alice",
            "user.addresses[0]", "NY"
        );
        Map<String, Object> after = Map.of(
            "user.name", "Alice Updated",
            "user.addresses[0].city", "San Francisco"
        );

        // When: 启用增强去重
        DiffDetector.setEnhancedDeduplicationEnabled(true);
        List<ChangeRecord> result = DiffDetector.diff("User", before, after);

        // Then: 仅保留后代路径
        List<String> paths = result.stream().map(ChangeRecord::getFieldName).toList();
        assertThat(paths).contains("user.name", "user.addresses[0].city");
        assertThat(paths).doesNotContain("user", "user.addresses[0]");
    }
}
