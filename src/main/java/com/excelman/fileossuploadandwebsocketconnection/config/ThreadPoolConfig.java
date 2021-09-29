package com.excelman.fileossuploadandwebsocketconnection.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Excelman
 * @date 2021/9/27 下午7:12
 * @description 线程池配置
 */
@Configuration
public class ThreadPoolConfig {

    private final int QUEUE_SIZE = 100;
    private int availableProcessors = Runtime.getRuntime().availableProcessors();
    private int poolSize = (availableProcessors > 4 ? availableProcessors : 4) - 2;
    private final int MAX_POLL_SIZE = 8;

    @Bean(value = "threadPool")
    public ThreadPoolExecutor compressTaskThreadPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                poolSize,
                MAX_POLL_SIZE,
                30000L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                new ThreadFactory() {
                    private AtomicInteger number = new AtomicInteger(0);
                    @Override
                    public synchronized Thread newThread(Runnable r) {
                        String threadName = "compressThread" + "-" + number.getAndIncrement();
                        return new Thread(r, threadName);
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());
        //提前创建核心线程
        executor.prestartAllCoreThreads();
        return executor;
    }

}
