package com.syy.taskflowinsight.tracking.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * PriorityCalculator可调权重测试
 * 验证配置化的优先级计算逻辑
 */
@DisplayName("PriorityCalculator可调权重测试")
class PriorityCalculatorTest {
    
    private PathDeduplicationConfig config;
    private PriorityCalculator calculator;
    private Object testObject;
    
    @BeforeEach
    void setUp() {
        config = new PathDeduplicationConfig();
        calculator = new PriorityCalculator(config);
        testObject = new TestObject("test");
    }
    
    @Test
    @DisplayName("基础优先级计算：深度权重正确计算")
    void shouldCalculateDepthPriority() {
        // Given: 不同深度的路径候选
        PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("field", 1, PathArbiter.AccessType.FIELD, testObject);
        PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("obj.nested.field", 3, PathArbiter.AccessType.FIELD, testObject);
        
        // When: 计算优先级
        long shallowPriority = calculator.calculatePriority(shallow);
        long deepPriority = calculator.calculatePriority(deep);
        
        // Then: 深度更大的应该有更高优先级
        assertThat(deepPriority).isGreaterThan(shallowPriority);
        
        // 验证具体的深度权重计算
        long expectedDepthDiff = (deep.getDepth() - shallow.getDepth()) * config.getDepthWeight();
        assertThat(deepPriority - shallowPriority).isGreaterThanOrEqualTo(expectedDepthDiff);
    }
    
    @Test
    @DisplayName("访问类型权重：FIELD > MAP_KEY > ARRAY_INDEX > SET_ELEMENT")
    void shouldRespectAccessTypeWeights() {
        // Given: 相同深度，不同访问类型的候选
        PathArbiter.PathCandidate field = new PathArbiter.PathCandidate("obj.field", 2, PathArbiter.AccessType.FIELD, testObject);
        PathArbiter.PathCandidate mapKey = new PathArbiter.PathCandidate("obj[\"key\"]", 2, PathArbiter.AccessType.MAP_KEY, testObject);
        PathArbiter.PathCandidate arrayIndex = new PathArbiter.PathCandidate("obj[0]", 2, PathArbiter.AccessType.ARRAY_INDEX, testObject);
        PathArbiter.PathCandidate setElement = new PathArbiter.PathCandidate("obj[id=123]", 2, PathArbiter.AccessType.SET_ELEMENT, testObject);
        
        // When: 计算各自的优先级
        long fieldPriority = calculator.calculatePriority(field);
        long mapKeyPriority = calculator.calculatePriority(mapKey);
        long arrayIndexPriority = calculator.calculatePriority(arrayIndex);
        long setElementPriority = calculator.calculatePriority(setElement);
        
        // Then: 应该遵循访问类型优先级顺序
        assertThat(fieldPriority).isGreaterThan(mapKeyPriority);
        assertThat(mapKeyPriority).isGreaterThan(arrayIndexPriority);
        assertThat(arrayIndexPriority).isGreaterThan(setElementPriority);
    }
    
    @Test
    @DisplayName("字典序tie-break：相同深度和类型时选择字典序较小者")
    void shouldUseLexicalOrderForTieBreak() {
        // Given: 相同深度和访问类型的候选，不同路径名称
        PathArbiter.PathCandidate alpha = new PathArbiter.PathCandidate("obj.alpha", 2, PathArbiter.AccessType.FIELD, testObject);
        PathArbiter.PathCandidate zebra = new PathArbiter.PathCandidate("obj.zebra", 2, PathArbiter.AccessType.FIELD, testObject);

        // When: 使用比较器/选择器进行裁决（而非数值分比较）
        PathArbiter.PathCandidate selected = calculator.selectHighestPriority(Arrays.asList(alpha, zebra));

        // Then: 字典序较小的(alpha)应该被选中
        assertThat(selected.getPath()).isEqualTo("obj.alpha");
    }
    
    @Test
    @DisplayName("配置化权重：权重配置影响优先级计算")
    void shouldRespectConfigurableWeights() {
        // Given: 自定义权重配置
        PathDeduplicationConfig customConfig = new PathDeduplicationConfig();
        customConfig.setDepthWeight(2000);     // 双倍深度权重
        customConfig.setAccessTypeWeight(50);  // 减半访问类型权重
        PriorityCalculator customCalculator = new PriorityCalculator(customConfig);
        
        PathArbiter.PathCandidate candidate = new PathArbiter.PathCandidate("obj.field", 2, PathArbiter.AccessType.FIELD, testObject);
        
        // When: 分别用默认和自定义配置计算
        long defaultPriority = calculator.calculatePriority(candidate);
        long customPriority = customCalculator.calculatePriority(candidate);
        
        // Then: 应该产生不同的优先级分数
        assertThat(customPriority).isNotEqualTo(defaultPriority);
        
        // 验证配置详情
        assertThat(customCalculator.getConfig().getDepthWeight()).isEqualTo(2000);
        assertThat(customCalculator.getConfig().getAccessTypeWeight()).isEqualTo(50);
    }
    
