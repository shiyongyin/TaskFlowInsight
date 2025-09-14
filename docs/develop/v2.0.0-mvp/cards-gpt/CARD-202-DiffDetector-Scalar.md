Title: CARD-202 — DiffDetector（标量字段对比，M0）

一、开发目标
- ☐ 实现 `DiffDetector.diff(Map<String,Object> before, Map<String,Object> after)`，返回 `List<ChangeRecord>`。
- ☐ 对比规则：字段全集 = old.keys ∪ new.keys；CREATE/DELETE/UPDATE 判定（类型不同或 equals 不等为 UPDATE）。
- ☐ 值类型处理：支持 String/Number/Boolean/Date（Date 用时间戳比较）。
- ☐ valueRepr：先转义再截断（8192），空值 `null`；可选填 valueType/valueKind（标量时补充）。
- ☐ 性能目标：2 字段对比 P95 ≤ 200μs（建议目标，M0 门控以延迟为主）。
- ☐ 预留扩展点：策略接口占位（集合/Map、Mask/Serialize 在 M1 接入）。

二、开发清单
- ☐ 新增 `src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java`
- ☐ 定义变更类型枚举/常量：CREATE/UPDATE/DELETE。
- ☐ 对 Date 值统一转换为 long 进行比较与表现。
- ☐ 提供可注入的 valueRepr 生成策略（M0 默认内置）。
- ☐ 处理 null 边界与类型变化（包含装箱类型）。

三、测试要求
- ☐ null/类型变化/相等/不等路径覆盖。
- ☐ String/Number/Boolean/Date 的正确性校验（Date 比较基于时间戳）。
- ☐ 100 次循环、2 字段对比性能烟囱测试（P95 ≤ 200μs 作为建议目标）。
- ☐ 生成的 ChangeRecord 内容与消息格式契合（供 TASK-204/211 输出）。

四、关键指标
- ☐ 2 字段 P95 ≤ 200μs（建议目标）。
- ☐ 无额外对象膨胀；GC 次数不异常。

五、验收标准
- ☐ 单测全部通过；Diff 结果稳定一致；异常不冒泡。
- ☐ 与 `ObjectSnapshot`/`ChangeTracker` 对接无缝。

六、风险评估
- ☐ equals 行为异步（自定义类）导致不可预期成本——M0 避免复杂对象进入对比。
- ☐ 时间度量误差——使用 JMH 或稳定微基准；报告记录机器/参数。

七、核心技术设计（必读）
- ☐ 字段全集：`union(before.keySet, after.keySet)`，稳定遍历顺序（按字典序），确保输出可比。
- ☐ 变更判定：
  - ☐ old=null & new!=null → CREATE；old!=null & new=null → DELETE。
  - ☐ 类型不同 → UPDATE；类型相同但 !Objects.equals → UPDATE；其余不生成记录。
- ☐ Date 比较：基于 `getTime()`（long）；valueRepr 保留格式化习惯但内部比较用 long。
- ☐ 值表示：`valueRepr = Repr.repr(value, maxLen)`（先转义后截断）。
- ☐ 输出：每条 `ChangeRecord` 填充 changeType、valueType（FQCN）、valueKind（STRING/NUMBER/BOOLEAN/DATE 可选）。

八、核心代码说明（骨架/伪码）
```java
public final class DiffDetector {
  public static List<ChangeRecord> diff(String objName, Map<String,Object> before, Map<String,Object> after, Ctx ctx){
    Set<String> fields = new TreeSet<>();
    if (before != null) fields.addAll(before.keySet());
    if (after  != null) fields.addAll(after.keySet());
    List<ChangeRecord> out = new ArrayList<>();
    for (String f : fields) {
      Object o = before != null ? before.get(f) : null;
      Object n = after  != null ? after.get(f)  : null;
      String type;
      if (o == null && n != null) type = "CREATE";
      else if (o != null && n == null) type = "DELETE";
      else if (!sameType(o,n) || !Objects.equals(normalize(o), normalize(n))) type = "UPDATE";
      else continue;
      out.add(buildRecord(objName, f, o, n, type, ctx));
    }
    return out;
  }

  static Object normalize(Object v){ return (v instanceof Date d) ? d.getTime() : v; }
  static boolean sameType(Object a, Object b){ return (a==null||b==null) ? a==b : a.getClass()==b.getClass(); }
}
```
