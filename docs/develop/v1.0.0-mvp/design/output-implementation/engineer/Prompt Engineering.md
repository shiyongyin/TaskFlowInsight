# 输出实现模块 - 提示词工程指南

## 概述
本文档提供输出实现模块开发的精准提示词模板，帮助开发者高效驱动AI完成ConsoleExporter和JsonExporter的实现、测试和优化工作。

## 核心提示词设计原则

### 1. 明确角色定位
- 指定AI扮演"资深Java输出格式专家"
- 强调格式美观性和标准合规性
- 突出性能优化和内存管理能力

### 2. 清晰任务边界
- 精确指定输出格式要求（ASCII树形、JSON RFC 7159）
- 明确性能指标（控制台<10ms、JSON<20ms）
- 限定实现范围（不包含XML、HTML等其他格式）

### 3. 结构化输出要求
- 要求代码包含中文注释
- 指定测试覆盖率≥95%
- 规定文档格式和位置

## 分阶段提示词模板

### 阶段1：需求分析与设计评审

#### 提示词模板1.1：需求理解
```
你是一位资深的Java输出格式设计专家。请分析以下输出实现需求：

任务：
1. 阅读 docs/task/v1.0.0-mvp/output-implementation/TASK-014-ConsoleOutput.md
2. 阅读 docs/task/v1.0.0-mvp/output-implementation/TASK-015-JsonExport.md
3. 理解 TaskFlow Insight 的 Session、TaskNode、Message 数据模型

输出要求：
1. 总结控制台输出的核心需求（5个要点）
2. 总结JSON导出的核心需求（5个要点）
3. 识别可能的技术难点和风险
4. 提出3个澄清问题（如有疑问）

格式：使用Markdown，每部分用二级标题分隔
```

#### 提示词模板1.2：设计方案评审
```
角色：输出格式架构师
任务：评审输出实现设计方案

请评审以下设计文档：
- docs/develop/v1.0.0-mvp/design/output-implementation/DEV-014-控制台输出实现.md
- docs/develop/v1.0.0-mvp/design/output-implementation/DEV-015-JSON导出实现.md

评审维度：
1. 设计完整性（是否覆盖所有需求）
2. 技术可行性（实现难度和风险）
3. 性能可达性（能否满足性能指标）
4. 扩展性（未来支持其他格式的难度）

输出：
- 设计优点（3-5个）
- 潜在问题（如有）
- 改进建议（如有）
- 总体评价（通过/需修改）
```

### 阶段2：核心功能实现

#### 提示词模板2.1：ConsoleExporter实现
```
角色：Java高级开发工程师，精通文本格式化和树形结构展示
任务：实现ConsoleExporter类

技术要求：
- Java 21语法特性
- 使用StringBuilder高效构建字符串
- ASCII字符绘制树形结构（├──, └──, │, 空格）
- 递归遍历TaskNode树
- 支持PrintStream和String两种输出方式

功能要求：
1. export(Session session): String - 导出为字符串
2. print(Session session) - 输出到System.out
3. print(Session session, PrintStream out) - 输出到指定流
4. 格式化会话头部信息（ID、线程、状态、耗时）
5. 递归绘制任务树（缩进、连接线、任务信息）
6. 显示任务消息（按类型标记[INFO]/[ERROR]/[WARN]）

性能要求：
- 1000个节点生成时间 < 10ms
- 内存使用 < 1MB
- StringBuilder预分配容量优化

代码要求：
- 方法注释使用中文
- 关键算法添加实现说明
- 遵循KISS原则
- 处理null和空数据情况

请提供完整的ConsoleExporter.java实现代码。
```

#### 提示词模板2.2：JsonExporter实现
```
角色：Java高级开发工程师，精通JSON序列化和流式处理
任务：实现JsonExporter类

技术要求：
- Java 21语法特性
- 手动JSON序列化（不使用第三方库）
- 支持Writer流式输出
- 正确的JSON转义处理
- 递归序列化嵌套结构

功能要求：
1. export(Session session): String - 导出为JSON字符串
2. export(Session session, Writer writer) - 流式导出
3. 序列化Session完整信息
4. 递归序列化TaskNode树结构
5. 序列化Message列表
6. 处理特殊字符转义（\", \\, \n, \r, \t）

输出模式：
1. COMPAT模式（默认）：
   - 时间使用毫秒时间戳
   - 简洁的字段名
   - 与基线示例一致
   
2. ENHANCED模式（可选）：
   - 包含$schema定义
   - 纳秒精度时间
   - 额外统计信息

性能要求：
- 1000个节点序列化 < 20ms
- 内存使用 < 2MB
- 支持10000+节点流式处理

代码要求：
- 方法注释使用中文
- JSON格式符合RFC 7159
- 无第三方依赖
- 异常处理完善

请提供完整的JsonExporter.java实现代码。
```

