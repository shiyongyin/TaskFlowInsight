package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;

import java.lang.reflect.Field;
import java.net.URI;

/**
 * URL 归一化比较器。
 *
 * <p>比较两个 URL 时执行基本归一化处理：
 * <ul>
 *   <li>协议和主机名转换为小写</li>
 *   <li>移除默认端口（http:80, https:443）</li>
 *   <li>移除尾部斜杠（路径 "/" 等价于空路径）</li>
 *   <li>解析失败时回退到字符串相等比较</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public class UrlCanonicalComparator implements PropertyComparator {

    @Override
    public boolean areEqual(Object left, Object right, Field field) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        String leftStr = String.valueOf(left);
        String rightStr = String.valueOf(right);

        // 快速路径：字符串完全相等
        if (leftStr.equals(rightStr)) {
            return true;
        }

        // 归一化后比较
        String normalizedLeft = normalize(leftStr);
        String normalizedRight = normalize(rightStr);
        return normalizedLeft.equals(normalizedRight);
    }

    /**
     * 归一化 URL 字符串。
     *
     * @param url 原始 URL 字符串
     * @return 归一化后的字符串，解析失败时返回原始字符串的 trim 结果
     */
    private String normalize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        try {
            URI uri = URI.create(url.trim());

            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            int port = uri.getPort();
            String path = uri.getPath() != null ? uri.getPath() : "";
            String query = uri.getQuery() != null ? uri.getQuery() : "";

            // 移除默认端口
            if (("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            // 移除尾部斜杠
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }

            // 重组
            StringBuilder sb = new StringBuilder();
            if (!scheme.isEmpty()) {
                sb.append(scheme).append("://");
            }
            sb.append(host);
            if (port > 0) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (!query.isEmpty()) {
                sb.append('?').append(query);
            }

            return sb.toString();
        } catch (IllegalArgumentException e) {
            // URI 解析失败，回退到 trim 比较
            return url.trim();
        }
    }
}

