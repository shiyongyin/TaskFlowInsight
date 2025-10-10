package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 EntityListStrategy 重复键场景下将 elementEvent.duplicateKey 标记为 true。
 */
class EntityListDuplicateKeyFlagTests {

    private EntityListStrategy entityStrategy;

    @BeforeEach
    void setUp() {
        entityStrategy = new EntityListStrategy();
    }

    @Test
    void duplicate_keys_set_duplicateKey_flag_on_events() {
        // 两侧都出现重复键 id=1
        Person p1a = new Person(1, "v1");
        Person p1b = new Person(1, "v2");
        Person p1c = new Person(1, "v3");

        List<Person> oldList = Arrays.asList(p1a, p1b);
        List<Person> newList = Collections.singletonList(p1c);

        CompareResult r = entityStrategy.compare(oldList, newList, CompareOptions.DEFAULT);
        assertTrue(r.hasChanges());
        assertTrue(r.hasDuplicateKeys());

        // 至少存在一个事件带有 duplicateKey 标记
        boolean anyFlagged = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
            (fc.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.CREATE
                || fc.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.DELETE)
            && fc.getElementEvent().isDuplicateKey()
        );
        assertTrue(anyFlagged, "重复键场景应设置 duplicateKey 标记");
    }

    @Entity
    private static class Person {
        @Key
        private final int id;
        private final String v;
        Person(int id, String v) { this.id = id; this.v = v; }
    }
}
