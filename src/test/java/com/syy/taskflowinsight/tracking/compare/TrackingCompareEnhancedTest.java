package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Enhanced tests targeting low-coverage classes in tracking.compare package
 * Focus: MapCompareStrategy (31%), Pair (42%), MergeResult (58%), CollectionCompareStrategy (63%)
 */
class TrackingCompareEnhancedTest {

    private MapCompareStrategy mapStrategy;
    private CollectionCompareStrategy collectionStrategy;
    private CompareOptions options;

    @BeforeEach
    void setUp() {
        mapStrategy = new MapCompareStrategy();
        collectionStrategy = new CollectionCompareStrategy();
        options = CompareOptions.builder().build();
    }

    // ========== MapCompareStrategy Tests (提升31%→80%+) ==========

    @Test
    @DisplayName("MapCompareStrategy - 基本功能")
    void mapCompareStrategy_basicFunctionality() {
        assertThat(mapStrategy.getName()).isEqualTo("MapCompare");
        assertThat(mapStrategy.supports(Map.class)).isTrue();
        assertThat(mapStrategy.supports(HashMap.class)).isTrue();
        assertThat(mapStrategy.supports(List.class)).isFalse();
    }

    @Test
    @DisplayName("MapCompareStrategy - 相同对象引用")
    void mapCompareStrategy_sameReference() {
        Map<String, String> map = Map.of("key", "value");
        
        CompareResult result = mapStrategy.compare(map, map, options);
        
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.getChanges()).isEmpty();
    }

    @Test
    @DisplayName("MapCompareStrategy - null值比较")
    void mapCompareStrategy_nullComparison() {
        Map<String, String> map = Map.of("key", "value");
        
        // 第一个为null
        CompareResult result1 = mapStrategy.compare(null, map, options);
        assertThat(result1.isIdentical()).isFalse();
        
        // 第二个为null
        CompareResult result2 = mapStrategy.compare(map, null, options);
        assertThat(result2.isIdentical()).isFalse();
        
        // 都为null
        CompareResult result3 = mapStrategy.compare(null, null, options);
        assertThat(result3.isIdentical()).isTrue();
    }

    @Test
    @DisplayName("MapCompareStrategy - 键值变更检测")
    void mapCompareStrategy_keyValueChanges() {
        Map<String, String> map1 = Map.of("key1", "value1", "key2", "value2");
        Map<String, String> map2 = Map.of("key1", "changed", "key3", "value3");
        
        CompareResult result = mapStrategy.compare(map1, map2, options);
        
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.getChanges()).hasSize(3); // UPDATE, DELETE, CREATE
        
        // 验证变更类型
        List<FieldChange> changes = result.getChanges();
        assertThat(changes).extracting(FieldChange::getChangeType)
            .containsExactlyInAnyOrder(ChangeType.UPDATE, ChangeType.DELETE, ChangeType.CREATE);
    }

    @Test
    @DisplayName("MapCompareStrategy - 创建变更")
    void mapCompareStrategy_createChange() {
        Map<String, String> map1 = Map.of("key1", "value1");
        Map<String, String> map2 = Map.of("key1", "value1", "key2", "value2");
        
        CompareResult result = mapStrategy.compare(map1, map2, options);
        
        assertThat(result.getChanges()).hasSize(1);
        FieldChange change = result.getChanges().get(0);
        assertThat(change.getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(change.getFieldName()).isEqualTo("key2");
        assertThat(change.getOldValue()).isNull();
        assertThat(change.getNewValue()).isEqualTo("value2");
    }

    @Test
    @DisplayName("MapCompareStrategy - 删除变更")
    void mapCompareStrategy_deleteChange() {
        Map<String, String> map1 = Map.of("key1", "value1", "key2", "value2");
        Map<String, String> map2 = Map.of("key1", "value1");
        
        CompareResult result = mapStrategy.compare(map1, map2, options);
        
        assertThat(result.getChanges()).hasSize(1);
        FieldChange change = result.getChanges().get(0);
        assertThat(change.getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(change.getFieldName()).isEqualTo("key2");
        assertThat(change.getOldValue()).isEqualTo("value2");
        assertThat(change.getNewValue()).isNull();
    }

    @Test
    @DisplayName("MapCompareStrategy - 相似度计算")
    void mapCompareStrategy_similarityCalculation() {
        options.setCalculateSimilarity(true);
        
        // 完全相同
        Map<String, String> map1 = Map.of("key1", "value1", "key2", "value2");
        Map<String, String> map2 = Map.of("key1", "value1", "key2", "value2");
        CompareResult result1 = mapStrategy.compare(map1, map2, options);
        assertThat(result1.getSimilarity()).isEqualTo(1.0);
        
        // 部分相同
        Map<String, String> map3 = Map.of("key1", "value1", "key2", "changed");
        CompareResult result2 = mapStrategy.compare(map1, map3, options);
        assertThat(result2.getSimilarity()).isEqualTo(0.5); // 1/2键相同
        
        // 空Map
        CompareResult result3 = mapStrategy.compare(Collections.emptyMap(), Collections.emptyMap(), options);
        assertThat(result3.getSimilarity()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("MapCompareStrategy - MARKDOWN报告生成")
    void mapCompareStrategy_markdownReport() {
        options.setGenerateReport(true);
        options.setReportFormat(ReportFormat.MARKDOWN);
        
        Map<String, String> map1 = Map.of("key1", "value1");
        Map<String, String> map2 = Map.of("key1", "changed");
        
        CompareResult result = mapStrategy.compare(map1, map2, options);
        
        assertThat(result.getReport()).contains("## Map Comparison");
        assertThat(result.getReport()).contains("| Key | Old Value | New Value | Change |");
        assertThat(result.getReport()).contains("key1");
        assertThat(result.getReport()).contains("**Total changes:**");
    }

    @Test
    @DisplayName("MapCompareStrategy - 文本报告生成")
    void mapCompareStrategy_textReport() {
        options.setGenerateReport(true);
        options.setReportFormat(ReportFormat.TEXT);
        
        Map<String, String> map1 = Map.of("key1", "value1");
        Map<String, String> map2 = Map.of("key1", "changed");
        
        CompareResult result = mapStrategy.compare(map1, map2, options);
        
        assertThat(result.getReport()).contains("Map Comparison:");
        assertThat(result.getReport()).contains("key1: value1 -> changed");
        assertThat(result.getReport()).contains("Total changes:");
    }

    // ========== CollectionCompareStrategy Tests (提升63%→80%+) ==========

    @Test
    @DisplayName("CollectionCompareStrategy - 基本功能")
    void collectionCompareStrategy_basicFunctionality() {
        assertThat(collectionStrategy.getName()).isEqualTo("CollectionCompare");
        assertThat(collectionStrategy.supports(Collection.class)).isTrue();
        assertThat(collectionStrategy.supports(List.class)).isTrue();
        assertThat(collectionStrategy.supports(Set.class)).isTrue();
        assertThat(collectionStrategy.supports(Map.class)).isFalse();
    }

    @Test
    @DisplayName("CollectionCompareStrategy - 相同引用")
    void collectionCompareStrategy_sameReference() {
        List<String> list = Arrays.asList("a", "b", "c");
        
        CompareResult result = collectionStrategy.compare(list, list, options);
        
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.getChanges()).isEmpty();
    }

    @Test
    @DisplayName("CollectionCompareStrategy - null值比较")
    void collectionCompareStrategy_nullComparison() {
        List<String> list = Arrays.asList("a", "b");
        
        CompareResult result1 = collectionStrategy.compare(null, list, options);
        assertThat(result1.isIdentical()).isFalse();
        
        CompareResult result2 = collectionStrategy.compare(list, null, options);
        assertThat(result2.isIdentical()).isFalse();
    }

    @Test
    @DisplayName("CollectionCompareStrategy - 元素添加检测")
    void collectionCompareStrategy_elementAddition() {
        List<String> list1 = Arrays.asList("a", "b");
        List<String> list2 = Arrays.asList("a", "b", "c");
        
        CompareResult result = collectionStrategy.compare(list1, list2, options);
        
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.getChanges()).hasSize(1);
        
        FieldChange change = result.getChanges().get(0);
        assertThat(change.isCollectionChange()).isTrue();
        assertThat(change.getCollectionDetail().getAddedCount()).isEqualTo(1);
        assertThat(change.getCollectionDetail().getRemovedCount()).isEqualTo(0);
        assertThat(change.getCollectionDetail().getOriginalSize()).isEqualTo(2);
        assertThat(change.getCollectionDetail().getNewSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("CollectionCompareStrategy - 元素删除检测")
    void collectionCompareStrategy_elementRemoval() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "b");
        
        CompareResult result = collectionStrategy.compare(list1, list2, options);
        
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.getChanges()).hasSize(1);
        
        FieldChange change = result.getChanges().get(0);
        assertThat(change.getCollectionDetail().getAddedCount()).isEqualTo(0);
        assertThat(change.getCollectionDetail().getRemovedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CollectionCompareStrategy - 复杂变更")
    void collectionCompareStrategy_complexChanges() {
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "d", "e");
        
        CompareResult result = collectionStrategy.compare(list1, list2, options);
        
        assertThat(result.isIdentical()).isFalse();
        
        FieldChange change = result.getChanges().get(0);
        assertThat(change.getCollectionDetail().getAddedCount()).isEqualTo(2); // d, e
        assertThat(change.getCollectionDetail().getRemovedCount()).isEqualTo(2); // b, c
    }

    @Test
    @DisplayName("CollectionCompareStrategy - 相似度计算")
    void collectionCompareStrategy_similarity() {
        options.setCalculateSimilarity(true);
        
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "b", "d");
        
        CompareResult result = collectionStrategy.compare(list1, list2, options);
        
        assertThat(result.getSimilarity()).isGreaterThan(0.0);
        assertThat(result.getSimilarity()).isLessThan(1.0);
    }

    @Test
    @DisplayName("CollectionCompareStrategy - 报告生成")
    void collectionCompareStrategy_reportGeneration() {
        options.setGenerateReport(true);
        
        List<String> list1 = Arrays.asList("a", "b");
        List<String> list2 = Arrays.asList("a", "c");
        
        CompareResult result = collectionStrategy.compare(list1, list2, options);
        
        assertThat(result.getReport()).contains("Collection Comparison");
        assertThat(result.getReport()).contains("Added");
        assertThat(result.getReport()).contains("Removed");
    }

    // ========== Pair Tests (提升42%→80%+) ==========

    @Test
    @DisplayName("Pair - 基本功能")
    void pair_basicFunctionality() {
        Pair<String, Integer> pair = new Pair<>("left", 42);
        
        assertThat(pair.getLeft()).isEqualTo("left");
        assertThat(pair.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("Pair - 静态创建方法")
    void pair_staticCreation() {
        Pair<String, Integer> pair = Pair.of("test", 123);
        
        assertThat(pair.getLeft()).isEqualTo("test");
        assertThat(pair.getRight()).isEqualTo(123);
    }

    @Test
    @DisplayName("Pair - 交换功能")
    void pair_swapFunctionality() {
        Pair<String, Integer> original = Pair.of("left", 42);
        Pair<Integer, String> swapped = original.swap();
        
        assertThat(swapped.getLeft()).isEqualTo(42);
        assertThat(swapped.getRight()).isEqualTo("left");
    }

    @Test
    @DisplayName("Pair - 设置器功能")
    void pair_setterFunctionality() {
        Pair<String, Integer> pair = new Pair<>();
        
        pair.setLeft("newLeft");
        pair.setRight(999);
        
        assertThat(pair.getLeft()).isEqualTo("newLeft");
        assertThat(pair.getRight()).isEqualTo(999);
    }

    @Test
    @DisplayName("Pair - equals和hashCode")
    void pair_equalsAndHashCode() {
        Pair<String, Integer> pair1 = Pair.of("test", 42);
        Pair<String, Integer> pair2 = Pair.of("test", 42);
        Pair<String, Integer> pair3 = Pair.of("different", 42);
        
        assertThat(pair1).isEqualTo(pair2);
        assertThat(pair1).isNotEqualTo(pair3);
        assertThat(pair1.hashCode()).isEqualTo(pair2.hashCode());
    }

    @Test
    @DisplayName("Pair - toString")
    void pair_toString() {
        Pair<String, Integer> pair = Pair.of("test", 42);
        
        String toString = pair.toString();
        assertThat(toString).contains("test");
        assertThat(toString).contains("42");
    }

    // ========== MergeResult Tests (提升58%→80%+) ==========

    @Test
    @DisplayName("MergeResult - 基本构建")
    void mergeResult_basicConstruction() {
        List<FieldChange> leftChanges = Arrays.asList(
            FieldChange.builder().fieldName("field1").changeType(ChangeType.UPDATE).build()
        );
        List<FieldChange> rightChanges = Arrays.asList(
            FieldChange.builder().fieldName("field2").changeType(ChangeType.CREATE).build()
        );
        List<MergeConflict> conflicts = Arrays.asList(
            MergeConflict.builder().fieldName("conflict").conflictType(ConflictType.VALUE_CONFLICT).build()
        );
        
        MergeResult result = MergeResult.builder()
            .base("base")
            .left("left")
            .right("right")
            .leftChanges(leftChanges)
            .rightChanges(rightChanges)
            .conflicts(conflicts)
            .merged("merged")
            .autoMergeSuccessful(false)
            .build();
        
        assertThat(result.getBase()).isEqualTo("base");
        assertThat(result.getLeft()).isEqualTo("left");
        assertThat(result.getRight()).isEqualTo("right");
        assertThat(result.getLeftChanges()).hasSize(1);
        assertThat(result.getRightChanges()).hasSize(1);
        assertThat(result.getConflicts()).hasSize(1);
        assertThat(result.getMerged()).isEqualTo("merged");
        assertThat(result.isAutoMergeSuccessful()).isFalse();
    }

    @Test
    @DisplayName("MergeResult - 冲突检测")
    void mergeResult_conflictDetection() {
        // 无冲突情况
        MergeResult noConflicts = MergeResult.builder()
            .conflicts(Collections.emptyList())
            .build();
        assertThat(noConflicts.hasConflicts()).isFalse();
        
        // 有冲突情况
        MergeResult withConflicts = MergeResult.builder()
            .conflicts(Arrays.asList(
                MergeConflict.builder().fieldName("conflict").build()
            ))
            .build();
        assertThat(withConflicts.hasConflicts()).isTrue();
    }

    @Test
    @DisplayName("MergeResult - 自动合并能力检测")
    void mergeResult_autoMergeCapability() {
        // 无冲突，可以自动合并
        MergeResult noConflicts = MergeResult.builder()
            .conflicts(Collections.emptyList())
            .build();
        assertThat(noConflicts.canAutoMerge()).isTrue();
        
        // 有冲突，无策略，不能自动合并
        MergeResult withConflicts = MergeResult.builder()
            .conflicts(Arrays.asList(MergeConflict.builder().fieldName("conflict").build()))
            .build();
        assertThat(withConflicts.canAutoMerge()).isFalse();
        
        // 有冲突，有能解决冲突的策略，可以自动合并
        MergeStrategy resolvingStrategy = new MergeStrategy() {
            @Override
            public boolean canResolveConflicts() { return true; }
            @Override
            public Object resolveConflict(MergeConflict conflict) { return null; }
            @Override
            public String getName() { return "TestStrategy"; }
            @Override
            public String getDescription() { return "Test strategy"; }
        };
        
        MergeResult withResolvingStrategy = MergeResult.builder()
            .conflicts(Arrays.asList(MergeConflict.builder().fieldName("conflict").build()))
            .strategy(resolvingStrategy)
            .build();
        assertThat(withResolvingStrategy.canAutoMerge()).isTrue();
    }

    @Test
    @DisplayName("MergeResult - 变更统计")
    void mergeResult_changeStatistics() {
        List<FieldChange> leftChanges = Arrays.asList(
            FieldChange.builder().fieldName("field1").build(),
            FieldChange.builder().fieldName("field2").build()
        );
        List<FieldChange> rightChanges = Arrays.asList(
            FieldChange.builder().fieldName("field3").build()
        );
        List<MergeConflict> conflicts = Arrays.asList(
            MergeConflict.builder().fieldName("conflict1").build(),
            MergeConflict.builder().fieldName("conflict2").build(),
            MergeConflict.builder().fieldName("conflict3").build()
        );
        
        MergeResult result = MergeResult.builder()
            .leftChanges(leftChanges)
            .rightChanges(rightChanges)
            .conflicts(conflicts)
            .build();
        
        assertThat(result.getTotalChanges()).isEqualTo(3); // 2 + 1
        assertThat(result.getConflictCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("MergeResult - 默认值处理")
    void mergeResult_defaultValues() {
        MergeResult result = MergeResult.builder().build();
        
        assertThat(result.getLeftChanges()).isEmpty();
        assertThat(result.getRightChanges()).isEmpty();
        assertThat(result.getConflicts()).isEmpty();
        assertThat(result.getTotalChanges()).isEqualTo(0);
        assertThat(result.getConflictCount()).isEqualTo(0);
        assertThat(result.hasConflicts()).isFalse();
        assertThat(result.canAutoMerge()).isTrue();
    }

}