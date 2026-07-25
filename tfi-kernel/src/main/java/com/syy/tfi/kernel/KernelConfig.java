package com.syy.tfi.kernel;

import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.IdGenerator;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.util.List;
import java.util.Objects;

/**
 * 每个 Session 在开始时捕获的不可变配置，运行中替换只影响之后创建的 Session。
 *
 * @param enabled 当前配置快照是否允许记录；默认 true，仍需与启动地板和运行期开关共同判定
 * @param sinks 按顺序同步接收冻结终态的 Sink 列表；列表及元素不可为 null，默认空列表表示不自动外发
 * @param sampler 根 Session 创建前调用一次的采样策略；不可为 null，默认始终记录
 * @param idGenerator 为已采样 Session 生成标识的策略；不可为 null，默认生成单调 ULID
 * @param clock 提供 epoch 毫秒和单调纳秒的时间源；不可为 null，默认使用 JDK 系统时钟
 * @param maxStages 单个 Session 允许的 Stage 总数，包含根 Stage，范围 1..1024，默认 64
 * @param maxSessionEncodedBytes 最终 Session canonical JSON 的 UTF-8 字节预算，范围 1..1048576，默认 12288
 * @param maxRecordEncodedBytes 单条 Record 及单个属性冻结值的 UTF-8 字节上限，范围 1..65536，默认 2048，
 *                              且不得超过 Session 预算
 * @param maxAttrs Session 全树允许的属性槽位数，范围 0..256，默认 32；仅覆盖同一节点同一 key 不增加计数
 */
public record KernelConfig(
        boolean enabled,
        List<FlowSink> sinks,
        Sampler sampler,
        IdGenerator idGenerator,
        KernelClock clock,
        int maxStages,
        int maxSessionEncodedBytes,
        int maxRecordEncodedBytes,
        int maxAttrs) {

    /** {@link #maxStages()} 可接受的硬上限，计数包含根 Stage。 */
    private static final int MAX_STAGES_CEILING = 1_024;
    /** {@link #maxSessionEncodedBytes()} 可接受的硬上限，单位为 UTF-8 字节。 */
    private static final int MAX_SESSION_BYTES_CEILING = 1_048_576;
    /** {@link #maxRecordEncodedBytes()} 可接受的硬上限，单位为 UTF-8 字节。 */
    private static final int MAX_RECORD_BYTES_CEILING = 65_536;
    /** {@link #maxAttrs()} 可接受的全树属性槽位数硬上限。 */
    private static final int MAX_ATTRS_CEILING = 256;

    /** 校验边界并防御性复制 Sink 顺序。 */
    public KernelConfig {
        sinks = List.copyOf(Objects.requireNonNull(sinks, "sinks"));
        sampler = Objects.requireNonNull(sampler, "sampler");
        idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        clock = Objects.requireNonNull(clock, "clock");
        requireRange("maxStages", maxStages, 1, MAX_STAGES_CEILING);
        requireRange("maxSessionEncodedBytes", maxSessionEncodedBytes, 1, MAX_SESSION_BYTES_CEILING);
        requireRange("maxRecordEncodedBytes", maxRecordEncodedBytes, 1, MAX_RECORD_BYTES_CEILING);
        requireRange("maxAttrs", maxAttrs, 0, MAX_ATTRS_CEILING);
        if (maxRecordEncodedBytes > maxSessionEncodedBytes) {
            throw new IllegalArgumentException("maxRecordEncodedBytes must not exceed session budget");
        }
    }

    /** 返回安全、无自动外发的默认配置。 */
    public static KernelConfig defaults() {
        KernelClock clock = KernelClock.system();
        return new KernelConfig(
                true, List.of(), Sampler.always(), IdGenerator.ulid(clock), clock,
                64, 12_288, 2_048, 32);
    }

    private static void requireRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
    }
}
