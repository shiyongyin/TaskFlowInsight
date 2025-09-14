# VIP-017 Documentation 示例汇总（由正文迁移）

## Markdown 模板示例
```markdown
# 标题
- 要点1
- 要点2
```

```markdown
## 子标题
- 内容A
- 内容B
```

## 代码注释/示例
```java
/**
 * 导出当前会话为 JSON
 * @return JSON 字符串，失败返回 null
 */
public static String exportToJson() {
  try {
    Session s = getCurrentSession();
    return s != null ? new JsonExporter().export(s) : null;
  } catch (Exception e) {
    logger.error("Failed to export to JSON", e);
    return null;
  }
}
```

## 配置示例（YAML）
```yaml
docs:
  generator:
    enabled: false
    format: markdown   # markdown/html
  linter:
    enabled: true
    max-line-length: 120
```
