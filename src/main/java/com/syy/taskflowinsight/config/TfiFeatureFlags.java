package com.syy.taskflowinsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TFI 特性开关配置
 *
 * <p>提供统一的特性开关治理，便于线上风险控制与临时回退。</p>
 *
 * <p><b>配置示例（Spring - application.yml）：</b></p>
 * <pre>{@code
 * tfi:
 *   api:
 *     facade:
 *       enabled: true  # Facade 入口总开关（默认 true）
 *   render:
 *     masking:
 *       enabled: true  # 敏感字段掩码开关（默认 true）
 * }</pre>
 *
 * <p><b>非 Spring 场景（JVM 参数）：</b></p>
 * <pre>
 * -Dtfi.api.facade.enabled=false
 * -Dtfi.render.masking.enabled=false
 * </pre>
 *
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>facade 关闭：仅用于紧急排障，关闭后 TFI API 返回安全默认值</li>
 *   <li>masking 关闭：可能泄露敏感信息，谨慎使用</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 */
@Component
@ConfigurationProperties(prefix = "tfi")
public class TfiFeatureFlags {

    /**
     * System Property 键名
     */
    private static final String FACADE_ENABLED_KEY = "tfi.api.facade.enabled";
    private static final String MASKING_ENABLED_KEY = "tfi.render.masking.enabled";

    /**
     * 默认值
     */
    private static final boolean DEFAULT_FACADE_ENABLED = true;
    private static final boolean DEFAULT_MASKING_ENABLED = true;

    /**
     * API 相关配置
     */
    private Api api = new Api();

    /**
     * 渲染相关配置
     */
    private Render render = new Render();

    /**
     * API 配置
     */
    public static class Api {
        private Facade facade = new Facade();

        public Facade getFacade() {
            return facade;
        }

        public void setFacade(Facade facade) {
            this.facade = facade;
        }
    }

    /**
     * Facade 配置
     */
    public static class Facade {
        private boolean enabled = DEFAULT_FACADE_ENABLED;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 渲染配置
     */
    public static class Render {
        private Masking masking = new Masking();

        public Masking getMasking() {
            return masking;
        }

        public void setMasking(Masking masking) {
            this.masking = masking;
        }
    }

    /**
     * 掩码配置
     */
    public static class Masking {
        private boolean enabled = DEFAULT_MASKING_ENABLED;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    // Getters & Setters
    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Render getRender() {
        return render;
    }

    public void setRender(Render render) {
        this.render = render;
    }

    // ==================== 静态工具方法（非 Spring 场景兜底）====================

    /**
     * 检查 Facade 是否启用
     *
     * <p>优先级：Spring bean > System property > 环境变量 > 默认值</p>
     *
     * @return true 表示启用，false 表示禁用
     */
    public static boolean isFacadeEnabled() {
        // 尝试从 System Property 读取
        String property = System.getProperty(FACADE_ENABLED_KEY);
        if (property != null) {
            return Boolean.parseBoolean(property);
        }

        // 尝试从环境变量读取（将点转为下划线大写）
        String envKey = FACADE_ENABLED_KEY.replace('.', '_').toUpperCase();
        String env = System.getenv(envKey);
        if (env != null) {
            return Boolean.parseBoolean(env);
        }

        // 使用默认值
        return DEFAULT_FACADE_ENABLED;
    }

    /**
     * 检查掩码是否启用
     *
     * <p>优先级：Spring bean > System property > 环境变量 > 默认值</p>
     *
     * @return true 表示启用，false 表示禁用
     */
    public static boolean isMaskingEnabled() {
        // 尝试从 System Property 读取
        String property = System.getProperty(MASKING_ENABLED_KEY);
        if (property != null) {
            return Boolean.parseBoolean(property);
        }

        // 尝试从环境变量读取
        String envKey = MASKING_ENABLED_KEY.replace('.', '_').toUpperCase();
        String env = System.getenv(envKey);
        if (env != null) {
            return Boolean.parseBoolean(env);
        }

        // 使用默认值
        return DEFAULT_MASKING_ENABLED;
    }
}
