package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制 Compare 事实写入 Kernel 的启动期组合，不改变两个 Core 的独立开关。
 *
 * @param enabled 是否创建 RecordPolicy 与 Recorder；关闭时两个 Core Bean 图仍存在
 * @param maxRecordedChanges 单次最多写入的 canonical change detail 数，单位为条；0 表示仅 summary
 * @param aop 可选 AOP 入口开关；程序化 Recorder 不依赖该能力
 * @since 4.0.0
 */
@ConfigurationProperties("tfi.kernel-compare")
public record TfiKernelCompareProperties(
        Boolean enabled,
        Integer maxRecordedChanges,
        Aop aop) {

    /** 补齐 integration owner 默认值，并先拒绝会产生半套 Bean 图的开关组合。 */
    public TfiKernelCompareProperties {
        enabled = enabled != null ? enabled : true;
        maxRecordedChanges = maxRecordedChanges != null
                ? maxRecordedChanges : KernelCompareRecordPolicy.defaults().maxRecordedChanges();
        aop = aop != null ? aop : new Aop(null);
        try {
            new KernelCompareRecordPolicy(maxRecordedChanges);
        } catch (RuntimeException exception) {
            throw invalid(
                    "tfi.kernel-compare.max-recorded-changes",
                    "cannot construct KernelCompareRecordPolicy",
                    exception);
        }
        if (aop.enabled() && !enabled) {
            throw invalid(
                    "tfi.kernel-compare.aop.enabled",
                    "requires tfi.kernel-compare.enabled=true",
                    null);
        }
    }

    /** 创建 bridge 的不可变 detail 策略。 */
    KernelCompareRecordPolicy toRecordPolicy() {
        return new KernelCompareRecordPolicy(maxRecordedChanges);
    }

    private static IllegalArgumentException invalid(String path, String reason, Throwable cause) {
        return new IllegalArgumentException("KCS_E_1003: " + path + " " + reason, cause);
    }

    /**
     * 可选切面能力的独立开关；依赖存在与否由后续 AOP 阶段校验。
     *
     * @param enabled 是否请求创建 AOP 基础设施，默认 false
     */
    public record Aop(Boolean enabled) {

        /** 缺省关闭，保证程序化组合不隐式创建代理。 */
        public Aop {
            enabled = enabled != null ? enabled : false;
        }
    }
}
