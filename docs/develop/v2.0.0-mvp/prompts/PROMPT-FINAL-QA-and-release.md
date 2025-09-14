## 1) SYSTEM
你是资深 Java 开发工程师与发布经理。请在 M2‑M0 全量开发完成后，统一执行收尾质检与发布：回归、覆盖与证据汇总、发布清单与回滚演练。

## 2) CONTEXT & SOURCES（务必具体）
- 合并卡索引与卡片：../cards-final/INDEX.md、../cards-final/*.md（应全部☑️）
- 阶段提示词与产物：../prompts/PROMPT-PHASE-*.md（用于回溯范围）
- 代码与测试：`src/main/java/**`, `src/test/java/**`
- 运行命令：`./mvnw clean verify`、`./mvnw -q -DskipTests=false test`、`./mvnw spring-boot:run`

## 3) GOALS
- 业务目标：形成可交付的 M2‑M0 MVP 版本包与发布说明，附完整证据。
- 技术目标：一次跑通回归，整理输出（测试报告、Console/JSON 片段、基准报告、卡片☑️清单），生成发布与回滚清单。

## 4) SCOPE
- In Scope：
  - [ ] 全量回归：单测/集成/基准（如基准耗时，可执行最小一组并附历史报告）
  - [ ] 证据汇编：测试摘要、CHANGE 片段、基准报告（含环境）、卡片☑️列表
  - [ ] 发布清单：改动文件列表、影响范围、兼容性说明、启用方式（双开关）
  - [ ] 回滚演练：哪些改动可回滚、如何回退开关、风险点
- Out of Scope：
  - [ ] 引入新依赖或扩展超出 M0 的功能

## 5) EXECUTION PLAN（动作顺序）
1. 回归测试：
   - 运行 `./mvnw -q -DskipTests=false test`；输出总数/通过/失败=0；贴关键信息
   - 如有基准代码，运行最小一组并附报告（或引用阶段 C 产出）
2. 证据汇编：
   - Console/JSON 的 `CHANGE` 片段（来自阶段 A/B 的集成测试）
   - MessageFormat 正则断言截图或文本
   - Reflection 缓存命中/上限行为简述
   - 并发/生命周期测试结果摘要（无随机失败）
3. 发布清单：
   - 改动文件分组：新增 tracking/**、TFI.java 修改、ManagedThreadContext.java 修改、配置类新增与启用装配、测试与基准代码
   - 影响与兼容：默认禁用（`tfi.change-tracking.enabled=false`），全局开关 + 变更追踪开关两层控制
   - 启用方式：Spring 配置文件或环境变量覆盖 `tfi.change-tracking.enabled=true`；可选指定 `valueReprMaxLength`
4. 回滚演练：
   - 关闭变更追踪开关（恢复禁用）
   - 必要时移除 TFI.stop 尾部刷新段（小范围回滚）
   - 遇故障的处置顺序：禁用 → 降低字段集 → 缩短 Demo/基准执行 → 仅保留 CHANGE 文本

## 6) DELIVERABLES（输出必须包含）
- 回归测试摘要：用例总数/通过/失败；关键断言片段
- 证据包：Console/JSON CHANGE 片段、格式正则、缓存命中/上限行为、并发/生命周期说明、基准报告（含环境参数）
- 发布说明（Markdown）：范围、改动文件、启用/回滚、兼容性与已知限制

## 7) ACCEPTANCE（核对清单，默认空）
- [ ] 测试：失败=0；无随机失败
- [ ] 文档：发布说明可用；卡片全☑️
- [ ] 观测：日志级别合理；异常不冒泡
- [ ] 性能：建议目标达成或附改进计划

## 8) RISKS & MITIGATIONS
- 环境差异导致基准波动 → 固定参数并记录环境；对比量级而非绝对值
- 配置缺失 → 双开关确保可回退；默认禁用降低风险

## 9) REPORT（一次性回传）
- 回归报告与关键断言
- 证据包与链接路径
- 发布说明全文

