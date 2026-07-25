package com.syy.tfi.kernel.model;

import java.util.List;
import java.util.Map;

/**
 * 一次业务流转的只读终态；正常发布前必须完成冻结。
 */
public interface FlowSession {

    /** 返回当前 Session 标识。 */
    String sessionId();

    /** 返回链接父 Session 标识，根 Session 为 null。 */
    String parentSessionId();

    /** 返回业务流名称。 */
    String name();

    /** 返回不可修改的 Session 属性。 */
    Map<String, Object> attrs();

    /** 返回 Session 状态。 */
    FlowStatus status();

    /** 返回开始时的 epoch 毫秒。 */
    long startMs();

    /** 返回基于单调时钟计算的持续毫秒。 */
    long durMs();

    /** 返回记录是否因边界约束而不完整。 */
    boolean truncated();

    /** 返回按合同顺序排列且不可修改的不完整原因。 */
    List<String> incompleteReasons();

    /** 返回根阶段。 */
    StageNode root();
}
