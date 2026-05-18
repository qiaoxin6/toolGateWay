package com.toolgateway.gateway;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路追踪过滤器 —— 从请求头提取 traceId 并注入 MDC / 响应头。
 * 优先级最高，确保在所有业务逻辑之前执行。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter implements Filter {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String B3_HEADER = "X-B3-TraceId";
    private static final String W3C_HEADER = "traceparent";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 提取或生成 traceId
        String traceId = req.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = req.getHeader(B3_HEADER);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = req.getHeader(W3C_HEADER);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 注入 MDC（自动出现在日志中）
        MDC.put("traceId", traceId);

        // 响应头透传
        resp.setHeader(TRACE_HEADER, traceId);
        // 放入 request attribute 供业务代码使用
        req.setAttribute("traceId", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
