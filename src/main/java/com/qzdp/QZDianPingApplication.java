package com.qzdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.qzdp.mapper")
@SpringBootApplication
public class QZDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(QZDianPingApplication.class, args);
    }

}
