package com.toolgateway.gateway.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolgateway.core.model.ToolMetadata;
import com.toolgateway.core.model.ToolRequest;
import com.toolgateway.core.model.ToolResponse;
import com.toolgateway.core.registry.ToolRegistry;
import com.toolgateway.gateway.adapter.GrpcAdapter;
import com.toolgateway.grpc.Toolgateway;
import com.toolgateway.grpc.ToolGatewayServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * gRPC ToolGatewayService 实现。
 * 接收 gRPC 客户端请求，委托给 ToolRegistry，返回 protobuf 响应。
 */
@Component
public class GrpcToolService extends ToolGatewayServiceGrpc.ToolGatewayServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(GrpcToolService.class);

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private GrpcAdapter adapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void listTools(Toolgateway.ListToolsRequest request,
                          StreamObserver<Toolgateway.ListToolsResponse> responseObserver) {
        var tools = (request.getKeyword().isBlank() && request.getTagsCount() == 0)
                ? registry.listTools()
                : registry.search(
                        request.getKeyword().isBlank() ? null : request.getKeyword(),
                        request.getTagsList().isEmpty() ? null : request.getTagsList());

        Toolgateway.ListToolsResponse.Builder resp =
                Toolgateway.ListToolsResponse.newBuilder().setTotal(tools.size());

        for (ToolMetadata meta : tools) {
            Toolgateway.ToolInfo.Builder info = Toolgateway.ToolInfo.newBuilder()
                    .setName(meta.name())
                    .setVersion(meta.version())
                    .setDescription(meta.description())
                    .setRunnerType(meta.runnerType().name())
                    .setTarget(meta.target() != null ? meta.target() : "")
                    .setEnabled(meta.enabled())
                    .setReleaseChannel(
                            (String) meta.extra().getOrDefault("releaseChannel", "stable"));
            if (meta.tags() != null) {
                info.addAllTags(meta.tags());
            }
            resp.addTools(info);
        }

        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
        log.debug("gRPC listTools: {} tools returned", tools.size());
    }

    @Override
    public void callTool(Toolgateway.CallToolRequest request,
                         StreamObserver<Toolgateway.CallToolResponse> responseObserver) {
        // gRPC → ToolRequest
        ToolRequest toolReq = adapter.adapt(request, Map.of());

        // 调用工具
        ToolResponse<?> result = registry.invoke(request.getToolName(), toolReq);

        // ToolResponse → gRPC
        Toolgateway.CallToolResponse grpcResp =
                (Toolgateway.CallToolResponse) adapter.adaptResponse(result);

        responseObserver.onNext(grpcResp);
        responseObserver.onCompleted();
        log.debug("gRPC callTool: name={}, success={}", request.getToolName(), result.success());
    }
}
