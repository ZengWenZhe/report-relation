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

    @Value("${extract.enable:true}")
    private Boolean extractEnable;

    @Value("${extract.schedule.batchSize:200}")
    private Integer extractBatchSize;

    // ========== AI 接口配置 ==========

    @Value("${ai.api.url:https://one.20100706.xyz/v1/messages}")
    private String aiApiUrl;

    @Value("${ai.api.key:sk-eVgMqC874Gtm6XoQ0TFqirpDrrfVdZSZpEGyD9HiTeWaT01u}")
    private String aiApiKey;

    @Value("${ai.api.model:bpModel-1}")
    private String aiApiModel;

    @Value("${ai.api.maxTokens:180000}")
    private Integer aiApiMaxTokens;

    @Value("${ai.api.timeoutSeconds:120}")
    private Integer aiApiTimeoutSeconds;

    @Value("${ai.rate.limit.hourly:2000}")
    private Integer aiRateLimitHourly;
}
