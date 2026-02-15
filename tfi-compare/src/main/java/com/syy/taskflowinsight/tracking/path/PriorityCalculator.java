package com.syy.taskflowinsight.tracking.path;

import java.util.Comparator;

/**
 * 路径优先级计算器
 * 基于配置的权重实现可调节的优先级计算逻辑
 * 
 * 优先级计算规则：
 * 1. 深度优先（depth * depthWeight）
 * 2. 访问类型（accessType.weight * accessTypeWeight）  
 * 3. 字典序（反转处理，越小分数越高）
 * 4. 稳定ID（tie-break最终决定因素）
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class PriorityCalculator {
    
    private final PathDeduplicationConfig config;
    
    public PriorityCalculator(PathDeduplicationConfig config) {
        this.config = config != null ? config : new PathDeduplicationConfig();
    }
    
    /**
     * 计算路径候选的综合优先级分数
     * 
     * @param candidate 路径候选
     * @return 优先级分数（越高越优先）
     */
    public long calculatePriority(PathArbiter.PathCandidate candidate) {
        if (candidate == null) {
            return Long.MIN_VALUE;
        }
        
        long score = 0;
        
        // 1. 深度分数（深度越大分数越高）
        score += (long) candidate.getDepth() * config.getDepthWeight();
        
        // 2. 访问类型分数（权重越大分数越高）
        score += (long) candidate.getAccessWeight() * config.getAccessTypeWeight();
        
        // 3. 字典序不参与数值总分，作为tie-break在比较器阶段处理
        
        // 4. 稳定ID用于最终tie-break，不参与数值总分（避免放大效应干扰深度/类型主导）
        // 保留在比较器的 thenComparing 阶段处理
        
        return score;
    }
    
    /**
     * 创建优先级比较器
     * 
     * @return 基于优先级分数的比较器
     */
    public Comparator<PathArbiter.PathCandidate> createComparator() {
        return Comparator
            .comparingLong(this::calculatePriority)           // 按综合分数排序（大优先，配合max使用）
            .thenComparing(PathArbiter.PathCandidate::getPath, java.util.Comparator.reverseOrder()) // tie-break: 字典序小优先
            .thenComparing(PathArbiter.PathCandidate::getStableId, java.util.Comparator.reverseOrder()); // 最终tie-break：稳定ID小优先
    }
    
    /**
     * 创建详细比较器（分层比较，用于调试）
     * 
     * @return 分层优先级比较器
     */
    public Comparator<PathArbiter.PathCandidate> createDetailedComparator() {
        return Comparator
            .comparing((PathArbiter.PathCandidate c) -> (long) c.getDepth() * config.getDepthWeight())
            .thenComparing(c -> (long) c.getAccessWeight() * config.getAccessTypeWeight())
            .thenComparing(c -> calculateLexicalScore(c.getPath()) * config.getLexicalWeight())
            .thenComparing(PathArbiter.PathCandidate::getPath, java.util.Comparator.reverseOrder())
            .thenComparing(PathArbiter.PathCandidate::getStableId, java.util.Comparator.reverseOrder());
    }
    
    /**
     * 计算字典序分数
     * 字符串越小（字典序靠前）分数越高
     * 
     * @param path 路径字符串
     * @return 字典序分数
     */
    private long calculateLexicalScore(String path) {
        if (path == null || path.isEmpty()) {
            return 1000L; // 空路径给一个固定小分值
        }
        // 简化：长度越短分数越高（范围约在 0..1000）
        int length = Math.min(path.length(), 1000);
        return 1000L - length;
    }
    
    /**
     * 计算稳定ID分数
     * 用于最终tie-break，确保结果可预测
     * 
     * @param stableId 稳定ID字符串
     * @return 稳定ID分数
     */
    private long calculateStableIdScore(String stableId) {
        if (stableId == null || stableId.isEmpty()) {
            return 0;
        }
        
        // 提取稳定ID中的数值部分
        if (stableId.startsWith("ID") && stableId.length() > 2) {
            try {
                String hexPart = stableId.substring(2);
                return Long.parseLong(hexPart, 16); // 十六进制转长整型
            } catch (NumberFormatException e) {
                // 回退到哈希值
                return stableId.hashCode() & 0x7FFFFFFFL;
            }
        }
        
        return stableId.hashCode() & 0x7FFFFFFFL;
    }
    
    /**
     * 生成优先级调试信息
     * 
     * @param candidate 路径候选
     * @return 优先级详细信息字符串
     */
    public String getPriorityDetails(PathArbiter.PathCandidate candidate) {
        if (candidate == null) {
            return "null-candidate";
        }
        
        long depthScore = (long) candidate.getDepth() * config.getDepthWeight();
        long accessScore = (long) candidate.getAccessWeight() * config.getAccessTypeWeight();
        long lexicalScore = calculateLexicalScore(candidate.getPath()) * config.getLexicalWeight();
        long stableScore = 0L; // 稳定ID不计入总分，仅用于比较器tie-break
        long totalScore = calculatePriority(candidate);
        
        return String.format("Priority{total=%d, depth=%d*%d=%d, access=%d*%d=%d, lexical=%d, stable=%d, path='%s'}",
            totalScore, 
            candidate.getDepth(), config.getDepthWeight(), depthScore,
            candidate.getAccessWeight(), config.getAccessTypeWeight(), accessScore,
            lexicalScore, stableScore, candidate.getPath());
    }
    
    /**
     * 批量计算优先级并排序
     * 
     * @param candidates 候选列表
     * @return 按优先级排序的候选列表
     */
    public java.util.List<PathArbiter.PathCandidate> sortByPriority(
            java.util.List<PathArbiter.PathCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        return candidates.stream()
            .sorted(createComparator().reversed()) // 降序排列，高优先级在前
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 选择最高优先级候选
     * 
     * @param candidates 候选列表
     * @return 最高优先级的候选，如果列表为空返回null
     */
    public PathArbiter.PathCandidate selectHighestPriority(
            java.util.List<PathArbiter.PathCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        
        return candidates.stream()
            .max(createComparator())
            .orElse(null);
    }
    
    /**
     * 验证优先级计算的一致性
     * 多次计算相同候选应该得到相同结果
     * 
     * @param candidate 测试候选
     * @param iterations 验证次数
     * @return 是否一致
     */
    public boolean verifyConsistency(PathArbiter.PathCandidate candidate, int iterations) {
        if (candidate == null || iterations < 1) {
            return true;
        }
        
        long baselinePriority = calculatePriority(candidate);
        
        for (int i = 0; i < iterations; i++) {
            long currentPriority = calculatePriority(candidate);
            if (currentPriority != baselinePriority) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取配置信息
     */
    public PathDeduplicationConfig getConfig() {
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("PriorityCalculator{weights: depth=%d, access=%d, lexical=%d, stable=%d}",
            config.getDepthWeight(), config.getAccessTypeWeight(), 
            config.getLexicalWeight(), config.getStableIdWeight());
    }
}
