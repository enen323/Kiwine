package com.prepdeck.interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 面试引擎配置
 *
 * <p>异步评分线程池及 SSE 心跳线程池。</p>
 */
@Configuration
@EnableAsync
public class InterviewConfig {

    /**
     * 面试异步评分专用线程池
     */
    @Bean("interviewExecutor")
    public Executor interviewExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        var executor = new ThreadPoolExecutor(
                Math.max(cores / 2, 1),
                Math.max(cores, 4),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * SSE 心跳专用线程池（单线程，定时发送心跳）
     */
    @Bean("sseHeartbeatExecutor")
    public Executor sseHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 面试阶段评分聚合调度器（单线程，延迟执行聚合任务）
     */
    @Bean("interviewScheduler")
    public ScheduledExecutorService interviewScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "interview-aggregation");
            t.setDaemon(true);
            return t;
        });
    }
}
