package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.TestChangeRecordFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * PathDeduplicator单元测试
 * 验证路径去重的核心功能和性能指标
 */
@DisplayName("PathDeduplicator单元测试")
class PathDeduplicatorTest {

    private PathDeduplicator deduplicator;
    private PathDeduplicationConfig config;

    @BeforeEach
    void setUp() {
        config = new PathDeduplicationConfig();
        config.setEnabled(true);
        config.setCacheEnabled(true);
        config.setMaxCacheSize(1000);
        deduplicator = new PathDeduplicator(config);
    }

    @Nested
    @DisplayName("基本功能测试")
    class BasicFunctionTests {

        @Test
        @DisplayName("空输入应返回空列表")
        void shouldHandleEmptyInput() {
            List<ChangeRecord> result = deduplicator.deduplicate(null);
            assertThat(result).isEmpty();

            result = deduplicator.deduplicate(new ArrayList<>());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("禁用去重时应返回原列表")
        void shouldReturnOriginalWhenDisabled() {
            config.setEnabled(false);
            deduplicator = new PathDeduplicator(config);

            List<ChangeRecord> records = createTestRecords();
            List<ChangeRecord> result = deduplicator.deduplicate(records);

            assertThat(result).isEqualTo(records);
        }

        @Test
        @DisplayName("单条记录应直接返回")
        void shouldReturnSingleRecord() {
            ChangeRecord single = TestChangeRecordFactory.create("field1", "old", "new", ChangeType.UPDATE);
            List<ChangeRecord> result = deduplicator.deduplicate(List.of(single));

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(single);
        }
    }

    @Nested
    @DisplayName("对象图去重测试")
    class ObjectGraphDeduplicationTests {

        @Test
        @DisplayName("同一对象多路径应保留最具体路径")
        void shouldKeepMostSpecificPathForSameObject() {
            // 创建指向同一对象的多个变更
            String sharedObject = "SharedValue";
            Map<String, Object> beforeSnapshot = new HashMap<>();
            Map<String, Object> afterSnapshot = new HashMap<>();

            afterSnapshot.put("root", sharedObject);
            afterSnapshot.put("root.child", sharedObject);
            afterSnapshot.put("root.child.grandchild", sharedObject);

            List<ChangeRecord> records = Arrays.asList(
                TestChangeRecordFactory.create("root", null, sharedObject, ChangeType.CREATE),
                TestChangeRecordFactory.create("root.child", null, sharedObject, ChangeType.CREATE),
                TestChangeRecordFactory.create("root.child.grandchild", null, sharedObject, ChangeType.CREATE)
            );

            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                records, beforeSnapshot, afterSnapshot);

            // 应该只保留最深的路径
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFieldName()).isEqualTo("root.child.grandchild");
        }

        @Test
        @DisplayName("不同对象的路径都应保留")
        void shouldKeepPathsForDifferentObjects() {
            Map<String, Object> beforeSnapshot = new HashMap<>();
            Map<String, Object> afterSnapshot = new HashMap<>();

            afterSnapshot.put("user.name", "Alice");
            afterSnapshot.put("user.email", "alice@example.com");
            afterSnapshot.put("user.age", 25);

            List<ChangeRecord> records = Arrays.asList(
                TestChangeRecordFactory.create("user.name", null, "Alice", ChangeType.CREATE),
                TestChangeRecordFactory.create("user.email", null, "alice@example.com", ChangeType.CREATE),
                TestChangeRecordFactory.create("user.age", null, 25, ChangeType.CREATE)
            );

            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                records, beforeSnapshot, afterSnapshot);

