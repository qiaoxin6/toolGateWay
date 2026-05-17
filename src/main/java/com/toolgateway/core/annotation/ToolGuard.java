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
    // 周期内允许次数，0=不限
    long rateLimit() default 0;
    // 周期时长
    long rateLimitPeriodMs() default 1000; // 1s
    // 限流维度（空=tool级别, "tenant"/"caller"）
    String rateLimitKey() default ""; 
}
