package com.syy.taskflowinsight.tracking.compare;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象对
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pair<L, R> {
    
    private L left;
    private R right;
    
    /**
     * 创建对象对
     */
    public static <L, R> Pair<L, R> of(L left, R right) {
        return new Pair<>(left, right);
    }
    
    /**
     * 交换左右值
     */
    public Pair<R, L> swap() {
        return new Pair<>(right, left);
    }
}