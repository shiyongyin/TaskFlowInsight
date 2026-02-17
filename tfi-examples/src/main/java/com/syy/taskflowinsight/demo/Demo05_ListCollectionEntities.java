package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.model.Address;
import com.syy.taskflowinsight.demo.model.Product;
import com.syy.taskflowinsight.demo.model.Supplier;
import com.syy.taskflowinsight.demo.model.Warehouse;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo05: Entity list comparison using the TFI facade API.
 *
 * <p>Demonstrates the recommended {@code TFI.comparator()} facade for comparing lists of
 * {@code @Entity} objects with various nesting scenarios. Uses shared model classes from
 * {@code com.syy.taskflowinsight.demo.model}.</p>
 *
 * <h3>Test scenarios</h3>
 * <ol>
 *   <li><b>Simple entity list</b> — Products with single {@code @Key}; add/remove/update</li>
 *   <li><b>Nested entity (deep)</b> — Product with {@link Supplier}; deep comparison</li>
 *   <li><b>Nested entity (shallow)</b> — Product with {@link Warehouse} {@code @ShallowReference}</li>
 *   <li><b>Nested value object</b> — Product with {@link Address} {@code @ValueObject}</li>
 * </ol>
 *
 * <h3>Recommended API</h3>
 * <pre>{@code
 * // Entity list comparison (recommended)
 * CompareResult result = TFI.comparator()
 *     .typeAware()
 *     .withStrategyName("ENTITY")
 *     .compare(list1, list2);
 *
 * // Render to Markdown
 * String markdown = TFI.render(result, "standard");
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class Demo05_ListCollectionEntities {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("Demo05: Entity list comparison (TFI.comparator() facade API)");
        System.out.println("================================================================================");

        TFI.enable();

        // Scenario 1: Simple entity list (single @Key)
        runScenario1();

        // Scenario 2: Entity nested Entity (deep comparison)
        runScenario2();

        // Scenario 3: Entity nested Entity (@ShallowReference)
        runScenario3();

        // Scenario 4: Entity nested ValueObject
        runScenario4();

        System.out.println("\n================================================================================");
        System.out.println("All scenarios completed.");
        System.out.println("================================================================================");
    }

    /**
     * Scenario 1: Simple entity list — Products with single @Key; add/remove/update.
     */
    private static void runScenario1() {
        System.out.println("\n【Scenario 1】Simple entity list (single @Key)");
        System.out.println("-".repeat(80));

        List<Product> list1 = new ArrayList<>();
        list1.add(new Product(1L, "Laptop", 999.99, 10));
        list1.add(new Product(2L, "Mouse", 29.99, 50));
        list1.add(new Product(5L, "Tablet", 888.99, 2));
        list1.add(new Product(3L, "Keyboard", 79.99, 30));

        List<Product> list2 = new ArrayList<>();
        list2.add(new Product(1L, "Laptop", 1099.99, 8));    // price, stock changed
        list2.add(new Product(2L, "Mouse", 29.99, 50));      // unchanged
        list2.add(new Product(4L, "Monitor", 399.99, 15));   // added
        list2.add(new Product(5L, "Tablet", 1099.99, 5));     // changed; ID=3 removed

        CompareResult result = TFI.comparator()
                .typeAware()
                .withStrategyName("ENTITY")
                .compare(list1, list2);

        printResult(result);
    }

    /**
     * Scenario 2: Entity nested Entity (deep) — Product with Supplier; deep comparison.
     */
    private static void runScenario2() {
        System.out.println("\n【Scenario 2】Nested Entity (deep) — Product with Supplier");
        System.out.println("-".repeat(80));

        List<Product> list1 = new ArrayList<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setSupplier(new Supplier(100L, "TechCorp", "San Francisco", "CA"));
        list1.add(p1);
        Product p2 = new Product(2L, "Mouse", 29.99, 50);
        p2.setSupplier(new Supplier(200L, "MouseCo", "Los Angeles", "CA"));
        list1.add(p2);
        Product p3 = new Product(3L, "Keyboard", 79.99, 30);
        p3.setSupplier(new Supplier(300L, "KeyCorp", "Seattle", "WA"));
        list1.add(p3);
        Product p5 = new Product(5L, "Tablet", 888.99, 2);
        p5.setSupplier(new Supplier(500L, "TabCorp", "San Francisco", "CA"));
        list1.add(p5);

        List<Product> list2 = new ArrayList<>();
        Product p1n = new Product(1L, "Laptop", 1099.99, 8);
        p1n.setSupplier(new Supplier(100L, "TechCorp", "New York", "NY"));
        list2.add(p1n);
        Product p2n = new Product(2L, "Mouse", 29.99, 50);
        p2n.setSupplier(new Supplier(200L, "MouseCo", "Los Angeles", "CA"));
        list2.add(p2n);
        Product p4n = new Product(4L, "Monitor", 399.99, 15);
        p4n.setSupplier(new Supplier(400L, "MonCorp", "Chicago", "IL"));
        list2.add(p4n);
        Product p5n = new Product(5L, "Tablet", 1099.99, 5);
        p5n.setSupplier(new Supplier(500L, "TabCorp", "New York", "NY"));
        list2.add(p5n);

        CompareResult result = TFI.comparator()
                .typeAware()
                .withStrategyName("ENTITY")
                .compare(list1, list2);

        printResult(result);
    }

    /**
     * Scenario 3: Entity nested Entity (@ShallowReference) — Product with Warehouse.
     */
    private static void runScenario3() {
        System.out.println("\n【Scenario 3】Nested Entity (@ShallowReference) — Product with Warehouse");
        System.out.println("-".repeat(80));

        List<Product> list1 = new ArrayList<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setWarehouse(new Warehouse(1001L, "US", "California", 1000));
        list1.add(p1);
        Product p2 = new Product(2L, "Mouse", 29.99, 50);
        p2.setWarehouse(new Warehouse(2001L, "EU", "Berlin", 500));
        list1.add(p2);
        Product p3 = new Product(3L, "Keyboard", 79.99, 30);
        p3.setWarehouse(new Warehouse(3001L, "US", "Texas", 800));
        list1.add(p3);
        Product p5 = new Product(5L, "Tablet", 888.99, 2);
        p5.setWarehouse(new Warehouse(5001L, "EU", "Paris", 300));
        list1.add(p5);

        List<Product> list2 = new ArrayList<>();
        Product p1n = new Product(1L, "Laptop", 1099.99, 8);
        p1n.setWarehouse(new Warehouse(1002L, "US", "Nevada", 1200));
        list2.add(p1n);
        Product p2n = new Product(2L, "Mouse", 29.99, 50);
        p2n.setWarehouse(new Warehouse(2001L, "EU", "Berlin", 600));
        list2.add(p2n);
        Product p4n = new Product(4L, "Monitor", 399.99, 15);
        p4n.setWarehouse(new Warehouse(4001L, "CN", "Shanghai", 2000));
        list2.add(p4n);
        Product p5n = new Product(5L, "Tablet", 1099.99, 5);
        p5n.setWarehouse(new Warehouse(5002L, "EU", "Madrid", 400));
        list2.add(p5n);

        CompareResult result = TFI.comparator()
                .typeAware()
                .withStrategyName("ENTITY")
                .compare(list1, list2);

        printResult(result);
    }

    /**
     * Scenario 4: Entity nested ValueObject — Product with Address.
     */
    private static void runScenario4() {
        System.out.println("\n【Scenario 4】Nested ValueObject — Product with Address");
        System.out.println("-".repeat(80));

        List<Product> list1 = new ArrayList<>();
        Product p1 = new Product(1L, "Laptop", 999.99, 10);
        p1.setShippingAddress(new Address("San Francisco", "CA", "123 Main St"));
        list1.add(p1);
        Product p2 = new Product(2L, "Mouse", 29.99, 50);
        p2.setShippingAddress(new Address("Los Angeles", "CA", "456 Oak Ave"));
        list1.add(p2);
        Product p3 = new Product(3L, "Keyboard", 79.99, 30);
        p3.setShippingAddress(new Address("Seattle", "WA", "789 Pine Rd"));
        list1.add(p3);
        Product p5 = new Product(5L, "Tablet", 888.99, 2);
        p5.setShippingAddress(new Address("San Francisco", "CA", "321 Market St"));
        list1.add(p5);

        List<Product> list2 = new ArrayList<>();
        Product p1n = new Product(1L, "Laptop", 1099.99, 8);
        p1n.setShippingAddress(new Address("New York", "NY", "100 Broadway"));
        list2.add(p1n);
        Product p2n = new Product(2L, "Mouse", 29.99, 50);
        p2n.setShippingAddress(new Address("Los Angeles", "CA", "456 Oak Ave"));
        list2.add(p2n);
        Product p4n = new Product(4L, "Monitor", 399.99, 15);
        p4n.setShippingAddress(new Address("Chicago", "IL", "200 Lake St"));
        list2.add(p4n);
        Product p5n = new Product(5L, "Tablet", 1099.99, 5);
        p5n.setShippingAddress(new Address("New York", "NY", "500 5th Ave"));
        list2.add(p5n);

        CompareResult result = TFI.comparator()
                .typeAware()
                .withStrategyName("ENTITY")
                .compare(list1, list2);

        printResult(result);
    }

    private static void printResult(CompareResult result) {
        if (result.getChanges().isEmpty()) {
            System.out.println("No changes detected.");
        } else {
            String markdown = TFI.render(result, "standard");
            System.out.println(markdown);
        }
    }
}
