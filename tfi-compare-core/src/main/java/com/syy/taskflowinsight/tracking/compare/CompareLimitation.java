package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.Objects;
import java.util.Optional;

/**
 * policy或预算允许的执行边界，与非预期problem在类型上严格隔离。
 *
 * @param code 稳定边界码，不包含自由文本
 * @param stage 触发边界的比较阶段
 * @param path 可选的 typed path；全局边界时为空
 */
public record CompareLimitation(
        CompareLimitationCode code,
        CompareStage stage,
        Optional<ComparePath> path) {

    /** 校验边界事实的所有结构字段均已显式提供。 */
    public CompareLimitation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(path, "path");
    }
}
