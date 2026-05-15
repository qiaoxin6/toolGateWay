package com.toolgateway.persistence;

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
import java.util.List;

/**
 * 从 DB 恢复 ADMIN 来源的工具（CODE/YAML 由各自的加载器负责）。
 * 在 ApplicationReadyEvent 之后运行，确保 ToolRegistry 已初始化。
 */
@Component
public class PersistenceLoader {

    private static final Logger log = LoggerFactory.getLogger(PersistenceLoader.class);

    private final ToolRegistryRepository repository;
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public PersistenceLoader(ToolRegistryRepository repository,
                             ToolRegistry registry,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadFromDatabase() {
        List<ToolRegistryEntity> entities = repository.findBySource("ADMIN");
        if (entities.isEmpty()) {
            log.info("No ADMIN tools to restore from DB");
            return;
        }

        int restored = 0;
        for (ToolRegistryEntity e : entities) {
            // 跳过与 CODE/YAML 冲突的工具
            if (registry.isRegistered(e.getName())) {
                log.warn("DB tool {} conflicts with existing registry entry, skipping", e.getName());
                continue;
            }

            // 禁用检查
            if (Boolean.FALSE.equals(e.getEnabled())) {
                log.info("DB tool {} is disabled, skipping", e.getName());
                continue;
            }

            // 重建 Handler
            ToolHandler handler = buildHandler(e);
            if (handler == null) continue;

            // 解析 JSON 字段
            List<String> tags = parseTags(e.getTags());

            java.util.Map<String, Object> extra = parseExtra(e.getExtra());
            extra.put("source", "ADMIN");

            ToolMetadata meta = new ToolMetadata(
                    e.getName(),
                    e.getVersion(),
                    e.getDescription(),
                    Collections.emptyList(),
                    RunnerType.valueOf(e.getRunnerType()),
                    e.getTarget(),
                    tags,
                    extra
            );

            registry.register(e.getName(), handler, meta);
            restored++;
            log.info("Restored from DB: name={}, runnerType={}", e.getName(), e.getRunnerType());
        }
        log.info("PersistenceLoader complete: {} tools restored from DB", restored);
    }

    // ── Handler 重建 ────────────────────────────────────────────────

    private ToolHandler buildHandler(ToolRegistryEntity e) {
        long timeout = e.getTimeoutMs() != null ? e.getTimeoutMs() : 30_000L;
        try {
            RunnerType type = RunnerType.valueOf(e.getRunnerType());
            return switch (type) {
                case HTTP_SIDECAR ->
                        new HttpProxyToolHandler(e.getTarget(), timeout, objectMapper);
                case MCP_NATIVE -> {
                    String[] parts = e.getTarget().split("#", 2);
                    if (parts.length < 2) {
                        log.error("MCP tool {} has invalid target format: {}", e.getName(), e.getTarget());
                        yield null;
                    }
                    yield new McpClientToolHandler(parts[0], parts[1], timeout, objectMapper);
                }
                default -> {
                    log.warn("DB tool {} has unsupported runnerType={}, skipping",
                            e.getName(), e.getRunnerType());
                    yield null;
                }
            };
        } catch (Exception ex) {
            log.error("Failed to build handler for DB tool {}", e.getName(), ex);
            return null;
        }
    }

    // ── JSON 解析辅助 ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(tagsJson, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseExtra(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(extraJson, java.util.Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
