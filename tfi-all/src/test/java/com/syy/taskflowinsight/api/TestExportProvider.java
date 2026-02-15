package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ExportProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * 测试专用 ExportProvider 实现
 */
public class TestExportProvider implements ExportProvider {

    private final List<String> methodCalls = new CopyOnWriteArrayList<>();
    private boolean exportSuccess = true;

    @Override
    public boolean exportToConsole(boolean showTimestamp) {
        methodCalls.add("exportToConsole:" + showTimestamp);
        return exportSuccess;
    }

    @Override
    public String exportToJson() {
        methodCalls.add("exportToJson");
        return "{\"session\":\"test\"}";
    }

    @Override
    public Map<String, Object> exportToMap() {
        methodCalls.add("exportToMap");
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", "test-123");
        return result;
    }

    // 测试断言辅助方法

    public boolean wasMethodCalled(String methodPrefix) {
        return methodCalls.stream().anyMatch(c -> c.startsWith(methodPrefix));
    }

    public int getMethodCallCount(String methodPrefix) {
        return (int) methodCalls.stream().filter(c -> c.startsWith(methodPrefix)).count();
    }

    public void setExportSuccess(boolean success) {
        this.exportSuccess = success;
    }

    public void reset() {
        methodCalls.clear();
        exportSuccess = true;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
