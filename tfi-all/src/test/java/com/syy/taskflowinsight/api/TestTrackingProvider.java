package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试专用typed TrackingProvider。
 *
 * <p>只记录资源生命周期，刻意不接收或执行action，用于反证facade不会把sequencing下放给provider。</p>
 */
public class TestTrackingProvider implements TrackingProvider {

    /** 按发生顺序记录begin/capture/close调用。 */
    private final List<String> methodCalls = new CopyOnWriteArrayList<>();
    /** 测试可观察的最近batch目标引用。 */
    private final Map<String, Object> trackedObjects = new ConcurrentHashMap<>();

    @Override
    public TrackingBatchScope begin(
            List<TrackingExecutor.Target> targets,
            CompareOptions options) {
        methodCalls.add("begin:" + targets.size());
        targets.forEach(target -> trackedObjects.put(target.name(), target.value()));
        return new TrackingBatchScope() {
            /** scope是否已关闭。 */
            private boolean closed;

            @Override
            public List<TrackingExecutor.Item> capture() {
                methodCalls.add("capture");
                return targets.stream()
                        .map(target -> new TrackingExecutor.Item(
                                target.name(), CompareResult.identical()))
                        .toList();
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    methodCalls.add("close");
                }
            }
        };
    }

    public boolean wasMethodCalled(String methodPrefix) {
        return methodCalls.stream().anyMatch(call -> call.startsWith(methodPrefix));
    }

    public int getMethodCallCount(String methodPrefix) {
        return (int) methodCalls.stream().filter(call -> call.startsWith(methodPrefix)).count();
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
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
