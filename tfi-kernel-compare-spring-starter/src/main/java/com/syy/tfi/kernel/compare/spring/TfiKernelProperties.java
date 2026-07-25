package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.IdGenerator;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 将 {@code tfi.kernel.*} 在 context 启动期冻结为 Kernel owner 的资源边界。
 *
 * <p>缺省值只从 {@link KernelConfig#defaults()} 读取；Spring 层不维护第二套默认配置。</p>
 *
 * @param enabled 当前 context 是否允许记录，仍受 JVM 启动地板和 Runtime 开关约束
 * @param maxStages 单个 Session 的 Stage 总数上限，包含 root，单位为个
 * @param maxSessionEncodedBytes 单个 Session canonical JSON 的 UTF-8 字节预算
 * @param maxRecordEncodedBytes 单条 Record 及单个属性冻结值的 UTF-8 字节预算
 * @param maxAttrs 单个 Session 全树允许的属性槽位数，单位为个
 * @since 4.0.0
 */
@ConfigurationProperties("tfi.kernel")
public record TfiKernelProperties(
        Boolean enabled,
        Integer maxStages,
        Integer maxSessionEncodedBytes,
        Integer maxRecordEncodedBytes,
        Integer maxAttrs) {

    /** 使用 Core owner 的缺省值补齐未绑定字段，并在发布配置 Bean 前校验完整预算。 */
    public TfiKernelProperties {
        KernelConfig defaults = KernelConfig.defaults();
        enabled = valueOrDefault(enabled, defaults.enabled());
        maxStages = valueOrDefault(maxStages, defaults.maxStages());
        maxSessionEncodedBytes = valueOrDefault(
                maxSessionEncodedBytes, defaults.maxSessionEncodedBytes());
        maxRecordEncodedBytes = valueOrDefault(
                maxRecordEncodedBytes, defaults.maxRecordEncodedBytes());
        maxAttrs = valueOrDefault(maxAttrs, defaults.maxAttrs());
        validateWithCore(
                defaults,
                enabled,
                maxStages,
                maxSessionEncodedBytes,
                maxRecordEncodedBytes,
                maxAttrs);
    }

    /** 组合启动期已选择的 local SPI，构造最终不可变 Kernel 配置。 */
    KernelConfig toConfig(
            List<FlowSink> sinks,
            Sampler sampler,
            IdGenerator idGenerator,
            KernelClock clock) {
        try {
            return new KernelConfig(
                    enabled,
                    sinks,
                    sampler,
                    idGenerator,
                    clock,
                    maxStages,
                    maxSessionEncodedBytes,
                    maxRecordEncodedBytes,
                    maxAttrs);
        } catch (RuntimeException exception) {
            throw invalid("tfi.kernel", "cannot construct KernelConfig", exception);
        }
    }

    private static void validateWithCore(
            KernelConfig defaults,
            boolean enabled,
            int maxStages,
            int maxSessionEncodedBytes,
            int maxRecordEncodedBytes,
            int maxAttrs) {
        try {
            new KernelConfig(
                    enabled,
                    defaults.sinks(),
                    defaults.sampler(),
                    defaults.idGenerator(),
                    defaults.clock(),
                    maxStages,
                    maxSessionEncodedBytes,
                    maxRecordEncodedBytes,
                    maxAttrs);
        } catch (RuntimeException exception) {
            throw invalid(
                    kernelPropertyPath(exception),
                    "cannot construct KernelConfig",
                    exception);
        }
    }

    private static String kernelPropertyPath(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "tfi.kernel";
        }
        if (message.contains("maxStages")) {
            return "tfi.kernel.max-stages";
        }
        if (message.contains("maxSessionEncodedBytes")) {
            return "tfi.kernel.max-session-encoded-bytes";
        }
        if (message.contains("maxRecordEncodedBytes")) {
            return "tfi.kernel.max-record-encoded-bytes";
        }
        if (message.contains("maxAttrs")) {
            return "tfi.kernel.max-attrs";
        }
        return "tfi.kernel";
    }

    private static IllegalArgumentException invalid(String path, String reason, Throwable cause) {
        return new IllegalArgumentException("KCS_E_1003: " + path + " " + reason, cause);
    }

    private static <T> T valueOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
