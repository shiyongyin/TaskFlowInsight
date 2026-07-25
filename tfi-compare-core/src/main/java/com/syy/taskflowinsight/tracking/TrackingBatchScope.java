package com.syy.taskflowinsight.tracking;

import java.util.List;

/**
 * 一次业务action两侧的批量追踪资源边界。
 *
 * <p>scope由provider在线程内创建，capture最多消费一次，close只负责释放baseline引用，
 * 不拥有业务action、Core Session或Task终态。该边界让资源失败无法重新取得action执行权。</p>
 *
 * @since 4.0.0
 */
public interface TrackingBatchScope extends AutoCloseable {

    /**
     * 捕获action后的状态并按target输入顺序返回结果。
     *
     * @return 不可为null的有序追踪结果
     * @throws IllegalStateException scope已capture或已close
     */
    List<TrackingExecutor.Item> capture();

    /**
     * 释放baseline资源；实现必须支持重复关闭且不得完成外部业务生命周期。
     */
    @Override
    void close();
}
