package com.toolgateway.core.model;

import java.util.Map;

/**
 * 统一内部请求模型 —— 所有外部协议（HTTP、MCP、gRPC）最终都转换为该格式。
 */
public record ToolRequest(

    /** 工具名称，如 mysql_query、python_search */
    String toolName,

    /** 工具参数键值对，如 {"sql": "SELECT 1", "limit": 10} */
    Map<String, Object> params,

    /** 全链路追踪ID，贯穿网关→护栏→执行的整个调用链 */
    String traceId,

    /** 请求上下文：协议类型、租户、调用方角色、自定义扩展字段等 */
    Map<String, String> context
) {

    /** 从上下文提取租户标识，默认 "default" */
    public String getTenant() {
        return context != null ? context.getOrDefault("tenant", "default") : "default";
    }

    /** 从上下文提取调用方标识，默认 "anonymous" */
    public String getCaller() {
        return context != null ? context.getOrDefault("caller", "anonymous") : "anonymous";
    }

    /** 从上下文提取协议类型，默认 "unknown" */
    public String getProtocol() {
        return context != null ? context.getOrDefault("protocol", "unknown") : "unknown";
    }
}
