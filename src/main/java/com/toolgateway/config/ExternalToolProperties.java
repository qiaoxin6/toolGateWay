package com.toolgateway.config;

import com.toolgateway.core.model.RunnerType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部工具配置，绑定 application.yml 中 gateway.external-tools 列表。
 */
@ConfigurationProperties(prefix = "gateway")
public class ExternalToolProperties {

    private List<ToolConfig> externalTools = new ArrayList<>();

    public List<ToolConfig> getExternalTools() {
        return externalTools;
    }

    public void setExternalTools(List<ToolConfig> externalTools) {
        this.externalTools = externalTools;
    }

    /**
     * 单个外部工具配置。
     */
    public static class ToolConfig {

        /** 工具名称（唯一标识） */
        private String name;

        /** 工具类型，默认 HTTP_SIDECAR */
        private RunnerType type = RunnerType.HTTP_SIDECAR;

        /** 工具描述 */
        private String description;

        /** HTTP/MCP Server URL */
        private String url;

        /**
         * 远端工具名 —— 仅 MCP_NATIVE 类型需要。
         * 指远端 MCP Server 上注册的工具名。
         */
        private String remoteToolName;

        /** 调用超时，默认 30s */
        private Duration timeout = Duration.ofSeconds(30);

        /** 标签列表 */
        private List<String> tags = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public RunnerType getType() { return type; }
        public void setType(RunnerType type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getRemoteToolName() { return remoteToolName; }
        public void setRemoteToolName(String remoteToolName) { this.remoteToolName = remoteToolName; }

        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
}
