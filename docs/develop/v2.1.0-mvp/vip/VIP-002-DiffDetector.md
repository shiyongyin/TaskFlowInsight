# VIP-002-DiffDetector（合并版）

## 1. 概述
- 主题：差异检测器扩展
- 源卡：
  - GPT: `../../gpt/PROMPT-CARD-202-diffdetector-scalar.md`
  - GPT测试: `../../gpt/PROMPT-CARD-260-unit-diffdetector-scalar.md`
  - OPUS: `../../opus/PROMPT-M2M1-004-diff-detector-extension.md`
- 相关代码：
  - `src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java#diff()`
  - `src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java`
  - `src/main/java/com/syy/taskflowinsight/tracking/ChangeType.java`

## 2. 相同点（达成共识）
- 核心功能：对比before/after快照，生成变更记录
- 字段并集遍历：检测CREATE/DELETE/UPDATE
- Date归一化：统一使用毫秒时间戳比较
- 结果排序：按字段名字典序排序
- 值表示：通过repr()方法生成字符串表示

## 3. 差异与歧义

### 差异#1：数据模型选择
- **影响**：API兼容性和扩展性
- **GPT方案**：使用现有ChangeRecord模型
- **OPUS方案**：新建Change模型，包含valueKind/reprOld/reprNew/raw字段
- **建议取舍**：保留现有ChangeRecord，通过扩展字段实现增强
- **理由**：避免破坏现有API，渐进式演进
- **回滚与开关**：`tfi.change-tracking.diff.enhanced-mode=false`

### 差异#2：值类型分类
- **影响**：输出格式和可读性
- **GPT方案**：valueKind分为STRING/NUMBER/BOOLEAN/DATE/ENUM/OTHER
- **OPUS方案**：增加NULL/COLLECTION/MAP/OBJECT
- **建议取舍**：采用OPUS的完整分类，但初期只实现标量类型
- **理由**：为未来扩展预留空间

### 差异#3：DELETE场景处理
- **影响**：下游消费者兼容性
- **GPT方案**：DELETE时valueRepr=null
- **OPUS方案**：保留reprOld字段
- **建议取舍**：compat模式用null，enhanced模式保留reprOld
- **理由**：兼容现有行为，同时提供更多信息

## 4. 最终设计（融合后）

### 接口与契约

示例已迁移：见 `snippets/VIP-002-DiffDetector-EXAMPLES.md`


### 配置键

示例已迁移：见 `snippets/VIP-002-DiffDetector-EXAMPLES.md`


### 值类型分类（ValueKind）

示例已迁移：见 `snippets/VIP-002-DiffDetector-EXAMPLES.md`


## 5. 与代码的对齐与改造清单

### 变更点
- `DiffDetector.java#diff()` → 增加模式参数重载方法
- `ChangeRecord.java` → 添加可选字段（valueKind/reprOld/reprNew）
- 新增：`ValueKind.java` 枚举类
- 新增：`DiffMode.java` 枚举类

### 不改动项
- 保持现有`diff()`方法签名不变（默认compat模式）
- 现有的normalize/detectChangeType逻辑保持不变
- 上下文获取逻辑（ManagedThreadContext）保持不变

## 6. 测试计划

### 单元测试
- **基础功能测试**：
  - null值处理
  - 类型变化检测
  - 相等/不等判断
  - Date归一化验证
- **排序测试**：
  - 字典序稳定性
  - 空字段处理
  - 特殊字符排序
- **模式切换测试**：
  - compat模式输出验证
  - enhanced模式额外字段验证
  - DELETE场景valueRepr处理

### 集成测试
- 场景：ChangeTracker -> ObjectSnapshot -> DiffDetector
- 验证：track() -> 修改对象 -> getChanges()
- 期望：变更记录正确、排序稳定、格式一致

### 性能测试

示例已迁移：见 `snippets/VIP-002-DiffDetector-EXAMPLES.md`


## 7. 验收与回滚

### 验收清单
- [x] 标量类型diff正确
- [x] Date归一化正确
- [x] 排序稳定
- [x] DELETE场景处理正确
- [ ] 模式切换正常
- [ ] 性能达标

### 回滚方案
1. 配置切换：`tfi.change-tracking.diff.output-mode=compat`
2. 代码回滚：恢复到仅使用原diff()方法
3. 验证步骤：
   - 运行回归测试
   - 检查下游消费者
   - 确认输出格式

## 8. 差异与建议（文档 vs 代码冲突）
- 现有代码已实现基础diff功能，建议保持向后兼容
- valueRepr生成逻辑已存在（ObjectSnapshot.repr），复用即可
- 上下文获取（sessionId/taskPath）已实现，无需修改

## 9. 开放问题 / 行动项
- [ ] **问题**：是否需要在UPDATE场景同时输出oldRepr和newRepr？
  - 责任人：产品确认
  - 截止：本迭代末
  - 所需：用户场景分析

- [ ] **问题**：集合/Map类型diff何时支持？
  - 责任人：架构评审
  - 截止：v2.2规划
  - 所需：性能影响评估

- [ ] **行动**：性能基准测试
  - 责任人：测试团队
  - 截止：发布前
  - 所需：JMH测试脚本

## 10. 实施计划

### Phase 1：兼容性保证（本周）
1. 保持现有diff()方法不变
2. 添加diffWithMode()重载方法
3. 默认使用compat模式

### Phase 2：增强功能（下周）
1. 实现ValueKind枚举
2. 扩展ChangeRecord字段
3. 实现enhanced模式

### Phase 3：测试完善（第三周）
1. 补充单元测试矩阵
2. 性能测试和优化
3. 集成测试验证

### Phase 4：文档和发布（第四周）
1. 更新API文档
2. 迁移指南
3. 发布说明

---
*生成时间：2024-01-12*
*版本：v2.1.0-MVP*
