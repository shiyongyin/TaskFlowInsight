package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 变更追踪统计信息
 * 提供追踪过程的统计数据和分析
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class TrackingStatistics {
    
    private final Instant startTime;
    private final AtomicInteger totalObjectsTracked;
    private final AtomicInteger totalChangesDetected;
    private final AtomicLong totalTrackingTimeNanos;
    private final Map<ChangeType, AtomicInteger> changesByType;
    private final Map<String, ObjectStatistics> objectStats;
    private final List<PerformanceSnapshot> performanceSnapshots;
    
    public TrackingStatistics() {
        this.startTime = Instant.now();
        this.totalObjectsTracked = new AtomicInteger(0);
        this.totalChangesDetected = new AtomicInteger(0);
        this.totalTrackingTimeNanos = new AtomicLong(0);
        this.changesByType = new EnumMap<>(ChangeType.class);
        this.objectStats = new ConcurrentHashMap<>();
        this.performanceSnapshots = new CopyOnWriteArrayList<>();
        
        // 初始化变更类型计数器
        for (ChangeType type : ChangeType.values()) {
            changesByType.put(type, new AtomicInteger(0));
        }
    }
    
    /**
     * 记录对象追踪
     */
    public void recordObjectTracked(String objectName) {
        totalObjectsTracked.incrementAndGet();
        // 处理null值和空字符串
        String safeName = (objectName == null) ? "<null>" : objectName.trim().isEmpty() ? "<empty>" : objectName;
        objectStats.computeIfAbsent(safeName, k -> new ObjectStatistics(k));
    }
    
    /**
     * 记录变更检测
     */
    public void recordChanges(List<ChangeRecord> changes, long detectionTimeNanos) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        
        totalChangesDetected.addAndGet(changes.size());
        totalTrackingTimeNanos.addAndGet(detectionTimeNanos);
        
        // 按类型统计
        for (ChangeRecord change : changes) {
            if (change.getChangeType() != null) {
                changesByType.get(change.getChangeType()).incrementAndGet();
            }
            
            // 更新对象统计
            String objectName = change.getObjectName();
            if (objectName != null) {
                ObjectStatistics objStats = objectStats.get(objectName);
                if (objStats != null) {
                    objStats.recordChange(change);
                }
            }
        }
        
        // 记录性能快照
        if (performanceSnapshots.size() < 1000) { // 限制内存使用
            performanceSnapshots.add(new PerformanceSnapshot(
                changes.size(), 
                detectionTimeNanos,
                Instant.now()
            ));
        }
    }
    
    /**
     * 获取统计摘要
     */
    public StatisticsSummary getSummary() {
        return new StatisticsSummary(
            totalObjectsTracked.get(),
            totalChangesDetected.get(),
            getChangeTypeDistribution(),
            getAverageDetectionTimeMs(),
            getTopChangedObjects(10),
            Duration.between(startTime, Instant.now())
        );
    }
    
    /**
     * 获取变更类型分布
     */
    public Map<ChangeType, Integer> getChangeTypeDistribution() {
        Map<ChangeType, Integer> distribution = new EnumMap<>(ChangeType.class);
        for (Map.Entry<ChangeType, AtomicInteger> entry : changesByType.entrySet()) {
            distribution.put(entry.getKey(), entry.getValue().get());
        }
        return distribution;
    }
    
    /**
     * 获取平均检测时间（毫秒）
     */
    public double getAverageDetectionTimeMs() {
        int snapshots = performanceSnapshots.size();
        if (snapshots == 0) {
            return 0.0;
        }
        return totalTrackingTimeNanos.get() / 1_000_000.0 / snapshots;
    }
    
    /**
     * 获取变更最多的对象
     */
    public List<ObjectStatistics> getTopChangedObjects(int limit) {
        return objectStats.values().stream()
            .sorted((a, b) -> Integer.compare(b.getTotalChanges(), a.getTotalChanges()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取性能统计
     */
    public PerformanceStatistics getPerformanceStatistics() {
        if (performanceSnapshots.isEmpty()) {
            return new PerformanceStatistics(0, 0, 0, 0, 0);
        }
        
        List<Long> times = performanceSnapshots.stream()
            .map(s -> s.detectionTimeNanos)
            .sorted()
            .collect(Collectors.toList());
        
        int size = times.size();
        long min = times.get(0);
        long max = times.get(size - 1);
        long p50 = times.get(size / 2);
        long p95 = times.get((int)(size * 0.95));
        long p99 = times.get((int)(size * 0.99));
        
        return new PerformanceStatistics(
            min / 1000, // 转换为微秒
            max / 1000,
            p50 / 1000,
            p95 / 1000,
            p99 / 1000
        );
    }
    
    /**
     * 清空统计数据
     */
    public void reset() {
        totalObjectsTracked.set(0);
        totalChangesDetected.set(0);
        totalTrackingTimeNanos.set(0);
        changesByType.values().forEach(counter -> counter.set(0));
        objectStats.clear();
        performanceSnapshots.clear();
    }
    
    /**
     * 格式化输出统计信息
     */
    public String format() {
        StatisticsSummary summary = getSummary();
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== Tracking Statistics ===\n");
        sb.append(String.format("Duration: %s\n", formatDuration(summary.duration)));
        sb.append(String.format("Objects Tracked: %d\n", summary.totalObjectsTracked));
        sb.append(String.format("Changes Detected: %d\n", summary.totalChangesDetected));
        sb.append(String.format("Avg Detection Time: %.2f ms\n", summary.averageDetectionTimeMs));
        
        sb.append("\nChange Type Distribution:\n");
        for (Map.Entry<ChangeType, Integer> entry : summary.changeTypeDistribution.entrySet()) {
            sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
        }
        
        if (!summary.topChangedObjects.isEmpty()) {
            sb.append("\nTop Changed Objects:\n");
            for (ObjectStatistics obj : summary.topChangedObjects) {
                sb.append(String.format("  %s: %d changes\n", obj.objectName, obj.getTotalChanges()));
            }
        }
        
        return sb.toString();
    }
    
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }
    
    /**
     * 对象统计信息
     */
    public static class ObjectStatistics {
        private final String objectName;
        private final AtomicInteger totalChanges;
        private final Map<String, AtomicInteger> fieldChangeCounts;
        private final Map<ChangeType, AtomicInteger> changeTypeCounts;
        
        public ObjectStatistics(String objectName) {
            this.objectName = objectName;
            this.totalChanges = new AtomicInteger(0);
            this.fieldChangeCounts = new ConcurrentHashMap<>();
            this.changeTypeCounts = new EnumMap<>(ChangeType.class);
            
            for (ChangeType type : ChangeType.values()) {
                changeTypeCounts.put(type, new AtomicInteger(0));
            }
        }
        
        public void recordChange(ChangeRecord change) {
            totalChanges.incrementAndGet();
            changeTypeCounts.get(change.getChangeType()).incrementAndGet();
            fieldChangeCounts.computeIfAbsent(change.getFieldName(), k -> new AtomicInteger(0))
                            .incrementAndGet();
        }
        
        public String getObjectName() { return objectName; }
        public int getTotalChanges() { return totalChanges.get(); }
        public Map<String, Integer> getFieldChangeCounts() {
            Map<String, Integer> result = new ConcurrentHashMap<>();
            fieldChangeCounts.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
    }
    
    /**
     * 性能快照
     */
    private static class PerformanceSnapshot {
        final int changeCount;
        final long detectionTimeNanos;
        final Instant timestamp;
        
        PerformanceSnapshot(int changeCount, long detectionTimeNanos, Instant timestamp) {
            this.changeCount = changeCount;
            this.detectionTimeNanos = detectionTimeNanos;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 统计摘要
     */
    public static class StatisticsSummary {
        public final int totalObjectsTracked;
        public final int totalChangesDetected;
        public final Map<ChangeType, Integer> changeTypeDistribution;
        public final double averageDetectionTimeMs;
        public final List<ObjectStatistics> topChangedObjects;
        public final Duration duration;
        
        public StatisticsSummary(int totalObjectsTracked, int totalChangesDetected,
                                Map<ChangeType, Integer> changeTypeDistribution,
                                double averageDetectionTimeMs,
                                List<ObjectStatistics> topChangedObjects,
                                Duration duration) {
            this.totalObjectsTracked = totalObjectsTracked;
            this.totalChangesDetected = totalChangesDetected;
            this.changeTypeDistribution = changeTypeDistribution;
            this.averageDetectionTimeMs = averageDetectionTimeMs;
            this.topChangedObjects = topChangedObjects;
            this.duration = duration;
        }
    }
    
    /**
     * 性能统计
     */
    public static class PerformanceStatistics {
        public final long minMicros;
        public final long maxMicros;
        public final long p50Micros;
        public final long p95Micros;
        public final long p99Micros;
        
        public PerformanceStatistics(long minMicros, long maxMicros, 
                                    long p50Micros, long p95Micros, long p99Micros) {
            this.minMicros = minMicros;
            this.maxMicros = maxMicros;
            this.p50Micros = p50Micros;
            this.p95Micros = p95Micros;
            this.p99Micros = p99Micros;
        }
    }
}