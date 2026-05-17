package com.toolgateway.core.model;

import java.util.List;
import java.util.Map;

/**
 * 工具元数据 —— 注册时登记，发现时暴露给调用方。
 */
public record ToolMetadata(

    /** 工具唯一名称，如 mysql_query */
    String name,

    /** 语义化版本号，如 1.0.0 */
    String version,

    /** 工具功能描述 */
    String description,

    /** 参数列表，描述每个参数的名称、类型、是否必填等 */
    List<ParamSchema> params,

    /** 运行类型：本地/HTTP Sidecar/gRPC Sidecar/子进程/MCP原生 */
    RunnerType runnerType,

    /** 运行目标地址（HTTP URL、gRPC 地址、脚本路径等） */
    String target,

    /** 标签列表，用于按标签筛选工具，如 ["database", "read"] */
    List<String> tags,

    /** 扩展配置：认证角色、超时覆写、自定义字段等 */
    Map<String, Object> extra,

    /** 是否启用，默认 true。false 时调用会直接拒绝 */
    boolean enabled
) {

    /**
     * 参数 Schema —— 描述工具入参的元信息。
     */
    public record ParamSchema(

        /** 参数名 */
        String name,

        /** 参数类型：string、number、boolean、object、array */
        String type,

        /** 参数说明 */
        String description,

        /** 是否必填 */
        boolean required,

        /** 默认值（可选） */
        Object defaultValue
    ) {}
}
