package com.syy.taskflowinsight.demo.model;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;

import java.util.Objects;

/**
 * 产品实体，用于演示多种注解组合场景。
 *
 * <p>包含以下演示要素：
 * <ul>
 *   <li>{@code @Key} 单主键：{@code productId}</li>
 *   <li>嵌套 {@link Entity}（深度比较）：{@link Supplier}</li>
 *   <li>{@code @ShallowReference}（浅引用，仅比较 Key）：{@link Warehouse}</li>
 *   <li>嵌套 {@link com.syy.taskflowinsight.annotation.ValueObject}：{@link Address}</li>
 * </ul></p>
 *
 * @since 3.0.0
 */
@Entity(name = "Product")
public class Product {

    @Key
    private Long productId;

    private String name;
    private Double price;
    private Integer stock;

    /** 嵌套 Entity — 深度比较。 */
    private Supplier supplier;

    /** 浅引用 — 仅比较 Key 字段。 */
    @ShallowReference
    private Warehouse warehouse;

    /** 嵌套 ValueObject — 值对象整体比较。 */
    private Address shippingAddress;

    public Product(Long productId, String name, Double price, Integer stock) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{id=").append(productId)
          .append(", name=\"").append(name).append("\"")
          .append(", price=").append(String.format("%.2f", price))
          .append(", stock=").append(stock);
        if (supplier != null) {
            sb.append(", supplier: ").append(supplier);
        }
        if (warehouse != null) {
            sb.append(", warehouse.key: {id=").append(warehouse.getWarehouseId())
              .append(", regionCode=\"").append(warehouse.getRegionCode()).append("\"}");
        }
        if (shippingAddress != null) {
            sb.append(", addr: ").append(shippingAddress);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(productId, product.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
}
