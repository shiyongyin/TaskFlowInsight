## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-202-DiffDetector-Scalar.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#com.syy.taskflowinsight.enums.MessageType
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext
- 相关配置/SQL/脚本：无
- 工程操作规范：../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：对比对象快照（Map）生成字段级变更列表（仅标量/字符串/日期）。
- 技术目标：新增 `DiffDetector.diff(String objectName, Map<String,Object> before, Map<String,Object> after, Ctx ctx)`；CREATE/DELETE/UPDATE 判定；Date 用 long 比较；valueRepr 先转义后截断（8192）。

## 4) SCOPE
- In Scope：
  - [ ] 新建 `src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java`。
  - [ ] 字段全集 = keys(before) ∪ keys(after)，字典序输出以便可测试。
  - [ ] 变更类型判定与 ChangeRecord 构建（valueType/valueKind 在标量时可选填）。
- Out of Scope：
  - [ ] 集合/Map 对比策略与 SPI（M1）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：新增 tracking/detector/DiffDetector.java；读取 tracking/model/ChangeRecord.java（201 将新增）。
2. 新建类与方法签名：
```java
package com.syy.taskflowinsight.tracking.detector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import java.util.*;
public final class DiffDetector {
  public static List<ChangeRecord> diff(String objectName, Map<String,Object> before, Map<String,Object> after) { /* 实现见补丁 */ }
}
```
3. 实现：并集字段遍历；类型不同或 !Objects.equals(normalize(o), normalize(n)) → UPDATE；o=null,n!=null → CREATE；o!=null,n=null → DELETE。
4. 补丁：提供完整文件内容。
5. 文档：勾选卡片；在 201/203/204 说明本方法如何被使用。
6. 自测：编写 DiffDetector 单测（卡260）并在本卡提交基础用例以保障回归。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：DiffDetector.java（完整内容）。
- 测试：基础 DiffDetectorTests（最小用例集，详尽矩阵在卡260）。
- 文档：卡片勾选。
- 回滚/灰度：无。
- 观测：无新增指标；必要时 DEBUG 打印对象名与字段名。

## 7) API & MODELS（必须具体化）
- 接口签名：见上；返回 `List<ChangeRecord>`。
- 异常映射：输入为 null 时安全处理（返回空列表）。
- DTO：ChangeRecord（201 定义）。

## 8) DATA & STORAGE
- 无数据库；内存计算。

## 9) PERFORMANCE & RELIABILITY
- 性能预算：2 字段 P95 ≤ 200μs（建议目标，最终由卡250报告）。
- 可靠性：空 Map/空字段安全处理；字典序稳定输出。

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 80%
  - [ ] null/类型变化/相等/不等；String/Number/Boolean/Date；字典序断言
- 集成测试：
  - [ ] 与 ObjectSnapshot 的组合（最小烟囱）
- 性能测试：
  - [ ] 见 250

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：三类变更类型判定正确
- [ ] 文档：卡片勾选
- [ ] 观测：无异常日志
- [ ] 性能：建议目标满足或报告说明
- [ ] 风险：空输入与类型差异处理妥当

## 12) RISKS & MITIGATIONS
- equals 代价与异常 → 标量限定 + try/catch 防护 + normalize(Date→long)。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] valueType/valueKind 的最小填充范围确认（建议仅标量填）。
- 责任人/截止日期/所需产物：架构确认；卡片备注。

