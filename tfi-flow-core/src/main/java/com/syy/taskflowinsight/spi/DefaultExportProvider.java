package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExportOptions;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;

import java.util.Map;

import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.TREE;

/**
 * 默认导出服务提供者。
 *
 * <p>委托 core 内置 exporter，并按 ExportProvider 参数的字面语义固定输出选择。
 *
 * <p>默认导出 Provider 会优先读取当前 {@link FlowProvider} 的 Session，再回退到底层
 * {@link ManagedThreadContext}。这个顺序是为了兼容自定义 FlowProvider：
 * 用户只替换流程 Provider、没有替换导出 Provider 时，Console/JSON/Map 仍应导出自定义 Provider
 * 持有的会话，而不是只看默认线程上下文。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultExportProvider implements ExportProvider {

    /**
     * 创建无状态的兜底导出 Provider。
     */
    public DefaultExportProvider() {
    }

    @Override
    public boolean exportToConsole(boolean showTimestamp) {
        Session session = currentSession();
        if (session == null) {
            return false;
        }
        ConsoleExportOptions options = new ConsoleExportOptions(TREE, showTimestamp);
        new ConsoleExporter().print(session, System.out, options);
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
        try {
            /*
             * 不通过 TfiFlow.getCurrentSession()，避免 ExportProvider -> TfiFlow -> ExportProvider
             * 形成递归依赖；直接查 Registry 即可拿到当前最高优先级 FlowProvider。
             */
            FlowProvider provider = ProviderRegistry.resolve(FlowProvider.class);
            if (provider != null) {
                return provider.currentSession();
            }
        } catch (Exception ignored) {
            // 回退到底层线程上下文，保持默认导出的异常安全语义。
        }

        ManagedThreadContext context = ManagedThreadContext.current();
        return context != null ? context.getCurrentSession() : null;
    }
}
