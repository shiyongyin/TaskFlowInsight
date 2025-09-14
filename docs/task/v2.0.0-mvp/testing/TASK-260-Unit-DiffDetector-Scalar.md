Title: TASK-260 — DiffDetector 标量对比单测

一、任务背景
- 确保标量对比逻辑在各种输入下稳定可靠。

二、目标
- 覆盖 null/类型变化/相等/不等；String/Number/Boolean/Date。

三、做法
- 基于 JUnit5：构造 before/after Map，调用 diff，断言 ChangeRecord 列表。

四、测试标准
- 全分支覆盖；日期比较基于时间戳；valueType/valueKind/valueRepr 正确。

五、验收标准
- 单测通过；命名清晰；无魔数。

