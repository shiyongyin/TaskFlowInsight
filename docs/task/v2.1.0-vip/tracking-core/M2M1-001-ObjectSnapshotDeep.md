# tracking-core - M2M1-001-ObjectSnapshotDeep（VIP 终版）

## 目标与范围

### 业务目标
在不改变现有使用习惯的前提下，提供可控、可观测、可回退的深度对象快照能力，服务变更追踪（前后快照）。

### 技术目标
- 保持100%向后兼容
- 通过开关控制新功能（默认关闭）
- 提供清晰的降级路径

### Out of Scope
并行 DFS、元素级集合 Diff、平台特化优化、Spring Boot 2.x 支持（主线仅支持3.x）

## 核心设计决策

### 兼容性策略（最高优先级）
- **原则**：兼容优先 + 开关兜底 + 分阶段推进
- **默认行为**：保持现有浅快照不变
- **增强功能**：全部通过开关控制，默认关闭

### 架构设计
```java
// 门面模式 + 适配器模式
public class SnapshotFacade {
    private final ObjectSnapshot shallowSnapshot;  // 现有
    private final ObjectSnapshotDeep deepSnapshot; // 新增
    
    public Map<String, Object> capture(Object root, SnapshotConfig config) {
        // 默认路径：使用现有浅快照
        if (!config.isDeepEnabled()) {
            return shallowSnapshot.capture(name, root);
        }
        // 可选路径：深度快照（需开关开启）
        return deepSnapshot.captureDeep(root, config);
    }
}
```

## 实现要求

### Phase 1 - 低风险改动（本周）
1. **配置属性化**
   ```yaml
   tfi:
     snapshot:
       deep:
         enabled: false          # 默认关闭
         max-depth: 3           # 最大深度
         max-elements: 100      # 集合最大元素
         max-stack-depth: 1000  # 调用栈限制
   ```

2. **创建门面骨架**
   - 新建 `SnapshotFacade` 类
   - 保持默认行为不变
   - 端点暴露配置状态

### Phase 2 - 核心功能（下周）
1. **深度遍历实现**
   - 循环引用检测（IdentityHashMap）
   - 路径跟踪（如 `user.address.city`）
   - 类型处理（基本类型、集合、自定义对象）

2. **性能护栏**
   - maxDepth：最大深度限制
   - maxElements：集合最大元素数
   - maxStackDepth：调用栈深度限制
   - 超限自动降级到浅快照

### Phase 3 - 优化增强（后续）
1. 代理对象支持（Spring AOP/CGLIB）
2. 性能优化和缓存调优
3. 更细粒度的路径匹配

## 性能指标（分场景SLA）

### 默认路径（浅快照）
- CPU开销：< 1%（护栏要求）
- 响应时间：< 100ns
- 内存占用：不变

### 深度快照（开启后）
| 对象复杂度 | P50 | P99 | CPU开销 |
|-----------|-----|-----|---------|
| 简单（<10字段） | 100ns | 1ms | <1% |
| 中等（10-100字段） | 1ms | 10ms | <3% |
| 复杂（>100字段） | 10ms | 100ms | <5% |

注：深度快照作为独立基线验证，不与默认护栏冲突

## 冲突解决方案

### 核心冲突点及决策

1. **与现有ObjectSnapshot的关系**
   - 冲突：现有只支持标量字段，直接修改会破坏兼容性
   - 决策：门面模式 + 适配器，保持向后兼容
   - 实施：Phase 1保持默认行为，Phase 2通过开关启用

2. **Spring版本兼容**
   - 冲突：2.x和3.x API不兼容
   - 决策：主线仅支持Spring Boot 3.x
   - 备选：如需2.x，发布独立starter子构件

3. **性能目标矛盾**
   - 冲突：深度遍历与<5% CPU开销难以同时满足
   - 决策：分场景SLA，默认能力<5%，深度功能独立基线

## 测试计划

### 功能测试
- 基本类型快照测试
- 嵌套对象快照测试
- 集合类型快照测试
- 循环引用检测测试

### 兼容性测试（重点）
- **开关切换测试**
  - 默认关闭时使用浅快照
  - 开启后使用深快照
  - 运行时切换行为验证
- **降级路径测试**
  - 超限自动降级
  - 异常时回退到浅快照
- **现有功能不受影响**

### 性能测试
- 分场景基准测试（simple/medium/large）
- CPU开销验证
- 内存占用监控

## 监控与可观测性

### 核心指标
```java
// 最小指标集
tfi.snapshot.depth.limit      // 深度限制触发次数
tfi.snapshot.cycle.skip       // 循环引用跳过次数
tfi.snapshot.fallback.count   // 降级次数
tfi.snapshot.config.status    // 配置状态（开/关）
```

### 日志级别约定
- INFO：开关状态变更、降级触发
- WARN：超限警告、异常降级
- DEBUG：路径跳过、循环检测

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 行为漂移 | 低 | 高 | 开关默认关闭，充分测试 |
| 性能退化 | 中 | 中 | 分场景SLA，自动降级 |
| 内存泄漏 | 低 | 高 | IdentityHashMap，定期清理 |
| 代理对象处理 | 中 | 低 | Phase 3再支持，先降级 |

## 实施检查清单

### Phase 1（立即执行）
- [ ] 增加配置属性 `tfi.snapshot.deep.*`
- [ ] 创建 `SnapshotFacade` 骨架
- [ ] 端点暴露配置状态
- [ ] 更新 application.yml 示例

### Phase 2（下周）
- [ ] 实现 `ObjectSnapshotDeep`
- [ ] 集成循环检测
- [ ] 添加性能护栏
- [ ] 完成单元测试

### Phase 3（后续）
- [ ] 性能优化
- [ ] 代理对象支持
- [ ] 基准报告

## 开放问题
1. include/exclude 默认配置预置清单？
   - 建议：先不预置，根据使用反馈添加
2. 字段元数据缓存上限调整？
   - 建议：可配置化，默认保持1024

---
*更新：基于工程评审反馈，强化兼容性和分阶段实施*