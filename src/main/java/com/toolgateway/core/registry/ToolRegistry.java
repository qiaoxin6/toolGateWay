package com.toolgateway.core.registry;

import com.toolgateway.core.annotation.Tool;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, ToolMetadata> metadata = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext appCtx;

    @Autowired
    // Micrometer 的指标注册中心，用于记录工具调用指标、错误率等
    private MeterRegistry meterRegistry;

    // ── Register / Unregister ──────────────────────────────────────────

    public void register(String name, ToolHandler handler, ToolMetadata meta) {
        handlers.put(name, handler);
        metadata.put(name, meta);
        log.info("Tool registered: name={}, version={}, runner={}", name, meta.version(), meta.runnerType());
    }

    public void unregister(String name) {
        handlers.remove(name);
        metadata.remove(name);
        log.info("Tool unregistered: name={}", name);
    }

    /** 工具是否已在注册表中 */
    public boolean isRegistered(String name) {
        return handlers.containsKey(name);
    }

    // ── Discovery ──────────────────────────────────────────────────────

    public List<ToolMetadata> listTools() {
        return new ArrayList<>(metadata.values());
    }

    public List<ToolMetadata> search(String keyword, List<String> tags) {
        return metadata.values().stream()
                .filter(m -> keyword == null || keyword.isBlank()
                        || m.name().toLowerCase().contains(keyword.toLowerCase())
                        || m.description().toLowerCase().contains(keyword.toLowerCase()))
                .filter(m -> tags == null || tags.isEmpty()
                        || !Collections.disjoint(m.tags(), tags))
                .toList();
    }

    public ToolMetadata getMeta(String name) {
        ToolMetadata meta = metadata.get(name);
        if (meta == null) {
            throw new ToolNotFoundException(name);
        }
        return meta;
    }

    public ToolHandler getHandler(String name) {
        ToolHandler handler = handlers.get(name);
        if (handler == null) {
            throw new ToolNotFoundException(name);
        }
        return handler;
    }

    // ── Invocation ─────────────────────────────────────────────────────

    public ToolResponse<?> invoke(String toolName, ToolRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        // 获取工具执行器
        ToolHandler handler = getHandler(toolName);
        // 调用工具执行器
        ToolResponse<?> resp = handler.execute(request);
        sample.stop(Timer.builder("tool.invoke.duration")
                .tag("tool", toolName)
                .tag("status", resp.success() ? "success" : "fail")
                .register(meterRegistry));
        meterRegistry.counter("tool.invoke.count", "tool", toolName).increment();
        if (!resp.success()) {
            meterRegistry.counter("tool.error.count",
                    "tool", toolName,
                    "error", resp.errorCode()).increment();
        }
        return resp;
    }

    // ── Auto-registration on startup ───────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void autoRegister() {
        Map<String, Object> beans = appCtx.getBeansWithAnnotation(Tool.class);
        for (Object bean : beans.values()) {
            if (!(bean instanceof ToolHandler handler)) {
                log.warn("@Tool-annotated bean {} does not implement ToolHandler, skipping", bean.getClass().getName());
                continue;
            }
            Tool ann = AnnotationUtils.findAnnotation(bean.getClass(), Tool.class);
            if (ann == null) {
                log.warn("Could not resolve @Tool annotation for bean {}", bean.getClass().getName());
                continue;
            }
            ToolMetadata meta = new ToolMetadata(
                    ann.name(),
                    ann.version(),
                    ann.description(),
                    List.of(),           // param schema can be introspected later
                    ann.runnerType(),
                    ann.target(),
                    List.of(ann.tags()),
                    Collections.singletonMap("source", "CODE")
            );
            register(ann.name(), handler, meta);
        }
        log.info("Auto-registration complete: {} tools loaded", handlers.size());
        meterRegistry.gauge("tool.registry.size", handlers.size());
    }

    // ── Exceptions ─────────────────────────────────────────────────────

    public static class ToolNotFoundException extends RuntimeException {
        public ToolNotFoundException(String name) {
            super("Tool not found: " + name);
        }
    }
}
