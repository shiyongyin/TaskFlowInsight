package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.Objects;
import java.util.Optional;

/**
 * 结果issue的路径准入决策。
 *
 * <p>该值对象刻意分离“issue整体无法准入”与“issue可准入但本来没有path”，避免用null污染
 * {@link Optional}合同；它不持有result accumulator，也不形成第二预算owner。</p>
 *
 * @param admitted code与stage固定事实是否仍可进入结果预算
 * @param path 准入后保留的有界路径；未准入时必须为空
 */
record ResultIssuePathFit(boolean admitted, Optional<ComparePath> path) {

    ResultIssuePathFit {
        Objects.requireNonNull(path, "path");
        if (!admitted && path.isPresent()) {
            throw new IllegalArgumentException("rejected issue path fit must not retain a path");
        }
    }
}
