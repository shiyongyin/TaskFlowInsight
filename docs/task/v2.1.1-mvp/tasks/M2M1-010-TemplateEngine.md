# M2M1-010: 轻量模板引擎实现

## 任务概述

| 属性 | 值 |
|------|-----|
| 任务ID | M2M1-010 |
| 任务名称 | 轻量模板引擎实现 |
| 所属模块 | 格式化引擎 (Format Engine) |
| 优先级 | P0 |
| 预估工期 | S (2天) |
| 依赖任务 | M2M1-004 |

## 背景

TaskFlow Insight需要将差异结果格式化为可读的输出。实现轻量级模板引擎，支持简单的占位符替换，避免引入重量级模板框架，保持系统简洁高效。

## 目标

1. 实现`#[...]`占位符替换机制
2. 支持多种输出格式模板
3. 提供模板选择优先级机制
4. 实现变量转义和安全处理
5. 支持条件和循环的简单语法

## 非目标

- 不支持复杂表达式求值
- 不实现完整模板语言
- 不支持模板继承
- 不引入第三方模板引擎

## 实现要点

### 1. 模板引擎核心设计

```java
@Component
public class TemplateEngine {
    private final Map<String, Template> templates;
    
    public String render(String templateName, Map<String, Object> context) {
        Template template = templates.get(templateName);
        if (template == null) {
            template = Template.DEFAULT;
        }
        return template.render(context);
    }
}
```

### 2. 占位符语法定义

```
基本占位符：#[variable]
嵌套访问：#[user.name]
默认值：#[variable|defaultValue]
条件判断：#[if:condition]...#[endif]
循环：#[foreach:items]...#[endforeach]
```

### 3. 模板解析器

```java
public class TemplateParser {
    private static final Pattern PLACEHOLDER = 
        Pattern.compile("#\\[([^\\]]+)\\]");
    
    public List<Token> parse(String template) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        int lastEnd = 0;
        
        while (matcher.find()) {
            // 添加文本token
            if (matcher.start() > lastEnd) {
                tokens.add(new TextToken(
                    template.substring(lastEnd, matcher.start())
                ));
            }
            
            // 解析占位符
            String placeholder = matcher.group(1);
            tokens.add(parseP
laceholder(placeholder));
            lastEnd = matcher.end();
        }
        
        // 添加剩余文本
        if (lastEnd < template.length()) {
            tokens.add(new TextToken(template.substring(lastEnd)));
        }
        
        return tokens;
    }
}
```

### 4. 预定义模板

```java
public enum BuiltinTemplates {
    DIFF_TEXT("""
        Field: #[path]
        Type: #[type]
        Before: #[beforeKind](#[beforeRepr])
        After: #[afterKind](#[afterRepr])
        #[if:summary]Summary: #[summary]#[endif]
        """),
        
    DIFF_COMPACT("""
        #[type] #[path]: #[beforeRepr] -> #[afterRepr]
        """),
        
    DIFF_JSON("""
        {
          "path": "#[path]",
          "type": "#[type]",
          "before": {
            "kind": "#[beforeKind]",
            "value": "#[beforeRepr]"
          },
          "after": {
            "kind": "#[afterKind]",
            "value": "#[afterRepr]"
          }
        }
        """);
}
```

### 5. 变量解析和安全转义

```java
public class VariableResolver {
    private static final int MAX_PATH_DEPTH = 10;
    private static final int MAX_STRING_LENGTH = 10000;
    private static final Pattern SAFE_PATH = Pattern.compile("^[a-zA-Z0-9_\\.\\[\\]]+$");
    
    public Object resolve(String path, Map<String, Object> context) {
        // 路径安全验证
        if (!isPathSafe(path)) {
            throw new TemplateSecurityException("Unsafe path: " + path);
        }
        
        String[] parts = path.split("\\.");
        if (parts.length > MAX_PATH_DEPTH) {
            throw new TemplateSecurityException("Path too deep: " + path);
        }
        
        Object current = context;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current != null) {
                // 安全的反射访问，只允许public getter
                current = getPropertySafely(current, part);
            }
            
            if (current == null) {
                return handleNull(path);
            }
        }
        
        return escapeForContext(current);
    }
    
    private boolean isPathSafe(String path) {
        return path != null && 
               path.length() < 200 && 
               SAFE_PATH.matcher(path).matches();
    }
    
    private Object getPropertySafely(Object obj, String property) {
        try {
            // 只允许访问public getter方法
            String getterName = "get" + Character.toUpperCase(property.charAt(0)) + 
                               property.substring(1);
            Method getter = obj.getClass().getMethod(getterName);
            
            // 检查返回类型是否安全
            if (isUnsafeType(getter.getReturnType())) {
                return null;
            }
            
            return getter.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isUnsafeType(Class<?> type) {
        return type == Class.class || 
               type == ClassLoader.class ||
               type == Process.class ||
               type == ProcessBuilder.class ||
               type.getName().startsWith("java.lang.reflect");
    }
    
    private String escapeForContext(Object value) {
        if (value == null) return "";
        
        String str = value.toString();
        
        // 长度限制
        if (str.length() > MAX_STRING_LENGTH) {
            str = str.substring(0, MAX_STRING_LENGTH) + "...";
        }
        
        // 根据上下文进行不同的转义
        return escapeHtml(escapeJson(str));
    }
    
    private String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("'", "&#39;")
                   .replace("\"", "&quot;");
    }
    
    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("/", "\\/");  // 防止</script>注入
    }
}

// 模板编译缓存
@Component
public class TemplateCache {
    private final Cache<String, CompiledTemplate> cache;
    
    public TemplateCache() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();
    }
    
    public CompiledTemplate get(String template) {
        return cache.get(template, k -> compile(k));
    }
    
    private CompiledTemplate compile(String template) {
        // 预编译模板，提高性能
        List<Token> tokens = new TemplateParser().parse(template);
        return new CompiledTemplate(tokens);
    }
}
```

