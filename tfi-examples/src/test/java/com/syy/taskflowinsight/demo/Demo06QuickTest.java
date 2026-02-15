package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;

import java.util.*;

/**
 * 快速测试联合主键输出格式
 */
public class Demo06QuickTest {

    @Entity(name = "Warehouse")
    public static class Warehouse {
        @Key
        private Long warehouseId;
        @Key
        private String regionCode;
        private String location;
        private Integer capacity;

        public Warehouse(Long warehouseId, String regionCode, String location, Integer capacity) {
            this.warehouseId = warehouseId;
            this.regionCode = regionCode;
            this.location = location;
            this.capacity = capacity;
        }

        public Long getWarehouseId() { return warehouseId; }
        public String getRegionCode() { return regionCode; }
        public String getLocation() { return location; }
        public Integer getCapacity() { return capacity; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Warehouse warehouse = (Warehouse) o;
            return Objects.equals(warehouseId, warehouse.warehouseId) &&
                   Objects.equals(regionCode, warehouse.regionCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(warehouseId, regionCode);
        }

        @Override
        public String toString() {
            return String.format("{id=%d, regionCode=\"%s\", location=\"%s\", capacity=%d}",
                warehouseId, regionCode, location, capacity);
        }
    }

    public static void main(String[] args) {
        System.out.println("测试联合主键输出格式");
        System.out.println("=".repeat(80));

        Set<Warehouse> set1 = new HashSet<>();
        set1.add(new Warehouse(1001L, "US", "California", 1000));
        set1.add(new Warehouse(2001L, "EU", "Berlin", 500));
        set1.add(new Warehouse(3001L, "CN", "Shanghai", 800));

        Set<Warehouse> set2 = new HashSet<>();
        set2.add(new Warehouse(1001L, "US", "Nevada", 1200));
        set2.add(new Warehouse(2001L, "EU", "Berlin", 500));
        set2.add(new Warehouse(4001L, "AP", "Tokyo", 600));

        EntityListStrategy strategy = new EntityListStrategy();
        CompareResult result = strategy.compare(
            new ArrayList<>(set1),
            new ArrayList<>(set2),
            CompareOptions.builder().strategyName("ENTITY").build()
        );

        System.out.println("\n原始变更列表：");
        result.getChanges().forEach(change -> {
            System.out.printf("  %s: %s → %s (%s)\n",
                change.getFieldName(),
                change.getOldValue(),
                change.getNewValue(),
                change.getChangeType());
        });

        System.out.println("\n期望输出格式：");
        System.out.println("  Warehouse[warehouseId=1001, regionCode=\"US\"]");
        System.out.println("  Warehouse[warehouseId=4001, regionCode=\"AP\"]");
        System.out.println("  Warehouse[warehouseId=3001, regionCode=\"CN\"]");
    }
}
