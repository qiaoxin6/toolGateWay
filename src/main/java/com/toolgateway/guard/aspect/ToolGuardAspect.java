package com.toolgateway.guard.aspect;

import com.toolgateway.core.annotation.ToolGuard;
import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.guard.exception.ToolAuthDeniedException;
import com.toolgateway.guard.exception.ToolTimeoutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

@Aspect
@Component
public class ToolGuardAspect {

    private static final Logger log = LoggerFactory.getLogger(ToolGuardAspect.class);

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();

    /**
     * 拦截所有带有 @ToolGuard 注解的类的公共方法，
     * 或直接带有该注解的方法。
     */
    @Around("@within(com.toolgateway.core.annotation.ToolGuard) || " +
            "@annotation(com.toolgateway.core.annotation.ToolGuard)")
    public Object guard(ProceedingJoinPoint pjp) {
        ToolGuard guard = resolveGuard(pjp);
        if (guard == null) {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                return toResponse(t);
            }
        }

        String toolName = extractToolName(pjp.getArgs());

        // ── ① Auth ──────────────────────────────────────────
        if (guard.roles().length > 0 && !checkAuth(guard.roles(), pjp.getArgs())) {
            return ToolResponse.fail(ErrorCode.AUTH_DENIED.code, "Access denied: " + toolName);
        }

        // ── ② Timeout + ③ Retry + ④ CircuitBreaker ─────────
        // Order: CB (outer) → Retry → Timeout → actual call
        /*包装顺序：核心 → 重试 → 熔断
          执行顺序：熔断 → 重试 → 核心 */

