package com.syy.taskflowinsight.demo.model;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;

import java.util.Objects;

/**
 * 供应商实体，用于演示单主键 {@code @Entity} 和深度比较场景。
 *
 * <p>以 {@code supplierId} 为唯一标识，相同 ID 的供应商会进行字段级深度比较。</p>
 *
 * @since 3.0.0
 */
@Entity(name = "Supplier")
public class Supplier {

    @Key
    private Long supplierId;

    private String name;
    private String city;
    private String state;

    public Supplier(Long supplierId, String name, String city, String state) {
        this.supplierId = supplierId;
        this.name = name;
        this.city = city;
        this.state = state;
    }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    @Override
    public String toString() {
        return String.format("{id=%d, name=\"%s\", city=\"%s\", state=\"%s\"}", supplierId, name, city, state);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Supplier supplier = (Supplier) o;
        return Objects.equals(supplierId, supplier.supplierId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplierId);
    }
}
