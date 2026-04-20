package com.xgjktech.reportrelation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * 汇报关联服务启动类
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@EnableScheduling
@EnableFeignClients
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan("com.xgjktech")
@MapperScan("com.xgjktech.reportrelation.mapper")
@EnableAsync
@Slf4j
public class Application implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("report-relation server is started。。。");
    }
}
