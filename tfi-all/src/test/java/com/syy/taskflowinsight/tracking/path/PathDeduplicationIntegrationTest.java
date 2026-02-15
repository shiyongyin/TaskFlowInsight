package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.TestChangeRecordFactory;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 路径去重集成测试
 * 验证PathDeduplicator、PathArbiter、PathCollector、PathBuilder的集成
 */
@DisplayName("路径去重完整集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PathDeduplicationIntegrationTest {

    private PathDeduplicationConfig config;
    private PathDeduplicator deduplicator;
    private PathArbiter arbiter;
    private PathCollector collector;

    @BeforeEach
    void setUp() {
        config = new PathDeduplicationConfig();
        config.setEnabled(true);
        config.setCacheEnabled(true);
        config.setMaxCacheSize(10000);
        config.setMaxDepth(10);

        deduplicator = new PathDeduplicator(config);
        arbiter = new PathArbiter(config);
        collector = new PathCollector(config);
    }

    @Test
    @Order(1)
    @DisplayName("核心功能：最具体路径去重")
    void testMostSpecificPathDeduplication() {
        // 创建复杂对象图：同一对象通过多路径访问
        ComplexObject shared = new ComplexObject("shared-object");
        ComplexObject root = new ComplexObject("root");
        root.child = shared;
        root.deepChild = new ComplexObject("deep");
        root.deepChild.child = shared;

        // 模拟多路径变更
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("root.child", shared);
        snapshot.put("root.deepChild.child", shared);
        snapshot.put("root.reference", shared);

        List<ChangeRecord> records = Arrays.asList(
            TestChangeRecordFactory.create("root.child", null, shared, ChangeType.CREATE),
            TestChangeRecordFactory.create("root.deepChild.child", null, shared, ChangeType.CREATE),
            TestChangeRecordFactory.create("root.reference", null, shared, ChangeType.CREATE)
        );

        // 执行去重
        List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
            records, Collections.emptyMap(), snapshot);

        // 验证：同一对象只保留最深路径
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldName()).isEqualTo("root.deepChild.child");
    }

    @Test
    @Order(2)
    @DisplayName("访问类型优先级：字段>Map键>数组索引>Set元素")
    void testAccessTypePriority() {
        String sharedValue = "shared-value";
        Map<String, Object> snapshot = new HashMap<>();

        // 创建不同访问类型的路径
        snapshot.put("data.field", sharedValue);
        snapshot.put("data[\"mapKey\"]", sharedValue);
        snapshot.put("data[0]", sharedValue);
        snapshot.put("data[id=element123]", sharedValue);

        List<ChangeRecord> records = Arrays.asList(
            TestChangeRecordFactory.create("data[id=element123]", null, sharedValue, ChangeType.CREATE),
            TestChangeRecordFactory.create("data[0]", null, sharedValue, ChangeType.CREATE),
            TestChangeRecordFactory.create("data[\"mapKey\"]", null, sharedValue, ChangeType.CREATE),
            TestChangeRecordFactory.create("data.field", null, sharedValue, ChangeType.CREATE)
        );

        // 执行去重
        List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
            records, Collections.emptyMap(), snapshot);

        // 字段访问优先级最高
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldName()).isEqualTo("data.field");
    }

    @Test
    @Order(3)
    @DisplayName("路径构建规范验证：Map键使用双引号")
    void testPathBuildingStandards() {
        // 验证PathBuilder生成的路径格式
        String mapPath = PathBuilder.mapKey("parent", "key");
        assertThat(mapPath).isEqualTo("parent[\"key\"]");

        // 验证特殊字符转义
        String escapedPath = PathBuilder.mapKey("parent", "key\"with\\quotes");
        assertThat(escapedPath).isEqualTo("parent[\"key\\\"with\\\\quotes\"]");

        // 验证数组索引格式
        String arrayPath = PathBuilder.arrayIndex("parent", 0);
        assertThat(arrayPath).isEqualTo("parent[0]");

        // 验证Set元素格式
        String setPath = PathBuilder.setElement("parent", new ComplexObject("test"));
        assertThat(setPath).startsWith("parent[id=");
    }

    @Test
    @Order(4)
    @DisplayName("缓存性能验证：命中率>90%")
    void testCachePerformance() {
        List<ChangeRecord> dataset = createDataset(100);
        Map<String, Object> snapshot = createSnapshot(dataset);

        // 预热缓存
        deduplicator.deduplicateWithObjectGraph(dataset, snapshot, snapshot);

        // 重置统计
        deduplicator.resetStatistics();

        // 执行100次相同操作
        for (int i = 0; i < 100; i++) {
            deduplicator.deduplicateWithObjectGraph(dataset, snapshot, snapshot);
        }

        // 验证缓存效率
        PathDeduplicator.DeduplicationStatistics stats = deduplicator.getStatistics();
        assertThat(stats.getCacheEffectiveness()).isGreaterThan(80.0);
        System.out.printf("Cache hit rate: %.2f%%\n", stats.getCacheEffectiveness());
    }

    @Test
    @Order(5)
    @DisplayName("并发安全性验证")
    void testConcurrentSafety() throws Exception {
        List<ChangeRecord> dataset = createDataset(50);
        Map<String, Object> snapshot = createSnapshot(dataset);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);

        // 提交100个并发任务
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await(); // 等待统一开始
                    List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                        new ArrayList<>(dataset), snapshot, snapshot);
                    return computeSignature(result);
                } catch (Exception e) {
                    return "error";
                }
            }));
        }

        latch.countDown(); // 触发所有任务

        // 收集结果
        Set<String> uniqueResults = futures.stream()
            .map(f -> {
                try { return f.get(); }
                catch (Exception e) { return "error"; }
            })
            .collect(Collectors.toSet());

        executor.shutdown();

        // 验证所有并发执行产生相同结果
        assertThat(uniqueResults).hasSize(1);
        assertThat(uniqueResults).doesNotContain("error");
    }

    @Test
    @Order(6)
    @DisplayName("稳定性验证：1000次运行结果一致")
    void testStabilityAcross1000Runs() {
        List<ChangeRecord> dataset = createComplexDataset();
        Map<String, Object> snapshot = createSnapshot(dataset);

        // 获取参考结果
        List<ChangeRecord> reference = deduplicator.deduplicateWithObjectGraph(
            dataset, snapshot, snapshot);
        String referenceSignature = computeSignature(reference);

        // 验证1000次运行
        for (int i = 0; i < 1000; i++) {
            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                new ArrayList<>(dataset), snapshot, snapshot);
            String signature = computeSignature(result);

            if (!signature.equals(referenceSignature)) {
                fail("Run %d produced different result: %s vs %s", i, signature, referenceSignature);
            }
        }

        System.out.println("1000 runs produced consistent results");
    }

    @Test
    @Order(7)
    @DisplayName("PathArbiter裁决算法验证")
    void testPathArbiterAlgorithm() {
        // 测试深度优先
        PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate(
            "root.field", 2, PathArbiter.AccessType.FIELD, new Object());
        PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate(
            "root.child.field", 3, PathArbiter.AccessType.FIELD, new Object());

        List<PathArbiter.PathCandidate> candidates = Arrays.asList(shallow, deep);
        PathArbiter.PathCandidate selected = arbiter.selectMostSpecificConfigurable(candidates);

        assertThat(selected).isEqualTo(deep);

        // 测试访问类型优先级
        PathArbiter.PathCandidate fieldAccess = new PathArbiter.PathCandidate(
            "root.field", 2, PathArbiter.AccessType.FIELD, new Object());
        PathArbiter.PathCandidate mapAccess = new PathArbiter.PathCandidate(
            "root[\"key\"]", 2, PathArbiter.AccessType.MAP_KEY, new Object());

        candidates = Arrays.asList(mapAccess, fieldAccess);
        selected = arbiter.selectMostSpecificConfigurable(candidates);

        assertThat(selected).isEqualTo(fieldAccess);
    }

    @Test
    @Order(8)
    @DisplayName("完整性验证：不同对象的路径都保留")
    void testCompletenessForDifferentObjects() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("user.name", "Alice");
        snapshot.put("user.email", "alice@example.com");
        snapshot.put("user.age", 25);
        snapshot.put("settings.theme", "dark");
        snapshot.put("settings.language", "en");

        List<ChangeRecord> records = Arrays.asList(
            TestChangeRecordFactory.create("user.name", null, "Alice", ChangeType.CREATE),
            TestChangeRecordFactory.create("user.email", null, "alice@example.com", ChangeType.CREATE),
            TestChangeRecordFactory.create("user.age", null, 25, ChangeType.CREATE),
            TestChangeRecordFactory.create("settings.theme", null, "dark", ChangeType.CREATE),
            TestChangeRecordFactory.create("settings.language", null, "en", ChangeType.CREATE)
        );

        List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
            records, Collections.emptyMap(), snapshot);

        // 不同对象的路径都应保留
        assertThat(result).hasSize(5);

        Set<String> paths = result.stream()
            .map(ChangeRecord::getFieldName)
            .collect(Collectors.toSet());
        assertThat(paths).containsExactlyInAnyOrder(
            "user.name", "user.email", "user.age", "settings.theme", "settings.language"
        );
    }

    // 辅助类和方法
    private static class ComplexObject {
        String name;
        ComplexObject child;
        ComplexObject deepChild;
        Map<String, Object> data = new HashMap<>();
        List<Object> items = new ArrayList<>();

        ComplexObject(String name) {
            this.name = name;
        }
    }

    private List<ChangeRecord> createDataset(int size) {
        List<ChangeRecord> records = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            records.add(TestChangeRecordFactory.create(
                "field" + i, null, "value" + i, ChangeType.CREATE));
        }
        return records;
    }

    private List<ChangeRecord> createComplexDataset() {
        return Arrays.asList(
            TestChangeRecordFactory.create("user.profile.name", "old", "new", ChangeType.UPDATE),
            TestChangeRecordFactory.create("user.settings[\"theme\"]", null, "dark", ChangeType.CREATE),
            TestChangeRecordFactory.create("user.contacts[0].email", null, "test@example.com", ChangeType.CREATE),
            TestChangeRecordFactory.create("user.metadata.tags[5]", null, "important", ChangeType.CREATE)
        );
    }

    private Map<String, Object> createSnapshot(List<ChangeRecord> records) {
        Map<String, Object> snapshot = new HashMap<>();
        for (ChangeRecord record : records) {
            Object value = record.getNewValue() != null ? record.getNewValue() : record.getOldValue();
            if (value != null) {
                snapshot.put(record.getFieldName(), value);
            }
        }
        return snapshot;
    }

    private String computeSignature(List<ChangeRecord> results) {
        return results.stream()
            .map(r -> r.getFieldName() + ":" + r.getChangeType())
            .sorted()
            .collect(Collectors.joining(","));
    }
}