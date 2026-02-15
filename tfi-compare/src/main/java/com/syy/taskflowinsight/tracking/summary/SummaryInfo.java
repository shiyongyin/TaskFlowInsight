package com.syy.taskflowinsight.tracking.summary;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.*;

/**
 * 集合摘要信息模型
 * 存储集合的统计信息、示例数据等摘要内容
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummaryInfo {
    
    /**
     * 集合类型名称
     */
    private String type;
    
    /**
     * 集合大小
     */
    private int size;
    
    /**
     * 是否被截断（未完全遍历）
     */
    private boolean truncated;
    
    /**
     * 唯一值数量
     */
    private int uniqueCount;
    
    /**
     * 元素类型分布
     * key: 类型, value: 数量
     */
    private Map<Class<?>, Integer> typeDistribution;
    
    /**
     * Map的key类型分布
     */
    private Map<Class<?>, Integer> keyTypeDistribution;
    
    /**
     * Map的value类型分布
     */
    private Map<Class<?>, Integer> valueTypeDistribution;
    
    /**
     * 示例元素列表
     */
    private List<Object> examples;
    
    /**
     * Map示例条目
     */
    private List<Map.Entry<String, Object>> mapExamples;
    
    /**
     * 特征标记
     * 如：sorted, distinct, homogeneous等
     */
    private Set<String> features;
    
    /**
     * 摘要生成时间戳
     */
    private long timestamp;
    
    /**
     * 统计信息
     */
    private Statistics statistics;
    
    /**
     * 创建空摘要
     */
    public static SummaryInfo empty() {
        SummaryInfo info = new SummaryInfo();
        info.setType("empty");
        info.setSize(0);
        info.setExamples(Collections.emptyList());
        info.setTimestamp(System.currentTimeMillis());
        return info;
    }
    
    /**
     * 创建不支持的类型摘要
     */
    public static SummaryInfo unsupported(Class<?> type) {
        SummaryInfo info = new SummaryInfo();
        info.setType(type.getSimpleName());
        info.setSize(-1);
        info.setFeatures(Set.of("unsupported"));
        info.setTimestamp(System.currentTimeMillis());
        return info;
    }
    
    /**
     * 转换为Map格式（用于序列化）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("size", size);
        
        if (truncated) {
            map.put("truncated", true);
        }
        
        if (uniqueCount > 0) {
            map.put("uniqueCount", uniqueCount);
        }
        
        if (examples != null && !examples.isEmpty()) {
            map.put("examples", examples);
        }
        
        if (mapExamples != null && !mapExamples.isEmpty()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Map.Entry<String, Object> entry : mapExamples) {
                Map<String, Object> entryMap = new HashMap<>();
                entryMap.put("key", entry.getKey());
                entryMap.put("value", entry.getValue());
                entries.add(entryMap);
            }
            map.put("mapExamples", entries);
        }
        
        if (features != null && !features.isEmpty()) {
            map.put("features", features);
        }
        
        if (statistics != null) {
            map.put("statistics", statistics.toMap());
        }
        
        map.put("timestamp", timestamp);
        
        return map;
    }
    
    /**
     * 生成简洁的字符串表示
     */
    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append(" size=").append(size);
        
        if (uniqueCount > 0) {
            sb.append(" unique=").append(uniqueCount);
        }
        
        if (truncated) {
            sb.append(" truncated");
        }
        
        if (features != null && !features.isEmpty()) {
            sb.append(" features=").append(features);
        }
        
        sb.append("]");
        
        if (examples != null && !examples.isEmpty()) {
            sb.append(" examples=").append(examples.subList(0, Math.min(3, examples.size())));
            if (examples.size() > 3) {
                sb.append("...");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 统计信息内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        private Double min;
        private Double max;
        private Double mean;
        private Double median;
        private Double standardDeviation;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (min != null) map.put("min", min);
            if (max != null) map.put("max", max);
            if (mean != null) map.put("mean", mean);
            if (median != null) map.put("median", median);
            if (standardDeviation != null) map.put("stdDev", standardDeviation);
            return map;
        }
    }
}