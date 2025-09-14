# testing-quality - M2M1-061-PerformanceBaseline（VIP 合并版）

1. 目标与范围
- 业务目标：建立可复现的性能基线与不回退验证机制，护栏为“<5% CPU + 不劣化既有基线”。

2. A/B 对比与取舍
- A 组优点：提供环境/参数/元数据采集规范与 profiling 指南（V210-062）。
  - 参考：`../../v2.1.0-mvp/testing-quality/V210-061-Performance-Baseline-NonRegression.md`、`../../v2.1.0-mvp/testing-quality/V210-062-Perf-Env-and-Profiling-Guide.md`
- B 组优点：任务目标明确。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-061-PerformanceBaseline.md`
- 问题与风险：基线数据不稳定；环境差异影响；性能回退发现滞后。
- 冲突点清单：
  冲突#1：性能指标阈值
    - 影响面：产品可用性、用户体验
    - 方案A：严格 5% CPU 上限
    - 方案B：灵活调整阈值
    - 决策与理由：采用方案A，确保不影响业务
    - 迁移与回滚：通过开关控制启用
  
  冲突#2：基线数据采集方式
    - 影响面：数据准确性、可复现性
    - 方案A：手动采集和记录
    - 方案B：自动化采集和存储
    - 决策与理由：采用方案B，提高效率和准确性
    - 迁移与回滚：保留手动校验选项

3. 最终设计（融合后）
- 标准化 baseline profile；记录 Git SHA/配置/硬件/OS；预热≥5s、测量≥30s、重复≥5 次；
- 采用 JFR/async-profiler 定位异常；报告 JSON/Markdown 归档。

4. 与代码差异与改造清单
- 新增 perf job 或 profile；不改运行逻辑。

5. 开放问题与后续项
- 是否对关键路径构建门禁（按项目节奏决定）。

---

