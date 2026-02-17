package com.syy.taskflowinsight.demo.model;

import com.syy.taskflowinsight.annotation.ValueObject;

import java.util.Objects;

/**
 * 地址值对象，用于演示 {@code @ValueObject} 注解的比对行为。
 *
 * <p>值对象按全部字段进行相等性比较，没有独立身份标识。</p>
 *
 * @since 3.0.0
 */
@ValueObject
public class Address {

    private String city;
    private String state;
    private String street;

    public Address(String city, String state, String street) {
        this.city = city;
        this.state = state;
        this.street = street;
    }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    @Override
    public String toString() {
        return String.format("{city=\"%s\", state=\"%s\", street=\"%s\"}", city, state, street);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(city, address.city)
                && Objects.equals(state, address.state)
                && Objects.equals(street, address.street);
    }

    @Override
    public int hashCode() {
        return Objects.hash(city, state, street);
    }
}
