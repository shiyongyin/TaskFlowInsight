package com.syy.taskflowinsight.tracking.summary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集合诊断摘要模型。
 *
 * <p>字段只描述抽样后的展示事实，不能参与业务相等性判断，也不能替代完整集合快照。</p>
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
     * 创建不携带样本的空诊断对象。
     *
     * @return 带生成时间但不含业务值的空摘要
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
     * 创建只描述类型且不读取对象内容的诊断对象。
     *
     * @param type 无法生成集合统计的输入类型
     * @return 标记为unsupported的有界摘要
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
     * 转换为面向旧序列化消费者的诊断Map；该结构不能回流到Diff。
     *
     * @return 仅包含已采集摘要字段的Map
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
     * 生成面向日志的紧凑诊断文本，示例最多展示三项。
     *
     * @return 不具备相等证明能力的展示文本
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
     * 数值样本的展示统计；所有字段均可缺失，不能代表完整集合分布。
     *
     * @since 2025-01-13
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        /** 已处理样本中的最小值。 */
        private Double min;

        /** 已处理样本中的最大值。 */
        private Double max;

        /** 已处理样本的算术平均值。 */
        private Double mean;

        /** 已处理样本排序后的中位数。 */
        private Double median;

        /** 可选标准差；未计算时保持null而不是伪造0。 */
        private Double standardDeviation;

        /**
         * 输出已实际计算的统计字段，避免null被消费者误解为数值0。
         *
         * @return 只包含非null统计量的诊断Map
         */
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
