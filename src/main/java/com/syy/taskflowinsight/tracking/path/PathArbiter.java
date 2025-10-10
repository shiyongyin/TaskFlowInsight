package com.syy.taskflowinsight.tracking.path;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 路径裁决器（基础版本）
 * 实现最具体路径裁决算法，为CARD-CT-ALIGN提供扩展接口
 * 
 * 裁决规则：
 * 1. 深度优先（深度越大越具体）
 * 2. 访问类型权重（字段 > Map键 > 数组索引）
 * 3. 字典序排序
 * 4. 稳定标识（确保可重现）
 * 
 * 提供静态方法（向后兼容）和实例化版本（配置化支持）
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class PathArbiter {
    
    // 实例化版本的成员变量
    private final PathDeduplicationConfig config;
    private final PriorityCalculator priorityCalculator;
    private final PathCache cache;
    
    /**
     * 实例化构造器（配置化版本）
     */
    public PathArbiter(PathDeduplicationConfig config) {
        this.config = config != null ? config : new PathDeduplicationConfig();
        this.priorityCalculator = new PriorityCalculator(this.config);
        this.cache = new PathCache(this.config.isCacheEnabled(), this.config.getMaxCacheSize(), this.config.getCacheEvictionPolicy());
    }
    
    /**
     * 默认构造器
     */
    public PathArbiter() {
        this(new PathDeduplicationConfig());
    }
    
    // === 实例化版本的方法 ===
    
    /**
     * 选择最具体的路径（实例版本，支持配置化）
     */
    public PathCandidate selectMostSpecificConfigurable(List<PathCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates list cannot be empty");
        }

        if (candidates.size() == 1) {
            PathCandidate selectedSingle = candidates.get(0);
            // 写回缓存，确保后续一致性
            if (selectedSingle != null && selectedSingle.getTarget() != null && cache != null && config.isCacheEnabled()) {
                cache.put(selectedSingle.getTarget(), selectedSingle.getPath());
            }
            return selectedSingle;
        }

        // 先检查缓存命中：若缓存路径在候选集合中，直接返回
        if (cache != null && config.isCacheEnabled()) {
            Object target = candidates.get(0).getTarget();
            String cached = cache.get(target);
            if (cached != null) {
                for (PathCandidate c : candidates) {
                    if (cached.equals(c.getPath())) {
                        return c;
                    }
                }
            }
        }

        // 使用配置化优先级比较器（统一与PriorityCalculator逻辑）
        PathCandidate selected = candidates.stream()
            .max(priorityCalculator.createComparator())
            .orElse(candidates.get(0));
        // 写回缓存，确保后续一致性
        if (selected != null && selected.getTarget() != null && cache != null && config.isCacheEnabled()) {
            cache.put(selected.getTarget(), selected.getPath());
        }
        return selected;
    }
    
    /**
     * 路径去重（实例版本，基于路径语义）
     */
    public List<PathCandidate> deduplicateConfigurable(List<PathCandidate> allPaths) {
        if (allPaths == null || allPaths.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 使用全局最具体路径去重（基于路径语义，不分组）
        return deduplicateMostSpecific(allPaths);
    }
    
    // === 静态版本的方法（向后兼容） ===
    
    /**
     * 选择最具体的路径（单一路径选择）
     * 
     * @param candidates 路径候选列表
     * @return 最具体的路径候选
     */
    public static PathCandidate selectMostSpecific(List<PathCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidates list cannot be empty");
        }
        
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        // 使用默认配置的PriorityCalculator，保证静态与实例一致
        PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
        return candidates.stream()
            .max(pc.createComparator())
            .orElseThrow(() -> new IllegalStateException("No candidate selected"));
    }
    
    /**
     * 路径去重（基础实现）
     * 对于指向同一对象的多个路径，只保留最具体的一条
     * 
     * @param allPaths 所有路径列表
     * @return 去重后的路径列表
     */
    public static List<PathCandidate> deduplicate(List<PathCandidate> allPaths) {
        if (allPaths == null || allPaths.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 按对象引用分组，每组选择最具体的路径
        return allPaths.stream()
            .collect(Collectors.groupingBy(PathCandidate::getTargetId))
            .values()
            .stream()
            .map(PathArbiter::selectMostSpecific)
            .collect(Collectors.toList());
    }
    
    /**
     * 创建优先级比较器
     * 优先级顺序：深度 > 访问权重 > 字典序 > 稳定ID
     * 
     * @return 优先级比较器
     */
    private static Comparator<PathCandidate> createPriorityComparator() {
        return Comparator
            .comparing(PathCandidate::getDepth)                                            // 1. 深度（大优先）
            .thenComparing(PathCandidate::getAccessWeight)                                 // 2. 访问类型权重（大优先）
            .thenComparing(PathCandidate::getPath, java.util.Comparator.reverseOrder())    // 3. 字典序（小优先 -> 反转后在max下取小）
            .thenComparing(PathCandidate::getStableId, java.util.Comparator.reverseOrder()); // 4. 稳定ID（小优先）
    }
    
    /**
     * 路径候选对象（增强版）
     * 封装路径相关的所有信息用于裁决，确保确定性稳定ID
     */
    public static class PathCandidate {
        private final String path;
        private final int depth;
        private final AccessType accessType;
        private final Object target;
        private final String stableId;
        private final int hashCode;
        
        public PathCandidate(String path, int depth, AccessType accessType, Object target) {
            this.path = path != null ? path : "";
            this.depth = depth;
            this.accessType = accessType != null ? accessType : AccessType.FIELD;
            this.target = target;
            this.stableId = generateStableId(this.path);
            this.hashCode = calculateHashCode();
        }
        
        /**
         * 便捷构造器：从路径自动推断访问类型
         */
        public PathCandidate(String path, int depth, Object target) {
            this(path, depth, AccessType.fromPath(path), target);
        }
        
        public int getAccessWeight() {
            return accessType.getWeight();
        }
        
        /**
         * 生成目标对象标识（用于去重）
         * 结合对象类型和身份哈希码，确保唯一性
         * 
         * @return 目标对象的稳定标识
         */
        public String getTargetId() {
            if (target == null) {
                return "null-target";
            }
            
            // 使用System.identityHashCode确保对象唯一性
            return target.getClass().getSimpleName() + "@" + System.identityHashCode(target);
        }
        
        /**
         * 生成稳定ID（确定性版本）
         * 基于路径、深度、访问类型生成确定性哈希，确保多次运行一致
         * 
         * @param path 路径字符串
         * @return 稳定ID
         */
        private String generateStableId(String path) {
            if (path == null || path.isEmpty()) {
                return "ID00000000";
            }
            
            // 使用路径内容的确定性哈希，不依赖时间戳
            int pathHash = path.hashCode();
            int depthHash = Integer.hashCode(depth);
            int typeHash = accessType.hashCode();
            
            // 组合哈希确保稳定性和唯一性
            int combinedHash = pathHash ^ (depthHash << 8) ^ (typeHash << 16);
            
            // 确保正数
            if (combinedHash < 0) {
                combinedHash = combinedHash & 0x7FFFFFFF;
            }
            
            return "ID" + String.format("%08X", combinedHash);
        }
        
        /**
         * 计算对象哈希码（用于equals/hashCode契约）
         */
        private int calculateHashCode() {
            return java.util.Objects.hash(path, depth, accessType, System.identityHashCode(target));
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            
            PathCandidate that = (PathCandidate) obj;
            return depth == that.depth &&
                   java.util.Objects.equals(path, that.path) &&
                   accessType == that.accessType &&
                   target == that.target; // 对象引用相等性
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
        
        // Getters
        public String getPath() { return path; }
        public int getDepth() { return depth; }
        public AccessType getAccessType() { return accessType; }
        public Object getTarget() { return target; }
        public String getStableId() { return stableId; }
        
        @Override
        public String toString() {
            return String.format("PathCandidate{path='%s', depth=%d, type=%s, weight=%d, id='%s'}", 
                path, depth, accessType, getAccessWeight(), stableId);
        }
    }
    
    /**
     * 访问类型优先级枚举
     * 权重越高优先级越高，符合CARD-CT-ALIGN规范
     */
    public enum AccessType {
        FIELD(100),        // 字段访问优先级最高 - user.name
        MAP_KEY(50),       // Map键访问中等     - user["key"]
        ARRAY_INDEX(10),   // 数组索引较低     - list[0]
        SET_ELEMENT(5);    // Set元素最低      - set[id=123]
        
        private final int weight;
        
        AccessType(int weight) {
            this.weight = weight;
        }
        
        public int getWeight() { return weight; }
        
        /**
         * 从路径字符串推断访问类型
         * 与PathBuilder格式保持一致
         */
        public static AccessType fromPath(String path) {
            if (path == null || path.isEmpty()) {
                return FIELD;
            }
            
            // 获取路径最后一个段落进行分析
            String lastSegment = getLastPathSegment(path);
            
            if (lastSegment.startsWith("[\"") && lastSegment.endsWith("\"]")) {
                return MAP_KEY;  // ["key"] 格式
            } else if (lastSegment.startsWith("[") && lastSegment.endsWith("]")) {
                String content = lastSegment.substring(1, lastSegment.length() - 1);
                if (content.matches("\\d+")) {
                    return ARRAY_INDEX;  // [123] 数字索引
                } else if (content.startsWith("id=")) {
                    return SET_ELEMENT;  // [id=xxx] Set元素
                } else {
                    return MAP_KEY;      // [key] 其他Map键
                }
            } else {
                return FIELD;  // 字段访问
            }
        }
        
        private static String getLastPathSegment(String path) {
            int lastDotIndex = path.lastIndexOf('.');
            int lastBracketIndex = path.lastIndexOf('[');
            
            if (lastBracketIndex > lastDotIndex) {
                return path.substring(lastBracketIndex);
            } else if (lastDotIndex >= 0) {
                return path.substring(lastDotIndex + 1);
            } else {
                return path;
            }
        }
    }
    
    /**
     * 判断是否为祖先路径
     * 
     * @param ancestor 潜在的祖先路径
     * @param descendant 潜在的后代路径
     * @return 如果descendant是ancestor的后代路径，返回true
     */
    private static boolean isAncestor(String ancestor, String descendant) {
        if (ancestor == null || descendant == null) {
            return false;
        }
        
        // 相等的路径不算祖先关系
        if (ancestor.equals(descendant)) {
            return false;
        }
        
        // 后代路径必须以祖先路径+"."或祖先路径+"["开头
        return descendant.startsWith(ancestor + ".") || descendant.startsWith(ancestor + "[");
    }
    
    /**
     * 全局最具体路径去重（基于路径语义）
     * 仅保留最具体的路径，移除祖先路径
     * 
     * @param allCandidates 所有路径候选
     * @return 去重后仅保留最具体路径的候选列表
     */
    public static List<PathCandidate> deduplicateMostSpecific(List<PathCandidate> allCandidates) {
        if (allCandidates == null || allCandidates.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用优先级比较器排序：最具体的在前（深度大、访问权重高、字典序小、稳定ID小）
        List<PathCandidate> sorted = new ArrayList<>(allCandidates);
        // 简化且稳健的优先级：按深度降序，其次按访问类型权重降序，其次按路径字典序升序
        sorted.sort((a, b) -> {
            int d = Integer.compare(b.getDepth(), a.getDepth());
            if (d != 0) return d;
            int w = Integer.compare(b.getAccessWeight(), a.getAccessWeight());
            if (w != 0) return w;
            return a.getPath().compareTo(b.getPath());
        });

        java.util.LinkedHashMap<String, PathCandidate> keptMap = new java.util.LinkedHashMap<>();

        for (PathCandidate candidate : sorted) {
            String p = candidate.getPath();
            if (p == null || p.isEmpty()) continue;

            // 若已存在某个已保留路径是当前路径的后代，则丢弃当前（祖先）路径
            boolean hasDescendant = false;
            for (String kept : keptMap.keySet()) {
                if (isAncestor(p, kept)) { hasDescendant = true; break; }
            }
            if (hasDescendant) continue;

            // 移除已保留的祖先路径，保留更具体的当前路径
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (String kept : keptMap.keySet()) {
                if (isAncestor(kept, p)) {
                    toRemove.add(kept);
                }
            }
            for (String rem : toRemove) keptMap.remove(rem);

            keptMap.put(p, candidate);
        }

        return new java.util.ArrayList<>(keptMap.values());
    }

    // 快速检测是否存在以 p 为前缀的后代路径（p. 或 p[）
    private static boolean hasDescendantPrefix(java.util.NavigableSet<String> tree, String p) {
        String dotPrefix = p + ".";
        String brPrefix = p + "[";
        String cand = tree.ceiling(dotPrefix);
        if (cand != null && cand.startsWith(dotPrefix)) return true;
        cand = tree.ceiling(brPrefix);
        return cand != null && cand.startsWith(brPrefix);
    }

    // 移除已保留集合中的所有祖先路径
    private static void removeAncestors(java.util.Set<String> keptSet, java.util.NavigableSet<String> keptTree,
                                        java.util.Map<String, PathCandidate> keptMap, String p) {
        java.util.List<String> ancestors = new java.util.ArrayList<>();
        String ancestor = parentPath(p);
        while (ancestor != null && !ancestor.isEmpty()) {
            if (keptSet.contains(ancestor)) {
                ancestors.add(ancestor);
            }
            ancestor = parentPath(ancestor);
        }
        if (!ancestors.isEmpty()) {
            for (String a : ancestors) {
                keptSet.remove(a);
                keptTree.remove(a);
                keptMap.remove(a);
            }
        }
    }

    private static String parentPath(String path) {
        int dot = path.lastIndexOf('.')
            , br = path.lastIndexOf('[');
        int idx = Math.max(dot, br);
        if (idx <= 0) return null;
        return path.substring(0, idx);
    }
    
    /**
     * 扩展点：为CARD-CT-ALIGN预留的高级裁决接口
     * 
     * @param candidates 候选路径
     * @param strategy 裁决策略（预留）
     * @return 裁决结果
     */
    public static PathCandidate selectMostSpecificAdvanced(
            List<PathCandidate> candidates, 
            String strategy) {
        // 当前使用基础实现，未来可扩展
        return selectMostSpecific(candidates);
    }
    
    /**
     * 验证路径裁决的稳定性
     * 
     * @param candidates 候选路径列表
     * @param iterations 验证次数
     * @return 是否稳定
     */
    public static boolean verifyStability(List<PathCandidate> candidates, int iterations) {
        if (candidates == null || candidates.isEmpty() || iterations < 1) {
            return true;
        }
        
        // 第一次裁决作为基准
        PathCandidate baseline = selectMostSpecific(new ArrayList<>(candidates));
        
        // 多次裁决验证一致性
        for (int i = 1; i < iterations; i++) {
            PathCandidate result = selectMostSpecific(new ArrayList<>(candidates));
            if (!baseline.getPath().equals(result.getPath())) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 获取路径缓存实例（用于优化）
     */
    public PathCache getPathCache() {
        return cache;
    }
}
