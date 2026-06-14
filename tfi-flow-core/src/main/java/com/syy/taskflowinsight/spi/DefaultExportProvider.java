package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;

import java.util.Map;

/**
 * 默认导出服务提供者。
 *
 * <p>委托 core 内置 exporter，保证 ExportProvider seam 接入后默认输出保持兼容。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultExportProvider implements ExportProvider {

    @Override
    public boolean exportToConsole(boolean showTimestamp) {
        Session session = currentSession();
        if (session == null) {
            return false;
        }
        ConsoleExporter exporter = new ConsoleExporter();
        if (showTimestamp) {
            exporter.print(session);
        } else {
            exporter.printSimple(session);
        }
        return true;
    }

    @Override
    public String exportToJson() {
        Session session = currentSession();
        return session != null ? new JsonExporter().export(session) : "{}";
    }

    @Override
    public Map<String, Object> exportToMap() {
        Session session = currentSession();
        return session != null ? MapExporter.export(session) : Map.of();
    }

    @Override
    public int priority() {
        return -1000;  // 最低优先级
    }

    private Session currentSession() {
        ManagedThreadContext context = ManagedThreadContext.current();
        return context != null ? context.getCurrentSession() : null;
    }
}
