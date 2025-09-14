# 输出实现模块需求澄清问题清单

## 评审日期
2025-01-08

## 现有实现评审结果

### ConsoleExporter评审
**现有实现问题：**
1. **设计模式问题**：使用静态工具类模式，而任务卡要求是实例化的类
2. **输出格式不符**：当前使用简单的缩进格式，不是要求的ASCII树形格式（├──, └──, │）
3. **功能缺失**：
   - 缺少 `export(Session session): String` 返回字符串的方法
   - 缺少 `print(Session session, PrintStream out)` 支持自定义输出流
4. **性能未优化**：使用 System.out.printf 多次调用，没有使用 StringBuilder
5. **树形绘制错误**：当前的树形绘制算法不正确，没有正确处理连接线

### JsonExporter评审
**现有实现问题：**
1. **违反技术约束**：使用了Jackson库，任务卡明确要求不使用第三方JSON库
2. **设计模式问题**：静态工具类，而要求是实例化的类
3. **功能缺失**：
   - 缺少 `export(Session session, Writer writer)` 流式输出方法
   - 缺少 COMPAT/ENHANCED 模式支持
4. **依赖问题**：依赖MapExporter，增加了复杂性

### MapExporter评审
**额外发现：**
- 提供了Map转换功能，可以作为中间层
- 但增加了不必要的复杂性和性能开销

## 高优先级问题

### 1. 架构设计不符合要求
- **影响**：需要重构为实例化的类，而非静态工具类
- **建议**：重新设计类结构，使用实例方法

### 2. JsonExporter违反技术约束
- **影响**：使用Jackson库违反了"不使用第三方JSON库"的要求
- **建议**：实现手动JSON序列化，移除Jackson依赖

### 3. ConsoleExporter格式不正确
- **影响**：输出格式不满足ASCII树形要求
- **建议**：重新实现树形绘制算法，使用正确的ASCII字符

## 中优先级问题

### 1. 性能未优化
- **影响**：当前实现可能无法满足性能指标（ConsoleExporter<10ms, JsonExporter<20ms）
- **建议**：使用StringBuilder预分配容量，减少字符串操作

### 2. 缺少测试覆盖
- **影响**：没有单元测试，无法验证功能正确性
- **建议**：编写完整的测试套件，覆盖率≥95%

### 3. 缺少流式输出支持
- **影响**：JsonExporter无法处理大数据量
- **建议**：实现Writer接口的流式输出

## 低优先级问题

### 1. 缺少导出模式支持
- **影响**：JsonExporter没有COMPAT/ENHANCED模式
- **建议**：添加ExportMode枚举和相应逻辑

### 2. 错误处理不完善
- **影响**：null处理过于简单
- **建议**：提供更友好的错误信息

## 需求确认清单

### ConsoleExporter需求
- [ ] 实例化类，非静态工具类
- [ ] ASCII树形格式（├──, └──, │, 空格）
- [ ] export(Session): String 方法
- [ ] print(Session) 和 print(Session, PrintStream) 方法
- [ ] 性能 < 10ms/1000节点
- [ ] StringBuilder优化

### JsonExporter需求
- [ ] 实例化类，非静态工具类
- [ ] 不使用第三方JSON库
- [ ] 手动实现JSON序列化
- [ ] export(Session): String 方法
- [ ] export(Session, Writer) 流式输出
- [ ] COMPAT/ENHANCED模式支持
- [ ] 性能 < 20ms/1000节点
- [ ] 特殊字符转义处理

## 结论
- [ ] 需求100%明确
- [x] 需要重构现有实现
- [x] 可以进入实现阶段

## 建议实施方案

### 第一步：重构ConsoleExporter
1. 改为实例化类
2. 实现正确的ASCII树形绘制
3. 添加StringBuilder优化
4. 实现所有要求的方法

### 第二步：重构JsonExporter  
1. 移除Jackson依赖
2. 实现手动JSON序列化
3. 添加流式输出支持
4. 实现导出模式

### 第三步：编写测试
1. 功能测试
2. 性能测试
3. 边界测试

## 风险提示
1. **重构风险**：需要完全重写两个类
2. **性能风险**：手动JSON序列化可能影响性能
3. **兼容性风险**：需要确保输出格式正确

---

**评审结论：** 现有实现不满足任务卡要求，需要进行重大重构。建议按照任务卡要求重新实现。