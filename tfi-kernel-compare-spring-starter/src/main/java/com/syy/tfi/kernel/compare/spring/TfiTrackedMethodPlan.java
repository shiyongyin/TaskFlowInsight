package com.syy.tfi.kernel.compare.spring;

import java.util.List;
import java.util.Objects;

/**
 * 代理创建期可证明且调用期可重建的不可变 tracking 方法计划。
 *
 * @param methodOperation 方法级受控 operation，不包含动态业务值
 * @param targets 按参数索引升序排列的 target 槽位
 */
record TfiTrackedMethodPlan(
        String methodOperation,
        List<TargetSlot> targets) {

    /** 防御性冻结 target 槽位，避免代理创建期与调用期计划漂移。 */
    TfiTrackedMethodPlan {
        methodOperation = Objects.requireNonNull(methodOperation, "methodOperation");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }

    /**
     * 单个被跟踪参数的位置与受控名称。
     *
     * @param parameterIndex Java 方法参数索引，从 0 开始
     * @param targetName 同一方法计划内唯一的 target 名
     */
    record TargetSlot(int parameterIndex, String targetName) {

        /** 保证内部计划不会携带无效索引或 null 名称。 */
        TargetSlot {
            if (parameterIndex < 0) {
                throw new IllegalArgumentException("parameterIndex must be non-negative");
            }
            targetName = Objects.requireNonNull(targetName, "targetName");
        }
    }
}
