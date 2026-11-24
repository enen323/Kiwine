package com.prepdeck.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 报告服务启动类
 *
 * <p>职责：面试报告生成、评分分析、历史趋势。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.prepdeck.common", "com.prepdeck.report"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.prepdeck.report.client")
@MapperScan("com.prepdeck.report.repository")
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}