package com.toolgateway.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.core.registry.ToolRegistry;
import com.toolgateway.execution.proxy.HttpProxyToolHandler;
import com.toolgateway.execution.proxy.McpClientToolHandler;
import com.toolgateway.guard.aspect.ToolGuardAspect;
import com.toolgateway.persistence.ToolRegistryEntity;
import com.toolgateway.persistence.ToolRegistryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理面 API —— 动态注册/删除 HTTP 代理工具、聚合监控状态。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ToolGuardAspect guardAspect;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private ToolRegistryRepository repository;

    // ── 动态注册 ────────────────────────────────────────────────────────

    /**
     * 动态注册外部工具（HTTP 代理 或 MCP Client）。
     *
     * Body: {"name":"...","description":"...","url":"http://...","timeoutMs":30000,
     *        "type":"HTTP_SIDECAR|MCP_NATIVE","remoteToolName":"...","tags":["..."]}
     */
    @PostMapping("/tools")
    public ToolResponse<?> registerTool(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ToolResponse.fail(ErrorCode.PARAM_INVALID.code, "name is required");
        }

        ToolMetadata existing = null;
        try {
            existing = registry.getMeta(name);
        } catch (ToolRegistry.ToolNotFoundException ignored) {
        }
        if (existing != null) {
            return ToolResponse.fail(ErrorCode.PARAM_INVALID.code, "tool already exists: " + name);
        }

        String url = (String) body.get("url");
        if (url == null || url.isBlank()) {
            return ToolResponse.fail(ErrorCode.PARAM_INVALID.code, "url is required");
        }

        String typeStr = (String) body.getOrDefault("type", "HTTP_SIDECAR");
        RunnerType type;
        try {
            type = RunnerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return ToolResponse.fail(ErrorCode.PARAM_INVALID.code,
                    "unsupported type: " + typeStr + ". Allowed: HTTP_SIDECAR, MCP_NATIVE");
        }

        String description = (String) body.getOrDefault("description", "");
        long timeoutMs = body.containsKey("timeoutMs")
                ? ((Number) body.get("timeoutMs")).longValue() : 30_000L;

        @SuppressWarnings("unchecked")
        List<String> tags = body.containsKey("tags")
                ? (List<String>) body.get("tags") : Collections.emptyList();

        ToolHandler handler;
        String target;

        if (type == RunnerType.MCP_NATIVE) {
            String remoteToolName = (String) body.get("remoteToolName");
            if (remoteToolName == null || remoteToolName.isBlank()) {
                return ToolResponse.fail(ErrorCode.PARAM_INVALID.code,
                        "remoteToolName is required for MCP_NATIVE type");
            }
            handler = new McpClientToolHandler(url, remoteToolName, timeoutMs, objectMapper);
            target = url + "#" + remoteToolName;
            log.info("Admin: registered MCP client tool: name={}, server={}, remote={}",
                    name, url, remoteToolName);
        } else {
            handler = new HttpProxyToolHandler(url, timeoutMs, objectMapper);
            target = url;
            log.info("Admin: registered HTTP sidecar tool: name={}, url={}", name, url);
        }

        ToolMetadata meta = new ToolMetadata(
                name, "1.0.0", description, Collections.emptyList(),
                type, target, tags,
                Collections.singletonMap("source", "ADMIN")
        );

        registry.register(name, handler, meta);

        // 持久化到 DB
        ToolRegistryEntity entity = new ToolRegistryEntity();
        entity.setName(name);
        entity.setVersion("1.0.0");
        entity.setDescription(description);
        entity.setSource("ADMIN");
        entity.setRunnerType(type.name());
        entity.setTarget(target);
        entity.setTimeoutMs(timeoutMs);
        try {
            entity.setTags(objectMapper.writeValueAsString(tags));
        } catch (Exception e) {
            entity.setTags("[]");
        }
        entity.setExtra("{}");
        repository.insert(entity);

        return ToolResponse.success(Map.of("name", name, "url", url, "type", type.name()), 0);
    }

    // ── 删除 ────────────────────────────────────────────────────────────

    /**
     * 删除工具。仅允许删除 HTTP_SIDECAR 和 MCP_NATIVE 类型（外部代理工具）。
     */
    @DeleteMapping("/tools/{name}")
    public ToolResponse<?> deleteTool(@PathVariable String name) {
        ToolMetadata meta = registry.getMeta(name);

        if (meta.runnerType() != RunnerType.HTTP_SIDECAR
                && meta.runnerType() != RunnerType.MCP_NATIVE) {
            return ToolResponse.fail(ErrorCode.PARAM_INVALID.code,
                    "can only delete external proxy tools, not " + meta.runnerType());
        }

        registry.unregister(name);

        // 从 DB 删除
        repository.deleteByName(name);

        log.info("Admin: deleted tool: name={}", name);
        return ToolResponse.success(Map.of("name", name, "deleted", true), 0);
    }

    // ── 状态聚合 ────────────────────────────────────────────────────────

    /**
     * 工具注册中心聚合状态：健康、统计、熔断器状态。
     */
    @GetMapping("/tools/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // 健康检查
        status.put("health", healthEndpoint.health().getStatus().getCode());

        // 概览
        List<ToolMetadata> allTools = registry.listTools();
        status.put("totalTools", allTools.size());

        // 每工具详情
        var cbDetails = guardAspect.getCircuitBreakerDetails();
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolMetadata meta : allTools) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", meta.name());
            info.put("version", meta.version());
            info.put("runnerType", meta.runnerType().name());
            info.put("description", meta.description());
            info.put("target", meta.target());
            info.put("tags", meta.tags());

            // 熔断器状态
            var cb = cbDetails.getOrDefault(meta.name(), Map.of());
            info.put("circuitBreaker", cb);

            // 调用统计（从 Micrometer 读取）
            info.put("stats", Map.of(
                    "callCount", getCounter("tool.invoke.count", "tool", meta.name()),
                    "errorCount", getCounter("tool.error.count", "tool", meta.name()),
                    "avgDurationMs", getTimerAvg("tool.invoke.duration", "tool", meta.name())
            ));

            tools.add(info);
        }
        status.put("tools", tools);

        // 汇总指标
        long totalCalls = allTools.stream()
                .mapToLong(t -> getCounter("tool.invoke.count", "tool", t.name())).sum();
        long totalErrors = allTools.stream()
                .mapToLong(t -> getCounter("tool.error.count", "tool", t.name())).sum();
        status.put("totalCalls", totalCalls);
        status.put("totalErrors", totalErrors);
        status.put("errorRate", totalCalls > 0
                ? Math.round((double) totalErrors / totalCalls * 1000.0) / 1000.0 : 0);

        // 熔断器总数
        int cbOpenCount = (int) guardAspect.getCircuitBreakerStates().values().stream()
                .filter(Boolean::booleanValue).count();
        status.put("circuitBreakersOpen", cbOpenCount);

        return status;
    }

    // ── Micrometer helpers ──────────────────────────────────────────────

    private long getCounter(String metric, String tag, String value) {
        try {
            var sample = meterRegistry.get(metric).tag(tag, value).counter();
            return (long) sample.count();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getTimerAvg(String metric, String tag, String value) {
        try {
            var timer = meterRegistry.get(metric).tag(tag, value).timer();
            return Math.round(timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) * 100.0) / 100.0;
        } catch (Exception e) {
            return 0;
        }
    }
}
