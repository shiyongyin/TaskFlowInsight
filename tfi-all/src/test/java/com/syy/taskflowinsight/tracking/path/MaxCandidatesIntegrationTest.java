package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * maxCandidates 功能集成测试
 * 验证路径候选项限制功能是否正常工作
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
class MaxCandidatesIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(MaxCandidatesIntegrationTest.class);

    private PathDeduplicator pathDeduplicator;
    private PathDeduplicationConfig config;

    @BeforeEach
    void setUp() {
        config = new PathDeduplicationConfig();
        config.setMaxCandidates(5); // 设置最大候选项为5
        config.setEnabled(true);
        pathDeduplicator = new PathDeduplicator(config);

        logger.info("Test setup: maxCandidates={}", config.getMaxCandidates());
    }

    @Test
    @DisplayName("单个目标对象超过5个候选项时应被剪裁到5个")
    void shouldLimitCandidatesToConfiguredMaximum() {
        // Given: 创建一个共享对象，10个不同路径都指向它
        Object sharedTarget = new TestObject("shared-target");
        Map<String, Object> afterSnapshot = new HashMap<>();
        List<ChangeRecord> changeRecords = new ArrayList<>();

        // 创建10个不同路径指向同一个对象
        for (int i = 0; i < 10; i++) {
            String path = String.format("root.branch%d.leaf%d", i, i);
            afterSnapshot.put(path, sharedTarget);
            changeRecords.add(ChangeRecord.of("TestObj", path, null, "value" + i, ChangeType.UPDATE));
        }

        logger.info("Created {} change records pointing to same target", changeRecords.size());

        // When: 执行去重操作
        List<ChangeRecord> result = pathDeduplicator.deduplicateWithObjectGraph(
            changeRecords,
            Collections.emptyMap(),
            afterSnapshot
        );

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1); // 只应该保留一条记录

        // 验证统计信息
        PathDeduplicator.DeduplicationStatistics stats = pathDeduplicator.getStatistics();
        logger.info("Deduplication stats: clippedGroups={}, candidatesRemoved={}",
                    stats.getClippedGroupsCount(), stats.getClippedCandidatesRemoved());

        assertThat(stats.getClippedGroupsCount()).isGreaterThanOrEqualTo(1); // 至少有一组被剪裁
        assertThat(stats.getClippedCandidatesRemoved()).isEqualTo(5); // 剪裁掉了5个候选项（10-5=5）
    }

    @Test
    @DisplayName("候选项数量≤maxCandidates时不应触发剪裁")
    void shouldNotClipWhenCandidatesWithinLimit() {
        // Given: 只创建3个候选项（少于maxCandidates=5）
        Object sharedTarget = new TestObject("limited-target");
        Map<String, Object> afterSnapshot = new HashMap<>();
        List<ChangeRecord> changeRecords = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            String path = String.format("root.path%d", i);
            afterSnapshot.put(path, sharedTarget);
            changeRecords.add(ChangeRecord.of("TestObj", path, null, "value" + i, ChangeType.UPDATE));
        }

        // When: 执行去重操作
        pathDeduplicator.deduplicateWithObjectGraph(
            changeRecords,
            Collections.emptyMap(),
            afterSnapshot
        );

        // Then: 不应该发生剪裁
        PathDeduplicator.DeduplicationStatistics stats = pathDeduplicator.getStatistics();
        assertThat(stats.getClippedGroupsCount()).isEqualTo(0); // 没有组被剪裁
        assertThat(stats.getClippedCandidatesRemoved()).isEqualTo(0); // 没有候选项被移除
    }

    @Test
    @DisplayName("多个目标对象各自超过maxCandidates时都应被剪裁")
    void shouldClipMultipleTargetsIndependently() {
        // Given: 两个不同的目标对象，每个都有10个候选项
        Object target1 = new TestObject("target-1");
        Object target2 = new TestObject("target-2");

        Map<String, Object> afterSnapshot = new HashMap<>();
        List<ChangeRecord> changeRecords = new ArrayList<>();

        // 为target1创建10个路径
        for (int i = 0; i < 10; i++) {
            String path = String.format("target1.path%d", i);
            afterSnapshot.put(path, target1);
            changeRecords.add(ChangeRecord.of("TestObj1", path, null, "value1-" + i, ChangeType.UPDATE));
        }

        // 为target2创建10个路径
        for (int i = 0; i < 10; i++) {
            String path = String.format("target2.path%d", i);
            afterSnapshot.put(path, target2);
            changeRecords.add(ChangeRecord.of("TestObj2", path, null, "value2-" + i, ChangeType.UPDATE));
        }

        // When: 执行去重操作
        List<ChangeRecord> result = pathDeduplicator.deduplicateWithObjectGraph(
            changeRecords,
            Collections.emptyMap(),
            afterSnapshot
        );

        // Then: 每个目标对象保留一条记录
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2); // 两个目标对象各保留一条

        // 验证统计：两个组都被剪裁
        PathDeduplicator.DeduplicationStatistics stats = pathDeduplicator.getStatistics();
        assertThat(stats.getClippedGroupsCount()).isEqualTo(2); // 两个组被剪裁
        assertThat(stats.getClippedCandidatesRemoved()).isEqualTo(10); // 总共剪裁了10个候选项（每组5个）
    }

    @Test
    @DisplayName("验证maxCandidates配置的边界值")
    void shouldRespectMaxCandidatesConfiguration() {
        // Given: 测试不同的maxCandidates值
        for (int maxCandidates : Arrays.asList(1, 3, 5, 10)) {
            PathDeduplicationConfig testConfig = new PathDeduplicationConfig();
            testConfig.setMaxCandidates(maxCandidates);
            PathDeduplicator testDeduplicator = new PathDeduplicator(testConfig);

            Object target = new TestObject("test-target-" + maxCandidates);
            Map<String, Object> snapshot = new HashMap<>();
            List<ChangeRecord> records = new ArrayList<>();

            // 创建15个候选项
            int totalCandidates = 15;
            for (int i = 0; i < totalCandidates; i++) {
                String path = String.format("root.candidate%d", i);
                snapshot.put(path, target);
                records.add(ChangeRecord.of("TestObj", path, null, "value" + i, ChangeType.UPDATE));
            }

            // When: 执行去重
            testDeduplicator.deduplicateWithObjectGraph(records, Collections.emptyMap(), snapshot);

            // Then: 验证剪裁的数量符合预期
            PathDeduplicator.DeduplicationStatistics stats = testDeduplicator.getStatistics();
            int expectedRemoved = Math.max(0, totalCandidates - maxCandidates);

            logger.info("maxCandidates={}, totalCandidates={}, expectedRemoved={}, actualRemoved={}",
                       maxCandidates, totalCandidates, expectedRemoved, stats.getClippedCandidatesRemoved());

            assertThat(stats.getClippedCandidatesRemoved()).isEqualTo(expectedRemoved);
        }
    }

    @Test
    @DisplayName("maxCandidates=1时每个目标对象只保留最优路径")
    void shouldKeepOnlyBestPathWhenMaxCandidatesIsOne() {
        // Given: 极限情况，maxCandidates=1
        PathDeduplicationConfig strictConfig = new PathDeduplicationConfig();
        strictConfig.setMaxCandidates(1);
        PathDeduplicator strictDeduplicator = new PathDeduplicator(strictConfig);

        Object target = new TestObject("strict-target");
        Map<String, Object> snapshot = new HashMap<>();
        List<ChangeRecord> records = new ArrayList<>();

        // 创建不同深度的路径，深度更高的应该被优先选择
        String[] paths = {
            "root.shallow",                          // 深度2
            "root.medium.depth",                     // 深度3
            "root.deep.very.deep.path",             // 深度5 - 应该被选中
            "root.another.medium.path"               // 深度4
        };

        for (String path : paths) {
            snapshot.put(path, target);
            records.add(ChangeRecord.of("TestObj", path, null, "value", ChangeType.UPDATE));
        }

        // When: 执行去重
        List<ChangeRecord> result = strictDeduplicator.deduplicateWithObjectGraph(
            records, Collections.emptyMap(), snapshot);

        // Then: 只保留一条最优路径
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldName()).isEqualTo("root.deep.very.deep.path"); // 最深的路径被选中

        PathDeduplicator.DeduplicationStatistics stats = strictDeduplicator.getStatistics();
        assertThat(stats.getClippedCandidatesRemoved()).isEqualTo(3); // 剪裁掉了3个候选项
    }

    /**
     * 测试用对象类
     */
    private static class TestObject {
        private final String name;

        TestObject(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "TestObject{name='" + name + "'}";
        }
    }
}