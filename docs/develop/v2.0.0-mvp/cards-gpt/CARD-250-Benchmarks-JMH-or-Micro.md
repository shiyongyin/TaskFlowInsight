Title: CARD-250 — 基准测试（JMH/微基准）

一、开发目标
- ☐ 提供 JMH 基准（优先）或 JUnit 微基准；覆盖 2/20 字段（循环 100 次）、8/16 线程并发；输出延迟分位与简要 CPU 观测。
- ☐ 以延迟门槛为主（建议：2 字段 P95 ≤ 200μs）；CPU 占比为报告项，不作为 M0 硬门槛；报告记录机器/参数。

二、开发清单
- ☐ 基准类：`ChangeTrackingBenchmark`（或同义），覆盖流程：track → 修改 → getChanges → clearAllTracking。
- ☐ 固定热身/迭代参数；提供 Maven Profile/命令；输出样例入文档。

三、测试要求
- ☐ 本地与 CI 环境运行稳定；结果可复现且接近阈值。
- ☐ 报告包含关键参数：CPU/内存/OS/JDK/压力模型。

四、关键指标
- ☐ P95 延迟满足建议目标；无异常波动；GC 频率可接受。

五、验收标准
- ☐ 提交基准报告；未达标时附改进计划与 Issue。

六、风险评估
- ☐ 基准环境差异导致数据波动：固定参数并给出误差范围，记录环境信息。

七、核心技术设计（必读）
- ☐ JMH 设计：
  - ☐ @State(Scope.Thread) 保存共享对象；@BenchmarkMode(AverageTime) / @OutputTimeUnit(MICROSECONDS)。
  - ☐ 基准步骤：track → mutate → getChanges → clearAllTracking。
  - ☐ 线程并发：@Threads(8/16) 两组；Warmup/Measurement 轮次固定。
- ☐ 观测项：P50/P95 延迟统计；CPU 占比为辅助项（可通过外部工具观察）。

八、核心代码说明（骨架/伪码）
```java
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ChangeTrackingBenchmark {
  @Benchmark public void twoFields(){
    TFI.track("order", order, "status","amount");
    order.setStatus("PAID"); order.setAmount(order.getAmount().add(BigDecimal.ONE));
    TFI.getChanges();
    TFI.clearAllTracking();
  }
}
```
