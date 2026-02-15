package com.syy.taskflowinsight.config.resolver;

/**
 * 统一配置默认值
 * 
 * 所有默认值集中管理，确保一致性
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public final class ConfigDefaults {
    
    private ConfigDefaults() {
        // 工具类不允许实例化
    }
    
    // ==================== 深度与性能 ====================
    
    /** 最大遍历深度 - 统一为10 */
    public static final int MAX_DEPTH = 10;
    
    /** 时间预算（毫秒） - 统一为1000ms */
    public static final long TIME_BUDGET_MS = 1000L;
    
    /** 慢操作阈值（毫秒） - 保持200ms */
    public static final long SLOW_OPERATION_MS = 200L;
    
    /** 最大栈深度 - 防止栈溢出 */
    public static final int MAX_STACK_DEPTH = 1000;
    
    // ==================== 集合与降级 ====================
    
    /** 列表大小阈值 - 触发降级 */
    public static final int LIST_SIZE_THRESHOLD = 500;
    
    /** K对数阈值 - 触发降级 */
    public static final int K_PAIRS_THRESHOLD = 10000;
    
    /** 集合摘要阈值 - 触发摘要模式 */
    public static final int COLLECTION_SUMMARY_THRESHOLD = 100;
    
    /** 摘要最大示例数 */
    public static final int SUMMARY_MAX_EXAMPLES = 10;
    
    // ==================== 缓存与清理 ====================
    
    /** 值表示最大长度 */
    public static final int VALUE_REPR_MAX_LENGTH = 8192;
    
    /** 清理间隔（分钟） */
    public static final int CLEANUP_INTERVAL_MINUTES = 5;
    
    /** 最大缓存类数量 */
    public static final int MAX_CACHED_CLASSES = 1024;
    
    /** 上下文最大存活时间（毫秒） */
    public static final long MAX_CONTEXT_AGE_MILLIS = 3600000L; // 1小时
    
    // ==================== 降级阈值 ====================
    
    /** 内存阈值 - 跳过深度分析 */
    public static final double MEMORY_THRESHOLD_SKIP_DEEP = 60.0;
    
    /** 内存阈值 - 简单比较 */
    public static final double MEMORY_THRESHOLD_SIMPLE = 70.0;
    
    /** 内存阈值 - 仅摘要 */
    public static final double MEMORY_THRESHOLD_SUMMARY = 80.0;
    
    /** 内存阈值 - 禁用 */
    public static final double MEMORY_THRESHOLD_DISABLED = 90.0;
    
    /** CPU使用率阈值 */
    public static final double CPU_USAGE_THRESHOLD = 80.0;
    
    /** 慢操作比率阈值 */
    public static final double SLOW_OPERATION_RATE = 0.05; // 5%
    
    /** 临界操作时间（毫秒） */
    public static final long CRITICAL_OPERATION_TIME_MS = 1000L;
    
    // ==================== 功能开关 ====================
    
    /** 主功能开关 */
    public static final boolean ENABLED = false; // 默认关闭，需显式启用
    
    /** 注解功能开关 */
    public static final boolean ANNOTATION_ENABLED = true;
    
    /** 变更追踪开关 */
    public static final boolean CHANGE_TRACKING_ENABLED = false;
    
    /** 深度快照开关 */
    public static final boolean DEEP_SNAPSHOT_ENABLED = false;
    
    /** 降级机制开关 */
    public static final boolean DEGRADATION_ENABLED = false;
    
    /** 环境变量参与开关 */
    public static final boolean ENV_VARIABLES_ENABLED = false;
    
    /** 指标收集开关 */
    public static final boolean METRICS_ENABLED = true;
    
    /** 缓存开关 */
    public static final boolean CACHE_ENABLED = true;
    
    // ==================== 数值精度 (任务卡要求) ====================
    
    /** 浮点数容差 */
    public static final double NUMERIC_FLOAT_TOLERANCE = 1e-12;
    
    /** 相对容差 */
    public static final double NUMERIC_RELATIVE_TOLERANCE = 1e-9;
    
    /** BigDecimal默认比较方法 */
    public static final String NUMERIC_BIGDECIMAL_COMPARE_METHOD = "COMPARE_TO";
    
    /** BigDecimal默认scale */
    public static final int NUMERIC_BIGDECIMAL_DEFAULT_SCALE = -1;
    
    /** 默认舍入模式 */
    public static final String NUMERIC_DEFAULT_ROUNDING_MODE = "HALF_UP";
    
    // ==================== 日期时间精度 (任务卡要求) ====================
    
    /** 默认日期格式 */
    public static final String DATETIME_DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    /** 默认时间容差（毫秒） */
    public static final long DATETIME_TOLERANCE_MS = 0L;
    
    /** 默认时区 */
    public static final String DATETIME_DEFAULT_TIMEZONE = "SYSTEM";
    
    /** Duration格式 */
    public static final String DATETIME_DURATION_FORMAT = "ISO8601";
    
    // ==================== CT-006: 并发与内存优化 ====================
    
    /** 并发重试最大次数 */
    public static final int CONCURRENT_RETRY_MAX_ATTEMPTS = 1;
    
    /** 并发重试基础延迟（毫秒） */
    public static final long CONCURRENT_RETRY_BASE_DELAY_MS = 10L;
    
    /** 嵌套stage最大深度 */
    public static final int NESTED_STAGE_MAX_DEPTH = 20;
    
    /** 嵌套清理批次大小 */
    public static final int NESTED_CLEANUP_BATCH_SIZE = 100;
    
    /** FIFO缓存默认大小 */
    public static final int FIFO_CACHE_DEFAULT_SIZE = 1000;
    
    /** 指标收集缓冲区大小 */
    public static final int METRICS_BUFFER_SIZE = 1000;
    
    /** 指标刷新间隔（秒） */
    public static final int METRICS_FLUSH_INTERVAL_SECONDS = 10;
    
    // ==================== 差异检测 ====================
    
    /** 最大变更数（单对象） */
    public static final int MAX_CHANGES_PER_OBJECT = 1000;
    
    /** 包含null变更 */
    public static final boolean INCLUDE_NULL_CHANGES = false;
    
    /** 输出模式 */
    public static final String OUTPUT_MODE = "compat";
    
    /** 路径格式 */
    public static final String PATH_FORMAT = "legacy";
    
    // ==================== 导出配置 ====================
    
    /** 导出格式 */
    public static final String EXPORT_FORMAT = "json";
    
    /** 格式化打印 */
    public static final boolean PRETTY_PRINT = true;
    
    /** 显示时间戳 */
    public static final boolean SHOW_TIMESTAMP = false;
    
    /** 包含敏感信息 */
    public static final boolean INCLUDE_SENSITIVE_INFO = false;
    
    // ==================== 配置键常量 ====================
    
    public static final class Keys {
        // 深度与性能
        public static final String MAX_DEPTH = "tfi.change-tracking.snapshot.max-depth";
        public static final String TIME_BUDGET_MS = "tfi.change-tracking.snapshot.time-budget-ms";
        public static final String SLOW_OPERATION_MS = "tfi.change-tracking.degradation.slow-operation-threshold-ms";
        
        // 监控配置（任务卡要求的新键名）
        public static final String MONITORING_SLOW_OPERATION_MS = "tfi.change-tracking.monitoring.slow-operation-ms";
        
        // 数值精度（任务卡要求的新配置）
        public static final String NUMERIC_FLOAT_TOLERANCE = "tfi.change-tracking.numeric.float-tolerance";
        public static final String NUMERIC_RELATIVE_TOLERANCE = "tfi.change-tracking.numeric.relative-tolerance";
        public static final String NUMERIC_BIGDECIMAL_COMPARE_METHOD = "tfi.change-tracking.numeric.bigdecimal.compare-method";
        public static final String NUMERIC_BIGDECIMAL_DEFAULT_SCALE = "tfi.change-tracking.numeric.bigdecimal.default-scale";
        public static final String NUMERIC_DEFAULT_ROUNDING_MODE = "tfi.change-tracking.numeric.default-rounding-mode";
        
        // 日期时间精度（任务卡要求的新配置）
        public static final String DATETIME_DEFAULT_FORMAT = "tfi.change-tracking.datetime.default-format";
        public static final String DATETIME_TOLERANCE_MS = "tfi.change-tracking.datetime.tolerance-ms";
        public static final String DATETIME_DEFAULT_TIMEZONE = "tfi.change-tracking.datetime.timezone";
        public static final String DATETIME_DURATION_FORMAT = "tfi.change-tracking.datetime.duration-format";
        
        // 集合与降级
        public static final String LIST_SIZE_THRESHOLD = "tfi.change-tracking.degradation.list-size-threshold";
        public static final String K_PAIRS_THRESHOLD = "tfi.change-tracking.degradation.k-pairs-threshold";
        public static final String COLLECTION_SUMMARY_THRESHOLD = "tfi.change-tracking.snapshot.collection-summary-threshold";
        
        // 功能开关
        public static final String ENABLED = "tfi.enabled";
        public static final String ENV_VARIABLES_ENABLED = "tfi.config.env-vars.enabled";
        public static final String ENV_ENABLED = "tfi.config.enable-env"; // 任务卡规范键名
        public static final String RESOLVER_ENABLED = "tfi.config.resolver.enabled";
        
        // CT-006: 并发与内存优化
        public static final String CONCURRENT_RETRY_MAX_ATTEMPTS = "tfi.concurrent.retry.max-attempts";
        public static final String CONCURRENT_RETRY_BASE_DELAY_MS = "tfi.concurrent.retry.base-delay-ms";
        public static final String NESTED_STAGE_MAX_DEPTH = "tfi.context.nested-stage.max-depth";
        public static final String NESTED_CLEANUP_BATCH_SIZE = "tfi.context.nested-cleanup.batch-size";
        public static final String FIFO_CACHE_DEFAULT_SIZE = "tfi.cache.fifo.default-size";
        public static final String METRICS_BUFFER_SIZE = "tfi.metrics.buffer.size";
        public static final String METRICS_FLUSH_INTERVAL_SECONDS = "tfi.metrics.flush.interval-seconds";
        
        private Keys() {}
    }
}