package com.toolgateway.core.validation;

import com.toolgateway.core.model.ToolMetadata.ParamSchema;

import java.util.*;

/**
 * 根据 ParamSchema 列表校验 ToolRequest.params。
 */
public class SchemaValidator {

    private SchemaValidator() {}

    /**
     * 校验参数。返回错误列表，空列表表示通过。
     */
    public static List<String> validate(Map<String, Object> params, List<ParamSchema> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return Collections.emptyList();  // 未定义 schema，跳过校验
        }

        List<String> errors = new ArrayList<>();

        for (ParamSchema schema : schemas) {
            Object value = params != null ? params.get(schema.name()) : null;

            // 必填校验
            if (schema.required() && (value == null || (value instanceof String s && s.isBlank()))) {
                errors.add("missing required param: " + schema.name());
                continue;
            }

            if (value == null) {
                // 有默认值时补入
                if (schema.defaultValue() != null) {
                    params.put(schema.name(), schema.defaultValue());
                }
                continue;
            }

            // 类型校验
            String typeError = checkType(schema.name(), schema.type(), value);
            if (typeError != null) {
                errors.add(typeError);
            }
        }

        return errors;
    }

    private static String checkType(String name, String expected, Object value) {
        if (expected == null) return null;

        return switch (expected) {
            case "string"  -> value instanceof String ? null
                    : name + " expected string, got " + value.getClass().getSimpleName();
            case "number"  -> value instanceof Number ? null
                    : name + " expected number, got " + value.getClass().getSimpleName();
            case "boolean" -> value instanceof Boolean ? null
                    : name + " expected boolean, got " + value.getClass().getSimpleName();
            case "array"   -> value instanceof List<?> ? null
                    : name + " expected array, got " + value.getClass().getSimpleName();
            case "object"  -> value instanceof Map<?, ?> ? null
                    : name + " expected object, got " + value.getClass().getSimpleName();
            default        -> null;  // 未知类型不校验
        };
    }
}
