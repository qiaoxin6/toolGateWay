package com.toolgateway.core.model;

/**
 * 统一错误码枚举 —— 三段式编码。
 * <pre>
 * E0xx — 协议层错误（工具未找到、参数无效等）
 * E1xx — 护栏层错误（认证拒绝、限流、超时、熔断）
 * E2xx — 执行层错误（工具执行失败、内部异常）
 * E9xx — 系统级错误
 * </pre>
 */
public enum ErrorCode {

    // ── 协议层错误 ──────────────────────────────────────────────

    /** 工具未找到 */
    TOOL_NOT_FOUND("E001", "Tool not found"),
    /** 参数无效 */
    PARAM_INVALID("E002", "Invalid parameters"),
    /** 未知协议 */
    PROTOCOL_UNKNOWN("E003", "Unknown protocol"),

    // ── 护栏层错误 ──────────────────────────────────────────────

    /** 认证拒绝 */
    AUTH_DENIED("E100", "Authentication denied"),
    /** 频率限制 */
    RATE_LIMITED("E101", "Rate limit exceeded"),
    /** 工具执行超时 */
    TOOL_TIMEOUT("E102", "Tool execution timeout"),
    /** 熔断器已打开 */
    CIRCUIT_OPEN("E103", "Circuit breaker is open"),

    // ── 执行层错误 ──────────────────────────────────────────────

    /** 工具执行失败 */
    TOOL_EXEC_FAILED("E200", "Tool execution failed"),
    /** 工具内部错误 */
    TOOL_EXEC_ERROR("E201", "Tool internal error"),

    // ── 系统级错误 ──────────────────────────────────────────────

    /** 系统内部错误 */
    SYSTEM_ERROR("E999", "System internal error");

    /** 错误码 */
    public final String code;
    /** 默认错误信息 */
    public final String defaultMsg;

    ErrorCode(String code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }
}
