package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 黄金测试：MOVE / SET重复键 / 数组容器
 */
class ContainerElementEventGoldenTests {

    @Test
    void list_move_should_emit_move_event_with_old_and_new_index() {
        LevenshteinListStrategy strategy = new LevenshteinListStrategy();
        CompareOptions opts = CompareOptions.builder().detectMoves(true).build();

        List<String> oldList = List.of("A", "B", "C");
        List<String> newList = List.of("B", "A", "C");

        CompareResult result = strategy.compare(oldList, newList, opts);
        assertFalse(result.getChanges().isEmpty());

        boolean hasMove = !result.getChangesByType(com.syy.taskflowinsight.tracking.ChangeType.MOVE).isEmpty();
        assertTrue(hasMove, "Expected at least one MOVE change");

        FieldChange move = result.getChangesByType(com.syy.taskflowinsight.tracking.ChangeType.MOVE).stream()
            .findFirst().orElseThrow();

        assertTrue(move.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, move.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MOVE, move.getElementEvent().getOperation());
        assertNotNull(move.getElementEvent().getOldIndex());
        assertNotNull(move.getElementEvent().getNewIndex());
    }

    @Entity
    static class DUser {
        @Key
        private final String id;
        private final String name;

        DUser(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public String getName() { return name; }
        // 不覆写equals/hashCode，允许按对象身份重复（以触发“重复键”场景）
    }

    @Test
    void set_entity_with_duplicate_keys_should_emit_container_events_for_each_instance() {
        SetCompareStrategy strategy = new SetCompareStrategy();

        // 旧集合包含两个相同@Key（K1）的不同实例，新集合包含一个相同@Key（K1）
        Set<DUser> oldSet = new HashSet<>(Arrays.asList(
            new DUser("K1", "Alice"),
            new DUser("K1", "Alice-2")
        ));
        Set<DUser> newSet = new HashSet<>(Arrays.asList(
            new DUser("K1", "Bob")
        ));

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.builder().strictDuplicateKey(false).build());
        assertFalse(result.getChanges().isEmpty());

        // 由于重复键，按“独立实例”处理：1 CREATE + 2 DELETE = 3 变更
        assertTrue(result.getChanges().size() >= 3, "Expect at least 3 changes for duplicate-key scenario");

        long containerChanges = result.getContainerChanges().size();
        assertTrue(containerChanges >= 3, "All changes should be container element changes");

        // 不强制容器类型（EntitySet场景由 EntityListStrategy 委托，容器类型为 LIST）
        assertTrue(result.getContainerChanges().stream().allMatch(c -> c.getElementEvent() != null));
    }

    @Test
    void array_container_compared_as_list_should_emit_events_with_index() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        Integer[] oldArr = new Integer[] {1, 2, 3};
        Integer[] newArr = new Integer[] {1, 4, 3};

        // 将数组按List处理（当前引擎未提供独立Array策略）
        CompareResult result = strategy.compare(Arrays.asList(oldArr), Arrays.asList(newArr), CompareOptions.DEFAULT);
        assertFalse(result.getChanges().isEmpty());

        FieldChange change = result.getChanges().get(0);
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MODIFY, change.getElementEvent().getOperation());
        assertNotNull(change.getElementEvent().getIndex());
    }
}
