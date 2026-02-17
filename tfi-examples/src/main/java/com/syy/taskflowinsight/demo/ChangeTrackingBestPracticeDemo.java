package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.Arrays;
import java.util.List;

/**
 * Change tracking best practices demo using the TFI facade API exclusively.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Basic types, dates, Entity/ValueObject comparison</li>
 *   <li>Collection tracking (List, Set, Map)</li>
 *   <li>Performance tips (shallow vs deep, field selection)</li>
 *   <li>Error handling and cleanup patterns</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class ChangeTrackingBestPracticeDemo {

    private static final String SEP = "=".repeat(80);
    private static final String SUB = "-".repeat(60);

    public static void main(String[] args) {
        printHeader();
        try {
            TFI.enable();
            TFI.setChangeTrackingEnabled(true);

            demoBasicTypes();
            demoEntityValueObject();
            demoCollections();
            demoPerformanceTips();
            demoCleanupPatterns();

            printFooter();
        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Best practice: Use track() with explicit fields for performance.
     */
    private static void demoBasicTypes() {
        printSection("1. Basic Types – Explicit Field Tracking");

        Product before = new Product(1L, "Widget", 9.99, 100);
        Product after = new Product(1L, "Widget Pro", 12.99, 80);

        CompareResult result = TFI.compare(before, after);
        System.out.println("\nCompare result: " + result.getChanges().size() + " changes");
        result.getChanges().forEach(fc ->
            System.out.printf("  %s: %s → %s [%s]%n",
                fc.getFieldName(), fc.getOldValue(), fc.getNewValue(), fc.getChangeType()));

        System.out.println("\nTip: Use TFI.track(name, obj, \"field1\", \"field2\") to limit tracked fields.");
    }

    /**
     * Best practice: Entity vs ValueObject semantics via annotations.
     */
    private static void demoEntityValueObject() {
        printSection("2. Entity & ValueObject Best Practices");

        Product p1 = createProduct(1L, "Acme", "NYC");
        Product p2 = createProduct(1L, "Acme", "Boston");
        p2.getShippingAddress().setCity("Boston");

        CompareResult result = TFI.compare(p1, p2);
        System.out.println("\nProduct (Entity) with nested Supplier (Entity) and Address (ValueObject):");
        System.out.println("  Changes: " + result.getChanges().size());
        String report = TFI.render(result, "standard");
        System.out.println(report);

        System.out.println("Tip: @Entity uses @Key for matching; @ValueObject compares by value.");
    }

    /**
     * Best practice: Collection tracking with appropriate strategy.
     */
    private static void demoCollections() {
        printSection("3. Collection Best Practices");

        List<String> list1 = Arrays.asList("A", "B", "C", "D");
        List<String> list2 = Arrays.asList("A", "D", "B", "C");

        CompareResult simple = TFI.comparator().withStrategyName("SIMPLE").compare(list1, list2);
        CompareResult asSet = TFI.comparator().withStrategyName("AS_SET").compare(list1, list2);
        CompareResult lev = TFI.comparator().withStrategyName("LEVENSHTEIN").detectMoves().compare(list1, list2);

        System.out.println("\nList " + list1 + " → " + list2);
        System.out.println("  SIMPLE (position): " + simple.getChanges().size() + " changes");
        System.out.println("  AS_SET (unordered): " + asSet.getChanges().size() + " changes");
        System.out.println("  LEVENSHTEIN (moves): " + lev.getChanges().size() + " changes");

        System.out.println("\nTip: Use LEVENSHTEIN for move detection; AS_SET when order does not matter.");
    }

    /**
     * Best practice: Performance tips.
     */
    private static void demoPerformanceTips() {
        printSection("4. Performance Tips");

        System.out.println("\n1. Shallow vs deep:");
        System.out.println("   • track(name, obj, \"field1\") – shallow, fast");
        System.out.println("   • trackDeep(name, obj) – full graph, use with max-depth for large objects");

        System.out.println("\n2. Field selection:");
        System.out.println("   • Prefer explicit fields: track(\"order\", order, \"status\", \"amount\")");
        System.out.println("   • Avoid tracking large collections or binary fields");

        System.out.println("\n3. Cleanup:");
        System.out.println("   • Call TFI.clearAllTracking() after processing");
        System.out.println("   • Use TFI.withTracked() for automatic cleanup");

        System.out.println("\n4. Comparator options:");
        System.out.println("   • withMaxDepth(n) – limit recursion");
        System.out.println("   • ignoring(\"field\") – exclude from comparison");
    }

    /**
     * Best practice: Cleanup patterns.
     */
    private static void demoCleanupPatterns() {
        printSection("5. Cleanup Patterns");

        TFI.clearAllTracking();

        Product product = new Product(99L, "Temp", 1.0, 1);
        TFI.track("temp", product, "name");
        product.setName("Modified");
        List<ChangeRecord> changes = TFI.getChanges();
        System.out.println("\nBefore cleanup: " + changes.size() + " changes");

        TFI.clearAllTracking();
        List<ChangeRecord> afterCleanup = TFI.getChanges();
        System.out.println("After clearAllTracking(): " + afterCleanup.size() + " changes");

        System.out.println("\nPattern: withTracked auto-cleans:");
        TFI.withTracked("auto", product, () -> product.setName("AutoCleaned"), "name");
        System.out.println("  Changes cleared automatically after withTracked.");
    }

    private static Product createProduct(Long id, String supplierCity, String addrCity) {
        Product p = new Product(id, "Product", 10.0, 50);
        p.setSupplier(new Supplier(10L, "Supplier", supplierCity, "NY"));
        p.setWarehouse(new Warehouse(1L, "US-EAST", "Hub", 500));
        p.setShippingAddress(new Address(addrCity, "NY", "100 Main St"));
        return p;
    }

    private static void printHeader() {
        System.out.println("\n" + SEP);
        System.out.println("TaskFlowInsight Change Tracking Best Practices Demo");
        System.out.println(SEP);
    }

    private static void printFooter() {
        System.out.println("\n" + SEP);
        System.out.println("Demo complete.");
        System.out.println(SEP);
    }

    private static void printSection(String title) {
        System.out.println("\n" + SUB);
        System.out.println(title);
        System.out.println(SUB);
    }
}
