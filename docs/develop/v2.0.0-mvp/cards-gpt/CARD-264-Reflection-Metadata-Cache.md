Title: CARD-264 — 反射元数据缓存验证

一、开发目标
- ☐ 验证缓存命中有效、在重复类访问中减少反射耗时；具备容量上限保障。

二、开发清单
- ☐ 构造循环访问相同对象字段的场景，测量第一次与后续访问耗时差；统计缓存命中计数。
- ☐ 将缓存填充至上限，验证后续行为（不再扩容或合理淘汰）；并发下 `computeIfAbsent` 正确性。

三、测试要求
- ☐ 后续访问明显低于首次；并发场景无异常；缓存大小受控。

四、关键指标
- ☐ 命中率在热路径 ≥ 80%；容量上限有效；不引发内存抖动。

五、验收标准
- ☐ 用例通过；性能用例中有可见改善；不引入第三方依赖（M0）。

六、风险评估
- ☐ 无界缓存风险：默认上限与拒绝新增策略，必要时 WARN 日志提示。

七、核心技术设计（必读）
- ☐ 缓存策略：`ConcurrentHashMap<Class<?>, Map<String,Field>>` + 容量上限；构建时 setAccessible(true)。
- ☐ 计算模型：`computeIfAbsent` 单点构建；到达上限后不缓存（返回临时 meta）。
- ☐ 指标：命中计数/构建计数（可选简单计数器用于测试）。

八、核心代码说明（示例）
```java
class MetaCache {
  static final int MAX = 1024; static final ConcurrentHashMap<Class<?>,Map<String,Field>> C = new ConcurrentHashMap<>();
  static Map<String,Field> meta(Class<?> c, String[] fields){
    if (C.size()>=MAX && !C.containsKey(c)) return build(c, fields);
    return C.computeIfAbsent(c, k -> build(k, fields));
  }
}
```
