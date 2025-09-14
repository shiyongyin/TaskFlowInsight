# V210-042: 预热与有界缓存（PathMatcher & 相关）

- 优先级：P0  
- 预估工期：S（<2天）  
- Phase：M2.1  
- Owner：待定  
- 前置依赖：V210-003  
- 关联设计：`Spring Integration – 预热与有界缓存`

## 目标
- 应用启动时预热 PathMatcherCache；
- 统一有界缓存策略与阈值配置；
- 失败降级与指标计数。

## 实现要点
- 读取 `tfi.change-tracking.path-matcher.preload` 进行预编译；
- 记录成功/失败数；失败降级 literal；
- 公开容量与上限配置；
- 与 Actuator 端点联动展示统计概览。

### 配置示例
```yaml
tfi:
  change-tracking:
    path-matcher:
      preload: ["order/**", "user/*/name"]
      max-size: 1000
      pattern-max-length: 512
      max-wildcards: 32
```

## 验收标准
- [ ] 预热与容量约束生效；
- [ ] 指标可观测；
- [ ] 与默认 balanced 配置兼容。

## 对现有代码的影响（Impact）
- 影响级别：低。
- 行为：纯配置与初始化优化；不改变业务输出；失败降级为 literal。
- 测试：新增预热与降级覆盖；原有功能不受影响。
