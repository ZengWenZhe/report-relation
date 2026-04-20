package com.xgjktech.reportrelation.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgjktech.cloud.common.exception.BusinessException;
import com.xgjktech.common.base.AbstractBaseService;
import com.xgjktech.reportrelation.data.entity.ExtractPromptConfigEntity;
import com.xgjktech.reportrelation.enums.ExtractPromptCodeEnum;
import com.xgjktech.reportrelation.mapper.ExtractPromptConfigMapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 提取提示词配置Service（带本地缓存，5分钟过期）
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
@Service
public class ExtractPromptConfigService extends AbstractBaseService<ExtractPromptConfigMapper, ExtractPromptConfigEntity> {

    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    private final Map<String, CacheEntry> promptCache = new ConcurrentHashMap<>();

    /**
     * 根据提示词编码获取提示词内容（带本地缓存）
     */
    public String getPromptByCode(ExtractPromptCodeEnum promptCode) {
        if (promptCode == null) {
            throw new BusinessException("提示词编码不能为空");
        }

        String cacheKey = promptCode.getCode();
        CacheEntry cached = promptCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        String content = loadPromptFromDb(promptCode);
        promptCache.put(cacheKey, new CacheEntry(content));
        return content;
    }

    /**
     * 手动清除提示词缓存（修改提示词后调用）
     */
    public void clearCache() {
        promptCache.clear();
        log.info("提示词缓存已清除");
    }

    private String loadPromptFromDb(ExtractPromptCodeEnum promptCode) {
        LambdaQueryWrapper<ExtractPromptConfigEntity> query = new LambdaQueryWrapper<>();
        query.eq(ExtractPromptConfigEntity::getPromptCode, promptCode.getCode())
                .eq(ExtractPromptConfigEntity::getStatus, 1)
                .eq(ExtractPromptConfigEntity::getDeleted, false)
                .orderByDesc(ExtractPromptConfigEntity::getVersion)
                .last("LIMIT 1");

        ExtractPromptConfigEntity config = this.getOne(query);

        if (config == null || StringUtils.isBlank(config.getPromptContent())) {
            log.error("未找到提示词配置，promptCode={}", promptCode.getCode());
            throw new BusinessException("未找到提示词配置：" + promptCode.getName());
        }

        return config.getPromptContent();
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
