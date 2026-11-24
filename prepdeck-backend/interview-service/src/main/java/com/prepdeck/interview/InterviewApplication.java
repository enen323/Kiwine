package com.prepdeck.interview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 面试引擎服务启动类
 *
 * <p>职责：AI 面试引擎核心、对话管理、面试状态机。核心业务模块。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.interview"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.interview.client")
@MapperScan("com.prepdeck.interview.repository")
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }
}
