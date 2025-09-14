Title: CARD-201 — ChangeRecord 与 ObjectSnapshot（M0）

一、开发目标
- ☐ 建立 `ChangeRecord` 数据模型，字段齐全：objectName、fieldName、oldValue、newValue、timestamp、sessionId、taskPath、changeType、valueType、valueKind、valueRepr。
- ☐ 实现 `ObjectSnapshot.capture(String name, Object target, String... fields)`，产出 Map<String,Object> 作为快照源数据。
- ☐ 快照范围（M0）：仅纳入标量/字符串/日期字段；复杂对象/集合/Map 不进入快照。
- ☐ valueRepr 生成：先转义后截断，默认上限 8192，尾部追加 `... (truncated)`；空值输出 `null`。
- ☐ 日期类型深拷贝（不可变），避免外部修改污染快照。
- ☐ 反射元数据缓存：`ConcurrentHashMap<Class<?>, Map<String,Field>>`，配置容量上限，命中率随调用提升。
- ☐ 读取配置：通过 Spring `ChangeTrackingProperties`（`tfi.change-tracking.*`）注入；System.getProperty 仅作为极端 fallback。
- ☐ Javadoc 与异常边界：字段白名单、深度限制（M0 防御性不展开）、不可达字段忽略且 DEBUG 记录。

二、开发清单
- ☐ 新增 `src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java`
- ☐ 新增 `src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java`
- ☐ 在 `ObjectSnapshot` 中实现：
  - ☐ 字段白名单读取与缓存查找（setAccessible(true) 一次性设置）。
  - ☐ 仅采集标量/字符串/日期；复杂对象直接跳过（不调用 toString）。
  - ☐ 可配置的最大字段数/最大深度（保留为内部常量或从属性读取，默认 20/2）。
- ☐ 在 `ChangeRecord` 中实现：
  - ☐ 工厂/构造器对 valueRepr 执行“转义→截断”。
  - ☐ 可选填 valueType/valueKind（标量时补充，其他留空）。
- ☐ 使用 SLF4J 记录 DEBUG（字段不可达/异常时）。

三、测试要求
- ☐ 空值/类型变化/边界值（null、空字符串、极大/极小数）覆盖。
- ☐ 日期类型深拷贝校验（修改原对象不影响快照）。
- ☐ 长字符串按 8192 截断并追加 `... (truncated)`；包含引号/换行等特殊字符时先转义再截断。
- ☐ 复杂对象不进入快照，集合/Map 不采集（M0）。
- ☐ 反射缓存命中率在重复访问中提升；容量上限命中后不扩容（或告警）。

四、关键指标
- ☐ 反射缓存命中率在热路径 ≥ 80%（重复类访问场景）。
- ☐ 快照采集过程不触发重型 toString；无 OOM/内存泄漏迹象。
- ☐ 禁用状态（`tfi.change-tracking.enabled=false`）下，所有入口快速返回。

五、验收标准
- ☐ 单元测试全部通过；代码编译无警告；Javadoc 完整。
- ☐ 与 `TASK-202/203` 的接口契合（Map 快照可直接被 DiffDetector 消费）。
- ☐ 日志控制在 DEBUG/WARN，异常不冒泡业务层。

六、风险评估
- ☐ 反射性能风险：通过元数据缓存与白名单控制减轻；必要时增加容量上限与拒绝新增策略。
- ☐ 线程安全风险：缓存采用并发容器；避免在读路径上修改元数据。
- ☐ 行为歧义风险：明确 M0 不采集复杂对象/集合/Map，避免误报与重型成本。

七、核心技术设计（必读）
- ☐ 数据模型语义：
  - ☐ changeType：CREATE/UPDATE/DELETE；valueType（FQCN）与 valueKind（PRIMITIVE/STRING/NUMBER/BOOLEAN/DATE）在标量时填充。
  - ☐ valueRepr：对 old/new 各自生成（先转义后截断），用于稳定展示；不得用于逻辑比较。
- ☐ 快照规则：
  - ☐ 仅采集字段白名单中的 public/protected/private 成员（通过反射 setAccessible(true)）。
  - ☐ 最大深度=2 是防御性限制；M0 不展开复杂类型，直接跳过。
- ☐ 反射元数据缓存：
  - ☐ `ConcurrentHashMap<Class<?>, Map<String, Field>>`；computeIfAbsent 单点构建，字段按名称缓存。
  - ☐ 容量上限（如 1024 类），到达上限后拒绝新增并 WARN（避免无界增长）。
- ☐ 值归一化：
  - ☐ Date 深拷贝（`new Date(src.getTime())`）。
  - ☐ 标量判定：`String/Number/Boolean/Character/Enum/Date`； BigDecimal/BigInteger 视为 Number。
- ☐ 错误处理：字段访问异常时跳过并 DEBUG 日志，整体不抛出异常。

八、核心代码说明（骨架/伪码）
```java
// tracking/model/ChangeRecord.java
@Getter @Builder
public final class ChangeRecord {
  private final String objectName, fieldName;
  private final Object oldValue, newValue;
  private final long timestamp;
  private final String sessionId, taskPath;
  private final String changeType; // CREATE/UPDATE/DELETE
  private final String valueType;  // e.g. java.lang.String
  private final String valueKind;  // STRING/NUMBER/BOOLEAN/DATE
  private final String valueRepr;  // escaped + truncated
}

// tracking/snapshot/ObjectSnapshot.java
public final class ObjectSnapshot {
  private static final int MAX_CLASSES = 1024;
  private static final ConcurrentHashMap<Class<?>, Map<String, Field>> CACHE = new ConcurrentHashMap<>();

  public static Map<String,Object> capture(String name, Object target, String... fields) {
    if (target == null || fields == null || fields.length == 0) return Map.of();
    Class<?> type = target.getClass();
    Map<String,Field> meta = getOrBuildMeta(type, fields);
    Map<String,Object> out = new HashMap<>();
    for (String f : fields) {
      Field fld = meta.get(f);
      if (fld == null) continue;
      try {
        Object v = fld.get(target);
        if (isScalar(v)) out.put(f, normalize(v));
      } catch (Throwable e) { /* debug log */ }
    }
    return out;
  }

  private static Map<String,Field> getOrBuildMeta(Class<?> c, String[] fields){
    if (CACHE.size() >= MAX_CLASSES && !CACHE.containsKey(c)) {
      // warn & build local map without caching
      return buildMeta(c, fields);
    }
    return CACHE.computeIfAbsent(c, k -> buildMeta(k, fields));
  }

  private static Map<String,Field> buildMeta(Class<?> c, String[] fields){
    Map<String,Field> m = new HashMap<>();
    for (String f : fields) {
      try { Field fld = c.getDeclaredField(f); fld.setAccessible(true); m.put(f, fld);} catch (NoSuchFieldException ignored) {}
    }
    return m;
  }

  private static boolean isScalar(Object v){ /* String/Number/Boolean/Character/Enum/Date */ }
  private static Object normalize(Object v){ /* Date -> copy; others return as is */ }
}

// valueRepr 生成工具（示意）
public final class Repr {
  public static String repr(Object v, int max){
    if (v == null) return "null";
    String s = escape(String.valueOf(v));
    return s.length() <= max ? s : s.substring(0, Math.max(0, max - 16)) + "... (truncated)";
  }
}
```
