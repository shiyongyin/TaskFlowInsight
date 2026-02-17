package com.syy.taskflowinsight.demo.model;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;

import java.util.Objects;

/**
 * 仓库实体，用于演示联合主键（{@code @Key} 复合键）和 {@code @ShallowReference} 场景。
 *
 * <p>以 {@code warehouseId + regionCode} 为联合主键进行实体匹配。</p>
 *
 * @since 3.0.0
 */
@Entity(name = "Warehouse")
public class Warehouse {

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
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    @Override
    public String toString() {
        return String.format("{id=%d, regionCode=\"%s\", location=\"%s\", capacity=%d}",
                warehouseId, regionCode, location, capacity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Warehouse warehouse = (Warehouse) o;
        return Objects.equals(warehouseId, warehouse.warehouseId)
                && Objects.equals(regionCode, warehouse.regionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, regionCode);
    }
}
