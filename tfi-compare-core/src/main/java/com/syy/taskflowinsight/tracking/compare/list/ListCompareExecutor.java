package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.*;

/**
 * 冻结列表策略表上的确定性路由执行器。
 *
 * <p>该类型不读取Spring配置，也不基于规模或耗时自动换算法；否则同一runtime会因外部状态得到不同语义。
 * 当前只有在两侧所有非null元素都验证为Entity候选时选择ENTITY；混合或普通列表固定选择SIMPLE。</p>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ListCompareExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(ListCompareExecutor.class);
    
    /** 构造期冻结的策略名称到实现映射；执行期间不可增删。 */
    private final Map<String, ListCompareStrategy> strategies;
    
    /**
     * 冻结构造期提供的兼容策略表。
     *
     * @param strategyList 必须至少包含SIMPLE；ENTITY仅供后继卡的同质列表配对
     */
    public ListCompareExecutor(List<ListCompareStrategy> strategyList) {
        this.strategies = Map.copyOf(strategyList.stream()
            .collect(Collectors.toMap(ListCompareStrategy::getStrategyName, strategy -> strategy)));
        logger.info("Initialized ListCompareExecutor with strategyCount={}", strategies.size());
    }
    
    /**
     * 按冻结策略表选择一次执行路径；规模、耗时和外部配置不能在调用期间改变列表语义。
     *
     * @param list1 变更前列表，允许为null并由策略产生nullness事实
     * @param list2 变更后列表，允许为null并由策略产生nullness事实
     * @param options 与当前runtime policy同源的比较选项
     * @return 选中策略产生的typed比较结果
     */
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        long startTime = System.nanoTime();

        try {
            ListCompareStrategy strategy = selectStrategy(list1, list2);
            CompareResult result = strategy.compare(list1, list2, options);
            if (logger.isDebugEnabled()) {
                int ch = (result != null && result.getChanges() != null) ? result.getChanges().size() : -1;
                logger.debug("ListCompareExecutor result: identical={}, changes={}",
                    result != null && result.isIdentical(), ch);
            }
            return result;
        } finally {
            long duration = System.nanoTime() - startTime;
            long durationMs = duration / 1_000_000;
            if (logger.isDebugEnabled()) {
                logger.debug("List comparison completed in {}ms, sizes: [{}, {}]", 
                    durationMs, list1 != null ? list1.size() : 0, list2 != null ? list2.size() : 0);
            }
        }
    }
    
    /**
     * 选择合适的比较策略
     */
    private ListCompareStrategy selectStrategy(List<?> list1, List<?> list2) {
        if (strategies.containsKey(STRATEGY_ENTITY)
                && areHomogeneousEntityLists(list1, list2)) {
            return strategies.get(STRATEGY_ENTITY);
        }

        ListCompareStrategy defaultStrategy = strategies.get(STRATEGY_SIMPLE);
        if (defaultStrategy == null) {
            throw new IllegalStateException("SIMPLE strategy not found");
        }
        return defaultStrategy;
    }

    /**
     * 完整验证两侧列表的Entity同质性。
     *
     * <p>路由会改变配对语义，因此不能用固定数量样本推断整个容器；null不决定类型，至少存在一个
     * Entity候选且其余非null元素全部兼容时才允许进入后继卡持有的Entity策略。</p>
     */
    private boolean areHomogeneousEntityLists(List<?> list1, List<?> list2) {
        boolean foundEntity = false;
        for (List<?> list : Arrays.asList(list1, list2)) {
            if (list == null) {
                continue;
            }
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                Class<?> itemType = item.getClass();
                if (!itemType.isAnnotationPresent(Entity.class) && !hasKeyFields(itemType)) {
                    return false;
                }
                foundEntity = true;
            }
        }
        return foundEntity;
    }
    
    /**
     * 返回构造期已冻结的策略名称，用于诊断而非运行期注册。
     *
     * @return 不可变策略名集合
     */
    public Set<String> getSupportedStrategies() {
        return strategies.keySet();
    }
    
    /**
     * 递归检查类及其父类是否包含 @Key 注解字段
     *
     * @param clazz 待检查的类
     * @return true 如果找到 @Key 字段
     */
    private boolean hasKeyFields(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Key.class)) {
                    return true;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

}
