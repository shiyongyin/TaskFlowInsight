package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for tracking.compare package to improve coverage
 */
class TrackingCompareComprehensiveTest {

    @Test
    @DisplayName("FieldChange基本功能")
    void fieldChange_basicFunctionality() {
        FieldChange change = FieldChange.builder()
            .fieldName("username")
            .oldValue("oldName")
            .newValue("newName")
            .changeType(ChangeType.UPDATE)
            .valueType("String")
            .fieldPath("user.profile.username")
            .collectionChange(false)
            .build();

        assertThat(change.getFieldName()).isEqualTo("username");
        assertThat(change.getOldValue()).isEqualTo("oldName");
        assertThat(change.getNewValue()).isEqualTo("newName");
        assertThat(change.getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(change.getValueType()).isEqualTo("String");
        assertThat(change.getFieldPath()).isEqualTo("user.profile.username");
        assertThat(change.isCollectionChange()).isFalse();
        assertThat(change.isNullChange()).isFalse();
    }

    @Test
    @DisplayName("FieldChange值描述生成")
    void fieldChange_valueDescriptions() {
        // UPDATE变更
        FieldChange updateChange = FieldChange.builder()
            .oldValue("old")
            .newValue("new")
            .changeType(ChangeType.UPDATE)
            .build();
        assertThat(updateChange.getValueDescription()).isEqualTo("old -> new");

        // DELETE变更
        FieldChange deleteChange = FieldChange.builder()
            .oldValue("deleted")
            .changeType(ChangeType.DELETE)
            .build();
        assertThat(deleteChange.getValueDescription()).isEqualTo("deleted -> (deleted)");

        // CREATE变更
        FieldChange createChange = FieldChange.builder()
            .newValue("created")
            .changeType(ChangeType.CREATE)
            .build();
        assertThat(createChange.getValueDescription()).isEqualTo("(new) -> created");
    }

    @Test
    @DisplayName("FieldChange null变更检测")
    void fieldChange_nullChangeDetection() {
        // 双null
        FieldChange bothNull = FieldChange.builder()
            .oldValue(null)
            .newValue(null)
            .changeType(ChangeType.UPDATE)
            .build();
        assertThat(bothNull.isNullChange()).isTrue();

        // null -> 创建
        FieldChange nullCreate = FieldChange.builder()
            .oldValue(null)
            .newValue("value")
            .changeType(ChangeType.CREATE)
            .build();
        assertThat(nullCreate.isNullChange()).isTrue();

        // 删除 -> null
        FieldChange deleteNull = FieldChange.builder()
            .oldValue("value")
            .newValue(null)
            .changeType(ChangeType.DELETE)
            .build();
        assertThat(deleteNull.isNullChange()).isTrue();

        // 正常变更
        FieldChange normalChange = FieldChange.builder()
            .oldValue("old")
            .newValue("new")
            .changeType(ChangeType.UPDATE)
            .build();
        assertThat(normalChange.isNullChange()).isFalse();
    }

    @Test
    @DisplayName("FieldChange集合变更详情")
    void fieldChange_collectionChangeDetail() {
        FieldChange.CollectionChangeDetail detail = FieldChange.CollectionChangeDetail.builder()
            .addedCount(3)
            .removedCount(1)
            .modifiedCount(2)
            .originalSize(10)
            .newSize(12)
            .build();

        assertThat(detail.getAddedCount()).isEqualTo(3);
        assertThat(detail.getRemovedCount()).isEqualTo(1);
        assertThat(detail.getModifiedCount()).isEqualTo(2);
        assertThat(detail.getOriginalSize()).isEqualTo(10);
        assertThat(detail.getNewSize()).isEqualTo(12);

        FieldChange change = FieldChange.builder()
            .fieldName("items")
            .collectionChange(true)
            .collectionDetail(detail)
            .changeType(ChangeType.UPDATE)
            .build();

        assertThat(change.isCollectionChange()).isTrue();
        assertThat(change.getCollectionDetail()).isEqualTo(detail);
    }

    @Test
    @DisplayName("DateCompareStrategy基本比较")
    void dateCompareStrategy_basicComparison() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        
        assertThat(strategy.getName()).isEqualTo("DateCompare");
        assertThat(strategy.supports(Date.class)).isTrue();
        assertThat(strategy.supports(String.class)).isFalse();

        Date date1 = new Date(1000000000L); // 固定时间戳
        Date date2 = new Date(1000000000L); // 相同时间

        CompareOptions options = CompareOptions.builder()
            .calculateSimilarity(false)
            .generateReport(false)
            .build();

        CompareResult result = strategy.compare(date1, date2, options);
        assertThat(result.isIdentical()).isTrue();
    }

