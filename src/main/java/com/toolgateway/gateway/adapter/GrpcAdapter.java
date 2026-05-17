package com.toolgateway.gateway.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.grpc.Toolgateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * gRPC 协议适配器 —— 将 gRPC CallToolRequest 转为统一的 ToolRequest，
 * 将 ToolResponse 转回 CallToolResponse。
 */
@Component
public class GrpcAdapter implements ProtocolAdapter {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String protocolName() {
        return "grpc";
    }

    @Override
    public ToolRequest adapt(Object rawRequest, Map<String, String> headers) {
        Toolgateway.CallToolRequest grpcReq = (Toolgateway.CallToolRequest) rawRequest;

        Map<String, Object> params;
        try {
            String json = grpcReq.getParamsJson();
            if (json != null && !json.isBlank()) {
                params = objectMapper.readValue(json, new TypeReference<>() {});
            } else {
                params = new HashMap<>();
            }
        } catch (Exception e) {
            params = new HashMap<>();
        }

        Map<String, String> context = new HashMap<>();
        context.put("protocol", "grpc");
        if (grpcReq.getContextCount() > 0) {
            context.putAll(grpcReq.getContextMap());
        }

        return new ToolRequest(
                grpcReq.getToolName(),
                params,
                grpcReq.getTraceId(),
                context
        );
    }

    @Override
    public Object adaptResponse(ToolResponse<?> response) {
        Toolgateway.CallToolResponse.Builder builder =
                Toolgateway.CallToolResponse.newBuilder()
                        .setSuccess(response.success())
                        .setCostMs(response.costMs());

        try {
            if (response.data() != null) {
                builder.setDataJson(objectMapper.writeValueAsString(response.data()));
            }
        } catch (Exception ignored) {
        }

        if (response.errorCode() != null) {
            builder.setErrorCode(response.errorCode());
        }
        if (response.errorMsg() != null) {
            builder.setErrorMsg(response.errorMsg());
        }

        return builder.build();
    }
}
