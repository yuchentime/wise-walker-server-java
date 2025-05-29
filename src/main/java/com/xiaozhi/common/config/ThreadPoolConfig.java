package com.xiaozhi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "ioBatchExecutor")
    public ExecutorService ioBatchExecutor() {
        // 获取处理器核心数
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize * 2,       // 核心线程数
                corePoolSize * 4,       // 最大线程数
                60L,                    // 空闲线程存活时间(秒)
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), // 任务队列
                new ThreadPoolExecutor.CallerRunsPolicy()      // 拒绝策略
        );

        // 允许核心线程超时回收(节省资源)
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