#### 提示词模板2.3：工具类实现
```
角色：Java工具类设计专家
任务：实现输出格式化辅助工具类

需要实现的工具类：
1. TreeFormatter - 树形格式化工具
   - 计算缩进
   - 生成连接线
   - 处理最后节点标记
   
2. JsonWriter - 简化JSON写入
   - writeString(String value)
   - writeNumber(long value)
   - writeBoolean(boolean value)
   - writeNull()
   - 自动转义处理

3. TimeFormatter - 时间格式化
   - 格式化毫秒为可读时间
   - 计算持续时间
   - 时间戳转换

要求：
- 静态工具方法
- 高性能实现
- 充分的参数验证
- 单元测试友好设计

输出完整的工具类代码。
```

### 阶段3：单元测试编写

#### 提示词模板3.1：ConsoleExporter测试
```
角色：高级测试工程师
任务：为ConsoleExporter编写完整测试套件

测试范围：
1. 基本功能测试
   - testBasicSessionExport() - 基本导出
   - testEmptySession() - 空会话
   - testNullSession() - null处理
   - testSingleTask() - 单任务

2. 树结构测试
   - testNestedTasks() - 嵌套任务
   - testMultipleChildren() - 多子节点
   - testDeepNesting() - 深层嵌套（10层+）
   - testComplexHierarchy() - 复杂树形

3. 格式化测试
   - testAsciiAlignment() - ASCII对齐
   - testStatusDisplay() - 状态显示
   - testMessageFormatting() - 消息格式
   - testTimeFormatting() - 时间格式

4. 边界测试
   - testLongTaskNames() - 超长任务名
   - testSpecialCharacters() - 特殊字符
   - testLargeDataSet() - 1000+节点
   - testMaxDepth() - 最大深度

5. 性能测试
   - testPerformance1000Nodes() - 1000节点<10ms
   - testMemoryUsage() - 内存使用<1MB

测试要求：
- JUnit 5框架
- 断言清晰明确
- 测试数据构建器模式
- 覆盖率≥95%

输出ConsoleExporterTest.java完整代码。
```

#### 提示词模板3.2：JsonExporter测试
```
角色：高级测试工程师，精通JSON格式验证
任务：为JsonExporter编写完整测试套件

测试范围：
1. JSON格式测试
   - testJsonValidity() - 格式合法性
   - testJsonParseable() - 可解析性
   - testFieldCompleteness() - 字段完整性
   - testNestedStructure() - 嵌套结构

2. 数据完整性测试
   - testSessionSerialization() - Session序列化
   - testTaskNodeSerialization() - TaskNode序列化
   - testMessageSerialization() - Message序列化
   - testTimestampAccuracy() - 时间戳精度

3. 特殊字符测试
   - testEscaping() - 转义字符
   - testUnicode() - Unicode字符
   - testControlCharacters() - 控制字符
   - testEmptyStrings() - 空字符串

4. 模式测试
   - testCompatMode() - 兼容模式
   - testEnhancedMode() - 增强模式
   - testModeConsistency() - 模式一致性

5. 性能测试
   - testPerformance1000Nodes() - 1000节点<20ms
   - testStreamingLargeData() - 流式大数据
   - testMemoryEfficiency() - 内存效率

验证方法：
- 使用Jackson ObjectMapper验证JSON
- 字符串比较验证格式
- Schema验证（如适用）

输出JsonExporterTest.java完整代码。
```

### 阶段4：集成测试与优化

#### 提示词模板4.1：集成测试
```
角色：集成测试专家
任务：编写输出实现模块集成测试

测试场景：
1. 端到端测试
   - 创建Session → 添加Tasks → 导出Console → 验证
   - 创建Session → 添加Tasks → 导出JSON → 解析验证

2. 兼容性测试
   - ConsoleExporter输出在不同终端显示
   - JsonExporter输出被不同JSON库解析

3. 并发测试
   - 多线程同时导出
   - 大量Session并发处理

4. 真实场景测试
   - Web请求处理场景
   - 批处理任务场景
   - 异常处理场景

测试类：OutputIntegrationTest.java
要求：模拟真实使用场景，验证实际可用性
```

#### 提示词模板4.2：性能优化
```
角色：性能优化专家
任务：优化输出实现模块性能

优化目标：
1. ConsoleExporter
   - 当前：1000节点12ms → 目标：<10ms
   - 减少字符串拼接
   - 优化递归算法
   - 缓存重复计算

2. JsonExporter  
   - 当前：1000节点25ms → 目标：<20ms
   - 优化转义处理
   - 减少Writer调用次数
   - 批量写入优化

优化技术：
- StringBuilder容量预估
- 字符数组复用
- 缓存常用字符串
- 减少对象创建

输出：
1. 性能分析报告
2. 优化代码差异
3. 性能测试对比

使用JMH进行基准测试验证优化效果。
```

### 阶段5：文档与交付

