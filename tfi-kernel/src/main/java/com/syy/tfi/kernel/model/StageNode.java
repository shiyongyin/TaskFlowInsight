package com.syy.tfi.kernel.model;

import java.util.List;
import java.util.Map;

/**
 * Stage 树的只读视图；冻结后 Sink 只能通过该接口观察终态。
 */
public interface StageNode {

    /** 返回阶段名称。 */
    String name();

    /** 返回阶段状态。 */
    FlowStatus status();

    /** 返回开始时的 epoch 毫秒。 */
    long startMs();

    /** 返回基于单调时钟计算的持续毫秒。 */
    long durMs();

    /** 返回不可修改的阶段属性。 */
    Map<String, Object> attrs();

    /** 返回不可修改的阶段事实。 */
    List<Record> records();

    /** 返回不可修改的子阶段。 */
    List<StageNode> children();
}
