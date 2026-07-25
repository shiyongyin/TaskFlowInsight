package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;

import java.util.List;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.STRATEGY_ENTITY;

/**
 * keyed Entity List的无状态兼容入口。
 *
 * <p>unique key配对、MOVE、字段深比较与W2201均由唯一request-local内核负责。本类型不维护字符串
 * key map、首三项采样或独立捕获/差异图，否则直接实例化消费者会得到第二套结果。</p>
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 3.0.0
 */
public class EntityListStrategy implements ListCompareStrategy {

    /**
     * 返回旧策略注册名；实际Entity语义由request-local内核唯一拥有。
     *
     * @return 固定的Entity List策略名称
     */
    @Override
    public String getStrategyName() {
        return STRATEGY_ENTITY;
    }

    /**
     * 返回兼容层建议容量上限。
     *
     * <p>Entity语义不能随列表大小切换，因此容量约束只允许由统一预算显式表达。</p>
     *
     * @return 最大整数，表示本策略不建立额外的大小降级阈值
     */
    @Override
    public int getMaxRecommendedSize() {
        // 大小只能触发显式预算限制，不能再切换或降级Entity配对语义。
        return Integer.MAX_VALUE;
    }

    /**
     * 声明支持MOVE投影；MOVE与字段变化仍由内核同时发布。
     *
     * @return 始终为true
     */
    @Override
    public boolean supportsMoveDetection() {
        return true;
    }

    /**
     * 通过request-local内核比较两个Entity列表。
     *
     * @param list1 旧侧列表；允许为null
     * @param list2 新侧列表；允许为null
     * @param options 本次比较的冻结选项；非null
     * @return 保留typed Entity identity、MOVE和字段变化的比较结果
     */
    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }
        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }
        // List策略只做兼容委托，避免绕开request-global ledger与typed Entity path。
        return RequestLocalCompareKernel.compareObjects(
                list1, list2, options, options.getPolicy());
    }
}
