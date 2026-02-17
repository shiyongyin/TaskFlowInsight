package com.syy.taskflowinsight.demo.chapters;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.demo.core.DemoChapter;
import com.syy.taskflowinsight.demo.util.DemoUI;

import java.util.Arrays;
import java.util.List;

/**
 * 第 10 章：Spring 集成 — TFI 在 Spring Boot 环境下的完整集成指南。
 *
 * <p>本章通过递进场景，帮助开发者掌握 TFI 与 Spring Boot 的集成方式：
 * <ol>
 *   <li>{@code @TfiTask} 注解 — 声明式任务追踪</li>
 *   <li>配置体系 — {@code application.yml} 中的 {@code tfi.*} 配置项</li>
 *   <li>Actuator 端点 — {@code /actuator/taskflow} 监控端点</li>
 *   <li>REST 端点集成 — 在 {@code @RestController} 中使用 TFI</li>
 *   <li>最佳实践 — Spring 环境下的 TFI 使用建议</li>
 * </ol>
 *
 * <p><b>学习目标：</b>理解 TFI 如何通过 Spring Boot 自动配置、AOP 和 Actuator
 * 实现非侵入式的业务流程追踪与监控。
 *
 * <p><b>注意：</b>本章为 CLI 模式章节，通过代码片段展示和说明来演示 Spring 集成功能。
 * 要体验完整的 Spring 集成效果，请启动 Spring Boot 应用并访问 REST 端点。
 *
 * @since 4.0.0
 */
public class SpringIntegrationChapter implements DemoChapter {

    @Override
    public int getChapterNumber() { return 10; }

    @Override
    public String getTitle() { return "Spring 集成"; }

    @Override
    public String getDescription() {
        return "@TfiTask / Actuator / 配置体系 / @EnableTfi 完整指南";
    }

    @Override
    public void run() {
        DemoUI.printChapterHeader(10, getTitle(), getDescription());
        TFI.enable();

        DemoUI.section("10.1 @TfiTask 注解 — 声明式任务追踪");
        tfiTaskAnnotationDemo();

        DemoUI.section("10.2 配置体系 — application.yml 中的 tfi.* 配置项");
        configurationDemo();

        DemoUI.section("10.3 Actuator 端点 — /actuator/taskflow 监控");
        actuatorEndpointDemo();

        DemoUI.section("10.4 REST 端点集成 — @RestController + TFI");
        restEndpointDemo();

        DemoUI.section("10.5 最佳实践 — Spring 环境下的 TFI 使用建议");
        bestPracticesDemo();

        DemoUI.printSectionSummary("Spring 集成演示完成", getSummaryPoints());
    }

    /**
     * 场景 1：展示 {@code @TfiTask} 注解的完整参数与用法。
     *
     * <p>核心点：通过 AOP 切面，{@code @TfiTask} 可实现：
     * <ul>
     *   <li>自动开始/结束任务追踪</li>
     *   <li>条件性追踪（{@code condition}）</li>
     *   <li>采样率控制（{@code samplingRate}）</li>
     *   <li>参数/结果日志（{@code logArgs}/{@code logResult}）</li>
     * </ul>
     */
    private void tfiTaskAnnotationDemo() {
        System.out.println("  @TfiTask 是 TFI 的声明式入口，标注在方法上实现自动追踪。");
        System.out.println();
        System.out.println("  ┌─── 基础用法 ───────────────────────────────────────────────┐");
        System.out.println("  │ @TfiTask(\"greeting\")                                       │");
        System.out.println("  │ public String hello(String name) { ... }                    │");
        System.out.println("  └────────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─── 高级参数 ───────────────────────────────────────────────┐");
        System.out.println("  │ @TfiTask(                                                   │");
        System.out.println("  │   value = \"processData\",                                    │");
        System.out.println("  │   samplingRate = 0.5,      // 50% 采样率                    │");
        System.out.println("  │   logArgs = true,           // 记录方法入参                 │");
        System.out.println("  │   logResult = true,         // 记录返回值                   │");
        System.out.println("  │   tags = {\"important\",\"api\"} // 自定义标签                  │");
        System.out.println("  │ )                                                           │");
        System.out.println("  └────────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─── 条件追踪 ───────────────────────────────────────────────┐");
        System.out.println("  │ @TfiTask(                                                   │");
        System.out.println("  │   value = \"conditionalTask\",                                │");
        System.out.println("  │   condition = \"#input != null && #input.length() > 0\"       │");
        System.out.println("  │ )                                                           │");
        System.out.println("  │ // 仅当 input 非空时才启用追踪                              │");
        System.out.println("  └────────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  [实际验证] 在 CLI 模式下模拟 @TfiTask 的等效编程式调用：");
        try (var stage = TFI.stage("tfiTask-equivalent")) {
            stage.message("这等价于 @TfiTask(\"tfiTask-equivalent\") 的 AOP 效果");
            System.out.println("    stage 开启 → 等价于 @TfiTask 自动开始任务");
        }
        System.out.println("    stage 关闭 → 等价于 @TfiTask 自动结束任务");
    }

