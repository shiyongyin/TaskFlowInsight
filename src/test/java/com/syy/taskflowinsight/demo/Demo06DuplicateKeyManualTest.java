package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 手动测试Demo06的重复@Key场景
 *
 * @author TaskFlow Insight Team
 */
class Demo06DuplicateKeyManualTest {

    @Entity(name = "Product")
    public static class ProductWithFullEquals {
        @Key
        private Long productId;
        private String name;
        private Double price;
        private Integer stock;

        public ProductWithFullEquals(Long productId, String name, Double price, Integer stock) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        public Long getProductId() { return productId; }
        public String getName() { return name; }
        public Double getPrice() { return price; }
        public Integer getStock() { return stock; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProductWithFullEquals that = (ProductWithFullEquals) o;
            return Objects.equals(productId, that.productId) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(price, that.price) &&
                   Objects.equals(stock, that.stock);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, name, price, stock);
        }

        @Override
        public String toString() {
            return String.format("Product[id=%d, name=%s, price=%.2f, stock=%d]",
                productId, name, price, stock);
        }
    }

    @Test
    void testDuplicateKeyScenario() {
        System.out.println("\n【场景6】重复@Key场景 - equals/hashCode与@Key不一致");
        System.out.println("=".repeat(80));

        // 旧集合
        Set<ProductWithFullEquals> set1 = new HashSet<>();
        set1.add(new ProductWithFullEquals(1L, "Laptop", 999.99, 10));
        set1.add(new ProductWithFullEquals(2L, "Mouse", 29.99, 50));

        // 新集合：包含两个id=1的Product（但其他字段不同）
        Set<ProductWithFullEquals> set2 = new HashSet<>();
        set2.add(new ProductWithFullEquals(1L, "Laptop", 999.99, 10));
        set2.add(new ProductWithFullEquals(1L, "Gaming Laptop", 1499.99, 5));
        set2.add(new ProductWithFullEquals(2L, "Mouse", 29.99, 50));

        System.out.println("旧集合: " + set1);
        System.out.println("新集合: " + set2);
        System.out.println();

        // 使用 EntityListStrategy 比较
        EntityListStrategy strategy = new EntityListStrategy();
        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().build()
        );

        System.out.println("比较结果:");
        System.out.println("  变更数量: " + result.getChangeCount());
        System.out.println("  包含重复Keys: " + result.hasDuplicateKeys());
        if (result.hasDuplicateKeys()) {
            System.out.println("  重复Keys: " + result.getDuplicateKeys());
        }
        System.out.println();

        System.out.println("详细变更:");
        result.getChanges().forEach(change -> {
            System.out.printf("  %s | %s | %s → %s%n",
                change.getFieldName(),
                change.getChangeType(),
                change.getOldValue(),
                change.getNewValue());
        });

        // 断言
        assertTrue(result.hasDuplicateKeys(), "应该检测到重复key");
        assertTrue(result.getDuplicateKeys().contains("1"), "应该包含重复的key=1");
        assertTrue(result.getChangeCount() >= 2, "应该至少有2个变更（DELETE + CREATE）");

        // 验证entity[1#0]或entity[1#1]格式
        boolean hasIndexedKey = result.getChanges().stream()
            .anyMatch(c -> c.getFieldName().contains("#"));
        assertTrue(hasIndexedKey, "应该包含带#idx的entity key");

        System.out.println("\n✅ 重复@Key场景测试通过！");
    }
}
