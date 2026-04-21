package com.xgjktech.reportrelation.strategy;

import javax.annotation.Resource;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.PmsFeign;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.enums.ExtractConfigCodeEnum;
import com.xgjktech.reportrelation.service.ExtractSchemaService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BpExtractStrategy implements ExtractStrategy {

    private static final String BIZ_TYPE_BP = "BP";

    private static final String CONTEXT_PLACEHOLDER = "{{BP_CONTEXT}}";

    @Resource
    private ExtractSchemaService extractSchemaService;

    @Resource
    private PmsFeign pmsFeign;

    @Override
    public String bizType() {
        return BIZ_TYPE_BP;
    }

    @Override
    public void extract(ReportRelationBusinessEntity record) {
        String bpContext = fetchBpContext(record.getBizId());
        extractSchemaService.extractSingle(
                record,
                ExtractConfigCodeEnum.REPORT_EXTRACT_SCHEMA,
                bpContext,
                CONTEXT_PLACEHOLDER);
    }

    private String fetchBpContext(Long taskId) {
        try {
            Result<String> result = pmsFeign.getBpContext(taskId);
            if (result != null && StringUtils.isNotBlank(result.getData())) {
                return result.getData();
            }
        } catch (Exception e) {
            log.error("获取BP上下文失败，taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return null;
    }
}
