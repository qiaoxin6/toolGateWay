package com.toolgateway.core.model;

/**
 * 工具运行类型 —— 决定注册中心如何执行该工具。
 */
public enum RunnerType {

    /** 本地执行：工具与注册中心在同一 JVM 内，直接实现 {@link com.toolgateway.core.handler.ToolHandler} */
    LOCAL,

    /** HTTP Sidecar：工具作为独立 HTTP 服务运行，通过 HTTP 代理调用（适用于 Python/Go/Node.js 工具） */
    HTTP_SIDECAR,

    /** gRPC Sidecar：工具作为独立 gRPC 服务运行，通过 gRPC 代理调用 */
    GRPC_SIDECAR,

    /** 子进程：工具为命令行脚本，通过 stdin/stdout 交互（适用于一次性批处理） */
    SUBPROCESS,

    /** MCP 原生：工具本身暴露 MCP 协议，注册中心反向充当 MCP 客户端调用 */
    MCP_NATIVE
}
