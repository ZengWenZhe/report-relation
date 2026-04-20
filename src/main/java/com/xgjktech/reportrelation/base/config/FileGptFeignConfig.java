package com.xgjktech.reportrelation.base.config;

import feign.Request;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

/**
 * FilegptFeign 超时与重试配置
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
public class FileGptFeignConfig {

    @Bean
    public Request.Options fileGptRequestOptions() {
        int connectTimeout = 60 * 1000;
        int readTimeout = 600 * 1000;
        return new Request.Options(connectTimeout, readTimeout);
    }

    @Bean
    @Qualifier("fileGptRetryer")
    public Retryer fileGptRetryer() {
        return new Retryer.Default(1000, 3000, 2);
    }
}
