package com.xgjktech.reportrelation.strategy;

import javax.annotation.Resource;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.PmsFeign;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BpBizTypeHandler implements BizTypeHandler {

    private static final Long BP_TYPE_ID = 52L;

    private static final String BIZ_TYPE_BP = "BP";

    @Resource
    private PmsFeign pmsFeign;

    @Override
    public Long typeId() {
        return BP_TYPE_ID;
    }

    @Override
    public String bizType() {
        return BIZ_TYPE_BP;
    }

    @Override
    public Long fetchCorpId(Long bizId) {
        try {
            Result<Long> result = pmsFeign.getTaskCorpId(bizId);
            return (result != null) ? result.getData() : null;
        } catch (Exception e) {
            log.warn("获取任务corpId失败，bizId={}", bizId, e);
            return null;
        }
    }
}