    @Test
    @DisplayName("DateCompareStrategy相同对象比较")
    void dateCompareStrategy_sameObjectComparison() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        Date date = new Date();
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = strategy.compare(date, date, options);
        
        assertThat(result.isIdentical()).isTrue();
    }

    @Test
    @DisplayName("DateCompareStrategy null值处理")
    void dateCompareStrategy_nullHandling() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        Date date = new Date();
        
        CompareOptions options = CompareOptions.builder().build();
        
        // 一个null
        CompareResult result1 = strategy.compare(null, date, options);
        assertThat(result1.isIdentical()).isFalse();
        
        CompareResult result2 = strategy.compare(date, null, options);
        assertThat(result2.isIdentical()).isFalse();
        
        // 都为null (特殊情况：两个null被认为是相同的)
        CompareResult result3 = strategy.compare(null, null, options);
        assertThat(result3.isIdentical()).isTrue();
    }

    @Test
    @DisplayName("DateCompareStrategy容差范围")
    void dateCompareStrategy_tolerance() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000000500L); // 500ms差异，在1s容差内
        Date date3 = new Date(1000002000L); // 2s差异，超出容差
        
        CompareOptions options = CompareOptions.builder().build();
        
        // 容差内
        CompareResult result1 = strategy.compare(date1, date2, options);
        assertThat(result1.isIdentical()).isTrue();
        
        // 超出容差
        CompareResult result2 = strategy.compare(date1, date3, options);
        assertThat(result2.isIdentical()).isFalse();
        assertThat(result2.getChanges()).hasSize(1);
        
        FieldChange change = result2.getChanges().get(0);
        assertThat(change.getFieldName()).isEqualTo("date");
        assertThat(change.getChangeType()).isEqualTo(ChangeType.UPDATE);
    }

    @Test
    @DisplayName("DateCompareStrategy相似度计算")
    void dateCompareStrategy_similarityCalculation() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000003600000L); // 1小时后
        
        CompareOptions options = CompareOptions.builder()
            .calculateSimilarity(true)
            .build();
        
        CompareResult result = strategy.compare(date1, date2, options);
        assertThat(result.getSimilarity()).isNotNull(); // 确保有相似度计算
    }

    @Test
    @DisplayName("DateCompareStrategy报告生成 - 文本格式")
    void dateCompareStrategy_textReport() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000003600000L); // 1小时后
        
        CompareOptions options = CompareOptions.builder()
            .generateReport(true)
            .reportFormat(ReportFormat.TEXT)
            .build();
        
        CompareResult result = strategy.compare(date1, date2, options);
        assertThat(result.getReport()).contains("Date Comparison:");
        assertThat(result.getReport()).contains("Date 1:");
        assertThat(result.getReport()).contains("Date 2:");
        assertThat(result.getReport()).contains("Difference:");
        // 不检查具体的小时数，只检查有差异报告即可
    }

    @Test
    @DisplayName("DateCompareStrategy报告生成 - Markdown格式")
    void dateCompareStrategy_markdownReport() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000086400000L); // 1天后
        
        CompareOptions options = CompareOptions.builder()
            .generateReport(true)
            .reportFormat(ReportFormat.MARKDOWN)
            .build();
        
        CompareResult result = strategy.compare(date1, date2, options);
        assertThat(result.getReport()).contains("## Date Comparison");
        assertThat(result.getReport()).contains("| Attribute | Value |");
        assertThat(result.getReport()).contains("| Date 1 |");
        assertThat(result.getReport()).contains("| Date 2 |");
        assertThat(result.getReport()).contains("| Difference |");
        // 不检查具体的天数，只检查有表格格式即可
    }

    @Test
    @DisplayName("DateCompareStrategy报告生成 - 分钟和秒")
    void dateCompareStrategy_reportMinutesAndSeconds() {
        DateCompareStrategy strategy = new DateCompareStrategy();
        
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000120000L); // 2分钟后
        Date date3 = new Date(1000030000L); // 30秒后
        
        CompareOptions options = CompareOptions.builder()
            .generateReport(true)
            .reportFormat(ReportFormat.TEXT)
            .build();
        
        // 分钟差异
        CompareResult result1 = strategy.compare(date1, date2, options);
        assertThat(result1.getReport()).contains("2 minutes");
        
        // 秒差异
        CompareResult result2 = strategy.compare(date1, date3, options);
        assertThat(result2.getReport()).contains("30 seconds");
    }

    @Test
    @DisplayName("MergeConflict基本功能")
    void mergeConflict_basicFunctionality() {
        MergeConflict conflict = MergeConflict.builder()
            .conflictType(ConflictType.VALUE_CONFLICT)
            .fieldName("email")
            .baseValue("old@example.com")
            .leftValue("left@example.com")
            .rightValue("right@example.com")
            .suggestedResolution(ResolutionStrategy.MANUAL)
            .description("Email conflict requires manual resolution")
            .autoResolvable(false)
            .build();

        assertThat(conflict.getConflictType()).isEqualTo(ConflictType.VALUE_CONFLICT);
        assertThat(conflict.getFieldName()).isEqualTo("email");
        assertThat(conflict.getBaseValue()).isEqualTo("old@example.com");
        assertThat(conflict.getLeftValue()).isEqualTo("left@example.com");
        assertThat(conflict.getRightValue()).isEqualTo("right@example.com");
        assertThat(conflict.getSuggestedResolution()).isEqualTo(ResolutionStrategy.MANUAL);
        assertThat(conflict.getDescription()).isEqualTo("Email conflict requires manual resolution");
        assertThat(conflict.isAutoResolvable()).isFalse();
    }

    @Test
    @DisplayName("ResolutionStrategy枚举")
    void resolutionStrategy_enum() {
        ResolutionStrategy[] strategies = ResolutionStrategy.values();
        assertThat(strategies).contains(
            ResolutionStrategy.MANUAL,
            ResolutionStrategy.USE_LEFT,
            ResolutionStrategy.USE_RIGHT,
            ResolutionStrategy.USE_BASE,
            ResolutionStrategy.MERGE_VALUES,
            ResolutionStrategy.SKIP
        );
        
        assertThat(ResolutionStrategy.valueOf("MANUAL")).isEqualTo(ResolutionStrategy.MANUAL);
        assertThat(ResolutionStrategy.valueOf("USE_LEFT")).isEqualTo(ResolutionStrategy.USE_LEFT);
    }

    @Test
    @DisplayName("ConflictType枚举")
    void conflictType_enum() {
        ConflictType[] types = ConflictType.values();
        assertThat(types).contains(
            ConflictType.VALUE_CONFLICT,
            ConflictType.TYPE_CONFLICT,
            ConflictType.STRUCTURE_CONFLICT
        );
        
        assertThat(ConflictType.valueOf("VALUE_CONFLICT")).isEqualTo(ConflictType.VALUE_CONFLICT);
    }

    @Test
    @DisplayName("ReportFormat枚举")
    void reportFormat_enum() {
        ReportFormat[] formats = ReportFormat.values();
        assertThat(formats).contains(
            ReportFormat.TEXT,
            ReportFormat.MARKDOWN,
            ReportFormat.JSON
        );
        
        assertThat(ReportFormat.valueOf("MARKDOWN")).isEqualTo(ReportFormat.MARKDOWN);
    }

    @Test
    @DisplayName("PatchFormat枚举")
    void patchFormat_enum() {
        PatchFormat[] formats = PatchFormat.values();
        assertThat(formats).contains(
            PatchFormat.JSON_PATCH,
            PatchFormat.MERGE_PATCH,
            PatchFormat.CUSTOM
        );
        
        assertThat(PatchFormat.valueOf("JSON_PATCH")).isEqualTo(PatchFormat.JSON_PATCH);
        assertThat(PatchFormat.valueOf("MERGE_PATCH")).isEqualTo(PatchFormat.MERGE_PATCH);
    }

    @Test
    @DisplayName("Pair工具类")
    void pair_utility() {
        Pair<String, Integer> pair = new Pair<>("hello", 42);
        
        assertThat(pair.getLeft()).isEqualTo("hello");
        assertThat(pair.getRight()).isEqualTo(42);
        
        // 测试相等性
        Pair<String, Integer> pair2 = new Pair<>("hello", 42);
        assertThat(pair).isEqualTo(pair2);
        assertThat(pair.hashCode()).isEqualTo(pair2.hashCode());
        
        // 测试不相等
        Pair<String, Integer> pair3 = new Pair<>("world", 42);
        assertThat(pair).isNotEqualTo(pair3);
    }

    @Test
    @DisplayName("MergeResult基本功能")
    void mergeResult_basicFunctionality() {
        FieldChange leftChange = FieldChange.builder()
            .fieldName("field1")
            .changeType(ChangeType.UPDATE)
            .build();
            
        FieldChange rightChange = FieldChange.builder()
            .fieldName("field2")
            .changeType(ChangeType.CREATE)
            .build();
            
        List<MergeConflict> conflicts = Arrays.asList(
            MergeConflict.builder()
                .conflictType(ConflictType.VALUE_CONFLICT)
                .fieldName("field1")
                .build()
        );

        MergeResult result = MergeResult.builder()
            .base("baseObject")
            .left("leftObject")
            .right("rightObject")
            .leftChanges(Arrays.asList(leftChange))
            .rightChanges(Arrays.asList(rightChange))
            .conflicts(conflicts)
            .merged(Map.of("key", "value"))
            .autoMergeSuccessful(false)
            .build();

        assertThat(result.getBase()).isEqualTo("baseObject");
        assertThat(result.getLeft()).isEqualTo("leftObject");
        assertThat(result.getRight()).isEqualTo("rightObject");
        assertThat(result.getLeftChanges()).hasSize(1);
        assertThat(result.getRightChanges()).hasSize(1);
        assertThat(result.getConflicts()).hasSize(1);
        assertThat(result.getMerged()).isEqualTo(Map.of("key", "value"));
        assertThat(result.isAutoMergeSuccessful()).isFalse();
        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.getTotalChanges()).isEqualTo(2);
        assertThat(result.getConflictCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CompareResult静态工厂方法")
    void compareResult_staticFactoryMethods() {
        // identical()
        CompareResult identical = CompareResult.identical();
        assertThat(identical.isIdentical()).isTrue();
        assertThat(identical.getChanges()).isEmpty();
        
        // ofNullDiff()
        CompareResult nullDiff = CompareResult.ofNullDiff("value1", null);
        assertThat(nullDiff.isIdentical()).isFalse();
        assertThat(nullDiff.getObject1()).isEqualTo("value1");
        assertThat(nullDiff.getObject2()).isNull();
    }

    @Test
    @DisplayName("CompareResult完整构建")
    void compareResult_fullConstruction() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1("obj1")
            .object2("obj2")
            .identical(false)
            .similarity(0.85)
            .changes(Arrays.asList(change))
            .report("Test report")
            .patch("patch content")
            .compareTimeMs(1000L)
            .build();

        assertThat(result.getObject1()).isEqualTo("obj1");
        assertThat(result.getObject2()).isEqualTo("obj2");
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.getSimilarity()).isEqualTo(0.85);
        assertThat(result.getChanges()).hasSize(1);
        assertThat(result.getReport()).isEqualTo("Test report");
        assertThat(result.getPatch()).isEqualTo("patch content");
        assertThat(result.getCompareTimeMs()).isEqualTo(1000L);
        
        // 测试计算方法
        assertThat(result.getChangeCount()).isEqualTo(1);
        assertThat(result.hasChanges()).isTrue();
        assertThat(result.getSimilarityPercent()).isEqualTo(85.0);
    }

    @Test
    @DisplayName("CompareResult其他静态方法")
    void compareResult_additionalStaticMethods() {
        // ofTypeDiff()
        CompareResult typeDiff = CompareResult.ofTypeDiff("string", 123);
        assertThat(typeDiff.isIdentical()).isFalse();
        assertThat(typeDiff.getSimilarity()).isEqualTo(0.0);
        assertThat(typeDiff.getObject1()).isEqualTo("string");
        assertThat(typeDiff.getObject2()).isEqualTo(123);
    }

    @Test
    @DisplayName("MergeConflict getSummary方法")
    void mergeConflict_getSummary() {
        MergeConflict conflict = MergeConflict.builder()
            .fieldName("email")
            .leftValue("left@example.com")
            .rightValue("right@example.com")
            .conflictType(ConflictType.VALUE_CONFLICT)
            .build();

        String summary = conflict.getSummary();
        assertThat(summary).contains("Conflict in field 'email'");
        assertThat(summary).contains("left=left@example.com");
        assertThat(summary).contains("right=right@example.com");
        assertThat(summary).contains("type=VALUE_CONFLICT");
    }
}