    /**
     * 场景 2：展示 TFI 在 {@code application.yml} 中的配置体系。
     */
    private void configurationDemo() {
        System.out.println("  TFI 通过 tfi.* 前缀在 application.yml 中进行配置。");
        System.out.println();
        System.out.println("  ┌─── 核心配置项 ─────────────────────────────────────────────┐");
        System.out.println("  │                                                              │");
        System.out.println("  │  tfi:                                                        │");
        System.out.println("  │    enabled: true              # 主开关（默认 false）         │");
        System.out.println("  │    annotation:                                               │");
        System.out.println("  │      enabled: true            # @TfiTask 注解支持            │");
        System.out.println("  │                                                              │");
        System.out.println("  │    change-tracking:                                          │");
        System.out.println("  │      enabled: true            # 变更追踪开关                 │");
        System.out.println("  │      snapshot:                                               │");
        System.out.println("  │        max-depth: 10          # 最大遍历深度                 │");
        System.out.println("  │        time-budget-ms: 1000   # 单次快照时间预算             │");
        System.out.println("  │                                                              │");
        System.out.println("  │    compare:                                                  │");
        System.out.println("  │      auto-route:                                             │");
        System.out.println("  │        entity.enabled: true   # Entity 列表自动路由          │");
        System.out.println("  │        lcs.enabled: true      # LCS 策略开关                 │");
        System.out.println("  │                                                              │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  [实际验证] 读取当前运行时状态：");
        System.out.println("    TFI.isEnabled() = " + TFI.isEnabled());
        System.out.println("    TFI.isChangeTrackingEnabled() = " + TFI.isChangeTrackingEnabled());
    }

    /**
     * 场景 3：展示 Spring Actuator 端点的集成能力。
     */
    private void actuatorEndpointDemo() {
        System.out.println("  TFI 提供 Spring Actuator 端点用于运行时监控。");
        System.out.println();
        System.out.println("  ┌─── Actuator 端点清单 ──────────────────────────────────────┐");
        System.out.println("  │                                                              │");
        System.out.println("  │  GET /actuator/taskflow        查看 TFI 运行状态             │");
        System.out.println("  │  GET /actuator/health          包含 TFI 健康检查             │");
        System.out.println("  │  GET /actuator/metrics          Micrometer 指标              │");
        System.out.println("  │  GET /actuator/prometheus       Prometheus 格式导出          │");
        System.out.println("  │                                                              │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─── application.yml 配置 ───────────────────────────────────┐");
        System.out.println("  │                                                              │");
        System.out.println("  │  management:                                                 │");
        System.out.println("  │    endpoints:                                                │");
        System.out.println("  │      web:                                                    │");
        System.out.println("  │        exposure:                                             │");
        System.out.println("  │          include: health,taskflow,metrics,prometheus          │");
        System.out.println("  │                                                              │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  提示: 启动 Spring Boot 后，访问 http://localhost:19090/actuator/taskflow");
    }

