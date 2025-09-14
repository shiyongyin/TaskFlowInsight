# 质量门禁（QA Gates）

面向 CI 的硬门禁与建议阈值。未达标直接阻断合并/发布。

## 硬门禁（必须达标）
- [ ] 代码覆盖率 ≥ 80%（行/分支综合，单元+部分 IT）
- [x] 并发 10–16 线程 IT：无交叉污染；三处清理幂等；无泄漏
- [ ] 24h 稳定性冒烟：无 OOM；`tfi.degraded.state` 始终为 0（未触发自动禁用）
- [ ] 性能 P50 达标：快照 < 10 μs；diff < 50 μs；`TFI.stop` 清理 < 1 ms
- [x] 结构化导出一致（Console/JSON/Map 数量一致；结构字段齐全）

## 软门禁（建议 & 报警）
- [ ] P95 不异常放大（< 5× P50）；若超出需给出说明与后续计划
- [ ] 指标齐备：计数/时延/Gauge；标签维度 ≤ 2（避免高基数）
- [ ] 关键路径日志采样生效（避免噪声与放大）

## CI 集成建议
- 覆盖率（JaCoCo）
  - jacoco-maven-plugin 最小配置示例：
    ```xml
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.12</version>
      <executions>
        <execution>
          <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
          <id>report</id>
          <phase>verify</phase>
          <goals><goal>report</goal></goals>
        </execution>
      </executions>
    </plugin>
    ```
- 门禁脚本
  - 将 P50/P95 统计输出为测试日志或 `target/it-metrics.json`，在 CI 中解析并断言阈值
  - 失败时附加导出样例（JSON）与热点提示

> 参考：《Integration Test Plan》《TECH-SPEC》
