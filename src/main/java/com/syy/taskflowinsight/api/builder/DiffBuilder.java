package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Objects;

/**
 * 统一装配入口（非 Spring 友好）。
 *
 * 提供最小 API，用于在纯 Java 环境下一行创建对比上下文；
 * 在 Spring 环境中可通过 {@link #fromSpring(Environment)} 读取默认值，
 * 再用链式方法覆盖。
 */
public final class DiffBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DiffBuilder.class);

    private final CompareOptions.CompareOptionsBuilder optionsBuilder = CompareOptions.builder();
    // 可选：用于注册路径级比较器（占位，P0 非 Spring 下不保证生效）
    private PropertyComparatorRegistry registry;

    // 配置优先级跟踪：DEFAULT < YAML < BUILDER
    private enum Source { DEFAULT, YAML, BUILDER }
    private Integer maxDepth; private Source maxDepthSource = Source.DEFAULT;
    private Boolean deep; private Source deepSource = Source.DEFAULT;
    private java.util.List<String> excludes; private Source excludesSource = Source.DEFAULT;

    private DiffBuilder() {}

    /** 创建 Builder（纯 Java 环境）。 */
    public static DiffBuilder create() { return new DiffBuilder(); }

    /**
     * 从 Spring Environment 读取配置并返回 Builder（示例演示）。
     * 读取最小配置：max-depth 与 enable-deep。
     */
    public static DiffBuilder fromSpring(Environment env) {
        DiffBuilder b = new DiffBuilder();
        if (env != null) {
            try {
                Integer md = env.getProperty("tfi.change-tracking.snapshot.max-depth", Integer.class);
                if (md != null && md > 0) b.setMaxDepth(md, Source.YAML);
            } catch (Throwable ignored) {}
            try {
                Boolean dv = env.getProperty("tfi.change-tracking.snapshot.enable-deep", Boolean.class);
                if (dv != null) b.setDeep(dv, Source.YAML);
            } catch (Throwable ignored) {}
            try {
                String raw = env.getProperty("tfi.change-tracking.snapshot.excludes");
                if (raw != null && !raw.isBlank()) {
                    String[] parts = raw.split("[,;\\s]+");
                    java.util.List<String> list = new java.util.ArrayList<>();
                    for (String p : parts) { if (!p.isBlank()) list.add(p.trim()); }
                    if (!list.isEmpty()) b.setExcludes(list, Source.YAML);
                }
            } catch (Throwable ignored) {}
        }
        return b;
    }

    /** 最大深度（隐式开启深度比较）。 */
    public DiffBuilder withMaxDepth(int depth) { if (depth > 0) setMaxDepth(depth, Source.BUILDER); return this; }

    /** 启用深度比较。 */
    public DiffBuilder withDeepCompare(boolean enable) { setDeep(enable, Source.BUILDER); return this; }

    /** 排除字段（支持通配/路径模式占位，映射至 CompareOptions.excludeFields）。 */
    public DiffBuilder withExcludePatterns(String... patterns) {
        if (patterns != null && patterns.length > 0) {
            setExcludes(Arrays.asList(patterns), Source.BUILDER);
        }
        return this;
    }

    private void setMaxDepth(int depth, Source src) {
        if (depth <= 0) return;
        boolean conflict = (this.maxDepth != null && !Objects.equals(this.maxDepth, depth) && src == Source.BUILDER && this.maxDepthSource == Source.YAML);
        this.maxDepth = depth;
        this.maxDepthSource = src;
        // deep 与 maxDepth 一致性：设置maxDepth隐式开启deep
        if (this.deep == null || !this.deep) { this.deep = true; this.deepSource = src == Source.DEFAULT ? Source.BUILDER : src; }
        if (conflict && logger.isWarnEnabled()) { logger.warn("Config override: maxDepth YAML={} -> BUILDER={}", this.maxDepth, depth); }
    }

    private void setDeep(boolean enable, Source src) {
        if (enable) {
            boolean conflict = (this.deep != null && !Objects.equals(this.deep, enable) && src == Source.BUILDER && this.deepSource == Source.YAML);
            this.deep = true;
            this.deepSource = src;
            if (conflict && logger.isWarnEnabled()) { logger.warn("Config override: deep YAML=false -> BUILDER=true"); }
        }
    }

    private void setExcludes(java.util.List<String> patterns, Source src) {
        if (patterns == null || patterns.isEmpty()) return;
        boolean conflict = (this.excludes != null && !this.excludes.isEmpty() && src == Source.BUILDER && this.excludesSource == Source.YAML);
        this.excludes = new java.util.ArrayList<>(patterns);
        this.excludesSource = src;
        if (conflict && logger.isWarnEnabled()) { logger.warn("Config override: excludes YAML -> BUILDER ({} -> {})", this.excludes.size(), patterns.size()); }
    }

    /**
     * 注册路径级字段比较器（占位）。
     * P0：仅登记到本地 Registry；在 Spring 环境中建议使用全局 Bean 注入以生效。
     */
    public DiffBuilder withPropertyComparator(String propertyPath, PropertyComparator comparator) {
        if (propertyPath == null || comparator == null) return this;
        if (registry == null) registry = new PropertyComparatorRegistry();
        try {
            registry.register(propertyPath, comparator);
        } catch (IllegalArgumentException ex) {
            logger.warn("Ignore invalid propertyPath for comparator registration: {}", propertyPath);
        }
        return this;
    }

    /** 构建不可变上下文（默认 CompareService 策略集合由工厂方法封装）。 */
    public TfiContext build() {
        long startNanos = System.nanoTime();
        CompareOptions opts = optionsBuilder.build();

        // 输出简要配置摘要（占位，避免敏感信息）
        if (logger.isInfoEnabled()) {
            logger.info("DiffBuilder.build: deep={}, maxDepth={}, excludes={} (registry={})",
                opts.isEnableDeepCompare(), opts.getMaxDepth(),
                opts.getExcludeFields() != null ? opts.getExcludeFields().size() : 0,
                registry != null ? "local" : "none");
        }

        // 推荐做法：CompareService 内部封装默认策略集合
        // 合成最终选项（Builder > YAML > 默认）
        CompareOptions.CompareOptionsBuilder ob = CompareOptions.builder();
        boolean finalDeep = (deep != null ? deep : false);
        if (finalDeep) ob.enableDeepCompare(true);
        int finalMaxDepth = (maxDepth != null ? maxDepth : CompareOptions.builder().build().getMaxDepth());
        if (finalDeep) ob.maxDepth(finalMaxDepth);
        if (excludes != null && !excludes.isEmpty()) ob.excludeFields(excludes);
        CompareOptions effective = ob.build();

        // 输出最终生效配置摘要（含来源）
        if (logger.isInfoEnabled()) {
            logger.info("DiffBuilder.effective: deep={}({}), maxDepth={}({}), excludes={}({})",
                finalDeep, deepSource,
                finalMaxDepth, maxDepthSource,
                (excludes != null ? excludes.size() : 0), excludesSource);
        }

        CompareService compareService = CompareService.createDefault(effective, registry);

        // 简易指标占位（Micrometer 全局注册表可用时记录）
        try {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            io.micrometer.core.instrument.Metrics.counter("tfi.builder.init.count").increment();
            io.micrometer.core.instrument.Timer.builder("tfi.builder.init.duration.milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(io.micrometer.core.instrument.Metrics.globalRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            // 冲突计数（P0 占位：当前无冲突检测，统一记录0不增加）
        } catch (Throwable ignored) {
            // 指标不可用时静默
        }

        return new TfiContext(compareService, effective);
    }
}
