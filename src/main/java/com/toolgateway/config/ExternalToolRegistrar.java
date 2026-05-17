package com.toolgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.registry.ToolRegistry;
import com.toolgateway.execution.proxy.HttpProxyToolHandler;
import com.toolgateway.execution.proxy.McpClientToolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动时读取 gateway.external-tools 配置，
 * 根据 type 创建 HttpProxyToolHandler 或 McpClientToolHandler 并注册。
 */
@Component
public class ExternalToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ExternalToolRegistrar.class);

    private final ExternalToolProperties properties;
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public ExternalToolRegistrar(ExternalToolProperties properties,
                                 ToolRegistry registry,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerExternalTools() {
        for (ExternalToolProperties.ToolConfig cfg : properties.getExternalTools()) {
            ToolHandler handler;
            String target;

            if (cfg.getType() == RunnerType.MCP_NATIVE) {
                if (cfg.getRemoteToolName() == null || cfg.getRemoteToolName().isBlank()) {
                    log.warn("MCP tool {} missing remote-tool-name, skipping", cfg.getName());
                    continue;
                }
                handler = new McpClientToolHandler(
                        cfg.getUrl(), cfg.getRemoteToolName(),
                        cfg.getTimeout().toMillis(), objectMapper
                );
                target = cfg.getUrl() + "#" + cfg.getRemoteToolName();
                log.info("MCP client tool registered: name={}, server={}, remote={}",
                        cfg.getName(), cfg.getUrl(), cfg.getRemoteToolName());
            } else {
                handler = new HttpProxyToolHandler(
                        cfg.getUrl(), cfg.getTimeout().toMillis(), objectMapper
                );
                target = cfg.getUrl();
                log.info("HTTP sidecar tool registered: name={}, url={}", cfg.getName(), cfg.getUrl());
            }

            Map<String, Object> extra = new HashMap<>();
            extra.put("source", "YAML");
            extra.put("releaseChannel", cfg.getReleaseChannel());
            if (cfg.getRouteRule() != null && !cfg.getRouteRule().isBlank()) {
                extra.put("routeRule", cfg.getRouteRule());
            }

            ToolMetadata meta = new ToolMetadata(
                    cfg.getName(),
                    "1.0.0",
                    cfg.getDescription(),
                    Collections.emptyList(),
                    cfg.getType(),
                    target,
                    cfg.getTags(),
                    extra,
                    true
            );

            registry.register(cfg.getName(), handler, meta, cfg.getCanaryWeight());
        }
    }
}
