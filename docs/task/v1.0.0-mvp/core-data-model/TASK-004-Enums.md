# TASK-004: 枚举定义实现

## 任务背景

TaskFlow Insight系统需要多个枚举类型来定义系统中的各种状态、类型和配置选项。这些枚举提供了类型安全的常量定义，确保系统中使用的状态值是预定义和有效的。枚举的设计需要考虑扩展性、可读性和性能，同时要为后续的状态转换和验证提供支持。

## 目标

1. 实现SessionStatus会话状态枚举，定义会话的生命周期状态
2. 实现TaskStatus任务状态枚举，定义任务节点的执行状态  
3. 实现SystemStatus系统状态枚举，定义TFI系统的运行状态
4. 实现ExportFormat导出格式枚举，定义支持的数据导出格式
5. 提供状态转换验证和枚举工具方法
6. 确保所有枚举具有良好的可读性和扩展性

## 实现方案

### 4.1 SessionStatus会话状态枚举

```java
package com.syy.taskflowinsight.enums;

/**
 * 会话状态枚举
 * 定义Session对象的生命周期状态
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public enum SessionStatus {
    
    /**
     * 初始化状态 - 会话已创建但尚未开始执行任务
     */
    INITIALIZED("INITIALIZED", "初始化", 0),
    
    /**
     * 运行中状态 - 会话正在执行任务
     */
    RUNNING("RUNNING", "运行中", 1),
    
    /**
     * 已完成状态 - 会话正常完成所有任务
     */
    COMPLETED("COMPLETED", "已完成", 2),
    
    /**
     * 异常终止状态 - 会话因异常而终止
     */
    ABORTED("ABORTED", "异常终止", 3),
    
    /**
     * 超时状态 - 会话执行超时
     */
    TIMEOUT("TIMEOUT", "超时", 4);
    
    private final String code;
    private final String description;
    private final int order;
    
    SessionStatus(String code, String description, int order) {
        this.code = code;
        this.description = description;
        this.order = order;
    }
    
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getOrder() { return order; }
    
    /**
     * 判断是否为活跃状态（可以继续执行任务）
     * 
     * @return true if active
     */
    public boolean isActive() {
        return this == INITIALIZED || this == RUNNING;
    }
    
    /**
     * 判断是否为终止状态（不能继续执行任务）
     * 
     * @return true if terminated
     */
    public boolean isTerminated() {
        return this == COMPLETED || this == ABORTED || this == TIMEOUT;
    }
    
    /**
     * 判断是否可以转换到目标状态
     * 
     * @param target 目标状态
     * @return true if transition is valid
     */
    public boolean canTransitionTo(SessionStatus target) {
        switch (this) {
            case INITIALIZED:
                return target == RUNNING || target == ABORTED;
            case RUNNING:
                return target == COMPLETED || target == ABORTED || target == TIMEOUT;
            case COMPLETED:
            case ABORTED:
            case TIMEOUT:
                return false; // 终止状态不能转换
            default:
                return false;
        }
    }
    
    /**
     * 获取下一个可能的状态列表
     * 
     * @return 可转换的状态列表
     */
    public SessionStatus[] getNextPossibleStates() {
        switch (this) {
            case INITIALIZED:
                return new SessionStatus[]{RUNNING, ABORTED};
            case RUNNING:
                return new SessionStatus[]{COMPLETED, ABORTED, TIMEOUT};
            default:
                return new SessionStatus[0];
        }
    }
}
```

### 4.2 TaskStatus任务状态枚举

```java
package com.syy.taskflowinsight.enums;

/**
 * 任务状态枚举
 * 定义TaskNode的执行状态
 * 
 * @author TaskFlow Insight Team  
 * @version 1.0.0
 */
public enum TaskStatus {
    
    /**
     * 待开始状态 - 任务已创建但尚未开始执行
     */
    PENDING("PENDING", "待开始", 0),
    
    /**
     * 执行中状态 - 任务正在执行
     */
    RUNNING("RUNNING", "执行中", 1),
    
    /**
     * 已完成状态 - 任务正常完成
     */
    COMPLETED("COMPLETED", "已完成", 2),
    
    /**
     * 执行失败状态 - 任务执行过程中发生错误
     */
    FAILED("FAILED", "执行失败", 3),
    
    /**
     * 已取消状态 - 任务被主动取消
     */
    CANCELLED("CANCELLED", "已取消", 4),
    
    /**
     * 超时状态 - 任务执行超时
     */
    TIMEOUT("TIMEOUT", "超时", 5),
    
    /**
     * 暂停状态 - 任务被暂停（保留状态，暂不实现）
     */
    PAUSED("PAUSED", "暂停", 6);
    
    private final String code;
    private final String description;
    private final int order;
    
    TaskStatus(String code, String description, int order) {
        this.code = code;
        this.description = description;
        this.order = order;
    }
    
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getOrder() { return order; }
    
    /**
     * 判断是否为活跃状态
     * 
     * @return true if active
     */
    public boolean isActive() {
        return this == PENDING || this == RUNNING || this == PAUSED;
    }
    
    /**
     * 判断是否为终止状态
     * 
     * @return true if terminated
     */
    public boolean isTerminated() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }
    
    /**
     * 判断是否为成功状态
     * 
     * @return true if successful
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * 判断是否为失败状态
     * 
     * @return true if failed
     */
    public boolean isFailed() {
        return this == FAILED || this == TIMEOUT || this == CANCELLED;
    }
    
    /**
     * 验证状态转换是否有效
     * 
     * @param target 目标状态
     * @return true if transition is valid
     */
    public boolean canTransitionTo(TaskStatus target) {
        switch (this) {
            case PENDING:
                return target == RUNNING || target == CANCELLED;
            case RUNNING:
                return target == COMPLETED || target == FAILED || target == TIMEOUT || target == CANCELLED;
            case PAUSED:
                return target == RUNNING || target == CANCELLED;
            case COMPLETED:
            case FAILED:
            case CANCELLED:
            case TIMEOUT:
                return false; // 终止状态不能转换
            default:
                return false;
        }
    }
}
```

