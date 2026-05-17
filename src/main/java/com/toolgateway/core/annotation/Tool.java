package com.toolgateway.core.annotation;

import com.toolgateway.core.model.RunnerType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a registered tool.
 * All @Tool-annotated Spring beans are auto-registered on startup.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    String name();
    String version() default "1.0.0";
    String description() default "";
    RunnerType runnerType() default RunnerType.LOCAL;
    String target() default "";
    String[] tags() default {};

    /** 发布通道：stable / canary / beta */
    String releaseChannel() default "stable";

    /** 灰度权重 0-100，stable 通道忽略 */
    int canaryWeight() default 0;

    /** 参数 Schema 列表 */
    Param[] params() default {};

    /**
     * 单个参数定义。
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Param {
        String name();
        String type() default "string";   // string / number / boolean / array / object
        String description() default "";
        boolean required() default false;
        String defaultValue() default "";
    }
}
