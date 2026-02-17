package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive change tracking demo using the TFI facade API exclusively.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Object comparison via {@code TFI.compare()} and {@code TFI.comparator()}</li>
 *   <li>Change tracking via {@code TFI.track()}, {@code TFI.trackDeep()}, {@code TFI.getChanges()}</li>
 *   <li>Convenience API {@code TFI.withTracked()}</li>
 *   <li>List comparison strategies (SIMPLE, AS_SET, LEVENSHTEIN)</li>
 *   <li>Shared model classes: Address, Product, Supplier, Warehouse</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class ChangeTrackingComprehensiveDemo {

    private static final String SEP = "=".repeat(80);
    private static final String SUB = "-".repeat(60);

    public static void main(String[] args) {
        System.out.println(SEP);
        System.out.println("TaskFlowInsight Change Tracking Comprehensive Demo (Facade API)");
        System.out.println(SEP);

        TFI.enable();
        TFI.setChangeTrackingEnabled(true);

        demoBasicComparison();
        demoTrackAndGetChanges();
        demoDeepTracking();
        demoListStrategies();
        demoSharedModels();
        demoWithTracked();

        System.out.println("\n" + SEP);
        System.out.println("Demo complete.");
        System.out.println(SEP);
    }

    /**
     * Basic object comparison using TFI.compare().
     */
    private static void demoBasicComparison() {
        System.out.println("\n" + SUB);
        System.out.println("1. Basic Comparison (TFI.compare)");
        System.out.println(SUB);

        Address before = new Address("NYC", "NY", "123 Main St");
        Address after = new Address("LA", "CA", "456 Broadway");

        CompareResult result = TFI.compare(before, after);
        System.out.println("\nAddress comparison:");
        System.out.println("  Identical: " + result.isIdentical());
        System.out.println("  Changes: " + result.getChanges().size());
        for (FieldChange fc : result.getChanges()) {
            System.out.printf("  - %s: %s → %s [%s]%n",
                fc.getFieldName(), fc.getOldValue(), fc.getNewValue(), fc.getChangeType());
        }

        String report = TFI.render(result, "standard");
        System.out.println("\nRendered report (standard):");
        System.out.println(report);
    }

    /**
     * Track → mutate → getChanges workflow.
     */
    private static void demoTrackAndGetChanges() {
        System.out.println("\n" + SUB);
        System.out.println("2. Track → Mutate → getChanges");
        System.out.println(SUB);

        TFI.clearAllTracking();

        Product product = new Product(1L, "Widget", 9.99, 100);
        product.setSupplier(new Supplier(10L, "Acme", "NYC", "NY"));
        product.setShippingAddress(new Address("NYC", "NY", "100 Main St"));

        TFI.track("product", product, "name", "price", "stock");
        product.setName("Widget Pro");
        product.setPrice(12.99);
        product.setStock(80);

        List<ChangeRecord> changes = TFI.getChanges();
        System.out.println("\nDetected " + changes.size() + " changes:");
        for (ChangeRecord cr : changes) {
            System.out.printf("  %s.%s: %s → %s [%s]%n",
                cr.getObjectName(), cr.getFieldName(),
                cr.getOldValue(), cr.getNewValue(), cr.getChangeType());
        }

        TFI.clearAllTracking();
    }

    /**
     * Deep tracking for nested objects.
     */
    private static void demoDeepTracking() {
        System.out.println("\n" + SUB);
        System.out.println("3. Deep Tracking (TFI.trackDeep)");
        System.out.println(SUB);

        TFI.clearAllTracking();

        Product product = new Product(2L, "Gadget", 19.99, 50);
        product.setSupplier(new Supplier(20L, "TechCo", "SF", "CA"));
        product.setWarehouse(new Warehouse(1L, "US-EAST", "NYC Hub", 1000));
        product.setShippingAddress(new Address("SF", "CA", "200 Market St"));

        TFI.trackDeep("product", product);
        product.setName("Gadget Plus");
        product.getSupplier().setCity("Oakland");
        product.getShippingAddress().setStreet("300 Market St");

        List<ChangeRecord> changes = TFI.getChanges();
        System.out.println("\nDeep changes detected: " + changes.size());
        for (ChangeRecord cr : changes) {
            System.out.printf("  %s: %s → %s%n",
                cr.getFieldName(), cr.getOldValue(), cr.getNewValue());
        }

        TFI.clearAllTracking();
    }

    /**
     * List comparison with different strategies.
     */
    private static void demoListStrategies() {
        System.out.println("\n" + SUB);
        System.out.println("4. List Strategies (SIMPLE, AS_SET, LEVENSHTEIN)");
        System.out.println(SUB);

        List<String> list1 = Arrays.asList("A", "B", "C", "D");
        List<String> list2 = Arrays.asList("A", "D", "B", "C");

        System.out.println("\nList: " + list1 + " → " + list2);

        CompareResult simple = TFI.comparator()
            .withStrategyName("SIMPLE")
            .compare(list1, list2);
        System.out.println("  SIMPLE: " + simple.getChanges().size() + " changes, identical=" + simple.isIdentical());

        CompareResult asSet = TFI.comparator()
            .withStrategyName("AS_SET")
            .compare(list1, list2);
        System.out.println("  AS_SET: " + asSet.getChanges().size() + " changes, identical=" + asSet.isIdentical());

        CompareResult levenshtein = TFI.comparator()
            .withStrategyName("LEVENSHTEIN")
            .detectMoves()
            .compare(list1, list2);
        System.out.println("  LEVENSHTEIN: " + levenshtein.getChanges().size() + " changes, identical="
            + levenshtein.isIdentical());
    }

    /**
     * Comparison using shared model classes (Product, Supplier, Warehouse, Address).
     */
    private static void demoSharedModels() {
        System.out.println("\n" + SUB);
        System.out.println("5. Shared Model Classes (Product, Supplier, Warehouse, Address)");
        System.out.println(SUB);

        Product before = createSampleProduct(1L);
        Product after = createSampleProduct(1L);
        after.setName("Product Renamed");
        after.setPrice(15.99);
        after.getSupplier().setCity("Boston");
        after.getShippingAddress().setCity("Boston");

        CompareResult result = TFI.compare(before, after);
        System.out.println("\nProduct comparison (Entity with nested Entity/ValueObject):");
        System.out.println("  Identical: " + result.isIdentical());
        System.out.println("  Changes: " + result.getChanges().size());
        String report = TFI.render(result, "detailed");
        System.out.println(report);
    }

    /**
     * Convenience API: TFI.withTracked().
     */
    private static void demoWithTracked() {
        System.out.println("\n" + SUB);
        System.out.println("6. Convenience API (TFI.withTracked)");
        System.out.println(SUB);

        Product product = new Product(3L, "Item", 5.00, 10);
        product.setShippingAddress(new Address("NYC", "NY", "50 Wall St"));

        try (var stage = TFI.stage("withTrackedDemo")) {
            TFI.withTracked("product", product, () -> {
                product.setName("Item Updated");
                product.setStock(5);
            }, "name", "stock");
        }
        System.out.println("\nwithTracked: mutations applied, changes auto-captured and cleared.");
    }

    private static Product createSampleProduct(Long id) {
        Product p = new Product(id, "Sample Product", 10.99, 100);
        p.setSupplier(new Supplier(100L, "Supplier Inc", "NYC", "NY"));
        p.setWarehouse(new Warehouse(1L, "US-EAST", "NYC Hub", 500));
        p.setShippingAddress(new Address("NYC", "NY", "100 Main St"));
        return p;
    }
}
