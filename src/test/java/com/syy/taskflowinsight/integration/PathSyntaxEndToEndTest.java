package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.tracking.path.PathBuilder;
import com.syy.taskflowinsight.tracking.detector.ChangeRecordComparator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.TestChangeRecordFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路径语法端到端集成测试
 * 验证各组件协同工作的正确性
 */
@SpringBootTest
class PathSyntaxEndToEndTest {

    private PathDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        PathDeduplicationConfig config = new PathDeduplicationConfig();
        config.setEnabled(true);
        config.setCacheEnabled(true);
        config.setMaxCacheSize(1000);
        deduplicator = new PathDeduplicator(config);
    }

    @Test
    @DisplayName("端到端：Map键双引号格式 + 三级排序 + 路径去重")
    void testEndToEndPathProcessing() {
        // 1. 验证PathBuilder生成正确的双引号格式
        String standardPath = PathBuilder.mapKey("user", "profile data");
        assertEquals("user[\"profile data\"]", standardPath);

        String escapedPath = PathBuilder.mapKey("user", "key\"with\\quotes");
        assertEquals("user[\"key\\\"with\\\\quotes\"]", escapedPath);

        // 2. 创建包含各种路径格式的变更记录
        List<ChangeRecord> records = Arrays.asList(
            // Map键路径
            TestChangeRecordFactory.create("user[\"name\"]", "John", "Jane", ChangeType.UPDATE),
            TestChangeRecordFactory.create("user[\"profile data\"]", "old", "new", ChangeType.UPDATE),
            TestChangeRecordFactory.create("user[\"key\\\"with\\\\quotes\"]", "val1", "val2", ChangeType.UPDATE),

            // 数组索引路径
            TestChangeRecordFactory.create("items[0]", null, "first", ChangeType.CREATE),
            TestChangeRecordFactory.create("items[1]", null, "second", ChangeType.CREATE),

            // 字段路径
            TestChangeRecordFactory.create("user.id", "123", "456", ChangeType.UPDATE),
            TestChangeRecordFactory.create("user.email", null, "test@example.com", ChangeType.CREATE),

            // Set元素路径
            TestChangeRecordFactory.create("tags[id=Tag001F4240]", null, "important", ChangeType.CREATE),
            TestChangeRecordFactory.create("tags[id=Tag00989680]", null, "urgent", ChangeType.CREATE),

            // 重复路径（用于测试去重）
            TestChangeRecordFactory.create("user[\"name\"]", "John", "Jane", ChangeType.UPDATE), // 重复
            TestChangeRecordFactory.create("user.id", "123", "456", ChangeType.UPDATE) // 重复
        );

        // 3. 应用三级排序
        List<ChangeRecord> sortedRecords = records.stream()
            .sorted(ChangeRecordComparator.INSTANCE)
            .collect(Collectors.toList());

        // 验证排序结果：路径字典序
        assertTrue(isSortedByPath(sortedRecords), "记录应按路径字典序排序");

        // 4. 创建对象快照用于去重
        Map<String, Object> snapshot = createSnapshot(records);

        // 5. 应用路径去重
        List<ChangeRecord> deduplicatedRecords = deduplicator.deduplicateWithObjectGraph(
            sortedRecords, snapshot, snapshot);

        // 6. 验证端到端结果
        // 应该移除重复的记录
        assertTrue(deduplicatedRecords.size() < sortedRecords.size(), "去重后记录数应减少");

        // 验证没有重复的路径
        Set<String> paths = deduplicatedRecords.stream()
            .map(ChangeRecord::getFieldName)
            .collect(Collectors.toSet());
        assertEquals(paths.size(), deduplicatedRecords.size(), "去重后不应有重复路径");

        // 验证关键路径格式
        List<String> allPaths = deduplicatedRecords.stream()
            .map(ChangeRecord::getFieldName)
            .collect(Collectors.toList());

        boolean hasDoubleQuotedMapKeys = allPaths.stream()
            .anyMatch(path -> path.contains("[\"") && path.contains("\"]"));
        assertTrue(hasDoubleQuotedMapKeys, "应包含双引号格式的Map键");

        boolean hasEscapedChars = allPaths.stream()
            .anyMatch(path -> path.contains("\\\"") || path.contains("\\\\"));
        assertTrue(hasEscapedChars, "应正确转义特殊字符");

        System.out.println("端到端测试通过 - 处理了 " + records.size() + " 个原始记录，" +
                          "排序后 " + sortedRecords.size() + " 个，去重后 " + deduplicatedRecords.size() + " 个");

        // 打印结果用于验证
        System.out.println("最终路径列表：");
        allPaths.forEach(path -> System.out.println("  " + path));
    }

    @Test
    @DisplayName("稳定性：多次执行结果一致")
    void testConsistencyAcrossMultipleRuns() {
        List<ChangeRecord> records = createComplexDataset();
        Map<String, Object> snapshot = createSnapshot(records);

        // 执行多次并收集结果
        List<String> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<ChangeRecord> processed = records.stream()
                .sorted(ChangeRecordComparator.INSTANCE)
                .collect(Collectors.toList());

            processed = deduplicator.deduplicateWithObjectGraph(processed, snapshot, snapshot);

            String result = processed.stream()
                .map(ChangeRecord::getFieldName)
                .sorted()
                .collect(Collectors.joining(","));

            results.add(result);
        }

        // 验证所有结果一致
        Set<String> uniqueResults = new HashSet<>(results);
        assertEquals(1, uniqueResults.size(), "多次执行结果应完全一致");
    }

    @Test
    @DisplayName("兼容性：legacy和standard模式切换")
    void testModeCompatibility() {
        // 测试legacy模式（单引号）
        String legacyPath = PathBuilder.mapKey("parent", "simple_key", false);
        assertEquals("parent['simple_key']", legacyPath);

        // 测试standard模式（双引号）
        String standardPath = PathBuilder.mapKey("parent", "simple_key", true);
        assertEquals("parent[\"simple_key\"]", standardPath);

        // 测试特殊字符处理差异
        String legacySpecial = PathBuilder.mapKey("parent", "key'with'quotes", false);
        assertEquals("parent['key\\'with\\'quotes']", legacySpecial);

        String standardSpecial = PathBuilder.mapKey("parent", "key\"with\"quotes", true);
        assertEquals("parent[\"key\\\"with\\\"quotes\"]", standardSpecial);

        System.out.println("兼容性测试通过 - legacy和standard模式都工作正常");
    }

    @Test
    @DisplayName("性能：大数据集处理")
    void testLargeDatasetPerformance() {
        // 创建大数据集（1000条记录）
        List<ChangeRecord> largeDataset = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeDataset.add(TestChangeRecordFactory.create(
                "data[\"key" + i + "\"]", "oldValue" + i, "newValue" + i, ChangeType.UPDATE));
        }

        Map<String, Object> snapshot = createSnapshot(largeDataset);

        long startTime = System.currentTimeMillis();

        // 执行完整流程
        List<ChangeRecord> processed = largeDataset.stream()
            .sorted(ChangeRecordComparator.INSTANCE)
            .collect(Collectors.toList());

        processed = deduplicator.deduplicateWithObjectGraph(processed, snapshot, snapshot);

        long duration = System.currentTimeMillis() - startTime;

        assertFalse(processed.isEmpty(), "处理后应有结果");
        assertTrue(duration < 500, "1000条记录处理应在500ms内完成，实际: " + duration + "ms");

        System.out.println("大数据集处理完成 - 处理了 " + largeDataset.size() +
                          " 条记录，耗时 " + duration + "ms");
    }

    // 辅助方法
    private boolean isSortedByPath(List<ChangeRecord> records) {
        for (int i = 1; i < records.size(); i++) {
            if (records.get(i - 1).getFieldName().compareTo(records.get(i).getFieldName()) > 0) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> createSnapshot(List<ChangeRecord> records) {
        Map<String, Object> snapshot = new HashMap<>();
        Map<String, Object> sharedValues = new HashMap<>();

        for (ChangeRecord record : records) {
            Object value = record.getNewValue() != null ? record.getNewValue() : record.getOldValue();

            // 使用共享实例确保对象引用相等
            if (value instanceof String) {
                String key = (String) value;
                sharedValues.putIfAbsent(key, value);
                value = sharedValues.get(key);
            }

            snapshot.put(record.getFieldName(), value);
        }
        return snapshot;
    }

    private List<ChangeRecord> createComplexDataset() {
        return Arrays.asList(
            TestChangeRecordFactory.create("root.user[\"name\"]", "John", "Jane", ChangeType.UPDATE),
            TestChangeRecordFactory.create("root.user[\"email\"]", null, "jane@example.com", ChangeType.CREATE),
            TestChangeRecordFactory.create("root.settings[\"theme\"]", "light", "dark", ChangeType.UPDATE),
            TestChangeRecordFactory.create("root.items[0]", null, "first", ChangeType.CREATE),
            TestChangeRecordFactory.create("root.items[1]", "second", null, ChangeType.DELETE),
            TestChangeRecordFactory.create("root.tags[id=Tag12345678]", null, "important", ChangeType.CREATE),
            TestChangeRecordFactory.create("root.config.debug", false, true, ChangeType.UPDATE),
            TestChangeRecordFactory.create("root.config.version", "1.0", "2.0", ChangeType.UPDATE)
        );
    }
}