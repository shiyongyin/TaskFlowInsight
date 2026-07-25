package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;


/**
 * 数组比较策略。
 *
 * <p>该兼容入口委托request-local内核，使对象数组和原始类型数组与普通List共享ordered-index、
 * typed path及预算边界，不再以{@code Objects.equals}短路嵌套元素。</p>
 *
 * @since 4.0.0
 */
public class ArrayCompareStrategy implements CompareStrategy<Object> {

    @Override
    public CompareResult compare(Object arr1, Object arr2, CompareOptions options) {
        if (arr1 == arr2) {
            return CompareResult.identical();
        }
        if (arr1 == null || arr2 == null) {
            return CompareResult.ofNullDiff(arr1, arr2);
        }
        return RequestLocalCompareKernel.compareObjects(
                arr1, arr2, options, options.getPolicy());
    }

    @Override
    public String getName() {
        return "ArrayCompare";
    }

    @Override
    public boolean supports(Class<?> type) {
        return type != null && type.isArray();
    }
}
