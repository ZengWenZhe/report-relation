package com.xgjktech.reportrelation.service;

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
 * 提取提示词配置Service
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
@Service
public class ExtractPromptConfigService extends AbstractBaseService<ExtractPromptConfigMapper, ExtractPromptConfigEntity> {

    /**
     * 根据提示词编码获取提示词内容
     */
    public String getPromptByCode(ExtractPromptCodeEnum promptCode) {
        if (promptCode == null) {
            throw new BusinessException("提示词编码不能为空");
        }

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
}
