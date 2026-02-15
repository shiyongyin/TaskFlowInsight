package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试专用 TrackingProvider 实现
 * 记录所有方法调用，便于测试断言
 */
public class TestTrackingProvider implements TrackingProvider {

    private final List<String> methodCalls = new CopyOnWriteArrayList<>();
    private final Map<String, Object> trackedObjects = new ConcurrentHashMap<>();
    private final List<ChangeRecord> changeRecords = new CopyOnWriteArrayList<>();

    @Override
    public void track(String name, Object target, String... fields) {
        methodCalls.add("track:" + name);
        trackedObjects.put(name, target);
    }

    @Override
    public List<ChangeRecord> changes() {
        methodCalls.add("changes");
        return new ArrayList<>(changeRecords);
    }

    @Override
    public void clear() {
        methodCalls.add("clear");
        trackedObjects.clear();
        changeRecords.clear();
    }

    @Override
    public void trackAll(Map<String, Object> targets) {
        methodCalls.add("trackAll:" + targets.size());
        trackedObjects.putAll(targets);
    }

    @Override
    public void trackDeep(String name, Object obj, TrackingOptions options) {
        methodCalls.add("trackDeep:" + name);
        trackedObjects.put(name, obj);
    }

    @Override
    public void recordChange(String objectName, String fieldName,
                           Object oldValue, Object newValue, ChangeType changeType) {
        methodCalls.add("recordChange:" + objectName + "." + fieldName);
        ChangeRecord record = ChangeRecord.builder()
            .objectName(objectName)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .changeType(changeType)
            .build();
        changeRecords.add(record);
    }

    @Override
    public void clearTracking(String sessionName) {
        methodCalls.add("clearTracking:" + sessionName);
        trackedObjects.clear();
    }

    @Override
    public void withTracked(String name, Object obj, Runnable action, String... fields) {
        methodCalls.add("withTracked:" + name);
        trackedObjects.put(name, obj);
        if (action != null) {
            action.run();
        }
    }

    // 测试断言辅助方法

    public boolean wasMethodCalled(String methodPrefix) {
        return methodCalls.stream().anyMatch(c -> c.startsWith(methodPrefix));
    }

    public int getMethodCallCount(String methodPrefix) {
        return (int) methodCalls.stream().filter(c -> c.startsWith(methodPrefix)).count();
    }

    public int getTotalCallCount() {
        return methodCalls.size();
    }

    public int getTrackedCount() {
        return trackedObjects.size();
    }

    public void reset() {
        methodCalls.clear();
        trackedObjects.clear();
        changeRecords.clear();
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE; // 测试优先级最高
    }
}
