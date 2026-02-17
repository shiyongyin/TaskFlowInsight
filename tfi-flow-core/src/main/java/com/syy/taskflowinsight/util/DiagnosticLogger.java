package com.syy.taskflowinsight.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 诊断日志工具类
 * <p>
 * 提供一次性诊断日志功能，避免重复输出刷屏。
 * 每个诊断码在同一线程/请求上下文中最多输出一次。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DiagnosticLogger.once(
 *     "TFI-DIAG-005",
 *     "RenderFallback",
 *     "Unknown render style alias '" + alias + "'",
 *     "Use simple/standard/detailed or provide RenderStyle object"
 * );
 * }</pre>
 *
 * <h3>设计特性</h3>
 * <ul>
 *   <li>线程隔离：每个线程维护独立的已输出集合</li>
 *   <li>全局去重：跨线程使用轻量 LRU 限制总条目数</li>
 *   <li>异常安全：内部捕获所有异常，不影响主流程</li>
 *   <li>日志级别：INFO（可配置 debug 显示详细堆栈）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public final class DiagnosticLogger {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticLogger.class);

    /**
     * 全局去重缓存（轻量 LRU）
     * 限制最大条目数，防止内存泄漏
     */
    private static final int MAX_GLOBAL_ENTRIES = 100;
    private static final ConcurrentHashMap<String, AtomicInteger> GLOBAL_DIAGNOSTICS = new ConcurrentHashMap<>();

    /**
     * 线程本地已输出诊断码集合
     * 确保同一线程/请求中每个诊断码最多输出一次
     */
    private static final ThreadLocal<Set<String>> THREAD_LOCAL_DIAGNOSTICS =
        ThreadLocal.withInitial(HashSet::new);

    /**
     * 私有构造函数，防止实例化
     */
    private DiagnosticLogger() {
        throw new UnsupportedOperationException("DiagnosticLogger is a utility class");
    }

    /**
     * 一次性诊断日志
     * <p>
     * 在当前线程/请求中，每个诊断码最多输出一次。
     * 输出格式：[code] title | Reason: reason | Advise: advise
     * </p>
     *
     * @param code 诊断码（如 "TFI-DIAG-005"）
     * @param title 诊断标题（如 "RenderFallback"）
     * @param reason 原因描述
     * @param advise 建议措施
     */
    public static void once(String code, String title, String reason, String advise) {
        try {
            // 参数校验
            if (code == null || code.isEmpty()) {
                return;
            }

            // 检查线程本地是否已输出
            Set<String> localSet = THREAD_LOCAL_DIAGNOSTICS.get();
            if (localSet.contains(code)) {
                // 已输出，直接返回
                return;
            }

            // 检查全局是否超过上限（轻量 LRU）
            if (GLOBAL_DIAGNOSTICS.size() >= MAX_GLOBAL_ENTRIES) {
                // 简单的 LRU：清理计数最低的条目
                evictLeastUsed();
            }

            // 记录到全局统计
            GLOBAL_DIAGNOSTICS.computeIfAbsent(code, k -> new AtomicInteger(0)).incrementAndGet();

            // 记录到线程本地
            localSet.add(code);

            // 格式化日志消息
            String message = formatDiagnosticMessage(code, title, reason, advise);

            // 输出 INFO 级别日志
            logger.info(message);

            // 如果启用 debug，输出当前堆栈
            if (logger.isDebugEnabled()) {
                logger.debug("Diagnostic triggered at:", new Exception("Diagnostic stacktrace"));
            }

        } catch (Throwable t) {
            // 绝不抛异常，避免影响主流程
            // 仅在严重错误时输出 error 日志
            try {
                logger.error("DiagnosticLogger internal error: {}", t.getMessage());
            } catch (Throwable ignored) {
                // 连日志都失败了，彻底忽略
            }
        }
    }

    /**
     * 格式化诊断消息
     *
     * @param code 诊断码
     * @param title 标题
     * @param reason 原因
     * @param advise 建议
     * @return 格式化后的消息
     */
    private static String formatDiagnosticMessage(String code, String title, String reason, String advise) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(code).append("]");

        if (title != null && !title.isEmpty()) {
            sb.append(" ").append(title);
        }

        if (reason != null && !reason.isEmpty()) {
            sb.append(" | Reason: ").append(reason);
        }

        if (advise != null && !advise.isEmpty()) {
            sb.append(" | Advise: ").append(advise);
        }

        return sb.toString();
    }

    /**
     * 清理计数最低的条目（轻量 LRU）
     */
    private static void evictLeastUsed() {
        try {
            // 找到计数最低的条目
            String minKey = null;
            int minCount = Integer.MAX_VALUE;

            for (var entry : GLOBAL_DIAGNOSTICS.entrySet()) {
                int count = entry.getValue().get();
                if (count < minCount) {
                    minCount = count;
                    minKey = entry.getKey();
                }
            }

            // 移除计数最低的条目
            if (minKey != null) {
                GLOBAL_DIAGNOSTICS.remove(minKey);
            }
        } catch (Exception e) {
            // 清理失败不影响主流程
            logger.debug("Failed to evict LRU entry: {}", e.getMessage());
        }
    }

    /**
     * 清理当前线程的诊断记录
     * <p>
     * 在请求结束或线程复用场景中调用，避免 ThreadLocal 泄漏。
     * </p>
     */
    public static void clearThreadLocal() {
        try {
            THREAD_LOCAL_DIAGNOSTICS.remove();
        } catch (Exception e) {
            // 清理失败不影响主流程
            logger.debug("Failed to clear thread local diagnostics: {}", e.getMessage());
        }
    }

    /**
     * 获取全局诊断统计
     * <p>
     * 仅用于测试和监控，返回不可变视图。
     * </p>
     *
     * @return 诊断码到触发次数的映射
     */
    public static java.util.Map<String, Integer> getGlobalStatistics() {
        try {
            java.util.Map<String, Integer> stats = new java.util.HashMap<>();
            for (var entry : GLOBAL_DIAGNOSTICS.entrySet()) {
                stats.put(entry.getKey(), entry.getValue().get());
            }
            return Collections.unmodifiableMap(stats);
        } catch (Exception e) {
            logger.debug("Failed to get global statistics: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 重置所有诊断记录
     * <p>
     * 仅用于测试场景，生产环境不应调用。
     * </p>
     */
    public static void reset() {
        try {
            GLOBAL_DIAGNOSTICS.clear();
            THREAD_LOCAL_DIAGNOSTICS.remove();
        } catch (Exception e) {
            logger.debug("Failed to reset diagnostics: {}", e.getMessage());
        }
    }
}
