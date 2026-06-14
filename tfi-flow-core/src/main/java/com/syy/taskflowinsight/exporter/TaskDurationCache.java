package com.syy.taskflowinsight.exporter;

import com.syy.taskflowinsight.model.TaskNode;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Export-scoped accumulated duration cache.
 */
public final class TaskDurationCache {

    private final Map<TaskNode, Long> accumulatedNanos;

    private TaskDurationCache(Map<TaskNode, Long> accumulatedNanos) {
        this.accumulatedNanos = accumulatedNanos;
    }

    /**
     * Builds a cache for the given task tree.
     *
     * @param root root task node, may be null
     * @return duration cache
     */
    public static TaskDurationCache from(TaskNode root) {
        Map<TaskNode, Long> durations = new IdentityHashMap<>();
        compute(root, durations);
        return new TaskDurationCache(durations);
    }

    /**
     * Returns accumulated duration in milliseconds for a node.
     *
     * @param node task node
     * @return accumulated duration in milliseconds
     */
    public long getAccumulatedDurationMillis(TaskNode node) {
        Long nanos = accumulatedNanos.get(node);
        if (nanos != null) {
            return nanos / 1_000_000L;
        }
        return node != null ? node.getAccumulatedDurationMillis() : 0L;
    }

    int size() {
        return accumulatedNanos.size();
    }

    private static long compute(TaskNode node, Map<TaskNode, Long> durations) {
        if (node == null) {
            return 0L;
        }

        long total = node.getSelfDurationNanos();
        for (TaskNode child : node.getChildren()) {
            total += compute(child, durations);
        }
        durations.put(node, total);
        return total;
    }
}
