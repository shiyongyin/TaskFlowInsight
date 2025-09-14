## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-202-DiffDetector-Scalar.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java#com.syy.taskflowinsight.tracking.detector.DiffDetector.diff(String,Map,Map)
  - src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java#com.syy.taskflowinsight.tracking.model.ChangeRecord
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeType.java#com.syy.taskflowinsight.tracking.ChangeType
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.repr(Object)
- 相关配置/SQL/脚本：
  - N/A（M0 不落盘）；性能脚本可后续补充到 `docs/perf/`（若需要）。
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：在仅标量的前提下，对 before/after 快照进行差异检测，产出稳定可读的变更集，供 ChangeTracker/TFI/导出器消费。
- 技术目标：
  - 并集字段遍历，判定 CREATE/DELETE/UPDATE；Date 使用毫秒时间戳归一化比较。
  - 结果按字段名字典序排序；valueRepr 统一由 ObjectSnapshot.repr(newValue) 生成（DELETE 置 null）。

## 4) SCOPE
- In Scope：
  - [ ] `DiffDetector.diff(String, Map<String,Object>, Map<String,Object>)` 行为完整（并集/判定/排序/上下文元数据填充）。
  - [ ] `valueKind` 分类：STRING/NUMBER/BOOLEAN/DATE/ENUM/OTHER。
  - [ ] valueType 使用 FQCN；sessionId/taskPath 来自 `ManagedThreadContext.current()`。
- Out of Scope：
  - [ ] 集合/Map/复杂对象对比；掩码/序列化策略（留扩展点，不实现）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响文件：`tracking/detector/DiffDetector.java`（主逻辑）。
2. 方法签名与骨架（已存在，校验并完善）：
```java
public final class DiffDetector {
  public static List<ChangeRecord> diff(String obj, Map<String,Object> b, Map<String,Object> a);
  // normalize(Date->long), detectChangeType(), getValueKind() 保持私有
}
```
3. 按卡片规则完善：并集字段、时间戳归一化、稳定排序；DELETE 场景 valueRepr=null。
4. 输出 unified diff；补充 Javadoc 说明删除场景 valueRepr 语义。
5. 本地验证：`./mvnw test`；与 201/203 联调 smoke（可在 demo/ 中构造 1~2 个字段变更）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：完善 `DiffDetector.diff` 与内部 normalize/kind/detect 方法；Javadoc 更新。
- 测试：
  - 单测矩阵：null/类型变化/相等/不等 × STRING/NUMBER/BOOLEAN/DATE。
  - 排序稳定性断言；DELETE valueRepr=null 断言。
- 文档：README 片段或任务卡结论回填。
- 回滚/灰度：N/A（纯计算逻辑）。
- 观测：异常仅 DEBUG，不冒泡。

## 7) API & MODELS（必须具体化）
```java
public static List<ChangeRecord> DiffDetector.diff(String objectName,
    Map<String,Object> before, Map<String,Object> after)
```
- 错误/异常：上下文获取失败 → DEBUG 后继续；null 快照允许。
- DTO：`ChangeRecord` 字段集对齐 201。

## 8) DATA & STORAGE
- 无数据库；变更集内存构造，立即消费。

## 9) PERFORMANCE & RELIABILITY
- 性能预算：2 字段 P95 ≤ 200μs（建议）。
- 可靠性：空 Map/空字段稳健处理；上下文缺失时 sessionId/taskPath 为空。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 四类值与四种关系的交叉用例；
  - [ ] 字典序排序断言；
  - [ ] DELETE → valueRepr=null；CREATE/UPDATE → valueRepr=new 的 escape+truncate 口径。
- 集成测试：
  - [ ] 与 ChangeTracker/TFI 联动：track→修改→getChanges 回收增量。
- 性能测试：
  - [ ] 循环 100 次，报告 P95（记录机器/参数）。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：并集/判定/排序/上下文元数据/表示口径全部正确。
- [ ] 文档：卡片回填与 README 片段。
- [ ] 观测：异常不冒泡，仅 DEBUG。
- [ ] 性能：P95 建议达成或给出计划。
- [ ] 风险：DELETE 场景下游兼容确认。

## 12) RISKS & MITIGATIONS
- 性能：大字段数量下排序/遍历成本 → 仅标量/有限字段，按需 track 字段。
- 兼容性：导出器消费字段口径 → Console/JSON 已使用消息字符串，对 ChangeRecord 扩展保持兼容。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 与现有实现一致：DELETE 使用 valueRepr=null；若消费方需要 oldRepr 可在 2.1 演进方案引入 enhanced 字段，兼容添加不破坏。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] valueRepr 是否在 UPDATE 场景输出 oldRepr？当前一致使用 newRepr；若需要请在导出层扩展，不在 Diff 层混入展示逻辑。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 代码+测试+说明。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-202-diffdetector-scalar.md

