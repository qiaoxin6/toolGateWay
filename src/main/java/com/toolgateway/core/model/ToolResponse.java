package com.toolgateway.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一内部响应模型 —— 所有工具执行结果都包装为该格式。
 * 使用 {@link JsonInclude.Include#NON_NULL} 序列化，空字段不输出。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResponse<T>(

    /** 是否成功执行 */
    boolean success,

    /** 成功时的业务数据，类型由工具决定 */
    T data,

    /** 失败时的错误码，见 {@link ErrorCode} */
    String errorCode,

    /** 失败时的错误描述 */
    String errorMsg,

    /** 工具执行耗时（毫秒） */
    long costMs
) {

    /** 构造成功响应 */
    public static <T> ToolResponse<T> success(T data, long costMs) {
        return new ToolResponse<>(true, data, null, null, costMs);
    }

    /** 构造失败响应（不计耗时） */
    public static <T> ToolResponse<T> fail(String errorCode, String errorMsg) {
        return new ToolResponse<>(false, null, errorCode, errorMsg, 0);
    }

    /** 构造失败响应（含耗时） */
    public static <T> ToolResponse<T> fail(String errorCode, String errorMsg, long costMs) {
        return new ToolResponse<>(false, null, errorCode, errorMsg, costMs);
    }
}
