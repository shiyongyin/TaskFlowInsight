package com.syy.taskflowinsight.exporter.map;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 将 Session 的单份不可变快照投影为 canonical V2 Map。
 *
 * <p>本类只拥有 public capture boundary；exact schema 由 model package 的唯一 projection owner 构建，
 * 避免 Map 与 JSON 分别维护字段树。
 */
public final class MapExporter {

    private MapExporter() {
        throw new UnsupportedOperationException("MapExporter is a utility class");
    }

    /**
     * 导出 canonical V2 Map；null Session 保留 empty Map fallback。
     *
     * @param session 要导出的会话
     * @return 深度不可修改的 canonical V2 Map，null Session 返回 empty Map
     */
    public static Map<String, Object> export(Session session) {
        return export(session, SessionExportSnapshot::capture);
    }

    /** 包内 capturer seam 固定一次捕获语义，同时避免增加第二个 public 入口。 */
    static Map<String, Object> export(
            Session session, Function<Session, SessionExportSnapshot> capturer) {
        if (session == null) {
            return Collections.emptyMap();
        }
        Objects.requireNonNull(capturer, "capturer");
        SessionExportSnapshot snapshot = Objects.requireNonNull(
                capturer.apply(session), "capturer returned null snapshot");
        return snapshot.toCanonicalV2();
    }
}
