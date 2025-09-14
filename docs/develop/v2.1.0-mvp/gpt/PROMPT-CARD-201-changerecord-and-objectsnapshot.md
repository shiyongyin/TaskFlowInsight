## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-201-ChangeRecord-and-ObjectSnapshot.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java#com.syy.taskflowinsight.tracking.model.ChangeRecord
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.capture(String,Object,String...)
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.repr(Object,int)
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.formatValue(Object)
  - src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java#com.syy.taskflowinsight.tracking.detector.DiffDetector.diff(String,Map,Map)
- 相关配置/SQL/脚本：
  - src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java#@ConfigurationProperties(prefix="tfi.change-tracking")
  - src/main/resources/application.yml: tfi.change-tracking.enabled, tfi.change-tracking.value-repr-max-length, tfi.change-tracking.cleanup-interval-minutes, tfi.change-tracking.max-cached-classes（如未存在请新增）
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供稳定的字段级快照与变更记录基础模型，支撑后续 Diff、追踪与导出；仅支持标量/字符串/日期，不展开复杂对象/集合/Map。
- 技术目标：
  - 数据模型：ChangeRecord 包含 objectName/fieldName/oldValue/newValue/timestamp/sessionId/taskPath/changeType/valueType/valueKind/valueRepr。
  - 快照：ObjectSnapshot.capture 仅采集标量，日期深拷贝，反射元数据缓存有上限（默认 1024）。
  - 表示：valueRepr 先转义后截断（默认 8192，后缀"... (truncated)"）。

## 4) SCOPE
- In Scope（当次实现必做）：
  - [x] 实现/校准 ChangeRecord 字段与 Builder 行为（默认 timestamp=System.currentTimeMillis()）。
  - [x] 完善 ObjectSnapshot.capture 的标量采集、深拷贝(Date)、字段白名单与缓存上限。
  - [x] 提供统一 repr(Object,int) 与 escape+truncate 逻辑；TFI.formatValue 委托该逻辑。
  - [x] 反射缓存 ConcurrentHashMap 上限控制与超限 WARN（不缓存，仅临时构建）。
- Out of Scope（排除项）：
  - [ ] 持久化/查询能力（M0 不做）。
  - [ ] 集合/Map 的展开与深度遍历（M0 不做）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - ChangeRecord 模型：`src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java`
   - 快照工具：`src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java`
   - 门面委托：`src/main/java/com/syy/taskflowinsight/api/TFI.java#formatChangeMessage/formatValue`
   - 配置：`src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java` 与 `src/main/resources/application.yml`
2. 给出重构/新建的类与方法签名：
```java
// ChangeRecord 字段核对（已存在，确保字段齐备且Builder默认timestamp可用）
@Getter @Builder(toBuilder = true) @AllArgsConstructor
public final class ChangeRecord { /* 字段见当前实现 */ }

// ObjectSnapshot（补强标量规则/缓存上限/escape+truncate）
public final class ObjectSnapshot {
  public static Map<String,Object> capture(String name, Object target, String... fields);
  public static String repr(Object value);              // 委托 repr(value, maxLen)
  public static String repr(Object value, int maxLen);  // 先转义再截断
  public static void setMaxValueLength(int length);
}
```
3. 逐步实现：接口契约 → 领域模型/DTO → 服务/仓储 → 配置 → 观测埋点。
   - 按卡片“仅标量/字符串/日期入快照；Date 深拷贝；escape→truncate(8192)”落实；
   - 反射缓存上限：达到 1024 类后，不缓存新类，直接构建并 WARN。
4. 给出完整补丁或文件全量内容：实现完成后输出 unified diff（包含变更点）。
5. 更新 README/设计摘录：在 docs/task/v2.0.0-mvp/change-tracking-core 或项目 README 补充快照与表示口径摘要。
6. 提交前自测：`./mvnw -q -DskipTests=false test`；手动运行 Demo 校验 Console/JSON 中 CHANGE 展示是否稳定（引号/换行）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新增/变更：`ObjectSnapshot` 的缓存上限/repr/escape+truncate 完整实现；必要的 Javadoc。
  - ChangeRecord 字段校验（若缺字段则补齐）；保持对外兼容。
