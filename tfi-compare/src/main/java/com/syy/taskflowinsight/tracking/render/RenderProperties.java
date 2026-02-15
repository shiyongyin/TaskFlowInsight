package com.syy.taskflowinsight.tracking.render;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 渲染配置属性
 *
 * <p>支持配置敏感字段掩码规则。</p>
 *
 * <p><b>配置示例（application.yml）：</b></p>
 * <pre>{@code
 * tfi:
 *   render:
 *     mask-fields:
 *       - password
 *       - secret
 *       - token
 *       - apiKey
 *       - internal*
 *       - credential*
 * }</pre>
 *
 * <p><b>非 Spring 场景兜底：</b></p>
 * <p>通过 {@code System.getProperty("tfi.render.mask-fields")} 配置，
 * 多个规则用逗号分隔，例如：{@code -Dtfi.render.mask-fields=password,secret,token}</p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 */
@Component
@ConfigurationProperties(prefix = "tfi.render")
public class RenderProperties {

    /**
     * 默认掩码字段列表
     */
    private static final List<String> DEFAULT_MASK_FIELDS = Arrays.asList(
        "password",
        "secret",
        "token",
        "apiKey",
        "internal*",
        "ssn",
        "idCard",
        "credential*"
    );

    /**
     * System Property 键名
     */
    private static final String SYSTEM_PROPERTY_KEY = "tfi.render.mask-fields";

    /**
     * 掩码字段规则列表
     *
     * <p>支持：</p>
     * <ul>
     *   <li>字段名匹配：{@code password}</li>
     *   <li>全路径匹配：{@code user.credentials.password}</li>
     *   <li>通配符：{@code internal*}（匹配 internalFlag, internalToken 等）</li>
     * </ul>
     */
    private List<String> maskFields = new ArrayList<>(DEFAULT_MASK_FIELDS);

    /**
     * 获取掩码字段规则列表
     */
    public List<String> getMaskFields() {
        return maskFields;
    }

    /**
     * 设置掩码字段规则列表
     */
    public void setMaskFields(List<String> maskFields) {
        this.maskFields = maskFields != null ? maskFields : new ArrayList<>(DEFAULT_MASK_FIELDS);
    }

    /**
     * 获取默认掩码字段列表（静态方法，供非 Spring 场景使用）
     */
    public static List<String> getDefaultMaskFields() {
        return new ArrayList<>(DEFAULT_MASK_FIELDS);
    }

    /**
     * 从 System Property 加载掩码字段配置（非 Spring 场景兜底）
     *
     * @return 掩码字段列表，未配置时返回默认清单
     */
    public static List<String> loadFromSystemProperty() {
        String property = System.getProperty(SYSTEM_PROPERTY_KEY);
        if (property == null || property.trim().isEmpty()) {
            return getDefaultMaskFields();
        }

        // 按逗号分隔，去除空白
        List<String> fields = new ArrayList<>();
        for (String field : property.split(",")) {
            String trimmed = field.trim();
            if (!trimmed.isEmpty()) {
                fields.add(trimmed);
            }
        }

        return fields.isEmpty() ? getDefaultMaskFields() : fields;
    }
}
