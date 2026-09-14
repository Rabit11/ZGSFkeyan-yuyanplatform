package com.comac.rpm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 科研项目信息化管理平台 - 后端启动类
 */
@SpringBootApplication
@MapperScan("com.comac.rpm.**.mapper")
@EnableScheduling
public class RpmApplication {

    public static void main(String[] args) {
        SpringApplication.run(RpmApplication.class, args);
    }
}
