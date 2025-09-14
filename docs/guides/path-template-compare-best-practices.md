# 路径/模板/比较规则 最佳实践

## 路径（fieldPath）
- 点分路径（不含根对象名）：`address.city`；最终输出由 `#[object].#[field]` 组合。
- 集合/Map 不展开为索引路径（仅摘要）；
- 匹配规则（配置用）：优先采用 Ant 风格（`*`、`**`），可选 regex；大小写敏感；
- 优先级：byPath > byBeanName > byType > default；白名单优先于黑名单。

## 模板（占位符统一 #[...])
- 支持占位：`#[object]、#[field]、#[old]、#[new]、#[type]、#[time]、#[valueKind]`；
- 时间：默认 ISO‑8601（UTC，可配时区）；
- 禁止表达式求值（不支持 SpEL），仅占位替换；
- 示例：`#[object].#[field]: #[old] → #[new]`。

## 比较规则（Comparator/Normalization）
- 三项优先：
  - 数字容差（`numberTolerance`）：用于金额/浮点容差；
  - 时间对齐（`timeRounding=millis|seconds`）：用于时间戳对齐；
  - 字符串归一化（`stringNormalize=true|false`）：trim/大小写/空白标准化。
- 解析优先级：byPath > byBeanName > byType > default；
- 建议仅在必要字段上开启（按路径精确匹配），减少误报与开销。

## 集合示例项（可选开关）
- 默认仅摘要（size 与 +N/-N）。
- 如需示例项：开启 `collection.examples.enabled=true`；
  - `topN` 默认为 3；按 valueRepr（大小写不敏感）升序输出；
  - 截断：`item-max-length=128`，`line-max-length=512`，超限以 `…` 结尾；
  - Map：仅展示 key 的新增/删除；同 key 值变化仅计数；
  - 复杂对象：使用字符串化（转义+截断），不逐项深度对比。

## 缓存与匹配
- Pattern 编译结果 LRU 上限默认 256，可配；
- path→resolver（已解析策略/模板）缓存上限默认 1024，可配；
- 不建议随意调大，避免内存占用与命中率下降。

