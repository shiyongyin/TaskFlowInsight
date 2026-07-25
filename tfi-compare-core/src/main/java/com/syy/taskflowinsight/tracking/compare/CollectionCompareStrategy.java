package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;

import java.util.Collection;

/**
 * 非List、非Set的普通Collection兼容策略。
 *
 * <p>集合成员按遇到顺序进入typed index path，并与Map/List/array共用request-local预算；该类型
 * 不再通过HashSet折叠重复值，也不持有独立路由或相似度算法。</p>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class CollectionCompareStrategy implements CompareStrategy<Collection<?>> {

    @Override
    public CompareResult compare(Collection<?> col1, Collection<?> col2, CompareOptions options) {
        if (col1 == col2) {
            return CompareResult.identical();
        }
        
        if (col1 == null || col2 == null) {
            return CompareResult.ofNullDiff(col1, col2);
        }

        return RequestLocalCompareKernel.compareObjects(
                col1, col2, options, options.getPolicy());
    }
    
    @Override
    public String getName() {
        return "CollectionCompare";
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return Collection.class.isAssignableFrom(type);
    }
}
