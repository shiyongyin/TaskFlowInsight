Title: TASK-202 — DiffDetector（标量字段对比）

一、任务背景
- M0 仅需支持标量/字符串/日期的字段级差异检测，保证正确性与性能，为集合/Map 摘要对比（M1）留出扩展点。

二、目标
- 实现 `DiffDetector.diff(before, after) : List<ChangeRecord>`（仅标量）。
- 处理 null、类型变化、值相等/不等；填充 ChangeRecord 的类型信息。

 三、做法
- 输入：ObjectSnapshot 生成的 Map<String,Object>（old/new）。
- 对比规则：
  - 字段全集 = old.keys ∪ new.keys。
  - old=null,new!=null → CREATE；old!=null,new=null → DELETE；类型不同 → UPDATE；equals 不等 → UPDATE。
  - valueRepr 生成采用“先转义后截断”，默认 8192 上限；valueType/valueKind 在 M0 可选填（标量补充，其他留空）。
- 扩展性：保留 Strategy 接口，用于 M1 集合/Map 与 Serialize/Mask 等策略注入。

四、测试标准
- 单测：
  - null/类型变化/相等/不等的路径覆盖。
  - String/Number/Boolean/Date 的正确性，Date 比较用 long 值。
  - 性能冒烟：100 次循环，2 字段对比平均耗时 < 200μs。

 五、验收标准
 - 测试全部通过；ChangeRecord 内容与消息格式契合 PRD；无异常/资源泄漏；复杂对象不参与 diff。
