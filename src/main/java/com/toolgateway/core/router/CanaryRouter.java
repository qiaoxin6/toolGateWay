package com.toolgateway.core.router;

import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.model.ToolRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度路由器 —— 从名下列表中选择一个版本。
 *
 * 策略：
 *   CONTEXT  — 请求 context.canary=true 或 context.x-route=canary → canary 通道
 *   WEIGHTED — 按 canaryWeight 随机分流（stable 取 100 - canary合计）
 *   LATEST   — 默认，选 stable 最高版本
 */
public class CanaryRouter {

    private static final Logger log = LoggerFactory.getLogger(CanaryRouter.class);

    private CanaryRouter() {}

    /**
     * 从候选列表中选出要执行的版本。
     *
     * @param name      工具名
     * @param versions  该工具所有版本（至少 1 条）
     * @param request   调用请求
     * @return 选中的 VersionedHandler
     */
    public static VersionedHandler select(String name, List<VersionedHandler> versions,
                                          ToolRequest request) {
        if (versions.size() == 1) {
            return versions.get(0);
        }

        // ── ① CONTEXT 优先 ──
        VersionedHandler contextHit = selectByContext(versions, request);
        if (contextHit != null) {
            return contextHit;
        }

        // ── ② WEIGHTED ──
        int totalCanaryWeight = versions.stream()
                .filter(v -> "canary".equals(v.meta().extra().get("releaseChannel")))
                .mapToInt(VersionedHandler::canaryWeight)
                .sum();

        if (totalCanaryWeight > 0) {
            return selectByWeight(versions, totalCanaryWeight);
        }

        // ── ③ LATEST 默认 → stable 通道最高版本 ──
        return versions.stream()
                .filter(v -> "stable".equals(channelOf(v)))
                .max(Comparator.comparing(v -> parseVersion(v.meta().version())))
                .orElse(versions.get(0));
    }

    // ── 策略实现 ────────────────────────────────────────────

    private static VersionedHandler selectByContext(List<VersionedHandler> versions,
                                                     ToolRequest req) {
        Map<String, String> ctx = req.context();
        if (ctx == null) return null;

        // ① 显式 canary 标记
        if ("true".equals(ctx.get("canary"))) {
            return findChannel(versions, "canary");
        }

        // ② 按 beta 标记
        if ("true".equals(ctx.get("beta"))) {
            VersionedHandler hit = findChannel(versions, "beta");
            if (hit != null) return hit;
        }

        // ③ 按 route_rule 匹配
        for (var v : versions) {
            if ("stable".equals(channelOf(v))) continue;
            if (matchRouteRule(v, ctx)) return v;
        }

        return null;
    }

    /**
     * 解析 route_rule JSON 并匹配请求上下文。
     *
     * route_rule 格式：
     * {
     *   "tenants": ["t-a", "t-b"],          // 白名单租户
     *   "headers": {"x-env": "staging"}     // 精确 header 匹配
     * }
     * 任一条件命中即返回 true。
     */
    @SuppressWarnings("unchecked")
    private static boolean matchRouteRule(VersionedHandler v, Map<String, String> ctx) {
        Object raw = v.meta().extra().get("routeRule");
        if (raw == null) return false;

        Map<String, Object> rule;
        try {
            if (raw instanceof String s && !s.isBlank()) {
                rule = new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Map.class);
            } else if (raw instanceof Map) {
                rule = (Map<String, Object>) raw;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to parse route_rule for tool {}", v.meta().name(), e);
            return false;
        }

        // tenants 白名单
        Object tenants = rule.get("tenants");
        if (tenants instanceof List<?> list) {
            String reqTenant = ctx.get("tenant");
            if (reqTenant != null && list.contains(reqTenant)) {
                return true;
            }
        }

        // headers 精确匹配
        Object headers = rule.get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            for (var entry : headerMap.entrySet()) {
                String key = entry.getKey().toString().toLowerCase();
                String expected = entry.getValue() != null ? entry.getValue().toString() : null;
                String actual = ctx.get(key);
                if (expected != null && expected.equals(actual)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static VersionedHandler findChannel(List<VersionedHandler> versions,
                                                 String channel) {
        return versions.stream()
                .filter(v -> channel.equals(channelOf(v)))
                .findFirst()
                .orElse(null);
    }

    private static VersionedHandler selectByWeight(List<VersionedHandler> versions,
                                                    int totalCanaryWeight) {
        int stableWeight = Math.max(0, 100 - totalCanaryWeight);
        int dice = ThreadLocalRandom.current().nextInt(100);

        int cursor = 0;
        for (var v : versions) {
            if ("stable".equals(channelOf(v))) {
                cursor += stableWeight;
            } else if ("canary".equals(channelOf(v))) {
                cursor += v.canaryWeight();
            }
            if (dice < cursor) {
                log.debug("Canary select: channel={}, weight cursor={}", channelOf(v), cursor);
                return v;
            }
        }
        return versions.get(0);
    }

    private static String channelOf(VersionedHandler vh) {
        Object ch = vh.meta().extra().get("releaseChannel");
        return ch != null ? ch.toString() : "stable";
    }

    private static int parseVersion(String v) {
        try {
            String[] parts = v.split("\\.");
            return Integer.parseInt(parts[0]) * 10000
                 + Integer.parseInt(parts[1]) * 100
                 + Integer.parseInt(parts[2]);
        } catch (Exception e) {
            return 0;
        }
    }
}
