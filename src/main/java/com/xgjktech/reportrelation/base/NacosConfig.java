package com.xgjktech.reportrelation.base;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Nacos动态配置
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Data
@Component
@RefreshScope
public class NacosConfig {

    @Value("${server.domain:}")
    private String apiDomain;

    @Value("${web.domain:}")
    private String webDomain;

    @Value("${scheduled.job.enable:true}")
    private Boolean jobEnable;

    @Value("${extract.concurrent.threads:4}")
    private Integer extractConcurrentThreads;
}
