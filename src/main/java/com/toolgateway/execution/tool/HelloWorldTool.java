package com.toolgateway.execution.tool;

import com.toolgateway.core.annotation.Tool;
import com.toolgateway.core.annotation.ToolGuard;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.RunnerType;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.springframework.stereotype.Component;

@Component
@Tool(
    name        = "hello_world",
    version     = "1.0.0",
    description = "A simple tool that returns a fixed response",
    runnerType  = RunnerType.LOCAL,
    tags        = {"hello", "world"}
)
@ToolGuard(
    roles      = {"admin", "operator", "analyst"},
    timeoutMs  = 2000,
    retries    = 2,
    circuitBreakerThreshold = 0.5
)
public class HelloWorldTool implements ToolHandler {
     @Override
    public ToolResponse<?> execute(ToolRequest request) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        
        return ToolResponse.success("Hello, World!", 100L);
    }
}
