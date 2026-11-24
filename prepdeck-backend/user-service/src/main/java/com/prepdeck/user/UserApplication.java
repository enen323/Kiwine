package com.prepdeck.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 *
 * <p>职责：用户注册/登录、认证鉴权、用户信息管理。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.user"})
@MapperScan("com.prepdeck.user.repository")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.user.client")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
