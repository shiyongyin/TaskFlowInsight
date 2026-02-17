package com.syy.taskflowinsight.actuator.support;

/**
 * 会话ID脱敏工具类。
 *
 * <p>对外暴露的 Session ID 统一经过此工具脱敏，仅保留首尾各 4 字符，
 * 中间以 {@code ***} 代替，防止原始标识符泄露。</p>
 *
 * <p>线程安全：无状态工具类，所有方法均为纯函数。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public final class SessionIdMasker {

    /** 脱敏后保留的前缀长度 */
    private static final int PREFIX_LEN = 4;

    /** 脱敏后保留的后缀长度 */
    private static final int SUFFIX_LEN = 4;

    /** 可脱敏的最短 Session ID 长度 */
    private static final int MIN_MASKABLE_LEN = PREFIX_LEN + SUFFIX_LEN;

    /** 脱敏占位符 */
    private static final String MASK = "***";

    private SessionIdMasker() {
        // utility class
    }

    /**
     * 对 Session ID 进行脱敏处理。
     *
     * <ul>
     *   <li>{@code null} 或长度 &lt; {@value #MIN_MASKABLE_LEN} 的输入返回 {@code "***"}</li>
     *   <li>其余保留首 {@value #PREFIX_LEN} 字符和尾 {@value #SUFFIX_LEN} 字符</li>
     * </ul>
     *
     * @param sessionId 原始会话ID，可为 {@code null}
     * @return 脱敏后的会话ID，永不为 {@code null}
     */
    public static String mask(String sessionId) {
        if (sessionId == null || sessionId.length() < MIN_MASKABLE_LEN) {
            return MASK;
        }
        return sessionId.substring(0, PREFIX_LEN)
                + MASK
                + sessionId.substring(sessionId.length() - SUFFIX_LEN);
    }
}
