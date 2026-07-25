package com.syy.taskflowinsight.tracking.path;

import java.util.regex.Pattern;

/**
 * 旧tracking快照的无状态display path构建器。
 *
 * <p>canonical内核只使用typed {@link ComparePath}；该类仅保留旧字符串输出形状，不能缓存、
 * 参与地址identity或向Ops发布命中率，否则会重新形成第二个path owner。</p>
 * 
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
public class PathBuilder {
    
    /** 仅用于避免无转义字符时创建中间字符串；不保存动态key或跨请求状态。 */
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[\"\\\\\\n\\t\\r]");
    
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
     * 字符串转义（JSON风格）。
     *
     * <p>转义成本与单个输入长度线性相关；不保留跨请求cache，
     * 避免无界动态key占用进程内存。</p>
     * 
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private static String escapeString(String input) {
        // 快速路径：无特殊字符直接返回，不缓存
        if (!SPECIAL_CHARS.matcher(input).find()) {
            return input;
        }
        
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
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
     * 创建旧display path的可变链式构建器。
     *
     * <p>该入口只服务旧tracking输出；canonical比较必须使用{@link ComparePath}，不能把构建结果
     * 当作地址identity。</p>
     *
     * @param root display path根文本；{@code null}按空根处理
     * @return 仅在当前调用方内累积字符串的链式构建器
     */
    public static PathBuilderChain start(String root) {
        return new PathBuilderChain(root);
    }
    
    /**
     * 旧display path的调用方局部可变构建器。
     *
     * <p>实例不共享也不缓存，避免动态路径进入进程级状态；构建结果不具备typed path身份语义。</p>
     *
     * @since v3.0.0
     */
    public static class PathBuilderChain {

        /** 当前实例已累积的legacy display path；空字符串表示尚无根或segment。 */
        private String path;
        
        PathBuilderChain(String root) {
            this.path = root != null ? root : "";
        }
        
        /**
         * 追加一个字段display segment。
         *
         * @param name 字段展示名
         * @return 当前可变构建器，便于连续追加
         */
        public PathBuilderChain field(String name) {
            this.path = fieldPath(this.path, name);
            return this;
        }

        /**
         * 追加一个已转义的Map key display segment。
         *
         * @param key Map键展示文本；允许为{@code null}
         * @return 当前可变构建器，便于连续追加
         */
        public PathBuilderChain mapKey(String key) {
            this.path = PathBuilder.mapKey(this.path, key);
            return this;
        }

        /**
         * 追加一个数组索引display segment。
         *
         * @param index 调用方提供的数组索引
         * @return 当前可变构建器，便于连续追加
         */
        public PathBuilderChain arrayIndex(int index) {
            this.path = PathBuilder.arrayIndex(this.path, index);
            return this;
        }

        /**
         * 返回当前legacy display path，不执行解析或canonical化。
         *
         * @return 当前已累积的路径文本，不返回{@code null}
         */
        public String build() {
            return this.path;
        }

        /**
         * 使用与{@link #build()}相同的display文本，避免额外展示语义。
         *
         * @return 当前已累积的路径文本
         */
        @Override
        public String toString() {
            return build();
        }
    }
}
