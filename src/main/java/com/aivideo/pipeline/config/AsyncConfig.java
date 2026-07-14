package com.aivideo.pipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool riêng cho pipeline. Render video rất nặng CPU
 * nên chỉ cho chạy 1 job cùng lúc, các job khác xếp hàng đợi.
 * Đây chính là "job queue" đơn giản nhất - đủ dùng cho 1 kênh YouTube.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "pipelineExecutor")
    public TaskExecutor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pipeline-");
        executor.initialize();
        return executor;
    }
}
