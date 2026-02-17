# TaskFlowInsight 整体项目 — 运维文档

> **作者**: 资深运维专家  
> **日期**: 2026-02-16  
> **版本**: v1.0  
> **范围**: 全项目 CI/CD、监控、安全

---

## 1. 运维概述

TFI 是 Java 库，通过 Maven 依赖集成到宿主应用，运维关注：配置、监控、CI/CD、安全。

---

## 2. 构建命令

```bash
./mvnw clean verify          # 完整构建 + 测试 + 质量检查
./mvnw clean package -DskipTests  # 快速打包
./mvnw test -Dtest=ClassName  # 指定测试
./mvnw clean test jacoco:report   # 覆盖率报告
./mvnw spotbugs:check         # SpotBugs
./mvnw checkstyle:check       # Checkstyle
./mvnw pmd:check              # PMD
```

---

## 3. CI/CD 流水线

### tfi-compare-ci.yml

- **触发**: push/PR on main, feature/**
- **Jobs**: build-and-test → static-analysis → dependency-check → api-compat

### perf-gate.yml

- **触发**: push/PR on main
- **门禁**: < 5% 性能退化

### 流水线缺口

| 问题 | 风险 |
|------|------|
| 仅覆盖 tfi-compare | **高**: 其余模块无 CI |
| SpotBugs/Checkstyle/PMD 不阻断构建 | **高**: 质量阀门失效 |
| OWASP 仅 tfi-compare | 中 |

---

## 4. 监控

### Prometheus 指标

| 指标 | 类型 |
|------|------|
| `tfi_stage_duration_seconds` | Histogram |
| `tfi_compare_duration_seconds` | Histogram |
| `tfi_tracking_objects_count` | Gauge |
| `tfi_errors_total` | Counter |

### 健康检查 (TfiHealthIndicator)

| 维度 | 阈值 |
|------|------|
| 内存增量 | < 10MB |
| CPU | < 0.1% |
| 缓存命中率 | > 95% |
| 错误率 | < 1% |
| 健康评分 | ≥ 70: UP, < 50: DOWN |

---

## 5. 安全

| 维度 | 状态 |
|------|------|
| 敏感字段屏蔽 | ✅ exclude-patterns |
| Actuator 安全 | ✅ SecureTfiEndpoint |
| OWASP 扫描 | ✅ tfi-compare（需扩展） |
| SpotBugs | ✅ 但未阻断构建 |

---

## 6. 配置管理

```
优先级: System Props > Env Vars > application-{profile}.yml > application.yml > 代码默认值
```

| Profile | TFI | 日志 | Actuator |
|---------|:---:|------|----------|
| dev | 启用 | DEBUG | 全开放 |
| prod | 禁用 | INFO | health/info |

---

## 7. 运维就绪度

| 维度 | 评分 |
|------|:----:|
| 可观测性 | 7/10 |
| 可部署性 | 5/10 |
| 可配置性 | 8/10 |
| 安全性 | 6/10 |
| CI/CD | 5/10 |
| **综合** | **6.3/10** |
