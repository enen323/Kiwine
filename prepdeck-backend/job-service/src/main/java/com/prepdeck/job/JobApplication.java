package com.prepdeck.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.job"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.job.client")
@MapperScan("com.prepdeck.job.repository")
@EnableTransactionManagement
public class JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
