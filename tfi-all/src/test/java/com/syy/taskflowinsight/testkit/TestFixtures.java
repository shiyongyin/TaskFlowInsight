package com.syy.taskflowinsight.testkit;

import com.syy.taskflowinsight.core.TfiCore;

import java.util.*;

/**
 * Reusable test fixtures for TFI unit and integration tests.
 *
 * <p>Centralizes object creation patterns that are duplicated across
 * many test classes, reducing boilerplate and improving consistency.
 *
 * <pre>{@code
 * var user = TestFixtures.sampleUser();
 * var order = TestFixtures.sampleOrder();
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public final class TestFixtures {

    private TestFixtures() {
        throw new AssertionError("utility class");
    }

    // ─────────────────────────────────────────────────────────────
    //  TfiCore factories
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a ready-to-use TfiCore with default enabled config.
     */
    public static TfiCore enabledCore() {
        return new TfiCore(true, true);
    }

    // ─────────────────────────────────────────────────────────────
    //  Sample domain objects for tracking / comparison tests
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a sample user Map for change-tracking tests.
     */
    public static Map<String, Object> sampleUser() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", 1001L);
        user.put("name", "Alice");
        user.put("email", "alice@example.com");
        user.put("age", 28);
        user.put("active", true);
        return user;
    }

    /**
     * Creates a modified version of the sample user.
     */
    public static Map<String, Object> modifiedUser() {
        Map<String, Object> user = sampleUser();
        user.put("name", "Alice Updated");
        user.put("email", "alice.new@example.com");
        user.put("age", 29);
        return user;
    }

    /**
     * Creates a sample order Map for change-tracking tests.
     */
    public static Map<String, Object> sampleOrder() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", "ORD-001");
        order.put("status", "PENDING");
        order.put("amount", 199.99);
        order.put("items", List.of("iPhone", "AirPods"));
        return order;
    }

    /**
     * Creates a modified version of the sample order.
     */
    public static Map<String, Object> modifiedOrder() {
        Map<String, Object> order = sampleOrder();
        order.put("status", "SHIPPED");
        order.put("amount", 179.99);
        order.put("items", List.of("iPhone", "AirPods", "MacBook"));
        return order;
    }

    // ─────────────────────────────────────────────────────────────
    //  Bulk data factories for performance/concurrency tests
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates N sample user objects with unique IDs.
     */
    public static List<Map<String, Object>> bulkUsers(int count) {
        List<Map<String, Object>> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", (long) (1000 + i));
            user.put("name", "User-" + i);
            user.put("email", "user" + i + "@example.com");
            user.put("age", 20 + (i % 50));
            user.put("active", i % 3 != 0);
            users.add(user);
        }
        return users;
    }

    /**
     * Creates N named targets for explicit {@code TrackingExecutor.execute(...)} batch tests.
     */
    public static Map<String, Object> bulkTrackedTargets(int count) {
        Map<String, Object> targets = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("id", (long) i);
            obj.put("value", "data-" + i);
            targets.put("target-" + i, obj);
        }
        return targets;
    }
}
