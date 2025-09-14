# Monitoring Checks 设计说明（非MVP，可选）

本文件将“监控/泄漏检测”与核心上下文管理解耦，明确其为可选能力：默认关闭，可通过配置启用，调试期使用为主，稳定后可降低频率或关闭。

## 1. 范围与定位
- 范围：上下文泄漏检测（定期扫描）、死线程清理、诊断与统计查询
- 定位：可选模块，非MVP必需；对核心功能零侵入

## 2. 启用/关闭与频率
- 默认关闭：
  - `SafeContextManager` 泄漏检测：`taskflow.context.leakDetection.enabled=false`
  - `ZeroLeakThreadLocalManager` 清理任务：`taskflow.threadlocal.cleanup.enabled=false`
- 配置开启：
  - `-Dtaskflow.context.leakDetection.enabled=true`
  - `-Dtaskflow.context.leakDetection.intervalMillis=60000`
  - `-Dtaskflow.threadlocal.cleanup.enabled=true`
  - `-Dtaskflow.threadlocal.cleanup.intervalMillis=60000`
- 频率调整：稳定后降低/关闭；接口`setLeakDetectionEnabled/Interval`、`setCleanupEnabled`支持运行时切换

## 3. 解耦设计
- 懒启动：仅启用时创建调度线程；关闭时立即`shutdownNow()`并释放资源
- 配置读取：启动时从`System.getProperty`读取；支持运维通过JVM参数配置
- 最小依赖：监控逻辑仅引用公共API；不影响核心上下文创建/传播/关闭

## 4. 典型使用场景
- 初期联调：开启高频检测，快速定位未关闭上下文
- 稳定期：降低频率（如5-10分钟），或关闭监控
- 故障诊断：短时开启`ZeroLeakThreadLocalManager`诊断模式（反射默认关闭）

## 5. 约束与建议
- 线上默认关闭，必要时短期开启；避免高频扫描带来的额外开销
- 告警与指标集成可作为后续增强，避免与MVP绑定

---
上述设计确保监控检查不与MVP强绑定，支持配置化开启/关闭，并在实现上与核心功能低耦合。

