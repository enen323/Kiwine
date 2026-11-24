package com.prepdeck.resume;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 简历服务启动类
 *
 * <p>职责：简历上传、AI 解析、简历 CRUD。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.resume"})
@MapperScan("com.prepdeck.resume.repository")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.resume.client")
public class ResumeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeApplication.class, args);
    }
}
