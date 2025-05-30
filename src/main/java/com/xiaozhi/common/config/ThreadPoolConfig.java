package com.xiaozhi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "ioBatchExecutor")
    public ExecutorService ioBatchExecutor() {
        // 使用虚拟线程池处理IO密集型任务
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
