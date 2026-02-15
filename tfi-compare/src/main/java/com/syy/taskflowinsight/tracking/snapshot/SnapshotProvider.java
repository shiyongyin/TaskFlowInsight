package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.api.TrackingOptions;

import java.util.Map;

/**
 * 快照提供器抽象：统一封装浅/深快照的捕获逻辑。
 *
 * 默认实现为 DirectSnapshotProvider（直连 ObjectSnapshot/ObjectSnapshotDeep），
 * 可通过 FacadeSnapshotProvider 切换为 SnapshotFacade 以便 A/B 与统一入口。
 */
public interface SnapshotProvider {

    /**
     * 捕获基线快照（传统 API：字段白名单）。
     */
    Map<String, Object> captureBaseline(String name, Object target, String[] fields);

    /**
     * 按 TrackingOptions 捕获快照（支持深度与类型感知）。
     */
    Map<String, Object> captureWithOptions(String name, Object target, TrackingOptions options);
}

