package com.toolgateway;

import com.toolgateway.config.ExternalToolProperties;
import com.toolgateway.config.HealthCheckProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({ExternalToolProperties.class, HealthCheckProperties.class})
@MapperScan("com.toolgateway.persistence")
@EnableScheduling
public class ToolGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ToolGatewayApplication.class, args);
    }
}
