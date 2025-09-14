Title: TASK-201 — ChangeRecord 与 ObjectSnapshot 实现

一、任务背景
- 变更追踪的基础是“捕获对象的前后快照并生成结构化变更记录”。M0 阶段要求最小可行：支持标量字段（含 String/Date），并为 M1/M2 的检索与回放预留类型信息。

二、目标
- 新增 ChangeRecord 数据模型与 ObjectSnapshot 快照采集器。
- 支持字段白名单与最大深度约束；M0 仅纳入标量/字符串/日期字段进入快照 Map，复杂对象不进入快照（避免高代价 toString 与误报）。
- 预留类型信息字段（valueType/valueKind/valueRepr）。

三、做法（实现方案）
- 参考：docs/specs/m2/final/TaskFlow-Insight-M2-Design.md 第 4 节。
- 新增包：`src/main/java/com/syy/taskflowinsight/tracking`：
  - `model/ChangeRecord`：
    - 字段：objectName, fieldName, oldValue, newValue, timestamp, sessionId, taskPath, changeType, valueType, valueKind, valueRepr。
    - valueRepr 生成规范：先转义后截断；默认上限 8192 字符，超出以 `... (truncated)` 结尾；M0 可只稳定填充 valueRepr，其余类型字段按需/可空。
    - M0 对复杂类型不做递归与摘要写入快照；集合/Map 摘要留待 M1。
  - `ObjectSnapshot`：
    - `capture(String name, Object target, String... fields) : Map<String,Object>`
    - 规则：仅读取白名单字段；最大深度=2（M0 防御性，不展开复杂对象）；字段不可达或异常时忽略并记录 DEBUG。
    - 元数据缓存：`ConcurrentHashMap<Class<?>, Map<String,Field>>`，setAccessible(true)。

四、测试标准
- 单元测试：
  - 空值/类型变化/边界值（null、空字符串、极大/极小数）
  - 时间类型（Date）拷贝为不可变值（副本）
  - 长字符串按 8192 截断并追加 `... (truncated)`；复杂对象不进入快照
- 代码扫描：无反射访问泄漏（字段 setAccessible）；缓存命中率在重复调用中上升。

五、验收标准
- 通过上述测试；无未处理异常；生成的 ChangeRecord 满足字段与截断/摘要策略。
- Javadoc 注释齐备；与 Design 文档一致；无 Checkstyle/编译警告。
