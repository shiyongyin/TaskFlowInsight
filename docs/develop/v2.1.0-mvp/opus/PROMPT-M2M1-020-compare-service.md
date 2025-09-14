# PROMPT-M2M1-020-CompareService 开发提示词

## 1) SYSTEM
你是**资深 Java 开发工程师**与**AI 结对编程引导者**。你需要基于下述"上下文与证据"，**按步骤**完成实现并给出**可提交的变更**（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../task/v2.1.0-vip/compare-strategy/M2M1-020-CompareService.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/diff#DiffDetector（消费方）
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot#快照结果（输入源）
- 相关配置：
  - src/main/resources/application.yml: tfi.change-tracking.compare.tolerance
  - src/main/resources/application.yml: tfi.change-tracking.compare.normalize-enabled
  - src/main/resources/application.yml: tfi.change-tracking.compare.identity-paths
- 工程操作规范：../../develop/开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：在 Diff 前统一值的"可比较表示"，降低无谓差异，提高结果稳定性
- 技术目标：
  - 实现 pre-normalize 预规范化服务
  - 数值容差比较（默认 0）
  - 时间统一 UTC/ISO-8601
  - 字符串 trim + lowercase
  - identity-paths 短路优化

## 4) SCOPE
- In Scope（当次实现必做）：
  - [ ] 创建 com.syy.taskflowinsight.tracking.compare.CompareService
  - [ ] 创建 com.syy.taskflowinsight.tracking.compare.CompareContext
  - [ ] 创建 com.syy.taskflowinsight.tracking.compare.NormalizationRules
  - [ ] 实现数值容差比较（绝对容差）
  - [ ] 实现时间规范化（UTC/ISO-8601）
  - [ ] 实现字符串规范化（trim/lowercase）
  - [ ] 实现 identity-paths 快速判定
  - [ ] 集成到 DiffDetector 前置处理
- Out of Scope（排除项）：
  - [ ] 复杂规则引擎
  - [ ] 路径级大量定制
  - [ ] 相对容差比较

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 列出受影响模块与文件：
   - 新建：com.syy.taskflowinsight.tracking.compare.CompareService
   - 新建：com.syy.taskflowinsight.tracking.compare.CompareContext
   - 新建：com.syy.taskflowinsight.tracking.compare.NormalizationRules
   - 新建：com.syy.taskflowinsight.tracking.compare.CompareConfig
   - 修改：DiffDetector（调用 CompareService）

2. 给出重构/新建的**类与方法签名**：
```java
// CompareService.java
@Service
public class CompareService {
    private final NormalizationRules rules;
    private final Set<String> identityPaths;
    
    public int compare(String path, Object a, Object b, CompareContext context);
    public Object normalize(Object value, String path, CompareContext context);
    private boolean isIdentityPath(String path);
    private int compareNumbers(Number a, Number b, double tolerance);
    private int compareDates(Object a, Object b);
    private int compareStrings(String a, String b, boolean ignoreCase, boolean trim);
}

// CompareContext.java
@Data
public class CompareContext {
    private double tolerance = 0.0;          // 数值容差
    private boolean normalizeEnabled = true;  // 启用规范化
    private boolean trimStrings = true;       // 字符串去空格
    private boolean lowercaseStrings = true;  // 字符串小写
    private boolean utcDates = true;          // 时间转UTC
    private Map<String, Object> metadata;     // 扩展元数据
}

// NormalizationRules.java
public class NormalizationRules {
    // 数值规范化
    public Number normalizeNumber(Number value, double tolerance) {
        if (tolerance > 0 && value instanceof Double) {
            return Math.round(value.doubleValue() / tolerance) * tolerance;
        }
        return value;
    }
    
    // 时间规范化
    public String normalizeDate(Object date) {
        if (date instanceof Date) {
            return ISO_INSTANT.format(((Date) date).toInstant());
        }
        if (date instanceof LocalDateTime) {
            return ((LocalDateTime) date).atZone(UTC).format(ISO_INSTANT);
        }
        return date.toString();
    }
    
    // 字符串规范化
    public String normalizeString(String value, boolean trim, boolean lowercase) {
        String result = value;
        if (trim) result = result.trim();
        if (lowercase) result = result.toLowerCase();
        return result;
    }
}

// CompareConfig.java
@ConfigurationProperties(prefix = "tfi.change-tracking.compare")
@Data
public class CompareConfig {
    private double tolerance = 0.0;
    private boolean normalizeEnabled = true;
    private Set<String> identityPaths = Set.of("**/id", "**/uuid", "**/key");
    private StringConfig string = new StringConfig();
    private DateConfig date = new DateConfig();
    
    @Data
    public static class StringConfig {
        private boolean trim = true;
        private boolean lowercase = true;
    }
    
    @Data
    public static class DateConfig {
        private boolean utc = true;
        private String format = "ISO-8601";
    }
}
```

