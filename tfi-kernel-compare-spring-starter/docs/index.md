# TFI Kernel Compare Spring Starter 文档

本目录是 `tfi-kernel-compare-spring-starter` 的文档入口。当前模块提供 Kernel、Compare Core 与纯 Java bridge 的
Spring Boot 程序化组合，以及默认关闭的完整 AOP convenience；业务语义仍由两个 Core 和 bridge 拥有。

## 文档导航

- [设计说明](design-doc.md)：依赖边界、Bean 图、owner 模式与启动失败语义。
- [测试计划](test-plan.md)：D1+D2+E1+E2 契约测试、验收命令与证据要求。
- [迁移说明](migration.md)：从旧 Flow/Compare Spring 生态迁移到组合 Starter。
- [运维说明](operations.md)：制品冲突、context 退役、Sink 合同与故障处置。

## 当前交付边界

- 已交付：三组 typed properties、六种 owner 模式、固定派生 Bean、Recorder、composition validator、Runtime retirement、
  context 隔离/销毁顺序、三层 artifact guard、optional AOP dependency、两注解与无缓存静态 method plan、AOP tracking
  exactly-once、返回/异常身份、事务观察语义与可复现的初始性能基线。
- KCS-10 待完成：真实 consumer、发布制品、回滚、全 Reactor，以及 Compare Core BASELINED 和 owner 性能预算/RC 决策。

当前 E2 产物同时保留程序化路径与可整体关闭的 AOP 路径，但不代表最终发布 Gate 已通过。
