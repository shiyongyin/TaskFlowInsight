# TaskFlowInsight 整体项目 — 测试方案

> **作者**: 资深测试专家  
> **日期**: 2026-02-16  
> **版本**: v1.0  
> **范围**: 全模块（tfi-all, tfi-compare, tfi-ops-spring, tfi-flow-spring-starter）

---

## 1. 测试策略

### 测试金字塔

```
              ╱╲        E2E
             ╱  ╲       集成测试 (@SpringBootTest)
            ╱────╲      单元测试 (策略/路径/快照)
           ╱──────╲     属性测试 + 架构测试
```

| 层级 | 比例目标 | 当前 |
|------|:--------:|:----:|
| 单元测试 | 60% | 55% |
| 集成测试 | 25% | 30% |
| 属性/架构 | 5% | 3% |
| 性能测试 | 5% | 10% |
| E2E | 5% | 2% |

---

## 2. 各模块覆盖率

| 模块 | 测试文件数 | JaCoCo | 预估覆盖率 | 风险 |
|------|:----------:|:------:|:----------:|:----:|
| tfi-all | ~395 | ✅ | ~60% | 低 |
| tfi-compare | ~80 | ❌ | ~55% | 中 |
| tfi-ops-spring | 0 | ❌ | 0% | **高** |
| tfi-flow-spring-starter | 0 | ❌ | 0% | **高** |

---

## 3. 测试类型分布

| 类型 | 数量 | 工具 |
|------|:----:|------|
| 单元测试 | ~300 | JUnit 5 + AssertJ |
| 集成测试 | ~40 | @SpringBootTest + Mockito |
| 属性测试 | 1 | jqwik |
| 架构测试 | 3+ | ArchUnit |
| 金标准测试 | 6+ | ApprovalTests |
| 性能测试 | 5+ | JMH / JUnit gate |

---

## 4. 关键缺口与改进

| 缺口 | 优先级 | 建议 |
|------|:------:|------|
| tfi-ops-spring 零测试 | P0 | 补齐 Actuator 端点测试 |
| tfi-flow-spring-starter 零测试 | P0 | 补齐 AOP 切面测试 |
| JaCoCo 仅 tfi-all 启用 | P1 | 扩展至 tfi-compare |
| 金标准测试部分 @Disabled | P1 | 更新 golden 文件 |
| 并发测试不足 | P1 | jcstress + CountDownLatch |

---

## 5. 覆盖率目标

| 模块 | 当前 | 目标 | 时间线 |
|------|:----:|:----:|--------|
| tfi-all | ~60% | 70% | 2 周 |
| tfi-compare | ~55% | 65% | 2 周 |
| tfi-ops-spring | 0% | 50% | 3 周 |
| tfi-flow-spring-starter | 0% | 50% | 3 周 |

---

## 6. 工具链

| 工具 | 用途 |
|------|------|
| JUnit 5 | 测试框架 |
| AssertJ | 流式断言 |
| Mockito | Mock |
| jqwik | 属性测试 |
| ArchUnit | 架构测试 |
| ApprovalTests | 金标准测试 |
| JMH 1.37 | 微基准测试 |
| JaCoCo | 覆盖率 |
