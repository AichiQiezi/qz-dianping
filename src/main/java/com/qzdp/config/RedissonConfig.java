package com.qzdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
//        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("rediss://152.136.198.137:6379")
//                .setPassword("aishuishui")
//                .setTimeout(2000)
//                .setRetryAttempts(3)
//                .setRetryInterval(1000)
//                .setPingConnectionInterval(1000)//**此项务必设置为redisson解决之前bug的timeout问题关键*****
//                .setDatabase(3);
//        // 创建RedissonClient对象
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://152.136.198.137:6379").setPassword("aishuishui");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
