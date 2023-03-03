package com.qzdp.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * @author haofeng
 * @date 2022/10/16 22:13
 * @description 配置线程池
 */

@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
@Configuration
public class MyThreadConfig {

    private static final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNamePrefix("demo-pool-").build();

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties pool) {
        return new ThreadPoolExecutor(
                pool.getCoreSize(),
                pool.getMaxSize(),
                pool.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(100000),
                namedThreadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

}
