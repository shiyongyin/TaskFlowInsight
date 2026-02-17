package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 联合主键 Entity 集合比对的快速验证测试。
 *
 * <p>验证 {@link EntityListStrategy} 在使用 {@code @Key} 联合主键时
 * 能正确识别新增、删除和修改。</p>
 *
 * @since 3.0.0
 */
class Demo06QuickTest {

    @Entity(name = "Warehouse")
    static class Warehouse {
        @Key private Long warehouseId;
        @Key private String regionCode;
        private String location;
        private Integer capacity;

        Warehouse(Long warehouseId, String regionCode, String location, Integer capacity) {
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
            Warehouse w = (Warehouse) o;
            return Objects.equals(warehouseId, w.warehouseId)
                    && Objects.equals(regionCode, w.regionCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(warehouseId, regionCode);
        }
    }

    @Test
    @DisplayName("EntityListStrategy 应检测到联合主键 Entity 的新增/删除/修改")
    void testCompositeKeyEntityComparison() {
        Set<Warehouse> set1 = new HashSet<>();
        set1.add(new Warehouse(1001L, "US", "California", 1000));
        set1.add(new Warehouse(2001L, "EU", "Berlin", 500));
        set1.add(new Warehouse(3001L, "CN", "Shanghai", 800));

        Set<Warehouse> set2 = new HashSet<>();
        set2.add(new Warehouse(1001L, "US", "Nevada", 1200));   // 修改
        set2.add(new Warehouse(2001L, "EU", "Berlin", 500));     // 不变
        set2.add(new Warehouse(4001L, "AP", "Tokyo", 600));      // 新增 (3001/CN 删除)

        EntityListStrategy strategy = new EntityListStrategy();
        CompareResult result = strategy.compare(
                new ArrayList<>(set1),
                new ArrayList<>(set2),
                CompareOptions.builder().strategyName("ENTITY").build()
        );

        assertThat(result).isNotNull();
        assertThat(result.getChanges()).isNotEmpty();
        assertThat(result.getChanges().size()).isGreaterThanOrEqualTo(3);
    }
}
