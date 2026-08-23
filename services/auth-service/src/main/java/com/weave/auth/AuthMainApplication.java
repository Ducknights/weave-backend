package com.weave.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients
@EnableAsync
public class AuthMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthMainApplication.class, args);
    }
}
