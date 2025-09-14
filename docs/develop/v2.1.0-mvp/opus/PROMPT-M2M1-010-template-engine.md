# PROMPT-M2M1-010-TemplateEngine 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/format-engine/M2M1-010-TemplateEngine.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/diff#Change（数据源）
  - src/main/java/com/syy/taskflowinsight/exporter#现有导出器
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.template.enabled
  - src/main/resources/application.yml: tfi.change-tracking.template.mode
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供轻量模板化的变更集文本输出（非 JSON），便于控制台/日志阅读
- 技术目标：
  - 实现最小模板引擎（占位符替换）
  - 支持 default/compact 两种模板
  - 安全转义敏感字符
  - 性能控制在 20ms 内

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.format.template.TemplateEngine
  - [ ] 创建 com.syy.taskflowinsight.format.template.TemplateOption
  - [ ] 实现占位符替换：${path}/${kind}/${reprOld}/${reprNew}
  - [ ] 实现 default 模板（详细格式）
  - [ ] 实现 compact 模板（紧凑格式）
  - [ ] 实现安全转义（HTML/XML/SQL）
  - [ ] 实现长度截断（单值 < 1000 字符）
- Out of Scope（排除项）：
  - [ ] 复杂模板语法（循环、条件）
  - [ ] 插件系统
  - [ ] 用户自定义模板

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.format.template.TemplateEngine
   - 新建：com.syy.taskflowinsight.format.template.TemplateOption
   - 新建：com.syy.taskflowinsight.format.template.Template
   - 配置：application.yml 新增 template 配置

2. 给出重构/新建的**类与方法签名**：
```java
// TemplateEngine.java
public class TemplateEngine {
    private static final String DEFAULT_TEMPLATE = """
        === Change Summary ===
        Total changes: ${count}
        ${changes}
        - [${kind}] ${path}: ${reprOld} -> ${reprNew}
        ${/changes}
        """;
    
    private static final String COMPACT_TEMPLATE = "${path}: ${reprOld}→${reprNew}";
    
    public String render(List<Change> changes, TemplateOption option);
    private String renderSingle(Change change, String template);
    private String replacePlaceholders(String template, Map<String, Object> context);
    private String escape(String value, EscapeMode mode);
    private String truncate(String value, int maxLength);
}

// TemplateOption.java
@Data
public class TemplateOption {
    private TemplateMode mode = TemplateMode.DEFAULT; // DEFAULT, COMPACT
    private boolean escapeHtml = true;
    private boolean escapeXml = false;
    private boolean escapeSql = true;
    private int maxValueLength = 1000;
    private boolean colorize = false; // 终端颜色支持
}

// Template.java
public class Template {
    private final String content;
    private final List<Placeholder> placeholders;
    
    public Template(String content) {
        this.content = content;
        this.placeholders = parsePlaceholders(content);
    }
    
    public String apply(Map<String, Object> context);
    private List<Placeholder> parsePlaceholders(String content);
}
```

3. 逐步实现：
   - 模板解析器（占位符识别）
   - 上下文构建（Change → Map）
   - 占位符替换引擎
   - 安全转义处理
   - 性能优化（缓存编译后的模板）

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：TemplateEngine.java, TemplateOption.java, Template.java
  - 新文件：EscapeUtils.java（转义工具）
  - 测试文件：TemplateEngineTest.java
- 测试：
  - 单测：占位符替换、转义、截断
  - 性能测试：100条/1000条渲染时间
- 文档：README 添加模板语法说明
- 配置：默认模板配置

## 7) API & MODELS（必须具体化）
- 接口签名：
```java
public String render(List<Change> changes, TemplateOption option)
```
- 占位符语法：
  - `${path}` - 变更路径
  - `${kind}` - 值类型
  - `${reprOld}` - 旧值表示
  - `${reprNew}` - 新值表示
  - `${count}` - 变更总数
- 输出示例（default）：
```
=== Change Summary ===
Total changes: 3
- [STRING] user.name: Alice -> Bob
- [NUMBER] user.age: 25 -> 26
- [BOOLEAN] user.active: true -> false
```
- 输出示例（compact）：
```
user.name: Alice→Bob
user.age: 25→26
user.active: true→false
```

## 8) DATA & STORAGE
- 模板缓存：内存中缓存编译后的模板对象
- 无持久化需求

## 9) PERFORMANCE & RELIABILITY
- 性能预算：
  - 中等规模（100条）< 20ms
  - 大规模（1000条）< 200ms
- 内存控制：单次渲染 < 1MB
- 失败处理：转义失败时保留原值
- 安全措施：
  - HTML 实体转义：<>&"'
  - SQL 关键词检测：DROP, DELETE, UPDATE
  - 长度截断：防止内存溢出

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 85%
  - [ ] 占位符替换测试
    - 单个占位符
    - 多个占位符
    - 嵌套占位符
    - 未知占位符处理
  - [ ] 转义测试
    - HTML 转义：<script>alert()</script>
    - SQL 注入防护：'; DROP TABLE--
    - XML 实体转义
  - [ ] 截断测试
    - 边界值：999, 1000, 1001 字符
  - [ ] 模板切换测试
- 性能测试：
  - [ ] JMH 基准测试
  - [ ] 100条变更 < 20ms
  - [ ] 1000条变更 < 200ms
- 集成测试：
  - [ ] 与 Change 对象集成
  - [ ] 控制台输出验证

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：模板渲染正确
- [ ] 性能：满足时间要求
- [ ] 安全：转义机制有效
- [ ] 文档：模板语法清晰
- [ ] 配置：可切换模板模式

## 12) RISKS & MITIGATIONS
- 性能风险：大量变更渲染慢 → 批处理 + 缓存模板
- 安全风险：注入攻击 → 严格转义 + 白名单
- 内存风险：超长值 → 强制截断
- 兼容性：不影响 JSON 导出 → 独立模块

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议将模板定义外部化到配置文件，便于修改

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：是否需要支持终端颜色输出？
  - 责任人：产品组
  - 期限：实现前确认
  - 所需：颜色方案（ADD=绿、DELETE=红、MODIFY=黄）
- [ ] 问题2：是否需要按变化类型分组输出？
  - 责任人：产品组
  - 期限：实现前确认
  - 所需：分组规则