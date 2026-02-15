package com.syy.taskflowinsight.tracking.monitoring;

/**
 * 降级级别枚举
 * 
 * 定义5级降级链，从高精度到零开销：
 * FULL_TRACKING → SKIP_DEEP_ANALYSIS → SIMPLE_COMPARISON → SUMMARY_ONLY → DISABLED
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public enum DegradationLevel {
    
    /**
     * 完整追踪模式 - 最高精度
     * 适用场景：系统资源充足，性能良好
     */
    FULL_TRACKING(10, Integer.MAX_VALUE, "完整追踪") {
        @Override
        public boolean allowsDeepAnalysis() {
            return true;
        }
        
        @Override
        public boolean allowsMoveDetection() {
            return true;
        }
        
        @Override
        public boolean allowsPathOptimization() {
            return true;
        }
    },
    
    /**
     * 跳过深度分析 - 减少计算复杂度
     * 适用场景：轻微性能压力，内存使用率60-70%
     */
    SKIP_DEEP_ANALYSIS(8, 10000, "跳过深度分析") {
        @Override
        public boolean allowsDeepAnalysis() {
            return false;
        }
        
        @Override
        public boolean allowsMoveDetection() {
            return true;
        }
        
        @Override
        public boolean allowsPathOptimization() {
            return true;
        }
    },
    
    /**
     * 简单比较 - 仅基础差异检测
     * 适用场景：中等性能压力，内存使用率70-80%
     */
    SIMPLE_COMPARISON(5, 5000, "简单比较") {
        @Override
        public boolean allowsDeepAnalysis() {
            return false;
        }
        
        @Override
        public boolean allowsMoveDetection() {
            return false;
        }
        
        @Override
        public boolean allowsPathOptimization() {
            return false;
        }
    },
    
    /**
     * 仅摘要信息 - 最小开销追踪
     * 适用场景：高性能压力，内存使用率80-90%
     */
    SUMMARY_ONLY(3, 1000, "仅摘要信息") {
        @Override
        public boolean allowsDeepAnalysis() {
            return false;
        }
        
        @Override
        public boolean allowsMoveDetection() {
            return false;
        }
        
        @Override
        public boolean allowsPathOptimization() {
            return false;
        }
        
        @Override
        public boolean onlySummaryInfo() {
            return true;
        }
    },
    
    /**
     * 完全禁用 - 无追踪开销
     * 适用场景：极端性能压力，内存使用率>90%
     */
    DISABLED(0, 0, "完全禁用") {
        @Override
        public boolean allowsDeepAnalysis() {
            return false;
        }
        
        @Override
        public boolean allowsMoveDetection() {
            return false;
        }
        
        @Override
        public boolean allowsPathOptimization() {
            return false;
        }
        
        @Override
        public boolean isDisabled() {
            return true;
        }
    };
    
    private final int maxDepth;
    private final int maxElements;
    private final String description;
    
    DegradationLevel(int maxDepth, int maxElements, String description) {
        this.maxDepth = maxDepth;
        this.maxElements = maxElements;
        this.description = description;
    }
    
    public int getMaxDepth() { 
        return maxDepth; 
    }
    
    public int getMaxElements() { 
        return maxElements; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    // 抽象方法，由各级别实现具体行为
    public abstract boolean allowsDeepAnalysis();
    public abstract boolean allowsMoveDetection();
    public abstract boolean allowsPathOptimization();
    
    // 默认行为，可被子类覆盖
    public boolean onlySummaryInfo() {
        return false;
    }
    
    public boolean isDisabled() {
        return false;
    }
    
    /**
     * 判断当前级别是否比目标级别更严格（降级程度更高）
     */
    public boolean isMoreRestrictiveThan(DegradationLevel other) {
        return this.ordinal() > other.ordinal();
    }
    
    /**
     * 获取下一个更严格的级别
     */
    public DegradationLevel getNextMoreRestrictive() {
        DegradationLevel[] levels = values();
        int nextIndex = this.ordinal() + 1;
        return nextIndex < levels.length ? levels[nextIndex] : this;
    }
    
    /**
     * 获取下一个更宽松的级别
     */
    public DegradationLevel getNextLessRestrictive() {
        int prevIndex = this.ordinal() - 1;
        return prevIndex >= 0 ? values()[prevIndex] : this;
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s)", name(), description);
    }
}