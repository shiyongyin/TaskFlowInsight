---
id: TASK-230
title: Console/JSON 导出验证
owner: 待指派
priority: P2
status: Planned
estimate: 2人时
dependencies:
  - TASK-204（需要CHANGE消息写入）
---

## 1. 目标与范围
- 业务目标：验证CHANGE消息在现有导出器中正确显示
- 技术目标：编写集成测试验证ConsoleExporter和JsonExporter输出
- 范围（In Scope）：
  - [ ] Console导出格式验证
  - [ ] JSON导出格式验证
  - [ ] CHANGE消息内容验证
  - [ ] 无需修改现有导出器
- 边界（Out of Scope）：
  - [ ] 新增导出器
  - [ ] 修改导出格式

## 2. 输入 / 输出
- 输入：包含CHANGE消息的Session
- 输出：Console字符串和JSON对象

## 3. 设计与实现要点

### 集成测试实现
```java
// 位置：src/test/java/com/syy/taskflowinsight/export/ChangeMessageExportTest.java
@Test
public void testConsoleExportWithChangeMessages() {
    // 1. 创建会话并产生变更
    Session session = TFI.startSession("export-test");
    TFI.start("order-processing");
    
    Order order = new Order("ORD-001", "PENDING", 100.0);
    TFI.track("order", order, "status", "amount");
    order.setStatus("PAID");
    order.setAmount(150.0);
    TFI.track("order", order, "status", "amount");
    
    TFI.stop();
    TFI.endSession();
    
    // 2. 导出到Console
    ConsoleExporter exporter = new ConsoleExporter();
    String output = exporter.export(session);
    
    // 3. 验证输出包含CHANGE消息
    assertTrue(output.contains("order.status: PENDING → PAID"));
    assertTrue(output.contains("order.amount: 100.0 → 150.0"));
    assertTrue(output.contains("[CHANGE]") || output.contains("Type: CHANGE"));
}

@Test
public void testJsonExportWithChangeMessages() {
    // 创建会话并产生变更（同上）
    Session session = createSessionWithChanges();
    
    // 导出到JSON
    JsonExporter exporter = new JsonExporter();
    String jsonStr = exporter.export(session);
    
    // 解析JSON并验证
    JsonNode root = objectMapper.readTree(jsonStr);
    JsonNode tasks = root.path("tasks");
    
    boolean foundChange = false;
    for (JsonNode task : tasks) {
        JsonNode messages = task.path("messages");
        for (JsonNode msg : messages) {
            if ("CHANGE".equals(msg.path("type").asText())) {
                foundChange = true;
                String content = msg.path("content").asText();
                assertTrue(content.contains("→"));
            }
        }
    }
    
    assertTrue(foundChange, "应找到CHANGE类型消息");
}
```

### 验证正则表达式
```java
// Console输出验证模式
Pattern changePattern = Pattern.compile(
    "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]\\s+\\[CHANGE\\]\\s+(.+):\\s+(.+)\\s+→\\s+(.+)"
);

// JSON结构验证
{
  "session": {
    "id": "...",
    "tasks": [{
      "name": "order-processing",
      "messages": [{
        "type": "CHANGE",
        "content": "order.status: PENDING → PAID",
        "timestamp": "..."
      }]
    }]
  }
}
```

## 4. 开发清单（可勾选）
- [ ] 代码实现：ChangeMessageExportTest.java
- [ ] 代码实现：testConsoleExportWithChangeMessages()
- [ ] 代码实现：testJsonExportWithChangeMessages()
- [ ] 文档补全：导出格式规范文档
- [ ] 压测脚本与报告：大量CHANGE消息导出性能
- [ ] 回滚/灰度预案：测试失败不影响导出器

## 5. 测试要求（可勾选）
- 单元测试
  - [ ] 覆盖率 ≥ 80%
  - [ ] 边界/异常用例（空消息、特殊字符）
- 集成测试
  - [ ] 关键路径通过（Console/JSON导出）
  - [ ] 回归用例通过（其他消息类型）
- 性能测试
  - [ ] P95 写开销 ≤ 3%
  - [ ] 查询 ≤ 200μs @ 导出序列化

## 6. 关键指标（可勾选）
- [ ] 采集成功率 ≥ 99%
- [ ] 落盘失败率 ≤ 1%
- [ ] 检索 P95 ≤ 200μs
- [ ] 可视化 2 步到达差异详情（Console/JSON可见）

## 7. 验收标准（可勾选）
- [ ] 功能验收：导出结果包含CHANGE消息
- [ ] 文档齐备（输出格式示例）
- [ ] 监控告警就绪（暂无）
- [ ] 风险关闭或降级可接受（格式兼容）

## 8. 风险评估（可勾选）
- [ ] 性能：大量CHANGE消息对导出性能影响
- [ ] 稳定性：特殊字符转义
- [ ] 依赖与外部影响：依赖现有导出器
- [ ] 安全与合规：敏感数据已脱敏

## 9. 里程碑与排期
- 计划里程碑：M0阶段验证完成
- DOR（就绪定义）
  - [ ] 需求输入齐备（docs/task/v2.0.0-mvp/export-verification/TASK-230-Console-Json-ChangeMessage-Verification.md）
  - [ ] 依赖版本锁定（TASK-204完成）
- DOD（完成定义）
  - [ ] 全测试通过（Console/JSON验证）
  - [ ] 指标达标（导出正确）
  - [ ] 灰度/回滚演练完成（格式验证）

## 10. 证据与引用
- 源文档：docs/task/v2.0.0-mvp/export-verification/TASK-230-Console-Json-ChangeMessage-Verification.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java
  - src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java
- 备注：无需修改导出器，仅验证

## 11. 开放问题（必须列出）
- [ ] CHANGE消息在Console中的格式规范
- [ ] JSON中timestamp格式（ISO8601 vs 毫秒）
- [ ] 是否需要支持其他导出格式（XML/CSV）
- [ ] 大量消息时的分页处理