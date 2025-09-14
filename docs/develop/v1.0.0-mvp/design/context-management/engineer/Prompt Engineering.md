# Prompt Engineering（提示工程）指南 —— TaskFlowInsight Context-Management

本指南提供在“上下文管理”模块相关的AI交互中（如生成诊断报告、压测计划、测试用例、运维告警解读等）可复用、可审计、可落地的提示模板与策略，确保结果稳定、结构化、可追溯。

> 原则：最小必要上下文、结构化输出（JSON/YAML/Markdown表格）、禁止长链路思维暴露、可重复执行与可比对。

## 1. 使用场景与输出契约
- 场景A：上下文树（Session/TaskNode）摘要与可读化报告
  - 目的：将复杂任务树转为可读摘要，供研发/运维快速理解。
  - 输出：受控Markdown或JSON，包含关键信息与统计。
- 场景B：泄漏与健康状态诊断
  - 目的：根据指标（contexts_created/cleaned、leaks_detected/fixed、health）生成诊断结论与建议动作。
  - 输出：JSON（等级、证据、建议、风险）。
- 场景C：测试用例生成与补齐
  - 目的：基于接口契约与边界条件生成/补齐单测与并发测试点。
  - 输出：Markdown检查清单+JUnit骨架片段。
- 场景D：性能压测计划与报告草稿
  - 目的：生成可执行的压测清单、数据量级与观测项；形成报告框架。
  - 输出：Markdown大纲+YAML配置示例。

## 2. 输入上下文规范（最小且充足）
- 元信息：
  - `module`: 固定为 `context-management`
  - `version`: 语义化版本，例如 `v1.0.0-mvp`
- 数据摘要（尽量结构化，避免长文本）：
  - `sessions`: 数量、示例ID
  - `tasks`: 深度、最大子数、示例路径
  - `metrics`: contexts_created/cleaned, leaks_detected/fixed, active_threads, health
  - `limits`: 配置如 leak_detection_interval, max_context_age 等
- 约束：禁止提供敏感数据（账户/密钥/内网地址）；禁止输出思维过程，仅给最终结论与推理要点。

示例（输入片段，JSON）：
```json
{
  "module": "context-management",
  "version": "v1.0.0-mvp",
  "metrics": {
    "contexts_created": 120345,
    "contexts_cleaned": 120340,
    "leaks_detected": 3,
    "leaks_fixed": 3,
    "active_threads": 48,
    "health": "HEALTHY"
  },
  "tasks": {"max_depth": 4, "examples": ["root/api/login", "root/order/pay"]},
  "limits": {"leak_detection_interval_ms": 60000, "max_context_age_ms": 3600000}
}
```

## 3. 模板库（可直接复用）
### 3.1 上下文树摘要（简报）
指令：
```
你是一名资深Java性能与并发工程师。给定任务树与会话指标，请输出面向研发的简报：
- 受众：后端同事
- 要求：
  1) 以要点列举主要路径与热点任务；
  2) 指出潜在深度/广度风险；
  3) 给出三条可操作改进建议；
- 输出：Markdown，最多200行，禁止输出思维过程。
- 输入：<JSON数据>
```
输出结构示例：
```markdown
## 任务树简报（v1.0.0-mvp）
- 热点路径：root/api/login, root/order/pay
- 最大深度：4；建议深度阈值：<=6
- 风险摘要：
  - [中] 某些任务链较深，建议拆分I/O等待段
- 改进建议：
  - 为异步链使用快照+装饰器，避免ITL隐式继承
  - 对热路径增加采样日志并聚合统计
  - 为超时任务增加失败快速回收策略
```

### 3.2 泄漏与健康诊断（结构化）
指令：
```
你是内存与线程诊断专家。基于输入指标判断健康状态并给出行动建议。
- 输出JSON，字段：
  {"level":"HEALTHY|WARNING|CRITICAL","evidence":[...],"actions":[...],"risk_note":"..."}
- 不输出额外文字，不输出思维过程。
- 输入：<JSON指标>
```
输出示例：
```json
{
  "level": "HEALTHY",
  "evidence": ["leaks_detected=3","leaks_fixed=3","delta=0"],
  "actions": ["维持现有检测周期","保留反射清理开关为关闭"],
  "risk_note": "若活跃线程>200且检测周期>2min，考虑缩短周期"
}
```

### 3.3 测试用例补齐清单
指令：
```
你是测试设计专家。根据接口契约与边界条件，生成单元/并发测试清单：
- 输出Markdown清单，按：功能/异常/并发/性能/稳定性 分组
- 对每条给出"目的-步骤-断言"
- 不超过250行，不输出思维过程。
- 输入：<契约与边界条件>
```

### 3.4 性能压测计划与报告骨架
指令：
```
你是性能工程师。根据指标与目标，生成压测计划与报告骨架：
- 输出Markdown大纲，含：目标/场景/数据规模/度量项/风险与解读
- 附带一份YAML配置示例（含QPS/并发度/时长/采样）
- 不超过200行，不输出思维过程。
- 输入：<指标与目标>
```

## 4. 结构化与可比对输出规范
- JSON模式（示例）：
```json
{
  "title": "string",
  "findings": ["string"],
  "recommendations": ["string"],
  "severity": "LOW|MEDIUM|HIGH",
  "links": ["url"]
}
```
- Markdown表格：用于清单/报告，严格限制列数（<=6列）。
- YAML：用于工具配置/压测参数，避免多文档分隔符。

## 5. 安全与隐私
- 输入脱敏：不得包含访问密钥、内网域名、账号、PII。
- 输出合规：禁止产生或要求“推理过程/思维链”。
- 归档：保留提示与输出版本号、时间戳、数据摘要（非原始数据）。

## 6. 质量保障与复核
- 再现性：相同输入与模板，在同一版本模型下输出应稳定。
- 差异对比：结构化输出支持变更diff（字段对比）。
- 人审步骤：对CRITICAL级别建议与报告必须人工复核。

## 7. 快速上手清单（Checklist）
- 选择合适模板（A/B/C/D）。
- 准备最小充分的JSON输入（指标与样例路径即可）。
- 指定输出格式（JSON/Markdown/YAML）。
- 禁止长链路思维与额外文本。
- 保存版本号与时间戳，便于复查。

---
以上模板与规范面向上下文管理模块在运维、测试、性能与诊断领域的高频需求。可按场景扩展，但必须保持“最小上下文+结构化输出+可比对”的核心原则。

