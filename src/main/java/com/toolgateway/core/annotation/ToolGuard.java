package com.toolgateway.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative guard configuration for a tool handler.
 * Applied at the method or class level; enforced by ToolGuardAspect.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolGuard {

    /** Roles permitted to invoke this tool */
    String[] roles() default {};

    /** Max execution time in milliseconds (0 = no limit) */
    long timeoutMs() default 30_000;

    /** Max retry attempts (0 = no retry) */
    int retries() default 0;

    /** Circuit breaker — error rate threshold that opens the breaker (0.0–1.0) */
    double circuitBreakerThreshold() default 0.5;

    /** Fallback method name on the same class */
    String fallback() default "";
}
