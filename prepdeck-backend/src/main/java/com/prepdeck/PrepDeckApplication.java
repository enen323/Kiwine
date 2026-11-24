package com.prepdeck;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.prepdeck")
@MapperScan(basePackages = "com.prepdeck", markerInterface = BaseMapper.class)
public class PrepDeckApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrepDeckApplication.class, args);
    }
}
