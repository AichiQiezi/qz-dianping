package com.qzdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author haofeng
 * @date 2022/10/16 22:13
 * @description 线程池常量
 */
@ConfigurationProperties("hm.thread")
@Data
public class ThreadPoolConfigProperties {
    private Integer coreSize;

    private Integer maxSize;

    private Integer keepAliveTime;

}
