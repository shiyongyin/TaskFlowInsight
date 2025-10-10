package com.syy.taskflowinsight.tracking.path;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 统一路径构建器
 * 负责生成符合规范的对象路径字符串，支持Map键双引号格式和JSON风格转义
 * 
 * 主要功能：
 * - Map键路径使用双引号格式：parent["key"]  
 * - 自动转义特殊字符（" \ \n \t \r）
 * - 提供缓存优化提升性能
 * - 支持链式调用构建复杂路径
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class PathBuilder {
    
    // 特殊字符正则模式（用于快速检测）
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[\"\\\\\\n\\t\\r]");
    
    // 转义结果缓存（线程安全）
    private static final ConcurrentHashMap<String, String> ESCAPE_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 构建Map键路径（双引号格式）
     * 
     * @param parent 父路径
     * @param key Map键值
     * @return 格式化的路径字符串 parent["key"]
     */
    public static String mapKey(String parent, String key) {
        return mapKey(parent, key, true); // 默认使用标准格式
    }
    
    /**
     * 构建Map键路径（支持兼容模式）
     * 
     * @param parent 父路径
     * @param key Map键值
     * @param useStandardFormat true=双引号格式，false=单引号兼容格式
     * @return 格式化的路径字符串
     */
    public static String mapKey(String parent, String key, boolean useStandardFormat) {
        if (key == null) {
            return parent + "[null]";
        }
        
        if (useStandardFormat) {
            String escapedKey = escapeString(key);
            return parent + "[\"" + escapedKey + "\"]";
        } else {
            // 兼容模式：单引号，简单转义
            String escapedKey = key.replace("'", "\\'");
            return parent + "['" + escapedKey + "']";
        }
    }
    
    /**
     * 构建数组索引路径
     * 
     * @param parent 父路径  
     * @param index 数组索引
     * @return 格式化的路径字符串 parent[index]
     */
    public static String arrayIndex(String parent, int index) {
        return parent + "[" + index + "]";
    }
    
    /**
     * 构建字段路径
     * 
     * @param parent 父路径
     * @param fieldName 字段名
     * @return 格式化的路径字符串
     */
    public static String fieldPath(String parent, String fieldName) {
        if (parent == null || parent.isEmpty()) {
            return fieldName;
        }
        return parent + "." + fieldName;
    }
    
    /**
     * 构建Set元素路径（支持CARD-CT-ALIGN）
     * 使用id=标识符格式来表示Set中的元素
     * 
     * @param parent 父路径
     * @param element Set元素对象  
     * @return 格式化的路径字符串 parent[id=elementId]
     */
    public static String setElement(String parent, Object element) {
        if (element == null) {
            return parent + "[id=null]";
        }
        
        // 生成稳定的元素标识符
        String elementId = generateElementId(element);
        return parent + "[id=" + elementId + "]";
    }
    
    /**
     * 生成Set元素的稳定标识符
     * 使用确定性哈希算法确保JVM间稳定性
     */
    private static String generateElementId(Object element) {
        if (element == null) {
            return "null";
        }

        // 使用对象的字符串表示和类名生成确定性哈希
        String className = element.getClass().getSimpleName();
        String objectString = element.toString();

        // 组合类名和对象字符串表示
        String combined = className + ":" + objectString;

        // 使用确定性哈希算法（不依赖JVM实现）
        int hash = combined.hashCode();

        // 确保正数
        if (hash < 0) {
            hash = hash & 0x7FFFFFFF;
        }

        return className + String.format("%08X", hash);
    }
    
    /**
     * 字符串转义（JSON风格，带缓存优化）
     * 
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private static String escapeString(String input) {
        // 快速路径：无特殊字符直接返回，不缓存
        if (!SPECIAL_CHARS.matcher(input).find()) {
            return input;
        }
        
        // 只有需要转义的字符串才使用缓存
        return ESCAPE_CACHE.computeIfAbsent(input, key -> {
            // 按JSON标准转义特殊字符
            return key.replace("\\", "\\\\")    // 反斜杠必须最先处理
                     .replace("\"", "\\\"")     // 双引号
                     .replace("\n", "\\n")      // 换行符
                     .replace("\t", "\\t")      // 制表符  
                     .replace("\r", "\\r");     // 回车符
        });
    }
    
    /**
     * 获取缓存大小（用于监控和测试）
     * 
     * @return 当前缓存条目数
     */
    public static int getCacheSize() {
        return ESCAPE_CACHE.size();
    }
    
    /**
     * 清理缓存（用于测试或内存压力时）
     */
    public static void clearCache() {
        ESCAPE_CACHE.clear();
    }
    
    /**
     * 构建字段路径（用于PathCollector）
     *
     * @param parent 父路径
     * @param fieldName 字段名
     * @return 格式化的路径字符串
     */
    public static String buildFieldPath(String parent, String fieldName) {
        return fieldPath(parent, fieldName);
    }

    /**
     * 构建Map键路径（用于PathCollector）
     *
     * @param parent 父路径
     * @param key Map键值
     * @return 格式化的路径字符串 parent["key"]
     */
    public static String buildMapKeyPath(String parent, String key) {
        return mapKey(parent, key);
    }

    /**
     * 构建数组索引路径（用于PathCollector）
     *
     * @param parent 父路径
     * @param index 数组索引
     * @return 格式化的路径字符串 parent[index]
     */
    public static String buildArrayIndexPath(String parent, int index) {
        return arrayIndex(parent, index);
    }

    /**
     * 构建Set元素路径（用于PathCollector）
     *
     * @param parent 父路径
     * @param element Set元素
     * @return 格式化的路径字符串 parent[id=elementId]
     */
    public static String buildSetElementPath(String parent, Object element) {
        return setElement(parent, element);
    }

    /**
     * 链式构建器支持复杂路径构建
     */
    public static PathBuilderChain start(String root) {
        return new PathBuilderChain(root);
    }
    
    /**
     * 链式路径构建器
     */
    public static class PathBuilderChain {
        private String path;
        
        PathBuilderChain(String root) {
            this.path = root != null ? root : "";
        }
        
        public PathBuilderChain field(String name) {
            this.path = fieldPath(this.path, name);
            return this;
        }
        
        public PathBuilderChain mapKey(String key) {
            this.path = PathBuilder.mapKey(this.path, key);
            return this;
        }
        
        public PathBuilderChain arrayIndex(int index) {
            this.path = PathBuilder.arrayIndex(this.path, index);
            return this;
        }
        
        public String build() {
            return this.path;
        }
        
        @Override
        public String toString() {
            return build();
        }
    }
}