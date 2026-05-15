package com.toolgateway.guard.exception;

public class ToolTimeoutException extends RuntimeException {
    public ToolTimeoutException(String toolName, long timeoutMs) {
        super("Tool [" + toolName + "] exceeded timeout of " + timeoutMs + "ms");
    }
}
