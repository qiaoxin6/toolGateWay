package com.toolgateway.persistence;

import java.time.LocalDateTime;

/**
 * tool_registry 表实体。
 */
public class ToolRegistryEntity {

    private String name;
    private String version;
    private String description;
    private String source;         // CODE / YAML / ADMIN
    private String runnerType;
    private String target;
    private Long timeoutMs;
    private String tags;           // JSON 数组
    private String extra;          // JSON 扩展
    private Boolean enabled;

    // ── 灰度预留 ──
    private String releaseChannel;  // stable / canary / beta
    private Integer canaryWeight;   // 0-100
    private String routeRule;       // JSON, nullable
    private Integer minStableWeight;// 0-100

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRunnerType() { return runnerType; }
    public void setRunnerType(String runnerType) { this.runnerType = runnerType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getReleaseChannel() { return releaseChannel; }
    public void setReleaseChannel(String releaseChannel) { this.releaseChannel = releaseChannel; }
    public Integer getCanaryWeight() { return canaryWeight; }
    public void setCanaryWeight(Integer canaryWeight) { this.canaryWeight = canaryWeight; }
    public String getRouteRule() { return routeRule; }
    public void setRouteRule(String routeRule) { this.routeRule = routeRule; }
    public Integer getMinStableWeight() { return minStableWeight; }
    public void setMinStableWeight(Integer minStableWeight) { this.minStableWeight = minStableWeight; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
