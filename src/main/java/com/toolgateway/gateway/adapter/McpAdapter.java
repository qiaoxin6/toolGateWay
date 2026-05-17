package com.toolgateway.gateway.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class McpAdapter implements ProtocolAdapter {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String protocolName() {
        return "mcp";
    }

    @Override
    public ToolRequest adapt(Object rawRequest, Map<String, String> headers) {
        JsonNode rpc = (JsonNode) rawRequest;

        String method = rpc.has("method") ? rpc.get("method").asText() : "tools/call";
        String rpcId = rpc.has("id") ? rpc.get("id").asText() : null;
        JsonNode params = rpc.get("params");
        if (params == null) {
            params = objectMapper.createObjectNode();
        }

        String toolName = params.has("name")
                ? params.get("name").asText()
                : "unknown";

        Map<String, Object> toolArgs = params.has("arguments")
                ? objectMapper.convertValue(params.get("arguments"), new TypeReference<>() {})
                : new HashMap<>();

        Map<String, String> context = new HashMap<>();
        context.put("protocol", "mcp");
        context.put("jsonrpc", rpc.has("jsonrpc") ? rpc.get("jsonrpc").asText() : "2.0");
        if (params.has("role")) {
            context.put("role", params.get("role").asText());
        }
        if (params.has("canary")) {
            context.put("canary", params.get("canary").asText());
        }

        return new ToolRequest(toolName, toolArgs, rpcId, context);
    }

    @Override
    public Object adaptResponse(ToolResponse<?> response) {
        Map<String, Object> result = new HashMap<>();
        result.put("jsonrpc", "2.0");

        if (response.success()) {
            result.put("result", response.data());
        } else {
            result.put("error", Map.of(
                    "code", mapErrorCode(response.errorCode()),
                    "message", response.errorMsg()
            ));
        }
        return result;
    }

    /** 将内部错误码映射为 JSON-RPC 错误码 */
    private int mapErrorCode(String code) {
        if (code == null) return -32603;
        return switch (code) {
            case "E001" -> -32601; // 工具未找到
            case "E002" -> -32602; // 参数无效
            case "E100" -> -32001; // 认证拒绝
            case "E102" -> -32002; // 超时
            default     -> -32603; // 内部错误
        };
    }
}
