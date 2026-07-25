package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;

import java.util.List;

/**
 * 普通List的ordered-index兼容策略。
 *
 * <p>该实现只保留旧{@link ListCompareStrategy}入口，实际比较委托唯一request-local内核；否则直接
 * 调用策略会绕开typed path和全请求预算，重新形成第二套List语义。</p>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class SimpleListStrategy implements ListCompareStrategy {
    
    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }
        
        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }

        return RequestLocalCompareKernel.compareObjects(
                list1, list2, options, options.getPolicy());
    }
    
    @Override
    public boolean supportsMoveDetection() {
        return false; // SIMPLE策略不支持移动检测
    }
    
    @Override
    public String getStrategyName() {
        return "SIMPLE";
    }
    
    @Override
    public int getMaxRecommendedSize() {
        return Integer.MAX_VALUE; // SIMPLE策略无大小限制
    }

}