## 测试要求

### 单元测试

1. **基本功能测试**
   - 简单占位符替换
   - 嵌套属性访问
   - 默认值处理
   - 特殊字符转义

2. **条件和循环测试**
   - if条件判断
   - foreach循环
   - 嵌套结构

3. **边界条件测试**
   - 空模板
   - 无占位符模板
   - 未定义变量
   - null值处理

4. **性能测试**
   - 小模板渲染：P95 ≤ 0.1ms
   - 大模板(1KB)：P95 ≤ 1ms
   - 批量渲染(100个)：P95 ≤ 10ms

### 集成测试

1. 与DiffDetector集成
2. 多种输出格式测试
3. 真实数据渲染

## 验收标准

### 功能验收

- [ ] 占位符正确替换
- [ ] 嵌套属性可访问
- [ ] 条件和循环工作
- [ ] 转义机制完善
- [ ] 默认值生效

### 性能验收

- [ ] 渲染性能达标
- [ ] 内存占用合理
- [ ] 无内存泄漏

### 质量验收

- [ ] 单元测试覆盖率 > 85%
- [ ] 代码简洁清晰
- [ ] 文档完整

## 风险评估

### 技术风险

1. **R013: 模板注入攻击**
   - 缓解：严格转义 + 白名单
   - 验证：安全测试

2. **R014: 性能退化**
   - 缓解：模板预编译
   - 监控：渲染时间

3. **R015: 复杂度失控**
   - 缓解：功能限制（YAGNI）
   - 原则：保持简单

### 依赖风险

- 依赖M2M1-004的DiffResult格式

## 需要澄清

1. 是否需要支持数学运算
2. 模板缓存策略
3. 错误处理方式（静默/异常）

## 代码示例

### 使用示例

```java
// 准备数据
Map<String, Object> context = new HashMap<>();
context.put("path", "user.email");
context.put("type", "MODIFY");
context.put("beforeKind", "string");
context.put("beforeRepr", "old@example.com");
context.put("afterKind", "string");
context.put("afterRepr", "new@example.com");
context.put("summary", "Email changed");

// 渲染模板
TemplateEngine engine = new TemplateEngine();
String output = engine.render("diff.compact", context);
// 输出: MODIFY user.email: old@example.com -> new@example.com

// 使用详细模板
String detailed = engine.render("diff.text", context);
// 输出:
// Field: user.email
// Type: MODIFY
// Before: string(old@example.com)
// After: string(new@example.com)
// Summary: Email changed
```

### 配置类

```java
@ConfigurationProperties("tfi.template")
public class TemplateConfig {
    private String defaultFormat = "compact";    // 默认格式
    private boolean enableCache = true;          // 启用缓存
    private int cacheSize = 100;                // 缓存大小
    private boolean strictMode = false;         // 严格模式
    private Map<String, String> custom = new HashMap<>(); // 自定义模板
}
```

### 模板注册

```java
@Configuration
public class TemplateConfiguration {
    
    @Bean
    public TemplateRegistry templateRegistry(TemplateConfig config) {
        TemplateRegistry registry = new TemplateRegistry();
        
        // 注册内置模板
        registry.register("diff.text", BuiltinTemplates.DIFF_TEXT);
        registry.register("diff.compact", BuiltinTemplates.DIFF_COMPACT);
        registry.register("diff.json", BuiltinTemplates.DIFF_JSON);
        
        // 注册自定义模板
        config.getCustom().forEach((name, template) -> 
            registry.register(name, Template.parse(template))
        );
        
        return registry;
    }
}
```

## 实施计划

### Day 1: 核心实现
- 模板解析器
- 占位符替换
- 变量解析

### Day 2: 功能完善与测试
- 条件和循环支持
- 预定义模板
- 单元测试
- 性能测试

## 参考资料

1. 模板引擎设计模式
2. 字符串模板最佳实践
3. 安全转义规范

---

*文档版本*: v1.0.0  
*创建日期*: 2025-01-12  
*状态*: 待开发