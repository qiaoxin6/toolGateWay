package com.toolgateway.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.core.registry.ToolRegistry;
import com.toolgateway.gateway.adapter.ProtocolAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private List<ProtocolAdapter> adapters;

    // ── HTTP 协议入口 ────────────────────────────────────────────────────

    /**
     * HTTP 协议调用工具。
     *
     * @param toolName URL 路径中的工具名称，如 /api/tools/mysql_query 中的 mysql_query
     * @param body     HTTP 请求体，JSON 格式，内含 params（工具参数键值对）
     * @param req      原始 HttpServletRequest，用于提取 X-Role、X-Trace-Id 等头信息
     * @return 适配后的协议响应（JSON），包含 success/data/error/costMs 字段
     */
    @PostMapping({"/api/tools/{toolName}", "/tools/{toolName}"})
    public Object handleHttp(
            @PathVariable String toolName,
            @RequestBody String body,
            HttpServletRequest req) {

        ProtocolAdapter adapter = getAdapter("http");

        // 1. HTTP → 统一 ToolRequest
        Map<String, String> headers = extractHeaders(req);
        ToolRequest toolReq = adapter.adapt(Map.of("body", body, "headers", headers), headers);
        toolReq = new ToolRequest(toolName, toolReq.params(), toolReq.traceId(), toolReq.context());

        // 2. 调用工具
        ToolResponse<?> result = registry.invoke(toolName, toolReq);

        // 3. 统一 ToolResponse → HTTP 响应
        return adapter.adaptResponse(result);
    }

    // ── MCP 协议入口 ────────────────────────────────────────────────────

    /**
     * MCP 协议（JSON-RPC 2.0）入口，支持 tools/list 和 tools/call 两种方法。
     *
     * @param body JSON-RPC 请求体，格式为 {"jsonrpc":"2.0","id":"1","method":"tools/call","params":{...}}
     *             method=tools/list 时列出所有工具；
     *             method=tools/call 时 params.name 为工具名，params.arguments 为工具参数
     * @return JSON-RPC 2.0 格式响应，成功含 result 字段，失败含 error 字段
     */
    @PostMapping("/mcp")
    public Object handleMcp(@RequestBody JsonNode body) {

        ProtocolAdapter adapter = getAdapter("mcp");
        ToolRequest toolReq = adapter.adapt(body, Map.of());

        // tools/list：列出所有已注册工具
        if ("tools/list".equals(body.path("method").asText())) {
            List<ToolMetadata> tools = registry.listTools();
            return Map.of("jsonrpc", "2.0", "result", Map.of("tools", tools));
        }

        // tools/call：调用指定工具
        ToolResponse<?> result = registry.invoke(toolReq.toolName(), toolReq);

        // 构造 JSON-RPC 响应
        Map<String, Object> mcpResp = new LinkedHashMap<>();
        mcpResp.put("jsonrpc", "2.0");
        mcpResp.put("id", body.path("id").asText());
        if (result.success()) {
            mcpResp.put("result", result.data());
        } else {
            mcpResp.put("error", Map.of(
                    "code", -32603,
                    "message", result.errorMsg()
            ));
        }
        return mcpResp;
    }

    // ── 工具发现接口 ────────────────────────────────────────────────────

    /**
     * 列出或搜索已注册的工具。
     *
     * @param keyword 可选，按工具名称/描述模糊搜索
     * @param tags    可选，按标签精确过滤（如 database、script）
     * @return 匹配的工具元数据列表（名称、版本、描述、参数schema、标签等）
     */
    @GetMapping("/api/tools")
    public List<ToolMetadata> listTools(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags) {
        if (keyword != null || tags != null) {
            return registry.search(keyword, tags);
        }
        return registry.listTools();
    }

    /**
     * 查看单个工具的元数据。
     *
     * @param toolName URL 路径中的工具名称
     * @return 工具元数据（名称、版本、描述、参数schema、运行类型、标签等）
     */
    @GetMapping("/api/tools/{toolName}")
    public ToolMetadata getTool(@PathVariable String toolName) {
        return registry.getMeta(toolName);
    }

    // ── 辅助方法 ────────────────────────────────────────────────────────

    /**
     * 根据协议名称查找对应的适配器。
     *
     * @param protocol 协议标识（"http"、"mcp" 等）
     * @return 匹配的 ProtocolAdapter 实例
     * @throws IllegalArgumentException 未找到对应适配器时抛出
     */
    private ProtocolAdapter getAdapter(String protocol) {
        return adapters.stream()
                .filter(a -> a.protocolName().equals(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No adapter for protocol: " + protocol));
    }

    /**
     * 从 HttpServletRequest 中提取所有 HTTP 头，key 统一转为小写。
     *
     * @param req 原始 Servlet 请求
     * @return header name（小写）→ value 的映射
     */
    private Map<String, String> extractHeaders(HttpServletRequest req) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), req.getHeader(name));
        }
        return headers;
    }
}
