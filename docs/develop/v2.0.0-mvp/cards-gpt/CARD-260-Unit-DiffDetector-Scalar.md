Title: CARD-260 — DiffDetector 标量对比单测

一、开发目标
- ☐ 覆盖 null/类型变化/相等/不等；String/Number/Boolean/Date 的对比正确性。

二、开发清单
- ☐ 编写 JUnit5 单元测试：构造 before/after Map，调用 `DiffDetector.diff`，断言 `ChangeRecord` 列表。
- ☐ 日期比较基于时间戳；valueRepr 先转义后截断；复杂对象不参与。

三、测试要求
- ☐ 全分支覆盖；异常路径校验（输入 null/空 Map 等）。

四、关键指标
- ☐ 用例执行稳定、断言明确；不可依赖系统时间细节导致偶发失败。

五、验收标准
- ☐ 单测通过；命名清晰；无魔数。

六、风险评估
- ☐ 边界值遗漏：基于等价类+边界分析补全用例集。

七、核心技术设计（必读）
- ☐ 用例矩阵：{null→val, val→null, val→same, val→diff, typeChange} × {STRING,NUMBER,BOOLEAN,DATE}。
- ☐ 断言项：changeType、valueRepr、valueType/valueKind（可选）、记录数量与顺序。

八、核心代码说明（示例）
```java
@Test void update_string(){
  Map<String,Object> b = Map.of("status","PENDING");
  Map<String,Object> a = Map.of("status","PAID");
  List<ChangeRecord> list = DiffDetector.diff("order", b, a, Ctx.of(...));
  assertEquals(1, list.size());
  assertEquals("UPDATE", list.get(0).getChangeType());
}
```
