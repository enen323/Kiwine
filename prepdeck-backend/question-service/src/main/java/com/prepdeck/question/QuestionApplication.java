package com.prepdeck.question;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 题库服务启动类
 *
 * <p>职责：面试题库管理、题目推荐。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.question"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.question.client")
public class QuestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestionApplication.class, args);
    }
}
