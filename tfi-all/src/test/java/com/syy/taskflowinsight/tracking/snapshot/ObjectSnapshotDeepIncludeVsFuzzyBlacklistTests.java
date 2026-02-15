package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectSnapshotDeepIncludeVsFuzzyBlacklistTests {

    static class Detail { String internalId; Detail(String id){ this.internalId=id; } }
    static class Item { List<Detail> details = new ArrayList<>(); }
    static class Order { List<Item> items = new ArrayList<>(); }

    private Order sample() {
        Order o = new Order();
        Item it = new Item(); it.details.add(new Detail("D1")); it.details.add(new Detail("D2"));
        o.items.add(it);
        return o;
    }

    @Test
    void include_precise_path_should_override_fuzzy_internal_blacklist_with_arrays() {
        SnapshotConfig config = new SnapshotConfig();
        config.setIncludePatterns(new ArrayList<>(List.of("items[*].details[*].internalId")));
        config.setExcludePatterns(new ArrayList<>(List.of("*.internal.*")));

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        Map<String, Object> flat = snapshot.captureDeep(sample(), 10,
            Collections.emptySet(), Collections.emptySet());

        assertTrue(flat.containsKey("items[0].details[0].internalId"));
        assertTrue(flat.containsKey("items[0].details[1].internalId"));
    }
}

