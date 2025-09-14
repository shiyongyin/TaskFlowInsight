# Context-Management 开发执行提示词

本文档为AI扮演"专业软件开发工程师"角色，执行Context-Management模块开发的完整提示词指南。结合任务卡要求、设计文档和工程约束，按四阶段流程推进开发工作。

## 前置条件与参考资源

### 必读文档路径
- 任务设计：`/Users/mac/work/development/project/TaskFlowInsight/docs/task/v1.0.0-mvp/context-management/`
- 详细设计：`/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v1.0.0-mvp/design/context-management/`
- 工程指南：`/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v1.0.0-mvp/design/context-management/engineer/`
- 项目规范：`/Users/mac/work/development/project/TaskFlowInsight/CLAUDE.md`

### 开发环境要求
- Java版本：21
- Spring Boot：3.5.5
- 构建工具：Maven Wrapper
- 测试框架：JUnit 5
- 服务端口：19090

### 关键命令
```bash
# 构建验证
./mvnw clean verify

# 运行测试
./mvnw test

# 启动应用
./mvnw spring-boot:run
```

---

## 主执行提示词（Master Execution Prompt）

```
你是一名专业的Java软件开发工程师，精通并发编程、内存管理和Spring Boot框架。
你将严格按照四阶段流程完成TaskFlowInsight的Context-Management模块开发。

技术约束：
- Java 21语法特性，包括虚拟线程和StructuredTaskScope
- Spring Boot 3.5.5框架集成
- 中文注释，简洁明确
- KISS原则，避免过度设计
- 可读性优先，性能第二

开发约束：
- 仅修改src/main/java和src/test/java目录
- 保持与现有Session/TaskNode模型兼容
- 100%资源清理保证
- 零内存泄漏设计目标

质量要求：
- 单元测试覆盖率 > 95%
- 性能目标：上下文创建<1μs，任务操作<100ns
- 并发安全验证
- 长时间运行稳定性
```

---

## 第一阶段：需求评估与问题澄清

### 执行步骤

```
阶段目标：100%明确所有实现细节

1. 深度阅读所有设计文档：
   - TASK-006～009任务卡
   - DEV-006～009设计实现
   - Context Engineering工程指南
   - AI Developer Prompt操作规程

2. 形成需求理解自检清单：
   [ ] ManagedThreadContext的强制资源管理机制是否明确？
   [ ] SafeContextManager的四层防护策略是否清晰？
   [ ] ZeroLeakThreadLocalManager的反射清理边界是否确定？
   [ ] 与现有Session/TaskNode的集成方式是否明确？
   [ ] InheritableThreadLocal vs 快照机制的使用场景是否清楚？
   [ ] 虚拟线程支持的实现方式是否明确？
   [ ] 性能目标的测试验证方法是否清晰？

3. 输出问题清单（如有）：
   文件路径：docs/develop/v1.0.0-mvp/design/context-management/Development-Questions.md
   
   格式示例：
   ## 需求澄清问题清单
   
   ### 高优先级问题
   1. [API设计] ManagedThreadContext.create()是否应该检查现有上下文？
      - 现状：文档未明确
      - 影响：可能导致嵌套上下文处理不一致
      - 建议：明确是警告还是异常
   
   ### 中优先级问题
   ...
   
4. 自我迭代验证：
   - 重新审视每个问题是否真的需要澄清
   - 检查是否可以从现有文档推导答案
   - 确认达到100%明确后进入下一阶段
```

### 评估检查点

```yaml
需求明确性:
  - API契约: 100%定义
  - 异常处理: 明确所有异常类型和场景
  - 性能指标: 量化且可测试
  - 兼容性: 与现有代码的集成点明确

实现可行性:
  - 技术风险: 已识别并有缓解方案
  - 依赖关系: 无循环依赖
  - 资源需求: 内存和CPU使用可控
  - 测试策略: 可验证所有需求
```

---

## 第二阶段：高质量编码实现

### 执行步骤

```
阶段目标：严格按照设计文档完成代码实现

1. 实现顺序（必须遵守依赖关系）：
   Step 1: ManagedThreadContext核心类
   Step 2: SafeContextManager管理器
   Step 3: ZeroLeakThreadLocalManager内存管理
   Step 4: 辅助类（装饰器、工具类）

2. 每个类的实现流程：
   a) 创建包结构和类框架
   b) 实现核心功能方法
   c) 添加中文注释说明
   d) 实现异常处理和资源清理
   e) 自检代码质量

3. 代码自检清单（每个类必须通过）：
   [ ] 需求覆盖度 = 100%
   [ ] 方法签名与设计文档一致
   [ ] 异常处理完整且合理
   [ ] 资源清理保证（try-with-resources）
   [ ] 线程安全性验证
   [ ] 性能热点优化
   [ ] 注释清晰完整
```

### 关键实现示例

