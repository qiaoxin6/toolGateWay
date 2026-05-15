package com.toolgateway.core.handler;

import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;

/**
 * Every tool — local or remote, Java or polyglot — implements this interface.
 */
@FunctionalInterface
public interface ToolHandler {
    ToolResponse<?> execute(ToolRequest request);
}
