package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;

import java.util.List;

/**
 * Creates the request-local baseline/capture boundary used by {@link TrackingExecutor}.
 *
 * <p>This narrow construction port keeps the comparison kernel independent from the shell-owned SPI registry.
 * It deliberately exposes no business action, runtime lookup, retry, or global result access.</p>
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface TrackingBatchProvider {

    /**
     * Creates one batch scope after the executor has validated and defensively copied every target.
     *
     * @param targets validated targets in input order
     * @param options immutable options constrained by the active comparison policy
     * @return request-local, single-capture batch scope
     */
    TrackingBatchScope begin(
            List<TrackingExecutor.Target> targets,
            CompareOptions options);
}
