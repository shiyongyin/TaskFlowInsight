package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.Objects;

/**
 * 变更一侧的路径和值事实；side缺失与present-null因此能在类型上区分。
 *
 * @param path 该侧值所在的 canonical typed path
 * @param value 已有界冻结的值事实，显式 null 也由该对象表达
 */
public record ChangeSide(ComparePath path, ValueSnapshot value) {

    /** 拒绝缺失路径或缺失值事实，避免退化为含糊的 null sentinel。 */
    public ChangeSide {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
    }
}
