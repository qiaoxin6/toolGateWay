package com.toolgateway.guard;

import com.toolgateway.config.HealthCheckProperties;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Date;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 工具健康检查器 —— 定时 ping 外部工具（HTTP_SIDECAR / MCP_NATIVE），
 * 连续失败达到阈值后自动禁用工具。
 */
@Component
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final ToolRegistry registry;
    private final HealthCheckProperties props;
    private final TaskScheduler scheduler;
    private final HttpClient pingClient;

    private final Map<String, HealthStatus> healthMap = new ConcurrentHashMap<>();
    private ScheduledFuture<?> scheduledTask;

    public HealthChecker(ToolRegistry registry, HealthCheckProperties props,
                         TaskScheduler scheduler) {
        this.registry = registry;
        this.props = props;
        this.scheduler = scheduler;
        this.pingClient = HttpClient.newBuilder()
                .connectTimeout(props.getPingTimeout())
                .build();
    }

    /** 启动后按配置间隔开始调度 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.isEnabled()) {
            log.info("Health checker is disabled");
            return;
        }
        scheduledTask = scheduler.scheduleWithFixedDelay(
                this::check,
                new Date(System.currentTimeMillis() + 10_000),  // 首次延迟 10s
                props.getInterval().toMillis()                   // 间隔 ms
        );
        log.info("Health checker started: interval={}, threshold={}",
                props.getInterval(), props.getFailureThreshold());
    }

    public void check() {
        for (ToolMetadata meta : registry.listTools()) {
            RunnerType type = meta.runnerType();
            if (type != RunnerType.HTTP_SIDECAR && type != RunnerType.MCP_NATIVE) {
                continue;
            }

            boolean alive = ping(meta);
            HealthStatus status = healthMap.computeIfAbsent(meta.name(),
                    k -> new HealthStatus());

            if (alive) {
                status.consecutiveFailures = 0;
                status.healthy = true;
            } else {
                status.consecutiveFailures++;
                status.healthy = false;
                log.warn("Health check failed: name={}, failures={}/{}",
                        meta.name(), status.consecutiveFailures,
                        props.getFailureThreshold());

                if (props.getFailureThreshold() > 0
                        && status.consecutiveFailures >= props.getFailureThreshold()) {
                    try {
                        registry.setEnabled(meta.name(), false);
                        log.warn("Auto-disabled tool: name={}", meta.name());
                        status.consecutiveFailures = 0;
                    } catch (Exception ignored) {
                    }
                }
            }
            status.lastCheck = LocalDateTime.now();
        }
    }

    // ── Ping ───────────────────────────────────────────────────

    private boolean ping(ToolMetadata meta) {
        try {
            String target = meta.target();
            if (target == null || target.isBlank()) return true; // 无 target 视为健康

            if (meta.runnerType() == RunnerType.MCP_NATIVE) {
                target = target.contains("#") ? target.split("#")[0] : target;
                String body = "{\"jsonrpc\":\"2.0\",\"id\":\"health\",\"method\":\"tools/list\"}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(target))
                        .timeout(props.getPingTimeout())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = pingClient.send(req,
                        HttpResponse.BodyHandlers.ofString());
                return resp.statusCode() < 500;
            } else {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(target))
                        .timeout(props.getPingTimeout())
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> resp = pingClient.send(req,
                        HttpResponse.BodyHandlers.discarding());
                return resp.statusCode() < 500;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, HealthStatus> getHealthMap() {
        return healthMap;
    }

    public static class HealthStatus {
        public volatile boolean healthy = true;
        public volatile int consecutiveFailures;
        public volatile LocalDateTime lastCheck;
    }
}
