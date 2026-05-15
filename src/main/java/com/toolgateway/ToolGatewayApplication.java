package com.toolgateway;

import com.toolgateway.config.ExternalToolProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ExternalToolProperties.class)
@MapperScan("com.toolgateway.persistence")
public class ToolGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ToolGatewayApplication.class, args);
    }
}
