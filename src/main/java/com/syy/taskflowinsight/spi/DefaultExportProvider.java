package com.syy.taskflowinsight.spi;

import java.util.Collections;
import java.util.Map;

/**
 * 默认导出服务提供者（Fallback实现）
 *
 * <p>当没有其他Provider可用时，返回空结果
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultExportProvider implements ExportProvider {

    @Override
    public boolean exportToConsole(boolean showTimestamp) {
        // 默认实现：无操作
        return false;
    }

    @Override
    public String exportToJson() {
        // 默认实现：空JSON对象
        return "{}";
    }

    @Override
    public Map<String, Object> exportToMap() {
        // 默认实现：空Map
        return Collections.emptyMap();
    }

    @Override
    public int priority() {
        return -1000;  // 最低优先级
    }
}
