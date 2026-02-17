package com.syy.taskflowinsight.actuator.support;

import java.time.Instant;

/**
 * 统一错误响应模型。
 *
 * <p>所有 TFI 端点在返回错误时应使用此 record，以确保客户端
 * 获得一致的、结构化的错误信息，而非裸异常堆栈。</p>
 *
 * <p>错误码规范：</p>
 * <ul>
 *   <li>{@code TFI-404} — 资源未找到</li>
 *   <li>{@code TFI-503} — 服务不可用（组件被禁用）</li>
 *   <li>{@code TFI-400} — 请求参数非法</li>
 *   <li>{@code TFI-500} — 内部错误</li>
 * </ul>
 *
 * @param code      错误码，如 {@code "TFI-404"}
 * @param message   面向用户的错误描述
 * @param hint      修复提示，如 {@code "check /sessions"}
 * @param timestamp 错误发生时间
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public record TfiErrorResponse(String code, String message, String hint, Instant timestamp) {

    /**
     * 快速构建 404 错误响应。
     *
     * @param resource 未找到的资源名称
     * @param hint     修复建议
     * @return 错误响应
     */
    public static TfiErrorResponse notFound(String resource, String hint) {
        return new TfiErrorResponse("TFI-404", resource + " not found", hint, Instant.now());
    }

    /**
     * 快速构建 503 服务不可用错误响应。
     *
     * @param service 不可用的服务名
     * @param hint    修复建议
     * @return 错误响应
     */
    public static TfiErrorResponse unavailable(String service, String hint) {
        return new TfiErrorResponse("TFI-503", service + " unavailable", hint, Instant.now());
    }

    /**
     * 快速构建 400 错误请求响应。
     *
     * @param detail 错误详情
     * @param hint   修复建议
     * @return 错误响应
     */
    public static TfiErrorResponse badRequest(String detail, String hint) {
        return new TfiErrorResponse("TFI-400", detail, hint, Instant.now());
    }
}
