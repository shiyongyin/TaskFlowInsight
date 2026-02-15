package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepPathIntegrationTests {

    static class Order {
        List<Item> items = new ArrayList<>();
        String debug_0001;
    }

    static class Item {
        String id;
        String internalId;
        Item(String id, String internalId) {
            this.id = id;
            this.internalId = internalId;
        }
    }

    @Test
    void path_engine_should_filter_glob_and_regex_in_snapshot() {
        // Arrange: config with glob and regex excludes
        SnapshotConfig config = new SnapshotConfig();
        config.setIncludePatterns(new ArrayList<>()); // no include filtering
        config.setExcludePatterns(new ArrayList<>(List.of("items[*].internalId")));
        config.setRegexExcludes(new ArrayList<>(List.of("^debug_\\d{4}$")));

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);

        Order order = new Order();
        order.items.add(new Item("P1", "SECRET-1"));
        order.items.add(new Item("P2", "SECRET-2"));
        order.debug_0001 = "X";

        // Act
        Map<String, Object> flat = snapshot.captureDeep(order, 10, Collections.emptySet(), Collections.emptySet());

        // Assert: id fields kept, internalId filtered by glob
        assertTrue(flat.containsKey("items[0].id"), "id should be captured");
        assertTrue(flat.containsKey("items[1].id"), "id should be captured");
        assertFalse(flat.containsKey("items[0].internalId"), "internalId should be filtered by glob");
        assertFalse(flat.containsKey("items[1].internalId"), "internalId should be filtered by glob");

        // Assert: top-level regex exclude filtered
        assertFalse(flat.containsKey("debug_0001"), "debug_0001 should be filtered by regex");
    }
}

