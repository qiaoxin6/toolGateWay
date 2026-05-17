package com.toolgateway.core.registry;

import com.toolgateway.core.annotation.Tool;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.core.router.CanaryRouter;
import com.toolgateway.core.router.VersionedHandler;
import com.toolgateway.core.validation.SchemaValidator;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    // name → all versions (stable + canary)
    private final Map<String, List<VersionedHandler>> handlers = new ConcurrentHashMap<>();

    // name → stable version metadata（向后兼容）
    private final Map<String, ToolMetadata> metadata = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext appCtx;

    @Autowired
    private MeterRegistry meterRegistry;

    // ── Register / Unregister ──────────────────────────────────────────

    public void register(String name, ToolHandler handler, ToolMetadata meta) {
        register(name, handler, meta, 0);
    }

    /** 注册工具到指定通道。canaryWeight 仅对 canary/beta 通道生效。 */
    public void register(String name, ToolHandler handler, ToolMetadata meta, int canaryWeight) {
        String channel = (String) meta.extra().getOrDefault("releaseChannel", "stable");
        VersionedHandler vh = new VersionedHandler(handler, meta, canaryWeight);

        handlers.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(vh);

        // stable 通道覆盖 metadata 快照
        if ("stable".equals(channel)) {
            metadata.put(name, meta);
        } else if (!metadata.containsKey(name)) {
            metadata.put(name, meta);
        }

        log.info("Tool registered: name={}, channel={}, version={}, weight={}",
                name, channel, meta.version(), canaryWeight);
    }

    public void unregister(String name) {
        handlers.remove(name);
        metadata.remove(name);
        log.info("Tool unregistered: name={}", name);
    }

    public boolean isRegistered(String name) {
        return handlers.containsKey(name);
    }

    public void setEnabled(String name, boolean enabled) {
        ToolMetadata old = metadata.get(name);
        if (old == null) throw new ToolNotFoundException(name);
        ToolMetadata updated = new ToolMetadata(
                old.name(), old.version(), old.description(), old.params(),
                old.runnerType(), old.target(), old.tags(), old.extra(), enabled
        );
        metadata.put(name, updated);
        // 同步更新所有版本
        List<VersionedHandler> versions = handlers.get(name);
        if (versions != null) {
            for (int i = 0; i < versions.size(); i++) {
                VersionedHandler vh = versions.get(i);
                versions.set(i, new VersionedHandler(vh.handler(), updated, vh.canaryWeight()));
            }
        }
        log.info("Tool {}: name={}", enabled ? "enabled" : "disabled", name);
    }

    // ── Discovery ──────────────────────────────────────────────────────

    public List<ToolMetadata> listTools() {
        return new ArrayList<>(metadata.values());
    }

    /** 获取某工具的所有版本 */
    public List<VersionedHandler> getVersions(String name) {
        List<VersionedHandler> list = handlers.get(name);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
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
        if (meta == null) throw new ToolNotFoundException(name);
        return meta;
    }

    public ToolHandler getHandler(String name) {
        List<VersionedHandler> list = handlers.get(name);
        if (list == null || list.isEmpty()) throw new ToolNotFoundException(name);
        return list.get(0).handler();
    }

    // ── Invocation ─────────────────────────────────────────────────────

    public ToolResponse<?> invoke(String toolName, ToolRequest request) {
        List<VersionedHandler> versions = handlers.get(toolName);
        if (versions == null || versions.isEmpty()) {
            return ToolResponse.fail(ErrorCode.TOOL_NOT_FOUND.code,
                    "Tool not found: " + toolName);
        }

        // 灰度路由
        VersionedHandler selected = CanaryRouter.select(toolName, versions, request);
        ToolMetadata selectedMeta = selected.meta();

        // 禁用拦截
        if (!selectedMeta.enabled()) {
            return ToolResponse.fail(ErrorCode.TOOL_EXEC_FAILED.code,
                    "Tool [" + toolName + "] is disabled");
        }

        // Schema 校验
        List<String> errors = SchemaValidator.validate(request.params(), selectedMeta.params());
        if (!errors.isEmpty()) {
            return ToolResponse.fail(ErrorCode.PARAM_INVALID.code,
                    "param validation failed: " + String.join("; ", errors));
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        ToolResponse<?> resp = selected.handler().execute(request);
        String versionTag = selectedMeta.version();
        sample.stop(Timer.builder("tool.invoke.duration")
                .tag("tool", toolName)
                .tag("version", versionTag)
                .tag("status", resp.success() ? "success" : "fail")
                .register(meterRegistry));
        meterRegistry.counter("tool.invoke.count", "tool", toolName,
                "version", versionTag).increment();
        if (!resp.success()) {
            meterRegistry.counter("tool.error.count",
                    "tool", toolName,
                    "version", versionTag,
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
                log.warn("@Tool-annotated bean {} does not implement ToolHandler, skipping",
                        bean.getClass().getName());
                continue;
            }
            Tool ann = AnnotationUtils.findAnnotation(bean.getClass(), Tool.class);
            if (ann == null) {
                log.warn("Could not resolve @Tool annotation for bean {}",
                        bean.getClass().getName());
                continue;
            }
            List<ToolMetadata.ParamSchema> paramSchemas = java.util.Arrays.stream(ann.params())
                    .map(p -> new ToolMetadata.ParamSchema(
                            p.name(), p.type(), p.description(),
                            p.required(),
                            p.defaultValue().isEmpty() ? null : p.defaultValue()))
                    .toList();

            Map<String, Object> extra = new HashMap<>();
            extra.put("source", "CODE");
            extra.put("releaseChannel", ann.releaseChannel());

            ToolMetadata meta = new ToolMetadata(
                    ann.name(), ann.version(), ann.description(),
                    paramSchemas, ann.runnerType(), ann.target(),
                    List.of(ann.tags()), extra, true
            );
            register(ann.name(), handler, meta, ann.canaryWeight());
        }
        log.info("Auto-registration complete: {} tool groups loaded", handlers.size());
        meterRegistry.gauge("tool.registry.size", metadata.size());
    }

    // ── Exceptions ─────────────────────────────────────────────────────

    public static class ToolNotFoundException extends RuntimeException {
        public ToolNotFoundException(String name) {
            super("Tool not found: " + name);
        }
    }
}