        // 核心调用：Throwable → RuntimeException 转换（Callable 只允许 Exception）
        Callable<Object> core = () -> {
            if (guard.timeoutMs() > 0) {
                return executeWithTimeout(pjp, guard.timeoutMs(), toolName);
            }
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                //Callable.call() 只能抛出 Exception ，而 pjp.proceed() 可能抛出 Throwable ，需要转换
                if (t instanceof RuntimeException re) throw re;// 已经是 RuntimeException，直接抛出
                throw new RuntimeException(t);  // 其他 Throwable 包装成 RuntimeException
            }
        };

        Callable<Object> decorated = core;

        if (guard.retries() > 0) {
            decorated = Retry.decorateCallable(getOrCreateRetry(toolName, guard), decorated);
        }

        if (guard.circuitBreakerThreshold() > 0) {
            decorated = CircuitBreaker.decorateCallable(getOrCreateCB(toolName, guard), decorated);
        }

        try {
            return decorated.call();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            return handleCircuitOpen(toolName, guard, pjp);
        } catch (Exception e) {
            return toResponse(e);
        }
    }

    // ── 认证校验 ───────────────────────────────────────────────────────

    private boolean checkAuth(String[] roles, Object[] args) {
        ToolRequest req = findToolRequest(args);
        if (req == null) return true; // 无 ToolRequest，跳过认证
        String callerRole = req.context().getOrDefault("role", "anonymous");
        boolean allowed = Arrays.asList(roles).contains(callerRole);
        if (!allowed) {
            log.warn("认证拒绝: tool={}, callerRole={}, required={}",
                    req.toolName(), callerRole, Arrays.toString(roles));
        }
        return allowed;
    }

    // ── 超时控制 ────────────────────────────────────────────────────────

    private Object executeWithTimeout(ProceedingJoinPoint pjp, long timeoutMs, String toolName) {
        // 1. 创建虚拟线程池，每个任务创建一个新线程
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<Object> future = null;
        try {
            // 2. 提交任务到线程池
            future = executor.submit(() -> {
                try {
                    return pjp.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
            // 3. 等待任务执行完成或超时
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);  // 中断正在执行的任务线程
            }
            log.warn("工具执行超时: tool={}, limit={}ms", toolName, timeoutMs);
            throw new ToolTimeoutException(toolName, timeoutMs);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected interruption", e);
        } finally {
            executor.shutdownNow();  // 强制中断残留线程
        }
    }

    // ── 熔断器 ─────────────────────────────────────────────────────────

    private CircuitBreaker getOrCreateCB(String toolName, ToolGuard guard) {
        // 线程安全：toolName不存在则创建，存在则直接返回缓存的实例
        return circuitBreakers.computeIfAbsent(toolName, k -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    // 失败率阈值：配置值×100 → 转为百分比（比如配置0.5 → 50%失败率触发熔断）
                    .failureRateThreshold((float) (guard.circuitBreakerThreshold() * 100))
                    // 滑动窗口大小：统计最近10次请求，计算失败率
                    .slidingWindowSize(10)
                     // 熔断开启后，等待30秒，才进入半开状态
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    // 半开状态下，允许放3个测试请求，验证服务是否恢复
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .build();
            // Resilience4j 自动将熔断指标注册到 MeterRegistry
            // 创建熔断注册中心 → 根据toolName创建/获取熔断实例
            return CircuitBreakerRegistry.of(config).circuitBreaker(toolName);
        });
    }
    /**
     * 熔断器打开后的降级处理方法
     * @param toolName 熔断的工具名称（唯一标识）
     * @param guard 熔断/重试配置类
     * @param pjp 切点对象（可以获取目标方法、参数、目标对象）
     * @return 降级后的响应结果
     */
    private Object handleCircuitOpen(String toolName, ToolGuard guard, ProceedingJoinPoint pjp) {
        log.warn("熔断器已打开: tool={}", toolName);
        if (!guard.fallback().isBlank()) {
            try {
                // 反射获取自定义降级方法
                Method fallbackMethod = pjp.getTarget().getClass()
                        .getMethod(guard.fallback(), ToolRequest.class);
                ToolRequest req = findToolRequest(pjp.getArgs());
                // 反射调用【自定义降级方法】，传入请求参数，返回降级结果
                return fallbackMethod.invoke(pjp.getTarget(), req);
            } catch (Exception e) {
                log.error("降级方法 '{}' 调用失败", guard.fallback(), e);
            }
        }
        // 最终兜底：没有配置降级方法 / 降级方法调用失败 → 返回统一的熔断失败响应
        return ToolResponse.fail(ErrorCode.CIRCUIT_OPEN.code,
                "工具 [" + toolName + "] 暂时不可用 (熔断器已打开)");
    }

    // ── 重试机制 ────────────────────────────────────────────────────────
    /**
     * 获取或创建 重试实例
     * @param toolName 工具名称（唯一标识）
     * @param guard 配置类（重试次数等）
     * @return 重试实例
     */
    private Retry getOrCreateRetry(String toolName, ToolGuard guard) {
        // 1. 从缓存中获取重试实例 ，如果不存在则创建
        return retries.computeIfAbsent(toolName, k -> {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(guard.retries() + 1)
                    .waitDuration(Duration.ofMillis(200))
                   // .ignoreExceptions(ToolTimeoutException.class) // 超时不重试，避免雪上加霜
                    .build();
            
            //  2. Resilience4j 自动将重试指标注册到 MeterRegistry
            //toolName 唯一标识retry实例
            return RetryRegistry.of(config).retry(toolName);
        });
    }

    // ── 状态查询（供 AdminController 使用）─────────────────────────────────

    /** 获取所有工具的熔断器状态：name → 是否打开 */
    public Map<String, Boolean> getCircuitBreakerStates() {
        Map<String, Boolean> states = new java.util.LinkedHashMap<>();
        circuitBreakers.forEach((name, cb) -> states.put(name,
                cb.getState() == CircuitBreaker.State.OPEN
                        || cb.getState() == CircuitBreaker.State.FORCED_OPEN));
        return states;
    }

    /** 获取所有工具的熔断器详细状态 */
    public Map<String, Map<String, Object>> getCircuitBreakerDetails() {
        Map<String, Map<String, Object>> details = new java.util.LinkedHashMap<>();
        circuitBreakers.forEach((name, cb) -> {
            var metrics = cb.getMetrics();
            Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("state", cb.getState().name());
            info.put("failureRate", Math.round(metrics.getFailureRate() * 100.0) / 100.0);
            info.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
            info.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
            details.put(name, info);
        });
        return details;
    }

    // ── 辅助方法 ────────────────────────────────────────────────────────

    private ToolGuard resolveGuard(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        ToolGuard g = method.getAnnotation(ToolGuard.class);
        if (g != null) return g;
        return pjp.getTarget().getClass().getAnnotation(ToolGuard.class);
    }

    private ToolResponse<?> toResponse(Throwable t) {
        // Unwrap ExecutionException / RuntimeException layers
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        if (cause instanceof ToolTimeoutException) {
            return ToolResponse.fail(ErrorCode.TOOL_TIMEOUT.code, cause.getMessage());
        }
        if (cause instanceof ToolAuthDeniedException) {
            return ToolResponse.fail(ErrorCode.AUTH_DENIED.code, cause.getMessage());
        }
        return ToolResponse.fail(ErrorCode.TOOL_EXEC_ERROR.code, cause.getMessage());
    }

    private ToolRequest findToolRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ToolRequest req) return req;
        }
        return null;
    }

    private String extractToolName(Object[] args) {
        ToolRequest req = findToolRequest(args);
        return req != null ? req.toolName() : "unknown";
    }
}
