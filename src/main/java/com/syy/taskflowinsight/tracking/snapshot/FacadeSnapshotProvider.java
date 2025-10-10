package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;

import java.util.Map;

/**
 * 基于 SnapshotFacade 的快照提供器（统一入口）。
 * 默认使用 v1 Facade；如需 v2 优化版，可按需调整实现。
 */
public class FacadeSnapshotProvider implements SnapshotProvider {

    @Override
    public Map<String, Object> captureBaseline(String name, Object target, String[] fields) {
        SnapshotConfig config = new SnapshotConfig();
        SnapshotFacade facade = new SnapshotFacade(config);
        return facade.capture(name, target, fields != null ? fields : new String[0]);
    }

    @Override
    public Map<String, Object> captureWithOptions(String name, Object target, TrackingOptions options) {
        SnapshotConfig config = new SnapshotConfig();
        if (options != null) {
            config.setTimeBudgetMs(options.getTimeBudgetMs());
            config.setCollectionSummaryThreshold(options.getCollectionSummaryThreshold());
        }
        SnapshotFacade facade = new SnapshotFacade(config);
        if (options != null) {
            return facade.capture(name, target, options);
        }
        return facade.capture(name, target);
    }
}
