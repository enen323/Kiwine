package com.prepdeck;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.prepdeck")
@MapperScan("com.prepdeck")
public class PrepDeckApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrepDeckApplication.class, args);
    }
}
