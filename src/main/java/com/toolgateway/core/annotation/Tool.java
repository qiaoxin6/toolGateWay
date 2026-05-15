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
}
