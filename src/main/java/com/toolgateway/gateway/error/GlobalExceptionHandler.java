package com.toolgateway.gateway.error;

import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.core.registry.ToolRegistry.ToolNotFoundException;
import com.toolgateway.guard.exception.ToolAuthDeniedException;
import com.toolgateway.guard.exception.ToolTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ToolNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ToolResponse<?> handleNotFound(ToolNotFoundException e) {
        return ToolResponse.fail(ErrorCode.TOOL_NOT_FOUND.code, e.getMessage());
    }

    @ExceptionHandler(ToolAuthDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ToolResponse<?> handleAuthDenied(ToolAuthDeniedException e) {
        return ToolResponse.fail(ErrorCode.AUTH_DENIED.code, e.getMessage());
    }

    @ExceptionHandler(ToolTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ToolResponse<?> handleTimeout(ToolTimeoutException e) {
        return ToolResponse.fail(ErrorCode.TOOL_TIMEOUT.code, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ToolResponse<?> handleBadRequest(IllegalArgumentException e) {
        return ToolResponse.fail(ErrorCode.PARAM_INVALID.code, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ToolResponse<?> handleUnknown(Exception e) {
        log.error("Unhandled exception in tool gateway", e);
        return ToolResponse.fail(ErrorCode.SYSTEM_ERROR.code, e.getMessage());
    }
}
