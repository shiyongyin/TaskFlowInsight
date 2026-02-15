package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路径收集器
 * 遍历对象图收集所有可能的访问路径，为去重提供真实的路径候选
 * 
 * 核心功能：
 * - 对象图深度遍历（字段、集合、Map、数组）
 * - 路径候选生成（targetObject + path + depth + accessType）  
 * - 循环引用检测与处理
 * - 性能优化（缓存、深度限制、对象数限制）
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class PathCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(PathCollector.class);
    
    private final PathDeduplicationConfig config;
    
    // 已访问对象缓存（用于循环引用检测）
    private final ThreadLocal<Set<Integer>> visitedObjects = new ThreadLocal<>();
    
    // 路径缓存（性能优化）
    // 使用身份相等的缓存，避免Map键自身hashCode触发递归
    private final Map<Object, List<PathArbiter.PathCandidate>> pathCache =
        java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    
    public PathCollector(PathDeduplicationConfig config) {
        this.config = config != null ? config : new PathDeduplicationConfig();
    }
    
    /**
     * 从变更记录和对象图中收集路径候选
     * 这是 DiffDetector 的主要集成点
     * 
     * @param changeRecords 变更记录列表
     * @param beforeSnapshot 变更前对象快照
     * @param afterSnapshot 变更后对象快照  
     * @return 路径候选列表
     */
    public List<PathArbiter.PathCandidate> collectFromChangeRecords(
            List<ChangeRecord> changeRecords,
            Map<String, Object> beforeSnapshot, 
            Map<String, Object> afterSnapshot) {
            
        if (!config.isEnabled() || changeRecords == null || changeRecords.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<PathArbiter.PathCandidate> candidates = new ArrayList<>();
        
        try {
            visitedObjects.set(new HashSet<>());
            
            // 为每个变更记录找到对应的实际对象和路径
            for (ChangeRecord record : changeRecords) {
                if (record == null || record.getFieldName() == null) {
                    continue;
                }
                
                String fieldPath = record.getFieldName();
                
                // 从变更后对象中查找目标对象（CREATE/UPDATE场景）
                Object targetObject = findTargetObject(afterSnapshot, fieldPath);
                if (targetObject == null) {
                    // 从变更前对象中查找（DELETE场景）
                    targetObject = findTargetObject(beforeSnapshot, fieldPath);
                }
                
                if (targetObject != null) {
                    // 收集该目标对象的所有可能路径
                    List<PathArbiter.PathCandidate> objectPaths = collectPathsForObject(
                        targetObject, fieldPath, afterSnapshot);
                    // 过滤路径
                    if (objectPaths != null && !objectPaths.isEmpty()) {
                        for (PathArbiter.PathCandidate c : objectPaths) {
                            if (c != null && config.isPathAllowed(c.getPath())) {
                                candidates.add(c);
                            }
                        }
                    }

                    // 始终补充记录中的字段路径候选（保证顶层字段如 "name" 能出现）
                    if (fieldPath != null && !fieldPath.isEmpty() && config.isPathAllowed(fieldPath)) {
                        int d = calculatePathDepth(fieldPath);
                        candidates.add(new PathArbiter.PathCandidate(fieldPath, d,
                                PathArbiter.AccessType.fromPath(fieldPath), targetObject));
                    }
                }
            }
            
            logger.debug("Collected {} path candidates from {} change records", 
                        candidates.size(), changeRecords.size());
            
        } catch (Exception e) {
            logger.warn("Failed to collect paths from change records: {}", e.getMessage(), e);
        } finally {
            visitedObjects.remove();
        }
        
        return candidates;
    }
    
    /**
     * 为单个对象收集所有访问路径
     * 
     * @param targetObject 目标对象
     * @param basePath 基础路径
     * @param rootSnapshot 根对象快照
     * @return 该对象的所有路径候选
     */
    public List<PathArbiter.PathCandidate> collectPathsForObject(
            Object targetObject, String basePath, Map<String, Object> rootSnapshot) {
            
        if (targetObject == null) {
            return Collections.emptyList();
        }
        
        // 检查缓存
        if (config.isCacheEnabled()) {
            List<PathArbiter.PathCandidate> cached = pathCache.get(targetObject);
            if (cached != null) {
                return new ArrayList<>(cached);
            }
        }
        
        List<PathArbiter.PathCandidate> paths = new ArrayList<>();
        
        try {
            // 收集从根对象到目标对象的所有可能路径
            collectAllPathsToObject(rootSnapshot, "", targetObject, paths, 0);

            // 如果没有找到路径，创建基于给定路径的候选
            if (paths.isEmpty() && basePath != null) {
                int depth = calculatePathDepth(basePath);
                PathArbiter.AccessType accessType = PathArbiter.AccessType.fromPath(basePath);
                if (config.isPathAllowed(basePath)) {
                    paths.add(new PathArbiter.PathCandidate(basePath, depth, accessType, targetObject));
                }
            }
            
            // 缓存结果
            if (config.isCacheEnabled() && !paths.isEmpty()) {
                pathCache.put(targetObject, new ArrayList<>(paths));
            }
            
        } catch (Exception e) {
            logger.warn("Failed to collect paths for object: {}", e.getMessage());
        }
        
        return paths;
    }
    
    /**
     * 递归收集到指定目标对象的所有路径
     * 
     * @param current 当前遍历的对象
     * @param currentPath 当前路径
     * @param targetObject 目标对象
     * @param results 结果收集列表
     * @param depth 当前深度
     */
    private void collectAllPathsToObject(Object current, String currentPath, 
                                       Object targetObject, List<PathArbiter.PathCandidate> results, int depth) {
        
        // 深度限制
        if (depth >= config.getMaxCollectionDepth()) {
            return;
        }
        
        // 循环引用检测（修复ThreadLocal问题）
        if (current != null) {
            int objectId = System.identityHashCode(current);
            Set<Integer> visited = visitedObjects.get();
            if (visited == null) {
                visited = new HashSet<>();
                visitedObjects.set(visited);
            }
            if (visited.contains(objectId)) {
                return; // 避免循环引用
            }
            visited.add(objectId);
        }
        
        try {
            // 如果找到目标对象，创建路径候选
            if (current == targetObject) {
                if (!currentPath.isEmpty() && config.isPathAllowed(currentPath)) {
                    PathArbiter.AccessType accessType = PathArbiter.AccessType.fromPath(currentPath);
                    results.add(new PathArbiter.PathCandidate(currentPath, depth, accessType, targetObject));
                }
                return;
            }
            
            // 继续遍历对象结构
            if (current instanceof Map) {
                collectFromMap((Map<?, ?>) current, currentPath, targetObject, results, depth);
            } else if (current instanceof Collection) {
                collectFromCollection((Collection<?>) current, currentPath, targetObject, results, depth);
            } else if (current != null && current.getClass().isArray()) {
                collectFromArray(current, currentPath, targetObject, results, depth);
            } else if (current != null && !isSimpleType(current.getClass())) {
                collectFromObject(current, currentPath, targetObject, results, depth);
            }
            
        } finally {
            // 清理循环引用标记
            if (current != null) {
                Set<Integer> visited = visitedObjects.get();
                if (visited != null) {
                    visited.remove(System.identityHashCode(current));
                }
            }
        }
    }
    
    /**
     * 从Map中收集路径
     */
    private void collectFromMap(Map<?, ?> map, String basePath, Object targetObject, 
                              List<PathArbiter.PathCandidate> results, int depth) {
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (count >= config.getMaxObjectsPerLevel()) {
                break; // 限制每层处理的对象数
            }
            
            Object key = entry.getKey();
            Object value = entry.getValue();
            
            if (key != null) {
                String displayKey = String.valueOf(key);
                String mapNode = PathUtils.buildMapValuePath(displayKey);
                String keyPath = (basePath == null || basePath.isEmpty())
                        ? mapNode
                        : PathBuilder.fieldPath(basePath, mapNode);
                collectAllPathsToObject(value, keyPath, targetObject, results, depth + 1);
            }
            count++;
        }
    }
    
    /**
     * 从集合中收集路径
     */
    private void collectFromCollection(Collection<?> collection, String basePath, Object targetObject,
                                     List<PathArbiter.PathCandidate> results, int depth) {
        if (collection instanceof List) {
            // List使用索引访问
            List<?> list = (List<?>) collection;
            for (int i = 0; i < Math.min(list.size(), config.getMaxObjectsPerLevel()); i++) {
                String indexPath = PathBuilder.arrayIndex(basePath, i);
                collectAllPathsToObject(list.get(i), indexPath, targetObject, results, depth + 1);
            }
        } else if (collection instanceof Set) {
            // Set使用元素ID访问
            int count = 0;
            for (Object element : collection) {
                if (count >= config.getMaxObjectsPerLevel()) {
                    break;
                }
                String elementPath = PathBuilder.setElement(basePath, element);
                collectAllPathsToObject(element, elementPath, targetObject, results, depth + 1);
                count++;
            }
        } else {
            // 其他集合类型，使用通用处理
            int count = 0;
            for (Object element : collection) {
                if (count >= config.getMaxObjectsPerLevel()) {
                    break;
                }
                String elementPath = basePath + "[" + count + "]"; // 使用索引
                collectAllPathsToObject(element, elementPath, targetObject, results, depth + 1);
                count++;
            }
        }
    }
    
    /**
     * 从数组中收集路径
     */
    private void collectFromArray(Object array, String basePath, Object targetObject,
                                List<PathArbiter.PathCandidate> results, int depth) {
        if (array.getClass().isArray()) {
            Object[] objArray = (Object[]) array;
            for (int i = 0; i < Math.min(objArray.length, config.getMaxObjectsPerLevel()); i++) {
                String indexPath = PathBuilder.arrayIndex(basePath, i);
                collectAllPathsToObject(objArray[i], indexPath, targetObject, results, depth + 1);
            }
        }
    }
    
    /**
     * 从普通对象的字段中收集路径
     */
    private void collectFromObject(Object obj, String basePath, Object targetObject,
                                 List<PathArbiter.PathCandidate> results, int depth) {
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            int count = 0;
            
            for (Field field : fields) {
                if (count >= config.getMaxObjectsPerLevel()) {
                    break;
                }
                
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                
                String fieldPath = PathBuilder.fieldPath(basePath, field.getName());
                collectAllPathsToObject(fieldValue, fieldPath, targetObject, results, depth + 1);
                count++;
            }
        } catch (Exception e) {
            logger.debug("Failed to collect from object fields: {}", e.getMessage());
        }
    }
    
    /**
     * 根据路径在快照中查找目标对象（简化版本）
     */
    private Object findTargetObject(Map<String, Object> snapshot, String fieldPath) {
        if (snapshot == null || fieldPath == null) {
            return null;
        }
        
        // 简化实现：优先直接匹配
        Object directValue = snapshot.get(fieldPath);
        if (directValue != null) {
            return directValue;
        }
        
        // 如果没有直接匹配，返回null（避免虚假分组）
        return null;
    }
    
    /**
     * 计算路径深度
     */
    private int calculatePathDepth(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        
        int depth = 0;
        for (char c : path.toCharArray()) {
            if (c == '.' || c == '[') {
                depth++;
            }
        }
        return depth;
    }
    
    /**
     * 判断是否为简单类型（不需要进一步遍历）
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() || 
               clazz == String.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Double.class ||
               clazz == Float.class ||
               clazz == Boolean.class ||
               clazz == Character.class ||
               clazz == Byte.class ||
               clazz == Short.class ||
               Number.class.isAssignableFrom(clazz) ||
               clazz == Date.class;
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        pathCache.clear();
        logger.debug("PathCollector cache cleared");
    }
    
    /**
     * 获取缓存统计
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", pathCache.size());
        stats.put("cacheEnabled", config.isCacheEnabled());
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("PathCollector{config=%s, cacheSize=%d}", 
                           config, pathCache.size());
    }
}