### 4.3 SystemStatus系统状态枚举

```java
package com.syy.taskflowinsight.enums;

/**
 * 系统状态枚举
 * 定义TFI系统的运行状态
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public enum SystemStatus {
    
    /**
     * 未初始化状态 - 系统尚未初始化
     */
    UNINITIALIZED("UNINITIALIZED", "未初始化", 0),
    
    /**
     * 已禁用状态 - 系统功能被禁用
     */
    DISABLED("DISABLED", "已禁用", 1),
    
    /**
     * 已启用状态 - 系统功能已启用，可以接受任务
     */
    ENABLED("ENABLED", "已启用", 2),
    
    /**
     * 维护模式 - 系统处于维护状态，拒绝新任务
     */
    MAINTENANCE("MAINTENANCE", "维护模式", 3),
    
    /**
     * 错误状态 - 系统发生严重错误
     */
    ERROR("ERROR", "错误状态", 4);
    
    private final String code;
    private final String description;
    private final int priority;
    
    SystemStatus(String code, String description, int priority) {
        this.code = code;
        this.description = description;
        this.priority = priority;
    }
    
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getPriority() { return priority; }
    
    /**
     * 判断系统是否可以接受新任务
     * 
     * @return true if can accept tasks
     */
    public boolean canAcceptTasks() {
        return this == ENABLED;
    }
    
    /**
     * 判断系统是否处于正常工作状态
     * 
     * @return true if working normally
     */
    public boolean isWorking() {
        return this == ENABLED || this == MAINTENANCE;
    }
    
    /**
     * 判断系统是否需要干预
     * 
     * @return true if needs intervention
     */
    public boolean needsIntervention() {
        return this == ERROR || this == UNINITIALIZED;
    }
}
```

### 4.4 ExportFormat导出格式枚举

```java
package com.syy.taskflowinsight.enums;

/**
 * 导出格式枚举
 * 定义支持的数据导出格式
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public enum ExportFormat {
    
    /**
     * JSON格式 - 结构化JSON数据
     */
    JSON("JSON", "application/json", ".json", true),
    
    /**
     * XML格式 - XML结构化数据
     */
    XML("XML", "application/xml", ".xml", true),
    
    /**
     * CSV格式 - 逗号分隔值（扁平化数据）
     */
    CSV("CSV", "text/csv", ".csv", false),
    
    /**
     * TXT格式 - 纯文本格式（人类可读）
     */
    TEXT("TEXT", "text/plain", ".txt", false),
    
    /**
     * YAML格式 - YAML结构化数据
     */
    YAML("YAML", "application/x-yaml", ".yml", true);
    
    private final String name;
    private final String mimeType;
    private final String fileExtension;
    private final boolean supportsHierarchy;
    
    ExportFormat(String name, String mimeType, String fileExtension, boolean supportsHierarchy) {
        this.name = name;
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.supportsHierarchy = supportsHierarchy;
    }
    
    public String getName() { return name; }
    public String getMimeType() { return mimeType; }
    public String getFileExtension() { return fileExtension; }
    public boolean supportsHierarchy() { return supportsHierarchy; }
    
    /**
     * 根据文件扩展名获取格式
     * 
     * @param extension 文件扩展名
     * @return 对应的格式，如果不支持则返回null
     */
    public static ExportFormat fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        
        String ext = extension.toLowerCase();
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        
        for (ExportFormat format : values()) {
            if (format.fileExtension.equals(ext)) {
                return format;
            }
        }
        return null;
    }
    
    /**
     * 根据MIME类型获取格式
     * 
     * @param mimeType MIME类型
     * @return 对应的格式，如果不支持则返回null
     */
    public static ExportFormat fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }
        
        for (ExportFormat format : values()) {
            if (format.mimeType.equalsIgnoreCase(mimeType)) {
                return format;
            }
        }
        return null;
    }
    
    /**
     * 获取支持层次结构的格式列表
     * 
     * @return 支持层次结构的格式数组
     */
    public static ExportFormat[] getHierarchicalFormats() {
        return java.util.Arrays.stream(values())
                .filter(ExportFormat::supportsHierarchy)
                .toArray(ExportFormat[]::new);
    }
}
```

