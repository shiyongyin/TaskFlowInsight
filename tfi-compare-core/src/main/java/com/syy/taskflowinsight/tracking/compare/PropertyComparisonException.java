package com.syy.taskflowinsight.tracking.compare;

/**
 * 字段级比较异常：比较器实现失败时抛出，上层应记录并降级。
 */
public class PropertyComparisonException extends RuntimeException {
    public PropertyComparisonException(String message) { super(message); }
    public PropertyComparisonException(String message, Throwable cause) { super(message, cause); }
}