```java
// ManagedThreadContext核心实现模板
public final class ManagedThreadContext implements AutoCloseable {
    // 静态ThreadLocal持有器 - 强制管理
    private static final ThreadLocal<ManagedThreadContext> HOLDER = new ThreadLocal<>();
    
    // 构造函数私有化，强制使用工厂方法
    private ManagedThreadContext() {
        this.threadId = Thread.currentThread().getId();
        this.contextId = UUID.randomUUID().toString();
        this.sessionStack = new Stack<>();
        this.taskStack = new Stack<>();
        this.closed = new AtomicBoolean(false);
        
        // 检测并警告嵌套上下文
        ManagedThreadContext existing = HOLDER.get();
        if (existing != null && !existing.closed.get()) {
            this.parent = existing;
            this.nestingLevel = existing.nestingLevel + 1;
            LOGGER.warn("嵌套上下文检测: level={}, parentId={}", 
                       nestingLevel, existing.contextId);
        }
        
        // 设置到ThreadLocal
        HOLDER.set(this);
    }
    
    /**
     * 创建新的线程上下文
     * 必须在try-with-resources中使用
     */
    public static ManagedThreadContext create() {
        return new ManagedThreadContext();
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // 清理未完成的任务和会话
                while (!taskStack.isEmpty()) {
                    TaskNode task = taskStack.pop();
                    LOGGER.warn("强制结束未完成任务: {}", task.getName());
                    task.markFailed("Context closed");
                }
                
                while (!sessionStack.isEmpty()) {
                    Session session = sessionStack.pop();
                    LOGGER.warn("强制结束未完成会话: {}", session.getSessionId());
                    session.markError("Context closed");
                }
            } finally {
                // 从ThreadLocal移除
                HOLDER.remove();
                
                // 恢复父上下文
                if (parent != null) {
                    HOLDER.set(parent);
                }
            }
        }
    }
}
```

### 代码质量标准

```yaml
命名规范:
  - 类名: 大驼峰，见名知意
  - 方法名: 小驼峰，动词开头
  - 常量: 全大写下划线分隔
  - 变量: 小驼峰，名词

注释规范:
  - 类注释: 说明职责和使用场景
  - 方法注释: 说明功能、参数、返回值、异常
  - 关键逻辑: 解释为什么这样做
  - TODO标记: 明确后续改进点

性能优化:
  - 避免不必要的对象创建
  - 使用StringBuilder拼接字符串
  - 合理使用缓存
  - 优先无锁设计
```

---

## 第三阶段：全面测试与验证

### 执行步骤

```
阶段目标：通过全面测试验证实现正确性

1. 测试分层策略：
   - 单元测试：功能正确性（覆盖率>95%）
   - 集成测试：组件协作验证
   - 并发测试：线程安全性（1000线程）
   - 性能测试：达到性能指标
   - 稳定性测试：24小时运行

2. 测试用例设计原则：
   - 正常场景全覆盖
   - 边界条件必须测试
   - 异常场景完整验证
   - 并发竞争条件检测
   - 内存泄漏专项测试

3. 测试失败处理流程：
   a) 优先检查实现代码是否有bug
   b) 验证测试用例的合理性
   c) 调整实现而非为了通过测试修改用例
   d) 记录无法解决的问题

4. 测试报告生成：
   - 测试覆盖率统计
   - 性能基准数据
   - 问题和风险清单
   - 改进建议
```

### 核心测试模板

```java
@TestMethodOrder(OrderAnnotation.class)
class ManagedThreadContextTest {
    
    @Test
    @Order(1)
    @DisplayName("验证强制资源管理 - 必须使用try-with-resources")
    void testForcedResourceManagement() {
        // 验证无活动上下文时抛出异常
        assertThrows(IllegalStateException.class, 
            () -> ManagedThreadContext.current());
        
        // 验证正确使用方式
        try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
            assertNotNull(ctx);
            assertEquals(Thread.currentThread().getId(), ctx.getThreadId());
            
            // 验证功能
            Session session = ctx.startSession();
            TaskNode task = ctx.startTask("test");
            
            assertNotNull(session);
            assertNotNull(task);
            assertEquals("test", task.getName());
            
            ctx.endTask();
            ctx.endSession();
        }
        
        // 验证自动清理
        assertThrows(IllegalStateException.class, 
            () -> ManagedThreadContext.current());
    }
    
    @Test
    @Order(2) 
    @DisplayName("性能测试 - 上下文创建<1μs，任务操作<100ns")
    void testPerformanceRequirements() {
        // 预热JVM
        for (int i = 0; i < 10000; i++) {
            try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
                ctx.startTask("warmup");
                ctx.endTask();
            }
        }
        
        // 测试上下文创建性能
        final int ITERATIONS = 100000;
        long start = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
                // 仅创建和关闭
            }
        }
        
        long elapsed = System.nanoTime() - start;
        long avgTime = elapsed / ITERATIONS;
        
        assertTrue(avgTime < 1000, 
            String.format("上下文创建应<1μs，实际: %dns", avgTime));
    }
}
```

### 测试验收标准

```yaml
功能测试:
  - 单元测试通过率: 100%
  - 代码覆盖率: >95%
  - 分支覆盖率: >90%
  - 异常场景覆盖: 100%

性能测试:
  - 上下文创建: <1μs
  - 任务操作: <100ns
  - 内存占用: <1KB/线程
  - 泄漏检测开销: <1%

稳定性测试:
  - 24小时零泄漏
  - 高并发无死锁
  - 异常恢复正常
  - 资源清理100%
```

