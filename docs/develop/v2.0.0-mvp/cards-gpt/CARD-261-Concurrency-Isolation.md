Title: CARD-261 — 并发隔离与归属正确性测试

一、开发目标
- ☐ 确保多线程并发修改不同对象时追踪数据隔离；通过 `TFIAwareExecutor` 传播上下文后归属正确。

二、开发清单
- ☐ 使用 CountDownLatch/Executor；创建多个子任务（每个子任务独立 start/stop）。
- ☐ 在子任务中调用 track/修改；stop 后主线程导出并断言归属与不重复。

三、测试要求
- ☐ 10~16 线程并发下无交叉污染、无随机失败；执行时间可接受。

四、关键指标
- ☐ 稳定性优先；无死锁/资源泄漏；上下文正确关闭。

五、验收标准
- ☐ 用例稳定重复通过；日志与导出片段可佐证。

六、风险评估
- ☐ 线程池复用导致泄漏：三处清理路径全覆盖；子线程 finally close。

七、核心技术设计（必读）
- ☐ 并发模式：固定线程池（8/16），CountDownLatch 控制同时起跑。
- ☐ 任务结构：每个子任务 `start("sub-i")` → track/修改 → stop。
- ☐ 断言：每个子节点 messages 仅包含该子任务的 CHANGE；无交叉污染。

八、核心代码说明（示例）
```java
ExecutorService pool = TFIAwareExecutor.newFixedThreadPool(8);
CountDownLatch start = new CountDownLatch(1);
for (int i=0;i<8;i++) pool.submit(() -> { start.await(); TFI.start("sub"); try{ /* track+mutate */ } finally{ TFI.stop(); }});
start.countDown(); pool.shutdown(); pool.awaitTermination(30, TimeUnit.SECONDS);
```