    @Test
    @DisplayName("比较器生成：创建的比较器应该正确排序")
    void shouldCreateCorrectComparator() {
        // Given: 多个不同优先级的候选
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("obj[0]", 1, PathArbiter.AccessType.ARRAY_INDEX, testObject),      // 低优先级
            new PathArbiter.PathCandidate("obj.deep.field", 3, PathArbiter.AccessType.FIELD, testObject),   // 高优先级
            new PathArbiter.PathCandidate("obj[\"key\"]", 2, PathArbiter.AccessType.MAP_KEY, testObject)     // 中优先级
        );
        
        // When: 使用生成的比较器排序
        Comparator<PathArbiter.PathCandidate> comparator = calculator.createComparator();
        List<PathArbiter.PathCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort(comparator.reversed()); // 降序排列，高优先级在前
        
        // Then: 排序结果应该符合优先级顺序
        assertThat(sorted.get(0).getPath()).isEqualTo("obj.deep.field");  // 最高优先级
        assertThat(sorted.get(1).getPath()).isEqualTo("obj[\"key\"]");    // 中优先级
        assertThat(sorted.get(2).getPath()).isEqualTo("obj[0]");          // 最低优先级
    }
    
    @Test
    @DisplayName("批量排序：sortByPriority方法正确排序")
    void shouldSortCandidatesByPriority() {
        // Given: 随机顺序的候选列表
        List<PathArbiter.PathCandidate> randomOrder = Arrays.asList(
            new PathArbiter.PathCandidate("obj.zebra", 1, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("obj.alpha.deep", 3, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("obj.beta", 2, PathArbiter.AccessType.FIELD, testObject)
        );
        
        // When: 按优先级排序
        List<PathArbiter.PathCandidate> sorted = calculator.sortByPriority(randomOrder);
        
        // Then: 应该按优先级降序排列
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).getDepth()).isEqualTo(3); // 最深的优先
        assertThat(sorted.get(1).getDepth()).isEqualTo(2); // 其次
        assertThat(sorted.get(2).getDepth()).isEqualTo(1); // 最浅的最后
    }
    
    @Test
    @DisplayName("最高优先级选择：selectHighestPriority正确工作")
    void shouldSelectHighestPriorityCandidate() {
        // Given: 多个候选
        List<PathArbiter.PathCandidate> candidates = Arrays.asList(
            new PathArbiter.PathCandidate("obj[0]", 1, PathArbiter.AccessType.ARRAY_INDEX, testObject),
            new PathArbiter.PathCandidate("obj.field", 3, PathArbiter.AccessType.FIELD, testObject),
            new PathArbiter.PathCandidate("obj[\"key\"]", 2, PathArbiter.AccessType.MAP_KEY, testObject)
        );
        
        // When: 选择最高优先级
        PathArbiter.PathCandidate highest = calculator.selectHighestPriority(candidates);
        
        // Then: 应该选择深度最大且访问类型优先级最高的
        assertThat(highest).isNotNull();
        assertThat(highest.getPath()).isEqualTo("obj.field");
        assertThat(highest.getDepth()).isEqualTo(3);
        assertThat(highest.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
    }
    
    @Test
    @DisplayName("优先级调试信息：getPriorityDetails提供有用信息")
    void shouldProvideUsefulPriorityDetails() {
        // Given: 一个路径候选
        PathArbiter.PathCandidate candidate = new PathArbiter.PathCandidate("obj.nested.field", 3, PathArbiter.AccessType.FIELD, testObject);
        
        // When: 获取优先级详情
        String details = calculator.getPriorityDetails(candidate);
        
        // Then: 应该包含关键信息
        assertThat(details).contains("Priority{");
        assertThat(details).contains("total=");
        assertThat(details).contains("depth=3");
        assertThat(details).contains("path='obj.nested.field'");
        assertThat(details).contains("access=100*10000"); // FIELD的权重是100
    }
    
    @Test
    @DisplayName("一致性验证：多次计算相同候选应该得到相同结果")
    void shouldBeConsistentAcrossMultipleCalculations() {
        // Given: 一个候选
        PathArbiter.PathCandidate candidate = new PathArbiter.PathCandidate("test.path", 2, PathArbiter.AccessType.FIELD, testObject);
        
        // When: 验证一致性
        boolean isConsistent = calculator.verifyConsistency(candidate, 1000);
        
        // Then: 应该保持一致
        assertThat(isConsistent).isTrue();
    }
    
    @Test
    @DisplayName("边界情况：null和空列表处理")
    void shouldHandleNullAndEmptyCases() {
        // When & Then: null候选处理
        long nullPriority = calculator.calculatePriority(null);
        assertThat(nullPriority).isEqualTo(Long.MIN_VALUE);
        
        // When & Then: 空列表处理
        List<PathArbiter.PathCandidate> emptyList = calculator.sortByPriority(Collections.emptyList());
        assertThat(emptyList).isEmpty();
        
        PathArbiter.PathCandidate nullResult = calculator.selectHighestPriority(null);
        assertThat(nullResult).isNull();
    }
    
    // 测试用的简单对象
    private static class TestObject {
        private final String name;
        
        public TestObject(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return "TestObject{name='" + name + "'}";
        }
    }
}