- 测试：
  - 单测：空值/类型变化/长字符串/包含引号与换行/日期深拷贝/反射缓存上限与命中率。
- 文档：
  - README 片段更新：valueRepr 口径与默认阈值；调优建议。
- 回滚/灰度：
  - 通过 `ChangeTrackingProperties.valueReprMaxLength` 动态调节；必要时关闭模块（全局/属性双开关）。
- 观测：
  - 日志键：ObjectSnapshot 捕获失败 DEBUG、缓存超限 WARN。

## 7) API & MODELS（必须具体化）
- 接口签名：
```java
public static Map<String,Object> ObjectSnapshot.capture(String name, Object target, String... fields)
public static String ObjectSnapshot.repr(Object value)
public static String ObjectSnapshot.repr(Object value, int maxLength)
```
- 错误码/异常映射：
  - 反射访问失败 → 仅 DEBUG 日志，不冒泡；字段缺失跳过。
- DTO/实体/聚合根：
  - ChangeRecord 字段：objectName, fieldName, oldValue, newValue, timestamp, sessionId, taskPath, changeType, valueType, valueKind, valueRepr。
- 鉴权/审计：N/A。

## 8) DATA & STORAGE
- 表/索引：无（M0 不落盘）。
- 幂等策略：快照采集幂等；repr 无副作用。
- 脱敏：M0 不做通用脱敏；保留后续 Mask 策略扩展点。

## 9) PERFORMANCE & RELIABILITY
- 性能预算：2 字段 P95 ≤ 200μs（建议，报告机器/参数）。
- 失败与回退：缓存超限时不缓存新类（临时构建+WARN）。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [x] ObjectSnapshot.capture：标量采集/复杂对象不采集/日期深拷贝。
  - [x] repr：先转义后截断；长度=8192 截断为 "... (truncated)"。
  - [x] 缓存：首次构建命中率上升；超过 1024 类时不缓存且 WARN。
- 集成测试：
  - [x] 与 DiffDetector/TFI 流转：repr 在 CHANGE 消息中稳定展示。
- 性能测试：
  - [ ] 简单微基准：2 字段、100 次循环，输出 P95。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：仅标量采集，日期深拷贝，复杂对象/集合不采集。
- [ ] 文档：README/设计摘录已更新。
- [ ] 观测：缓存上限 WARN & 捕获失败 DEBUG。
- [ ] 性能：P95 建议目标达成或给出计划。
- [ ] 风险：无异常冒泡；默认禁用时快速返回。

## 12) RISKS & MITIGATIONS
- 性能：反射高频开销 → 元数据缓存 + 上限；必要时白名单字段。
- 兼容性：valueRepr 截断可能影响断言 → 在测试中对齐 escape+truncate 逻辑。
- 安全与合规：不输出敏感字段（M0 仅按字段名白名单选择）。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- docs 要求的字段集 vs 现有 ChangeRecord 字段集对齐良好；如需新增字段，请在 ChangeRecord 保持 Builder 兼容并在导出器保持兼容字段不回退。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 反射缓存上限是否需要暴露为 `tfi.change-tracking.max-cached-classes`？当前有 `ChangeTrackingProperties#maxCachedClasses` 可对齐。
- [ ] 截断长度是否允许运行时动态调整？对齐 `ChangeTrackingProperties#valueReprMaxLength` 注入 `ObjectSnapshot.setMaxValueLength`。
- 责任人/截止日期/所需产物：待指派 / 本卡交付周期内 / 代码+测试+README 片段。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-201-changerecord-and-objectsnapshot.md
