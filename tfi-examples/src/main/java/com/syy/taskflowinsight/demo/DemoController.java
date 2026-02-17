package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.SafeContextManager;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * TFI 演示控制器。
 *
 * <p>提供快速开始指南中使用的 REST 演示端点，用于展示 TFI 在 Web 场景下的用法。
 *
 * <p><b>端点概览：</b>
 * <ul>
 *   <li>{@code GET /api/demo/hello/{name}} - 简单问候，演示基础 TFI 功能</li>
 *   <li>{@code POST /api/demo/process} - 处理请求，演示采样率与标签</li>
 *   <li>{@code POST /api/demo/async} - 异步处理，演示 TFI 上下文传播</li>
 *   <li>{@code POST /api/demo/async-comparison} - 正确与错误异步用法对比</li>
 * </ul>
 *
 * @since 3.0.0
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    
    /**
     * 简单的问候端点 - 演示基础TFI功能
     */
    @GetMapping("/hello/{name}")
    @TfiTask("greeting")
    public Map<String, Object> hello(@PathVariable String name) {
        return Map.of(
            "message", "Hello, " + name,
            "timestamp", System.currentTimeMillis(),
            "contextId", ManagedThreadContext.current() != null ? 
                     ManagedThreadContext.current().getContextId() : "unknown"
        );
    }
    
    /**
     * 处理请求 - 演示采样率和标签
     */
    @PostMapping("/process")
    @TfiTask(
        value = "processData",
        samplingRate = 0.5,
        logArgs = true,
        tags = {"important", "api"}
    )
    public Map<String, Object> process(@RequestBody Map<String, Object> request) {
        // 演示Stage API内部步骤追踪
        try (var validation = TFI.stage("validation")) {
            if (!request.containsKey("data")) {
                throw new IllegalArgumentException("Missing 'data' field");
            }
        }
        
        try (var processing = TFI.stage("processing")) {
            Thread.sleep(50); // 模拟处理时间
            return Map.of(
                "status", "processed",
                "data", request.get("data"),
                "processedAt", LocalDateTime.now().toString(),
                "contextId", ManagedThreadContext.current() != null ? 
                    ManagedThreadContext.current().getContextId() : "unknown"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processing interrupted", e);
        }
    }
    
    /**
     * 异步处理演示
     */
    @PostMapping("/async")
    @TfiTask("asyncDemo")
    public Map<String, Object> asyncDemo(@RequestBody Map<String, Object> request) {
        // 使用TFI上下文传播的异步执行
        SafeContextManager.getInstance().executeAsync("asyncProcessing", () -> {
            try {
                Thread.sleep(50);
                log.info("Async processing completed for: {}", request.get("data"));
                
                // 验证上下文传播
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    log.info("✅ Async context successfully propagated! ContextId: {}", context.getContextId());
                } else {
                    log.warn("❌ Context not propagated to async thread");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Async processing interrupted", e);
            }
        });
        
        return Map.of(
            "status", "submitted",
            "asyncStarted", true,
            "contextId", ManagedThreadContext.current() != null ? 
                ManagedThreadContext.current().getContextId() : "unknown",
            "message", "Using TFI context-aware async execution"
        );
    }
    
    /**
     * 上下文传播对比演示 - 展示正确和错误的异步用法
     */
    @PostMapping("/async-comparison")
    @TfiTask("asyncComparisonDemo")
    public Map<String, Object> asyncComparisonDemo(@RequestBody Map<String, Object> request) {
        String currentContextId = ManagedThreadContext.current() != null ? 
            ManagedThreadContext.current().getContextId() : "unknown";
        
        log.info("Main thread context ID: {}", currentContextId);
        
        // 错误方式：直接使用 CompletableFuture.runAsync（不传播上下文）
        CompletableFuture.runAsync(() -> {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                log.info("❌ Wrong way - Context unexpectedly found: {}", context.getContextId());
            } else {
                log.warn("❌ Wrong way - Context NOT propagated (expected)");
            }
        });
        
        // 正确方式：使用 SafeContextManager.executeAsync（自动传播上下文）
        SafeContextManager.getInstance().executeAsync("correctAsyncDemo", () -> {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                log.info("✅ Right way - Context successfully propagated: {}", context.getContextId());
            } else {
                log.error("✅ Right way - Context should have been propagated but wasn't!");
            }
        });
        
        return Map.of(
            "status", "comparison_started",
            "mainThreadContextId", currentContextId,
            "message", "Check logs to see the difference between wrong and right async execution"
        );
    }
}