package com.toolgateway.guard.exception;

public class ToolAuthDeniedException extends RuntimeException {
    public ToolAuthDeniedException(String toolName, String role) {
        super("Access denied to tool [" + toolName + "] for role [" + role + "]");
    }
}
