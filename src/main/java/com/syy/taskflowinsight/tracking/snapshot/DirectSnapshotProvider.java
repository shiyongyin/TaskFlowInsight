package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;

import java.util.Map;

/**
 * 直连型快照提供器（保持现有行为）。
 */
public class DirectSnapshotProvider implements SnapshotProvider {

    @Override
    public Map<String, Object> captureBaseline(String name, Object target, String[] fields) {
        return ObjectSnapshot.capture(name, target, fields != null ? fields : new String[0]);
    }

    @Override
    public Map<String, Object> captureWithOptions(String name, Object target, TrackingOptions options) {
        if (options != null && options.getDepth() == TrackingOptions.TrackingDepth.DEEP) {
            SnapshotConfig config = new SnapshotConfig();
            config.setTimeBudgetMs(options.getTimeBudgetMs());
            config.setCollectionSummaryThreshold(options.getCollectionSummaryThreshold());

            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            return deep.captureDeep(target, options);
        }
        String[] fields = (options != null && !options.getIncludeFields().isEmpty())
            ? options.getIncludeFields().toArray(new String[0])
            : new String[0];
        return ObjectSnapshot.capture(name, target, fields);
    }
}

