Title: CARD-263 — 变更消息格式测试

一、开发目标
- ☐ 校验标准格式：`<Object>.<field>: <old> → <new>`；包含 CHANGE 标签。
- ☐ 截断规范：先转义后截断；默认 8192；超出以 `... (truncated)` 结尾；空值 `null`。

二、开发清单
- ☐ 运行典型流程，抓取当前 TaskNode 的 messages；断言 type=CHANGE 且 content 符合正则。
- ☐ 正则示例：`^[A-Za-z0-9_$.]+\.[A-Za-z0-9_$.]+: .+ \u2192 .+$`；覆盖包含换行/引号等特殊字符场景。

三、测试要求
- ☐ 正则匹配通过；转义后再截断；空值显示 `null`。

四、关键指标
- ☐ 不同导出器（Console/JSON）中展示一致。

五、验收标准
- ☐ 用例通过；与 Console/JSON 展示一致。

六、风险评估
- ☐ 文本格式兼容性：统一在 `TFI.stop()/withTracked` 的格式化函数中实现，单点维护。

七、核心技术设计（必读）
- ☐ 格式化单点：`ChangeMessageFormatter.format(ChangeRecord, maxLen)`；实现“先转义后截断”。
- ☐ 正则与示例：`^([\w.$]+)\.([\w.$]+): (.+) \u2192 (.+)$`；包含 UTF-8 箭头字符。

八、核心代码说明（骨架/伪码）
```java
public final class ChangeMessageFormatter {
  public static String format(ChangeRecord cr, int max){
    String oldR = Repr.repr(cr.getOldValue(), max);
    String newR = Repr.repr(cr.getNewValue(), max);
    return cr.getObjectName()+"."+cr.getFieldName()+": "+oldR+" \u2192 "+newR;
  }
}
```