    /**
     * 场景 4：展示在 REST 端点中集成 TFI 的最佳方式。
     */
    private void restEndpointDemo() {
        System.out.println("  在 @RestController 中结合 @TfiTask + Stage API 实现分步追踪。");
        System.out.println();
        System.out.println("  ┌─── DemoController 示例 ────────────────────────────────────┐");
        System.out.println("  │                                                              │");
        System.out.println("  │  @PostMapping(\"/process\")                                    │");
        System.out.println("  │  @TfiTask(value=\"processData\", samplingRate=0.5,             │");
        System.out.println("  │           logArgs=true, tags={\"important\",\"api\"})             │");
        System.out.println("  │  public Map<String,Object> process(@RequestBody req) {       │");
        System.out.println("  │      try (var v = TFI.stage(\"validation\")) {                 │");
        System.out.println("  │          // 参数校验                                         │");
        System.out.println("  │      }                                                       │");
        System.out.println("  │      try (var p = TFI.stage(\"processing\")) {                 │");
        System.out.println("  │          // 业务处理                                         │");
        System.out.println("  │      }                                                       │");
        System.out.println("  │      return result;                                          │");
        System.out.println("  │  }                                                           │");
        System.out.println("  │                                                              │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  [实际验证] 模拟 REST 端点的 TFI 追踪流程：");
        try (var stage = TFI.stage("rest-validation")) {
            stage.message("验证请求参数");
            System.out.println("    [validation] 参数校验完成");
        }
        try (var stage = TFI.stage("rest-processing")) {
            stage.message("执行业务逻辑");
            System.out.println("    [processing] 业务处理完成");
        }
        System.out.println();
        System.out.println("  可用 REST 端点（启动 Spring Boot 后）：");
        System.out.println("    GET  http://localhost:19090/api/demo/hello/{name}");
        System.out.println("    POST http://localhost:19090/api/demo/process");
        System.out.println("    POST http://localhost:19090/api/demo/async");
        System.out.println("    POST http://localhost:19090/api/demo/async-comparison");
    }

    /**
     * 场景 5：总结 Spring 环境下的 TFI 最佳实践。
     */
    private void bestPracticesDemo() {
        System.out.println("  ┌─── Spring 集成最佳实践 ─────────────────────────────────────┐");
        System.out.println("  │                                                              │");
        System.out.println("  │  1. 优先使用 @TfiTask 注解而非编程式 API                     │");
        System.out.println("  │     → 非侵入式，与 Spring AOP 天然配合                      │");
        System.out.println("  │                                                              │");
        System.out.println("  │  2. 方法内部分步骤用 TFI.stage() + try-with-resources        │");
        System.out.println("  │     → 自动关闭，零泄漏保证                                  │");
        System.out.println("  │                                                              │");
        System.out.println("  │  3. 异步场景用 SafeContextManager.executeAsync()             │");
        System.out.println("  │     → 自动传播 TFI 上下文到异步线程                         │");
        System.out.println("  │                                                              │");
        System.out.println("  │  4. 生产环境开启 Actuator + Prometheus 监控                  │");
        System.out.println("  │     → 实时观测 TFI 运行状态和性能指标                       │");
        System.out.println("  │                                                              │");
        System.out.println("  │  5. 敏感字段使用 tfi.change-tracking.snapshot.exclude-patterns│");
        System.out.println("  │     → 自动脱敏 password/secret/token 等                     │");
        System.out.println("  │                                                              │");
        System.out.println("  │  6. 通过 tfi.enabled=false 可完全无损禁用                    │");
        System.out.println("  │     → 禁用后 TFI 所有操作退化为空实现（零开销）             │");
        System.out.println("  │                                                              │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
    }

    @Override
    public List<String> getSummaryPoints() {
        return Arrays.asList(
                "学会了 @TfiTask 注解的声明式任务追踪（采样、条件、日志）",
                "了解了 tfi.* 配置体系及核心配置项",
                "掌握了 Actuator 端点监控（/actuator/taskflow）",
                "学会了在 @RestController 中集成 @TfiTask + TFI.stage()",
                "掌握了 Spring 环境下 TFI 的 6 大最佳实践"
        );
    }
}
