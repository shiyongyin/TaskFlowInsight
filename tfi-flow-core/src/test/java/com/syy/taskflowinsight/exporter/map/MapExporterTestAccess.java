package com.syy.taskflowinsight.exporter.map;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;

import java.util.Map;
import java.util.function.Function;

/** 仅供跨 package contract test 访问 Map exporter 的既有测试 seam。 */
public final class MapExporterTestAccess {

    private MapExporterTestAccess() {
    }

    /** 将传入 capturer 原样交给 Map exporter 的 package-private seam。 */
    public static Map<String, Object> export(
            Session session,
            Function<Session, SessionExportSnapshot> capturer) {
        return MapExporter.export(session, capturer);
    }
}
