package com.toolgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 工具健康检查配置，绑定 gateway.health-check。
 */
@ConfigurationProperties(prefix = "gateway.health-check")
public class HealthCheckProperties {

    /** 是否启用健康检查，默认 true */
    private boolean enabled = true;

    /** 检查间隔，默认 30s */
    private Duration interval = Duration.ofSeconds(30);

    /** 连续失败多少次后自动禁用工具，0=不自动禁用 */
    private int failureThreshold = 3;

    /** 单次 ping 超时 */
    private Duration pingTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval = interval; }

    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }

    public Duration getPingTimeout() { return pingTimeout; }
    public void setPingTimeout(Duration pingTimeout) { this.pingTimeout = pingTimeout; }
}