3. 逐步实现：
   - 规范化规则实现
   - 比较逻辑实现
   - identity-paths 匹配器
   - DiffDetector 集成

## 6) DELIVERABLES（输出必须包含）
- 代码改动：
  - 新文件：CompareService.java, CompareContext.java, NormalizationRules.java, CompareConfig.java
  - 变更文件：DiffDetector.java（添加 pre-normalize 调用）
  - 测试文件：CompareServiceTest.java
- 测试：
  - 单测：各类型规范化测试
  - 集成测试：与 DiffDetector 集成
- 文档：规范化规则说明
- 配置：默认配置值

## 7) API & MODELS（必须具体化）
- 接口签名：
```java
public int compare(String path, Object a, Object b, CompareContext context)
// 返回值：0 相等，非0 不等
```
- 规范化规则：
  - 数值：绝对容差范围内视为相等
  - 时间：统一转换为 UTC ISO-8601 格式
  - 字符串：trim() + toLowerCase()
  - identity-paths：直接比较，不规范化

## 8) DATA & STORAGE
- 无持久化需求
- identity-paths 缓存在内存 Set 中

## 9) PERFORMANCE & RELIABILITY
- 性能预算：单次比较 < 0.1ms
- 规范化开销：< 5% 额外 CPU
- 缓存策略：identity-paths 预编译
- 失败处理：规范化失败时使用原值

## 10) TEST PLAN（可运行、可断言）
- 单元测试：
  - [ ] 覆盖率 ≥ 85%
  - [ ] 数值容差测试
    - tolerance=0: 1.0 vs 1.0 (相等)
    - tolerance=0: 1.0 vs 1.1 (不等)
    - tolerance=0.1: 1.0 vs 1.05 (相等)
    - tolerance=0.1: 1.0 vs 1.11 (不等)
  - [ ] 时间规范化测试
    - Date → ISO-8601
    - LocalDateTime → UTC ISO-8601
    - 时区转换正确性
  - [ ] 字符串规范化测试
    - " ABC " → "abc" (trim + lowercase)
    - null 值处理
  - [ ] identity-paths 测试
    - user.id 匹配 **/id
    - order.items[0].uuid 匹配 **/uuid
- 集成测试：
  - [ ] DiffDetector 集成场景
  - [ ] 规范化前后对比
- 性能测试：
  - [ ] 10000次比较 < 1秒

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：规范化规则正确
- [ ] 性能：不超过 5% 开销
- [ ] 配置：可开关规范化
- [ ] 兼容：默认不改变行为
- [ ] 文档：规则说明清晰

## 12) RISKS & MITIGATIONS
- 性能风险：规范化开销大 → 缓存 + 懒加载
- 兼容性风险：破坏现有比较逻辑 → 默认 tolerance=0，可关闭
- 精度风险：浮点数比较 → 使用 BigDecimal 或 epsilon 比较

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 建议将 CompareService 设计为策略模式，便于扩展规则

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 问题1：identity-paths 默认集合的完整列表？
  - 责任人：架构组
  - 期限：实现前确认
  - 所需：常用 ID 字段路径模式
- [ ] 问题2：是否需要支持相对容差（百分比）？
  - 责任人：产品组
  - 期限：设计阶段确认
  - 所需：使用场景说明