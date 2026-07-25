package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.Objects;
import java.util.Optional;

/**
 * 非预期能力故障的有界事实；刻意不保存Throwable、message或任意metadata。
 *
 * @param code 稳定问题码，不包含自由文本
 * @param stage 发生问题的比较阶段
 * @param path 可选的 typed path；无局部路径时为空
 */
public record CompareProblem(
        CompareProblemCode code,
        CompareStage stage,
        Optional<ComparePath> path) {

    /** 校验问题事实的所有结构字段均已显式提供。 */
    public CompareProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(path, "path");
    }
}
