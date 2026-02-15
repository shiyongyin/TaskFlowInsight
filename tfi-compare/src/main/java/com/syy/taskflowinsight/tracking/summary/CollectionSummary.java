package com.syy.taskflowinsight.tracking.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 集合摘要生成器
 * 对大集合进行智能降级处理，生成统计摘要信息
 * 
 * 核心功能：
 * - 大集合阈值检测与降级
 * - 类型分布统计
 * - 示例数据收集
 * - 敏感信息过滤
 * - 性能优化的摘要算法
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
public class CollectionSummary {
    
    private static final Logger logger = LoggerFactory.getLogger(CollectionSummary.class);
    
    // ========== 配置属性 ==========
    
    @Value("${tfi.change-tracking.summary.enabled:true}")
    private boolean enabled = true;
    
    @Value("${tfi.change-tracking.summary.max-size:100}")
    private int maxSize = 100;
    
    @Value("${tfi.change-tracking.summary.max-examples:10}")
    private int maxExamples = 10;
    
    @Value("${tfi.change-tracking.summary.sensitive-words:password,secret,token,key,credential}")
    private List<String> sensitiveWords = Arrays.asList("password", "secret", "token", "key", "credential");
    
    // 静态配置（用于非Spring环境）
    private static volatile CollectionSummary instance;
    private static final Map<String, CollectionSummary> CONFIG_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 获取默认实例（非Spring环境使用）
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
    
    // Setter方法（用于测试和配置）
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
    
    public void setMaxExamples(int maxExamples) {
        this.maxExamples = maxExamples;
    }
    
    public void setSensitiveWords(List<String> sensitiveWords) {
        this.sensitiveWords = sensitiveWords;
    }
}