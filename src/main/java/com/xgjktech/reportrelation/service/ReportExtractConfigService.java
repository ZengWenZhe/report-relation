package com.xgjktech.reportrelation.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgjktech.cloud.common.exception.BusinessException;
import com.xgjktech.common.base.AbstractBaseService;
import com.xgjktech.reportrelation.data.entity.ReportExtractConfigEntity;
import com.xgjktech.reportrelation.enums.ExtractConfigCodeEnum;
import com.xgjktech.reportrelation.mapper.ReportExtractConfigMapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportExtractConfigService extends AbstractBaseService<ReportExtractConfigMapper, ReportExtractConfigEntity> {

    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    private final Map<String, CacheEntry> configCache = new ConcurrentHashMap<>();

    /**
     * 根据配置编码获取配置内容（不区分bizType，向后兼容）
     */
    public String getConfigContentByCode(ExtractConfigCodeEnum configCode) {
        return getConfigContentByCode(configCode, null);
    }

    /**
     * 根据配置编码 + bizType 获取配置内容（带本地缓存）
     */
    public String getConfigContentByCode(ExtractConfigCodeEnum configCode, String bizType) {
        if (configCode == null) {
            throw new BusinessException("配置编码不能为空");
        }

        String cacheKey = configCode.getCode() + ":" + StringUtils.defaultString(bizType, "ALL");
        CacheEntry cached = configCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        String content = loadConfigFromDb(configCode, bizType);
        configCache.put(cacheKey, new CacheEntry(content));
        return content;
    }

    public void clearCache() {
        configCache.clear();
        log.info("配置缓存已清除");
    }

    private String loadConfigFromDb(ExtractConfigCodeEnum configCode, String bizType) {
        LambdaQueryWrapper<ReportExtractConfigEntity> query = new LambdaQueryWrapper<>();
        query.eq(ReportExtractConfigEntity::getConfigCode, configCode.getCode())
                .eq(ReportExtractConfigEntity::getStatus, 1)
                .eq(ReportExtractConfigEntity::getDeleted, false);

        if (StringUtils.isNotBlank(bizType)) {
            query.eq(ReportExtractConfigEntity::getBizType, bizType);
        }

        query.orderByDesc(ReportExtractConfigEntity::getVersion)
                .last("LIMIT 1");

        ReportExtractConfigEntity config = this.getOne(query);

        if (config == null || StringUtils.isBlank(config.getConfigContent())) {
            log.error("未找到提取配置，configCode={}, bizType={}", configCode.getCode(), bizType);
            throw new BusinessException("未找到提取配置：" + configCode.getName());
        }

        return config.getConfigContent();
    }

    private static class CacheEntry {
        final String value;
        final long expireAt;

        CacheEntry(String value) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + CACHE_TTL_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
