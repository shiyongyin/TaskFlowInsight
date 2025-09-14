package com.syy.taskflowinsight.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "taskflow.monitoring.endpoint")
public class MonitoringEndpointProperties {
    /** 是否启用自定义Actuator端点（默认关闭） */
    private boolean enabled = false;

}

