package com.syy.taskflowinsight.exporter.json;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;

import java.util.function.Function;

/** 仅供跨 package contract test 访问 JSON exporter 的既有测试 seam。 */
public final class JsonExporterTestAccess {

    private JsonExporterTestAccess() {
    }

    /** 将传入 capturer 原样交给 JSON exporter 的 package-private String seam。 */
    public static String export(
            Session session,
            Function<Session, SessionExportSnapshot> capturer) {
        return new JsonExporter().export(session, capturer);
    }
}
