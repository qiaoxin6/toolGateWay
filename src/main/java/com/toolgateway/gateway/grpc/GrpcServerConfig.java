package com.toolgateway.gateway.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Server 配置 —— 在独立端口启动 Netty gRPC 服务。
 */
@Configuration
public class GrpcServerConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    @Bean(destroyMethod = "shutdown")
    public Server grpcServer(GrpcToolService toolService) throws IOException {
        Server server = ServerBuilder.forPort(grpcPort)
                .addService(toolService)
                .build()
                .start();

        log.info("gRPC server started on port {}", grpcPort);

        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server...");
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
            }
        }));

        return server;
    }
}
