package com.syy.taskflowinsight.demo.model;

/**
 * 并发下单模拟中的用户下单结果。
 */
public class UserOrderResult {
    private int userId;
    private boolean success;
    private String orderId;
    private String status;
    private String failReason;
    private long processTime;
    /** 可选：用于保存 JSON 报告等 */
    private String report;

    public UserOrderResult() {}

    public UserOrderResult(int userId) {
        this.userId = userId;
        this.success = false;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public long getProcessTime() {
        return processTime;
    }

    public void setProcessTime(long processTime) {
        this.processTime = processTime;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }
}