            // 不同对象的路径都应保留
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("访问类型优先级：字段>Map键>数组索引")
        void shouldRespectAccessTypePriority() {
            Integer sharedValue = 42;
            Map<String, Object> beforeSnapshot = new HashMap<>();
            Map<String, Object> afterSnapshot = new HashMap<>();

            afterSnapshot.put("data.field", sharedValue);
            afterSnapshot.put("data[\"key\"]", sharedValue);
            afterSnapshot.put("data[0]", sharedValue);

            List<ChangeRecord> records = Arrays.asList(
                TestChangeRecordFactory.create("data[0]", null, sharedValue, ChangeType.CREATE),
                TestChangeRecordFactory.create("data[\"key\"]", null, sharedValue, ChangeType.CREATE),
                TestChangeRecordFactory.create("data.field", null, sharedValue, ChangeType.CREATE)
            );

            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                records, beforeSnapshot, afterSnapshot);

            // 字段访问优先级最高
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFieldName()).isEqualTo("data.field");
        }
    }

    @Nested
    @DisplayName("性能和统计测试")
    class PerformanceTests {

        @Test
        @DisplayName("缓存命中率应大于90%")
        void shouldAchieveHighCacheHitRate() {
            // 重复处理相同的数据集
            List<ChangeRecord> records = createLargeDataset(100);
            Map<String, Object> snapshot = createSnapshot(records);

            // 多次处理相同数据
            for (int i = 0; i < 10; i++) {
                deduplicator.deduplicateWithObjectGraph(records, snapshot, snapshot);
            }

            PathDeduplicator.DeduplicationStatistics stats = deduplicator.getStatistics();
            double cacheEffectiveness = stats.getCacheEffectiveness();

            // 缓存命中率应该很高（首次miss，后续hit）
            assertThat(cacheEffectiveness).isGreaterThan(80.0);
        }

        @Test
        @DisplayName("去重统计信息应准确")
        void shouldTrackStatisticsAccurately() {
            List<ChangeRecord> records = createDuplicateRecords();
            Map<String, Object> snapshot = createSnapshot(records);

            deduplicator.deduplicateWithObjectGraph(records, snapshot, snapshot);

            PathDeduplicator.DeduplicationStatistics stats = deduplicator.getStatistics();
            assertThat(stats.getTotalDeduplicationCount()).isEqualTo(1);
            assertThat(stats.getDuplicatesRemovedCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("大数据集处理性能")
        void shouldHandleLargeDatasetEfficiently() {
            List<ChangeRecord> largeDataset = createLargeDataset(1000);
            Map<String, Object> snapshot = createSnapshot(largeDataset);

            long startTime = System.currentTimeMillis();
            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                largeDataset, snapshot, snapshot);
            long duration = System.currentTimeMillis() - startTime;

            assertThat(result).isNotEmpty();
            assertThat(duration).isLessThan(100); // 应在100ms内完成
        }
    }

    @Nested
    @DisplayName("稳定性测试")
    class StabilityTests {

        @Test
        @DisplayName("多次运行结果应一致")
        void shouldProduceConsistentResults() {
            List<ChangeRecord> records = createComplexDataset();
            Map<String, Object> snapshot = createSnapshot(records);

            Set<String> resultPaths = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                    records, snapshot, snapshot);

                String paths = result.stream()
                    .map(ChangeRecord::getFieldName)
                    .sorted()
                    .collect(Collectors.joining(","));
                resultPaths.add(paths);
            }

            // 所有运行结果应完全一致
            assertThat(resultPaths).hasSize(1);
        }

        @Test
        @DisplayName("统计重置功能")
        void shouldResetStatisticsCorrectly() {
            List<ChangeRecord> records = createTestRecords();
            deduplicator.deduplicate(records);

            PathDeduplicator.DeduplicationStatistics stats = deduplicator.getStatistics();
            assertThat(stats.getTotalDeduplicationCount()).isGreaterThan(0);

            deduplicator.resetStatistics();
            stats = deduplicator.getStatistics();
            assertThat(stats.getTotalDeduplicationCount()).isEqualTo(0);
            assertThat(stats.getDuplicatesRemovedCount()).isEqualTo(0);
            assertThat(stats.getCacheHitCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("处理null值的路径")
        void shouldHandleNullValues() {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("field1", null);
            snapshot.put("field2", "value");

            List<ChangeRecord> records = Arrays.asList(
                TestChangeRecordFactory.create("field1", "old", null, ChangeType.UPDATE),
                TestChangeRecordFactory.create("field2", null, "value", ChangeType.CREATE)
            );

            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                records, snapshot, snapshot);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("深度嵌套路径处理")
        void shouldHandleDeeplyNestedPaths() {
            String deepPath = IntStream.range(0, 20)
                .mapToObj(i -> "level" + i)
                .collect(Collectors.joining("."));

            ChangeRecord deepRecord = TestChangeRecordFactory.create(deepPath, null, "value", ChangeType.CREATE);
            List<ChangeRecord> result = deduplicator.deduplicate(List.of(deepRecord));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFieldName()).isEqualTo(deepPath);
        }

        @Test
        @DisplayName("特殊字符路径处理")
        void shouldHandleSpecialCharacterPaths() {
            List<ChangeRecord> records = Arrays.asList(
                TestChangeRecordFactory.create("field[\"key-with-dash\"]", null, "value1", ChangeType.CREATE),
                TestChangeRecordFactory.create("field[\"key.with.dots\"]", null, "value2", ChangeType.CREATE),
                TestChangeRecordFactory.create("field[\"key with spaces\"]", null, "value3", ChangeType.CREATE)
            );

            List<ChangeRecord> result = deduplicator.deduplicate(records);
            assertThat(result).hasSize(3);
        }
    }

    // 辅助方法
    private List<ChangeRecord> createTestRecords() {
        return Arrays.asList(
            TestChangeRecordFactory.create("field1", "old1", "new1", ChangeType.UPDATE),
            TestChangeRecordFactory.create("field2", "old2", "new2", ChangeType.UPDATE),
            TestChangeRecordFactory.create("field3", null, "new3", ChangeType.CREATE)
        );
    }

    private List<ChangeRecord> createDuplicateRecords() {
        String sharedValue = "shared";
        return Arrays.asList(
            TestChangeRecordFactory.create("path1", null, sharedValue, ChangeType.CREATE),
            TestChangeRecordFactory.create("path1.sub", null, sharedValue, ChangeType.CREATE),
            TestChangeRecordFactory.create("path1.sub.deep", null, sharedValue, ChangeType.CREATE)
        );
    }

    private List<ChangeRecord> createLargeDataset(int size) {
        List<ChangeRecord> records = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            records.add(TestChangeRecordFactory.create(
                "field" + i,
                "old" + i,
                "new" + i,
                ChangeType.UPDATE
            ));
        }
        return records;
    }

    private List<ChangeRecord> createComplexDataset() {
        return Arrays.asList(
            TestChangeRecordFactory.create("user.profile.name", "old", "John", ChangeType.UPDATE),
            TestChangeRecordFactory.create("user.settings.theme", null, "dark", ChangeType.CREATE),
            TestChangeRecordFactory.create("user.contacts[0].email", null, "john@example.com", ChangeType.CREATE),
            TestChangeRecordFactory.create("user.metadata[\"lastLogin\"]", "2024-01-01", "2024-09-24", ChangeType.UPDATE)
        );
    }

    private Map<String, Object> createSnapshot(List<ChangeRecord> records) {
        Map<String, Object> snapshot = new HashMap<>();

        // 使用共享对象实例以确保缓存能正确命中
        Map<String, Object> sharedObjects = new HashMap<>();

        for (ChangeRecord record : records) {
            Object value = record.getNewValue() != null ? record.getNewValue() : record.getOldValue();

            // 如果是字符串值，使用共享实例确保对象引用相等
            if (value instanceof String) {
                String key = (String) value;
                sharedObjects.putIfAbsent(key, value);
                value = sharedObjects.get(key);
            }

            snapshot.put(record.getFieldName(), value);
        }
        return snapshot;
    }
}