#### 提示词模板5.1：API文档生成
```
角色：技术文档工程师
任务：编写输出实现模块API文档

文档内容：
1. 模块概述
   - 功能介绍
   - 架构设计
   - 使用场景

2. API参考
   - ConsoleExporter类
   - JsonExporter类
   - 方法签名和说明
   - 参数说明
   - 返回值说明
   - 异常说明

3. 使用示例
   - 基本用法
   - 高级特性
   - 最佳实践
   - 常见问题

4. 输出格式规范
   - 控制台格式示例
   - JSON格式示例
   - 字段说明

输出：OUTPUT-IMPLEMENTATION-API.md
要求：清晰、完整、包含代码示例
```

#### 提示词模板5.2：验收报告
```
角色：项目验收专员
任务：生成输出实现模块验收报告

验收内容：
1. 功能完成度
   - [x] ConsoleExporter实现
   - [x] JsonExporter实现
   - [x] 多输出流支持
   - [x] 错误处理

2. 性能指标
   - 控制台输出：实测值 vs 目标值
   - JSON序列化：实测值 vs 目标值
   - 内存使用：实测值 vs 目标值

3. 质量指标
   - 代码覆盖率：___%
   - 单元测试数：___个
   - 集成测试数：___个
   - Bug修复数：___个

4. 文档完成度
   - API文档
   - 使用示例
   - 性能报告

生成验收报告OUTPUT-ACCEPTANCE.md
```

## 高级提示词技巧

### 1. 迭代优化提示词
```
初始提示：实现ConsoleExporter
↓
优化1：实现ConsoleExporter，使用StringBuilder，处理null
↓
优化2：实现ConsoleExporter类，要求：
- 使用StringBuilder（预分配4096容量）
- 递归遍历TaskNode
- null安全处理
- 性能<10ms/1000节点
↓
最终：[见模板2.1]
```

### 2. 链式提示词
```
Step 1: 分析需求 → 输出理解
Step 2: 基于理解 → 设计方案
Step 3: 基于方案 → 实现代码
Step 4: 基于代码 → 编写测试
Step 5: 基于测试 → 优化性能
```

### 3. 对比提示词
```
对比任务：
方案A：使用StringBuilder实现JSON序列化
方案B：使用Writer流式实现JSON序列化

请对比两种方案的：
1. 性能差异
2. 内存使用
3. 代码复杂度
4. 适用场景

推荐最佳方案并说明理由。
```

### 4. 验证提示词
```
验证任务：
给定代码：[ConsoleExporter实现]

请验证：
1. 是否满足所有功能需求
2. 是否达到性能指标
3. 是否处理边界情况
4. 是否符合编码规范

输出验证报告和改进建议。
```

## 提示词质量检查清单

### 好的提示词特征
- ✅ 明确的角色定位
- ✅ 清晰的任务描述
- ✅ 具体的技术要求
- ✅ 可量化的性能指标
- ✅ 结构化的输出要求
- ✅ 包含示例和参考

### 避免的提示词问题
- ❌ 模糊的任务描述
- ❌ 缺少技术约束
- ❌ 没有性能要求
- ❌ 输出格式不明确
- ❌ 缺少验收标准
- ❌ 过于冗长复杂

## 特定场景提示词

### 场景1：Bug修复
```
角色：Bug修复专家
问题：ConsoleExporter在处理深度>20的树时对齐错位

任务：
1. 复现问题（提供测试用例）
2. 定位根因（调试分析）
3. 提出修复方案
4. 实现修复代码
5. 验证修复效果

要求：最小改动，不影响其他功能
```

### 场景2：代码重构
```
角色：重构专家
目标：提升JsonExporter可维护性

任务：
1. 分析现有代码问题
2. 提出重构方案
3. 执行重构（保持功能不变）
4. 确保测试通过
5. 对比重构前后

原则：
- 单一职责
- 开闭原则
- 提高可测试性
```

### 场景3：新功能扩展
```
角色：功能设计师
需求：添加XML导出支持

任务：
1. 设计XmlExporter接口
2. 参考JsonExporter实现
3. 保持架构一致性
4. 编写单元测试
5. 更新文档

约束：
- 复用现有基础设施
- 性能指标参考JSON
- 向后兼容
```

## 提示词模板使用指南

### 1. 选择合适的模板
- 需求分析 → 使用模板1.x
- 功能实现 → 使用模板2.x
- 测试编写 → 使用模板3.x
- 优化调试 → 使用模板4.x
- 文档交付 → 使用模板5.x

### 2. 定制化调整
- 根据具体需求调整参数
- 添加项目特定约束
- 补充领域知识背景

### 3. 迭代改进
- 记录提示词效果
- 收集最佳实践
- 持续优化模板

## 总结

本指南提供了输出实现模块开发全流程的提示词模板，覆盖从需求分析到最终交付的各个阶段。通过使用这些精心设计的提示词，可以：

1. 提高开发效率
2. 确保代码质量
3. 满足性能要求
4. 完善测试覆盖
5. 规范文档输出

持续优化和积累提示词模板，将显著提升团队的AI辅助开发能力。