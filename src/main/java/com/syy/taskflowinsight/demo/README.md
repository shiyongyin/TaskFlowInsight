# TaskFlow Insight - 完整功能演示（示例入口）

## 概览

TaskFlowInsightDemo 是一个交互式演示，围绕“电商下单场景”循序渐进展示 TaskFlow Insight 的核心能力：从基础 API 到并发、异常恢复、性能对比与高级 API。

新版已拆分为“主入口 + 章节实现 + 复用服务”，职责更清晰、可维护性更高。

## 运行演示

- IDE 直接运行：主类 `com.syy.taskflowinsight.demo.TaskFlowInsightDemo`
- 命令行（无需插件）：
  - `./mvnw -q -DskipTests compile`
  - `java -cp target/classes com.syy.taskflowinsight.demo.TaskFlowInsightDemo [1|2|3|4|5|all]`
- 命令行（可选，若已配置 exec:java 插件）：
  - `./mvnw -q -DskipTests exec:java -Dexec.mainClass="com.syy.taskflowinsight.demo.TaskFlowInsightDemo" -Dexec.args="1"`

支持参数：`1|2|3|4|5|all|help`；未传参则进入交互式菜单。

交互菜单小技巧：在菜单界面按 `h` 可直接打印“代码路径与目录结构”。

## 演示目录（章节 → 类）

- 第1章 快速入门 → `chapters.QuickStartChapter`
- 第2章 实际业务场景 → `chapters.BusinessScenarioChapter`
- 第3章 高级特性 → `chapters.AdvancedFeaturesChapter`
- 第4章 最佳实践 → `chapters.BestPracticesChapter`
- 第5章 高级API功能 → `chapters.AdvancedApiChapter`

辅助组件：
- 订单示例服务 → `service.EcommerceDemoService`
- 领域模型 → `model.Order`, `model.UserOrderResult`
- 控制台工具 → `util.DemoUI`, `util.DemoUtils`

## 代码路径与目录结构

所有演示代码均位于：`src/main/java/com/syy/taskflowinsight/demo`

```
src/main/java/com/syy/taskflowinsight/demo
├── TaskFlowInsightDemo.java              # 主入口（菜单与调度）
├── core/
│   ├── DemoChapter.java                  # 章节接口
│   └── DemoRegistry.java                 # 章节注册与查找
├── chapters/
│   ├── QuickStartChapter.java            # 第1章：快速入门
│   ├── BusinessScenarioChapter.java      # 第2章：实际业务场景
│   ├── AdvancedFeaturesChapter.java      # 第3章：高级特性
│   ├── BestPracticesChapter.java         # 第4章：最佳实践
│   └── AdvancedApiChapter.java           # 第5章：高级API功能
├── service/
│   └── EcommerceDemoService.java         # 电商示例业务逻辑
├── model/
│   ├── Order.java                        # 订单模型
│   └── UserOrderResult.java              # 并发下单结果
└── util/
    ├── DemoUI.java                       # 控制台输出工具
    └── DemoUtils.java                    # 通用工具（sleep等）
```

快速跳转（可点击打开文件）：
- `src/main/java/com/syy/taskflowinsight/demo/TaskFlowInsightDemo.java`
- `src/main/java/com/syy/taskflowinsight/demo/chapters/QuickStartChapter.java`
- `src/main/java/com/syy/taskflowinsight/demo/chapters/BusinessScenarioChapter.java`
- `src/main/java/com/syy/taskflowinsight/demo/chapters/AdvancedFeaturesChapter.java`
- `src/main/java/com/syy/taskflowinsight/demo/chapters/BestPracticesChapter.java`
- `src/main/java/com/syy/taskflowinsight/demo/chapters/AdvancedApiChapter.java`
- `src/main/java/com/syy/taskflowinsight/demo/service/EcommerceDemoService.java`

## 你将学到（5分钟起步）

- 使用 `TFI.run()`/`TFI.call()` 追踪无/有返回值任务
- 使用 TaskContext 管理属性、标签与子任务
- 记录 PROCESS/METRIC/CHANGE/ALERT 四类消息
- 组织多步骤业务流程与查看报告
- 并发下的独立会话与结果汇总
- 异常恢复与状态管理（success/fail/stop）

## 快速上手代码片段

```java
// 无返回值任务
TFI.run("发送邮件", () -> TFI.message("发送邮件给用户", MessageType.PROCESS));

// 有返回值任务
String result = TFI.call("查询数据", () -> {
    TFI.message("查询数据库", MessageType.PROCESS);
    return "查询结果";
});
```

## 设计原则（为何这样拆分）

- 会话边界内聚：每个章节自己 `startSession/endSession`，报告一目了然
- 消息类型清晰：PROCESS=流程、METRIC=指标、CHANGE=变更、ALERT=异常
- 时长口径一致：节点显示累计/自身时长，与 JSON 导出字段一致
- 并发输出预期：多线程报告可能交错，但每个会话完整不丢失

## 常见问题（FAQ）

- 并发报告交错是异常吗？ → 不是，这是并发输出的正常现象
- 禁用后任务还会执行吗？ → 会执行但不被记录（`TFI.disable()`）
- 何时用 run/call/context？ → 简单无返回值用 run；有返回值用 call；需要上下文/属性/标签时用 try-with-resource 的 `TaskContext`

## 进阶练习

- 修改某个消息为 `METRIC` 并观察报告差异
- 给任一章节新增一个子任务并查看层级
- 切换导出格式：控制台/JSON/Map，理解各自用途

— 完 —
