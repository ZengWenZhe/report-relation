package com.xgjktech.reportrelation.base.feign;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.config.FileGptFeignConfig;
import com.xgjktech.reportrelation.base.param.AiData;
import com.xgjktech.reportrelation.base.param.AiModel;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * AI推理服务 Feign Client
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@FeignClient(name = "filegpt", fallback = FileGptFeignFailure.class, configuration = FileGptFeignConfig.class)
public interface FilegptFeign {

    @RequestMapping(value = "s_ai/inner/qa2", method = RequestMethod.POST)
    Result<AiData> getScript(@RequestBody AiModel aiModel);
}
