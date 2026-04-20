package com.xgjktech.reportrelation.base.feign;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.cloud.common.exception.BusinessException;
import com.xgjktech.reportrelation.base.param.AiData;
import com.xgjktech.reportrelation.base.param.AiModel;

import org.springframework.stereotype.Component;

/**
 * FilegptFeign 降级
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Component
public class FileGptFeignFailure implements FilegptFeign {

    @Override
    public Result<AiData> getScript(AiModel aiModel) {
        throw new BusinessException("AI服务暂不可用，请稍后重试");
    }
}
