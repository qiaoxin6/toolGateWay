package com.toolgateway.gateway.adapter;

import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;

import java.util.Map;

/**
 * 防腐层（Anti-corruption Layer）：每个外部协议都有一个适配器，
 * 将其原生格式转换为统一的 ToolRequest/ToolResponse 模型。
 */
public interface ProtocolAdapter {

    /** 该适配器处理的协议标识符（如 "http", "mcp", "grpc"） */
    String protocolName();

    /** 将外部原始请求转换为统一模型 */
    ToolRequest adapt(Object rawRequest, Map<String, String> headers);

    /** 将统一响应转换回协议特定格式 */
    Object adaptResponse(ToolResponse<?> response);
}