### 4.5 枚举工具类

```java
package com.syy.taskflowinsight.enums;

import java.util.*;

/**
 * 枚举工具类
 * 提供枚举相关的通用工具方法
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class EnumUtils {
    
    private EnumUtils() {
        // 工具类不允许实例化
    }
    
    /**
     * 安全地从字符串转换为枚举值
     * 
     * @param enumClass 枚举类
     * @param value 字符串值
     * @param defaultValue 默认值
     * @param <T> 枚举类型
     * @return 枚举值
     */
    public static <T extends Enum<T>> T safeValueOf(Class<T> enumClass, String value, T defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取枚举所有值的字符串表示
     * 
     * @param enumClass 枚举类
     * @param <T> 枚举类型
     * @return 所有枚举值的字符串列表
     */
    public static <T extends Enum<T>> List<String> getAllValues(Class<T> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 验证字符串是否为有效的枚举值
     * 
     * @param enumClass 枚举类
     * @param value 要验证的值
     * @param <T> 枚举类型
     * @return true if valid
     */
    public static <T extends Enum<T>> boolean isValidValue(Class<T> enumClass, String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        return Arrays.stream(enumClass.getEnumConstants())
                .anyMatch(e -> e.name().equalsIgnoreCase(value.trim()));
    }
    
    /**
     * 根据描述查找枚举值（适用于有getDescription方法的枚举）
     * 
     * @param values 枚举值数组
     * @param description 描述文本
     * @param <T> 枚举类型
     * @return 匹配的枚举值，未找到返回null
     */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> T findByDescription(T[] values, String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        
        return Arrays.stream(values)
                .filter(e -> {
                    try {
                        // 使用反射调用getDescription方法
                        return e.getClass()
                                .getMethod("getDescription")
                                .invoke(e)
                                .equals(description);
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 创建枚举值到描述的映射
     * 
     * @param values 枚举值数组
     * @param <T> 枚举类型
     * @return 映射表
     */
    public static <T extends Enum<T>> Map<T, String> createDescriptionMap(T[] values) {
        Map<T, String> map = new EnumMap<>((Class<T>) values[0].getClass());
        
        for (T value : values) {
            try {
                String description = (String) value.getClass()
                        .getMethod("getDescription")
                        .invoke(value);
                map.put(value, description);
            } catch (Exception e) {
                map.put(value, value.name());
            }
        }
        
        return map;
    }
}
```

## 测试标准

### 4.1 功能测试要求

1. **枚举值完整性测试**
   - 验证所有必要的枚举值都已定义
   - 验证枚举值的名称和描述正确
   - 验证枚举值的顺序和优先级

2. **状态转换测试**  
   - 验证SessionStatus状态转换逻辑
   - 验证TaskStatus状态转换逻辑
   - 验证非法转换被正确拒绝

3. **枚举工具方法测试**
   - 验证字符串到枚举的安全转换
   - 验证枚举查找和验证方法
   - 验证描述映射创建功能

4. **导出格式测试**
   - 验证文件扩展名匹配功能
   - 验证MIME类型匹配功能
   - 验证层次结构支持判断

### 4.2 边界测试要求

1. **空值处理**
   - 所有方法正确处理null输入
   - 空字符串处理正确
   - 无效值处理得当

2. **大小写敏感性**
   - 字符串匹配忽略大小写
   - 枚举名称保持原样
   - 描述文本区分大小写

## 验收标准

### 5.1 功能验收

- [ ] 所有枚举类型正确定义，包含必要的属性和方法
- [ ] 状态转换逻辑正确实现，符合业务规则
- [ ] 枚举工具类提供完整的辅助功能
- [ ] ExportFormat支持完整的格式处理功能
- [ ] 所有枚举具有良好的可读性和扩展性

### 5.2 质量验收

- [ ] 枚举值命名清晰，符合约定
- [ ] 枚举方法实现正确，无逻辑错误
- [ ] 工具类方法健壮，处理各种边界情况
- [ ] 代码注释完整，包含使用示例

### 5.3 性能验收

- [ ] 枚举操作性能满足要求（< 1微秒）
- [ ] 状态转换验证性能良好（< 0.5微秒）
- [ ] 字符串查找性能acceptable（< 10微秒）

### 5.4 测试验收

- [ ] 单元测试覆盖率 ≥ 90%
- [ ] 所有枚举值和方法都有测试覆盖
- [ ] 边界情况测试完整
- [ ] 状态转换测试覆盖所有路径

## 依赖关系

- **前置依赖**: 无
- **后置依赖**: TASK-001, TASK-002, TASK-003 (所有数据模型都依赖这些枚举)
- **相关任务**: 所有涉及状态管理的任务

## 预计工期

- **开发时间**: 1天
- **测试时间**: 0.5天
- **总计**: 1.5天

## 风险识别

1. **扩展性风险**: 后期需要添加新的枚举值
   - **缓解措施**: 设计时预留扩展空间，避免破坏性变更

2. **兼容性风险**: 枚举值变更可能影响序列化兼容性
   - **缓解措施**: 使用稳定的标识符，避免修改现有值