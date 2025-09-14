# format-engine - M2M1-010-TemplateEngine（VIP 合并版）

1. 目标与范围
- 业务目标：提供轻量模板化的变更集文本输出（非 JSON），便于控制台/日志阅读。
- Out of Scope：复杂模板语法、插件系统。

2. A/B 对比与取舍
- A 组优点：最小模板对（default/compact），安全占位符与性能门槛。
  - 参考：`../../v2.1.0-mvp/format-engine/V210-010-Lightweight-Template-Engine.md`
- B 组优点：任务目标明确。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-010-TemplateEngine.md`
- 问题与风险：模板语法复杂度控制；占位符注入风险；大规模变更集渲染性能。
- 冲突点清单：
  冲突#1：模板引擎选择
    - 影响面：依赖引入、性能开销、安全性
    - 方案A：自实现最小占位符替换
    - 方案B：引入成熟模板引擎（如 Mustache）
    - 决策与理由：采用方案A，避免外部依赖，控制复杂度
    - 迁移与回滚：预留模板引擎接口，后续可扩展
  
  冲突#2：输出格式数量
    - 影响面：维护成本、用户选择复杂度
    - 方案A：仅提供 default/compact 两种
    - 方案B：支持用户自定义模板
    - 决策与理由：MVP 阶段采用方案A，降低复杂度
    - 迁移与回滚：后续版本可增加自定义能力

3. 最终设计（融合后）
3.1 接口
- `render(List<Change> changes, TemplateOption opt)`，占位：${path}/${kind}/${reprOld}/${reprNew}
- 模板示例（default）：
  ```
  === Change Summary ===
  Total changes: ${count}
  ${changes}
  - [${kind}] ${path}: ${reprOld} -> ${reprNew}
  ${/changes}
  ```
- 模板示例（compact）：`${path}: ${reprOld}→${reprNew}`
3.2 非功能
- 性能：中等规模（100条）< 20ms；大规模（1000条）< 200ms
- 安全：HTML/XML 转义、SQL 关键词检测、长度截断（单值 <1000 字符）
- 可关闭：不影响 JSON 导出

4. 与代码差异与改造清单
- 复用 Console 渲染或最小占位替换；新增 template 选项；
- 测试：渲染正确性与转义、性能简单度量。

5. 开放问题与后续项
- 是否按变化类型着色；是否输出分组统计。

---

