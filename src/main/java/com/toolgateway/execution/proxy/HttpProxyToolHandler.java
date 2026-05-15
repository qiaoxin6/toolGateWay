package com.toolgateway.execution.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.handler.ToolHandler;
import com.toolgateway.core.model.ErrorCode;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通用 HTTP Sidecar 代理：将 ToolRequest 转发到任意外部 HTTP 服务。
 * 这是调用 Python/Go/Node.js 工具的方式 —— 它们作为 HTTP 服务运行，
 * 此处理器充当代理桥梁。
 *
 * 使用示例：
 *   registry.register("python_search",
 *       new HttpProxyToolHandler("http://python-search:8000/search", 30_000));
 */
public class HttpProxyToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyToolHandler.class);

    private final String targetUrl;
    private final long timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpProxyToolHandler(String targetUrl, long timeoutMs, ObjectMapper objectMapper) {
        this.targetUrl = targetUrl;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public ToolResponse<?> execute(ToolRequest request) {
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-Trace-Id", request.traceId())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() >= 200 && httpResp.statusCode() < 300) {
                return objectMapper.readValue(httpResp.body(), ToolResponse.class);
            }
            return ToolResponse.fail(ErrorCode.TOOL_EXEC_FAILED.code,
                    "HTTP sidecar returned status " + httpResp.statusCode());

        } catch (Exception e) {
            log.error("HTTP sidecar call failed: target={}", targetUrl, e);
            return ToolResponse.fail(ErrorCode.TOOL_EXEC_ERROR.code, e.getMessage());
        }
    }
}