---

## 第四阶段：交付与文档更新

### 执行步骤

```
阶段目标：完成交付物并更新所有相关文档

1. 验收评估：
   - 对照每个任务卡的验收标准
   - 记录达成和未达成项
   - 评估差距和改进方案

2. 文档更新：
   路径：docs/develop/v1.0.0-mvp/design/context-management/
   
   创建：Development-Report.md
   内容：
   - 实现总结
   - 测试结果
   - 性能数据
   - 已知问题
   - 后续建议

3. 任务卡状态更新：
   - 标记完成的验收项
   - 说明未完成项的原因
   - 提供问题解决方案
   - 估算剩余工作量

4. 代码审查准备：
   - 确保所有代码有注释
   - 格式化代码
   - 更新README
   - 准备演示案例
```

### 交付清单模板

```markdown
## Context-Management 开发交付报告

### 完成情况汇总

#### DEV-006: ManagedThreadContext
- [x] 强制资源管理实现
- [x] 嵌套上下文检测
- [x] 上下文快照功能
- [x] 性能目标达成（<1μs）
- [ ] 虚拟线程完整支持（需Java 21环境）

完成度：90%
剩余工作：虚拟线程测试环境配置

#### DEV-007: SafeContextManager
- [x] 四层防护机制
- [x] 异步任务支持
- [x] 监控指标实现
- [x] 健康检查功能
- [x] 零泄漏验证通过

完成度：100%

#### DEV-008: ZeroLeakThreadLocalManager
- [x] 多层检测机制
- [x] 自动修复功能
- [x] 诊断模式（默认关闭）
- [x] 反射清理（需--add-opens）
- [x] 降级策略实现

完成度：95%
风险项：反射操作JVM兼容性

#### DEV-009: 测试实现
- [x] 单元测试（覆盖率96%）
- [x] 并发测试（1000线程通过）
- [x] 性能测试（达标）
- [x] 24小时稳定性（零泄漏）

完成度：100%

### 性能测试结果

| 指标 | 目标值 | 实测值 | 状态 |
|------|--------|--------|------|
| 上下文创建 | <1μs | 0.8μs | ✅ |
| 任务操作 | <100ns | 85ns | ✅ |
| 内存占用 | <1KB | 768B | ✅ |
| 泄漏检测开销 | <1% | 0.7% | ✅ |

### 已知问题与限制

1. 反射清理需要JVM参数：
   ```bash
   --add-opens java.base/java.lang=ALL-UNNAMED
   ```

2. InheritableThreadLocal在线程池中的限制已通过快照机制解决

3. 虚拟线程测试需要Java 21环境

### 后续优化建议

1. 增加Micrometer集成用于指标导出
2. 提供Spring Boot Starter简化集成
3. 增加可视化监控Dashboard
4. 性能进一步优化空间（批量操作）
```

---

## 使用指南

### 快速开始

对于AI执行开发任务，使用以下提示词：

```
我需要你作为专业的Java软件开发工程师，执行TaskFlowInsight项目的Context-Management模块开发。

请严格按照以下资源进行开发：
1. 开发执行提示词：docs/develop/v1.0.0-mvp/design/context-management/engineer/Development-Execution-Prompt.md
2. 任务卡：docs/task/v1.0.0-mvp/context-management/
3. 详细设计：docs/develop/v1.0.0-mvp/design/context-management/

现在从第一阶段开始，评估需求并识别需要澄清的问题。
```

### 阶段切换

```
# 进入编码阶段
"第一阶段已完成，需求100%明确。现在进入第二阶段，开始实现ManagedThreadContext。"

# 进入测试阶段  
"代码实现完成。进入第三阶段，开始编写并执行测试用例。"

# 进入交付阶段
"测试全部通过。进入第四阶段，生成交付报告。"
```

### 问题处理

```
# 遇到技术问题
"在实现[具体功能]时遇到问题：[问题描述]。请提供解决方案。"

# 性能未达标
"性能测试显示[指标]未达标，实测[数值]，目标[数值]。需要优化建议。"

# 测试失败
"测试用例[名称]失败，错误：[信息]。优先检查实现代码。"
```

---

## 质量保证检查点

### 代码质量门禁

```yaml
必须通过:
  - Maven构建成功
  - 单元测试全部通过
  - 代码覆盖率>95%
  - 无编译警告
  - 静态检查通过

建议达到:
  - 圈复杂度<10
  - 重复代码<5%
  - 注释率>30%
  - 方法行数<50
  - 类行数<500
```

### 评审检查清单

```markdown
- [ ] 需求实现完整性
- [ ] API设计合理性
- [ ] 异常处理充分性
- [ ] 资源管理正确性
- [ ] 并发安全性
- [ ] 性能目标达成
- [ ] 测试覆盖充分
- [ ] 文档更新完整
- [ ] 代码规范符合
- [ ] 可维护性良好
```

---

本提示词指南确保AI能够以专业开发工程师的标准完成Context-Management模块的开发工作。严格遵循四阶段流程，确保交付高质量的代码实现。