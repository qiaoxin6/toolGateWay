package com.toolgateway.execution.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * MCP Client 工具处理器 —— 网关作为 MCP Client，将 ToolRequest 转换为
 * JSON-RPC 2.0 请求，调用外部 MCP Server 的工具，将响应包装为 ToolResponse。
 *
 * 使用示例：
 *   registry.register("external_search",
 *       new McpClientToolHandler("http://mcp-server:8080/mcp", "search_tool", 30_000, mapper));
 */
public class McpClientToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(McpClientToolHandler.class);

    private final String mcpServerUrl;
    private final String remoteToolName;
    private final long timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param mcpServerUrl   外部 MCP Server 的 /mcp 端点，如 http://localhost:9000/mcp
     * @param remoteToolName 远端 MCP Server 上注册的工具名
     * @param timeoutMs      调用超时（毫秒）
     * @param objectMapper   JSON 序列化器
     */
    public McpClientToolHandler(String mcpServerUrl, String remoteToolName,
                                long timeoutMs, ObjectMapper objectMapper) {
        this.mcpServerUrl = mcpServerUrl;
        this.remoteToolName = remoteToolName;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public ToolResponse<?> execute(ToolRequest request) {
        try {
            // 构建 JSON-RPC 2.0 tools/call 请求
            Map<String, Object> rpcReq = Map.of(
                    "jsonrpc", "2.0",
                    "id", request.traceId() != null ? request.traceId() : UUID.randomUUID().toString(),
                    "method", "tools/call",
                    "params", Map.of(
                            "name", remoteToolName,
                            "arguments", (Object) request.params()
                    )
            );

            String body = objectMapper.writeValueAsString(rpcReq);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(mcpServerUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(httpReq,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() >= 500) {
                return ToolResponse.fail(ErrorCode.TOOL_EXEC_FAILED.code,
                        "MCP server returned HTTP " + httpResp.statusCode());
            }

            JsonNode rpcResp = objectMapper.readTree(httpResp.body());

            // 检查 JSON-RPC error
            if (rpcResp.has("error")) {
                JsonNode err = rpcResp.get("error");
                String msg = err.has("message") ? err.get("message").asText() : "MCP error";
                return ToolResponse.fail(ErrorCode.TOOL_EXEC_FAILED.code, msg);
            }

            // 成功：提取 result
            JsonNode result = rpcResp.get("result");
            Object data = objectMapper.treeToValue(result, Object.class);
            return ToolResponse.success(data, 0);

        } catch (Exception e) {
            log.error("MCP client call failed: server={}, tool={}", mcpServerUrl, remoteToolName, e);
            return ToolResponse.fail(ErrorCode.TOOL_EXEC_ERROR.code, e.getMessage());
        }
    }
}
