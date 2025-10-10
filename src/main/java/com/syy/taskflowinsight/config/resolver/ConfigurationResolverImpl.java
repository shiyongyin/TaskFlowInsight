package com.syy.taskflowinsight.config.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置解析器实现
 * 
 * 实现7层优先级配置解析，支持类型转换和默认值回退
 * 
 * <p><b>架构说明：</b></p>
 * 本类采用单体聚合模式实现，整合了CARD-CT-005文档中提到的多个概念组件：
 * <ul>
 *   <li>TfiConfigurationResolver（本类） - 统一配置解析门面</li>
 *   <li>ConfigurationPriorityEngine - 内嵌在 resolveByPriority() 和 getEffectivePriority() 方法中</li>
 *   <li>TfiConfigurationValidator - 由独立的 TfiConfigValidator 类实现</li>
 *   <li>ConfigMigrationMapper - 独立类，通过组合方式集成</li>
 * </ul>
 * 
 * <p><b>7层优先级顺序：</b></p>
 * Runtime API > Method Annotation > Class Annotation > Spring Config > Environment Variables(可选) > JVM Properties > Default Values
 * 
 * <p><b>环境变量层特性：</b></p>
 * - 默认关闭（安全考虑）
 * - 支持短名映射（TFI_MAX_DEPTH等）和通用规则转换
 * - 可通过 tfi.config.enable-env、tfi.config.env-vars.enabled 或 TFI_ENABLE_ENV 环境变量启用
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Component
@ConditionalOnProperty(name = "tfi.config.resolver.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigurationResolverImpl implements ConfigurationResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationResolverImpl.class);
    
    private final Environment environment;
    private final Map<String, Object> runtimeConfigs = new ConcurrentHashMap<>();
    private final Map<String, Object> methodAnnotationConfigs = new ConcurrentHashMap<>();
    private final Map<String, Object> classAnnotationConfigs = new ConcurrentHashMap<>();
    private final Map<String, Map<ConfigPriority, ConfigSource>> configSourcesCache = new ConcurrentHashMap<>();
    private final ConfigMigrationMapper migrationMapper;
    
    // 配置解析器开关
    private final boolean resolverEnabled;
    private final boolean envVariablesEnabled;
    
    // 环境变量短名映射（任务卡要求的显式映射）
    private static final Map<String, String> ENV_SHORT_NAME_MAPPINGS = Map.of(
        "TFI_MAX_DEPTH", "tfi.change-tracking.snapshot.max-depth",
        "TFI_TIME_BUDGET_MS", "tfi.change-tracking.snapshot.time-budget-ms",
        "TFI_SLOW_OPERATION_MS", "tfi.change-tracking.monitoring.slow-operation-ms",
        "TFI_ENABLE_ENV", "tfi.config.enable-env",
        "TFI_FLOAT_TOLERANCE", "tfi.change-tracking.numeric.float-tolerance",
        "TFI_RELATIVE_TOLERANCE", "tfi.change-tracking.numeric.relative-tolerance"
    );
    
    @Autowired
    public ConfigurationResolverImpl(Environment environment, ConfigMigrationMapper migrationMapper) {
        this.environment = environment;
        this.migrationMapper = migrationMapper;
        this.resolverEnabled = environment.getProperty(
            ConfigDefaults.Keys.RESOLVER_ENABLED, 
            Boolean.class, 
            true
        );
        // 兼容两种环境变量配置键名，并支持短名映射
        Boolean envEnabled = environment.getProperty(ConfigDefaults.Keys.ENV_ENABLED, Boolean.class);
        if (envEnabled == null) {
            envEnabled = environment.getProperty(ConfigDefaults.Keys.ENV_VARIABLES_ENABLED, Boolean.class);
        }
        // 如果Spring配置都没有，检查短名环境变量或系统属性
        if (envEnabled == null) {
            String tfiEnableEnvValue = System.getenv("TFI_ENABLE_ENV");
            Boolean parsed = parseTruthy(tfiEnableEnvValue);
            if (parsed == null) {
                parsed = parseTruthy(System.getProperty("TFI_ENABLE_ENV"));
            }
            if (parsed != null) {
                envEnabled = parsed;
                logger.debug("TFI_ENABLE_ENV detected via {}: {}",
                    System.getenv("TFI_ENABLE_ENV") != null ? "env" : "system property", envEnabled);
            }
        }
        this.envVariablesEnabled = envEnabled != null ? envEnabled : ConfigDefaults.ENV_VARIABLES_ENABLED;
        
        logger.info("ConfigurationResolver initialized: resolverEnabled={}, envVariablesEnabled={}", 
            resolverEnabled, envVariablesEnabled);
    }
    
    @Override
    public <T> T resolve(String key, Class<T> type, T defaultValue) {
        // 检查并迁移旧键（产生一次性迁移警告）；解析时优先使用传入键，并兼容新旧键读取
        migrationMapper.checkAndMigrate(key);
        
        if (!resolverEnabled) {
            // 解析器禁用时，直接返回Spring配置或默认值
            return environment.getProperty(key, type, defaultValue);
        }
        
        // 按优先级顺序尝试解析
        Optional<T> value = resolveByPriority(key, type);
        
        if (value.isPresent()) {
            logger.debug("Resolved config '{}' = {} from {}", 
                key, value.get(), getEffectivePriority(key));
            return value.get();
        }
        
        logger.debug("Using default value for config '{}' = {}", key, defaultValue);
        return defaultValue;
    }
    
    @Override
    public <T> Optional<T> resolve(String key, Class<T> type) {
        // 检查并迁移旧键（产生一次性迁移警告）；解析时优先使用传入键，并兼容新旧键读取
        migrationMapper.checkAndMigrate(key);
        
        if (!resolverEnabled) {
            return Optional.ofNullable(environment.getProperty(key, type));
        }
        return resolveByPriority(key, type);
    }
    
    private <T> Optional<T> resolveByPriority(String key, Class<T> type) {
        // 1. Runtime API (最高优先级) - 支持新旧键兼容
        if (runtimeConfigs.containsKey(key)) {
            return Optional.ofNullable(convertValue(runtimeConfigs.get(key), type));
        } else {
            String mappedNew = migrationMapper.getNewKey(key);
            if (!Objects.equals(mappedNew, key) && runtimeConfigs.containsKey(mappedNew)) {
                return Optional.ofNullable(convertValue(runtimeConfigs.get(mappedNew), type));
            }
            String mappedOld = migrationMapper.getOldKeyForNew(key);
            if (mappedOld != null && runtimeConfigs.containsKey(mappedOld)) {
                return Optional.ofNullable(convertValue(runtimeConfigs.get(mappedOld), type));
            }
        }
        
        // 2. Method Annotation
        if (methodAnnotationConfigs.containsKey(key)) {
            return Optional.ofNullable(convertValue(methodAnnotationConfigs.get(key), type));
        } else {
            String mappedNew = migrationMapper.getNewKey(key);
            if (!Objects.equals(mappedNew, key) && methodAnnotationConfigs.containsKey(mappedNew)) {
                return Optional.ofNullable(convertValue(methodAnnotationConfigs.get(mappedNew), type));
            }
            String mappedOld = migrationMapper.getOldKeyForNew(key);
            if (mappedOld != null && methodAnnotationConfigs.containsKey(mappedOld)) {
                return Optional.ofNullable(convertValue(methodAnnotationConfigs.get(mappedOld), type));
            }
        }
        
        // 3. Class Annotation
        if (classAnnotationConfigs.containsKey(key)) {
            return Optional.ofNullable(convertValue(classAnnotationConfigs.get(key), type));
        } else {
            String mappedNew = migrationMapper.getNewKey(key);
            if (!Objects.equals(mappedNew, key) && classAnnotationConfigs.containsKey(mappedNew)) {
                return Optional.ofNullable(convertValue(classAnnotationConfigs.get(mappedNew), type));
            }
            String mappedOld = migrationMapper.getOldKeyForNew(key);
            if (mappedOld != null && classAnnotationConfigs.containsKey(mappedOld)) {
                return Optional.ofNullable(convertValue(classAnnotationConfigs.get(mappedOld), type));
            }
        }
        
        // 4. Spring Configuration（支持旧/新键双向兼容读取；对已废弃键优先新键且忽略旧键值）
        String mappedNew = migrationMapper.getNewKey(key);
        String mappedOld = migrationMapper.getOldKeyForNew(key);

        T springValue;
        if (!Objects.equals(mappedNew, key)) {
            // key 为废弃旧键：只读取新键，不读取旧键值（符合 CT-005 对关键迁移项的严控）
            springValue = environment.getProperty(mappedNew, type);
        } else {
            // key 为新键或未废弃：先读自身，再读新键（等于自身时无效）
            // 注意：不再回落到旧键，避免旧键值覆盖新键默认值（CT-005 迁移约束）
            springValue = environment.getProperty(key, type);
            if (springValue == null && !Objects.equals(mappedNew, key)) {
                springValue = environment.getProperty(mappedNew, type);
            }
        }
        if (springValue != null) {
            return Optional.of(springValue);
        }
        
        // 5. Environment Variable (可选)
        if (envVariablesEnabled) {
            // 先尝试短名映射
            String envValue = tryGetEnvByShortName(key);
            if (envValue == null) {
                // 再尝试通用规则
                String envKey = toEnvVariableKey(key);
                envValue = System.getenv(envKey);
                if (envValue != null) {
                    logger.debug("Found environment variable: {}={}", envKey, envValue);
                }
            }
            if (envValue != null) {
                return Optional.ofNullable(convertValue(envValue, type));
            }
        }
        
        // 6. JVM System Property
        String jvmValue = System.getProperty(key);
        if (jvmValue != null) {
            return Optional.ofNullable(convertValue(jvmValue, type));
        }
        
        // 7. Default Value (在具体配置键中处理)
        return getDefaultValue(key, type);
    }
    
    @Override
    public ConfigPriority getEffectivePriority(String key) {
        // Runtime API
        if (runtimeConfigs.containsKey(key)) {
            return ConfigPriority.RUNTIME_API;
        }
        String mappedNew = migrationMapper.getNewKey(key);
        if (!Objects.equals(mappedNew, key) && runtimeConfigs.containsKey(mappedNew)) {
            return ConfigPriority.RUNTIME_API;
        }
        String mappedOld = migrationMapper.getOldKeyForNew(key);
        if (mappedOld != null && runtimeConfigs.containsKey(mappedOld)) {
            return ConfigPriority.RUNTIME_API;
        }
        if (methodAnnotationConfigs.containsKey(key)) {
            return ConfigPriority.METHOD_ANNOTATION;
        }
        if (!Objects.equals(mappedNew, key) && methodAnnotationConfigs.containsKey(mappedNew)) {
            return ConfigPriority.METHOD_ANNOTATION;
        }
        if (mappedOld != null && methodAnnotationConfigs.containsKey(mappedOld)) {
            return ConfigPriority.METHOD_ANNOTATION;
        }
        if (classAnnotationConfigs.containsKey(key)) {
            return ConfigPriority.CLASS_ANNOTATION;
        }
        if (!Objects.equals(mappedNew, key) && classAnnotationConfigs.containsKey(mappedNew)) {
            return ConfigPriority.CLASS_ANNOTATION;
        }
        if (mappedOld != null && classAnnotationConfigs.containsKey(mappedOld)) {
            return ConfigPriority.CLASS_ANNOTATION;
        }
        if (environment.getProperty(key) != null) {
            return ConfigPriority.SPRING_CONFIG;
        }
        if (!Objects.equals(mappedNew, key) && environment.getProperty(mappedNew) != null) {
            return ConfigPriority.SPRING_CONFIG;
        }
        if (mappedOld != null && environment.getProperty(mappedOld) != null) {
            return ConfigPriority.SPRING_CONFIG;
        }
        if (envVariablesEnabled) {
            // 先尝试短名映射，再尝试通用规则（与 resolveByPriority 保持一致）
            String envValue = tryGetEnvByShortName(key);
            if (envValue == null) {
                envValue = System.getenv(toEnvVariableKey(key));
            }
            if (envValue == null && !Objects.equals(mappedNew, key)) {
                envValue = tryGetEnvByShortName(mappedNew);
                if (envValue == null) envValue = System.getenv(toEnvVariableKey(mappedNew));
            }
            if (envValue == null && mappedOld != null) {
                envValue = tryGetEnvByShortName(mappedOld);
                if (envValue == null) envValue = System.getenv(toEnvVariableKey(mappedOld));
            }
            if (envValue != null) {
                return ConfigPriority.ENV_VARIABLE;
            }
        }
        if (System.getProperty(key) != null) {
            return ConfigPriority.JVM_PROPERTY;
        }
        if (!Objects.equals(mappedNew, key) && System.getProperty(mappedNew) != null) {
            return ConfigPriority.JVM_PROPERTY;
        }
        if (mappedOld != null && System.getProperty(mappedOld) != null) {
            return ConfigPriority.JVM_PROPERTY;
        }
        return ConfigPriority.DEFAULT_VALUE;
    }
    
    @Override
    public Map<ConfigPriority, ConfigSource> getConfigSources(String key) {
        return configSourcesCache.computeIfAbsent(key, k -> {
            Map<ConfigPriority, ConfigSource> sources = new TreeMap<>();
            String actualKey = migrationMapper.checkAndMigrate(k);
            
            // 收集所有层级的配置
            if (runtimeConfigs.containsKey(actualKey)) {
                sources.put(ConfigPriority.RUNTIME_API, 
                    new ConfigSource(ConfigPriority.RUNTIME_API, k, 
                        runtimeConfigs.get(actualKey), "Runtime API call"));
            }
            
            if (methodAnnotationConfigs.containsKey(k)) {
                sources.put(ConfigPriority.METHOD_ANNOTATION,
                    new ConfigSource(ConfigPriority.METHOD_ANNOTATION, k,
                        methodAnnotationConfigs.get(k), "@TfiTask annotation"));
            }
            
            if (classAnnotationConfigs.containsKey(k)) {
                sources.put(ConfigPriority.CLASS_ANNOTATION,
                    new ConfigSource(ConfigPriority.CLASS_ANNOTATION, k,
                        classAnnotationConfigs.get(k), "@TfiConfiguration annotation"));
            }
            
            String springValue = environment.getProperty(k);
            if (springValue != null) {
                sources.put(ConfigPriority.SPRING_CONFIG,
                    new ConfigSource(ConfigPriority.SPRING_CONFIG, k,
                        springValue, "application.yml"));
            }
            // 也检查映射的新旧键，以便在来源追踪中可见
            String mappedNew = migrationMapper.getNewKey(k);
            if (!Objects.equals(mappedNew, k)) {
                String v = environment.getProperty(mappedNew);
                if (v != null) {
                    sources.put(ConfigPriority.SPRING_CONFIG,
                        new ConfigSource(ConfigPriority.SPRING_CONFIG, mappedNew,
                            v, "application.yml (via migration from old key)"));
                }
            }
            String mappedOld = migrationMapper.getOldKeyForNew(k);
            if (mappedOld != null) {
                String v = environment.getProperty(mappedOld);
                if (v != null) {
                    sources.put(ConfigPriority.SPRING_CONFIG,
                        new ConfigSource(ConfigPriority.SPRING_CONFIG, mappedOld,
                            v, "application.yml (via migration from new key)"));
                }
            }
            
            if (envVariablesEnabled) {
                // 先尝试短名映射，再尝试通用规则（与 resolveByPriority 保持一致）
                String envValue = tryGetEnvByShortName(k);
                String envSource = null;
                if (envValue != null) {
                    // 找到使用的短名
                    for (Map.Entry<String, String> entry : ENV_SHORT_NAME_MAPPINGS.entrySet()) {
                        if (entry.getValue().equals(k)) {
                            envSource = "Environment variable (short name): " + entry.getKey();
                            break;
                        }
                    }
                } else {
                    String envKey = toEnvVariableKey(k);
                    envValue = System.getenv(envKey);
                    if (envValue != null) {
                        envSource = "Environment variable: " + envKey;
                    }
                }
                
                if (envValue != null && envSource != null) {
                    sources.put(ConfigPriority.ENV_VARIABLE,
                        new ConfigSource(ConfigPriority.ENV_VARIABLE, k,
                            envValue, envSource));
                }
            }
            
            String jvmValue = System.getProperty(k);
            if (jvmValue != null) {
                sources.put(ConfigPriority.JVM_PROPERTY,
                    new ConfigSource(ConfigPriority.JVM_PROPERTY, k,
                        jvmValue, "JVM property: -D" + k));
            }
            
            // 添加默认值
            getDefaultValue(k, Object.class).ifPresent(defaultValue ->
                sources.put(ConfigPriority.DEFAULT_VALUE,
                    new ConfigSource(ConfigPriority.DEFAULT_VALUE, k,
                        defaultValue, "ConfigDefaults")));
            
            return sources;
        });
    }
    
    @Override
    public void setRuntimeConfig(String key, Object value) {
        // 确保使用迁移后的键存储
        String actualKey = migrationMapper.checkAndMigrate(key);
        runtimeConfigs.put(actualKey, value);
        configSourcesCache.remove(key); // 清除原键缓存
        configSourcesCache.remove(actualKey); // 清除迁移键缓存
        logger.debug("Set runtime config: {}={} (migrated to: {})", key, value, actualKey);
    }
    
    @Override
    public void clearRuntimeConfig(String key) {
        // 确保清除迁移后的键
        String actualKey = migrationMapper.checkAndMigrate(key);
        runtimeConfigs.remove(actualKey);
        configSourcesCache.remove(key); // 清除原键缓存
        configSourcesCache.remove(actualKey); // 清除迁移键缓存
        logger.debug("Cleared runtime config: {} (migrated to: {})", key, actualKey);
    }
    
    @Override
    public boolean isEnvVariablesEnabled() {
        return envVariablesEnabled;
    }
    
    @Override
    public void refresh() {
        configSourcesCache.clear();
        logger.info("Configuration cache refreshed");
    }
    
    /**
     * 设置方法注解配置（供AOP使用）
     */
    public void setMethodAnnotationConfig(String key, Object value) {
        methodAnnotationConfigs.put(key, value);
        configSourcesCache.remove(key);
    }
    
    /**
     * 设置类注解配置（供AOP使用）
     */
    public void setClassAnnotationConfig(String key, Object value) {
        classAnnotationConfigs.put(key, value);
        configSourcesCache.remove(key);
    }
    
    /**
     * 清理方法注解配置（对称方法）
     */
    public void clearMethodAnnotationConfig(String key) {
        methodAnnotationConfigs.remove(key);
        configSourcesCache.remove(key);
        logger.debug("Cleared method annotation config: {}", key);
    }
    
    /**
     * 清理类注解配置（对称方法）
     */
    public void clearClassAnnotationConfig(String key) {
        classAnnotationConfigs.remove(key);
        configSourcesCache.remove(key);
        logger.debug("Cleared class annotation config: {}", key);
    }
    
    /**
     * 尝试通过短名映射获取环境变量
     */
    private String tryGetEnvByShortName(String key) {
        // 反向查找：通过配置键找到对应的短名
        for (Map.Entry<String, String> entry : ENV_SHORT_NAME_MAPPINGS.entrySet()) {
            if (entry.getValue().equals(key)) {
                String shortName = entry.getKey();
                String value = System.getenv(shortName);
                if (value != null) {
                    logger.debug("Found environment variable via short name: {}={}", shortName, value);
                    return value;
                }
            }
        }
        return null;
    }
    
    /**
     * 转换配置键为环境变量格式
     * tfi.change-tracking.max-depth -> TFI_CHANGE_TRACKING_MAX_DEPTH
     */
    private String toEnvVariableKey(String key) {
        return key.toUpperCase()
            .replace('.', '_')
            .replace('-', '_');
    }

    /**
     * 将字符串解析为布尔真值（支持 true/false、1/0、yes/no、on/off），大小写不敏感；无法解析返回null
     */
    private Boolean parseTruthy(String v) {
        if (v == null) return null;
        String s = v.trim().toLowerCase();
        switch (s) {
            case "true":
            case "1":
            case "yes":
            case "y":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "n":
            case "off":
                return false;
            default:
                return null;
        }
    }
    
    /**
     * 类型转换（改进：失败时返回默认值而非null）
     */
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> type) {
        return convertValue(value, type, null);
    }
    
    /**
     * 类型转换（带默认值回退）
     */
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> type, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        
        String stringValue = value.toString();
        
        try {
            // 基本类型转换
            if (type == Integer.class || type == int.class) {
                return (T) Integer.valueOf(stringValue);
            }
            if (type == Long.class || type == long.class) {
                return (T) Long.valueOf(stringValue);
            }
            if (type == Double.class || type == double.class) {
                return (T) Double.valueOf(stringValue);
            }
            if (type == Float.class || type == float.class) {
                return (T) Float.valueOf(stringValue);
            }
            if (type == Boolean.class || type == boolean.class) {
                return (T) Boolean.valueOf(stringValue);
            }
            if (type == String.class) {
                return (T) stringValue;
            }
            
            logger.warn("Unsupported type conversion: {} to {}, falling back to default: {}", 
                value.getClass(), type, defaultValue);
            return defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Type conversion failed: '{}' cannot be converted to {}: {}, falling back to default: {}", 
                stringValue, type.getSimpleName(), e.getMessage(), defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 获取默认值（根据配置键）
     */
    private <T> Optional<T> getDefaultValue(String key, Class<T> type) {
        Object defaultValue = switch (key) {
            case ConfigDefaults.Keys.MAX_DEPTH -> ConfigDefaults.MAX_DEPTH;
            case ConfigDefaults.Keys.TIME_BUDGET_MS -> ConfigDefaults.TIME_BUDGET_MS;
            case ConfigDefaults.Keys.SLOW_OPERATION_MS -> ConfigDefaults.SLOW_OPERATION_MS;
            case ConfigDefaults.Keys.MONITORING_SLOW_OPERATION_MS -> ConfigDefaults.SLOW_OPERATION_MS; // 统一默认值
            case ConfigDefaults.Keys.NUMERIC_FLOAT_TOLERANCE -> ConfigDefaults.NUMERIC_FLOAT_TOLERANCE;
            case ConfigDefaults.Keys.NUMERIC_RELATIVE_TOLERANCE -> ConfigDefaults.NUMERIC_RELATIVE_TOLERANCE;
            case ConfigDefaults.Keys.LIST_SIZE_THRESHOLD -> ConfigDefaults.LIST_SIZE_THRESHOLD;
            case ConfigDefaults.Keys.K_PAIRS_THRESHOLD -> ConfigDefaults.K_PAIRS_THRESHOLD;
            case ConfigDefaults.Keys.COLLECTION_SUMMARY_THRESHOLD -> ConfigDefaults.COLLECTION_SUMMARY_THRESHOLD;
            case ConfigDefaults.Keys.ENABLED -> ConfigDefaults.ENABLED;
            case ConfigDefaults.Keys.ENV_VARIABLES_ENABLED -> ConfigDefaults.ENV_VARIABLES_ENABLED;
            case ConfigDefaults.Keys.ENV_ENABLED -> ConfigDefaults.ENV_VARIABLES_ENABLED; // 兼容两种键名
            default -> null;
        };
        
        if (defaultValue != null) {
            return Optional.ofNullable(convertValue(defaultValue, type));
        }
        return Optional.empty();
    }
}
