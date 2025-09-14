package com.syy.taskflowinsight.demo.model;

import java.util.Map;

/**
 * 演示用的订单实体。
 */
public class Order {
    private String orderId;
    private String userId;
    /** 商品名称 -> 数量 */
    private Map<String, Integer> items;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, Integer> getItems() {
        return items;
    }

    public void setItems(Map<String, Integer> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", items=" + items +
                '}';
    }
}

