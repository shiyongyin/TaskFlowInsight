package com.syy.taskflowinsight.tracking.compare;

import java.util.Objects;

/**
 * 由明确versioned算法定义的归一化相似度；缺失由Optional表达，不使用0或1 sentinel。
 *
 * @param algorithmId 生成该分数的版本化算法身份
 * @param value 有限且位于闭区间 [0, 1] 的归一化分数
 */
public record SimilarityScore(AlgorithmId algorithmId, double value) {

    public SimilarityScore {
        Objects.requireNonNull(algorithmId, "algorithmId");
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("similarity value must be finite and within [0,1]");
        }
    }
}
