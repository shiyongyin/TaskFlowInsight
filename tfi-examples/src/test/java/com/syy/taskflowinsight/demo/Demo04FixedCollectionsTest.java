package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.path.MapKeySegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试修复后的Set和Map比较功能
 */
public class Demo04FixedCollectionsTest {

    private static final Logger logger = LoggerFactory.getLogger(Demo04FixedCollectionsTest.class);
    private CompareService compareService;

    @BeforeEach
    public void setUp() {
        // 初始化CompareService（模拟Demo04_Collections的初始化）
        List<ListCompareStrategy> strategies = Arrays.asList(
            new SimpleListStrategy(),
            new EntityListStrategy()
        );
        ListCompareExecutor listCompareExecutor = new ListCompareExecutor(strategies);
        compareService = new CompareService(listCompareExecutor);
    }

    @Test
    public void testSetComparisonDetailed() {
        logger.info("🎯 测试Set集合详细比较");

        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(2, 3, 4, 5, 6));

        CompareResult result = compareService.compare(set1, set2, CompareOptions.builder().build());

        logger.info("Set比较结果：");
        logger.info("  相同: {}", result.isIdentical());
        logger.info("  变更数量: {}", result.getChanges().size());

        assertFalse(result.isIdentical(), "Sets should be different");
        assertTrue(result.getChanges().size() > 0, "Should detect changes");

        long addedChanges = result.getChanges().stream()
                .filter(change -> change.kind() == ChangeKind.ADD)
                .count();
        long removedChanges = result.getChanges().stream()
                .filter(change -> change.kind() == ChangeKind.REMOVE)
                .count();
        assertEquals(2, addedChanges, "应新增2个元素 (5, 6)");
        assertEquals(1, removedChanges, "应删除1个元素 (1)");

        // 计算实际的新增和删除元素
        Set<Integer> added = new HashSet<>(set2);
        added.removeAll(set1);
        Set<Integer> removed = new HashSet<>(set1);
        removed.removeAll(set2);

        logger.info("  新增元素: {}", added);
        logger.info("  删除元素: {}", removed);

        assertEquals(Set.of(5, 6), added, "新增元素应为 [5, 6]");
        assertEquals(Set.of(1), removed, "删除元素应为 [1]");
    }

    @Test
    public void testMapComparisonDetailed() {
        logger.info("🗺️ 测试Map集合详细比较");

        Map<String, String> map1 = new HashMap<>();
        map1.put("name", "John");
        map1.put("age", "30");
        map1.put("city", "NYC");

        Map<String, String> map2 = new HashMap<>();
        map2.put("name", "John");     // 不变
        map2.put("age", "31");        // 更新
        map2.put("country", "USA");   // 新增
        // city被删除

        CompareResult result = compareService.compare(map1, map2, CompareOptions.builder().build());

        logger.info("Map比较结果：");
        logger.info("  相同: {}", result.isIdentical());
        logger.info("  变更数量: {}", result.getChanges().size());

        assertFalse(result.isIdentical(), "Maps should be different");
        assertTrue(result.getChanges().size() > 0, "Should detect changes");

        // 分析各种变更类型
        Map<String, FieldChange> changesByKey = new HashMap<>();
        for (FieldChange change : result.getChanges()) {
            changesByKey.put(exactMapKey(change), change);
            logger.info("  - {}: {}", change.getChangeType(), exactMapKey(change));
            logger.info("    旧值: {}", change.beforeValue().orElse(null));
            logger.info("    新值: {}", change.afterValue().orElse(null));
        }

        // 验证期望的变更
        assertTrue(changesByKey.containsKey("age"), "应检测到age的变更");
        assertTrue(changesByKey.containsKey("city"), "应检测到city的删除");
        assertTrue(changesByKey.containsKey("country"), "应检测到country的新增");
        assertFalse(changesByKey.containsKey("name"), "name未变更，不应出现在变更列表中");

        // 验证变更类型
        assertEquals("30", exactString(changesByKey.get("age").beforeValue().orElseThrow()));
        assertEquals("31", exactString(changesByKey.get("age").afterValue().orElseThrow()));
        assertEquals("NYC", exactString(changesByKey.get("city").beforeValue().orElseThrow()));
        assertTrue(changesByKey.get("city").afterValue().isEmpty());
        assertTrue(changesByKey.get("country").beforeValue().isEmpty());
        assertEquals("USA", exactString(changesByKey.get("country").afterValue().orElseThrow()));
    }

    @Test
    public void testEmptyCollections() {
        logger.info("🔍 测试空集合比较");

        Set<String> emptySet1 = new HashSet<>();
        Set<String> emptySet2 = new HashSet<>();

        CompareResult result = compareService.compare(emptySet1, emptySet2,
            CompareOptions.builder().build());

        assertTrue(result.isIdentical(), "两个空集合应该相同");
        assertEquals(0, result.getChanges().size(), "空集合比较不应有变更");

        logger.info("  空集合比较结果：相同 = {}", result.isIdentical());
    }

    private static String exactMapKey(FieldChange change) {
        return change.after().or(() -> change.before())
                .orElseThrow()
                .path()
                .segments()
                .stream()
                .filter(MapKeySegment.class::isInstance)
                .map(MapKeySegment.class::cast)
                .map(MapKeySegment::key)
                .map(Demo04FixedCollectionsTest::exactString)
                .findFirst()
                .orElseThrow();
    }

    private static String exactString(ValueSnapshot snapshot) {
        assertEquals(ValueSnapshot.Representation.EXACT, snapshot.representation());
        assertEquals("string", snapshot.typeCode());
        return snapshot.canonicalTextFacts().getFirst();
    }
}
