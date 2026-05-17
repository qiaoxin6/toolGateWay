package com.toolgateway.core.router;

import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.ToolMetadata;

/**
 * 包装 tool handler + 元数据 + 灰度信息。
 */
public record VersionedHandler(
    ToolHandler handler,
    ToolMetadata meta,
    int canaryWeight
) {}
