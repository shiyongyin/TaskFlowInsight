package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import com.syy.taskflowinsight.tracking.ChangeType;

class EntityListStrategyContainerEventTests {

    @Entity
    static class User {
        @Key
        private final String id;
        private final String name;

        User(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    @Test
    void entity_create_should_have_list_add_event_with_entity_key() {
        EntityListStrategy strategy = new EntityListStrategy();
        List<User> oldList = Collections.emptyList();
        List<User> newList = List.of(new User("U1", "Alice"));

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);
        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertEquals(ChangeType.CREATE, change.getChangeType());
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, change.getElementEvent().getOperation());
        assertNotNull(change.getElementEvent().getEntityKey());
    }

    @Test
    void entity_delete_should_have_list_remove_event_with_entity_key() {
        EntityListStrategy strategy = new EntityListStrategy();
        List<User> oldList = List.of(new User("U1", "Alice"));
        List<User> newList = Collections.emptyList();

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);
        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertEquals(ChangeType.DELETE, change.getChangeType());
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.REMOVE, change.getElementEvent().getOperation());
        assertNotNull(change.getElementEvent().getEntityKey());
    }
}
