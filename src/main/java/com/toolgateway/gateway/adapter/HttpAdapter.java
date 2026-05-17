package com.toolgateway.gateway.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/**
 * HTTP协议适配器，负责将HTTP请求转换为ToolRequest，将ToolResponse转换为HTTP响应。
 */

@Component
public class HttpAdapter implements ProtocolAdapter {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String protocolName() {
        return "http";
    }

    @SuppressWarnings("unchecked")
    @Override
    public ToolRequest adapt(Object rawRequest, Map<String, String> headers) {
        Map<String, Object> raw = (Map<String, Object>) rawRequest;
        String bodyStr = (String) raw.get("body");

        try {
            JsonNode json = (bodyStr != null && !bodyStr.isBlank())
                    ? objectMapper.readTree(bodyStr)
                    : objectMapper.createObjectNode();

            Map<String, Object> params = (json.has("params"))
                    ? objectMapper.convertValue(json.get("params"), new TypeReference<>() {})
                    : new HashMap<>();

            Map<String, String> context = new HashMap<>();
            context.put("protocol", "http");
            context.put("tenant", headers.getOrDefault("x-tenant", "default"));
            context.put("caller", headers.getOrDefault("x-caller", "anonymous"));
            context.put("role", headers.getOrDefault("x-role", "anonymous"));
            // 灰度路由
            if (headers.containsKey("x-canary")) {
                context.put("canary", headers.get("x-canary"));
            }

            String traceId = headers.getOrDefault("x-trace-id", UUID.randomUUID().toString());

            return new ToolRequest("__placeholder__", params, traceId, context);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse HTTP request body", e);
        }
    }

    @Override
    public Object adaptResponse(ToolResponse<?> response) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", response.success());
        if (response.data() != null) {
            result.put("data", response.data());
        }
        if (!response.success()) {
            result.put("error", Map.of(
                    "code", response.errorCode(),
                    "message", response.errorMsg()
            ));
        }
        result.put("costMs", response.costMs());
        return result;
    }
}
