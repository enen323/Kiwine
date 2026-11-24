package com.prepdeck.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.payment"})
@MapperScan("com.prepdeck.payment.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.payment.client")
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
