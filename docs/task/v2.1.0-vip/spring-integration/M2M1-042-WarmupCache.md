# spring-integration - M2M1-042-WarmupCache（VIP 合并版）

1. 目标与范围
- 业务目标：在启动期对 PathMatcherCache 进行可选预热，并设置有界容量与上限，降低首呼抖动与风险。
- Out of Scope：复杂预热策略。

2. A/B 对比与取舍
- A 组优点：明确预热失败降级 literal 与上限控制；
  - 参考：`../../v2.1.0-mvp/spring-integration/V210-042-Preload-and-Bounded-Caches.md`
- B 组优点：任务目标明确。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-042-WarmupCache.md`
- 问题与风险：启动时间延长；预热失败影响启动；内存占用。
- 冲突点清单：
  冲突#1：预热时机
    - 影响面：启动性能、首次请求延迟
    - 方案A：启动时同步预热
    - 方案B：启动后异步预热
    - 决策与理由：采用方案B，避免影响启动速度
    - 迁移与回滚：配置项控制预热启用
  
  冲突#2：预热失败处理
    - 影响面：系统稳定性、功能可用性
    - 方案A：失败则启动失败
    - 方案B：失败降级为 literal 匹配
    - 决策与理由：采用方案B，保证系统可用性
    - 迁移与回滚：记录失败指标，便于监控

3. 最终设计（融合后）
- `tfi.change-tracking.path-matcher.preload` 列表；max-size、pattern-max-length、max-wildcards 约束；
- 统计成功/失败数量；失败降级 literal 并计数。

4. 与代码差异与改造清单
- AutoConfig 初始化预热；
- 测试：预热列表、上限、降级覆盖。

5. 开放问题与后续项
- 预热列表的生成与维护方式。

---

