## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-201-ChangeRecord-and-ObjectSnapshot.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext
  - src/main/java/com/syy/taskflowinsight/model/TaskNode.java#com.syy.taskflowinsight.model.TaskNode.addMessage(String, com.syy.taskflowinsight.enums.MessageType)
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#com.syy.taskflowinsight.enums.MessageType.CHANGE
- 相关配置/SQL/脚本：
  - src/main/resources/application.yml#无 tfi.change-tracking.*（本卡不修改，卡240实现配置）
- 工程操作规范：../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供变更追踪的基础能力：变更记录模型与对象快照采集（仅标量/字符串/日期）。
- 技术目标：新增 ChangeRecord 模型类与 ObjectSnapshot 捕获函数；实现 valueRepr（先转义后截断 8192，尾部"... (truncated)"）、反射元数据缓存（并发+容量上限）；不展开集合/Map/复杂对象。

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 新建 `src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java`（字段：objectName、fieldName、oldValue、newValue、timestamp、sessionId、taskPath、changeType、valueType、valueKind、valueRepr）。
  - [ ] 新建 `src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java`，方法 `static Map<String,Object> capture(String name, Object target, String... fields)`。
  - [ ] 标量识别：String/Number/Boolean/Character/Enum/Date；Date 深拷贝；复杂对象与集合/Map 不采集。
  - [ ] 反射元数据缓存：ConcurrentHashMap<Class<?>, Map<String,Field>> + 容量上限（建议 1024 类）；超限不缓存并 WARN（日志级别 DEBUG/WARN）。
  - [ ] valueRepr：对 old/new 生成展示字符串，先转义后截断（8192）。
- Out of Scope（排除项）：
  - [ ] 集合/Map 摘要与递归展开（留至 M1）。
  - [ ] 可插拔序列化/脱敏 SPI（留至 M1）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：
   - 新建：tracking/model/ChangeRecord.java、tracking/snapshot/ObjectSnapshot.java。
   - 仅读取：context/ManagedThreadContext、model/TaskNode、enums/MessageType（用于后续集成对齐）。
2. 新建类与方法签名：
```java
// src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java
package com.syy.taskflowinsight.tracking.model;
import lombok.Builder; import lombok.Getter;
@Getter @Builder public final class ChangeRecord {
  private final String objectName, fieldName;
  private final Object oldValue, newValue;
  private final long timestamp;
  private final String sessionId, taskPath;
  private final String changeType; // CREATE/UPDATE/DELETE
  private final String valueType;  // FQCN，如 java.lang.String
  private final String valueKind;  // STRING/NUMBER/BOOLEAN/DATE
  private final String valueRepr;  // 统一字符串表现（转义+截断）
}

// src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java
package com.syy.taskflowinsight.tracking.snapshot;
import java.lang.reflect.Field; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
public final class ObjectSnapshot {
  private static final int MAX_CLASSES = 1024;
  private static final ConcurrentHashMap<Class<?>, Map<String, Field>> CACHE = new ConcurrentHashMap<>();
  public static Map<String,Object> capture(String name, Object target, String... fields) { /* 实现见补丁 */ }
}
```
3. 逐步实现：
   - 实现 capture：仅白名单字段；setAccessible(true)；仅标量入 Map；Date 深拷贝；异常 DEBUG 跳过。
   - 实现缓存：computeIfAbsent 构建；超限不缓存。
   - 实现字符串转义+截断（可在本类私有方法或新增内部 util）。
4. 给出完整补丁（unified diff）或小文件全量内容：必须包含上述两个新文件与其实现。
5. 更新文档：无需 OpenAPI；在卡片勾选开发清单；在 CHANGELOG 或 README（若有）简述新增模块。
6. 提交前自测：`./mvnw -q -DskipTests=false test`（配套新增单测）；构造对象与长字符串验证截断行为。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：ChangeRecord.java、ObjectSnapshot.java（完整内容）。
  - 如有：字符串转义/截断小工具类（可内联）。
- 测试：
  - 单测类：ObjectSnapshotTests（空值/边界/日期/截断/缓存上限）。
- 文档：
  - 在卡片 `CARD-201` 勾选完成项；必要时在 cards-final/AI-DEVELOPMENT-GUIDE.md 的相关小节标注完成。
- 回滚/灰度：
  - 无运行时开关改动；回滚即删除新增类与测试。
- 观测：
  - 日志键：`[ObjectSnapshot]` DEBUG/WARN；不新增指标。

## 7) API & MODELS（必须具体化）
- 模型：ChangeRecord（字段如上）。
- 错误处理：捕获反射异常（NoSuchFieldException/IllegalAccessException/RuntimeException），记录 DEBUG 并跳过，不抛出。
- DTO：无。
- 鉴权：无（进程内工具）。

## 8) DATA & STORAGE
- 无数据库；内存结构：ConcurrentHashMap<Class<?>, Map<String,Field>>。
- 幂等：capture 无副作用；缓存 computeIfAbsent 保证并发下单次构建。
- 脱敏：本卡不做通用脱敏；仅“先转义后截断”。

## 9) PERFORMANCE & RELIABILITY
- 性能预算：2 字段采集平均/中位应在亚毫秒级（配合 202/250 验证）。
- 可靠性：异常不冒泡；缓存超限拒绝新增避免无界内存。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] 空值/类型变化/极端长字符串/包含引号换行的字符串/Date 深拷贝
  - [ ] 缓存命中率提升与上限行为（填满上限后不缓存/不抛错）
- 集成测试：
  - [ ] 无（由 204/230 验证端到端可见性）
- 性能测试：
  - [ ] 见 250（本卡不单独提交 JMH）

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：仅标量/字符串/日期入快照；复杂/集合/Map 不入
- [ ] 文档：卡片勾选；代码 Javadoc 完整
- [ ] 观测：异常仅 DEBUG/WARN，无错误冒泡
- [ ] 性能：缓存命中有效；无明显膨胀
- [ ] 风险：缓存上限与拒绝新增生效

## 12) RISKS & MITIGATIONS
- 性能：反射访问开销 → 元数据缓存 + 白名单 + 不展开复杂对象。
- 兼容性：后续集合/Map 支持 → 预留扩展，不在 M0 实现。
- 安全与合规：字符串化前后仅做转义截断，不处理敏感字段 → M1 引入脱敏策略。
- 外部依赖：无。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无直接冲突；配置键 `tfi.change-tracking.*` 尚未存在于 application.yml（卡240落地）。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] valueType/valueKind 的填充范围：M0 是否仅在标量时填？（建议：标量填，其他留空）
- [ ] 缓存上限默认值是否 1024？（若需调整请产品/架构确认）
- 责任人/截止日期/所需产物：架构负责人确认；在卡片评论区记录决策。

