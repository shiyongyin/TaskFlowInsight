package com.syy.taskflowinsight.tracking.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 集合诊断摘要生成器。
 *
 * <p>该类型只生成面向展示和指标的有界诊断信息，不参与快照、Diff或相等性判断。
 * 摘要会抽样且可能截断，因此不能作为集合内容相同的证据。</p>
 * 
 * 核心功能：
 * - 大集合阈值检测
 * - 类型分布统计
 * - 示例数据收集
 * - 敏感信息过滤
 * - 性能优化的摘要算法
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class CollectionSummary {

    /** 摘要失败只进入诊断日志，不能改变比较结果。 */
    private static final Logger logger = LoggerFactory.getLogger(CollectionSummary.class);

    /** 总开关仅控制诊断摘要生成，不控制集合比较。 */
    private boolean enabled = true;

    /** 完整统计前允许读取的诊断样本上限。 */
    private int maxSize = 100;

    /** 对外诊断中最多保留的示例数量。 */
    private int maxExamples = 10;

    /** 展示前用于保守脱敏的关键词，不进入相等域。 */
    private List<String> sensitiveWords = Arrays.asList("password", "secret", "token", "key", "credential");

    /** 非Spring兼容入口共享的无状态默认生成器。 */
    private static volatile CollectionSummary instance;

    /**
     * 获取非Spring环境的兼容实例；实例只保存诊断配置，不能被Compare内核读取。
     *
     * @return 进程内延迟初始化的摘要生成器
     */
    public static CollectionSummary getInstance() {
        if (instance == null) {
            synchronized (CollectionSummary.class) {
                if (instance == null) {
                    instance = new CollectionSummary();
                }
            }
        }
        return instance;
    }
    
    /**
     * 判断是否需要摘要
     * 
     * @param collection 集合对象
     * @return 是否需要生成摘要
     */
    public boolean shouldSummarize(Object collection) {
        if (!enabled || collection == null) {
            return false;
        }
        
        int size = getSize(collection);
        return size > maxSize;
    }
    
    /**
     * 生成集合摘要
     * 
     * @param collection 集合对象
     * @return 摘要信息
     */
    public SummaryInfo summarize(Object collection) {
        if (collection == null) {
            return SummaryInfo.empty();
        }
        
        long startTime = System.nanoTime();
        SummaryInfo result;
        
        try {
            Class<?> type = collection.getClass();
            
            if (collection instanceof Collection) {
                result = summarizeCollection((Collection<?>) collection);
            } else if (collection instanceof Map) {
                result = summarizeMap((Map<?, ?>) collection);
            } else if (type.isArray()) {
                result = summarizeArray(collection);
            } else {
                result = SummaryInfo.unsupported(type);
            }
            
            // 记录生成时间
            result.setTimestamp(System.currentTimeMillis());
            
            // 性能日志
            long duration = (System.nanoTime() - startTime) / 1000; // 转换为微秒
            if (duration > 500) {
                logger.debug("Collection summary took {}μs for {} elements", 
                    duration, result.getSize());
            }
            
        } catch (Exception e) {
            logger.warn("Failed to summarize collection", e);
            result = SummaryInfo.empty();
        }
        
        return result;
    }
    
    /**
     * 获取集合大小
     */
    private int getSize(Object collection) {
        if (collection == null) {
            return 0;
        }
        
        if (collection instanceof Collection) {
            return ((Collection<?>) collection).size();
        } else if (collection instanceof Map) {
            return ((Map<?, ?>) collection).size();
        } else if (collection.getClass().isArray()) {
            return Array.getLength(collection);
        }
        
        return -1;
    }
    
    /**
     * 生成Collection摘要
     */
    private SummaryInfo summarizeCollection(Collection<?> collection) {
        SummaryInfo info = new SummaryInfo();
        info.setType(collection.getClass().getSimpleName());
        info.setSize(collection.size());
        
        // 如果是小集合，直接返回
        if (collection.size() <= maxSize) {
            info.setExamples(new ArrayList<>(collection).stream()
                .limit(maxExamples)
                .map(this::sanitize)
                .collect(Collectors.toList()));
            return info;
        }
        
        // 大集合降级处理
        Map<Class<?>, Integer> typeDistribution = new HashMap<>();
        Set<Object> uniqueValues = new HashSet<>();
        List<Object> examples = new ArrayList<>();
        Set<String> features = new HashSet<>();
        
        int processedCount = 0;
        int maxProcess = Math.min(collection.size(), maxSize * 2);
        
        Iterator<?> iterator = collection.iterator();
        while (iterator.hasNext() && processedCount < maxProcess) {
            Object item = iterator.next();
            processedCount++;
            
            // 类型分布
            if (item != null) {
                Class<?> itemType = item.getClass();
                typeDistribution.merge(itemType, 1, Integer::sum);
                
                // 唯一值统计（仅对简单类型）
                if (isSimpleType(itemType)) {
                    uniqueValues.add(item);
                }
            }
            
            // 收集示例
            if (examples.size() < maxExamples && !containsSensitive(item)) {
                examples.add(sanitize(item));
            }
        }
        
        // 判断是否被截断
        if (processedCount < collection.size()) {
            info.setTruncated(true);
        }
        
        // 计算特征
        if (typeDistribution.size() == 1) {
            features.add("homogeneous");
        }
        if (uniqueValues.size() == processedCount) {
            features.add("distinct");
        }
        if (collection instanceof List) {
            features.add("ordered");
        }
        if (collection instanceof Set) {
            features.add("unique");
        }
        
        // 设置摘要信息
        info.setTypeDistribution(typeDistribution);
        info.setUniqueCount(uniqueValues.size());
        info.setExamples(examples);
        info.setFeatures(features);
        
        // 数值集合的统计信息
        if (isNumericCollection(typeDistribution)) {
            info.setStatistics(calculateStatistics(collection, processedCount));
        }
        
        return info;
    }
    
    /**
     * 生成Map摘要
     */
    private SummaryInfo summarizeMap(Map<?, ?> map) {
        SummaryInfo info = new SummaryInfo();
        info.setType("Map");
        info.setSize(map.size());
        
        // 如果是小Map，直接返回
        if (map.size() <= maxSize) {
            List<Map.Entry<String, Object>> examples = map.entrySet().stream()
                .limit(maxExamples)
                .map(e -> Map.entry(
                    String.valueOf(sanitize(e.getKey())),
                    sanitize(e.getValue())
                ))
                .collect(Collectors.toList());
            info.setMapExamples(examples);
            return info;
        }
        
        // 大Map降级处理
        Map<Class<?>, Integer> keyTypes = new HashMap<>();
        Map<Class<?>, Integer> valueTypes = new HashMap<>();
        List<Map.Entry<String, Object>> examples = new ArrayList<>();
        
        int processedCount = 0;
        int maxProcess = Math.min(map.size(), maxSize * 2);
        
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (processedCount >= maxProcess) {
                info.setTruncated(true);
                break;
            }
            processedCount++;
            
            Object key = entry.getKey();
            Object value = entry.getValue();
            
            // 统计类型
            if (key != null) {
                keyTypes.merge(key.getClass(), 1, Integer::sum);
            }
            if (value != null) {
                valueTypes.merge(value.getClass(), 1, Integer::sum);
            }
            
            // 收集示例
            if (examples.size() < maxExamples && 
                !containsSensitive(key) && !containsSensitive(value)) {
                examples.add(Map.entry(
                    String.valueOf(sanitize(key)),
                    sanitize(value)
                ));
            }
        }
        
        info.setKeyTypeDistribution(keyTypes);
        info.setValueTypeDistribution(valueTypes);
        info.setMapExamples(examples);
        
        return info;
    }
    
    /**
     * 生成数组摘要
     */
    private SummaryInfo summarizeArray(Object array) {
        int length = Array.getLength(array);
        SummaryInfo info = new SummaryInfo();
        info.setType(array.getClass().getComponentType().getSimpleName() + "[]");
        info.setSize(length);
        
        List<Object> examples = new ArrayList<>();
        int maxProcess = Math.min(length, maxExamples);
        
        for (int i = 0; i < maxProcess; i++) {
            Object item = Array.get(array, i);
            if (!containsSensitive(item)) {
                examples.add(sanitize(item));
            }
        }
        
        info.setExamples(examples);
        
        // 数值数组的统计
        if (isNumericType(array.getClass().getComponentType())) {
            info.setStatistics(calculateArrayStatistics(array));
        }
        
        return info;
    }
    
    /**
     * 计算统计信息
     */
    private SummaryInfo.Statistics calculateStatistics(Collection<?> collection, int limit) {
        List<Double> numbers = new ArrayList<>();
        
        int count = 0;
        for (Object item : collection) {
            if (count >= limit) break;
            if (item instanceof Number) {
                numbers.add(((Number) item).doubleValue());
            }
            count++;
        }
        
        if (numbers.isEmpty()) {
            return null;
        }
        
        SummaryInfo.Statistics stats = new SummaryInfo.Statistics();
        DoubleSummaryStatistics summary = numbers.stream()
            .mapToDouble(Double::doubleValue)
            .summaryStatistics();
        
        stats.setMin(summary.getMin());
        stats.setMax(summary.getMax());
        stats.setMean(summary.getAverage());
        
        // 计算中位数
        Collections.sort(numbers);
        if (numbers.size() % 2 == 0) {
            stats.setMedian((numbers.get(numbers.size()/2 - 1) + numbers.get(numbers.size()/2)) / 2);
        } else {
            stats.setMedian(numbers.get(numbers.size()/2));
        }
        
        return stats;
    }
    
    /**
     * 计算数组统计信息
     */
    private SummaryInfo.Statistics calculateArrayStatistics(Object array) {
        int length = Array.getLength(array);
        if (length == 0) {
            return null;
        }
        
        List<Double> numbers = new ArrayList<>();
        for (int i = 0; i < Math.min(length, maxSize * 2); i++) {
            Object item = Array.get(array, i);
            if (item instanceof Number) {
                numbers.add(((Number) item).doubleValue());
            }
        }
        
        if (numbers.isEmpty()) {
            return null;
        }
        
        return calculateStatistics(numbers, numbers.size());
    }
    
    /**
     * 检查是否为简单类型
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               Number.class.isAssignableFrom(type) ||
               type == Boolean.class ||
               type == Character.class ||
               type.isEnum();
    }
    
    /**
     * 检查是否为数值类型
     */
    private boolean isNumericType(Class<?> type) {
        return Number.class.isAssignableFrom(type) ||
               type == int.class || type == long.class ||
               type == double.class || type == float.class ||
               type == short.class || type == byte.class;
    }
    
    /**
     * 检查是否为数值集合
     */
    private boolean isNumericCollection(Map<Class<?>, Integer> typeDistribution) {
        if (typeDistribution.isEmpty()) {
            return false;
        }
        
        return typeDistribution.keySet().stream()
            .allMatch(this::isNumericType);
    }
    
    /**
     * 检查是否包含敏感信息
     */
    private boolean containsSensitive(Object value) {
        if (value == null) {
            return false;
        }
        
        String str = value.toString().toLowerCase();
        return sensitiveWords.stream()
            .anyMatch(str::contains);
    }
    
    /**
     * 清理和脱敏值
     */
    private Object sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        
        String str = value.toString();
        
        // 检查敏感信息
        String lowerStr = str.toLowerCase();
        for (String sensitive : sensitiveWords) {
            if (lowerStr.contains(sensitive)) {
                return "***MASKED***";
            }
        }
        
        // 限制长度
        if (str.length() > 100) {
            return str.substring(0, 100) + "...";
        }
        
        return str;
    }
    
    /**
     * 非Spring测试可显式关闭诊断生成，不能借此关闭集合比较。
     *
     * @param enabled 是否生成诊断摘要
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 设置诊断统计允许处理的样本规模。
     *
     * @param maxSize 正向样本数量上限
     */
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * 设置诊断结果允许保留的示例数量。
     *
     * @param maxExamples 非负示例数量上限
     */
    public void setMaxExamples(int maxExamples) {
        this.maxExamples = maxExamples;
    }

    /**
     * 设置展示前用于保守脱敏匹配的关键词。
     *
     * @param sensitiveWords 诊断专用关键词列表，不参与比较相等性
     */
    public void setSensitiveWords(List<String> sensitiveWords) {
        this.sensitiveWords = sensitiveWords;
    }
}
